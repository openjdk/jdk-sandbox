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

import jdk.internal.javac.PreviewFeature;
import jdk.internal.util.json.Utils;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The interface that represents a JSON value. A {@code JsonValue} can be
 * produced by parsing a JSON document with {@link Json#parse(String)}. Extracting
 * a value is done in a 2-step process using {@link ##access access} and {@link
 * ##conversion conversion} methods. The {@link ##generation generation} method
 * produces the JSON compliant text from the {@code JsonValue}.
 * <h2 id="access">Navigating JSON documents</h2>
 * Use the access methods to navigate to the desired JSON element. {@link
 * #get(String)} is provided for JSON object and {@link #element(int)} for JSON array.
 * Given the JSON document:
 * {@snippet lang=java:
 * JsonValue json = Json.parse("""
 *     { "foo": ["bar", true, 42], "baz": null }
 *     """);
 * }
 * the JSON String "bar" can be accessed as follows:
 * {@snippet lang=java:
 * JsonValue foo0 = json.get("foo").element(0);
 * }
 * If an access method is invoked on an incompatible JSON type (for example,
 * calling {@code get(String)} on a JSON array), a {@code JsonAssertionException}
 * is thrown.
 * <p>
 * Once the desired JSON element is reached, call the corresponding conversion
 * method to retrieve an appropriate Java value from the {@code JsonValue}.
 * <h2 id=conversion>Converting JSON values to Java values</h2>
 * Use the conversion methods to produce a Java value from the {@code
 * JsonValue}. Each conversion methods corresponds to a JSON type:
 * <ul>
 *     <li>{@code string()} returns a String that represents the JSON string
 *     with all RFC 8259 JSON escapes translated to their corresponding
 *     characters.</li>
 *     <li>{@code toInt()} returns an int provided the JSON number is a whole
 *     number within range of {@code Integer.MIN_VALUE} and
 *     {@code Integer.MAX_VALUE}.
 *     </li>
 *     <li>{@code toLong()} returns a long provided the JSON number is a whole
 *     number within range of {@code Long.MIN_VALUE} and {@code Long.MAX_VALUE}.
 *     </li>
 *     <li>{@code toDouble()} returns a double provided the JSON number is
 *     within range of {@code -Double.MAX_VALUE} and {@code Double.MAX_VALUE}.
 *     </li>
 *     <li>{@code bool()} returns {@code true} or {@code false} for JSON
 *     boolean literals.</li>
 *     <li>{@code members()} returns an unmodifiable map of {@code String} to
 *     {@code JsonValue} for JSON object, guaranteed to contain neither null
 *     keys nor null values. If the JSON object contains no members, an empty
 *     map is returned.
 *     </li>
 *     <li>{@code elements()} returns an unmodifiable list of {@code JsonValue}
 *     for JSON array, guaranteed to contain non-null values. If the JSON array
 *     contains no elements, an empty list is returned.</li>
 * </ul>
 * For example,
 * {@snippet lang=java:
 * String bar = foo0.string();
 * }
 * The code above retrieves the Java String "bar" from the JSON element {@code foo0}.
 * If an incorrect conversion method is used, which does not correspond to the matching
 * JSON type, for example {@code foo0.bool()}, a {@code JsonAssertionException} is thrown.
 * <p>
 * These conversion methods always return a value when the {@code JsonValue} is
 * of the correct JSON type. The exceptions are {@code toInt()}, {@code toLong()},
 * and {@code toDouble()}; the {@code to} prefix implies that they may throw a
 * {@code JsonAssertionException} even when the {@code JsonValue} is a JSON
 * number, for example if it is outside their supported ranges.
 * <h2>Subtypes of JsonValue</h2>
 * The {@code JsonValue} subtypes correspond to the JSON types. For example,
 * {@code JsonString} to JSON string. If the type of JSON value is unknown, it can
 * be retrieved as follows:
 * {@snippet lang=java:
 * switch (json.get("foo")) {
 *     case JsonString js -> js.string(); // handle the value as JSON string
 *     case JsonArray ja -> ja.element(0).string(); // handle the value as JSON array
 *     default -> throw new JsonAssertionException("unexpected type");
 * }
 * }
 * <h2>Missing Object Members</h2>
 * There are times when the member in a JSON object is optional. For those
 * cases, use the access method {@link #getOrAbsent(String)} which returns an
 * Optional of JsonValue. For example:
 * {@snippet lang=java:
 * json.getOrAbsent("foo")
 *     .ifPresent(IO::println)
 * }
 * This example only prints the value if the member named "foo" exists.
 * <h2>Handling of null</h2>
 * In some JSON documents, JSON null is used to signify absence.
 * For those cases, use the access method {@link #valueOrNull()} which returns an
 * Optional of JsonValue. For example:
 * {@snippet lang=java:
 * json.get("baz")
 *     .valueOrNull()
 *     .ifPresent(IO::println)
 * }
 * This example only prints the value if the member named "baz" is not a JSON
 * null.
 * <h2 id="generation">Generating JSON documents</h2>
 * {@code JsonValue} overrides {@link Object#toString()} to generate RFC 8259 compliant
 * JSON text in a compact representation with white spaces eliminated.
 * For generating JSON documents suitable for display, use
 * the generation method {@link Json#toDisplayString(JsonValue, int)} instead.
 * <p>
 * Instances of {@code JsonValue} are immutable and thread safe.
 *
 * @implSpec A class implementing a non-sealed {@code JsonValue} sub-interface
 * must adhere to the
 * <a href="../../../java/lang/doc-files/ValueBased.html">value-based</a>
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
     * {@return this {@code JsonValue} as an {@code int}}
     *
     * @implSpec
     * The default implementation throws {@code JsonAssertionException}.
     *
     * @throws JsonAssertionException if this {@code JsonValue} is not an instance
     *      of {@code JsonNumber} nor can be represented as an {@code int}.
     */
    default int toInt() {
        throw Utils.composeTypeError(this, "JsonNumber");
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
