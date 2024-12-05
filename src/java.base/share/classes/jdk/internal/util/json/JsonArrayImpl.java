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
import java.util.Objects;

/**
 * JsonArray implementation class
 */
final class JsonArrayImpl implements JsonArray, JsonValueImpl {

    private final JsonDocumentInfo docInfo;
    private final int endIndex;
    private final int startIndex;
    private final int startOffset;
    private final int endOffset; // exclusive
    private List<JsonValue> theValues;

    JsonArrayImpl(List<?> from) {
        List<JsonValue> l = new ArrayList<>(from.size());
        for (Object o : from) {
            l.add(Json.fromUntyped(o));
        }
        theValues = Collections.unmodifiableList(l);
        this.endIndex = 0;
        this.startIndex = 0;
        this.startOffset = 0;
        this.endOffset = 0;
        docInfo = null;
    }

    JsonArrayImpl(JsonDocumentInfo doc, int offset, int index) {
        docInfo = doc;
        startOffset = offset;
        startIndex = index;
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

    // Inflate the JsonArray using the tokens array.
    private void inflate() {
        if (JsonParser.checkWhitespaces(docInfo, startOffset + 1, endOffset - 1)) {
            theValues = Collections.emptyList();
            return;
        }

        var v = new ArrayList<JsonValue>();
        var index = startIndex;
        while (index < endIndex) {
            // Traversal starts on the opening bracket, or a comma
            int offset = docInfo.getOffset(index) + 1;

            // For obj/arr/str we need to walk the comma to get the correct starting index
            if (docInfo.isWalkableStartIndex(docInfo.charAtIndex(index + 1))) {
                index++;
            }

            // Get the value
            var value = JsonParser.parseValue(docInfo, offset, index);
            v.add(value);

            offset = ((JsonValueImpl)value).getEndOffset();
            index = ((JsonValueImpl)value).getEndIndex();

            // Check there is no garbage after the JsonValue
            if (!JsonParser.checkWhitespaces(docInfo, offset, docInfo.getOffset(index))) {
                throw new JsonParseException(docInfo,
                        "Unexpected character(s) found after JsonValue: %s."
                                .formatted(value), offset);
            }

            var c = docInfo.charAtIndex(index);
            if (c != ',' && c != ']') {
                throw new JsonParseException(docInfo,
                        "Unexpected character(s) found after JsonValue: %s."
                                .formatted(value), offset);
            }
        }
        theValues = Collections.unmodifiableList(v);
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1;  // We are interested in the index after ']'
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

    List<Object> toUntyped() {
        return values().stream()
                .map(Json::toUntyped)
                .toList();
    }

    @Override
    public String toString() {
        var s = new StringBuilder("[");
        for (JsonValue v: values()) {
            s.append(v.toString()).append(",");
        }
        if (!values().isEmpty()) {
            s.setLength(s.length() - 1); // trim final comma
        }
        return s.append("]").toString();
    }

    @Override
    public String toDisplayString() {
        return toDisplayString(0, false);
    }

    @Override
    public String toDisplayString(int indent, boolean isField) {
        var prefix = " ".repeat(indent);
        var s = new StringBuilder(isField ? " " : prefix);
        if (values().isEmpty()) {
            s.append("[]");
        } else {
            s.append("[\n");
            for (JsonValue v: values()) {
                if (v instanceof JsonValueImpl impl) {
                    s.append(impl.toDisplayString(indent + INDENT, false)).append(",\n");
                } else {
                    throw new InternalError("type mismatch");
                }
            }
            s.setLength(s.length() - 2); // trim final comma/newline
            s.append("\n").append(prefix).append("]");
        }
        return s.toString();
    }
}
