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

import jdk.internal.javac.PreviewFeature;

import java.util.List;
import java.util.Objects;

/**
 * The interface that represents JSON array.
 * <p>
 * A {@code JsonArray} can be produced by {@link Json#parse(String)}.
 * <p> Alternatively, {@link #of(List)} can be used to obtain a {@code JsonArray}.
 *
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public sealed interface JsonArray extends JsonValue permits JsonArrayImpl {

    /**
     * {@return an unmodifiable list of the {@code JsonValue} elements in
     * this {@code JsonArray}}
     */
    List<JsonValue> values();

    /**
     * {@return the {@code JsonArray} created from the given
     * list of {@code JsonValue}s}
     *
     * @param src the list of {@code JsonValue}s. Non-null.
     * @throws NullPointerException if {@code src} is {@code null}
     */
    static JsonArray of(List<? extends JsonValue> src) {
        return new JsonArrayImpl(Objects.requireNonNull(src));
    }

    /**
     * {@return {@code true} if and only if the specified object is also a
     * {@code JsonArray}, both {@code JsonArray}s have the same size, and
     * all corresponding pairs of elements in the two {@code JsonArray}s
     * are equal}
     */
    boolean equals(Object obj);

    /**
     * {@return the hash code value for this {@code JsonArray}} The hash code of a
     * {@code JsonArray} is calculated by {@code Objects.hash(JsonArray.values()}.
     * This ensures that {@code ja1.equals(ja2)} implies that
     * {@code ja1.hashCode()==ja2.hashCode()} for any two {@code JsonArray}s,
     * {@code ja1} and {@code ja2}, as required by the general contract
     * of {@link Object#hashCode}.
     */
    int hashCode();
}
