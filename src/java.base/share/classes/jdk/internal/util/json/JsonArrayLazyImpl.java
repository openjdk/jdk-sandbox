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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * JsonArray lazy implementation subclass
 */
final class JsonArrayLazyImpl extends JsonArrayImpl implements JsonValueLazyImpl {

    private final JsonLazyDocumentInfo docInfo;
    private final int endIndex;
    private int currIndex;
    private boolean inflated;

    JsonArrayLazyImpl(JsonLazyDocumentInfo docInfo, int offset, int index) {
        this.docInfo = docInfo;
        startOffset = offset;
        currIndex = index;
        endIndex = docInfo.getStructureLength(index, offset, '[', ']');
        endOffset = docInfo.getOffset(endIndex) + 1;
    }

    @Override
    public List<JsonValue> values() {
        if (!inflated) {
            inflateAll();
        }
        return theValues;
    }

    @Override
    public JsonValue get(int index) {
        JsonValue val;
        if (theValues == null) {
            val = inflateUntilMatch(index);
        } else {
            // Search for key in list first, otherwise offsets
            if (theValues.size() - 1 >= index) {
                val = theValues.get(index);
            } else if (inflated) {
                throw new IndexOutOfBoundsException(
                        String.format("Index %s is out of bounds for length %s", index, theValues.size()));
            } else {
                val = inflateUntilMatch(index);
            }
        }
        return val;
    }

    // Inflate the entire list
    private void inflateAll() {
        inflate(-1);
    }

    // Inflate until the index is created in the array
    // If no match, should throw IOOBE
    private JsonValue inflateUntilMatch(int index) {
        var val = inflate(index);
        // null returned on no match, fail
        if (val == null) {
            throw new IndexOutOfBoundsException(
                    String.format("Index %s is out of bounds for length %s", index, theValues.size()));
        }
        return val;
    }

    private JsonValue inflate(int searchIndex) {
        if (inflated) {
            throw new InternalError("JsonArray is already inflated");
        }

        if (theValues == null) { // first time init
            if (JsonParser.checkWhitespaces(docInfo, startOffset + 1, endOffset - 1)) {
                theValues = Collections.emptyList();
                inflated = true;
                return null;
            }
            theValues = new ArrayList<>();
        }

        var v = theValues;
        while (currIndex < endIndex) {
            // Traversal starts on the opening bracket, or a comma
            int offset = docInfo.getOffset(currIndex) + 1;

            // For obj/arr we need to walk the comma to get the correct starting index
            if (docInfo.isWalkableStartIndex(docInfo.charAtIndex(currIndex + 1))) {
                currIndex++;
            }

            var value = JsonParser.parseValue(docInfo, offset, currIndex);
            v.add(value);

            offset = ((JsonValueImpl)value).getEndOffset();
            currIndex = ((JsonValueLazyImpl)value).getEndIndex();

            // Check that there is only a single valid JsonValue
            // Between the end of the value and the next index, there should only be WS
            if (!JsonParser.checkWhitespaces(docInfo, offset, docInfo.getOffset(currIndex))) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s."
                                .formatted(value), offset), offset);
            }

            var c = docInfo.charAtIndex(currIndex);
            if (c == ',' || c == ']') {
                if (searchIndex == theValues.size() - 1) {
                    if (c == ']') {
                        inflated = true;
                        theValues = Collections.unmodifiableList(v);
                    }
                    return value;
                }
            } else {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s."
                                .formatted(value), offset), offset);
            }
        }
        // inflated, so make unmodifiable
        inflated = true;
        theValues = Collections.unmodifiableList(v);
        return null;
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1;
    }
}
