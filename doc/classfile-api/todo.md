# To Do

## Attributes

We're missing unbound implementations for at least:
 - MethodParameters
 - XxxTypeAnnotations

We don't currently enforce the `validSince` constraint.

We deliver unrecognized attributes as UnknownAttribute, but should silently drop UnknownAttribute
presented to builders since we cannot vouch for their integrity (they may contain
offsets that are not valid.)  We should do the same with *bound* Type annotations attributes.

For some attributes which represent lists of things, such as the annotation attributes,
we may want to provide the option to expand the attribute into elements.  For each
such attribute or attribute group:
 - Add an option for EXPAND_XXX (e.g., EXPAND_ANNOTATIONS)
 - Make the appropriate list entry implement the appropriate XxxElement types (e.g.,
   Annotation implements MethodElement, ClassElement, etc)
 - It is an error to say `with(Annotation)` when we're not in EXPAND_ANNOTATION mode
 - It is an error to say with(AnnotationAttribute) when we *are* in EXPAND_ANNOTATION mode
 - At start of iteration, parse the appropriate attribute and generate elements
 - Suppress the attribute from the element iteration
 - Gather the appropriate elements into a list
 - Set up an end handler to take the gathered elements and generate the attribute

We'll start with annotations and see how we like it.

## CodeModel

We do yet not generate elements for LineNumber (gated by SKIP_LINE_NUMBER).
While the LineNumberTable attribute is an unsorted sequence of (startPC, lineNo)
pairs, I think we should consider having a CodeElement for

    LineNumber(int lineNo)

and delivering inline, like a label, but which carries its own state.  When
line numbers are in play, we can inflate a table lineNo[bci], walk the table with
something like

    for (startPc, lineNo in LineNumberTable
        lineNo[startPc] = lineNo

and then deliver lineNo elements (if not being skipped) in much the same way as
we do labels.  On writing, we can optimize the generation by writing
(startPc, lineNo) pairs to a BufWriter, skipping the intermediate format.

I would expect we'd keep a static cache of LineNumber elements for each line number
less than some threshold.

## CodeBuilder

#### Replay

We optimistically generate branches with short offsets. At some point, we will
discover that these are not long enough. When we do, we should re-run the lambda
with different code-gen parameters (always using long offsets) transparently to
the user.

#### Local variable allocation

CodeBuilder should provide support for allocating local slots. It can keep track
of the high water mark; a fresh DirectCodeBuilder initializes this from
maxLocals. Local allocation should respect block boundaries; variables slots
dispensed by the builder for a block go away when the block ends.

    int allocateLocal(TypeKind typeKind)

We can't prevent users from interfering, but we can tell users to either use
allocated locals exclusively, or do their own slot management, but not both.

## CodeModel

CodeModel should be able to yield the BCI of an instruction.  We can do this
by extending `Iterable` and covariantly overriding `elements` in `CodeModel`:

    interface CodeIterable extends Iterable<CodeElement> {
        CodeIterator iterator();
    }

    interface CodeIterator extends Iterator<CodeElement> {
        int currentBci();
    }

    CodeIterable elements();

The "fake" code models can throw on `currentBci`.

## RecordComponent

Currently, we're missing a lot of the artifacts for RecordComponent, including
the builder.  We also don't yet treat the `Record` attribute as a model
(as we do with Code attribute.)  Also there are no record classes in the
mini-corpus used for `make test`.

## Type annotations

In addition to getting all the type annotation classes going, we need to filter
type annotation attributes when adapting, because we have no way to know they
are valid.  I think the way to do this is to silently drop R{I,V}TA attributes
on write when their CP doesn't match the class being built; this will allow
users to generate them with the builder, and to do real adaptation on them,
but not pass them through unmodified when adapting.

## General

Review toString implementations; ClassModel::toString should give us pretty
much all the content that `javap` does.

## Tests

#### Custom attributes

- Implement a custom attribute (implement AttributeMapper, extend
  CustomAttribute).
- Create a classfile with the attribute.
- Read the classfile with the mapper present, ensure that the attribute is
  delivered properly.
- Read the classfile with the mapper absent, ensure than an UnknownAttribute is
  delivered.
- Read the classfile with the mapper absent and Option.SKIP_UNKNOWN_ATTRIBUTE
  set, ensure that no attribute is delivered.
- Adapt the classfile with the mapper present, ensure that the custom attribute
  is present in the adapted output.
- Adapt the classfile with the mapper absent, ensure that the UnknownAttribute
  is _not_ present in the adapted outout.


