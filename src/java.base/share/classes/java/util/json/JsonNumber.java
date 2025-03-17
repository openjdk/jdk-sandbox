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

/**
 * The interface that represents JSON number. The model presented by
 * {@code JsonNumber} is an arbitrary-precision decimal number.
 * <p>
 * A {@code JsonNumber} can be produced by {@link Json#parse(String)}.
 * Alternatively, {@link #of(double)} and its overload can be used to obtain
 * a {@code JsonNumber} from a {@code Number}.
 * When a JSON number is parsed, a {@code JsonNumber} object is created
 * as long as the syntax is valid. The value of the {@code JsonNumber}
 * can be retrieved from {@link #toString()} as the {@code String} representation
 * from which the JSON number is originally parsed, or with
 * {@link #toNumber()} as a {@code Number} instance.
 *
 * @since 25
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public sealed interface JsonNumber extends JsonValue permits JsonNumberImpl {

    /**
     * {@return the String representation of this {@code JsonNumber}}
     * If this {@code JsonNumber} is created by parsing a JSON document,
     * it preserves the text representation of the JSON number, regardless of its
     * precision or range. For example, a JSON number like
     * {@code 3.141592653589793238462643383279} in the JSON document will be
     * returned exactly as it appears.
     * If this {@code JsonNumber} is created via one of the factory methods,
     * such as {@link JsonNumber#of(double)}, the resulting String has the
     * same decimal digits that would result from converting the double value
     * to String as if by {@link Double#toString(double)}.
     */
    String toString();

    /**
     * {@return the {@code Number} value represented by this JSON number}
     * <p>
     * If the JSON number has no fractional part and is within the range of
     * {@code [-Math.pow(2, 53) + 1, Math.pow(2, 53) - 1]} then this method
     * returns an instance of {@code Long}, otherwise an instance of
     * {@code BigInteger} is returned.
     * If the JSON number has a fractional part and is withing the range of
     * {@code double} then this method returns that {@code double} value as an
     * instance of {@code Double}, otherwise an instance of {@code BigDecimal}
     * is returned.
     * In any of the four cases the lexical representation of the JSON
     * number is not guaranteed to be preserved, this representation can
     * be obtained from the JSON number's {@link #toString string value}.
     *
     * @apiNote
     * Pattern matching can be used to match against Long, Double, BigInteger,
     * or BigDecimal reference types. Subsequently, primitive type pattern
     * matching the unboxed values of Long, Double can be performed, for
     * example to determine if the JsonNumber can be represented as an
     * int value with no loss of precision. For example:
     * {@snippet lang=java:
     * switch(jsonNumber.toNumber()) {
     *     case Long bl when bl.longValue() instanceof int i -> { ... }
     *     case Double bd when bd.doubleValue() instanceof int i -> { ... }
     *     default -> { ... }
     * }
     *}
     * Or with unboxing:
     * {@snippet lang=java:
     * switch(jsonNumber.toNumber()) {
     *     case long l when l instanceof int i -> { ... }
     *     case double d when d instanceof int i -> { ... }
     *     default -> { ... }
     * }
     *}
     */
    Number toNumber();

    /**
     * {@return the {@code JsonNumber} created from the given
     * {@code double}}
     *
     * @implNote If the given {@code double} is equivalent to {@code +/-infinity}
     * or {@code NaN}, this method will throw an {@code IllegalArgumentException}.
     *
     * @param num the given {@code double}.
     * @throws IllegalArgumentException if the given {@code num} is out
     *          of the accepted range.
     */
    static JsonNumber of(double num) {
        // non-integral types
        return new JsonNumberImpl(num);
    }

    /**
     * {@return the {@code JsonNumber} created from the given
     * {@code long}}
     *
     * @param num the given {@code long}.
     */
    static JsonNumber of(long num) {
        // integral types
        return new JsonNumberImpl(num);
    }
}
