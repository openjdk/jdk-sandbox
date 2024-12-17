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
package java.util.json;

import java.util.Objects;

final class JsonDocumentInfo  {

    final char[] doc;
    final int endOffset;
    final int[] tokens;
    int index;
    int row = 0;
    int prevRowOff = 0;

    JsonDocumentInfo(char[] in) {
        doc = in;
        endOffset = doc.length;
        tokens = new int[endOffset];
        index = 0;
    }

    // Convenience to walk a token during inflation
    boolean shouldWalkToken(char c) {
        return switch (c) {
            case '"', '{', '['  -> true;
            default -> false;
        };
    }

    // gets offset in the input from the array index
    int getOffset(int index) {
        Objects.checkIndex(index, this.index);
        return tokens[index];
    }

    // Used by Json String, Boolean, Null, and Number to get the endIndex
    // Returns -1, if the next index is not within bounds of indexCount
    // Which should only happen when it is the root
    int nextIndex(int index) {
        if (index + 1 < this.index) {
            return index + 1;
        } else {
            return -1;
        }
    }

    // for convenience
    char charAtIndex(int index) {
        return doc[getOffset(index)];
    }

    int getIndexCount() {
        return index;
    }

    // Used by JsonObject and JsonArray to get the endIndex. In other words, a
    // Json Value where the next index is not +1.
    // This method is not that costly, since we walk the indices, not the offsets.
    // Tracking the end index during the offset array creation requires a stack
    // which is generally costlier than calculating here.
    int getStructureLength(int startIdx, int startOff, char startToken, char endToken) {
        var index = startIdx + 1;
        int depth = 0;
        while (index < this.index) {
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
        if (index >= this.index) {
            throw JsonParser.buildJPE(this,
                    "Braces or brackets do not match.", startOff);
        }
        return index;
    }

    int getEndOffset() {
        return endOffset;
    }

    // gets the char at the specified offset in the input
    char charAt(int offset) {
        return doc[offset];
    }

    // gets the substring at the specified start/end offsets in the input
    String substring(int startOffset, int endOffset) {
        return new String(doc, startOffset, endOffset - startOffset);
    }

    // Utility method to compose parse exception message
    String composeParseExceptionMessage(String message, int row, int prevOff, int offset) {
        return message + ": (%s) at Row %d, Col %d."
                .formatted(substring(offset, Math.min(offset + 8, endOffset)), row, offset - prevOff);
    }
}
