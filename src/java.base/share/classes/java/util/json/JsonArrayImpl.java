/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.vm.annotation.Stable;

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
    @Stable
    private List<JsonValue> theValues;

    JsonArrayImpl(List<? extends JsonValue> from) {
        theValues = List.copyOf(from);
        this.endIndex = 0;
        this.startIndex = 0;
        this.startOffset = 0;
        docInfo = null;
    }

    JsonArrayImpl(JsonDocumentInfo doc, int offset, int index) {
        docInfo = doc;
        startOffset = offset;
        startIndex = index;
        endIndex = startIndex == 0 ? docInfo.getIndexCount() - 1 // For root
                : docInfo.nextIndex(index, '[', ']');
    }

    @Override
    public List<JsonValue> values() {
        if (theValues == null) {
            theValues = inflate();
        }
        return theValues;
    }

    // Inflate the JsonArray using the tokens array.
    private List<JsonValue> inflate() {
        if (docInfo.charAt(JsonParser.skipWhitespaces(docInfo, startOffset + 1)) == ']') {
            return Collections.emptyList();
        }
        var v = new ArrayList<JsonValue>();
        var index = startIndex;
        while (index < endIndex) { // start on comma or opening bracket
            // Get Val
            int offset = docInfo.getOffset(index) + 1;
            if (docInfo.shouldWalkToken(docInfo.charAtIndex(index + 1))) {
                index++;
            }
            var value = JsonFactory.createValue(docInfo, offset, index);
            v.add(value);
            index = ((JsonValueImpl)value).getEndIndex(); // Move to comma or closing
        }
        return Collections.unmodifiableList(v);
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1;  // We are always interested in the index after ']'
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
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonArray oja &&
                Objects.equals(values(), oja.values());
    }

    @Override
    public int hashCode() {
        return Objects.hash(values());
    }
}
