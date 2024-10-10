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
 * A simple JSON parser that utilizes the deconstructor pattern matching. For a
 * simple JSON data, values can be retrieved using the pattern match, such as:
 * {@snippet lang=java :
 *     JsonValue doc = JsonParser.parse(aString);
 *     if (doc instanceof JsonObject(var keys) &&
 *         keys.get("name") instanceof JsonString(var name) &&
 *         keys.get("age") instanceof JsonNumber(var age)) { ... }
 * }
 */
public class JsonParser {

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code String}. Non-null.
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(String in) {
        Objects.requireNonNull(in);
        return parseImpl(new JsonDocumentInfo(in));
    }

    /**
     * Parses and creates the top level {@code JsonValue} in this JSON
     * document.
     *
     * @param in the input JSON document as {@code char[]}. Non-null.
     * @return the top level {@code JsonValue}
     */
    public static JsonValue parse(char[] in) {
        Objects.requireNonNull(in);
        return parseImpl(new JsonDocumentInfo(in));
    }

    // return the root value
    private static JsonValue parseImpl(JsonDocumentInfo docInfo) {
        JsonValue jv = parseValue(docInfo, 0, 0);

        // check the remainder is whitespace
        var offset = ((JsonValueImpl)jv).getEndOffset();
        if (!checkWhitespaces(docInfo, offset, docInfo.getEndOffset())) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Garbage characters at end.", offset), offset);
        }
        return jv;
    }

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
        return new JsonObjectImpl(docInfo, offset, index);
    }

    static JsonArray parseArray(JsonDocumentInfo docInfo, int offset, int index) {
        return new JsonArrayImpl(docInfo, offset, index);
    }

    static JsonString parseString(JsonDocumentInfo docInfo, int offset, int index) {
        return new JsonStringImpl(docInfo, offset, index);
    }

    static JsonBoolean parseBoolean(JsonDocumentInfo docInfo, int offset, int index) {
        return new JsonBooleanImpl(docInfo, offset, index);
    }

    static JsonNull parseNull(JsonDocumentInfo docInfo, int offset, int index) {
        return new JsonNullImpl(docInfo, offset, index);
    }

    static JsonNumber parseNumber(JsonDocumentInfo docInfo, int offset, int index) {
        return new JsonNumberImpl(docInfo, offset, index);
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
