# A data model-driven API for bytecode analysis

At its core, the bytecode API is driven by a _data model_ for classfiles from
which the primitives of the API are derived. This document describes the
current state of the data model, and the rules by which the API primitives are
derived from it. (The API may additionally have _convenience methods_ that are
defined in terms of these primitives; in the early stages, we should seek to be
clear about which methods are primitive and which are conveniences.)


## Interaction modes

Since there is such a wide variety of use cases for bytecode analysis and
generation, the API supports multiple ways of interacting with a classfile.
These can be broken into two kinds of views — streaming and materialized — and
three modes of interaction — reading, writing, and adapting. Streaming views a
classfile (or a part of a classfile) as a stream of elements; materialization
views a classfile as a fully-formed object (though the implementation may use
laziness to reduce the cost of materialization.)

For most use cases, streaming is both simpler and more efficient; for more
complex use cases, especially those that involve significant analysis, a
materialized view may be easier to work with.

Sometimes we use bytecode APIs for pure reading (such as reflection) or
writing (such as back-end code generation), but the most common is _adapting_,
where we read one classfile, make some targeted additions, removals, or
modifications, and write it back out. Adaptation involves composing reading and
writing, which exposes a signficant constraint: the reading and writing APIs
must be aligned if adaptation is to be easy to use.

The base API is a streaming one, in which we model a classfile as a tree of
elements, where elements might be high-level composites like fields or methods,
or low-level leaves like individual bytecodes. Some elements, such as methods,
are both elements of their parent (the classfile) and containers for their own
elements (such as attributes.)

## How the classfile is modeled

The key abstractions in the API are:

  * Element. This is the fundamental abstraction; every portion of a
    classfile is described by a suitable element type, which is an _immutable_
    carrier for its data.
  * Model. A _model_ is an immutable container for elements, which models a
    complex entity (class, method, field, etc). Models may
    also be elements of some higher-level entity; a `MethodModel` is both a
    model for a method, and a kind of element of a class. Models provide a
    means of enumerating their elements. Models may additionally have (ideally
    lazy) convenience methods for navigating the structure of the model.
  * Builder. A _builder_ consumes elements and produces a (part of a)
    classfile.  A builder is a `Consumer` for the appropriate element type; the
    `accept` method is the only primitive, but builders are likely to have many
    convenience methods to simplify the generation of classfiles. We
    obtain access to a `ClassBuilder` through a static factory; other kinds of builders are
    obtained indirectly from factory methods on the `ClassBuilder`.
  * Transform. A _transform_ describes a transformation of a sequence of
    elements; we can consider it a `flatMap` operation that takes one element
    and replaces it with zero or more elements.  Transforms can decide whether
    process the elements sequentially, or "dive" into the elements of a composite
    element.

Everything is tied together through `CompositeModel`:

    public interface CompositeModel<E extends CompositeModel.Element<E, B, ?>, B extends Consumer<E>> {
        Iterable<E> elements();
        Stream<E> elementStream();
        List<E> elementList();

        interface Element<E, B extends Consumer<E>, K extends Enum<K>> {
            void build(B builder); K kind();
        }
    }

For each kind of model (class, method, field, etc), there are corresponding
`XxxModel`, `XxxBuilder`, and `XxxModel.Element` types,
where `XxxModel` implements `CompositeModel<XxxModel.Element, XxxBuilder>`.
The main functionality of a model is to iterate its elements. Each kind of
model has a family of `Element` interfaces which model its elements. So an
`XxxModel` can be exploded into elements, the elements can be distinguished
via pattern matching, the stream of elements can be adapted to add, remove, or
modify elements, and an `XxxBuilder` can consume the resulting adapted stream.

<!-- review note: we haven't introduced arity yet, nor have we introduced properties -->

## Elements vs Properties

The general rule is that !-arity (exactly once) elements in the classfile are
properties of the XyzModel and not Elements in the XyzModel. For example,
ClassModel has properties for name, superclass, flags, and classfile version.
This has implication in that you need to know up front the name of the class
you would like to build, and also that you can get those properties without
streaming the other elements.

## A note on types

In many cases the models below uses String for types and descriptors. While
there may be conveniences that allows a user to build a ClassModel using a
String to denote the supertype in the model the supertype will be a ClassInfo
corresponding to a constantpool reference to a class. This doc will be updated
to reflect that after the constant pool model is finished.

<!-- review note: I think we are coming to a more refined understanding of
how much we want to expose CP entries vs higher-level descriptions.  Previously
we had kept the Entry types hidden, but the performance cost of this was crushing.
The current state of the implementation is sort of a halfway, but I now think
we'd like to get to a point where the primary data model deals in terms of
ClassEntry, and we have conveniences for Builder to accept String.  THe most
recent refactoring goes a long way towards making this possible, exposing the
ConstantPoolBuilder via builders that can be used to construct the needed entries.
There will be multiple rounds of adjusting the implementation to be consistent
with this goal (if we agree this makes sense, we should discuss), but we should
adjust the data model and implementation accordingly. -->

## Classes

This model is a rough sketch, needs to be discussed, and the code is not yet in
sync with it.

    ClassModel(String name, String superclass, List<String> interfaces, int flags, classfileVersionMajor, classfileVersionMinor)

    ClassElement =
        | Interfaces?(List<String> interfaces)
        | Signature?(String signature)
        | SourceFile?(String filename)
        | DebugExtension?(String debugExtensions)
        | ClassSignature?(String signature)
        | NestMember*(String nestMember)
        | InnerClass*(String innerClass, String outerClass, String innerName, int innerFlag)
        | MethodModel*(String name, String descriptor, int flags)
        | FieldModel*(String name, String descriptor, int flags)
        | Annotations{0,2}(boolean isRuntimeVisible)


## Methods

This model is a rough sketch, needs to be discussed, and the code is not yet in
sync with it.

    MethodElement =
        MethodExceptions // todo
        | MethodSignature?(String signature)
        | AnnotationDefault?(AnnotationModel annotationDefault)
        | CodeModel?()
        | MethodParameters?(String[] names, int[] flags)
        | Annotations{0,2}(boolean isRuntimeVisible)

A MethodParameters element indicate the presence of the 'method_parameters'
attribute in the method_info for this method. It is omitted if the attribute is
not present.

## Fields


This model is a rough sketch, needs to be discussed, and the code is not yet in
sync with it.

    FieldElement =
        FieldSignature?(String signature)
        | ConstantValue?(ConstantDesc constantValue)
        | Annotations{0,2}(boolean isRuntimeVisible)

## Code

This is the most vetted of the models. The instructions are largely done. The
code for instructions should be functionally in sync with the model, though the
naming conventions may need to be reviewed and normalized.

Several of the pseudo-instructions seem to be done, but others may need
discussion, implementation, or alignment.

An important consideration is the cost of implementing the pseudo-instructions.
For example, for line numbers, we have to parse the line number table(s), sort
the table by BCI, and then, when enumerating the contents of the `Code`
attribute, deliver the `LineNumber` elements at the right places. This may have
a significant overhead, so we will want measurements in place before we do this
work, so we can identify the cost. The iteration API may want flags for
non-essential expensive things, such as suppressing line number events (there's
a skeleton of this in `ClassModel.Option`.)

Some elements are sensitive to delivery timing and/or ordering.  `LabelTarget`
says that the target of the given label is the current position in the stream,
and the most recently delivered `LineNumber` element describes the current
line.  The `CatchHandler` elements could be delivered at any time in any order,
but probably should be delivered right at the start of the corresponding try
block.  Method parameter events should probably be delivered before any
instructions.

<!-- review note: In hindsight, it turned out that trying to be clever about the
timing of exception elements could result in subtly changing semantics on
adaptation, so the code was adjusted to alway deliver them up-front.  This
has implications for the modeling which should probably be discussed. -->

Eventually, the data model and documentation should reflect these ordering
constraints.

Nullity is used to signal absence in some elements, such as the exceptionType
corresponding to a finally block. These should also be reflected in the data
model and documentation.

The instruction classes have a common `opcode()` method, declared in
`Instruction`. This is left out of the data model to remove noise. For
instructions like `Branch`, the opcode tells us what kind of branch it is.

DebugInfoElements, unless suppressed, are delivered at a position in the
stream corresponding to their starting scope, or corresponding to the line
start.

    CodeElement = Instruction | PseudoInstruction | DebugInfoElement

    Instruction =
        Load(TypeKind type, int slot)
        | Store(TypeKind type, int slot)
        | Increment(int slot, int constant)
        | Branch(Label target)
        | LookupSwitch(Label defaultTarget, List<Integer> targets, List<Label> labels)
        | TableSwitch(Label defaultTarget, int low, int high, List<Label> labels)
        | ReturnFromMethod(TypeKind kind)
        | ThrowException()
        | FieldAccess(String owner, String name, String type)
        | Invoke(String owner, String name, String type, boolean isInterface)
        | InvokeDynamic(DynamicCallSiteDesc desc)
        | NewObject(String type)
        | NewArray(String componentType, int dimensions)
        | ArrayLoad(TypeKind type)
        | ArrayStore(TypeKind type)
        | Convert(TypeKind from, TypeKind to)
        | Stack()
        | Operator()
        | Constant(ConstantDesc c)
        | TypeCheck(String type)
        | Monitor()
        | Nop()

    PseudoInstruction =
        LabelTarget(Label label)
        | LineNumber(int lineNo)
        | ExceptionCatch(Label tryStart, Label tryEnd, String exceptionType)

    DebugInfoElement* =
        LocalVariable*(int slot, Utf8Entry name, Utf8Entry descriptor, Label startScope, Label endScope)
        | LocalVariableSignature*(int slot, Utf8Entry name, Utf8Entry signature, Label startScope, Label endScope)
        | LineNumber*(int lineNo)

DebugInfoElement is a likely candidate to be suppressed when adapting bytecode
on the fly  to speed up the execution of the transform. It should be possible
to opt out of these.

`MethodParameters`, if present, are part of the Method element.

## Annotations

Models that can have annotations emits an Element that is an instance of
Runtime(In)VisibleAnnotationsAttribute.  Whether the attribute represents a
runtime visible or runtime invisible attribute is encoded in the type and is
also exposed via the isRuntimeVisible property and is .  The annotations
attribute exposes the individual annotations as a `List<Annotation>
annotations()` method. For convenience the Annotation object also indicates its
runtime visibility. The annotation interface type elements (JLS $9.6.1) of an
annotation are accessible as a List of ElementValuePair where the element name
forms the first half o the pair and the element value the second.

When building or adapting, the user can create annotaton attributes using a
factory `of(List<Annotation>)` present on both annotation attribute classes.

<!-- review note: this is the third model of annotations, the first is
described in the note below:

We explode the Annotation attributes into elements for individual annotations,
and deliver those appropriately.  We should review the implications of this
modeling.  The annotation building API has also been revamped; we should try
programming with it to see how it works. -->

## Attributes and the constant pool

The constant pool is exposed as two interfaces, ConstantPool and
ConstantPoolBuilder, where the former is for reading data from an existing
constant pool and the latter allows for adding constants when building and
adapting classes.

<!-- Note, this is not 100% accurate as many of read X methods are defined on ClassfileView -->

Building a classfile requires a ConstantPoolBuilder. When adapting a
pre-existing class file an append only builder based on the adapted class files
constant pool can be created. This alows indices into the adapted class files
pool to remain vaild and allows for bulk copying of unmodified data including
constant pool indices.

The case of mutating the constant pool itself in order to for example swap one
UTF8 structure for another UTF8 structure is TBD.

Insert attributes here ...

<!-- review note: I think we've made good progress towards a story for modeling
both attributes and constant pool.  We can expose the XEntry types, and keep
the ConcreteXEntry types hidden; moving to a read-only CP Reader and an appendable
CP Builder makes this much safer.  We've tentatively made the choice to treat
some Attributes as also being Elements; in general, attributes that carry a single
piece of data (like Signature) make sense to just say "this attribute is an element."
For attributes that might be better of exploded into individual elements
(line numbers, inner class tables, annnotations), we define elements for these
and reassemble them on write. -->

## Modules

Analog to annotations there is no ModuleModel, instead modules are moddeled as
attributes attached to a regular ClassModel. There are Attribute classes that
model the attributes necessary for module-info.classes, ModuleAttribute,
ModuleMainClassAttribute, and ModulePackagesAttribute. These Attribute classes
expose the contents of their respective attribute in the module-info class
file.

There is a ModuleBuilder to streamline the building of a module-info.class
file, either from scratch or adapting a pre-existing clss file.
