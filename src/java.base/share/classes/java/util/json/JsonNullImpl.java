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

package java.util.json;

import java.util.Objects;

/**
 * JsonNull implementation class
 */
final class JsonNullImpl implements JsonNull, JsonValueImpl {

    private final JsonDocumentInfo docInfo;
    private final int endOffset;
    private final int endIndex;

    static final JsonNullImpl NULL = new JsonNullImpl();
    static final String VALUE = "null";
    static final int HASH = Objects.hash(VALUE);

    JsonNullImpl() {
        endOffset = 0;
        endIndex = 0;
        docInfo = null;
    }

    JsonNullImpl(JsonDocumentInfo doc, int offset, int index) {
        docInfo = doc;
        endIndex = docInfo.nextIndex(index);
        endOffset = endIndex != -1 ? docInfo.getOffset(endIndex) : docInfo.getEndOffset();
    }

    @Override
    public int getEndIndex() {
        return endIndex;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNullImpl;
    }

    @Override
    public int hashCode() {
        return HASH;
    }

    Object toUntyped() {
        return null;
    }

    @Override
    public String toString() {
        return VALUE;
    }

    @Override
    public String toDisplayString() {
        return toDisplayString(0, false);
    }

    @Override
    public String toDisplayString(int indent, boolean isField) {
        return " ".repeat(isField ? 1 : indent) + VALUE;
    }
}
