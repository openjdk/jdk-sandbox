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

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.Stable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JsonObject implementation class
 */
@ValueBased
final class JsonObjectImpl implements JsonObject, JsonValueImpl {

    private final JsonDocumentInfo docInfo;
    private final int startIndex;
    private final int endIndex;
    @Stable
    private Map<String, JsonValue> theMembers;

    JsonObjectImpl(Map<String, ? extends JsonValue> map) {
        // Map.copyOf() does not preserve insertion-order
        theMembers = Collections.unmodifiableMap(new LinkedHashMap<>(map));
        // We check for null key with LHM, because checking with the passed Map impl
        // may vary depending on implementation type
        if (theMembers.containsKey(null)) {
            throw new NullPointerException("Key is not a String");
        }
        docInfo = null;
        startIndex = 0;
        endIndex = 0;
    }

    JsonObjectImpl(JsonDocumentInfo doc, int index) {
        docInfo = doc;
        startIndex = index;
        endIndex = startIndex == 0 ? docInfo.getIndexCount() - 1 // For root
                : docInfo.nextIndex(index, '{', '}');
    }

    @Override
    public Map<String, JsonValue> members() {
        if (theMembers == null) {
            theMembers = inflate();
        }
        return theMembers;
    }

    // Inflates the JsonObject using the tokens array
    private Map<String, JsonValue> inflate() {
        var k = new LinkedHashMap<String, JsonValue>();
        var index = startIndex + 1;
        // Empty case automatically checked by index increment. {} is 2 tokens
        while (index < endIndex) {
            // Member name should be source string, not unescaped
            // Member equality is done via unescaped in JsonParser
            var key = docInfo.substring(
                    docInfo.getOffset(index) + 1, docInfo.getOffset(index + 1));
            index = index + 2;

            // Get value
            int offset = docInfo.getOffset(index) + 1;
            if (docInfo.shouldWalkToken(docInfo.charAtIndex(index + 1))) {
                index++;
            }
            var value = JsonFactory.createValue(docInfo, offset, index);

            // Store key and value
            k.put(key, value);
            // Move to the next key
            index = ((JsonValueImpl)value).getEndIndex() + 1;
        }
        return Collections.unmodifiableMap(k);
    }

    @Override
    public int getEndIndex() {
        return endIndex + 1; // We are interested in the index after '}'
    }

    @Override
    public String toString() {
        var s = new StringBuilder("{");
        for (Map.Entry<String, JsonValue> kv: members().entrySet()) {
            s.append("\"").append(kv.getKey()).append("\":")
             .append(kv.getValue().toString())
             .append(",");
        }
        if (!members().isEmpty()) {
            s.setLength(s.length() - 1); // trim final comma
        }
        return s.append("}").toString();
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonObject ojo &&
                Objects.equals(members(), ojo.members());
    }

    @Override
    public int hashCode() {
        return Objects.hash(members());
    }
}
