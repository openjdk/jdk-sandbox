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

    JsonArrayLazyImpl(JsonLazyDocumentInfo docInfo, int offset, int index) {
        this.docInfo = docInfo;
        startOffset = offset;
        currIndex = index;
        endIndex = docInfo.getStructureLength(index, offset, '[', ']');
        endOffset = docInfo.getOffset(endIndex) + 1;
    }

    @Override
    public List<JsonValue> values() {
        if (theValues == null) {
            inflate();
        }
        return theValues;
    }

    // Inflate the JsonArray using the offsets array.
    private void inflate() {
        if (JsonParser.checkWhitespaces(docInfo, startOffset + 1, endOffset - 1)) {
            theValues = Collections.emptyList();
            return;
        }

        var v = new ArrayList<JsonValue>();
        while (currIndex < endIndex) {
            // Traversal starts on the opening bracket, or a comma
            int offset = docInfo.getOffset(currIndex) + 1;

            // For obj/arr/str we need to walk the comma to get the correct starting index
            if (docInfo.isWalkableStartIndex(docInfo.charAtIndex(currIndex + 1))) {
                currIndex++;
            }

            // Get the value
            var value = JsonParser.parseValue(docInfo, offset, currIndex);
            v.add(value);

            offset = ((JsonValueImpl)value).getEndOffset();
            currIndex = ((JsonValueLazyImpl)value).getEndIndex();

            // Check there is no garbage after the JsonValue
            if (!JsonParser.checkWhitespaces(docInfo, offset, docInfo.getOffset(currIndex))) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s."
                                .formatted(value), offset), offset);
            }

            var c = docInfo.charAtIndex(currIndex);
            if (c != ',' && c != ']') {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s."
                                .formatted(value), offset), offset);
            }
        }
        theValues = Collections.unmodifiableList(v);
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1;  // We are interested in the index after ']'
    }
}
