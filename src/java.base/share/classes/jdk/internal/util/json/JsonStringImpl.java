/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.util.json;

import java.util.Objects;

/**
 * JsonString implementation class
 */
sealed class JsonStringImpl implements JsonString, JsonValueImpl permits JsonStringLazyImpl {

    JsonDocumentInfo docInfo;
    int startOffset;
    int endOffset;
    String theString;
    String source;

    // For use by subclasses
    JsonStringImpl() {}

    JsonStringImpl(JsonDocumentInfo docInfo, int offset) {
        this.docInfo = docInfo;
        startOffset = offset;
        theString = unescape(offset + 1, docInfo.getEndOffset()); // sets "endOffset"
    }

    @Override
    public String value() {
        return theString;
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonStringImpl ojsi &&
            Objects.equals(toString(), ojsi.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString());
    }

    String toUntyped() {
        return value();
    }

    // toString should return the source input String
    @Override
    public String toString() {
        if (source == null) {
            source = docInfo.substring(startOffset, endOffset);
        }
        return source;
    }

    @Override
    public String toDisplayString() {
        return toDisplayString(0, false);
    }

    @Override
    public String toDisplayString(int indent, boolean isField) {
        return " ".repeat(isField ? 1 : indent) + toString();
    }

    // gets the substring at the specified start/end offsets in the input with decoding
    // escape sequences. Eager implementations must find the closing quote.
    String unescape(int startOffset, int endOffset) {
        var sb = new StringBuilder();
        var escape = false;
        int offset = startOffset;
        boolean closeQuote = false;
        for (; offset < endOffset; offset++) {
            var c = docInfo.charAt(offset);
            if (escape) {
                switch (c) {
                    case '"', '\\', '/' -> {}
                    case 'b' -> c = '\b';
                    case 'f' -> c = '\f';
                    case 'n' -> c = '\n';
                    case 'r' -> c = '\r';
                    case 't' -> c = '\t';
                    case 'u' -> {
                        if (offset + 4 < endOffset) {
                            c = codeUnit(offset + 1);
                            offset += 4;
                        } else {
                            throw new JsonParseException(docInfo,
                                    "Illegal Unicode escape.", offset);
                        }
                    }
                    default -> throw new JsonParseException(docInfo,
                            "Illegal escape.", offset);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
                continue;
            } else if (c == '\"') {
                closeQuote = true;
                break;
            } else if (c < ' ') {
                throw new JsonParseException(docInfo,
                        "Unescaped control code.", offset);
            }
            sb.append(c);
        }
        if (!closeQuote) { // Eager fails if closing quote not found by end
            throw new JsonParseException(docInfo,
                    "JsonString missing closing quote.", offset);
        }
        // Eager needs to set endOffset, +1 for the closing quote
        this.endOffset = ++offset;
        return sb.toString();
    }

    char codeUnit(int offset) {
        char val = 0;
        for (int index = 0; index < 4; index ++) {
            char c = docInfo.charAt(offset + index);
            val <<= 4;
            val += (char) (
                switch (c) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                    case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
                    case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
                    default -> throw new JsonParseException(docInfo,
                            "Invalid Unicode escape.", offset);
                } );
        }
        return val;
    }
}
