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

/**
 * JsonString lazy implementation subclass
 */
final class JsonStringLazyImpl extends JsonStringImpl implements JsonValueLazyImpl {

    private final int endIndex;

    JsonStringLazyImpl(JsonLazyDocumentInfo docInfo, int offset, int index) {
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

    JsonStringLazyImpl(String str) {
        docInfo = new JsonLazyDocumentInfo("\"" + str + "\"");
        startOffset = 0;
        endIndex = 0;
        endOffset = docInfo.getEndOffset();
        theString = unescape(startOffset + 1, endOffset - 1);
    }

    // Lazy implementation already knows start and closing quotes, simply unescape
    @Override
    String unescape(int startOffset, int endOffset) {
        var sb = new StringBuilder();
        var escape = false;
        int offset = startOffset;
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
                            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                    "Illegal Unicode escape.", offset), offset);
                        }
                    }
                    default -> throw new JsonParseException(docInfo.composeParseExceptionMessage(
                            "Illegal escape.", offset), offset);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
                continue;
            } else if (c < ' ') {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unescaped control code.", offset), offset);
            }
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public String value() {
        // Ensure the input is validated
        if (theString == null) {
            theString = unescape(startOffset + 1, endOffset - 1);
        }
        return theString;
    }

    @Override
    public String formatCompact() {
        value(); // Call to validate input
        return super.formatCompact();
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1;
    }
}
