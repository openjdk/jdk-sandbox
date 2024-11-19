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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * JsonArray implementation class
 */
sealed class JsonArrayImpl implements JsonArray, JsonValueImpl permits JsonArrayLazyImpl {

    private JsonDocumentInfo docInfo;
    int startOffset;
    int endOffset; // exclusive
    List<JsonValue> theValues;

    // For use by subclasses
    JsonArrayImpl() {}

    JsonArrayImpl(List<?> from) {
        docInfo = null;
        startOffset = 0;
        endOffset = 0;
        List<JsonValue> l = new ArrayList<>(from.size());
        for (Object o : from) {
            l.add(JsonValue.from(o));
        }
        theValues = Collections.unmodifiableList(l);
    }

    JsonArrayImpl(JsonValue... values) {
        docInfo = null;
        startOffset = 0;
        endOffset = 0;
        theValues = Arrays.asList(values);
    }

    JsonArrayImpl(JsonDocumentInfo docInfo, int offset) {
        this.docInfo = docInfo;
        startOffset = offset;
        endOffset = parseArray(startOffset); // Sets "theValues"
    }

    // Used by the default (eager) implementation
    // Finds valid JsonValues and inserts until closing bracket encountered
    private int parseArray(int offset) {
        var vals = new ArrayList<JsonValue>();
        // Walk initial '['
        offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
        var c = docInfo.charAt(offset);
        while (c != ']') {
            // Get the JsonValue
            var val = JsonParser.parseValue(docInfo, offset);
            vals.add(val);
            // Walk to either ',' or ']'
            offset = JsonParser.skipWhitespaces(docInfo, ((JsonValueImpl)val).getEndOffset());
            c = docInfo.charAt(offset);
            if (c == ',') {
                offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
            } else if (c != ']') {
                // fail
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Unexpected character(s) found after JsonValue: %s.".formatted(val), offset), offset);
            }
        }
        theValues = Collections.unmodifiableList(vals);
        return ++offset;
    }

    @Override
    public List<JsonValue> values() {
        return theValues;
    }

    @Override
    public Stream<JsonValue> stream() {
        return values().stream();
    }

    @Override
    public JsonValue get(int index) {
        return theValues.get(index);
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonArrayImpl ojai &&
            Objects.equals(values(), ojai.values());
    }

    @Override
    public int hashCode() {
        return Objects.hash(values());
    }

    @Override
    public List<Object> to() {
        return values().stream()
                .map(JsonValue::to)
                .toList();
    }

    @Override
    public String toString() {
        return formatCompact();
    }

    @Override
    public String format(Option... options) {
        for (var o : options) {
            if (o == Option.Format.PRETTY_PRINT) {
                return formatReadable();
            }
        }
        return formatCompact();
    }

    String formatCompact() {
        var s = new StringBuilder("[");
        for (JsonValue v: values()) {
            s.append(v.toString()).append(",");
        }
        if (!values().isEmpty()) {
            s.setLength(s.length() - 1); // trim final comma
        }
        return s.append("]").toString();
    }

    String formatReadable() {
        return formatReadable(0, false);
    }

    @Override
    public String formatReadable(int indent, boolean isField) {
        var prefix = " ".repeat(indent);
        var s = new StringBuilder(isField ? " " : prefix);
        if (values().isEmpty()) {
            s.append("[]");
        } else {
            s.append("[\n");
            for (JsonValue v: values()) {
                if (v instanceof JsonValueImpl impl) {
                    s.append(impl.formatReadable(indent + INDENT, false)).append(",\n");
                } else {
                    throw new InternalError("type mismatch");
                }
            }
            s.setLength(s.length() - 2); // trim final comma/newline
            s.append("\n").append(prefix).append("]");
        }
        return s.toString();
    }

    @Override
    public int size() {
        return values().size();
    }
}
