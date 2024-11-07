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

// The full creation/validation of JsonValue(s) are deferred until needed.
// Hence, this class utilizes an array of indices which track certain tokens
final class JsonLazyDocumentInfo extends JsonDocumentInfo {
    private final int[] tokenOffsets;
    private final int indexCount;

    JsonLazyDocumentInfo(String in) {
        super(in);
        tokenOffsets = new int[endOffset];
        indexCount = createOffsetsArray();
    }

    JsonLazyDocumentInfo(char[] in) {
        super(in);
        tokenOffsets = new int[endOffset];
        indexCount = createOffsetsArray();
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
}
