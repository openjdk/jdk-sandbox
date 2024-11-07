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

import java.util.Objects;

/**
 * JsonNull implementation class
 */
sealed class JsonNullImpl implements JsonNull, JsonValueImpl permits JsonNullLazyImpl {

    JsonDocumentInfo docInfo;
    int startOffset;
    int endOffset;

    static final JsonNullImpl NULL = new JsonNullImpl();
    static final String VALUE = "null";
    static final int HASH = Objects.hash(VALUE);

    // For use by subclasses
    JsonNullImpl() {}

    JsonNullImpl(JsonDocumentInfo docInfo, int offset) {
        this.docInfo = docInfo;
        startOffset = offset;
        endOffset = offset + 4;
        validate();
    }

    void validate() {
        if (!VALUE.equals(docInfo.substring(startOffset, endOffset).trim())) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "'null' expected.", startOffset), startOffset);
        }
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNullImpl;
    }

    @Override
    public int hashCode() {
        return HASH;
    }

    @Override
    public Object to() {
        return null;
    }

    @Override
    public String toString() {
        return VALUE;
    }

    @Override
    public String formatCompact() {
        return VALUE;
    }

    @Override
    public String formatReadable() {
        return formatReadable(0, false);
    }

    @Override
    public String formatReadable(int indent, boolean isField) {
        return " ".repeat(isField ? 1 : indent) + VALUE;
    }
}
