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

import java.util.Objects;

/**
 * The interface that represents JSON string.
 * <p>
 * A {@code JsonString} can be produced by a {@link Json#parse(String)}.
 * <p> Alternatively, {@link #of(String)} can be used to obtain a {@code JsonString}
 * from a {@code String}.
 *
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public non-sealed interface JsonString extends JsonValue {

    /**
     * {@return the {@code String} value represented by this
     * {@code JsonString}} Any escaped characters in the original
     * JSON string are converted to their unescaped form in the
     * returned {@code String}. This is different from {@link #toString()},
     * which keeps the escaped characters as they are.
     * For example,
     * {@snippet lang=java:
     *     JsonString.of("fo\\u006f").value(); // returns "foo"
     * }
     * @throws IllegalStateException if the source JSON string cannot
     *          be unescaped.
     */
    String value();

    /**
     * {@return the {@code JsonString} created from the given
     * {@code String}}
     *
     * @param src the given {@code String}. Non-null.
     * @throws IllegalArgumentException if the given {@code src} is
     *          not a valid JSON string.
     * @throws NullPointerException if {@code src} is {@code null}
     */
    static JsonString of(String src) {
        Objects.requireNonNull(src);
        return new JsonStringImpl(src);
    }

    /**
     * {@return the String representation of this {@code JsonString}}
     * If this {@code JsonString} is created by parsing a JSON document,
     * it preserves the original text representation, including any escaped
     * characters.
     * For example,
     * {@snippet lang=java:
     *     JsonString.of("fo\\u006f").toString(); // returns "\"fo\\u006f\""
     * }
     */
    String toString();

    /**
     * {@return true if the given {@code obj} is equal to this {@code JsonString}}
     * Two {@code JsonString}s {@code js1} and {@code js2} represent the same value
     * if {@code js1.value().equals(js2.value())}.
     *
     * @see #value()
     */
    boolean equals(Object obj);

    /**
     * {@return the hash code value of this {@code JsonString}} The hash code of a
     * {@code JsonString} is calculated by {@code Objects.hash(JsonString.value())}.
     *
     * @see #value()
     */
    int hashCode();
}
