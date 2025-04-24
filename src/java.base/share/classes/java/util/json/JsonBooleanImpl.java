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

import java.util.Objects;

/**
 * JsonBoolean implementation class
 */
final class JsonBooleanImpl implements JsonBoolean, JsonValueImpl {

    private final JsonDocumentInfo docInfo;
    private final int startOffset;
    private final int endOffset;
    @Stable
    private Boolean theBoolean;

    static final JsonBooleanImpl TRUE = new JsonBooleanImpl(true);
    static final JsonBooleanImpl FALSE = new JsonBooleanImpl(false);

    JsonBooleanImpl(Boolean bool) {
        theBoolean = bool;
        // unused
        docInfo = null;
        startOffset = -1;
        endOffset = -1;
    }

    JsonBooleanImpl(JsonDocumentInfo doc, int start, int end) {
        docInfo = doc;
        startOffset = start;
        endOffset = end;
    }

    @Override
    public boolean value() {
        if (theBoolean == null) {
            theBoolean = docInfo.charAt(startOffset) == 't';
        }
        return theBoolean;
    }

    @Override
    public String toString() {
        return String.valueOf(value());
    }

    @Override
    public boolean equals(Object obj) {
        return this == obj ||
            obj instanceof JsonBoolean ojb &&
                Objects.equals(value(), ojb.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value());
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }
}
