/*
 * Copyright (c) 2015, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/// The Doclet API provides an environment which, in conjunction with
/// the Language Model API and Compiler Tree API, allows clients
/// to inspect the source-level structures of programs and
/// libraries, including API comments embedded in the source.
///
/// The [`standard doclet`][StandardDoclet] can be used to
/// generate HTML-formatted documentation. It supports user-defined
/// [`taglets`][Taglet], which can be used to generate customized
/// output for user-defined tags in documentation comments.
///
/// _**Note:** The declarations in this package supersede those
/// in the older package `com.sun.javadoc`. For details on the
/// mapping of old types to new types, see the
/// [Migration Guide][##migration]_.
///
/// Doclets are invoked by javadoc and this API can be used to write out
/// program information to files.  For example, the standard doclet is
/// invoked by default, to generate HTML documentation.
///
/// The invocation is defined by the interface [jdk.javadoc.doclet.Doclet]
/// -- the [`run`][jdk.javadoc.doclet.Doclet#run(DocletEnvironment)] interface
/// method, defines the entry point.
/// {@snippet id="entry-point" lang=java :
///    public boolean run(DocletEnvironment environment) // @highlight substring="run"
/// }
/// The [jdk.javadoc.doclet.DocletEnvironment] instance holds the
/// environment that the doclet will be initialized with. From this environment
/// all other information can be extracted, in the form of
/// [`elements`][javax.lang.model.element.Element]. One can further use the APIs and utilities
/// described by [`Language Model API`][javax.lang.model] to query Elements and Types.
///
/// # Terminology
///
/// <dl>
///   <dt><a id="selected"></a>Selected</dt>
///     <dd>An element is considered to be <em>selected</em>, if the
///         <em>selection controls</em> <a href="#options-heading">allow</a> it
///         to be documented. (Note that synthetic elements are never
///         selected.)
///    </dd>
///
///   <dt><a id="specified"></a>Specified</dt>
///   <dd>The set of elements specified by the user are considered to be <em>specified
///       elements</em>. Specified elements provide the starting points
///       for determining the <em>included elements</em> to be documented.
///   </dd>
///
///   <dt><a id="included"></a>Included</dt>
///   <dd>An element is considered to be <em>included</em>, if it is <em>selected</em> and any of the following is true:
///     <ul>
///       <li>the element is <em>specified</em>, or
///       <li>the element contains a <em>specified</em> element, or
///       <li>the element is enclosed in a <em>specified</em> element.
///     </ul>
///     Included elements will be documented.
///   </dd>
///
/// </dl>
///
/// # Options
///
/// Javadoc _selection control_ can be specified with these options
/// as follows:
///   - `--show-members:value` and `--show-types:value` can
///     be used to filter the members, with the following values:
///      - public    -- considers only public elements
///      - protected -- considers public and protected elements
///      - package   -- considers public, protected and package private elements
///      - private   -- considers all elements
///   - `--show-packages:value` "exported" or "all" can be used
///     to consider only exported packages or all packages within a module.
///   - `--show-module-contents:value` can be used to specify the level at
///     module declarations could be documented. A value of "api" indicates API
///     level documentation, and "all" indicates detailed documentation.
///
/// The following options can be used to specify the elements to be documented:
///
///   - `--module` documents the specified modules.
///   - `--expand-requires:value` expand the set of modules to be documented
///        by including some or all of the modules dependencies. The value may be
///        one of:
///      - transitive -- each module specified explicitly on the command line is
///        expanded to include the closure of its transitive dependencies
///      - all    -- each module specified explicitly on the command line
///        is expanded to include the closure of its transitive dependencies,
///        and also all of its direct dependencies
///
///   By default, only the specified modules will be considered, without expansion
///   of the module dependencies.
///
///   - `packagenames` can be used to specify packages.
///   - `-subpackages` can be used to recursively load packages.
///   - `-exclude` can be used exclude package directories.
///   - `sourcefilenames` can be used to specify source file names.
///
/// ## Interactions with older options.
///
/// The new `--show-*` options provide a more detailed replacement
/// for the older options `-public`, `-protected`, `-package`, `-private`.
/// Alternatively, the older options can continue to be used as shorter
/// forms for combinations of the new options, as described below:
///
/// <table class="striped">
///   <caption>Short form options mapping</caption>
///   <thead>
///       <tr>   <th rowspan="2" scope="col" style="vertical-align:top">
///                      Older option
///              <th colspan="5" scope="col" style="border-bottom: 1px solid black">
///                      Equivalent to these values with the new option
///       <tr>   <th scope="col">{@code --show-members}
///              <th scope="col">{@code --show-types}
///              <th scope="col">{@code --show-packages}
///              <th scope="col">{@code --show-module-contents}
///   </thead>
///   <tbody>
///       <tr>   <th scope="row">{@code -public}
///              <td>public
///              <td>public
///              <td>exported
///              <td>api
///       <tr>   <th scope="row">{@code -protected}
///              <td>protected
///              <td>protected
///              <td>exported
///              <td>api
///       <tr>   <th scope="row">{@code -package}
///              <td>package
///              <td>package
///              <td>all
///              <td>all
///       <tr>   <th scope="row">{@code -private}
///              <td>private
///              <td>private
///              <td>all
///              <td>all
///   </tbody>
/// </table>
///
/// <a id="qualified"></a>
/// A _qualified_ element name is one that has its package
/// name prepended to it, such as `java.lang.String`.  A non-qualified
/// name has no package name, such as `String`.
///
/// # Example
///
/// The following is an example doclet that displays information of a class
/// and its members, supporting an option.
///
/// {@snippet lang=java id="Example.java" :
/// // @replace region=imports replacement=" // Note: imports deleted for clarity"
/// import com.sun.source.doctree.DocCommentTree;
/// import com.sun.source.util.DocTrees;
/// import jdk.javadoc.doclet.Doclet;
/// import jdk.javadoc.doclet.DocletEnvironment;
/// import jdk.javadoc.doclet.Reporter;
///
/// import javax.lang.model.SourceVersion;
/// import javax.lang.model.element.Element;
/// import javax.lang.model.element.TypeElement;
/// import javax.lang.model.util.ElementFilter;
/// import javax.tools.Diagnostic.Kind;
/// import java.io.IOException;
/// import java.io.PrintWriter;
/// import java.util.List;
/// import java.util.Locale;
/// import java.util.Set;
/// // @end
///
///
/// public class Example implements Doclet {
///     private Reporter reporter;
///     private PrintWriter stdout;
///
///     @Override
///     public void init(Locale locale, Reporter reporter) {
///         reporter.print(Kind.NOTE, "Doclet using locale: " + locale);
///         this.reporter = reporter;
///         stdout = reporter.getStandardWriter();
///     }
///
///     public void printElement(DocTrees trees, Element e) {
///         DocCommentTree docCommentTree = trees.getDocCommentTree(e);
///         if (docCommentTree != null) {
///             stdout.println("Element (" + e.getKind() + ": "
///                     + e + ") has the following comments:");
///             stdout.println("Entire body: " + docCommentTree.getFullBody());
///             stdout.println("Block tags: " + docCommentTree.getBlockTags());
///         }
///     }
///
///     @Override
///     public boolean run(DocletEnvironment docEnv) {
///         reporter.print(Kind.NOTE, "overviewFile: " + overviewFile);
///
///         // get the DocTrees utility class to access document comments
///         DocTrees docTrees = docEnv.getDocTrees();
///
///         // location of an element in the same directory as overview.html
///         try {
///             Element e = ElementFilter.typesIn(docEnv.getSpecifiedElements()).iterator().next();
///             DocCommentTree docCommentTree
///                     = docTrees.getDocCommentTree(e, overviewFile);
///             if (docCommentTree != null) {
///                 stdout.println("Overview html: " + docCommentTree.getFullBody());
///             }
///         } catch (IOException missing) {
///             reporter.print(Kind.ERROR, "No overview.html found.");
///         }
///
///         for (TypeElement t : ElementFilter.typesIn(docEnv.getIncludedElements())) {
///             stdout.println(t.getKind() + ":" + t);
///             for (Element e : t.getEnclosedElements()) {
///                 printElement(docTrees, e);
///             }
///         }
///         return true;
///     }
///
///     @Override
///     public String getName() {
///         return "Example";
///     }
///
///     private String overviewFile;
///
///     @Override
///     public Set<? extends Option> getSupportedOptions() {
///         Option[] options = {
///             new Option() {
///                 private final List<String> someOption = List.of(
///                         "--overview-file",
///                         "-overviewfile",
///                         "-o"
///                 );
///
///                 @Override
///                 public int getArgumentCount() {
///                     return 1;
///                 }
///
///                 @Override
///                 public String getDescription() {
///                     return "an option with aliases";
///                 }
///
///                 @Override
///                 public Option.Kind getKind() {
///                     return Option.Kind.STANDARD;
///                 }
///
///                 @Override
///                 public List<String> getNames() {
///                     return someOption;
///                 }
///
///                 @Override
///                 public String getParameters() {
///                     return "file";
///                 }
///
///                 @Override
///                 public boolean process(String opt, List<String> arguments) {
///                     overviewFile = arguments.get(0);
///                     return true;
///                 }
///             }
///         };
///
///         return Set.of(options);
///     }
///
///     @Override
///     public SourceVersion getSupportedSourceVersion() {
///         // support the latest release
///         return SourceVersion.latest();
///     }
/// }
/// }
///
/// This doclet can be invoked with a command line, such as:
/// {@snippet id="run-doclet":
///     javadoc -docletpath doclet-classes \     // @highlight substring="doclet-classes " type=italic
///       -doclet Example \
///       --overview-file overview.html \
///       --source-path source-location \        // @highlight region substring="source-location" type=italic
///       source-location/Example.java           // @end
///     }
///
/// # Migration Guide
///
/// Many of the types in the old `com.sun.javadoc` API do not have equivalents in this
/// package. Instead, types in the `javax.lang.model` and `com.sun.source` APIs
/// are used instead.
///
/// The following table gives a guide to the mapping from old types to their replacements.
/// In some cases, there is no direct equivalent.
///
/// <table class="striped">
///   <caption>Guide for mapping old types to new types</caption>
///   <thead>
///     <tr><th scope="col">Old Type<th scope="col">New Type
///   </thead>
///   <tbody style="text-align:left">
///     <tr><th scope="row">{@code AnnotatedType}            <td>{@link javax.lang.model.type.TypeMirror javax.lang.model.type.TypeMirror}
///     <tr><th scope="row">{@code AnnotationDesc}           <td>{@link javax.lang.model.element.AnnotationMirror javax.lang.model.element.AnnotationMirror}
///     <tr><th scope="row">{@code AnnotationDesc.ElementValuePair}<td>{@link javax.lang.model.element.AnnotationValue javax.lang.model.element.AnnotationValue}
///     <tr><th scope="row">{@code AnnotationTypeDoc}        <td>{@link javax.lang.model.element.TypeElement javax.lang.model.element.TypeElement}
///     <tr><th scope="row">{@code AnnotationTypeElementDoc} <td>{@link javax.lang.model.element.ExecutableElement javax.lang.model.element.ExecutableElement}
///     <tr><th scope="row">{@code AnnotationValue}          <td>{@link javax.lang.model.element.AnnotationValue javax.lang.model.element.AnnotationValue}
///     <tr><th scope="row">{@code ClassDoc}                 <td>{@link javax.lang.model.element.TypeElement javax.lang.model.element.TypeElement}
///     <tr><th scope="row">{@code ConstructorDoc}           <td>{@link javax.lang.model.element.ExecutableElement javax.lang.model.element.ExecutableElement}
///     <tr><th scope="row">{@code Doc}                      <td>{@link javax.lang.model.element.Element javax.lang.model.element.Element}
///     <tr><th scope="row">{@code DocErrorReporter}         <td>{@link jdk.javadoc.doclet.Reporter jdk.javadoc.doclet.Reporter}
///     <tr><th scope="row">{@code Doclet}                   <td>{@link jdk.javadoc.doclet.Doclet jdk.javadoc.doclet.Doclet}
///     <tr><th scope="row">{@code ExecutableMemberDoc}      <td>{@link javax.lang.model.element.ExecutableElement javax.lang.model.element.ExecutableElement}
///     <tr><th scope="row">{@code FieldDoc}                 <td>{@link javax.lang.model.element.VariableElement javax.lang.model.element.VariableElement}
///     <tr><th scope="row">{@code LanguageVersion}          <td>{@link javax.lang.model.SourceVersion javax.lang.model.SourceVersion}
///     <tr><th scope="row">{@code MemberDoc}                <td>{@link javax.lang.model.element.Element javax.lang.model.element.Element}
///     <tr><th scope="row">{@code MethodDoc}                <td>{@link javax.lang.model.element.ExecutableElement javax.lang.model.element.ExecutableElement}
///     <tr><th scope="row">{@code PackageDoc}               <td>{@link javax.lang.model.element.PackageElement javax.lang.model.element.PackageElement}
///     <tr><th scope="row">{@code Parameter}                <td>{@link javax.lang.model.element.VariableElement javax.lang.model.element.VariableElement}
///     <tr><th scope="row">{@code ParameterizedType}        <td>{@link javax.lang.model.type.DeclaredType javax.lang.model.type.DeclaredType}
///     <tr><th scope="row">{@code ParamTag}                 <td>{@link com.sun.source.doctree.ParamTree com.sun.source.doctree.ParamTree}
///     <tr><th scope="row">{@code ProgramElementDoc}        <td>{@link javax.lang.model.element.Element javax.lang.model.element.Element}
///     <tr><th scope="row">{@code RootDoc}                  <td>{@link jdk.javadoc.doclet.DocletEnvironment jdk.javadoc.doclet.DocletEnvironment}
///     <tr><th scope="row">{@code SeeTag}                   <td>{@link com.sun.source.doctree.LinkTree com.sun.source.doctree.LinkTree}<br>
///                                                              {@link com.sun.source.doctree.SeeTree com.sun.source.doctree.SeeTree}
///     <tr><th scope="row">{@code SerialFieldTag}           <td>{@link com.sun.source.doctree.SerialFieldTree com.sun.source.doctree.SerialFieldTree}
///     <tr><th scope="row">{@code SourcePosition}           <td>{@link com.sun.source.util.SourcePositions com.sun.source.util.SourcePositions}
///     <tr><th scope="row">{@code Tag}                      <td>{@link com.sun.source.doctree.DocTree com.sun.source.doctree.DocTree}
///     <tr><th scope="row">{@code ThrowsTag}                <td>{@link com.sun.source.doctree.ThrowsTree com.sun.source.doctree.ThrowsTree}
///     <tr><th scope="row">{@code Type}                     <td>{@link javax.lang.model.type.TypeMirror javax.lang.model.type.TypeMirror}
///     <tr><th scope="row">{@code TypeVariable}             <td>{@link javax.lang.model.type.TypeVariable javax.lang.model.type.TypeVariable}
///     <tr><th scope="row">{@code WildcardType}             <td>{@link javax.lang.model.type.WildcardType javax.lang.model.type.WildcardType}
///   </tbody>
/// </table>
///
/// @see jdk.javadoc.doclet.Doclet
/// @see jdk.javadoc.doclet.DocletEnvironment
/// @since 9
package jdk.javadoc.doclet;
