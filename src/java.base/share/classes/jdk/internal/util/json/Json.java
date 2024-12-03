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
 * This class provides static methods that produce a {@link JsonValue}.
 * <p>
 * Use {@link #parse(String)} and its overload to parse data which adheres to the
 * JSON syntax defined in RFC 8259.
 * <p>
 * {@link #fromUntyped(Object)} and {@link #toUntyped(JsonValue)} provide a conversion
 * between {@code JsonValue} and an untyped Java value.
 * <p>
 * {@link #toDisplayString(JsonValue)} is a formatter that produces a human-readable
 * representation of the JSON value.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *          Object Notation (JSON) Data Interchange Format
 */
public class Json {

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code String}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(String in) {
        Objects.requireNonNull(in);
        return JsonParser.parseImpl(new JsonLazyDocumentInfo(in));
    }

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code char[]}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(char[] in) {
        Objects.requireNonNull(in);
        return JsonParser.parseImpl(new JsonLazyDocumentInfo(in));
    }

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
            case String str -> new JsonStringLazyImpl(str);
            case Map<?, ?> map -> new JsonObjectImpl(map);
            case List<?> list-> new JsonArrayImpl(list);
            case Boolean bool -> new JsonBooleanImpl(bool);
            // Use constructor for Float/Integer to prevent type from being promoted
            case Float f -> new JsonNumberImpl(f);
            case Integer i -> new JsonNumberImpl(i);
            case Double db -> JsonNumber.of(db);
            case Long lg -> JsonNumber.of(lg);
            case JsonValue jv -> jv;
            case null -> JsonNull.of();
            default -> throw new IllegalArgumentException("Type not recognized.");
        };
    }

    /**
     * {@return an {@code Object} that represents the data of the passed {@code
     * JsonValue}}
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
