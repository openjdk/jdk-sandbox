# Towards a bytecode API for the JDK, Part 2

#### Brian Goetz, August 2021

The [previous document](bca-arch-1.html) outlined the motivation for building a
new bytecode API; this document outlines the API design and implementation
approach we've taken.  The examples here are intended as conceptual
illustrations rather than as exposition of a finalized API.

## Traversing the classfile

Classfiles have a tree-like structure; a class contains class metadata, fields,
methods, and attributes; methods can also contain attributes, including the
special `Code` attribute, which contains the method body, and in turn can have
its own attributes such as the `LocalVariableTable` attribute.

Different use cases may want to traverse different portions of the classfile; if
we're scanning methods for annotations, there's no point in parsing the field
declarations, method bodies, line number tables, bootstrap method tables, etc.
Similarly, if we're adapting the constructors of a class, we will want to dive
into the bodies of the `<init>` methods,  but not into those of other methods.
Each use case will chart a different path through the tree structure, and we'd
like to only have to process the portions of the classfile that the client is
interested in (or those that are required by those portions.)

ASM gives us two choices for traversing the classfile; random access (the tree
API)  and streaming (the visitor API), and within the streaming API, it gives us
the choice to dive into method bodies or not each time `visitMethod` is called.
This is a defensible design choice, but it has a number of downsides:

  - The tree API is much more expensive than the streaming API, because it
    materializes the entire classfile up front
  - The two APIs are different (though there are some commonalities), so you
    have to commit to one or the other
  - Attributes are treated as an afterthought
  - Traversal patterns only approximate the structure of the classfile

It turns out that it is not necessary to choose between random access and
streaming, or to have completely disjoint APIs for the two, or that the
possibility of random access is inherently much more expensive; with some clever
use of laziness in the implementation, it is possible to use the same idioms to
model both a lazily-inflated random-access view of a classfile element, as well
as a source of contained elements, and to mix-and-match our use of them.

## Models, elements, and builders

A classfile contains a constant pool, classfile metadata (the fixed part of the
`ClassFile` structure), fields, methods, and attributes.  A method contains
method metadata (the fixed part of the `method_info` structure) and attributes,
including the `Code` attribute; a `Code` attribute contains an exception table,
bytecodes, and other attributes (such as `LineNumberTable`,
`LocalVariableTable`, etc.)

Zooming out, we can see the classfile structure as a tree; intermediate nodes
for structured entities that have their own children (classes, methods, fields)
with node-specific metadata, and leaf nodes for individual instructions and
other classfile entities such as annotations.  By choosing to model each layer
with a common structure, we gain the ability to abstract over traversals (as
well as simplicity.)

We model accessing and constructing a tree view of the classfile using three
main kinds of abstractions: models, elements, and builders. An _element_
describes an entity in a classfile -- a field, method, attribute, annotation,
instruction, etc.  Some elements are simple, such as instructions; others, such
as fields or methods, may also be containers for other elements. These complex
elements are _models_, and admit being treated as a whole or being broken down
further into a sequence of elements.  For each kind of model, there is a
corresponding _builder_ which accepts elements of the corresponding model type.

The core abstraction for modeling the classfile is `CompositeModel`, which ties
together these three aspects:

```
public interface CompositeModel<E extends CompositeModel.Element,
                                B extends CompositeModel.Builder<E, B>> {

    Iterable<E> elements();

    List<Attribute<?>> attributes();

    <T extends Attribute<T>> Optional<T> findAttribute(Attribute.AttributeMapper<T> attr);
    <T extends Attribute<T>> List<T> findAttributes(Attribute.AttributeMapper<T> attr);

    interface Element {
    }

    interface Builder<E extends Element, B extends Builder<E, B>> {
        B with(E element);
    }
}
```

The generics tell us that these things come in triples -- a related triple of
model, element, and builder types for each composite classfile entity.  A model
exposes a way to access its attributes and elements.  There are currently five
triples of (model, element, builder) types -- classes, methods, fields, record
components, and code.  For example, methods are modeled by `MethodModel` (which
extends `CompositeModel`), `MethodElement` (which extends
`CompositeModel.Element`, and whose subtypes are the kinds of elements of
methods), and `MethodBuilder` (which extends `CompositeModel.Builder`.)  The
same entity can be both a model and an element; a method is both an element of a
class (`MethodModel` implements `ClassElement`), as well as a model in its own
right, with its own elements.  Models and elements are immutable; elements can
be switched over by pattern matching.

For example, to enumerate the members of a class, we get a `ClassModel`,
enumerate its elements, and match on elements that are `FieldModel` or
`MethodModel`:

```
ClassModel cm = ClassModel.of(bytes);
for (ClassElement ce : cm.elements()) {
    switch (ce) {
        case FieldModel fm ->
            System.out.printf("Field %s : %s%n", fm.nameString(),
                              fm.descriptorString());
        case MethodModel mm ->
            System.out.printf("Method %s : %s%n", mm.nameString(),
                              mm.descriptorString());
    }
}
```

This mode of interaction is like the streaming mode of ASM, visiting a class
with `ClassVisitor`.  But there is also a version of the materialized mode of
ASM; `ClassModel` has accessors to expose the list of fields and methods
directly:

```
ClassModel cm = ClassModel.of(bytes);
for (FieldModel fm : cm.fields())
    System.out.printf("Field %s : %s%n", fm.nameString(),
                      fm.descriptorString());
for (MethodModel mm : cm.methods())
    System.out.printf("Method %s : %s%n", mm.nameString(),
                      mm.descriptorString());
}
```

Like ASM, we have a choice of streaming or materialized access, but unlike ASM,
we can use the same abstractions for both (e.g., `MethodModel`), and, because
the implementation is fully lazy, we don't pay a significant up-front cost for
constructing the model, we only pay when we access a portion of the classfile.
(For example, constructing a `MethodModel` doesn't parse any of the instructions
of that method, it merely scans the attribute table for their names and offsets;
instruction parsing only happens when we actually ask for the elements of a
`Code` attribute.)  This means we can even mix-and-match materialized and
streaming access as the situation requires.

#### Adapting

Adaptation is a combination of reading, intercepting some elements and replacing
them with others, and writing.  While `ClassBuilder` has methods for adding
methods, fields, class attributes, etc, it also can accept a `ClassElement`
directly.

If we wanted to adapt a class to filter out methods whose names start with
`debug`, it might look like:

```
ClassModel cm = ClassModel.of(bytes);
byte[] newClass = ClassBuilder.build(cm.flags(),
                                     cm.name(),
                                     cm.superclass(),
                                     cm.interfaces(),
                                     cm.majorVersion(),
                                     cm.minorVersion(),
                                     cb -> {
                                         for (ClassElement ce : cm.elements()) {
                                             if (ce instanceof MethodModel mm
                                                 && mm.nameString().startsWith("debug"))
                                                     continue;
                                             cb.with(ce);
                                         }
                                     });
});
```

This example copies the class header information from the underlying model,
creates a new builder for it, iterates the elements of the underlying model,
and passes them all on to the builder, except for the methods whose name
begins with `debug`.

While this is fairly clear and concise, we can do better.  We can model a
transformation on a stream of elements as a lambda that takes both an element
and a builder, which has a chance to examine each element, and then pass it on,
or not, or  pass different things on, to the builder.  We can express the above
more clearly as:

```
byte[] newClass = ClassModel.of(bytes).adapting((b, e) -> {
    if (ce instanceof MethodModel mm
            && !mm.nameString().startsWith("debug"))
        cb.with(ce);
});
```

This is the same as the previous example, except that the copying of header
information and iteration of the elements is handled by the `adapting` method.
Transforms can be applied at any level (class, method, field, instruction),
simple transforms can be composed into larger ones, and lower-level transforms
(such as on methods) can be lifted to higher ones (such as on classes.)

#### Immutability

All classfile entities (models, elements, and ancillary data structures such as
annotations) are modeled as immutable objects.  If we want to generate a
classfile, or adapt one classfile to another, we use a builder, which
accumulates state internally, and then it produces a complete classfile.
Builders are, at root, consumers of elements; most builder methods such as
`MethodBuilder::withExceptions` are conveniences which wrap their arguments in
an element (here, an `Exceptions` attribute), and pass it to the `consume`
method of the builder.

While at first glance this may sound like it would be slow, the library uses a
great deal of laziness under the hood for efficiency.  In the above adaptation
example, constructing a `MethodModel` does little more than capture the start
and end offset into the underlying classfile `byte[]`.  The `method_info`
structure is parsed only when information is needed out of it, and then only the
parts we need.  When a `MethodModel` is passed to our transform lambda in the
example above, and then passed back to `ClassBuilder::accept` having only
inspected its name, we know the method contents are unmodified and can copy the
raw bytes from the original classfile to the adapted one -- without ever parsing
the details of the method body.  (See "Constant pool sharing", below, for
caveats.)

#### Constant pool sharing

A `ClassModel` exposes a read-only view of its constant pool and bootstrap
method table.  Like other classfile entities, the constant pool is immutable and
lazily inflated.  When building a new classfile, idempotent mutative lookup
methods are provided (e.g., "get class entry"); if the entry being sought is
present, it is returned, otherwise it is added and the new entry returned.  The
bootstrap method table attribute is treated as logically being part of the
constant pool, rather than exposed as a raw attribute, since the two are tightly
coupled.

By default, when adapting a classfile, not only is the class header information
(name, supers, etc) copied from the old class to the new, but so is the constant
pool.  This means that constant pool indexes in the old classfile are guaranteed
to be valid and refer to the same thing in the new classfile, which in turn
means that the raw bytes of nearly all classfile entities (fields, methods,
attributes, annotations, stack maps, etc)  are also valid and mean the same
thing in the new classfile, and therefore can be bulk-copied rather than parsed
and regenerated.  (Constant pool sharing can be disabled if desired; this makes
generation slower but compacts the constant pool for the smallest possible
classfile.)

To avoid unnecessary round-trips through the constant pool, the base APIs expose
names as the appropriate kind of constant pool entry (e.g., the
`NewReferenceArray`) instruction uses a `ClassEntry` rather than a `String` to
expose the component type), with convenience methods to expose/accept the string
form.

## Data model

There are many degrees of creativity in designing an API for a domain as complex
as the Java classfile format.  We've chosen to build this API starting with a
data model for the classfile, and then mechanically deriving most of the API
from the data model.  (There is still room for creative choice in the modeling.)
The data model defines a set of primitives; we may also add higher-level
conveniences atop these.

We define a data model using a notation similar to Haskell's `data` construct:

```
ClassElement =
    FieldModel*(int flags, UtfEntry nameEntry, Utf8Entry descriptorEntry)
    | MethodModel*(int flags, UtfEntry nameEntry, Utf8Entry descriptorEntry)
    | ...
```

The list in the parentheses are the element's _components_; elements that are
themselves models may further have their own elements.  The element class will
have accessors for each component.  The character after the element name is an
arity indicator; `*` for zero-or-more, `+` for one-or-more, `?` for zero-or-one,
and `!` for exactly-one.  The types named `XxxEntry` are references into the
constant pool for various kinds of constant pool entry.

We start with `Class`:

```
ClassElement =
    FieldModel*(int flags, UtfEntry nameEntry, Utf8Entry descriptorEntry)
    | MethodModel*(int flags, UtfEntry nameEntry, Utf8Entry descriptorEntry)
    | ModuleAttribute?(int flags, ModuleEntry moduleNameEntry, UtfEntry versionEntry,
                       List<ModuleRequireInfo> requires, List<ModuleOpenInfo> opens,
                       List<ModuleExportInfo> exports, List<ModuleProvidesInfo> provides,
                       List<ClassEntry> uses)
    | ModulePackagesAttribute?(List<PackageEntry> packages)
    | ModuleTargetAttribute?(Utf8Entry targetPlatform)
    | ModuleHashesAttribute?(Utf8Entry algorithm, List<HashInfo> hashes)
    | ModuleResolutionAttribute?(int resolutionFlags)
    | SourceFileAttribute?(Utf8Entry sourceFileEntry)
    | SourceDebugExtensionsAttribute?(byte[] contents)
    | CompilationIDAttribute?(Utf8Entry compilationIdEntry)
    | SourceIDAttribute?(Utf8Entry sourceIdEntry)
    | NestHostAttribute?(ClassEntry hostClass)
    | NestMembersAttribute?(List<ClassEntry> members)
    | RecordAttribute?(List<RecordComponent> components)
    | EnclosingMethodAttribute?(ClassEntry classEntry, NameAndTypeEntry methodEntry)
    | InnerClassesAttribute?(List<InnerClassInfo> classes)
    | PermittedSubclassesAttribute?(List<ClassEntry> permittedSubclasses)
    | DeclarationElement*
```

`DeclarationElement` are the elements that are common to all declarations
(classes,  methods, fields), so we factor them out:

```
DeclarationElement =
    SignatureAttribute?(Utf8Entry signatureEntry)
    | SyntheticAttribute?()
    | DeprecatedAttribute?()
    | RuntimeInvisibleAnnotationsAttribute?(List<Annotation> annotations)
    | RuntimeVisibleAnnotationsAttribute?(List<Annotation> annotations)
    | CustomAttribute*
    | UnknownAttribute*
```

We see that the elements of a class are fields, methods, and a number of
attributes that are applicable to classes.  (But, not all attributes that might
live on a class are elements of the class; the `BootstrapMethodsTable` is
logically part of the constant pool, so it is modeled there.)  Fields and
methods are fairly simple as well; most of the complexity of methods lives in
the `Code` model (which models the `Code` attribute along with the code-related
atributes: stack map table, local variable table, line number table, etc.)

```
FieldElement =
    DeclarationElement
    | ConstantValueAttribute?(ConstantValueEntry entry)

MethodElement =
    DeclarationElement
    | CodeModel?()
    | AnnotationDefaultAttribute?(ElementValue defaultValue)
    | MethodParametersAttribute?(List<MethodParameterInfo> parameters)
    | ExceptionsAttribute?(List<ClassEntry> exceptions)
```

The `Code` model is unique in that its elements are _ordered_.  Elements of
`Code` include ordinary bytecodes, as well as a number of pseudo-instructions
representing branch targets, line number metadata, local variable metadata, and
catch blocks.

```
CodeElement = Instruction | PseudoInstruction

Instruction =
    Load(TypeKind type, int slot)
    | Store(TypeKind type, int slot)
    | Increment(TypeKind type, int slot, int constant)
    | Branch(Opcode opcode, Label target)
    | LookupSwitch(Label defaultTarget, List<Integer> targets,
                   List<Label> targets)
    | TableSwitch(Label defaultTarget, int low, int high,
                   List<Label> targets)
    | ReturnFromMethod(TypeKind kind)
    | ThrowException()
    | FieldAccess(FieldRefEntry fieldEntry)
    | Invoke(MemberRefEntry methodEntry, boolean isInterface)
    | InvokeDynamic(InvokeDynamicEntry entry)
    | NewObject(ClassEntry type)
    | NewReferenceArray(ClassEntry componentType)
    | NewPrimitiveArray(TypeKind typeKind)
    | NewMultiArray(ClassEntry componentType, int dims)
    | ArrayLoad(Opcode opcode)
    | ArrayStore(Opcode opcode)
    | TypeCheck(ClassEntry type)
    | Conversion(TypeKind from, TypeKind to)
    | Operator(Opcode opcode)
    | Constant(ConstantDesc c)
    | Stack(Opcode opcode)
    | Monitor(Opcode opcode)
    | Nop()

PseudoInstruction =
    | LabelTarget(Label label)
    | LineNumber(int line)
    | ExceptionCatch(Label tryStart, Label tryEnd, Label handler, String exceptionName)
    | LocalVariable(int slot, String name, String descriptor, Label startScope, Label endScope)
    | LocalVariableType(int slot, String name, String signature, Label startScope, Label endScope)
```

When a `Code` attribute is generated with a `CodeBuilder`, it generates the
ancillary attributes at the same time -- `LineNumberTable`, `StackMapTable`,
`LocalVariableTable`, `LocalVariableTypeTable`, etc.

#### Labels

Labels represent a point in the stream of instructions.  Because of the
possibility of forward branches, the actual target of the label may not be known
at the time the label is used; if so, these are filled in later.  The target of
a label is maintained by the context in which the label is used, so the same
label object can correspond to one offset in a `CodeModel`, and another offset
in a `Code` attribute being built by a `CodeBuilder`.  (This is essential if we
want to support streaming adaptation; we want to be able to take the elements of
a `CodeModel`, add or remove some, and present them in the right order to a
`CodeBuilder`, meaning that the offsets might change as a result.)

#### From data model to API

The base layer of the API is derived mechanically from the data model.  For a
leaf element (one that is not also a model), there is an interface with a
factory whose signature matches that of the data model (possibly along with
convenience versions), and accessors corresponding to each property in the data
model.  The element interface implements `XxxElement` for each context in which
it is applicable.  If the property is a constant pool entry that is backed by a
Utf8 entry, there is a convenience accessor to expose the string form as well;
if the property is a string, there are accessors for both the `Utf8Entry` form
and the string form.  For a non-leaf element, there are corresponding model and
builder classes, along with `adaptingXxx` methods on the parent model. For each
element of a model, the builder has `withXxx` methods whose signature
corresponds to the data model, plus possibly additional convenience versions.

[ creativity is in the model ]
[ conveniences are strictly conveniences ]

#### Model and builder conveniences

In addition to acting as a source of elements, models can also offer random
access; for example, `ClassModel` has accessors for `fields()` and `methods()`
that return lists of `FieldModel` and `MethodModel` respectively.  (These are
conveniences in that they could be implemented by iterating the elements
directly.)

Builders also offer multiple layers of conveniences; for issuing a load
instruction, you could say `with(LoadInstruction.of(TypeKind.REF, 0))`, or
`load(TypeKind.REF, 0)`, or `aload(0)`, or `aload_0()` (each is defined in terms
of the previous.)  Additionally, builders may offer conveniences for block
structuring and local variable management.

#### Attributes

Nearly all the data in classfiles lives in attributes.  For most attributes, the
API exposes the attribute directly as an element, rather than decomposing it
into smaller entities.  (This means that for elements you don't care about, you
don't pay anything to take them apart and put them back together.)

Attributes that are tightly couple to other classfile structures, such as the
bootstrap method table attribute (effectively part of the constant pool) or the
local variable table attribute (tightly coupled to the instructions of the
`Code` attribute) are treated specially.  For example, the `LocalVariableTable`
attribute (which contains indexes into the instruction array) is exploded into
`LocalVariable` elements and incorporated into the element stream of
`CodeModel`.  Processing of debug information (such as local variable
information) can be suppressed by a traversal option if desired.

Custom attributes are supported using the same framework as standard attributes.
There is an `AttributeMapper` abstraction, which models an attribute (including
where it is valid, and since what classfile version it is supported), and
manages the translation between the `byte[]` representation and the in-memory
representation.  For example, here is the `AttributeMapper` for the `Signature`
attribute, and the corresponding interface:

```
interface SignatureAttribute
        extends Attribute<SignatureAttribute>,
                ClassElement, MethodElement, FieldElement, RecordComponentElement {

    ConstantPool.Utf8Entry signatureEntry();
    String signatureString();
}

public static final AttributeMapper<SignatureAttribute>
        SIGNATURE = new AbstractAttributeMapper<>("Signature", EVERYWHERE,
                                                  ClassfileConstants.JAVA_5_VERSION) {
            @Override
            public SignatureAttribute read(CompositeModel<?, ?> enclosing,
                                           ClassReader reader,
                                           AttributeMapper<SignatureAttribute> mapper,
                                           int pos) {
                return new BoundAttributes.BoundSignatureAttribute(enclosing, reader, mapper, pos);
            }

            @Override
            protected void writeBody(BufWriter buf, SignatureAttribute attr) {
                buf.writeIndex(attr.signatureEntry());
            }
        };
```

This mediates between the classfile format of the attribute payload (which in
this case is a single `u2` containing the index of a Utf8 string) and the
`SignatureAttribute` element type.  Because `Signature` is valid on classes,
methods, fields, and record components, it implements each of the corresponding
`XxxElement` types.  Nonstandard attribute implementations extend the
`CustomAttribute` base class.

## Examples





[ Enumerating methods and fields ]
[ Injecting supertypes ]
[ Stripping debug attributes ]
[ Rewriting method call sites ]


[ handling absence ]