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

import java.util.Objects;

/**
 * This class is used to obtain a {@link JsonValue} by parsing a {@code String}
 * whose data adheres to the JSON syntax defined in RFC 8259. See {@link Json}
 * which provides the {@link Json#fromUntyped(Object)} and {@link
 * Json#toUntyped(JsonObject)} conversion methods between untyped data.
 *
 * <p>
 * This parser utilizes deconstructor pattern matching. For simple JSON data,
 * the underlying data of a {@code JsonValue} can be retrieved using pattern matching,
 * such as:
 * {@snippet lang=java :
 *     JsonValue doc = JsonParser.parse("{ \"name\" : \"John\", \"age\" : 40 }");
 *     if (doc instanceof JsonObject(var keys) &&
 *         keys.get("name") instanceof JsonString(var name) &&
 *         keys.get("age") instanceof JsonNumber(var age)) { ... }
 * }
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *          Object Notation (JSON) Data Interchange Format
 */
public class JsonParser {

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
     * @param in the input JSON document as {@code String}. Non-null.
     * @param options parsing options
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(String in, Option... options) {
        Objects.requireNonNull(in);

        for (var o : options) {
            if (o == Option.Parse.EAGER_PARSING) {
                return JsonParser.parseImpl(new JsonDocumentInfo(in));
            }
        }
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
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code char[]}. Non-null.
     * @param options parsing options
     * @throws JsonParseException if the input JSON document does not conform
     *      to the JSON document format
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(char[] in, Option... options) {
        Objects.requireNonNull(in);

        for (var o : options) {
            if (o == Option.Parse.EAGER_PARSING) {
                return JsonParser.parseImpl(new JsonDocumentInfo(in));
            }
        }
        return JsonParser.parseImpl(new JsonLazyDocumentInfo(in));
    }

    // return the root value
    static JsonValue parseImpl(JsonDocumentInfo docInfo) {
        JsonValue jv = parseValue(docInfo, 0, 0);

        // check the remainder is whitespace
        var offset = ((JsonValueImpl)jv).getEndOffset();
        if (!checkWhitespaces(docInfo, offset, docInfo.getEndOffset())) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Garbage characters at end.", offset), offset);
        }
        return jv;
    }

    // eager parsing
    static JsonValue parseValue(JsonDocumentInfo docInfo, int offset) {
        return parseValue(docInfo, offset, -1);
    }

    // lazy parsing
    static JsonValue parseValue(JsonDocumentInfo docInfo, int offset, int index) {
        offset = skipWhitespaces(docInfo, offset);
        if (offset >= docInfo.getEndOffset()) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Value not recognized.", offset), offset);
        }
        return switch (docInfo.charAt(offset)) {
            case '{' -> parseObject(docInfo, offset, index);
            case '[' -> parseArray(docInfo, offset, index);
            case '"' -> parseString(docInfo, offset, index);
            case 't', 'f' -> parseBoolean(docInfo, offset, index);
            case 'n' -> parseNull(docInfo, offset, index);
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-' -> parseNumber(docInfo, offset, index);
            default -> throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Invalid value.", offset), offset);
        };
    }

    static JsonObject parseObject(JsonDocumentInfo docInfo, int offset, int index) {
        return switch (docInfo) {
            case JsonLazyDocumentInfo li -> new JsonObjectLazyImpl(li, offset, index);
            default ->  new JsonObjectImpl(docInfo, offset);
        };
    }

    static JsonArray parseArray(JsonDocumentInfo docInfo, int offset, int index) {
        return switch (docInfo) {
            case JsonLazyDocumentInfo li -> new JsonArrayLazyImpl(li, offset, index);
            default -> new JsonArrayImpl(docInfo, offset);
        };
    }

    static JsonString parseString(JsonDocumentInfo docInfo, int offset, int index) {
        return switch (docInfo) {
            case JsonLazyDocumentInfo li -> new JsonStringLazyImpl(li, offset, index);
            default -> new JsonStringImpl(docInfo, offset);
        };
    }

    static JsonBoolean parseBoolean(JsonDocumentInfo docInfo, int offset, int index) {
        return switch (docInfo) {
            case JsonLazyDocumentInfo li -> new JsonBooleanLazyImpl(li, offset, index);
            default -> new JsonBooleanImpl(docInfo, offset);
        };
    }

    static JsonNull parseNull(JsonDocumentInfo docInfo, int offset, int index) {
        return switch (docInfo) {
            case JsonLazyDocumentInfo li -> new JsonNullLazyImpl(li, offset, index);
            default -> new JsonNullImpl(docInfo, offset);
        };
    }

    static JsonNumber parseNumber(JsonDocumentInfo docInfo, int offset, int index) {
        return switch (docInfo) {
            case JsonLazyDocumentInfo li -> new JsonNumberLazyImpl(li, offset, index);
            default -> new JsonNumberImpl(docInfo, offset);
        };
    }

    // Utility functions
    static int skipWhitespaces(JsonDocumentInfo docInfo, int offset) {
        while (offset < docInfo.getEndOffset()) {
            if (!isWhitespace(docInfo.charAt(offset))) {
                break;
            }
            offset ++;
        }
        return offset;
    }

    static boolean checkWhitespaces(JsonDocumentInfo docInfo, int offset, int endOffset) {
        int end = Math.min(endOffset, docInfo.getEndOffset());
        while (offset < end) {
            if (!isWhitespace(docInfo.charAt(offset))) {
                return false;
            }
            offset ++;
        }
        return true;
    }

    static boolean isWhitespace(char c) {
        return switch (c) {
            case ' ', '\t', '\n', '\r' -> true;
            default -> false;
        };
    }

    // no instantiation of this parser
    private JsonParser(){}
}
