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
import java.util.Objects;
import java.util.Optional;

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
        Objects.requireNonNull(key); // RFC8259 does not permit null keys
        Optional<JsonValue> val;
        if (theKeys == null) {
            val = inflateUntilMatch(key);
        } else {
            // Search for key in hashmap first, otherwise offsets
            val = Optional.ofNullable(theKeys.get(key));
            if (val.isEmpty()) {
                if (!inflated) {
                    val = inflateUntilMatch(key);
                }
            }
        }
        return val.orElse(null);
    }

    @Override
    public boolean contains(String key) {
        return get(key) != null;
    }

    // Inflate the entire map
    void inflateAll() {
        inflate(null);
    }

    // Upon match, return the key and defer the rest of inflation
    // Otherwise, if no match, returns empty Optional
    private Optional<JsonValue> inflateUntilMatch(String key) {
        return inflate(key);
    }

    /*
     * Inflates the JsonObject using the offsets array. Callers should not call
     * this method if the JsonObject is already inflated.
     *
     * @param searchKey a String key to stop inflation on. Can be null as well to ensure
     *        the full document is inflated.
     * @throws JsonParseException if the JSON syntax is violated or there exists a
     *         duplicate key
     * @return an Optional<JsonValue> that contains a JsonValue if searchKey was found,
     *         otherwise empty
     */
    private Optional<JsonValue> inflate(String searchKey) {

        Optional<JsonValue> ret = Optional.empty();
        if (theKeys == null) { // first time init
            if (JsonParser.checkWhitespaces(docInfo, startOffset + 1, endOffset - 1)) {
                theKeys = Collections.emptyMap();
                inflated = true;
                return ret;
            }
            theKeys = new HashMap<>();
        }

        var k = theKeys;
        while (currIndex < endIndex) {
            // Traversal starts on the opening bracket, or a comma
            // First, validate the key
            int offset = docInfo.getOffset(currIndex + 1);

            // Ensure no garbage before key
            if (!JsonParser.checkWhitespaces(docInfo,
                    docInfo.getOffset(currIndex)+1, docInfo.getOffset(currIndex+1))) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found instead of key.",
                        docInfo.getOffset(currIndex)+1), docInfo.getOffset(currIndex)+1);
            }

            var key = new JsonStringLazyImpl(docInfo, offset, currIndex + 1).value();

            // Ensure no garbage after key and before colon
            if (!JsonParser.checkWhitespaces(docInfo,
                    docInfo.getOffset(currIndex+2)+1, docInfo.getOffset(currIndex+3))) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after key: \"%s\".".formatted(key),
                        docInfo.getOffset(currIndex+2)+1), docInfo.getOffset(currIndex+2)+1);
            }

            // Check for the colon
            if (docInfo.charAtIndex(currIndex + 3) != ':') {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Invalid key:value syntax.",offset), offset);
            }

            // Check for duplicate keys
            if (k.containsKey(key)) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Duplicate keys not allowed.", offset), offset);
            }

            // Key is validated. Move offset and index to colon to get the value
            currIndex = currIndex + 3;
            offset = docInfo.getOffset(currIndex) + 1;

            // For obj/arr/str we need to walk the colon to get the correct starting index
            if (docInfo.isWalkableStartIndex(docInfo.charAtIndex(currIndex + 1))) {
                currIndex++;
            }

            // Get the value
            var value = JsonParser.parseValue(docInfo, offset, currIndex);
            k.put(key, value);

            offset = ((JsonValueImpl)value).getEndOffset();
            currIndex = ((JsonValueLazyImpl)value).getEndIndex();

            // Check there is no garbage after the JsonValue
            if (!JsonParser.checkWhitespaces(docInfo, offset, docInfo.getOffset(currIndex))) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s, for key: \"%s\"."
                                .formatted(value, key), offset), offset);
            }

            var c = docInfo.charAtIndex(currIndex);
            if (c == ',' || c == '}') {
                if (Objects.equals(searchKey, key)) {
                    ret = Optional.of(value);
                    return ret;
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
        return ret;
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1; // We are interested in the index after '}'
    }
}
