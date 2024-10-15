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
final class JsonStringImpl implements JsonString, JsonValueImpl {
    private final JsonDocumentInfo docInfo;
    private final int startOffset, endOffset;
    private final int endIndex;
    private String theString;
    private String source;

    JsonStringImpl(String str) {
        docInfo = new JsonDocumentInfo("\"" + str + "\"");
        startOffset = 0;
        endOffset = docInfo.getEndOffset();
        endIndex = 0;
    }

    JsonStringImpl(JsonDocumentInfo docInfo, int offset, int index) {
        this.docInfo = docInfo;
        startOffset = offset;
        endIndex = docInfo.nextIndex(index);
        // First quote is already implicitly matched during parse
        if (endIndex != -1 && docInfo.charAtIndex(endIndex) == '"') {
            endOffset = docInfo.getOffset(endIndex) + 1;
        } else {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Dangling quote.", offset), offset);
        }
    }

    @Override
    public String value() {
        // Ensure the input is sanitized
        if (theString == null) {
            theString = docInfo.unescape(startOffset + 1, endOffset - 1);
        }
        return theString;
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public int getEndIndex() {
        return endIndex;
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

    @Override
    public String to() {
        return value();
    }

    // toString should return the source input String
    @Override
    public String toString() {
        return formatCompact();
    }

    @Override
    public String formatCompact() {
        value(); // Call to sanitize input
        if (source == null) {
            source = docInfo.substring(startOffset, endOffset);
        }
        return source;
    }

    @Override
    public String formatReadable() {
        return formatReadable(0, false);
    }

    @Override
    public String formatReadable(int indent, boolean isField) {
        return " ".repeat(isField ? 1 : indent) + toString();
    }
}
