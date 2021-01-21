/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.Element;
import javax.lang.model.element.PackageElement;
import javax.tools.FileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

public class SnippetTaglet extends BaseTaglet {

    public SnippetTaglet() {
        super(DocTree.Kind.SNIPPET, true, EnumSet.allOf(Taglet.Location.class));
    }

    /*
     * A snippet is either inline or external.
     * An inline snippet has body.
     * An external snippet has either the "class" or "file" attribute.
     */
    @Override
    public Content getInlineTagOutput(Element holder, DocTree tag, TagletWriter writer) {
        String content;
        SnippetTree snippetTag = (SnippetTree) tag;

        boolean valid = true;

        final var externalRefs = snippetTag.getAttributes()
                .stream()
                .filter(a -> a.getName().contentEquals("class") || a.getName().contentEquals("file"))
                .limit(2) // one duplicate is enough
                .collect(Collectors.toUnmodifiableList());

        if (externalRefs.size() > 1) {
            valid = false;
            error(writer, holder, tag, "doclet.snippet.contents.ambiguity.external");
        }

        if (!externalRefs.isEmpty() && snippetTag.getBody() != null) {
            valid = false;
            error(writer, holder, tag, "doclet.snippet.contents.ambiguity.mixed");
        } else if (externalRefs.isEmpty() && snippetTag.getBody() == null) {
            valid = false;
            error(writer, holder, tag, "doclet.snippet.contents.none");
        }

        if (!valid) {
            // FIXME: what's the implied contract here for a method that has errored?
            return null;
        }

        if (snippetTag.getBody() != null) {
            content = snippetTag.getBody().getBody().stripIndent();
        } else {
            TagAttributeTree ref = externalRefs.get(0);
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
                    fileObject = fileManager.getFileForInput(StandardLocation.SOURCE_PATH, packageName(holder, writer), "snippet-files/" + v);
                }
            } catch (IOException e) {
                // FIXME: provide more context (the snippet, its attribute, and its attributes' value)
                error(writer, holder, tag, "doclet.exception.read.file", v, e.getCause());
                // FIXME: what's the implied contract here for a method that has errored?
                return null; // replace with UncheckedDocletException
            }

            if (fileObject == null) { /* i.e. the file does not exist */
                error(writer, holder, tag, "doclet.File_not_found", v);
                return null;
            }

            Path path = fileManager.asPath(fileObject);
            try {
                content = Files.readString(path);
            } catch (IOException e) {
                // FIXME: provide more context (the snippet, its attribute, and its attributes' value)
                error(writer, holder, tag, "doclet.exception.read.file", path, e.getCause());
                return null;
            }
        }
        return writer.snippetTagOutput(holder, snippetTag, content);
    }

    private static String stringOf(List<? extends DocTree> value) {
        return value.stream()
                .map(t -> ((TextTree) t).getBody()) // value consists of TextTree nodes
                .collect(Collectors.joining());
    }

    // FIXME: figure out how to do that correctly
    private void error(TagletWriter writer, Element holder, DocTree tag, String key, Object... args) {
        writer.configuration().getMessages().error(
                writer.configuration().utils.getCommentHelper(holder).getDocTreePath(tag), key, args);
    }

    private String packageName(Element e, TagletWriter writer) {
        PackageElement pkg = writer.configuration().utils.containingPackage(e);
        return writer.configuration().utils.getPackageName(pkg);
    }
}
