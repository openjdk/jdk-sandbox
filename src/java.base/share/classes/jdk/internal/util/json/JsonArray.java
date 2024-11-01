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

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * The interface that represents JSON array
 * <p>
 * A {@code JsonArray} can be produced by a {@link JsonParser} parse. A {@code
 * JsonArray} string passed to a {@code parse} has syntax as follows,
 * <blockquote><pre>
 * <i>JSON array</i> = '[' [ <i>JSON value</i> *( ',' <i>JSON value</i> ) ] ']'
 * </pre></blockquote>
 * A well-formed {@code JsonArray} string has the form of a pair of square brackets
 * enclosing zero or more JSON values. Subsequent JSON values are followed by a comma.
 * Note that the JSON values need not be of the same subtype.
 * For example, the following is a well-formed {@code JsonArray} string,
 * <blockquote><pre>
 * {@code "[ \"John\", 35, true ]" }
 * </pre></blockquote>
 * <p> Alternatively, {@link #from(List)} can be used to obtain a {@code JsonArray}
 * from a {@code List}. {@link #to()} is the inverse operation, producing a {@code List} from a
 * {@code JsonArray}. These methods are not guaranteed to produce a round-trip.
 */
public sealed interface JsonArray extends JsonValue permits JsonArrayImpl {
    /**
     * {@return the list of {@code JsonValue} elements in this array
     * value}
     */
    List<JsonValue> values();

    /**
     * {@return the stream of {@code JsonValue} elements in this JSON array}
     */
    Stream<JsonValue> stream();

    /**
     * {@return the {@code JsonValue} element in this JSON array}
     * @param index the index of the element
     */
    JsonValue get(int index);

    /**
     * {@return the list of {@code Object}s in this array}
     */
    List<Object> to();

    /**
     * {@return the size of this JSON array}.
     */
    int size();

    /**
     * {@return the {@code JsonArray} created from the given
     * list of {@code Object}s} {@code Element}(s) in {@code from} should be any
     * value such that {@link #from(Object) JsonValue.from(element)} does not throw
     * an exception.
     *
     * @param from the list of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    static JsonArray from(List<?> from) {
        Objects.requireNonNull(from);
        return new JsonArrayImpl(from);
    }

    /**
     * {@return the {@code JsonArray} created from the given
     * varargs of {@code JsonValue}s}
     *
     * @param values the varargs of {@code JsonValue}s. Non-null.
     * @param <T> the type of values, which is {@code JsonValue}
     */
    @SafeVarargs
    static <T extends JsonValue> JsonArray ofValues(T... values) {
        Objects.requireNonNull(values);
        return new JsonArrayImpl(values);
    }
}
