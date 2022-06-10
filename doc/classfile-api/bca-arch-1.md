# Towards a bytecode API for the JDK

#### Brian Goetz, August 2021

Bytecode generation, parsing, and instrumentation is ubiquitous in the Java
ecosystem.  Compilers for many languages generate and consume Java bytecode,
many tools and libraries need to be able to process bytecode, and frameworks
often perform on-the-fly bytecode instrumentation, transformation, and
generation.

The JDK itself is obviously one of the biggest dealers in bytecode; the `javac`
compiler consumes and produces it, tools such as `javap` help us debug it,
language features such as lambdas (and VM features such as method handles) are
implemented with the aid of dynamic bytecode generation, and libraries such as
dynamic proxies and reflection operate by code generation as well.

The Java ecosystem has many libraries for bytecode generation, parsing, and
instrumentation (ASM, BCEL, cglib, Javassist, gnu.bytecode, just to name a few),
each with different strengths and weaknesses.  And (largely for historical
reasons) the JDK itself contains at least three -- a fork of BCEL in a fork of
Xalan in `java.xml`, a fork of ASM used by `LambdaMetafactory` and others, and
the classfile library shared by the compiler and other JDK tools.  (Plus there
are specialized programs to generate _invalid_ classfiles for testing purposes.)

While ASM is a perfectly reasonable tool for bytecode generation, the six-month
release cadence makes incorporating ASM into the JDK more difficult.  ASM can't
release a version for JDK N until JDK N finalizes; that means that JDK N can at
best incorporate ASM N-1.  This, in turn, makes it harder to use new classfile
features in the release they are delivered, since the version of ASM in the JDK
may not be able to parse them.

Additionally, as new classfile features may show up every six months,
applications or libraries that depend on a given version of a bytecode parsing
library are more likely to be surprised when they encounter a classfile version
the library does not understand.  (One might be tempted to think that the
problem here is that the JDK bumps the classfile version for every JDK version,
even when there are no (obvious) changes in the classfile format.  However,
there are many other sources of classfile versioning dependencies other than
"added a new bytecode or constant pool form", such as dependencies between
compiler-generated code and JDK methods (such as `Iterable` or
`LambdaMetafactory`), new classfile attributes that affect semantics (such as
`NestMembers`), or subtle changes in the combinations of flags allowed (such as
`ACC_ABSTRACT` not being required on interface methods as of JDK 8.)  Taken
together, the majority of JDK releases has had at least one of these sorts of
dependency that legitimately warrants a version bump.)

Now that the JDK has feature releases every six months, and any release may
contain classfile changes, the time has come for the JDK to include a bytecode
generation, parsing, and adaptation library that is guaranteed to be up to date
with the classfile format as of the JDK in use.

## Why not "just" standardize on ASM?

An obvious question to ask at this point is: why not "just" bring ASM into the
JDK and keep it up to date with the latest classfile format?

ASM is good, but it's not the right choice for a native bytecode library in the
JDK.  (But, there's a lot we can learn from what it does well, and not so well.)
The reasons are many: it's an old codebase with a lot of legacy baggage; the
design priorities that informed its architecture may not be the same as the JDK
would want; it is difficult to evolve; and the language has improved
substantially since ASM was written, meaning that what might have been the best
possible API idioms in 2002 may not be ideal two decades later.

All that said, ASM gets a lot of things right:

**Both streaming and materialized views.**  ASM's division between a streaming
(`ClassWriter`) and materialized (`ClassNode`) is a practical one.  The
streaming API is easy to use and efficient, and suffices for the majority of use
cases.  The materialized API is considerably more expensive, but admits more
extensive analysis, and covers the rest.  The two can be combined to a degree;
one can "play" a materialized node into a visitor, or "collect" from a visitor
into a materialized node, but one does have to choose one or the other when
accessing the classfile contents.

**Emergent adaptation.**  While bytecode APIs are generally defined in terms of
reading and writing, the most common use case is actually to combine reading and
writing into _adaptation_.  ASMs choice to match the shape of the reading and
writing APIs pays off in a big way.  Both the streaming reading and writing
interfaces are defined in terms of common visitors, with adapter classes that
allow you to pass any unhandled events through to another visitor (usually
bottoming out in a `ClassWriter`).  This enables easy adaptation; you can
"override" handling of a small number of events, play the classfile events
through your adapter, intercept a few, and pass most of the rest to the
downstream writer.  This makes it easy to do things like filter out members or
attributes or make localized bytecode edits (e.g., swap out calls to `m()` for
calls to `n()`).

The winning design move here is that the adaptation is _emergent_; because the
read and write APIs match up in a clean way (with adapters provided), one can
combine the two easily, and can even chain together adapters so that various
transforms can be expressed separately and composed later.

**Hiding the details.**  While we generally think of classfiles as containing
fields and methods, and methods containing a sequence of bytecodes, there are
many ancillary structures that must be kept in sync with the bytecodes.  At the
class level, there is the constant pool and related attributes such as the
bootstrap methods table; at the method level, there are stack maps, local
variable tables, line number tables, which are all logically part of the `Code`
attribute.  Rather than asking clients to construct these directly (which is
tedious and error-prone), these are largely derived from adding fields, methods,
and bytecodes to the classfile.

Hiding the details is generally a strength, as it makes the API easier to use
and abstracts away low-level detail that is often merely an opportunity for
error.  However, sometimes we want to be able to access these lower-level
details directly -- such as via tools that help us debug classfiles (like
`javap`) or writing a bytecode interpreter or verifier.  So ASM gets the primary
view right here, but we probably would want to additionally expose these (at
least for reading) if you ask nicely.  (Going one step further, it is sometimes
desirable to generate _invalid_ classfiles, such as for testing compilers or
JVMs, but this comes with its own set of challenges, and may well best be left
to specialized tools.)

ASM also has a few areas where we would like to do better.

**Visitors.**  In 2002, the Visitor approach used by ASM seemed clever, and was
surely more pleasant to use than what came before.  However, Java has improved
tremendously since then; we now have lambdas, records and pattern matching, and
the JDK now has a standardized API for describing classfile constants
(`java.lang.constant`), so we should carefully evaluate whether this pattern
still carries its weight.

The experience of ASM has shown that visitors make the API extremely hard to
evolve, because it is very hard to expunge design errors once an interface has
been published and subclasses written.  Further, visitors are intrinsically a
workaround for the lack of pattern matching in the language; pattern matching is
more flexible (not tied to rigid type abstractions), more concise, and more
direct.  (Every ASM user must go through an initial learning curve understanding
how visitors work _in ASM_ before they can use the API effectively; pattern
matching is part of the language.)

We should seek to identify API idioms that match the benefits of visitors (e.g.,
emergent filtering, easy composition) without suffering its drawbacks (verbose,
rigid, hard to evolve.)

**Attributes.**  ASM's supports for attributes is largely an afterthought.
Because modifying a classfile involves writing a completely new constant pool,
opaque attributes cannot be safely propagated (as they may have references to
the constant pool, or to bytecode indexes, which may have changed).  Attributes
should have a more prominent representation in the API, and the API should be
aware of (and force the user to make a choice on) the interaction of unknown
attributes and constant pool rebuilding, code attribute rebuilding, etc.

## Some goals

A significant customer for this API is _the JDK itself_.  The JDK needs a
bytecode library that stays up-to-date with the classfile format, and that can
replace all current uses of ASM (and others) in the JDK, which includes
classfile analysis, transformation, and generation.  ASM doesn't meet that
requirement; even in the best case, there is an inherent one-version lag before
bytecode support is available.  Additionally, if we had a flexible, efficient,
up-to-date bytecode generation library, we can consider replacing the compiler
back-end and eliminating duplication, as well as in other tools (such as
`javap`.)

Of course, if there were such a library, it would be silly to keep it for
ourselves.  It is clear that there are many external users that would greatly
benefit from such an API, but only if the API is stable enough to be counted on,
and good enough to meet all the expected use cases.  In order to validate the
API before its API becomes permanent, it will likely go through one or more
rounds of incubation or preview.

If you read the marketing material for various bytecode libraries, nearly every
one of them describes themselves as "lightweight" or "high-performance".  Of
course, these terms don't mean anything, but the point is clear: providers of
bytecode libraries believe that users of bytecode libraries care about
performance.

ASM is obsessive about performance -- sometimes to the degree of putting
performance over usability or maintainability.  This is a reasonable choice for
ASM, and performance is important here too, but it is not the _most important_
consideration.  Instead, we aim to build a complete, reliable, maintainable,
evolvable, easy-to-use bytecode library that tracks the classfile format, which
performs "well enough."

#### Motivating examples

Since ASM was designed, the language has improved considerably, and this enables
some useful new API idioms.  The examples in this section will illustrate how we
can apply these features to arrive at a pleasant API for writing, reading, and
adapting bytecode.  These are intended solely as motivating examples rather than
as exposition of any concrete API; a more detailed exposition of the API design
approach will be covered in a separate document.

#### Writing, with builders and lambdas

Consider the following snippet of Java code:

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
`MethodVisitor`.  But it may be desirable to restack this workflow, where,
instead of the client _creating_ a builder, let it provide a lambda which
_accepts_ a builder:

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
        .labelTargeT(label1)
        .aload(0)
        .iload(2)
        .invokevirtual("Foo", "bar", "(I)V")
        .labelTarget(label2);
        .returnFromMethod(VOID);
});
```

While this doesn't look all that different, there's a powerful hidden benefit
here: by capturing the sequence of operations in a lambda, we get the
possibility of _replay_, which enables the library to do work that previously
the client had to do.  For example, branch offsets can be short or long.  If the
client generates instructions imperatively, they have to know how far the branch
offset is at the time they generate each branch.  But if the client provides a
lambda that takes a builder, the library can optimistically try with short
offsets, and if anything fails, discard the generated state and _re-invoke the
lambda_ with different code generation parameters.

Builders can also provide higher-level conveniences to manage block scoping,
allowing us to eliminate the manual label management and branching:

```
CodeBuilder classBuilder = ...
classBuilder.withMethod("fooBar", "(ZI)V", flags,
                        mb -> mb.withCode(cb -> {
    cb.iload(1)
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
the library can manage block-scoped allocation of local variables, freeing
clients of the bookkeeping for local variable slots as well.

#### Reading, with pattern matching

ASM's streaming view of the classfile is visitor-based.  Java now has  _pattern
matching_, which obviates the need for most uses of the visitor pattern.  For
example, if we want to traverse a `Code` attribute and collect dependencies for
a class dependency graph, we can simply iterate the instructions and match on
the ones we find interesting:

```
CodeModel code = ...
HashSet<String> deps = new HashSet<>();
for (CodeElement e : code.elements()) {
    switch (e) {
        case FieldAccess f -> deps.add(f.owner());
        case Invoke i      -> deps.add(i.owner());
        // similar for instanceof, cast, etc
    }
}
```

#### Adapting

The reading and writing APIs must line up so that adaptation is seamless.  The
reading example above traversed a sequence of `CodeElement`, letting clients
match against the individual elements; if we can  make the builder accept these
`CodeElements`, then typical adaptation idioms line up fairly naturally.  For
example, if we wanted to swap out invocations of methods on class `Foo` to class
`Bar`:

```
MethodModel method = ...
classBuilder.withMethod(method.name(), method.type(), method.flags(),
                        mb -> mb.withCode(cb -> {
    for (CodeElement e : method.code().elements()) {
        switch (e) {
            case Invoke i && i.owner().equals("Foo")) ->
                cb.withInvoke(i.opcode(), "Bar", i.name(), i.type());
            default -> cb.accept(codeElement);
        }
    }
});
```

Here, any elements of the body we don't want to change are passed through to the
builder, and any invocations of methods in `Foo` are rewritten to invocations of
the corresponding methods in `Bar`.
