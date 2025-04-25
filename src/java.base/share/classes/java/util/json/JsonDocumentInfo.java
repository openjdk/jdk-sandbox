/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package java.util.json;

final class JsonDocumentInfo  {

    // Access to the underlying JSON contents
    private final char[] doc;
    // Current offset during parsing
    int offset = 0;
    // For exception message on failure
    private int line = 0;
    private int lineStart = 0;

    JsonDocumentInfo(char[] in) {
        doc = in;
    }

    char[] getDoc() {
        return doc;
    }

    int getEndOffset() {
        return doc.length;
    }

    // returns true if the char at the specified offset equals the input char
    // and is within bounds of the JsonDocumentInfo
    boolean currCharEquals(char c) {
        return offset < getEndOffset() && c == charAt(offset);
    }

    // gets the char at the specified offset in the input
    char charAt(int offset) {
        return doc[offset];
    }

    // Returns true if the substring at the given offset equals the input String
    // and is within bounds
    boolean charsEqual(String s, int offset) {
        return offset + s.length() - 1 < getEndOffset()
                && substring(offset, offset + s.length()).equals(s);
    }

    // gets the substring at the specified start/end offsets in the input
    String substring(int startOffset, int endOffset) {
        return new String(doc, startOffset, endOffset - startOffset);
    }

    // Increments the row and col
    void updateLine(int offset) {
        line+=1;
        lineStart = offset;
    }

    int getLine() {
        return line;
    }

    int getLineStart() {
        return lineStart;
    }

    // Utility method to compose parse exception message
    String composeParseExceptionMessage(String message, int line, int lineStart, int offset) {
        return "%s: (%s) at Row %d, Col %d."
                .formatted(message, substring(offset, Math.min(offset + 8, doc.length)),
                        line, offset - lineStart);
    }
}
