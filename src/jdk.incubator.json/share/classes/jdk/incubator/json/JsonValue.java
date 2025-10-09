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

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

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

    /**
     * If this {@code JsonValue} is a {@code JsonObject}, this method returns
     * the member associated with the {@code name}. If {@code this} is not a
     * {@code JsonObject}, a {@code JsonAssertionException} will be thrown.
     *
     * @param name the member name
     * @throws IllegalArgumentException if the specified {@code name} does not
     *      exist in this {@code JsonObject}.
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws JsonAssertionException if {@code this} is not a {@code JsonObject}.
     * @return the member of this {@code JsonObject} associated with the {@code name}.
     */
    default JsonValue get(String name) {
        Objects.requireNonNull(name);
        return switch (object().members().get(Objects.requireNonNull(name))) {
            case JsonValue jv -> jv;
            case null -> throw new IllegalArgumentException(
                    "JsonObject member \"%s\" does not exist.".formatted(name) +
                            (this instanceof JsonValueImpl jvi && jvi.doc() != null ?
                                    Utils.getPath(jvi) : ""));
        };
    }

    /**
     * If this {@code JsonValue} is a {@code JsonArray}, this method returns
     * the element of this {@code JsonArray} at the {@code index}. If {@code this}
     * is not a {@code JsonArray}, a {@code JsonAssertionException} will be
     * thrown.
     *
     * @param index the index of the array
     * @throws IllegalArgumentException if the specified {@code index} is out of
     *      bounds of this {@code JsonArray}.
     * @throws JsonAssertionException if {@code this} is not a {@code JsonArray}.
     * @return the element of this {@code JsonArray} at the {@code index}.
     */
    default JsonValue element(int index) {
        return switch (this) {
            case JsonArray ja -> {
                try {
                    yield ja.values().get(index);
                } catch (IndexOutOfBoundsException _) {
                    throw new IllegalArgumentException(
                            "JsonArray index %d out of bounds for length %d."
                                    .formatted(index, ja.values().size()) +
                                    (this instanceof JsonValueImpl jvi && jvi.doc() != null  ?
                                            Utils.getPath(jvi) : ""));
                }
            }
            default -> throw Utils.composeTypeError(this, "JsonArray");
        };
    }

    /**
     * If this {@code JsonValue} is a {@code JsonBoolean}, this method returns
     * the boolean value of this {@code JsonBoolean}. If {@code this}
     * is not a {@code JsonBoolean}, a {@code JsonAssertionException} will be
     * thrown.
     *
     * @throws JsonAssertionException if {@code this} is not a {@code JsonBoolean}.
     * @return the value of this {@code JsonBoolean}.
     */
    default boolean boolean_() {
        return switch (this) {
            case JsonBoolean jb -> jb.value();
            default -> throw Utils.composeTypeError(this, "JsonBoolean");
        };
    }

    /**
     * If this {@code JsonValue} is a {@code JsonNumber}, this method returns
     * a {@code Number}. If {@code this} is not a {@code JsonNumber}, a
     * {@code JsonAssertionException} will be thrown.
     *
     * @throws JsonAssertionException if {@code this} is not a {@code JsonNumber}.
     * @return the {@code toNumber()} value of this {@code JsonNumber}.
     */
    default Number number() {
        return switch (this) {
            case JsonNumber jn -> jn.toNumber();
            default -> throw Utils.composeTypeError(this, "JsonNumber");
        };
    }

    /**
     * If this {@code JsonValue} is a {@code JsonString}, this method returns
     * a {@code String}. If {@code this} is not a {@code JsonString}, a
     * {@code JsonAssertionException} will be thrown.
     *
     * @throws JsonAssertionException if {@code this} is not a {@code JsonString}.
     * @return the value of this {@code JsonString}.
     */
    default String string() {
        return switch (this) {
            case JsonString js -> js.value();
            default -> throw Utils.composeTypeError(this, "JsonString");
        };
    }

    // Direct accessors to structural content

    /**
     * If this {@code JsonValue} is a {@code JsonObject}, this method returns
     * the {@code JsonObject} itself. If {@code this} is not a {@code JsonObject}, a
     * {@code JsonAssertionException} will be thrown.
     *
     * @throws JsonAssertionException if {@code this} is not a {@code JsonObject}.
     * @return this {@code JsonObject}.
     */
    default JsonObject object() {
        if (this instanceof JsonObject jo) {
            return jo;
        }
        throw Utils.composeTypeError(this, "JsonObject");
    }

    /**
     * If this {@code JsonValue} is a {@code JsonArray}, this method returns
     * the {@code JsonArray} itself. If {@code this} is not a {@code JsonArray}, a
     * {@code JsonAssertionException} will be thrown.
     *
     * @throws JsonAssertionException if {@code this} is not a {@code JsonArray}.
     * @return this {@code JsonArray}.
     */
    default JsonArray array() {
        if (this instanceof JsonArray ja) {
            return ja;
        }
        throw Utils.composeTypeError(this, "JsonArray");
    }

    /**
     * {@return the {@code Optional} of this {@code JsonValue}}
     * If this {@code JsonValue} is a {@code JsonNull}, this method returns
     * an empty {@code Optional}.
     */
    default Optional<JsonValue> orNull() {
        return switch (this) {
            case JsonNull _ -> Optional.empty();
            case JsonValue _ -> Optional.of(this);
        };
    }

    // Indirect accessors to structural content

    /**
     * If this {@code JsonValue} is a {@code JsonArray}, this method returns
     * the {@code Stream} of elements in this {@code JsonArray}. If {@code this}
     * is not a {@code JsonArray}, a {@code JsonAssertionException} will be thrown.
     *
     * @throws JsonAssertionException if {@code this} is not a {@code JsonArray}.
     * @return a {@code Stream} of elements in this {@code JsonArray}
     */
    default Stream<JsonValue> elements() {
        return array().values().stream();
    }

    /**
     * If this {@code JsonValue} is a {@code JsonObject}, this method returns
     * the {@code Optional} of the member associated with the {@code name}. If
     * there is no member associated with the {@code name}, an empty
     * {@code Optional} is returned. If {@code this} is not a {@code JsonObject}, a
     * {@code JsonAssertionException} will be thrown.
     *
     * @param name the member name
     * @throws NullPointerException if {@code name} is {@code null}.
     * @throws JsonAssertionException if {@code this} is not a {@code JsonObject}.
     * @return an {@code Optional} of the member of this {@code JsonObject}
     *          associated with the {@code name}, or an empty {@code Optional}.
     */
    default Optional<JsonValue> find(String name) {
        return switch (object().members().get(name)) {
            case JsonValue mv -> Optional.of(mv);
            case null -> Optional.empty();
        };
    }
}
