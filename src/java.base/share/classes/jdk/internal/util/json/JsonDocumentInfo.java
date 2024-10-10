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

final class JsonDocumentInfo {
    private final RawDocument doc;
    private final int[] tokenOffsets;
    private final int indexCount;
    private final int endOffset;

    JsonDocumentInfo(String in) {
        doc = new RawDocument(in);
        endOffset = in.length();
        tokenOffsets = new int[endOffset];
        indexCount = createOffsetsArray();
    }

    JsonDocumentInfo(char[] in) {
        doc = new RawDocument(in);
        endOffset = doc.length();
        tokenOffsets = new int[endOffset];
        indexCount = createOffsetsArray();
    }

    // gets offset in the input from the array index
    int getOffset(int index) {
        Objects.checkIndex(index, indexCount);
        return tokenOffsets[index];
    }

    int getEndOffset() {
        return endOffset;
    }

    int getIndexCount() {
        return indexCount;
    }

    // gets the char at the specified offset in the input
    char charAt(int offset) {
        return doc.charAt(offset);
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

    // Used by JsonObject and JsonArray to get the endIndex
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
            throw new JsonParseException(composeParseExceptionMessage(
                    "Braces or brackets do not match.", startOff), startOff);
        }

        return index;
    }

    // for convenience
    char charAtIndex(int index) {
        return doc.charAt(getOffset(index));
    }

    // Convenience to skip an index when inflating a JsonObject/Array
    boolean isWalkableStartIndex(char c) {
        return switch (c) {
            // Order is important, with String being most common JsonType
            case '"', '{', '['  -> true;
            default -> false;
        };
    }

    // gets the substring at the specified start/end offsets in the input
    String substring(int startOffset, int endOffset) {
        return doc.substring(startOffset, endOffset);
    }

    // gets the substring at the specified start/end offsets in the input with decoding
    // escape sequences
    String unescape(int startOffset, int endOffset) {
        var sb = new StringBuilder();
        var escape = false;
        for (int offset = startOffset; offset < endOffset; offset++) {
            var c = doc.charAt(offset);

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
                            throw new JsonParseException(composeParseExceptionMessage(
                                    "Illegal Unicode escape.", offset), offset);
                        }
                    }
                    default -> throw new JsonParseException(composeParseExceptionMessage(
                            "Illegal escape.", offset), offset);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
                continue;
            } else if (c < ' ') {
                throw new JsonParseException(composeParseExceptionMessage(
                        "Unescaped control code.", offset), offset);
            }

            sb.append(c);
        }

        return sb.toString();
    }

    // Utility method to compose parse exception messages that include offsets/chars
    String composeParseExceptionMessage(String message, int offset) {
        return message + " Offset: %d (%s)"
                .formatted(offset, substring(offset, Math.min(offset + 8, endOffset)));
    }

    // Utility method to compose parse exception messages that include offsets/chars
    String composeParseExceptionMessage2(String message, int offset) {
        return message + " Offset: %d (%s)"
                .formatted(offset, substring(offset, Math.min(offset + 8, endOffset)));
    }

    private char codeUnit(int offset) {
        char val = 0;
        for (int index = 0; index < 4; index ++) {
            char c = doc.charAt(offset + index);
            val <<= 4;
            val += (char) (
                    switch (c) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                        case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
                        case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
                        default -> throw new JsonParseException(composeParseExceptionMessage(
                                "Invalid Unicode escape.", offset), offset);
                    } );
        }
        return val;
    }

    private int createOffsetsArray() {
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

    /**
     * encapsulates the access to the document underneath, either
     * a String or a char array.
     */
    private static class RawDocument {
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
