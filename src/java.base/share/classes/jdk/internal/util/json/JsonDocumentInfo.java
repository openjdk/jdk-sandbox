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

// The base JsonDocumentInfo
// Can otherwise be thought of as an "eager" JSON document. Parsing should
// validate all leaves.
sealed class JsonDocumentInfo permits JsonLazyDocumentInfo {
    final RawDocument doc;
    final int endOffset;

    JsonDocumentInfo(String in) {
        doc = new RawDocument(in);
        endOffset = in.length();
    }

    JsonDocumentInfo(char[] in) {
        doc = new RawDocument(in);
        endOffset = doc.length();
    }

    int getEndOffset() {
        return endOffset;
    }

    // gets the char at the specified offset in the input
    char charAt(int offset) {
        return doc.charAt(offset);
    }

    // gets the substring at the specified start/end offsets in the input
    String substring(int startOffset, int endOffset) {
        return doc.substring(startOffset, endOffset);
    }

    // Utility method to compose parse exception messages that include offsets/chars
    public String composeParseExceptionMessage(String message, int offset) {
        return message + " Offset: %d (%s)"
                .formatted(offset, substring(offset, Math.min(offset + 8, endOffset)));
    }

    /**
     * encapsulates the access to the document underneath, either
     * a String or a char array.
     */
    static class RawDocument {
        final String inStr;
        final char[] inChArray;

        RawDocument(String in) {
            inStr = in;
            inChArray = null;
        }

        RawDocument(char[] in) {
            inStr = null;
            inChArray = in;
        }

        int length() {
            if (inStr != null) {
                return inStr.length();
            } else {
                assert inChArray != null;
                return inChArray.length;
            }
        }

        char charAt(int index) {
            if (inStr != null) {
                return inStr.charAt(index);
            } else {
                assert inChArray != null;
                return inChArray[index];
            }
        }

        String substring(int start, int end) {
            if (inStr != null) {
                return inStr.substring(start, end);
            } else {
                assert inChArray != null;
                return new String(inChArray, start, end - start);
            }
        }
    }
}
