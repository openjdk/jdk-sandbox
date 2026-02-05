/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import jdk.internal.util.json.JsonArrayImpl;
import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.json.Utils;

/**
 * The interface that represents JSON array.
 * <p>
 * A {@code JsonArray} can be produced by {@link Json#parse(String)}.
 * <p> Alternatively, {@link #of(List)} can be used to obtain a {@code JsonArray}.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259#section-5 RFC 8259:
 *      The JavaScript Object Notation (JSON) Data Interchange Format - Arrays
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public non-sealed interface JsonArray extends JsonValue {

    /**
     * {@inheritDoc}
     */
    @Override
    List<JsonValue> elements();

    /**
     * {@inheritDoc}
     *
     * @param index {@inheritDoc}
     * @throws JsonAssertionException if the given index is out of bounds
     */
    default JsonValue element(int index) {
        // Overridden to specify
        return JsonValue.super.element(index);
    }

    /**
     * {@return the {@code JsonArray} created from the given
     * list of {@code JsonValue}s}
     *
     * @param src the list of {@code JsonValue}s. Non-null.
     * @throws NullPointerException if {@code src} is {@code null}, or contains
     *      any values that are {@code null}
     */
    static JsonArray of(List<? extends JsonValue> src) {
        // Careful not to use List::contains on src for null checking which
        // throws NPE for immutable lists
        return new JsonArrayImpl(src
                .stream()
                .map(Objects::requireNonNull)
                .collect(Collectors.toCollection(ArrayList::new))
        );
    }

    /**
     * {@return {@code true} if the given object is also a {@code JsonArray}
     * and the two {@code JsonArray}s represent the same elements} Two
     * {@code JsonArray}s {@code ja1} and {@code ja2} represent the same
     * elements if {@code ja1.elements().equals(ja2.elements())}.
     *
     * @see #elements()
     */
    @Override
    boolean equals(Object obj);

    /**
     * {@return the hash code value for this {@code JsonArray}} The hash code value
     * of a {@code JsonArray} is derived from the hash code of {@code JsonArray}'s
     * {@link #elements()}.
     * Thus, for two {@code JsonArray}s {@code ja1} and {@code ja2},
     * {@code ja1.equals(ja2)} implies that {@code ja1.hashCode() == ja2.hashCode()}
     * as required by the general contract of {@link Object#hashCode}.
     *
     * @see #elements()
     */
    @Override
    int hashCode();
}
