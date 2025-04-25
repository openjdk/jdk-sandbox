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

    static JsonValue parseRoot(JsonDocumentInfo docInfo) {
        JsonValue root = parseValue(docInfo);
        skipWhitespaces(docInfo);
        if (docInfo.offset != docInfo.getEndOffset()) {
            throw failure(docInfo,"Unexpected character(s)");
        }
        return root;
    }

    static JsonValue parseValue(JsonDocumentInfo docInfo) {
        skipWhitespaces(docInfo);
        if (docInfo.offset >= docInfo.getEndOffset()) {
            throw failure(docInfo, "Missing JSON value");
        }
        return switch (docInfo.charAt(docInfo.offset)) {
            case '{' -> parseObject(docInfo);
            case '[' -> parseArray(docInfo);
            case '"' -> parseString(docInfo);
            case 't' -> parseTrue(docInfo);
            case 'f' -> parseFalse(docInfo);
            case 'n' -> parseNull(docInfo);
            // While JSON Number does not support leading '+', '.', or 'e'
            // we still accept, so that we can provide a better error message
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', 'e', '.'
                    -> parseNumber(docInfo);
            default -> throw failure(docInfo, "Unexpected character(s)");
        };
    }

    static JsonObject parseObject(JsonDocumentInfo docInfo) {
        var members = new LinkedHashMap<String, JsonValue>();
        docInfo.offset++; // Walk past the '{'
        skipWhitespaces(docInfo);
        // Check for empty case
        if (docInfo.currCharEquals('}')) {
            docInfo.offset++;
            return new JsonObjectImpl(members);
        }

        StringBuilder sb = null; // only init if we need to use for escapes
        while (docInfo.offset < docInfo.getEndOffset()) {
            // Get the name
            if (!docInfo.currCharEquals('"')) {
                throw failure(docInfo, "Invalid member name");
            }
            // Member equality done via unescaped String
            // see https://datatracker.ietf.org/doc/html/rfc8259#section-8.3
            docInfo.offset++; // Move past the starting quote
            var escape = false;
            boolean useBldr = false;
            var start = docInfo.offset;
            boolean foundClosing = false;
            for (; docInfo.offset < docInfo.getEndOffset(); docInfo.offset++) {
                var c = docInfo.charAt(docInfo.offset);
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
                            if (docInfo.offset + 4 < docInfo.getEndOffset()) {
                                escapeLength = 4;
                                docInfo.offset++; // Move to first char in sequence
                                c = codeUnit(docInfo);
                                // Move back, since outer loop will increment offset
                                docInfo.offset--;
                            } else {
                                throw failure(docInfo, "Invalid Unicode escape sequence");
                            }
                        }
                        default -> throw failure(docInfo, "Illegal escape");
                    }
                    if (!useBldr) {
                        if (sb == null) {
                            sb = new StringBuilder();
                        }
                        // Append everything up to the first escape sequence
                        sb.append(docInfo.getDoc(), start, docInfo.offset - escapeLength - 1 - start);
                        useBldr = true;
                    }
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                    continue;
                } else if (c == '\"') {
                    docInfo.offset++;
                    foundClosing = true;
                    break;
                } else if (c < ' ') {
                    throw failure(docInfo, "Unescaped control code");
                }
                if (useBldr) {
                    sb.append(c);
                }
            }
            if (!foundClosing) {
                throw failure(docInfo, "Closing quote missing");
            }

            String nameStr;
            if (useBldr) {
                nameStr = sb.toString();
                sb.setLength(0);
            } else {
                nameStr = docInfo.substring(start, docInfo.offset - 1);
            }
            if (members.containsKey(nameStr)) {
                throw failure(docInfo, "The duplicate member name: '%s' was already parsed".formatted(nameStr));
            }

            // Move from name to ':'
            skipWhitespaces(docInfo);

            if (!docInfo.currCharEquals(':')) {
                throw failure(docInfo,
                        "Expected ':' after the member name");
            }

            // Move from ':' to JsonValue
            docInfo.offset++;
            skipWhitespaces(docInfo);
            JsonValue val = JsonParser.parseValue(docInfo);
            members.put(nameStr, val);

            // Walk to either ',' or '}'
            skipWhitespaces(docInfo);
            if (docInfo.currCharEquals('}')) {
                docInfo.offset++;
                return new JsonObjectImpl(members);
            } else if (docInfo.currCharEquals(',')) {
                // Add the comma, and move to the next key
                docInfo.offset++;
                skipWhitespaces(docInfo);
                if (docInfo.offset >= docInfo.getEndOffset()) {
                    throw failure(docInfo, "Expected a member after ','");
                }
            } else {
                // Neither ',' nor '}' so fail
                break;
            }
        }
        throw failure(docInfo, "Object was not closed with '}'");
    }

    static JsonArray parseArray(JsonDocumentInfo docInfo) {
        var list = new ArrayList<JsonValue>();
        docInfo.offset++; // Walk past the '['
        skipWhitespaces(docInfo);
        // Check for empty case
        if (docInfo.currCharEquals(']')) {
            docInfo.offset++;
            return new JsonArrayImpl(list);
        }

        while (docInfo.offset < docInfo.getEndOffset()) {
            // Get the JsonValue
            JsonValue val = JsonParser.parseValue(docInfo);
            list.add(val);
            // Walk to either ',' or ']'
            skipWhitespaces(docInfo);
            if (docInfo.currCharEquals(']')) {
                docInfo.offset++;
                return new JsonArrayImpl(list);
            } else if (docInfo.currCharEquals(',')) {
                // Walk past the comma and move to before the next value
                docInfo.offset++;
                skipWhitespaces(docInfo);
            } else {
                // Neither ',' nor ']' after the value, so fail
                break;
            }
        }
        throw failure(docInfo, "Array was not closed with ']'");
    }

    static JsonString parseString(JsonDocumentInfo docInfo) {
        int start = docInfo.offset;
        docInfo.offset++; // Move past the starting quote
        var escape = false;
        for (; docInfo.offset < docInfo.getEndOffset(); docInfo.offset++) {
            var c = docInfo.charAt(docInfo.offset);
            if (escape) {
                switch (c) {
                    // Allowed JSON escapes
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {}
                    case 'u' -> {
                        if (docInfo.offset + 4 < docInfo.getEndOffset()) {
                            docInfo.offset++; // Move to first char in sequence
                            checkEscapeSequence(docInfo);
                            docInfo.offset--; // Move back, outer loop increments
                        } else {
                            throw failure(docInfo, "Invalid Unicode escape sequence");
                        }
                    }
                    default -> throw failure(docInfo, "Illegal escape");
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '\"') {
                return new JsonStringImpl(docInfo, start, docInfo.offset += 1);
            } else if (c < ' ') {
                throw failure(docInfo, "Unescaped control code");
            }
        }
        throw failure(docInfo, "Closing quote missing");
    }

    // Validate unicode escape sequence
    static void checkEscapeSequence(JsonDocumentInfo docInfo) {
        for (int index = 0; index < 4; index++) {
            char c = docInfo.charAt(docInfo.offset);
            if ((c < 'a' || c > 'f') && (c < 'A' || c > 'F') && (c < '0' || c > '9')) {
                throw failure(docInfo, "Invalid Unicode escape sequence");
            }
            docInfo.offset++;
        }
    }

    // Validate and construct corresponding value of unicode escape sequence
    static char codeUnit(JsonDocumentInfo docInfo) {
        char val = 0;
        for (int index = 0; index < 4; index ++) {
            char c = docInfo.charAt(docInfo.offset);
            val <<= 4;
            val += (char) (
                    switch (c) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                        case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
                        case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
                        default -> throw failure(docInfo, "Invalid Unicode escape sequence");
                    });
            docInfo.offset++;
        }
        return val;
    }

    static JsonBooleanImpl parseTrue(JsonDocumentInfo docInfo) {
        if (docInfo.charsEqual("rue", docInfo.offset + 1)) {
            docInfo.offset += 4;
            return JsonBooleanImpl.TRUE;
        }
        throw failure(docInfo, "Expected true");
    }

    static JsonBooleanImpl parseFalse(JsonDocumentInfo docInfo) {
        if (docInfo.charsEqual( "alse", docInfo.offset + 1)) {
            docInfo.offset += 5;
            return JsonBooleanImpl.FALSE;
        }
        throw failure(docInfo, "Expected false");
    }

    static JsonNullImpl parseNull(JsonDocumentInfo docInfo) {
        if (docInfo.charsEqual("ull", docInfo.offset + 1)) {
            docInfo.offset += 4;
            return JsonNullImpl.NULL;
        }
        throw failure(docInfo, "Expected null");
    }

    static JsonNumberImpl parseNumber(JsonDocumentInfo docInfo) {
        boolean sawDecimal = false;
        boolean sawExponent = false;
        boolean sawZero = false;
        boolean sawWhitespace = false;
        boolean havePart = false;
        boolean sawInvalid = false;
        boolean sawSign = false;
        var start = docInfo.offset;
        for (; docInfo.offset < docInfo.getEndOffset() && !sawWhitespace && !sawInvalid; docInfo.offset++) {
            switch (docInfo.charAt(docInfo.offset)) {
                case '-' -> {
                    if (docInfo.offset != start && !sawExponent || sawSign) {
                        throw failure(docInfo, "Invalid '-' position");
                    }
                    sawSign = true;
                }
                case '+' -> {
                    if (!sawExponent || havePart || sawSign) {
                        throw failure(docInfo, "Invalid '+' position");
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
                        throw failure(docInfo, "Invalid '0' position");
                    }
                    havePart = true;
                }
                case '.' -> {
                    if (sawDecimal) {
                        throw failure(docInfo, "Invalid '.' position");
                    } else {
                        if (!havePart) {
                            throw failure(docInfo, "Invalid '.' position");
                        }
                        sawDecimal = true;
                        havePart = false;
                    }
                }
                case 'e', 'E' -> {
                    if (sawExponent) {
                        throw failure(docInfo, "Invalid '[e|E]' position");
                    } else {
                        if (!havePart) {
                            throw failure(docInfo, "Invalid '[e|E]' position");
                        }
                        sawExponent = true;
                        havePart = false;
                        sawSign = false;
                    }
                }
                case ' ', '\t', '\r', '\n' -> {
                    sawWhitespace = true;
                    docInfo.offset --;
                }
                default -> {
                    docInfo.offset--;
                    sawInvalid = true;
                }
            }
        }
        if (!havePart) {
            throw failure(docInfo, "Input expected after '[.|e|E]'");
        }
        return new JsonNumberImpl(docInfo, start, docInfo.offset);
    }

    // Utility functions

    // Walk to the next non-white space char from the current docInfo offset
    static void skipWhitespaces(JsonDocumentInfo docInfo) {
        while (docInfo.offset < docInfo.getEndOffset()) {
            if (notWhitespace(docInfo)) {
                break;
            }
            docInfo.offset++;
        }
    }

    static boolean notWhitespace(JsonDocumentInfo docInfo) {
        return switch (docInfo.charAt(docInfo.offset)) {
            case ' ', '\t','\r' -> false;
            case '\n' -> {
                docInfo.updateLine(docInfo.offset + 1);
                yield false;
            }
            default -> true;
        };
    }

    static JsonParseException failure(JsonDocumentInfo docInfo, String message) {
        var errMsg = docInfo.composeParseExceptionMessage(
                message, docInfo.getLine(), docInfo.getLineStart(), docInfo.offset);
        return new JsonParseException(errMsg, docInfo.getLine(), docInfo.offset - docInfo.getLineStart());
    }

    // no instantiation of this parser
    private JsonParser(){}
}
