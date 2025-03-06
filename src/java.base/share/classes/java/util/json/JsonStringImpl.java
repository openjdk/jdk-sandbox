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

import java.util.Objects;

/**
 * JsonString implementation class
 */
final class JsonStringImpl implements JsonString, JsonValueImpl {

    private final JsonDocumentInfo docInfo;
    private final int startOffset;
    private final int endOffset;
    private final int endIndex;
    private String theString;
    private String source;

    JsonStringImpl(String str) {
        docInfo = new JsonDocumentInfo(("\"" + str + "\"").toCharArray());
        startOffset = 0;
        endOffset = docInfo.getEndOffset();
        theString = unescape(startOffset + 1, endOffset - 1);
        endIndex = 0;
    }
    
    JsonStringImpl(JsonDocumentInfo doc, int offset, int index) {
        docInfo = doc;
        startOffset = offset;
        endIndex = index + 1;
        endOffset = docInfo.getOffset(endIndex) + 1;
    }

    @Override
    public String value() {
        if (theString == null) {
            try {
                theString = unescape(startOffset + 1, endOffset - 1);
            } catch (IllegalArgumentException iae) {
                throw new IllegalStateException(iae);
            }
        }
        return theString;
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1; // We are interested in the index after '"'
    }

    @Override
    public String toUntyped() {
        return value();
    }

    @Override
    public String toString() {
        if (source == null) {
            source = docInfo.substring(startOffset, endOffset);
        }
        return source;
    }

    String unescape(int startOffset, int endOffset) {
        StringBuilder sb = null; // Only use if required
        var escape = false;
        int offset = startOffset;
        boolean useBldr = false;
        for (; offset < endOffset; offset++) {
            var c = docInfo.charAt(offset);
            if (escape) {
                var length = 0;
                switch (c) {
                    case '"', '\\', '/' -> {}
                    case 'b' -> c = '\b';
                    case 'f' -> c = '\f';
                    case 'n' -> c = '\n';
                    case 'r' -> c = '\r';
                    case 't' -> c = '\t';
                    case 'u' -> {
                        c = JsonParser.codeUnit(docInfo, offset + 1);
                        length = 4;
                    }
                    default -> throw new IllegalArgumentException("Illegal escape sequence");
                }
                if (!useBldr) {
                    useBldr = true;
                    // At best, we know the size of the first escaped value
                    sb = new StringBuilder(endOffset - startOffset - length - 1)
                            .append(docInfo.getDoc(), startOffset, offset - 1 - startOffset);
                }
                offset+=length;
                escape = false;
            } else if (c == '\\') {
                escape = true;
                continue;
            }
            if (useBldr) {
                sb.append(c);
            }
        }
        if (useBldr) {
            return sb.toString();
        } else {
            var ret = toString();
            // unescape() does not include the quotes
            return ret.substring(1, ret.length() - 1);
        }
    }
}
