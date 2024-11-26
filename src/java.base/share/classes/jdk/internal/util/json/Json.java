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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Collection of static helper methods.
 * <p>
 * A pair of {@code fromUntyped()/toUntyped()} and their overloads provide conversion
 * between JSON value and corresponding Java types that represent them.
 * To obtain a {@link JsonValue}, Use {@link #fromUntyped(Object)} and its overloads.
 * Additionally, the underlying data
 * value of the {@code JsonValue} can be produced by {@link #toUntyped(JsonValue)}
 * and its overloads. See {@link JsonParser} for producing {@code JsonValue}s by
 * parsing JSON strings.
 * <p>
 * {@code toDisplayString()} is a formatter that produces a human-readable representation
 * of the JSON value.
 */
public class Json {

    /**
     * {@return a {@code JsonValue} that represents the data type of {@code from}}
     *
     * @param from the data to produce the {@code JsonValue} from. May be null.
     * @throws IllegalArgumentException if {@code from} cannot be converted
     * to any of the {@code JsonValue} subtypes.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonValue fromUntyped(Object from) {
        return switch (from) {
            case String str -> fromUntyped(str);
            case Map<?, ?> map -> fromUntyped(map);
            case List<?> list-> fromUntyped(list);
            case Object[] array -> fromUntyped(Arrays.asList(array));
            case Boolean bool -> fromUntyped(bool);
            case Number num-> fromUntyped(num);
            case null -> JsonNull.ofNull();
            default -> throw new IllegalArgumentException("Type not recognized.");
        };
    }

    /**
     * {@return the {@code JsonArray} created from the given
     * list of {@code Object}s} {@code Element}(s) in {@code from} should be any
     * value such that {@link #fromUntyped(Object) JsonValue.fromUntyped(element)} does not throw
     * an exception.
     *
     * @param from the list of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonArray fromUntyped(List<?> from) {
        Objects.requireNonNull(from);
        return new JsonArrayImpl(from);
    }

    /**
     * {@return the {@code JsonBoolean} created from the given
     * {@code Boolean} object}
     *
     * @param from the given {@code Boolean}. Non-null.
     */
    public static JsonBoolean fromUntyped(Boolean from) {
        Objects.requireNonNull(from);
        return from ? JsonBooleanImpl.TRUE : JsonBooleanImpl.FALSE;
    }

    /**
     * {@return the {@code JsonNumber} created from the given
     * {@code Number} object}
     *
     * @implNote If the given {@code Number} has too great a magnitude represent as a
     * {@code double}, it will throw an {@code IllegalArgumentException}.
     *
     * @param num the given {@code Number}. Non-null.
     * @throws IllegalArgumentException if the given {@code num} is out
     *          of accepted range.
     */
    public static JsonNumber fromUntyped(Number num) {
        Objects.requireNonNull(num);
        return switch (num) {
            case Byte b -> new JsonNumberImpl(b);
            case Short s -> new JsonNumberImpl(s);
            case Integer i -> new JsonNumberImpl(i);
            case Long l -> new JsonNumberImpl(l);
            default -> {
                // non-integral types
                var d = num.doubleValue();
                if (Double.isNaN(d) || Double.isInfinite(d)) {
                    throw new IllegalArgumentException("Not a valid JSON number");
                }
                yield new JsonNumberImpl(d);
            }
        };
    }

    /**
     * {@return the {@code JsonObject} created from the given
     * Map of {@code Object}s} Keys should be strings, and values should be any
     * value such that {@link #fromUntyped(Object) JsonValue.fromUntyped(value)} does not throw
     * an exception.
     *
     * @param from the Map of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    public static JsonObject fromUntyped(Map<?, ?> from) {
        Objects.requireNonNull(from);
        return new JsonObjectImpl(from);
    }

    /**
     * {@return the {@code JsonString} created from the given
     * {@code String} object}
     *
     * @param from the given {@code String}. Non-null.
     */
    public static JsonString fromUntyped(String from) {
        Objects.requireNonNull(from);
        return new JsonStringLazyImpl(from);
    }

    /**
     * {@return an {@code Object} that represents the data of the passed {@code
     * JsonValue}}. The return type depends on the subtype of {@code from}.
     */
    public static Object toUntyped(JsonValue from) {
        return switch (from) {
            case JsonString jStr -> toUntyped(jStr);
            case JsonObject jMap -> toUntyped(jMap);
            case JsonArray jList-> toUntyped(jList);
            case JsonBoolean jBool -> toUntyped(jBool);
            case JsonNumber jNum-> toUntyped(jNum);
            case JsonNull jNull -> toUntyped(jNull);
        };
    }

    /**
     * {@return the {@code String} value corresponding to {@code from}}
     */
    public static String toUntyped(JsonString from) {
        return ((JsonStringImpl) from).toUntyped();
    }

    /**
     * {@return the map composed of {@code String} to {@code Object} corresponding to
     * {@code from}}
     */
    public static Map<String, Object> toUntyped(JsonObject from) {
        return ((JsonObjectImpl) from).toUntyped();
    }

    /**
     * {@return the list of {@code Object}s corresponding to {@code from}}
     */
    public static List<Object> toUntyped(JsonArray from) {
        return ((JsonArrayImpl) from).toUntyped();
    }

    /**
     * {@return the {@code Boolean} value corresponding to {@code from}}
     */
    public static boolean toUntyped(JsonBoolean from) {
        return ((JsonBooleanImpl) from).toUntyped();
    }

    /**
     * {@return the {@code Number} value corresponding to {@code from}}.
     * The Number subtype depends on the number value in {@code from}.
     */
    public static Number toUntyped(JsonNumber from) {
        return ((JsonNumberImpl) from).toUntyped();
    }

    /**
     * {@return {@code null}}
     */
    public static Object toUntyped(JsonNull from) {
        return ((JsonNullImpl) from).toUntyped();
    }

    /**
     * {@return the String representation of the given {@code JsonValue} that conforms
     * to the JSON syntax} As opposed to {@link JsonValue#toString()}, this method returns
     * JSON string that is suitable for display.
     */
    public static String toDisplayString(JsonValue jsonValue) {
        return ((JsonValueImpl)jsonValue).toDisplayString();
    }

    // no instantiation is allowed for this class
    private Json() {}
}
