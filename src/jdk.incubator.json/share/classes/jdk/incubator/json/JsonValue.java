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

package jdk.incubator.json;

import jdk.incubator.json.impl.JsonValueImpl;
import jdk.incubator.json.impl.Utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The interface that represents a JSON value.
 * <p>
 * Instances of {@code JsonValue} are immutable and thread safe.
 * <p>
 * A {@code JsonValue} can be produced by {@link Json#parse(String)} or {@link
 * Json#fromUntyped(Object)}. See {@link #toString()}  for converting a {@code
 * JsonValue} to its corresponding JSON String. For example,
 * {@snippet lang=java:
 *     List<Object> values = Arrays.asList("foo", true, 25);
 *     JsonValue json = Json.fromUntyped(values);
 *     json.toString(); // returns "[\"foo\",true,25]"
 * }
 *
 * A class implementing a non-sealed {@code JsonValue} sub-interface must adhere
 * to the following:
 * <ul>
 * <li>The class's implementations of {@code equals}, {@code hashCode},
 * and {@code toString} compute their results solely from the values
 * of the class's instance fields (and the members of the objects they
 * reference), not from the instance's identity.</li>
 * <li>The class's methods treat instances as <em>freely substitutable</em>
 * when equal, meaning that interchanging any two instances {@code x} and
 * {@code y} that are equal according to {@code equals()} produces no
 * visible change in the behavior of the class's methods.</li>
 * <li>The class performs no synchronization using an instance's monitor.</li>
 * <li>The class does not provide any instance creation mechanism that promises
 * a unique identity on each method call&mdash;in particular, any factory
 * method's contract must allow for the possibility that if two independently-produced
 * instances are equal according to {@code equals()}, they may also be
 * equal according to {@code ==}.</li>
 * </ul>
 * <p>
 * Users of {@code JsonValue} instances should ensure the following:
 * <ul>
 * <li> When two instances of {@code JsonValue} are equal (according to {@code equals()}), users
 * should not attempt to distinguish between their identities, whether directly via reference
 * equality or indirectly via an appeal to synchronization, identity hashing,
 * serialization, or any other identity-sensitive mechanism.</li>
 * <li> Synchronization on instances of {@code JsonValue} is strongly discouraged,
 * because the programmer cannot guarantee exclusive ownership of the
 * associated monitor.</li>
 * </ul>
 *
 * @since 99
 */

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
     * {@return the {@code boolean} value represented by a {@code JsonBoolean}}.
     *
     * @implSpec
     * The default implementation checks if this {@code JsonValue} is an instance
     * of {@code JsonBoolean} and if so returns the result of invoking {@link JsonBoolean#bool()},
     * otherwise throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonBoolean}.
     */
    default boolean bool() {
        return switch (this) {
            case JsonBoolean jb -> jb.bool();
            default -> throw Utils.composeTypeError(this, "JsonBoolean");
        };
    }

    /**
     * {@return the {@code Number} parsed or translated from the string representation
     * of a {@code JsonNumber}}
     *
     * @implSpec
     * The default implementation checks if this {@code JsonValue} is an instance
     * of {@code JsonNumber} and if so returns the result of invoking {@link JsonNumber#number()},
     * otherwise throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonNumber}.
     */
    default Number number() {
        return switch (this) {
            case JsonNumber jn -> jn.number();
            default -> throw Utils.composeTypeError(this, "JsonNumber");
        };
    }

    /**
     * {@return the {@code String} value represented by a {@code JsonString}}.
     *
     * @implSpec
     * The default implementation checks if this {@code JsonValue} is an instance
     * of {@code JsonString} and if so returns the result of invoking {@link JsonString#string()},
     * otherwise throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonString}.
     */
    default String string() {
        return switch (this) {
            case JsonString js -> js.string();
            default -> throw Utils.composeTypeError(this, "JsonString");
        };
    }

    // Accessor to JsonValue, except JsonNull

    /**
     * {@return an {@code Optional} containing this {@code JsonValue} if it
     * is not an instance of {@code JsonNull}, otherwise an empty {@code Optional}}
     *
     * @implSpec
     * The default implementation checks if this {@code JsonValue} is an instance
     * of {@code JsonNull} and if so returns the result of invoking {@link Optional#empty},
     * returns the result of invoking {@link Optional#of} with this {@code JsonValue} as the argument.
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
     * The default implementation returns the result of invoking {@link JsonArray#elements()} on the result
     * of invoking {@link JsonValue#array()}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonArray}.
     */
    default List<JsonValue> elements() {
        return array().elements();
    }

    /**
     * {@return the {@link JsonObject#members() members} of a {@code JsonObject}}
     *
     * @implSpec
     * The default implementation returns the result of invoking {@link JsonObject#members()} on the result
     * of invoking {@link JsonValue#object()}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonObject}.
     */
    default Map<String, JsonValue> members() {
        return object().members();
    }

    // Accessors to structural values

    /**
     * {@return this {@code JsonValue} as a {@code JsonObject}}.
     *
     * @implSpec
     * The default implementation checks if this {@code JsonValue} is an instance
     * of {@code JsonObject} and if so returns the result of casting this {@code JsonValue}
     * to {@code JsonObject}, otherwise throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonObject}.
     */
    default JsonObject object() {
        if (this instanceof JsonObject jo) {
            return jo;
        }
        throw Utils.composeTypeError(this, "JsonObject");
    }

    /**
     * {@return this {@code JsonValue} as a {@code JsonArray}}.
     *
     * @implSpec
     * The default implementation checks if this {@code JsonValue} is an instance
     * of {@code JsonArray} and if so returns the result of casting this {@code JsonValue}
     * to {@code JsonArray}, otherwise throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance of {@code JsonArray}.
     */
    default JsonArray array() {
        if (this instanceof JsonArray ja) {
            return ja;
        }
        throw Utils.composeTypeError(this, "JsonArray");
    }


    // Accessors to values of structural values

    /**
     * {@return the {@code JsonValue} associated with the given member name of a {@code JsonObject}}
     *
     * @implSpec
     * The default implementation obtains a {@code JsonValue} that is the result of the
     * invoking {@link Map#get} within the given member name on the result of invoking
     * {@link JsonValue#members()}, and returns that {@code JsonValue} if not {@code null},
     * otherwise throws {@code JsonAssertionException}.
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
            // @@@ throw JsonAssertionException
            case null -> throw new IllegalArgumentException(
                    "JsonObject member \"%s\" does not exist.".formatted(name) +
                            (this instanceof JsonValueImpl jvi && jvi.doc() != null ?
                                    Utils.getPath(jvi) : ""));
        };
    }

    /**
     * {@return an {@code Optional} containing the {@code JsonValue} associated with the given member
     * name of a {@code JsonObject}, otherwise if there is no association an empty {@code Optional}}
     *
     * @implSpec
     * The default implementation obtains a {@code JsonValue} that is the result of the
     * invoking {@link Map#get} within the given member name on the result of invoking
     * {@link JsonValue#members()}, and returns result of invoking {@link Optional#ofNullable}
     * with that {@code JsonValue}.
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
     * The default implementation returns the result of invoking {@link List#get} with the given index
     * on the result of invoking {@link JsonValue#elements()}, otherwise if the index is out of bounds
     * throws {@code JsonAssertionException}.
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
            throw new IllegalArgumentException(
                    "JsonArray index %d out of bounds for length %d."
                            .formatted(index, elements.size()) +
                            (this instanceof JsonValueImpl jvi && jvi.doc() != null  ?
                                    Utils.getPath(jvi) : ""));
        }
    }
}
