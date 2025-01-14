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

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;

/**
 * This class provides static methods that produce/manipulate a {@link JsonValue}.
 * <p>
 * Use {@link #parse(String)} and its overload to parse data which adheres to the
 * JSON syntax defined in RFC 8259.
 * <p>
 * {@link #fromUntyped(Object)} and {@link #toUntyped(JsonValue)} provide a conversion
 * between {@code JsonValue} and an untyped object.
 * <p>
 * {@link #toDisplayString(JsonValue)} is a formatter that produces a
 * representation of the JSON value suitable for display.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *          Object Notation (JSON) Data Interchange Format
 * @since 25
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public final class Json {

    // Depth limit used by Parser and Generator
    static final int MAX_DEPTH = 32;

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document. If the document contains any JSON Object that has
     * duplicate keys, a {@code JsonParseException} is thrown.
     *
     * @param in the input JSON document as {@code String}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format, or a JSON object containing
     *      duplicate keys is encountered.
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(String in) {
        Objects.requireNonNull(in);
        return JsonGenerator.createValue(JsonParser.parseRoot(
                new JsonDocumentInfo(in.toCharArray())), 0, 0);
    }

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document. If the document contains any JSON Object that has
     * duplicate keys, a {@code JsonParseException} is thrown.
     *
     * @param in the input JSON document as {@code char[]}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format, or a JSON object containing
     *      duplicate keys is encountered.
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(char[] in) {
        Objects.requireNonNull(in);
        return JsonGenerator.createValue(JsonParser.parseRoot(
                new JsonDocumentInfo(in)), 0, 0);
    }

    /**
     * {@return a {@code JsonValue} that represents the data type of {@code src}}
     * If {@code src} or an underlying element is a {@code JsonValue} it is
     * returned as is. Otherwise, a conversion is applied as follows:
     * <ul>
     * <li>{@code List<Object>} for {@code JsonArray}</li>
     * <li>{@code Boolean} for {@code JsonBoolean}</li>
     * <li>{@code `null`} for {@code JsonNull}</li>
     * <li>{@code Number} for {@code JsonNumber}</li>
     * <li>{@code Map<String, Object>} for {@code JsonObject}</li>
     * <li>{@code String} for {@code JsonString}</li>
     * </ul>
     * <p>If {@code src} contains a circular reference, {@code IllegalArgumentException}
     * will be thrown. For example, the following code throws an exception,
     * {@snippet lang=java:
     *     var map = new HashMap<String, Object>();
     *     map.put("foo", false);
     *     map.put("bar", map);
     *     Json.fromUntyped(map);
     * }
     *
     * @param src the data to produce the {@code JsonValue} from. May be null.
     * @throws IllegalArgumentException if {@code src} cannot be converted
     * to any of the {@code JsonValue} subtypes, or contains a circular reference.
     */
    public static JsonValue fromUntyped(Object src) {
        if (src instanceof JsonValue jv) {
            return jv; // If root is JV, no need to check depth
        } else {
            return JsonGenerator.untypedToJson(
                    src, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
        }
    }

    /**
     * {@return an {@code Object} that represents the data of the passed {@code
     * JsonValue}}
     * The returned {@code Object} is one of these types, depending on the {@code src}:
     * <ul>
     * <li>{@code List<Object>} for {@code JsonArray}</li>
     * <li>{@code Boolean} for {@code JsonBoolean}</li>
     * <li>{@code `null`} for {@code JsonNull}</li>
     * <li>{@code Number} for {@code JsonNumber}</li>
     * <li>{@code Map<String, Object>} for {@code JsonObject}</li>
     * <li>{@code String} for {@code JsonString}</li>
     * </ul>
     *
     * @param src the {@code JsonValue} to convert to untyped. Non-null.
     */
    public static Object toUntyped(JsonValue src) {
        Objects.requireNonNull(src);
        return switch (src) {
            case JsonString jStr -> ((JsonStringImpl) jStr).toUntyped();
            case JsonObject jMap -> ((JsonObjectImpl) jMap).toUntyped();
            case JsonArray jList-> ((JsonArrayImpl) jList).toUntyped();
            case JsonBoolean jBool -> ((JsonBooleanImpl) jBool).toUntyped();
            case JsonNumber jNum-> ((JsonNumberImpl) jNum).toUntyped();
            case JsonNull jNull -> ((JsonNullImpl) jNull).toUntyped();
        };
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
