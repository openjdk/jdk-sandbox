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

import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Objects;
import java.util.HashMap;
import java.util.LinkedHashMap;

/**
 * This class provides static methods for producing and manipulating a {@link JsonValue}.
 * <p>
 * {@link #parse(String)} and {@link #parse(char[])} produce a {@code JsonValue}
 * by parsing data adhering to the JSON syntax defined in RFC 8259.
 * <p>
 * {@link #toDisplayString(JsonValue)} is a formatter that produces a
 * representation of the JSON value suitable for display.
 * <p>
 * {@link #fromUntyped(Object)} and {@link #toUntyped(JsonValue)} provide a conversion
 * between {@code JsonValue} and an untyped object.
 *
 * <table id="mapping-table" class="striped">
 * <caption>Mapping Table</caption>
 * <thead>
 *    <tr>
 *       <th scope="col" class="TableHeadingColor">Untyped Object</th>
 *       <th scope="col" class="TableHeadingColor">JsonValue</th>
 *    </tr>
 * </thead>
 * <tbody>
     * <tr>
     *     <th>{@code List<Object>}</th>
     *     <th> {@code JsonArray}</th>
     * </tr>
     * <tr>
     *     <th>{@code Boolean}</th>
     *     <th>{@code JsonBoolean}</th>
     * </tr>
     * <tr>
     *     <th>{@code `null`}</th>
     *     <th> {@code JsonNull}</th>
     * </tr>
     * <tr>
     *     <th>{@code Number*}</th>
     *     <th>{@code JsonNumber}</th>
     * </tr>
     * <tr>
     *     <th>{@code Map<String, Object>}</th>
     *     <th> {@code JsonObject}</th>
     * </tr>
     * <tr>
     *     <th>{@code String}</th>
     *     <th>{@code JsonString}</th>
     * </tr>
 * </tbody>
 * </table>
 *
 * <i>The supported Number subclasses are: Byte, Integer, Long, Short, Float,
 * Double, BigInteger, and BigDecimal</i>
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *          Object Notation (JSON) Data Interchange Format
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public final class Json {

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document. If parsing succeeds, it guarantees that the input document
     * conforms to the JSON syntax.
     * If the document contains any JSON Object that has duplicate keys, a
     * {@code JsonParseException} is thrown.
     *
     * @param in the input JSON document as {@code String}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format or a JSON object containing
     *      duplicate keys is encountered.
     * @throws NullPointerException if {@code in} is {@code null}
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(String in) {
        Objects.requireNonNull(in);
        return JsonFactory.createValue(JsonParser.parseRoot(
                new JsonDocumentInfo(in.toCharArray())), 0, 0);
    }

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document. If parsing succeeds, it guarantees that the input document
     * conforms to the JSON syntax.
     * If the document contains any JSON Object that has duplicate keys, a
     * {@code JsonParseException} is thrown.
     *
     * @param in the input JSON document as {@code char[]}. Non-null.
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format or a JSON object containing
     *      duplicate keys is encountered.
     * @throws NullPointerException if {@code in} is {@code null}
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(char[] in) {
        Objects.requireNonNull(in);
        return JsonFactory.createValue(JsonParser.parseRoot(
                new JsonDocumentInfo(Arrays.copyOf(in, in.length))), 0, 0);
    }

    /**
     * {@return a {@code JsonValue} corresponding to {@code src}}
     * See the {@link ##mapping-table Mapping Table} for conversion details.
     *
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
     *      to {@code JsonValue} or contains a circular reference.
     * @see ##mapping-table Mapping Table
     * @see #toUntyped(JsonValue)
     */
    public static JsonValue fromUntyped(Object src) {
        if (src instanceof JsonValue jv) {
            return jv;
        } else {
            return JsonFactory.fromUntyped(
                    src, Collections.newSetFromMap(new IdentityHashMap<>()));
        }
    }

    /**
     * {@return an {@code Object} corresponding to {@code src}}
     * See the {@link ##mapping-table Mapping Table} for conversion details.
     *
     * @param src the {@code JsonValue} to convert to untyped. Non-null.
     * @throws NullPointerException if {@code src} is {@code null}
     * @see ##mapping-table Mapping Table
     * @see #fromUntyped(Object)
     */
    public static Object toUntyped(JsonValue src) {
        Objects.requireNonNull(src);
        return switch (src) {
            case JsonObject jo -> jo.members().entrySet().stream()
                    .collect(LinkedHashMap::new, // to allow `null` value
                            (m, e) -> m.put(e.getKey(), Json.toUntyped(e.getValue())),
                            HashMap::putAll);
            case JsonArray ja -> ja.values().stream()
                    .map(Json::toUntyped)
                    .toList();
            case JsonBoolean jb -> jb.value();
            case JsonNull _ -> null;
            case JsonNumber n -> n.toNumber();
            case JsonString js -> js.value();
        };
    }

    /**
     * {@return the String representation of the given {@code JsonValue} that conforms
     * to the JSON syntax} As opposed to the compact output returned by {@link
     * JsonValue#toString()}, this method returns a JSON string that is better
     * suited for display.
     *
     * @param value the {@code JsonValue} to create the display string from. Non-null.
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static String toDisplayString(JsonValue value) {
        Objects.requireNonNull(value);
        return toDisplayString(value, 0 , false);
    }

    private static String toDisplayString(JsonValue jv, int indent, boolean isField) {
        return switch (jv) {
            case JsonObject jo -> toDisplayString(jo, indent, isField);
            case JsonArray ja -> toDisplayString(ja, indent, isField);
            default -> " ".repeat(isField ? 1 : indent) + jv;
        };
    }

    private static String toDisplayString(JsonObject jo, int indent, boolean isField) {
        var prefix = " ".repeat(indent);
        var s = new StringBuilder(isField ? " " : prefix);
        if (jo.members().isEmpty()) {
            s.append("{}");
        } else {
            s.append("{\n");
            jo.members().forEach((key, value) -> {
                if (value instanceof JsonValue val) {
                    s.append(prefix)
                            .append(" ".repeat(INDENT))
                            .append("\"")
                            .append(key)
                            .append("\":")
                            .append(Json.toDisplayString(val, indent + INDENT, true))
                            .append(",\n");
                } else {
                    throw new InternalError("type mismatch");
                }
            });
            s.setLength(s.length() - 2); // trim final comma
            s.append("\n").append(prefix).append("}");
        }
        return s.toString();
    }

    private static String toDisplayString(JsonArray ja, int indent, boolean isField) {
        var prefix = " ".repeat(indent);
        var s = new StringBuilder(isField ? " " : prefix);
        if (ja.values().isEmpty()) {
            s.append("[]");
        } else {
            s.append("[\n");
            for (JsonValue v: ja.values()) {
                if (v instanceof JsonValue jv) {
                    s.append(Json.toDisplayString(jv,indent + INDENT, false)).append(",\n");
                } else {
                    throw new InternalError("type mismatch");
                }
            }
            s.setLength(s.length() - 2); // trim final comma/newline
            s.append("\n").append(prefix).append("]");
        }
        return s.toString();
    }

    // default indentation for display string
    private static final int INDENT = 2;

    // no instantiation is allowed for this class
    private Json() {}
}
