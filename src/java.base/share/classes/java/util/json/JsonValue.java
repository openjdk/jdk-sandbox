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
import jdk.internal.util.json.Utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The interface that represents a JSON value.
 * <p>
 * Instances of {@code JsonValue} are immutable and thread safe.
 * <p>
 * A {@code JsonValue} can be produced by {@link Json#parse(String)}. See
 * {@link #toString()} for converting a {@code JsonValue} to its corresponding
 * JSON String.
 *
 * @implSpec A class implementing a non-sealed {@code JsonValue} sub-interface must adhere
 * to the <a href="../../../java/lang/doc-files/ValueBased.html">value-based</a>
 * class requirements.
 *
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public sealed interface JsonValue permits JsonString, JsonNumber, JsonObject, JsonArray, JsonBoolean, JsonNull {

    /**
     * {@return the String representation of this {@code JsonValue} that conforms
     * to the JSON syntax} If this {@code JsonValue} is created by parsing a
     * JSON document, it preserves the text representation of the corresponding
     * JSON element, except that the returned string does not contain any white
     * spaces or newlines to produce a compact representation.
     * For a String representation suitable for display, use
     * {@link Json#toDisplayString(JsonValue, int)}.
     *
     * @see Json#toDisplayString(JsonValue, int)
     */
    String toString();


    // Accessors to content of leaf values

    /**
     * {@return the {@code boolean} value represented by a {@code JsonBoolean}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonBoolean}.
     */
    default boolean bool() {
        throw Utils.composeTypeError(this, "JsonBoolean");
    }

    /**
     * {@return this {@code JsonValue} as a {@code long}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance
     *      of {@code JsonNumber} nor can be represented as a {@code long}.
     */
    default long toLong() {
        throw Utils.composeTypeError(this, "JsonNumber");
    }

    /**
     * {@return this {@code JsonValue} as a {@code double}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance
     *      of {@code JsonNumber} nor can be represented as a {@code double}.
     */
    default double toDouble() {
        throw Utils.composeTypeError(this, "JsonNumber");
    }

    /**
     * {@return the {@code String} value represented by a {@code JsonString}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonString}.
     */
    default String string() {
        throw Utils.composeTypeError(this, "JsonString");
    }

    // Accessor to JsonValue, except JsonNull

    /**
     * {@return an {@code Optional} containing this {@code JsonValue} if it
     * is not an instance of {@code JsonNull}, otherwise an empty {@code Optional}}
     *
     * @implSpec
     * The default implementation returns {@link Optional#empty} if this
     * {@code JsonValue} is an instance of {@code JsonNull}; otherwise
     * {@link Optional#of} given this {@code JsonValue}.
     */
    default Optional<JsonValue> valueOrNull() {
        return switch (this) {
            case JsonNull _ -> Optional.empty();
            case JsonValue _ -> Optional.of(this);
        };
    }

    // Accessors to content of structural values

    /**
     * {@return the {@link JsonArray#elements() elements} of a {@code JsonArray}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonArray}.
     */
    default List<JsonValue> elements() {
        throw Utils.composeTypeError(this, "JsonArray");
    }

    /**
     * {@return the {@link JsonObject#members() members} of a {@code JsonObject}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonObject}.
     */
    default Map<String, JsonValue> members() {
        throw Utils.composeTypeError(this, "JsonObject");
    }

    // Accessors to structural values

    /**
     * {@return this {@code JsonValue} as a {@code JsonObject}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonObject}.
     */
    default JsonObject object() {
        throw Utils.composeTypeError(this, "JsonObject");
    }

    /**
     * {@return this {@code JsonValue} as a {@code JsonArray}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonArray}.
     */
    default JsonArray array() {
        throw Utils.composeTypeError(this, "JsonArray");
    }


    // Accessors to values of structural values

    /**
     * {@return the {@code JsonValue} associated with the given member name of a {@code JsonObject}}
     *
     * @implSpec
     * The default implementation obtains a {@code JsonValue} which is the result
     * of invoking {@link #members()}{@code .get(name)}. If {@code name} is absent,
     * {@code JsonAssertionException} is thrown.
     *
     * @param name the member name
     * @throws NullPointerException if the member name is {@code null}
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of a {@code JsonObject} or
     * there is no association with the member name
     */
    default JsonValue get(String name) {
        Objects.requireNonNull(name);
        return switch (members().get(name)) {
            case JsonValue jv -> jv;
            case null -> throw Utils.composeError(this,
                    "JsonObject member \"%s\" does not exist.".formatted(name));
        };
    }

    /**
     * {@return an {@code Optional} containing the {@code JsonValue} associated with the given member
     * name of a {@code JsonObject}, otherwise if there is no association an empty {@code Optional}}
     *
     * @implSpec
     * The default implementation obtains an {@code Optional<JsonValue>} by invoking {@link
     * #members()}{@code .get(name)}, which is then passed to {@link Optional#ofNullable}.
     *
     * @param name the member name
     * @throws NullPointerException if the member name is {@code null}
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of a {@code JsonObject}
     */
    default Optional<JsonValue> getOrAbsent(String name) {
        Objects.requireNonNull(name);
        return Optional.ofNullable(members().get(name));
    }

    /**
     * {@return the {@code JsonValue} associated with the given index of a {@code JsonArray}}
     *
     * @implSpec
     * The default implementation obtains a {@code JsonValue} which is the result
     * of invoking {@link #elements()}{@code .get(index)}. If {@code index} is
     * out of bounds, {@code JsonAssertionException} is thrown.
     *
     * @param index the index of the array
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of a {@code JsonArray}
     * or the given index is outside the bounds
     */
    default JsonValue element(int index) {
        List<JsonValue> elements = elements();
        try {
            return elements.get(index);
        } catch (IndexOutOfBoundsException _) {
            throw Utils.composeError(this,
                    "JsonArray index %d out of bounds for length %d."
                            .formatted(index, elements.size()));
        }
    }
}
