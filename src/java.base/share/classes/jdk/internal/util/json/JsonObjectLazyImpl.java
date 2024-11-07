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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * JsonObject lazy implementation subclass
 */
final class JsonObjectLazyImpl extends JsonObjectImpl implements JsonValueLazyImpl {

    private final JsonLazyDocumentInfo docInfo;
    private int currIndex;
    private final int endIndex;
    private boolean inflated;

    JsonObjectLazyImpl(JsonLazyDocumentInfo docInfo, int offset, int index) {
        this.docInfo = docInfo;
        startOffset = offset;
        currIndex = index;
        endIndex = docInfo.getStructureLength(index, offset, '{', '}');
        endOffset = docInfo.getOffset(endIndex) + 1;
    }

    @Override
    public Map<String, JsonValue> keys() {
        if (!inflated) {
            inflateAll();
        }
        return theKeys;
    }

    @Override
    public JsonValue get(String key) {
        JsonValue val;
        if (theKeys == null) {
            val = inflateUntilMatch(key);
        } else {
            // Search for key in hashmap first, otherwise offsets
            val = theKeys.get(key);
            if (val == null) {
                if (inflated) {
                    return null;
                } else {
                    val = inflateUntilMatch(key);
                }
            }
        }
        return val;
    }

    @Override
    public boolean contains(String key) {
        // See if we already have it, otherwise continue inflation to check
        JsonValue val;
        if (theKeys == null) {
            val = inflateUntilMatch(key);
        } else {
            if (!theKeys.containsKey(key)) {
                if (inflated) {
                    val = null;
                } else {
                    val = inflateUntilMatch(key);
                }
            } else {
                return true;
            }
        }
        return val != null;
    }

    // Inflate the entire map
    void inflateAll() {
        inflate(null);
    }

    // Upon match, return the key and defer the rest of inflation
    // Otherwise, if no match, returns null
    private JsonValue inflateUntilMatch(String key) {
        return inflate(key);
    }

    private JsonValue inflate(String searchKey) {
        if (inflated) {
            throw new InternalError("JsonObject is already inflated");
        }

        if (theKeys == null) { // first time init
            if (JsonParser.checkWhitespaces(docInfo, startOffset + 1, endOffset - 1)) {
                theKeys = Collections.emptyMap();
                inflated = true;
                return null;
            }
            theKeys = new HashMap<>();
        }

        var k = theKeys;
        while (currIndex < endIndex) {
            // Traversal starts on the opening bracket, or a comma
            // We need to parse a key, a value and ensure that there
            // is no added garbage within our key/value pair
            // As the initial creation was done lazily, we validate now
            int offset;
            var keyOffset = docInfo.getOffset(currIndex + 1);

            // Check the key indices are as expected
            if (docInfo.charAtIndex(currIndex + 1) != '"' ||
                    docInfo.charAtIndex(currIndex + 2) != '"' ||
                    docInfo.charAtIndex(currIndex + 3) != ':') {
                offset = keyOffset;
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Invalid key:value syntax.", offset), offset);
            }

            // Ensure no garbage before key
            JsonParser.failIfWhitespaces(docInfo,
                    docInfo.getOffset(currIndex)+1, docInfo.getOffset(currIndex+1),
                    "Unexpected character(s) found instead of key.");

            var key = new JsonStringLazyImpl(docInfo, keyOffset, currIndex + 1).value();

            // Ensure no garbage after key and before colon
            JsonParser.failIfWhitespaces(docInfo,
                    docInfo.getOffset(currIndex+2)+1, docInfo.getOffset(currIndex+3),
                    "Unexpected character(s) found after key: \"%s\".".formatted(key));

            // Check for duplicate keys
            if (k.containsKey(key)) {
                offset = keyOffset;
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Duplicate keys not allowed.", offset), offset);
            }

            boolean shouldWalk = false;
            offset = docInfo.getOffset(currIndex + 3) + 1;
            if (docInfo.isWalkableStartIndex(docInfo.charAtIndex(currIndex + 4))) {
                shouldWalk = true;
                currIndex = currIndex + 4;
            } else {
                currIndex = currIndex + 3;
            }

            var value = JsonParser.parseValue(docInfo, offset, currIndex);
            k.put(key, value);

            offset = ((JsonValueImpl)value).getEndOffset();
            currIndex = ((JsonValueLazyImpl)value).getEndIndex();

            if (shouldWalk) {
                currIndex++;
            }

            // Check there is no garbage after the JsonValue
            JsonParser.failIfWhitespaces(docInfo, offset, docInfo.getOffset(currIndex),
                    "Unexpected character(s) found after JsonValue: %s, for key: \"%s\".".formatted(value, key));


            var c = docInfo.charAtIndex(currIndex);
            if (c == ',' || c == '}') {
                if (searchKey != null && searchKey.equals(key)) {
                    if (c == '}') {
                        inflated = true;
                        theKeys = Collections.unmodifiableMap(k);
                    }
                    return value;
                }
                if (c == '}') {
                    break;
                }
            } else {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s, for key: \"%s\"."
                                .formatted(value, key), offset), offset);
            }
        }
        // inflated, so make unmodifiable
        inflated = true;
        theKeys = Collections.unmodifiableMap(k);
        return null;
    }

    @Override
    public int getEndIndex() {
        return endIndex;
    }
}
