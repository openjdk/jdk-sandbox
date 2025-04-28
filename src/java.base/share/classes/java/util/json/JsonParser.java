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

import java.util.ArrayList;
import java.util.LinkedHashMap;

// Parse the JSON document which creates a tree of nodes
// These nodes are lazy, structural JSON nodes contain their Data Structures
// Primitive JSON Values are fully lazy until their value/string is accessed
final class JsonParser { ;

    // Access to the underlying JSON contents
    private final char[] doc;
    // Current offset during parsing
    int offset;
    // For exception message on failure
    private int line;
    private int lineStart;

    JsonParser(char[] doc) {
        this.doc = doc;
    }

    JsonValue parseRoot() {
        JsonValue root = parseValue();
        skipWhitespaces();
        if (offset != doc.length) {
            throw failure("Unexpected character(s)");
        }
        return root;
    }

    JsonValue parseValue() {
        skipWhitespaces();
        if (offset >= doc.length) {
            throw failure( "Missing JSON value");
        }
        return switch (doc[offset]) {
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
            default -> throw failure( "Unexpected character(s)");
        };
    }

    JsonObject parseObject() {
        var members = new LinkedHashMap<String, JsonValue>();
        offset++; // Walk past the '{'
        skipWhitespaces();
        // Check for empty case
        if (currCharEquals('}')) {
            offset++;
            return new JsonObjectImpl(members);
        }

        StringBuilder sb = null; // only init if we need to use for escapes
        while (offset < doc.length) {
            // Get the name
            if (!currCharEquals('"')) {
                throw failure( "Invalid member name");
            }
            // Member equality done via unescaped String
            // see https://datatracker.ietf.org/doc/html/rfc8259#section-8.3
            offset++; // Move past the starting quote
            var escape = false;
            boolean useBldr = false;
            var start = offset;
            boolean foundClosing = false;
            for (; offset < doc.length; offset++) {
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
                            if (offset + 4 < doc.length) {
                                escapeLength = 4;
                                offset++; // Move to first char in sequence
                                c = codeUnit(doc, offset);
                                // Move to the last hex digit, since outer loop will increment offset
                                offset += 3;
                            } else {
                                throw failure( "Invalid Unicode escape sequence");
                            }
                        }
                        default -> throw failure( "Illegal escape");
                    }
                    if (!useBldr) {
                        if (sb == null) {
                            sb = new StringBuilder();
                        }
                        // Append everything up to the first escape sequence
                        sb.append(doc, start, offset - escapeLength - 1 - start);
                        useBldr = true;
                    }
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                    continue;
                } else if (c == '\"') {
                    offset++;
                    foundClosing = true;
                    break;
                } else if (c < ' ') {
                    throw failure( "Unescaped control code");
                }
                if (useBldr) {
                    sb.append(c);
                }
            }
            if (!foundClosing) {
                throw failure( "Closing quote missing");
            }

            String nameStr;
            if (useBldr) {
                nameStr = sb.toString();
                sb.setLength(0);
            } else {
                nameStr = new String(doc, start, offset - start - 1);
            }
            if (members.containsKey(nameStr)) {
                throw failure( "The duplicate member name: '%s' was already parsed".formatted(nameStr));
            }

            // Move from name to ':'
            skipWhitespaces();

            if (!currCharEquals(':')) {
                throw failure(
                        "Expected ':' after the member name");
            }

            // Move from ':' to JsonValue
            offset++;
            skipWhitespaces();
            JsonValue val = parseValue();
            members.put(nameStr, val);

            // Walk to either ',' or '}'
            skipWhitespaces();
            if (currCharEquals('}')) {
                offset++;
                return new JsonObjectImpl(members);
            } else if (currCharEquals(',')) {
                // Add the comma, and move to the next key
                offset++;
                skipWhitespaces();
                if (offset >= doc.length) {
                    throw failure( "Expected a member after ','");
                }
            } else {
                // Neither ',' nor '}' so fail
                break;
            }
        }
        throw failure( "Object was not closed with '}'");
    }

    JsonArray parseArray() {
        var list = new ArrayList<JsonValue>();
        offset++; // Walk past the '['
        skipWhitespaces();
        // Check for empty case
        if (currCharEquals(']')) {
            offset++;
            return new JsonArrayImpl(list);
        }

        while (offset < doc.length) {
            // Get the JsonValue
            JsonValue val = parseValue();
            list.add(val);
            // Walk to either ',' or ']'
            skipWhitespaces();
            if (currCharEquals(']')) {
                offset++;
                return new JsonArrayImpl(list);
            } else if (currCharEquals(',')) {
                // Walk past the comma and move to before the next value
                offset++;
                skipWhitespaces();
            } else {
                // Neither ',' nor ']' after the value, so fail
                break;
            }
        }
        throw failure( "Array was not closed with ']'");
    }

    JsonString parseString() {
        int start = offset;
        offset++; // Move past the starting quote
        var escape = false;
        for (; offset < doc.length; offset++) {
            var c = doc[offset];
            if (escape) {
                switch (c) {
                    // Allowed JSON escapes
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {}
                    case 'u' -> {
                        if (offset + 4 < doc.length) {
                            offset++; // Move to first char in sequence
                            checkEscapeSequence(doc);
                            offset += 3; // Move to the last hex digit, outer loop increments
                        } else {
                            throw failure( "Invalid Unicode escape sequence");
                        }
                    }
                    default -> throw failure( "Illegal escape");
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '\"') {
                return new JsonStringImpl(doc, start, offset += 1);
            } else if (c < ' ') {
                throw failure( "Unescaped control code");
            }
        }
        throw failure( "Closing quote missing");
    }

    JsonBooleanImpl parseTrue() {
        if (charsEqual("rue", offset + 1)) {
            offset += 4;
            return JsonBooleanImpl.TRUE;
        }
        throw failure( "Expected true");
    }

    JsonBooleanImpl parseFalse() {
        if (charsEqual( "alse", offset + 1)) {
            offset += 5;
            return JsonBooleanImpl.FALSE;
        }
        throw failure( "Expected false");
    }

    JsonNullImpl parseNull() {
        if (charsEqual("ull", offset + 1)) {
            offset += 4;
            return JsonNullImpl.NULL;
        }
        throw failure( "Expected null");
    }

    JsonNumberImpl parseNumber() {
        boolean sawDecimal = false;
        boolean sawExponent = false;
        boolean sawZero = false;
        boolean sawWhitespace = false;
        boolean havePart = false;
        boolean sawInvalid = false;
        boolean sawSign = false;
        var start = offset;
        for (; offset < doc.length && !sawWhitespace && !sawInvalid; offset++) {
            switch (doc[offset]) {
                case '-' -> {
                    if (offset != start && !sawExponent || sawSign) {
                        throw failure( "Invalid '-' position");
                    }
                    sawSign = true;
                }
                case '+' -> {
                    if (!sawExponent || havePart || sawSign) {
                        throw failure( "Invalid '+' position");
                    }
                    sawSign = true;
                }
                case '0' -> {
                    if (!havePart) {
                        sawZero = true;
                    }
                    havePart = true;
                }
                case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (!sawDecimal && !sawExponent && sawZero) {
                        throw failure( "Invalid '0' position");
                    }
                    havePart = true;
                }
                case '.' -> {
                    if (sawDecimal) {
                        throw failure( "Invalid '.' position");
                    } else {
                        if (!havePart) {
                            throw failure( "Invalid '.' position");
                        }
                        sawDecimal = true;
                        havePart = false;
                    }
                }
                case 'e', 'E' -> {
                    if (sawExponent) {
                        throw failure( "Invalid '[e|E]' position");
                    } else {
                        if (!havePart) {
                            throw failure("Invalid '[e|E]' position");
                        }
                        sawExponent = true;
                        havePart = false;
                        sawSign = false;
                    }
                }
                case ' ', '\t', '\r', '\n' -> {
                    sawWhitespace = true;
                    offset --;
                }
                default -> {
                    offset--;
                    sawInvalid = true;
                }
            }
        }
        if (!havePart) {
            throw failure( "Input expected after '[.|e|E]'");
        }
        return new JsonNumberImpl(doc, start, offset);
    }

    // Utility functions

    // Validate unicode escape sequence
    // This method does not increment offset
    void checkEscapeSequence(char[] doc) {
        for (int index = 0; index < 4; index++) {
            char c = doc[offset + index];
            if ((c < 'a' || c > 'f') && (c < 'A' || c > 'F') && (c < '0' || c > '9')) {
                throw failure("Invalid Unicode escape sequence");
            }
        }
    }

    // Validate and construct corresponding value of unicode escape sequence
    // This method does not increment offset
    static char codeUnit(char[] doc, int o) {
        char val = 0;
        for (int index = 0; index < 4; index ++) {
            char c = doc[o + index];
            val <<= 4;
            val += (char) (
                switch (c) {
                    case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                    case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
                    case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
                    default -> throw new JsonParseException("Invalid Unicode escape sequence");
                });
        }
        return val;
    }

    // Walk to the next non-white space char from the current offset
    void skipWhitespaces() {
        while (offset < doc.length) {
            if (notWhitespace()) {
                break;
            }
            offset++;
        }
    }

    boolean notWhitespace() {
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

    JsonParseException failure(String message) {
        var errMsg = composeParseExceptionMessage(
                message, line, lineStart, offset);
        return new JsonParseException(errMsg, line, offset - lineStart);
    }

    // returns true if the char at the specified offset equals the input char
    // and is within bounds of the char[]
    boolean currCharEquals(char c) {
        return offset < doc.length && c == doc[offset];
    }

    // Returns true if the substring starting at the given offset equals the
    // input String and is within bounds of the JSON document
    boolean charsEqual(String str, int offset) {
        if (offset + str.length() - 1 < doc.length) {
            for (int index = 0; index < str.length(); index++) {
                if (doc[offset] != str.charAt(index)) {
                    return false; // char does not match
                }
                offset++;
            }
            return true; // all chars match
        }
        return false; // not within bounds
    }

    // Utility method to compose parse exception message
    String composeParseExceptionMessage(String message, int line, int lineStart, int offset) {
        return "%s: (%s) at Row %d, Col %d."
            .formatted(message, new String(doc, offset, Math.min(offset + 8, doc.length) - offset),
                line, offset - lineStart);
    }
}
