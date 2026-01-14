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

import java.util.Objects;

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.json.JsonStringImpl;
import jdk.internal.util.json.Utils;

/**
 * The interface that represents a JSON string.
 * <p>
 * A {@code JsonString} can be produced by a {@link Json#parse(String)}.
 * Within a valid JSON String, any character may be escaped using either a
 * two-character escape sequence (if applicable) or a Unicode escape sequence.
 * Quotation mark (U+0022), reverse solidus (U+005C), and the control characters
 * (U+0000 through U+001F) must be escaped.
 * <p> Alternatively, {@link #of(String)} can be used to obtain a {@code JsonString}
 * directly from a {@code String}. The {@code JsonString} instances produced by
 * the following expressions are all equivalent,
 * {@snippet lang = "java":
 *     Json.parse("\"foo\\t\"");
 *     Json.parse("\"foo\\u0009\"");
 *     JsonString.of("foo\t");
 *}
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259#section-7 RFC 8259:
 *      The JavaScript Object Notation (JSON) Data Interchange Format - Strings
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public non-sealed interface JsonString extends JsonValue {

    /**
     * {@return the {@code JsonString} created from the given
     * {@code String}}
     *
     * @param value the given {@code String} used as the {@code value} of this
     *             {@code JsonString}. Non-null.
     * @throws NullPointerException if {@code value} is {@code null}
     */
    static JsonString of(String value) {
        var escaped = '"' + Utils.escape(Objects.requireNonNull(value)) + '"';
        return new JsonStringImpl(escaped.toCharArray(), 0, escaped.length(),
                escaped.length() != value.length() + 2);
    }

    /**
     * {@return the JSON {@code String} represented by this {@code JsonString}}
     * If this {@code JsonString} was created by parsing a JSON document, it
     * preserves the text representation of the corresponding JSON String. Otherwise,
     * the {@code value} is escaped to produce the JSON {@code String}.
     *
     * @see #string()
     */
    @Override
    String toString();

    /**
     * {@return the {@code String} value represented by this {@code JsonString}}
     * If this {@code JsonString} was created by parsing a JSON document, any
     * escaped characters in the original JSON document are converted to their
     * unescaped form.
     *
     * @see #toString()
     */
    @Override
    String string();

    /**
     * {@return true if the given {@code obj} is equal to this {@code JsonString}}
     * Two {@code JsonString}s {@code js1} and {@code js2} represent the same value
     * if {@code js1.value().equals(js2.value())}.
     *
     * @see #string()
     */
    @Override
    boolean equals(Object obj);

    /**
     * {@return the hash code value of this {@code JsonString}} The hash code of a
     * {@code JsonString} is derived from the hash code of {@code JsonString}'s
     * {@link #string()}.
     *
     * @see #string()
     */
    @Override
    int hashCode();
}
