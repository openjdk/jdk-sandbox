/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets;

import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.SnippetTree;
import com.sun.source.doctree.TagAttributeTree;
import com.sun.source.doctree.TextTree;
import jdk.javadoc.doclet.Taglet;
import jdk.javadoc.internal.doclets.toolkit.Content;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action.Action;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.parser.ParseException;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.parser.Parser;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.StyledText;

import javax.lang.model.element.Element;
import javax.lang.model.element.ModuleElement;
import javax.lang.model.element.PackageElement;
import javax.tools.FileObject;
import javax.tools.JavaFileManager;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public class SnippetTaglet extends BaseTaglet {

    public SnippetTaglet() {
        super(DocTree.Kind.SNIPPET, true, EnumSet.allOf(Taglet.Location.class));
    }

    /*
     * A snippet can specify content by value or by reference.
     *
     * To specify content by value, a snippet uses its body; the body of a snippet is the content.
     * To specify content by reference, a snippet uses either the "class" or "file" attribute;
     * the value of that attribute refers to the content.
     *
     * A snippet can specify the "region" attribute. That attribute refines
     * The value of that attribute must match one of the named regions in the snippets content.
     */
    @Override
    // FIXME
    //   On the one hand, returning null from this method is shady.
    //   On the other hand, throwing a checked exception from this method is impossible.
    //   This method MUST be revisited.
    public Content getInlineTagOutput(Element holder, DocTree tag, TagletWriter writer) {
        SnippetTree snippetTag = (SnippetTree) tag;

        Map<String, TagAttributeTree> attributes = new HashMap<>();

        // Organize attributes in a map performing basic checks along the way
        for (TagAttributeTree a : snippetTag.getAttributes()) {
            TagAttributeTree prev = attributes.putIfAbsent(a.getName().toString(), a);
            if (prev == null) {
                continue;
            }
            // Like-named attributes found: `prev` and `a`
            error(writer, holder, a, "doclet.tag.attribute.repeated", a.getName().toString());
            return null;
        }

        final boolean containsClass = attributes.containsKey("class");
        final boolean containsFile = attributes.containsKey("file");
        final boolean containsBody = snippetTag.getBody() != null;

        if (containsClass && containsFile) {
            error(writer, holder, tag, "doclet.snippet.contents.ambiguity.external");
            return null;
        } else if (!containsClass && !containsFile && !containsBody) {
            error(writer, holder, tag, "doclet.snippet.contents.none");
            return null;
        }

        // FIXME: remove as compiler people do not like assertions
        assert (containsClass || containsFile || containsBody) && !(containsClass && containsFile) :
                Arrays.toString(new boolean[]{containsClass, containsFile, containsBody});

        String r = null;
        TagAttributeTree region = attributes.get("region");
        if (region != null) {
            r = stringOf(region.getValue());
            if (r.isBlank()) {
                error(writer, holder, region, "doclet.tag.attribute.value.illegal", "region", region);
                return null;
            }
        }

        String inlineContent = null, externalContent = null;

        if (containsBody) {
            inlineContent = snippetTag.getBody().getBody().stripIndent();
        }

        if (containsFile || containsClass) {
            TagAttributeTree file = attributes.get("file");
            TagAttributeTree ref = file != null ? file : attributes.get("class");
            List<? extends DocTree> value = ref.getValue();
            String v = stringOf(value);
            String refType = ref.getName().toString();

            // We didn't create JavaFileManager, so we won't close it; even if an error occurs
            var fileManager = (StandardJavaFileManager) writer.configuration().getFileManager();

            FileObject fileObject;
            try {
                if (refType.equals("class")) {
                    // fileObject = fileManager.getJavaFileForInput(StandardLocation.SOURCE_PATH, v, JavaFileObject.Kind.SOURCE);
                    throw new UnsupportedOperationException("Not yet implemented");
                } else {
                    // assert refType.equals("file") : refType;
                    String relativeName = "snippet-files/" + v;
                    String packageName = packageName(holder, writer);
                    fileObject = fileManager.getFileForInput(StandardLocation.SOURCE_PATH,
                                                             packageName,
                                                             relativeName);
                    if (fileObject == null) {
                        ModuleElement moduleElement = writer.configuration().utils.containingModule(holder);
                        if (moduleElement != null) {
                            JavaFileManager.Location loc = fileManager.getLocationForModule(
                                    StandardLocation.MODULE_SOURCE_PATH, moduleElement.getQualifiedName().toString());
                            if (loc != null) {
                                fileObject = fileManager.getFileForInput(loc, packageName, relativeName);
                            }
                        }
                    }
                }
            } catch (IOException e) {
                // FIXME: provide more context (the snippet, its attribute, and its attributes' value)
                error(writer, holder, tag, "doclet.exception.read.file", v, e.getCause());
                return null;
            }

            if (fileObject == null) { /* i.e. the file does not exist */
                error(writer, holder, tag, "doclet.File_not_found", v);
                return null;
            }

            Path path = fileManager.asPath(fileObject);
            try {
                externalContent = Files.readString(path); // FIXME don't read file every single time; cache it
            } catch (IOException e) {
                // FIXME: provide more context (the snippet, its attribute, and its attributes' value)
                error(writer, holder, tag, "doclet.exception.read.file", path, e.getCause());
                return null;
            }
        }


        StyledText inlineSnippet = null;
        StyledText externalSnippet = null;

        try {
            if (inlineContent != null) {
                inlineSnippet = parse(inlineContent);
            }
            if (externalContent != null) {
                externalSnippet = parse(externalContent);
            }
            // The region must be matched at least in one content: it can be matched
            // in both, but never in none
            if (r != null) {
                if (inlineSnippet != null) {
                    inlineSnippet = zoomIntoRegion(r, inlineSnippet);
                }
                if (externalSnippet != null) {
                    externalSnippet = zoomIntoRegion(r, externalSnippet);
                }
                if (inlineSnippet == null && externalSnippet == null) {
                    error(writer, holder, tag, "doclet.snippet.region.not_found", r);
                    return null;
                }
            }
        } catch (ParseException e) {
            error(writer, holder, region, "doclet.snippet.markup", e.getMessage());
            return null;
        }

// Uncomment this check after better extraction of external snippets
// (they might have extra newline compared to inline because of markup):
//
//        if (inlineSnippet != null && externalSnippet != null) {
//            if (!Objects.equals(inlineSnippet.asCharSequence().toString(),
//                                externalSnippet.asCharSequence().toString())) {
//                error(writer, holder, tag, "doclet.snippet.contents.mismatch");
//                return null;
//            }
//        }

        assert inlineSnippet != null || externalSnippet != null;
        StyledText text = inlineSnippet != null ? inlineSnippet : externalSnippet;

        return writer.snippetTagOutput(holder, snippetTag, text);
    }

    private StyledText zoomIntoRegion(String r, StyledText t) {
        var z = t.getBookmark(r);
        return z != null ? z : t;
    }

    private StyledText parse(String content) throws ParseException {
        // TODO: need to be able to process more fine-grained, i.e. around a particular region...
        // or, which is even better, cache the styled text
        Parser.Result result = new Parser().parse(content);
        result.actions().forEach(Action::perform);
        return result.text();
    }

    private static String stringOf(List<? extends DocTree> value) {
        return value.stream()
                .map(t -> ((TextTree) t).getBody()) // value consists of TextTree nodes
                .collect(Collectors.joining());
    }

    // FIXME: figure out how to do that correctly
    // FIXME: consider returning null from this method so it can be used as oneliner
    private void error(TagletWriter writer, Element holder, DocTree tag, String key, Object... args) {
        writer.configuration().getMessages().error(
                writer.configuration().utils.getCommentHelper(holder).getDocTreePath(tag), key, args);
    }

    private String packageName(Element e, TagletWriter writer) {
        PackageElement pkg = writer.configuration().utils.containingPackage(e);
        return writer.configuration().utils.getPackageName(pkg);
    }
}
