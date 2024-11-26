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
     * {@return a {@code JsonValue} that represents the data type of {@code src}}
     *
     * @param src the data to produce the {@code JsonValue} from. May be null.
     * @throws IllegalArgumentException if {@code src} cannot be converted
     * to any of the {@code JsonValue} subtypes.
     * @throws StackOverflowError if {@code src} contains a circular reference
     */
    public static JsonValue fromUntyped(Object src) {
        return switch (src) {
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
     * list of {@code Object}s} {@code Element}(s) in {@code src} should be any
     * value such that {@link #fromUntyped(Object) JsonValue.fromUntyped(element)} does not throw
     * an exception.
     *
     * @param src the list of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code src} contains a circular reference
     */
    public static JsonArray fromUntyped(List<?> src) {
        Objects.requireNonNull(src);
        return new JsonArrayImpl(src);
    }

    /**
     * {@return the {@code JsonBoolean} created from the given
     * {@code Boolean} object}
     *
     * @param src the given {@code Boolean}. Non-null.
     */
    public static JsonBoolean fromUntyped(Boolean src) {
        Objects.requireNonNull(src);
        return src ? JsonBooleanImpl.TRUE : JsonBooleanImpl.FALSE;
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
     * @param src the Map of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code src} contains a circular reference
     */
    public static JsonObject fromUntyped(Map<?, ?> src) {
        Objects.requireNonNull(src);
        return new JsonObjectImpl(src);
    }

    /**
     * {@return the {@code JsonString} created from the given
     * {@code String} object}
     *
     * @param src the given {@code String}. Non-null.
     */
    public static JsonString fromUntyped(String src) {
        Objects.requireNonNull(src);
        return new JsonStringLazyImpl(src);
    }

    /**
     * {@return an {@code Object} that represents the data of the passed {@code
     * JsonValue}}. The return type depends on the subtype of {@code src}.
     *
     * @param src the {@code JsonValue} to convert to untyped. Non-null.
     */
    public static Object toUntyped(JsonValue src) {
        Objects.requireNonNull(src);
        return switch (src) {
            case JsonString jStr -> toUntyped(jStr);
            case JsonObject jMap -> toUntyped(jMap);
            case JsonArray jList-> toUntyped(jList);
            case JsonBoolean jBool -> toUntyped(jBool);
            case JsonNumber jNum-> toUntyped(jNum);
            case JsonNull jNull -> toUntyped(jNull);
        };
    }

    /**
     * {@return the {@code String} value corresponding to {@code src}}
     *
     * @param src the {@code JsonString} to convert to untyped. Non-null.
     */
    public static String toUntyped(JsonString src) {
        Objects.requireNonNull(src);
        return ((JsonStringImpl) src).toUntyped();
    }

    /**
     * {@return the map composed of {@code String} to {@code Object} corresponding to
     * {@code src}}
     *
     * @param src the {@code JsonObject} to convert to untyped. Non-null.
     */
    public static Map<String, Object> toUntyped(JsonObject src) {
        Objects.requireNonNull(src);
        return ((JsonObjectImpl) src).toUntyped();
    }

    /**
     * {@return the list of {@code Object}s corresponding to {@code src}}
     *
     * @param src the {@code JsonArray} to convert to untyped. Non-null.
     */
    public static List<Object> toUntyped(JsonArray src) {
        Objects.requireNonNull(src);
        return ((JsonArrayImpl) src).toUntyped();
    }

    /**
     * {@return the {@code Boolean} value corresponding to {@code src}}
     *
     * @param src the {@code JsonBoolean} to convert to untyped. Non-null.
     */
    public static boolean toUntyped(JsonBoolean src) {
        Objects.requireNonNull(src);
        return ((JsonBooleanImpl) src).toUntyped();
    }

    /**
     * {@return the {@code Number} value corresponding to {@code src}}.
     * The Number subtype depends on the number value in {@code src}.
     *
     * @param src the {@code JsonNumber} to convert to untyped. Non-null.
     */
    public static Number toUntyped(JsonNumber src) {
        Objects.requireNonNull(src);
        return ((JsonNumberImpl) src).toUntyped();
    }

    /**
     * {@return {@code null}}
     *
     * @param src the {@code JsonNull} to convert to untyped. Non-null.
     */
    public static Object toUntyped(JsonNull src) {
        Objects.requireNonNull(src);
        return ((JsonNullImpl) src).toUntyped();
    }

    /**
     * {@return the String representation of the given {@code JsonValue} that conforms
     * to the JSON syntax} As opposed to {@link JsonValue#toString()}, this method returns
     * JSON string that is suitable for display.
     *
     * @param value the {@code JsonValue} to create the display string from. Non-null.
     */
    public static String toDisplayString(JsonValue value) {
        Objects.requireNonNull(value);
        return ((JsonValueImpl)value).toDisplayString();
    }

    // no instantiation is allowed for this class
    private Json() {}
}
