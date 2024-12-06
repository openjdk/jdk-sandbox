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

final class JsonDocumentInfo  {

    final RawDocument doc;
    final int endOffset;
    private final int[] tokenOffsets;
    private final int indexCount;

    JsonDocumentInfo(String in) {
        doc = new RawDocument(in);
        endOffset = in.length();
        tokenOffsets = new int[endOffset];
        indexCount = createTokensArray();
    }

    JsonDocumentInfo(char[] in) {
        doc = new RawDocument(in);
        endOffset = doc.length();
        tokenOffsets = new int[endOffset];
        indexCount = createTokensArray();
    }

    // Convenience to skip an index when inflating a JsonObject/Array
    boolean isWalkableStartIndex(char c) {
        return switch (c) {
            // Order is important, with String being most common JsonType
            case '"', '{', '['  -> true;
            default -> false;
        };
    }

    // gets offset in the input from the array index
    int getOffset(int index) {
        Objects.checkIndex(index, indexCount);
        return tokenOffsets[index];
    }

    // Used by Json String, Boolean, Null, and Number to get the endIndex
    // Returns -1, if the next index is not within bounds of indexCount
    int nextIndex(int index) {
        if (index + 1 < indexCount) {
            return index + 1;
        } else {
            return -1;
        }
    }

    // for convenience
    char charAtIndex(int index) {
        return doc.charAt(getOffset(index));
    }

    int getIndexCount() {
        return indexCount;
    }

    // Used by JsonObject and JsonArray to get the endIndex. In other words, a
    // Json Value where the next index is not +1.
    // This method is not that costly, since we walk the indices, not the offsets.
    // Tracking the end index during the offset array creation requires a stack
    // which is generally costlier than calculating here.
    int getStructureLength(int startIdx, int startOff, char startToken, char endToken) {
        var index = startIdx + 1;
        int depth = 0;
        while (index < indexCount) {
            var c = charAtIndex(index);
            if (c == startToken) {
                depth++;
            } else if (c == endToken) {
                depth--;
            }
            if (depth < 0) {
                break;
            }
            index++;
        }
        if (index >= indexCount) {
            throw new JsonParseException(this,
                    "Braces or brackets do not match.", startOff);
        }
        return index;
    }

    private int createTokensArray() {
        int index = 0;
        boolean inQuote = false;

        for (int offset = 0; offset < doc.length(); offset++) {
            char c = doc.charAt(offset);
            switch (c) {
                case '{', '}', '[', ']', '"', ':', ',' -> {
                    if (c == '"') {
                        if (inQuote) {
                            // check prepending backslash
                            int lookback = offset - 1;
                            while (lookback >= 0 && doc.charAt(lookback) == '\\') {
                                lookback --;
                            }
                            if ((offset - lookback) % 2 != 0) {
                                inQuote = false;
                                tokenOffsets[index++] = offset;
                            }
                        } else {
                            tokenOffsets[index++] = offset;
                            inQuote = true;
                        }
                    } else {
                        if (!inQuote) {
                            tokenOffsets[index++] = offset;
                        }
                    }
                }
            }
        }
        return index;
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
    String composeParseExceptionMessage(String message, int offset) {
        return message + " Offset: %d (%s)"
                .formatted(offset, substring(offset, Math.min(offset + 8, endOffset)));
    }

    /**
     * encapsulates the access to the document underneath, either
     * a String or a char array.
     */
    private static class RawDocument {

        final char[] inChArray;

        RawDocument(String in) {
            inChArray = in.toCharArray();
        }

        RawDocument(char[] in) {
            inChArray = in;
        }

        int length() {
            return inChArray.length;
        }

        char charAt(int index) {
            return inChArray[index];
        }

        String substring(int start, int end) {
            return new String(inChArray, start, end - start);
        }
    }
}
