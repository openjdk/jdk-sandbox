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

/**
 * JsonObject implementation class
 */
final class JsonObjectImpl implements JsonObject, JsonValueImpl {
    private final JsonDocumentInfo docInfo;
    private final int startOffset, endOffset;
    private final int endIndex;
    Map<String, JsonValue> theKeys;
    // For lazy inflation
    private int currIndex;
    boolean inflated;

    JsonObjectImpl(Map<?, ?> map) {
        docInfo = null;
        startOffset = 0;
        endOffset = 0;
        endIndex = 0;
        inflated = true;
        HashMap<String, JsonValue> m = HashMap.newHashMap(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String strKey)) {
                throw new IllegalStateException("Key is not a String: " + entry.getKey());
            } else {
                if (entry.getValue() instanceof JsonValue jVal) {
                    m.put(strKey, jVal);
                } else {
                    m.put(strKey, JsonValue.from(entry.getValue()));
                }
            }
        }
        theKeys = Collections.unmodifiableMap(m);
    }

    JsonObjectImpl(JsonDocumentInfo docInfo, int offset, int index) {
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
    public JsonValue getOrDefault(String key, JsonValue defaultValue) {
        var val = get(key);
        if (val == null) {
            val = defaultValue;
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

    @Override
    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public int getEndIndex() {
        return endIndex;
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonObjectImpl ojoi &&
            Objects.equals(keys(), ojoi.keys());
    }

    @Override
    public int hashCode() {
        return Objects.hash(keys());
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

    // Used for eager or lazy inflation
    private JsonValue inflate(String searchKey) {
        if (inflated) { // prevent misuse
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
            if (!JsonParser.checkWhitespaces(docInfo,
                    docInfo.getOffset(currIndex)+1, docInfo.getOffset(currIndex+1))) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found instead of key.",
                        docInfo.getOffset(currIndex)+1), docInfo.getOffset(currIndex)+1);
            }

            var key = docInfo.unescape(keyOffset + 1,
                    docInfo.getOffset(currIndex + 2));

            // Ensure no garbage after key and before colon
            if (!JsonParser.checkWhitespaces(docInfo,
                    docInfo.getOffset(currIndex+2)+1, docInfo.getOffset(currIndex+3))) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after key: \"%s\".".formatted(key),
                        docInfo.getOffset(currIndex+2)+1), docInfo.getOffset(currIndex+2)+1);
            }

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
            currIndex = ((JsonValueImpl)value).getEndIndex();

            if (shouldWalk) {
                currIndex++;
            }

            // Check there is no garbage after the JsonValue
            if (!JsonParser.checkWhitespaces(docInfo, offset, docInfo.getOffset(currIndex))) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s, for key: \"%s\"."
                                .formatted(value, key), offset), offset);
            }


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
    public Map<String, Object> toUntyped() {
        return keys().entrySet().stream()
            .collect(HashMap::new, // to allow `null` value
                (m, e) -> m.put(e.getKey(), e.getValue().toUntyped()),
                HashMap::putAll);
    }

    @Override
    public String toString() {
        return formatCompact();
    }

    @Override
    public String formatCompact() {
        var s = new StringBuilder("{");
        for (Map.Entry<String, JsonValue> kv: keys().entrySet()) {
            s.append("\"").append(kv.getKey()).append("\":")
             .append(kv.getValue().toString())
             .append(",");
        }
        if (!keys().isEmpty()) {
            s.setLength(s.length() - 1); // trim final comma
        }
        return s.append("}").toString();
    }

    @Override
    public String formatReadable() {
        return formatReadable(0, false);
    }

    @Override
    public String formatReadable(int indent, boolean isField) {
        var prefix = " ".repeat(indent);
        var s = new StringBuilder(isField ? " " : prefix);
        if (keys().isEmpty()) {
            s.append("{}");
        } else {
            s.append("{\n");
            keys().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String::compareTo))
                .forEach(e -> {
                    var key = e.getKey();
                    var value = e.getValue();
                    if (value instanceof JsonValueImpl val) {
                        s.append(prefix)
                                .append(" ".repeat(INDENT))
                                .append("\"")
                                .append(key)
                                .append("\":")
                                .append(val.formatReadable(indent + INDENT, true))
                                .append(",\n");
                    } else {
                        throw new IllegalStateException("type mismatch");
                    }
                });
            s.setLength(s.length() - 2); // trim final comma
            s.append("\n").append(prefix).append("}");
        }
        return s.toString();
    }

    @Override
    public int size() {
        return keys().size();
    }
}
