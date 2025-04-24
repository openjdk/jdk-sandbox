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
import java.util.List;
import java.util.Objects;

/**
 * JsonArray implementation class
 */
@ValueBased
final class JsonArrayImpl implements JsonArray, JsonValueImpl {

    private final int endOffset;
    @Stable
    private final List<JsonValue> theValues;

    JsonArrayImpl(List<JsonValue> from) {
        theValues = from;
        // unused
        endOffset = -1;
    }

    JsonArrayImpl(List<JsonValue> from, int end) {
        theValues = from;
        endOffset = end;
    }

    @Override
    public List<JsonValue> values() {
        return Collections.unmodifiableList(theValues);
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

    @Override
    public int getEndOffset() {
        return endOffset;
    }
}
