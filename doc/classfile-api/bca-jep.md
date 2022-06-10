# Bytecode API for the JDK (Incubator)

Summary
-------

Provide a JDK API for parsing, generating, and transforming Java class files.
This will initially serve as an internal replacement for ASM in the JDK, to
be later opened as a public API.

Goals
-----

Bytecode generation, parsing, and instrumentation is ubiquitous in the Java
ecosystem; many tools and libraries need to be able to process bytecode, and
frameworks often perform on-the-fly bytecode instrumentation, transformation,
and generation.   The JDK should provide an accurate, complete, up-to-date,
performant API for reading, writing and transforming Java class files.

We aim to initially replace ASM as a runtime dependency of the JDK, without
unacceptable loss of performance. As a stretch goal, it would be desirable to
also replace the internal "classreader" library used by the compiler and JDK
tools.

Eventually, a wide range of applications and frameworks should be to effectively
use this library as an alternative to ASM, cglib, or other bytecode library.

Non-Goals
---------

There are dozens of libraries that process bytecode, each with different design
goals, strengths and weaknesses.  It is not a goal to render any of these
"obsolete", to be the "one bytecode library to rule them all", or to be the
"world's fastest bytecode library."

Motivation
----------

There are a number of reasons why it makes sense for the JDK to provide a
bytecode library.

**JDK consolidation.**  The JDK itself is a significant dealer in bytecode.
Largely for historical reasons, the JDK contains at least three bytecode
libraries -- a fork of BCEL in a fork of Xalan in `java.xml`, a fork of ASM used
by `LambdaMetafactory`, `jlink`, and others, and the internal `classreader`
class file library shared by the compiler and other JDK tools.  There is also a
delay inherent in the JDK's use of ASM; the ASM version for JDK N cannot
finalize until JDK N finalizes, which means that `javac` cannot generate class
file features that are new in JDK N until JDK N+1 -- because JDK tools such as
`jlink` will not be able to process them.  JDK developers need a bytecode
library that is kept up-to-date with the JVMS.

**Version skew between frameworks and running JDK.**  Applications and
frameworks that process bytecode generally bundle their bytecode library of
choice (e.g., ASM, cglib, etc) with their application. But because new class
file features can appear in any JDK release and the rate of JDK releases
accelerated substantially after Java 9, applications and frameworks are more
frequently encountering class files that are newer than the bytecode library
they are bundled with, resulting in runtime errors (or worse, frameworks trying
to parse class file formats "from the future", and engaging in leaps of faith
that nothing too serious has changed.)  Applications and framework developers
want a bytecode processing library that they can count on to be up-to-date with
the running JDK.

**JVM evolution.**  The JVM, and class file format, are evolving much faster now
than in the early years of Java. While some evolutions are simple
(such as adding new attributes such as `NestMembers`), others are more complex;
Project Valhalla will bring new bytecodes, new field descriptors, and new
verification rules. At some point, it may be prohibitively expensive or complex
to evolve existing bytecode libraries to support these new features.

**Language improvements.**  It is perhaps an "obvious" idea to "just" take ASM
into the JDK and take responsibility for its ongoing maintenance.  But, this is
not the right choice for a native bytecode library in the JDK, for many reasons.
It's an old codebase with a lot of legacy baggage; the design priorities that
informed its architecture may not be the same as the JDK would want; it is
difficult to evolve; and the language has improved substantially since ASM was
written (lambdas, records and sealed classes, pattern matching), meaning that
what might have been the best possible API idioms in 2002 may not be ideal two
decades later.

Description
-----------

We've adopted the following goals and principles in crafting a candidate API:

**Class file entities are represented by immutable objects.**  All class file
entities, such as methods, fields, attributes, instructions, annotations, etc,
are represented by immutable objects.

**Tree-structured representation.**  A class file has a tree structure; a class
has some metadata properties (name, supertype, etc), and a variable number of
fields, methods, and attributes. Fields and methods themselves have metadata
properties, and further contain attributes, including the `Code` attribute, and
the code attribute further contains instructions, exception handlers, etc.  The
API should reflect this tree-structured organization.

**User-driven navigation.**  The path we take through the class file tree is
driven by user choices.  If the user cares only about annotations on fields, we
should only have to parse as far down as the annotation attributes inside the
`field_info`; we should't have to look into any of the class attributes or the
bodies of methods.  Users should be able to deal with compound entities (such as
methods) either as a single unit or break them into streams of their constituent
elements, as desired.

**Laziness.**  User-driven navigation enables significant efficiencies, such as
not parsing any more of the class file than we need to satisfy the user's needs.
If the user is not going to dive into the contents of a method, then we need not
parse any more of the `method_info` structure than is needed to figure out how
where the next class file element starts. We can lazily inflate (and cache) the
full representation when the user has asked for it.

**Streaming plus materialized.**  Like ASM, we want to support both a streaming
and materialized view of the class file. The streaming view is suitable for the
majority of use cases; the materialized view is more general but (in the case of
ASM) much more expensive. Unlike ASM, though, we can provide the random access
that a materialized view provides, but in a far less expensive way through
laziness (enabled by immutability), and we can align the streaming and
materialized views so that they use a common vocabulary and that both can be
used in coordination, as is convenient for the use case.

**Emergent transformation.**  If the class file reading and writing APIs are
sufficiently aligned, then transformation can be an emergent property that does
not require its own special access mode.  (ASM achieves this by using a common
Visitor structure between readers and writers.)  If classes, methods, fields,
and code bodies are readable and writable as streams of elements, then
transformation can be viewed as a simple flat-map operation on this stream,
described by lambdas.

**Detail hiding.**  Many parts of the class file (constant pool, bootstrap
method table, stack maps, code-specific attributes such as line number tables)
are derived from other parts of the class file. It makes no sense to ask the
user to construct these directly; this makes extra work for the user and greater
chance of error. For attributes and class file entities that are tightly coupled
to other class file entities, we can let the library generate these based on
the methods, fields, and instructions added to the class file.

**Models, elements, builders, and transforms.**  We construct the API from four
key abstractions.  A _model_ is an immutable description of a composite entity
such as a class, field, or method, which contain simpler entities (classes
contain methods and fields; method bodies contain instructions.)  Models can be
dealt with as a whole, or can be decomposed into a stream of immutable
_elements_, which represent the parts of the complex entity.  (Some entities are
both models in their own right, and are also elements of larger models --
methods are elements of classes.)  Each kind of entity described by a model has
a corresponding _builder_, which has both specific building methods (e.g.,
`withMethod`) as well as acting as a `Consumer` of the appropriate element type.
Finally, a _transform_ represents a function that takes an element and a builder
and mediates how (if at all) that element is transformed into other elements.

**Lean into the language.**  In 2002, the Visitor approach used by ASM seemed
clever, and was surely more pleasant to use than what came before. However, Java
has improved tremendously since then; we now have lambdas, records and pattern
matching, and the JDK now has a standardized API for describing class file
constants (`java.lang.constant`). We can use these to design an API that is more
flexible and pleasant to use, less verbose, and less error-prone.  (Algebraic
data types constructed from records and sealed types are also easier to evolve
than visitors.)

#### Examples

The examples in this section incorporate early sketches of a possible API; they
are intended as motivating examples rather than as exposition of the final API.

**Writing.**  Consider the following snippet of Java code:

```
void fooBar(boolean z, int x) {
    if (z)
        foo(x);
    else
        bar(x);
}
```

In ASM, we'd generate this method as follows:

```
ClassWriter classWriter = ...
MethodVisitor mv = classWriter.visitMethod(0, "fooBar", "(ZI)V", null, null);
mv.visitCode();
mv.visitVarInsn(ILOAD, 1);
Label label1 = new Label();
mv.visitJumpInsn(IFEQ, label1);
mv.visitVarInsn(ALOAD, 0);
mv.visitVarInsn(ILOAD, 2);
mv.visitMethodInsn(INVOKEVIRTUAL, "Foo", "foo", "(I)V", false);
Label label2 = new Label();
mv.visitJumpInsn(GOTO, label2);
mv.visitLabel(label1);
mv.visitVarInsn(ALOAD, 0);
mv.visitVarInsn(ILOAD, 2);
mv.visitMethodInsn(INVOKEVIRTUAL, "Foo", "bar", "(I)V", false);
mv.visitLabel(label2);
mv.visitInsn(RETURN);
mv.visitEnd();
```

The `MethodVisitor` in ASM doubles as both a visitor and a builder.  Clients can
create a `ClassWriter` directly, and then can ask the `ClassWriter` for a
`MethodVisitor`.  However, there is value in inverting this API idiom, where,
instead of the client _creating_ a builder with a constructor or factory, it
provides a lambda which _accepts_ a builder.

```
ClassBuilder classBuilder = ...
classBuilder.withMethod("fooBar", "(ZI)V", flags,
                        mb -> mb.withCode(codeBuilder -> {
    Label label1 = new Label();
    Label label2 = new Label();
    codeBuilder.iload(1)
        .branch(IFEQ, label1)
        .aload(0)
        .iload(2)
        .invokevirtual("Foo", "foo", "(I)V")
        .branch(GOTO, label2)
        .labelTarget(label1)
        .aload(0)
        .iload(2)
        .invokevirtual("Foo", "bar", "(I)V")
        .labelTarget(label2);
        .returnFromMethod(VOID);
});
```

This is more specific and transparent (the builder has lots of conveniences
methods like `aload(n)`), but not yet any more concise or higher-level.  But
there is already a powerful hidden benefit: by capturing the sequence of
operations in a lambda, we get the possibility of _replay_, which enables the
library to do work that previously the client had to do.  For example, branch
offsets can be short or long.  If clients generate instructions imperatively,
they have to know how far the branch offset is at the time each branch is
generated, which is complex and error-prone.  But if the client provides a
lambda that takes a builder, the library can optimistically try to generate the
method with short offsets, and if this fails, discard the generated state and
_re-invoke the lambda_ with different code generation parameters.

Decoupling builders from visitation also lets us provide higher-level
conveniences to manage block scoping, allowing us to eliminate the manual label
management and branching:

```
CodeBuilder classBuilder = ...
classBuilder.withMethod("fooBar", "(ZI)V", flags,
                        methodBuilder -> methodBuilder.withCode(codeBuilder -> {
    codeBuilder.iload(1)
               .ifThenElse(
                   b1 -> b1.aload(0)
                           .iload(2)
                           .invokevirtual("Foo", "foo", "(I)V"),
                   b2 -> b2.aload(0)
                           .iload(2)
                           .invokevirtual("Foo", "bar", "(I)V")
               .returnFromMethod(VOID);
});
```

Because the block scoping is managed by the library, we didn't have to generate
labels or branch instructions -- the library can insert them for us.  Similarly,
the library can optionally manage block-scoped allocation of local variables,
freeing clients of the bookkeeping for local variable slots as well.

#### Reading, with pattern matching

ASM's streaming view of the class file is visitor-based. Visitors are often
characterized as a library workaround for the lack of pattern matching in the
language. Now that Java has pattern matching, we can express things more
directly. For example, if we want to traverse a `Code` attribute and collect
dependencies for a class dependency graph, we can simply iterate through the
instructions (the elements of the `CodeModel`) and match on the ones we find
interesting:

```
CodeModel code = ...
HashSet<ClassDesc> deps = new HashSet<>();
for (CodeElement e : code) {
    switch (e) {
        case FieldAccess f -> deps.add(f.owner());
        case Invoke i      -> deps.add(i.owner());
        // similar for instanceof, cast, etc
    }
}
```

#### Transformation

The reading and writing APIs must line up so that transformation is seamless.
The reading example above traversed a sequence of `CodeElement`, letting clients
match against the individual elements; if we make the builder accept these
`CodeElement`s, then typical transformation idioms line up fairly naturally.

If we want to process a class file and keep everything unchanged except for
removing methods whose names start with "debug", we would get a `ClassModel`,
create a `ClassBuilder`, iterate the elements of the original `ClassModel`, and
pass through all of them to the builder, except the methods we want to drop:

```
ClassModel classModel = ClassModel.of(bytes);
byte[] newBytes = Classfile.build(classModel.flags(), classModel.name(),
        classBuilder -> {
            for (ClassElement ce : classModel) {
                if (!(ce instanceof MethodModel mm
                          && mm.nameString().startsWith("debug"))
                    classBuilder.with(ce);
            }
        });
```

Transforming method bodies is slightly more complicated, as we have to explode
classes into their parts (including methods), methods into their parts (
including the code attribute), and the code attribute into instructions. The
following transformation swaps out invocations of methods on class `Foo` to
class `Bar`:

```
ClassModel classModel = ClassModel.of(bytes);
byte[] newBytes = ClassBuilder.of(classModel.flags(), classModel.name(),
        classBuilder -> {
            for (ClassElement ce : classModel) {
                if (ce instanceof MethodModel mm) {
                    classBuilder.withMethod(mm.name(), mm.descriptor(),
                                            mm.flags(), methodBuilder -> {
                        for (MethodElement me : mm) {
                            if (me instanceof CodeModel codeModel) {
                                methodBuilder.withCode(codeBuilder -> {
                                    for (CodeElement e : codeModel) {
                                        switch (e) {
                                            case Invoke i && i.owner().asInternal("Foo")) ->
                                                codeBuilder.invoke(i.opcode(), ClassDesc.of("Bar"), i.name(), i.type());
                                            default -> codeBuilder.with(e);
                                        }
                                });
                            }
                            else
                                methodBuilder.with(me);
                        }
                    });
                }
                else
                    classBuilder.with(ce);
            }
        });
```

We can see that the process of exploding and recreating a transformed entity
involves some boilerplate, which is repeated at multiple levels. We can simplify
this through the introduction of _transforms_ and builder methods which will
adapt an entity using a transform. A transform accepts a builder and an element
and lets us replace that element with zero or more other elements.
The `adaptXxx` methods copy the relevant metadata from an input model (names,
flags, etc) and then process its elements using the supplied transform.
The `adaptXxx` methods manage the iteration and lets the user capture the
essential transformation logic into a lambda. The "swap Foo methods for Bar
methods" can be rewritten as:

```
ClassModel classModel = ClassModel.of(bytes);
byte[] newBytes = classModel.adapt((classBuilder, ce) -> {
    if (ce instanceof MethodModel mm) {
        classBuilder.adapMethod(mm, (methodBuilder, me)-> {
            if (me instanceof CodeModel cm) {
                methodBuilder.adaptCode(cm, (codeBuilder, e) -> {
                    switch (e) {
                        case Invoke i && i.owner().equals("Foo")) ->
                            codeBuilder.invoke(i.opcode(), ClassDesc.of("Bar"), i.name(), i.type());
                        default -> codeBuilder.with(e);
                    }
                });
            }
            else
                methodBuilder.with(me);
        });
    }
    else
        classBuilder.with(ce);
});
```

We can factor out the instruction-specific activity directly as a
`CodeTransform`:

```
CodeTransform codeTransform = (codeBuilder, e) -> {
    switch (e) {
        case Invoke i && i.owner().equals("Foo")) ->
            codeBuilder.invoke(i.opcode(), ClassDesc.of("Bar"), i.name(), i.type());
        default -> codeBuilder.accept(e);
    }
};
```

and then we can _lift_ this transform on code elements into a transform on
method elements, and further lift that into a transform on class elements:

```
MethodTransform methodTransform = MethodTransform.adaptingCode(codeTransform);
ClassTransform classTransform = ClassTransform.adaptingMethods(methodTransform);
```

at which point our example becomes just:

```
byte[] newBytes = ClassModel.of(bytes).adapt(classTransform);
```

Alternatives
------------

We could continue to use ASM for bytecode generation and transformation in the
JDK.  ALternately, we could move the "classreader" library to `java.base` and
use that in preference to ASM.

Testing
-------

As this library will have a large surface area and must generate classes in
conformance with the JVMS, significant quality and conformance testing will be
required.  Further, to the degree that we replace existing uses of ASM with the
new library, we will want to be able to effectively compare the results using
both generation techniques to detect regressions, and do extensive performance
testing to detect and avoid performance regressions.

jep11: https://openjdk.java.net/jeps/11
