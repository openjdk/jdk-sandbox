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
sealed class JsonObjectImpl implements JsonObject, JsonValueImpl permits JsonObjectLazyImpl {

    private JsonDocumentInfo docInfo;
    int startOffset, endOffset;
    Map<String, JsonValue> theKeys;

    // For use by subclasses
    JsonObjectImpl() {}

    JsonObjectImpl(Map<?, ?> map) {
        docInfo = null;
        startOffset = 0;
        endOffset = 0;
        HashMap<String, JsonValue> m = HashMap.newHashMap(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (!(entry.getKey() instanceof String strKey)) {
                throw new IllegalStateException("Key is not a String: " + entry.getKey());
            } else {
                // Hack to allow JsonObject.Builder to support JsonValue directly
                if (JsonObject.Builder.class.equals(
                        StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE).getCallerClass())
                        && entry.getValue() instanceof JsonValue jVal) {
                    m.put(strKey, jVal);
                } else {
                    m.put(strKey, JsonValue.from(entry.getValue()));
                }
            }
        }
        theKeys = Collections.unmodifiableMap(m);
    }

    public JsonObjectImpl(JsonDocumentInfo docInfo, int offset) {
        this.docInfo = docInfo;
        startOffset = offset;
        endOffset = parseObject(startOffset); // Sets "theKeys"
    }

    // Used by the default (eager) implementation
    // Finds valid keys and JsonValues and inserts until closing bracket encountered
    private int parseObject(int offset) {
        var keys = new HashMap<String, JsonValue>();
        // Walk past the '{'
        offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
        var c = docInfo.charAt(offset);
        while (c != '}') {
            // Get the key, which should be a JsonString
            var key = (JsonStringImpl) JsonParser.parseValue(docInfo, offset);
            var keyString = key.value();
            // Check for duplicate keys
            if (keys.containsKey(keyString)) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Duplicate keys not allowed.", offset), offset);
            }
            // Move from key to ':'
            offset = JsonParser.skipWhitespaces(docInfo, key.getEndOffset());
            c = docInfo.charAt(offset);
            if (c != ':') {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after key: %s.".formatted(key), offset), offset);
            }
            // Move from ':' to JsonValue
            offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
            var val = JsonParser.parseValue(docInfo, offset);
            keys.put(keyString, val);

            // Walk to either ',' or '}'
            offset = JsonParser.skipWhitespaces(docInfo, ((JsonValueImpl)val).getEndOffset());
            c = docInfo.charAt(offset);
            if (c == ',') {
                offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
            } else if (c != '}') {
                // fail
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s.".formatted(val), offset), offset);
            }
        }
        theKeys = Collections.unmodifiableMap(keys);
        return ++offset;
    }

    @Override
    public Map<String, JsonValue> keys() {
        return theKeys;
    }

    @Override
    public JsonValue get(String key) {
        return theKeys.get(key);
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
        return theKeys.containsKey(key);
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

    @Override
    public Map<String, Object> to() {
        return keys().entrySet().stream()
            .collect(HashMap::new, // to allow `null` value
                (m, e) -> m.put(e.getKey(), e.getValue().to()),
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
