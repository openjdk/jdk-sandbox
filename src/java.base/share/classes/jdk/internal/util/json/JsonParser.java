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

package jdk.internal.util.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.json.JsonArray;
import java.util.json.JsonObject;
import java.util.json.JsonParseException;
import java.util.json.JsonString;
import java.util.json.JsonValue;

/**
 * Parses a JSON Document char[] into a tree of JsonValues. JsonObject and JsonArray
 * nodes create their data structures which maintain the connection to children.
 * JsonNumber and JsonString contain only a start and end offset, which
 * are used to lazily procure their underlying value/string on demand. Singletons
 * are used for JsonBoolean and JsonNull.
 */
public final class JsonParser {

    // Access to the underlying JSON contents
    private final char[] doc;
    // Current offset during parsing
    private int offset;
    // For exception message on failure
    private int line;
    private int lineStart;
    private StringBuilder builder;

    public JsonParser(char[] doc) {
        this.doc = doc;
    }

    // Parses the lone JsonValue root
    public JsonValue parseRoot() {
        JsonValue root = parseValue();
        if (hasInput()) {
            throw failure("Additional value(s) were found after the JSON Value");
        }
        return root;
    }

    /*
     * Parse any one of the JSON value types: object, array, number, string,
     * true, false, or null.
     *      JSON-text = ws value ws
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-3
     */
    private JsonValue parseValue() {
        skipWhitespaces();
        if (!hasInput()) {
            throw failure("Expected a JSON Object, Array, String, Number, Boolean, or Null");
        }
        var val = switch (doc[offset]) {
            case '{' -> parseObject();
            case '[' -> parseArray();
            case '"' -> parseString();
            case 't' -> parseTrue();
            case 'f' -> parseFalse();
            case 'n' -> parseNull();
            // While JSON Number does not support leading '+', '.', or 'e'
            // we still accept, so that we can provide a better error message
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', 'e', '.'
                    -> parseNumber();
            default -> throw failure(UNEXPECTED_VAL);
        };
        skipWhitespaces();
        return val;
    }

    /*
     * The parsed JsonObject contains a map which holds all lazy member mappings.
     * No offsets are required as member values hold their own offsets.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-4
     */
    private JsonObject parseObject() {
        offset++; // Walk past the '{'
        skipWhitespaces();
        // Check for empty case
        if (currCharEquals('}')) {
            offset++;
            return new JsonObjectImpl(Map.of());
        }
        var members = new LinkedHashMap<String, JsonValue>();
        while (hasInput()) {
            // Get the member name, which should be unescaped
            // Why not parse the name as a JsonString and then return its value()?
            // Would requires 2 passes; we should build the String as we parse.
            var name = parseName();

            if (members.containsKey(name)) {
                throw failure("The duplicate member name: \"%s\" was already parsed".formatted(name));
            }

            // Move from name to ':'
            skipWhitespaces();
            if (!currCharEquals(':')) {
                throw failure(
                        "Expected a colon after the member name");
            }

            // Move from ':' to JsonValue
            offset++;
            members.put(name, parseValue());
            // Ensure current char is either ',' or '}'
            if (currCharEquals('}')) {
                offset++;
                return new JsonObjectImpl(members);
            } else if (currCharEquals(',')) {
                // Add the comma, and move to the next key
                offset++;
                skipWhitespaces();
            } else {
                // Neither ',' nor '}' so fail
                break;
            }
        }
        throw failure("JSON Object is not closed with a brace");
    }

    /*
     * Member name equality and storage in the map should be done with the
     * unescaped value.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-8.3
     */
    private String parseName() {
        if (!currCharEquals('"')) {
            throw failure("Expecting a JSON Object member name");
        }
        offset++; // Move past the starting quote
        var escape = false;
        boolean useBldr = false;
        var start = offset;
        for (; hasInput(); offset++) {
            var c = doc[offset];
            if (escape) {
                var escapeLength = 0;
                switch (c) {
                    // Allowed JSON escapes
                    case '"', '\\', '/' -> {}
                    case 'b' -> c = '\b';
                    case 'f' -> c = '\f';
                    case 'n' -> c = '\n';
                    case 'r' -> c = '\r';
                    case 't' -> c = '\t';
                    case 'u' -> {
                        c = codeUnit();
                        escapeLength = 4;
                    }
                    default -> throw failure(UNRECOGNIZED_ESCAPE_SEQUENCE.formatted(c));
                }
                if (!useBldr) {
                    initBuilder();
                    // Append everything up to the first escape sequence
                    builder.append(doc, start, offset - escapeLength - 1 - start);
                    useBldr = true;
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
                continue;
            } else if (c == '\"') {
                offset++;
                if (useBldr) {
                    var name = builder.toString();
                    builder.setLength(0);
                    return name;
                } else {
                    return new String(doc, start, offset - start - 1);
                }
            } else if (c < ' ') {
                throw failure(UNESCAPED_CONTROL_CODE);
            }
            if (useBldr) {
                builder.append(c);
            }
        }
        throw failure(UNCLOSED_STRING.formatted("JSON Object member name"));
    }

    /*
     * The parsed JsonArray contains a List which holds all lazy children
     * elements. No offsets are required as children values hold their own offsets.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-5
     */
    private JsonArray parseArray() {
        offset++; // Walk past the '['
        skipWhitespaces();
        // Check for empty case
        if (currCharEquals(']')) {
            offset++;
            return new JsonArrayImpl(List.of());
        }
        var list = new ArrayList<JsonValue>();
        for (; hasInput(); offset++) {
            // Get the JsonValue
            list.add(parseValue());
            // Ensure current char is either ']' or ','
            if (currCharEquals(']')) {
                offset++;
                return new JsonArrayImpl(list);
            } else if (!currCharEquals(',')) {
                break;
            }
        }
        throw failure("JSON Array is not closed with a bracket");
    }

    /*
     * The parsed JsonString will contain offsets correlating to the beginning
     * and ending quotation marks. All Unicode characters are allowed except the
     * following that require escaping: quotation mark, reverse solidus, and the
     * control characters (U+0000 through U+001F). Any character may be escaped
     * either through a Unicode escape sequence or two-char sequence.
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-7
     */
    private JsonString parseString() {
        int start = offset++; // Move past the starting quote
        var escape = false;
        boolean hasEscape = false;
        for (; hasInput(); offset++) {
            var c = doc[offset];
            if (escape) {
                switch (c) {
                    // Allowed JSON escapes
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {}
                    case 'u' -> codeUnit();
                    default -> throw failure(UNRECOGNIZED_ESCAPE_SEQUENCE.formatted(c));
                }
                escape = false;
            } else if (c == '\\') {
                hasEscape = true;
                escape = true;
            } else if (c == '\"') {
                return new JsonStringImpl(doc, start, offset += 1, hasEscape);
            } else if (c < ' ') {
                throw failure(UNESCAPED_CONTROL_CODE);
            }
        }
        throw failure(UNCLOSED_STRING.formatted("JSON String"));
    }

    /*
     * Parsing true, false, and null return singletons. These JsonValues
     * do not require offsets to lazily compute their values.
     */
    private JsonBooleanImpl parseTrue() {
        if (charsEqual("rue", offset + 1)) {
            offset += 4;
            return JsonBooleanImpl.TRUE;
        }
        throw failure(UNEXPECTED_VAL);
    }

    private JsonBooleanImpl parseFalse() {
        if (charsEqual( "alse", offset + 1)) {
            offset += 5;
            return JsonBooleanImpl.FALSE;
        }
        throw failure(UNEXPECTED_VAL);
    }

    private JsonNullImpl parseNull() {
        if (charsEqual("ull", offset + 1)) {
            offset += 4;
            return JsonNullImpl.NULL;
        }
        throw failure(UNEXPECTED_VAL);
    }

    /*
     * The parsed JsonNumber contains offsets correlating to the first and last
     * allowed chars permitted in the JSON numeric grammar:
     *      number = [ minus ] int [ frac ] [ exp ]
     * See https://datatracker.ietf.org/doc/html/rfc8259#section-6
     */
    private JsonNumberImpl parseNumber() {
        boolean sawDecimal = false;
        boolean sawExponent = false;
        boolean sawZero = false;
        boolean havePart = false;
        boolean sawSign = false;
        var start = offset;

        endloop:
        for (; hasInput(); offset++) {
            var c = doc[offset];
            switch (c) {
                case '-' -> {
                    if (offset != start && !sawExponent || sawSign) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted(c));
                    }
                    sawSign = true;
                }
                case '+' -> {
                    if (!sawExponent || havePart || sawSign) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted(c));
                    }
                    sawSign = true;
                }
                case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (!sawDecimal && !sawExponent && sawZero) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted('0'));
                    }
                    if (doc[offset] == '0' && !havePart) {
                        sawZero = true;
                    }
                    havePart = true;
                }
                case '.' -> {
                    if (sawDecimal) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted(c));
                    } else {
                        if (!havePart) {
                            throw failure(INVALID_POSITION_IN_NUMBER.formatted(c));
                        }
                        sawDecimal = true;
                        havePart = false;
                    }
                }
                case 'e', 'E' -> {
                    if (sawExponent) {
                        throw failure(INVALID_POSITION_IN_NUMBER.formatted(c));
                    } else {
                        if (!havePart) {
                            throw failure(INVALID_POSITION_IN_NUMBER.formatted(c));
                        }
                        sawExponent = true;
                        havePart = false;
                        sawSign = false;
                    }
                }
                default -> {
                    // break the loop for white space or invalid characters
                    break endloop;
                }
            }
        }
        if (!havePart) {
            throw failure("Input expected after '[.|e|E]'");
        }
        return new JsonNumberImpl(doc, start, offset);
    }

    // Utility functions

    // Called when a SB is required to un-escape a member name
    private void initBuilder() {
        if (builder == null) {
            builder = new StringBuilder();
        }
    }

    // Unescapes the Unicode escape sequence and produces a char
    private char codeUnit() {
        char val = 0;
        int end = offset + 4;
        if (end >= doc.length) {
            throw failure("Invalid Unicode escape sequence. Expected four hex digits");
        }
        while (offset < end) {
            char c = doc[++offset];
            val <<= 4;
            val += (char) (
                    switch (c) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                        case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
                        case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
                        default -> throw failure(
                                "Invalid Unicode escape sequence. '%c' is not a hex digit".formatted(c));
                    });
        }
        return val;
    }

    // Returns true if the parser has not yet reached the end of the Document
    private boolean hasInput() {
        return offset < doc.length;
    }

    // Walk to the next non-white space char from the current offset
    private void skipWhitespaces() {
        while (hasInput()) {
            if (notWhitespace()) {
                break;
            }
            offset++;
        }
    }

    // see https://datatracker.ietf.org/doc/html/rfc8259#section-2
    private boolean notWhitespace() {
        return switch (doc[offset]) {
            case ' ', '\t','\r' -> false;
            case '\n' -> {
                // Increments the row and col
                line += 1;
                lineStart = offset + 1;
                yield false;
            }
            default -> true;
        };
    }

    // returns true if the char at the specified offset equals the input char
    // and is within bounds of the char[]
    private boolean currCharEquals(char c) {
        return hasInput() && c == doc[offset];
    }

    // Returns true if the substring starting at the given offset equals the
    // input String and is within bounds of the JSON document
    private boolean charsEqual(String str, int o) {
        if (o + str.length() <= doc.length) {
            for (int index = 0; index < str.length(); index++) {
                if (doc[o] != str.charAt(index)) {
                    return false; // char does not match
                }
                o++;
            }
            return true; // all chars match
        }
        return false; // not within bounds
    }

    private JsonParseException failure(String message) {
        // Non-revealing message does not produce input source String
        return new JsonParseException("%s. Location: row %d, col %d."
                .formatted(message, line, offset - lineStart),
                line, offset - lineStart);
    }

    // Parsing error messages ----------------------
    private static final String UNEXPECTED_VAL =
            "Unexpected value. Expected a JSON Object, Array, String, Number, Boolean, or Null";
    private static final String UNRECOGNIZED_ESCAPE_SEQUENCE =
            "Unrecognized escape sequence: \"\\%c\"";
    private static final String UNESCAPED_CONTROL_CODE =
            "Unescaped control code";
    private static final String UNCLOSED_STRING =
            "%s is not closed with a quotation mark";
    private static final String INVALID_POSITION_IN_NUMBER =
            "Invalid position of '%c' within JSON Number";
}
