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

    private JsonDocumentInfo docInfo;
    int startOffset;
    int endOffset;
    int currIndex;
    int endIndex;
    Map<String, JsonValue> theKeys;


    JsonObjectImpl(Map<?, ?> map) {
        HashMap<String, JsonValue> m = HashMap.newHashMap(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String strKey)) {
                throw new IllegalStateException("Key is not a String: " + entry.getKey());
            } else {
                m.put(strKey, Json.fromUntyped(entry.getValue()));
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
        if (theKeys == null) {
            inflate();
        }
        return theKeys;
    }

    // Inflates the JsonObject using the offsets array
    private void inflate() {
        if (JsonParser.checkWhitespaces(docInfo, startOffset + 1, endOffset - 1)) {
            theKeys = Collections.emptyMap();
            return;
        }

        var k = new HashMap<String, JsonValue>();
        while (currIndex < endIndex) {
            // Traversal starts on the opening bracket, or a comma

            // First, validate the key
            int offset = docInfo.getOffset(currIndex + 1);

            // Ensure no garbage before key
            if (!JsonParser.checkWhitespaces(docInfo,
                    docInfo.getOffset(currIndex)+1, docInfo.getOffset(currIndex+1))) {
                throw new JsonParseException(docInfo,
                        "Unexpected character(s) found instead of key.",
                        docInfo.getOffset(currIndex)+1);
            }

            var key = new JsonStringImpl(docInfo, offset, currIndex + 1).value();

            // Ensure no garbage after key and before colon
            if (!JsonParser.checkWhitespaces(docInfo,
                    docInfo.getOffset(currIndex+2)+1, docInfo.getOffset(currIndex+3))) {
                throw new JsonParseException(docInfo,
                        "Unexpected character(s) found after key: \"%s\".".formatted(key),
                        docInfo.getOffset(currIndex+2)+1);
            }

            // Check for the colon
            if (docInfo.charAtIndex(currIndex + 3) != ':') {
                throw new JsonParseException(docInfo,
                        "Invalid key:value syntax.", offset);
            }

            // Check for duplicate keys
            if (k.containsKey(key)) {
                throw new JsonParseException(docInfo,
                        "Duplicate keys not allowed.", offset);
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
            currIndex = ((JsonValueImpl)value).getEndIndex();

            // Check there is no garbage after the JsonValue
            if (!JsonParser.checkWhitespaces(docInfo, offset, docInfo.getOffset(currIndex))) {
                throw new JsonParseException(docInfo,
                        "Unexpected character(s) found after JsonValue: %s, for key: \"%s\"."
                                .formatted(value, key), offset);
            }

            var c = docInfo.charAtIndex(currIndex);
            if (c != ',' && c != '}') {
                throw new JsonParseException(docInfo,
                        "Unexpected character(s) found after JsonValue: %s, for key: \"%s\"."
                                .formatted(value, key), offset);
            }
        }
        theKeys = Collections.unmodifiableMap(k);
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1; // We are interested in the index after '}'
    }

    @Override
    public int getEndOffset() {
        return endOffset;
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

    Map<String, Object> toUntyped() {
        return keys().entrySet().stream()
            .collect(HashMap::new, // to allow `null` value
                (m, e) -> m.put(e.getKey(), Json.toUntyped(e.getValue())),
                HashMap::putAll);
    }

    @Override
    public String toString() {
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
    public String toDisplayString() {
        return toDisplayString(0, false);
    }

    @Override
    public String toDisplayString(int indent, boolean isField) {
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
                                .append(val.toDisplayString(indent + INDENT, true))
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
}
