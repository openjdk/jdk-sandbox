/*
 * Copyright (c) 1998, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.doclet;

import java.io.PrintWriter;
import java.util.Locale;
import javax.lang.model.element.Element;
import javax.tools.Diagnostic;
import javax.tools.FileObject;

import com.sun.source.doctree.CommentTree;
import com.sun.source.doctree.DocTypeTree;
import com.sun.source.doctree.ReferenceTree;
import com.sun.source.doctree.TextTree;
import com.sun.source.util.DocTreePath;

/// Interface for reporting diagnostics and other messages.
///
/// Diagnostics consist of a [`diagnostic kind`][Diagnostic.Kind] and a message,
/// and may additionally be associated with an [`element`][Element],
/// a [`tree node`][DocTreePath] in a documentation comment,
/// or an arbitrary position in a given [`file`][FileObject].
/// Other messages may be written directly to one of two streams that are informally
/// for use by "standard output" and "diagnostic output", where "standard output"
/// means the output that is the expected result of executing some operation,
/// such as the command-line help that is generated when using a `--help` option,
/// and "diagnostic output" refers to any errors, warnings and other output that is
/// a side effect of executing the operation.
///
/// The exact manner in which diagnostics are output is unspecified and depends
/// on the enclosing context. For example:
///
/// - The [javax.tools.DocumentationTool] API allows a client to specify a
/// [javax.tools.DiagnosticListener] to which diagnostics will be
/// [`reported`][javax.tools.DiagnosticListener#report]. If no listener is specified,
/// diagnostics will be written to a given stream, or to `System.err` if no such
/// stream is provided.
/// - The [java.util.spi.ToolProvider] API allows a client to specify
/// the streams to be used for reporting standard and diagnostic output.
///
/// @since 9
public interface Reporter {

    /// Prints a diagnostic message.
    ///
    /// @param kind    the kind of diagnostic
    /// @param message the message to be printed
    void print(Diagnostic.Kind kind, String message);

    /// Prints a diagnostic message related to a tree node in a documentation comment.
    ///
    /// @param kind    the kind of diagnostic
    /// @param path    the path for the tree node
    /// @param message the message to be printed
    void print(Diagnostic.Kind kind, DocTreePath path, String message);

    /// Prints a diagnostic message related to a position within a range of characters in a tree node.
    ///
    /// Only kinds of `DocTree` that wrap a simple string value are supported as leaf nodes
    /// of the given path. This currently includes
    /// [CommentTree], [DocTypeTree], [ReferenceTree], and [TextTree].
    ///
    /// The positions are all 0-based character offsets from the beginning of string.
    /// The positions should satisfy the relation `start <= pos <= end`.
    ///
    /// @implSpec
    /// This implementation ignores the `(start, pos, end)` values and simply calls
    /// [`print(kind, path, message)`][#print(Diagnostic.Kind, DocTreePath, String)].
    ///
    /// @param kind    the kind of diagnostic
    /// @param path    the path for the tree node
    /// @param start   the beginning of the enclosing range
    /// @param pos     the position
    /// @param end     the end of the enclosing range
    /// @param message the message to be printed
    ///
    /// @throws IllegalArgumentException if `start`, `pos` and `end` do
    ///          not form a valid range.
    ///
    /// @since 18
    default void print(Diagnostic.Kind kind, DocTreePath path, int start, int pos, int end, String message) {
        print(kind, path, message);
    }

    /// Prints a diagnostic message related to an element.
    ///
    /// @param kind    the kind of diagnostic
    /// @param element the element
    /// @param message the message to be printed
    void print(Diagnostic.Kind kind, Element element, String message);

    /// Prints a diagnostic message related to a position within a range of characters in a file.
    /// The positions are all 0-based character offsets from the beginning of content of the file.
    /// The positions should satisfy the relation `start <= pos <= end`.
    ///
    /// @implSpec
    /// This implementation always throws `UnsupportedOperationException`.
    /// The implementation provided by the `javadoc` tool to
    /// [`initialize`][Doclet#init(Locale, Reporter)] a doclet
    /// overrides this implementation.
    ///
    /// @param kind    the kind of diagnostic
    /// @param file    the file
    /// @param start   the beginning of the enclosing range
    /// @param pos     the position
    /// @param end     the end of the enclosing range
    /// @param message the message to be printed
    ///
    /// @since 17
    default void print(Diagnostic.Kind kind, FileObject file, int start, int pos, int end, String message) {
        throw new UnsupportedOperationException();
    }

    /// Returns a writer that can be used to write non-diagnostic output,
    /// or `null` if no such writer is available.
    ///
    /// @apiNote
    /// The value may or may not be the same as that returned by [#getDiagnosticWriter()].
    ///
    /// @implSpec
    /// This implementation returns `null`.
    /// The implementation provided by the `javadoc` tool to
    /// [`initialize`][Doclet#init(Locale, Reporter)] a doclet
    /// always returns a non-`null` value.
    ///
    /// @return the writer
    /// @since 17
    default PrintWriter getStandardWriter() {
        return null;
    }

    /// Returns a writer that can be used to write diagnostic output,
    /// or `null` if no such writer is available.
    ///
    /// @apiNote
    /// The value may or may not be the same as that returned by [#getStandardWriter()].
    ///
    /// @implSpec
    /// This implementation returns `null`.
    /// The implementation provided by the `javadoc` tool to
    /// [`initialize`][Doclet#init(Locale, Reporter)] a doclet
    /// always returns a non-`null` value.
    ///
    /// @return the writer
    /// @since 17
    default PrintWriter getDiagnosticWriter() {
        return null;
    }

}
