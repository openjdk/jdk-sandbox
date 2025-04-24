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
        int offset = skipWhitespaces(docInfo, 0);
        if (offset >= docInfo.getEndOffset()) {
            throw failure(docInfo, "Missing JSON value", offset);
        }
        JsonValueImpl root = parseValue(docInfo, offset);
        int end = root.getEndOffset();
        if (!checkWhitespaces(docInfo, end, docInfo.getEndOffset())) {
            throw failure(docInfo,"Unexpected character(s)", end);
        }
        return (JsonValue) root;
    }

    static JsonValueImpl parseValue(JsonDocumentInfo docInfo, int offset) {
        offset = skipWhitespaces(docInfo, offset);
        if (offset >= docInfo.getEndOffset()) {
            throw failure(docInfo, "Missing JSON value", offset);
        }
        return switch (docInfo.charAt(offset)) {
            case '{' -> parseObject(docInfo, offset);
            case '[' -> parseArray(docInfo, offset);
            case '"' -> parseString(docInfo, offset);
            case 't' -> parseTrue(docInfo, offset);
            case 'f' -> parseFalse(docInfo, offset);
            case 'n' -> parseNull(docInfo, offset);
            // While JSON Number does not support leading '+', '.', or 'e'
            // we still accept, so that we can provide a better error message
            case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '+', 'e', '.'
                    -> parseNumber(docInfo, offset);
            default -> throw failure(docInfo, "Unexpected character(s)", offset);
        };
    }

    static JsonObjectImpl parseObject(JsonDocumentInfo docInfo, int offset) {
        var members = new LinkedHashMap<String, JsonValue>();

        // Walk past the '{'
        offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
        // Check for empty case
        if (docInfo.charEquals('}', offset)) {
            return new JsonObjectImpl(members, ++offset);
        }

        StringBuilder sb = null; // only init if we need to use for escapes
        while (offset < docInfo.getEndOffset()) {
            // Get the name
            if (!docInfo.charEquals('"', offset)) {
                throw failure(docInfo, "Invalid member name", offset);
            }
            // Member equality done via unescaped String
            // see https://datatracker.ietf.org/doc/html/rfc8259#section-8.3
            offset++; // Move past the starting quote
            var escape = false;
            boolean useBldr = false;
            var start = offset;
            boolean foundClosing = false;
            for (; offset < docInfo.getEndOffset(); offset++) {
                var c = docInfo.charAt(offset);
                if (escape) {
                    var length = 0;
                    switch (c) {
                        // Allowed JSON escapes
                        case '"', '\\', '/' -> {}
                        case 'b' -> c = '\b';
                        case 'f' -> c = '\f';
                        case 'n' -> c = '\n';
                        case 'r' -> c = '\r';
                        case 't' -> c = '\t';
                        case 'u' -> {
                            if (offset + 4 < docInfo.getEndOffset()) {
                                c = codeUnit(docInfo, offset + 1);
                                length = 4;
                            } else {
                                throw failure(docInfo,
                                        "Invalid Unicode escape sequence", offset);
                            }
                        }
                        default -> throw failure(docInfo,
                                "Illegal escape", offset);
                    }
                    if (!useBldr) {
                        if (sb == null) {
                            sb = new StringBuilder();
                        }
                        sb.append(docInfo.getDoc(), start, offset - 1 - start);
                        useBldr = true;
                    }
                    offset+=length;
                    escape = false;
                } else if (c == '\\') {
                    escape = true;
                    continue;
                } else if (c == '\"') {
                    offset++;
                    foundClosing = true;
                    break;
                } else if (c < ' ') {
                    throw failure(docInfo,
                            "Unescaped control code", offset);
                }
                if (useBldr) {
                    sb.append(c);
                }
            }
            if (!foundClosing) {
                throw failure(docInfo, "Closing quote missing", offset);
            }

            String nameStr;
            if (useBldr) {
                nameStr = sb.toString();
                sb.setLength(0);
            } else {
                nameStr = docInfo.substring(start, offset - 1);
            }
            if (members.containsKey(nameStr)) {
                throw failure(docInfo, "The duplicate member name: '%s' was already parsed".formatted(nameStr), offset);
            }

            // Move from name to ':'
            offset = JsonParser.skipWhitespaces(docInfo, offset);

            if (!docInfo.charEquals(':', offset)) {
                throw failure(docInfo,
                        "Expected ':' after the member name", offset);
            }

            // Move from ':' to JsonValue
            offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
            JsonValueImpl val = JsonParser.parseValue(docInfo, offset);
            members.put(nameStr, (JsonValue) val);
            // Move to end of JsonValue
            offset = val.getEndOffset();

            // Walk to either ',' or '}'
            offset = JsonParser.skipWhitespaces(docInfo, offset);
            if (docInfo.charEquals('}', offset)) {
                return new JsonObjectImpl(members, ++offset);
            } else if (docInfo.charEquals(',', offset)) {
                // Add the comma, and move to the next key
                offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
                if (offset >= docInfo.getEndOffset()) {
                    throw failure(docInfo, "Expected a member after ','", offset);
                }
            } else {
                // Neither ',' nor '}' so fail
                break;
            }
        }
        throw failure(docInfo, "Object was not closed with '}'", offset);
    }

    static JsonArrayImpl parseArray(JsonDocumentInfo docInfo, int offset) {
        var list = new ArrayList<JsonValue>();
        // Walk past the '['
        offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
        // Check for empty case
        if (docInfo.charEquals(']', offset)) {
            return new JsonArrayImpl(list, ++offset);
        }

        while (offset < docInfo.getEndOffset()) {
            // Get the JsonValue
            JsonValueImpl val = JsonParser.parseValue(docInfo, offset);
            list.add((JsonValue) val);

            offset = val.getEndOffset();

            // Walk to either ',' or ']'
            offset = JsonParser.skipWhitespaces(docInfo, offset);
            if (docInfo.charEquals(']', offset)) {
                return new JsonArrayImpl(list, ++offset);
            } else if (docInfo.charEquals(',', offset)) {
                // Add the comma, and move to the next value
                offset = JsonParser.skipWhitespaces(docInfo, offset + 1);
                if (offset >= docInfo.getEndOffset()) {
                    throw failure(docInfo, "Expected a value after ','", offset);
                }
            } else {
                // Neither ',' nor ']' so fail
                break;
            }
        }
        throw failure(docInfo, "Array was not closed with ']'", offset);
    }

    static JsonStringImpl parseString(JsonDocumentInfo docInfo, int offset) {
        int start = offset;
        offset++; // Move past the starting quote
        var escape = false;
        for (; offset < docInfo.getEndOffset(); offset++) {
            var c = docInfo.charAt(offset);
            if (escape) {
                switch (c) {
                    // Allowed JSON escapes
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {}
                    case 'u' -> {
                        if (offset + 4 < docInfo.getEndOffset()) {
                            checkEscapeSequence(docInfo, offset + 1);
                            offset += 4;
                        } else {
                            throw failure(docInfo,
                                    "Invalid Unicode escape sequence", offset);
                        }
                    }
                    default -> throw failure(docInfo,
                            "Illegal escape", offset);
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c == '\"') {
                return new JsonStringImpl(docInfo, start, ++offset);
            } else if (c < ' ') {
                throw failure(docInfo,
                        "Unescaped control code", offset);
            }
        }
        throw failure(docInfo, "Closing quote missing", offset);
    }

    // Validate unicode escape sequence
    static void checkEscapeSequence(JsonDocumentInfo docInfo, int offset) {
        for (int index = 0; index < 4; index++) {
            char c = docInfo.charAt(offset + index);
            if ((c < 'a' || c > 'f') && (c < 'A' || c > 'F') && (c < '0' || c > '9')) {
                throw failure(docInfo, "Invalid Unicode escape sequence", offset);
            }
        }
    }

    // Validate and construct corresponding value of unicode escape sequence
    static char codeUnit(JsonDocumentInfo docInfo, int offset) {
        char val = 0;
        for (int index = 0; index < 4; index ++) {
            char c = docInfo.charAt(offset + index);
            val <<= 4;
            val += (char) (
                    switch (c) {
                        case '0', '1', '2', '3', '4', '5', '6', '7', '8', '9' -> c - '0';
                        case 'a', 'b', 'c', 'd', 'e', 'f' -> c - 'a' + 10;
                        case 'A', 'B', 'C', 'D', 'E', 'F' -> c - 'A' + 10;
                        default -> throw failure(docInfo, "Invalid Unicode escape sequence", offset);
                    });
        }
        return val;
    }

    static JsonBooleanImpl parseTrue(JsonDocumentInfo docInfo, int offset) {
        if (docInfo.charsEqual("rue", offset + 1)) {
            return new JsonBooleanImpl(docInfo, offset, offset + 4);
        }
        throw failure(docInfo, "Expected true", offset);
    }

    static JsonBooleanImpl parseFalse(JsonDocumentInfo docInfo, int offset) {
        if (docInfo.charsEqual( "alse", offset + 1)) {
            return new JsonBooleanImpl(docInfo, offset, offset + 5);
        }
        throw failure(docInfo, "Expected false", offset);
    }

    static JsonNullImpl parseNull(JsonDocumentInfo docInfo, int offset) {
        if (docInfo.charsEqual("ull", offset + 1)) {
            return new JsonNullImpl(offset + 4);
        }
        throw failure(docInfo, "Expected null", offset);
    }

    static JsonNumberImpl parseNumber(JsonDocumentInfo docInfo, int offset) {
        boolean sawDecimal = false;
        boolean sawExponent = false;
        boolean sawZero = false;
        boolean sawWhitespace = false;
        boolean havePart = false;
        boolean sawInvalid = false;
        boolean sawSign = false;
        var start = offset;
        for (; offset < docInfo.getEndOffset() && !sawWhitespace && !sawInvalid; offset++) {
            switch (docInfo.charAt(offset)) {
                case '-' -> {
                    if (offset != start && !sawExponent || sawSign) {
                        throw failure(docInfo,
                                "Invalid '-' position", offset);
                    }
                    sawSign = true;
                }
                case '+' -> {
                    if (!sawExponent || havePart || sawSign) {
                        throw failure(docInfo,
                                "Invalid '+' position", offset);
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
                        throw failure(docInfo,
                                "Invalid '0' position", offset);
                    }
                    havePart = true;
                }
                case '.' -> {
                    if (sawDecimal) {
                        throw failure(docInfo,
                                "Invalid '.' position", offset);
                    } else {
                        if (!havePart) {
                            throw failure(docInfo,
                                    "Invalid '.' position", offset);
                        }
                        sawDecimal = true;
                        havePart = false;
                    }
                }
                case 'e', 'E' -> {
                    if (sawExponent) {
                        throw failure(docInfo,
                                "Invalid '[e|E]' position", offset);
                    } else {
                        if (!havePart) {
                            throw failure(docInfo,
                                    "Invalid '[e|E]' position", offset);
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
            throw failure(docInfo,
                    "Input expected after '[.|e|E]'", offset);
        }
        return new JsonNumberImpl(docInfo, start, offset);
    }

    // Utility functions
    static int skipWhitespaces(JsonDocumentInfo docInfo, int offset) {
        while (offset < docInfo.getEndOffset()) {
            if (notWhitespace(docInfo, offset)) {
                break;
            }
            offset ++;
        }
        return offset;
    }

    static boolean checkWhitespaces(JsonDocumentInfo docInfo, int offset, int endOffset) {
        int end = Math.min(endOffset, docInfo.getEndOffset());
        while (offset < end) {
            if (notWhitespace(docInfo, offset)) {
                return false;
            }
            offset ++;
        }
        return true;
    }

    static boolean notWhitespace(JsonDocumentInfo docInfo, int offset) {
        return !isWhitespace(docInfo, offset);
    }

    static boolean isWhitespace(JsonDocumentInfo docInfo, int offset) {
        return switch (docInfo.charAt(offset)) {
            case ' ', '\t','\r' -> true;
            case '\n' -> {
                docInfo.updatePosition(offset + 1);
                yield true;
            }
            default -> false;
        };
    }

    static JsonParseException failure(JsonDocumentInfo docInfo, String message, int offset) {
        var errMsg = docInfo.composeParseExceptionMessage(
                message, docInfo.getLine(), docInfo.getLineStart(), offset);
        return new JsonParseException(errMsg, docInfo.getLine(), offset - docInfo.getLineStart());
    }

    // no instantiation of this parser
    private JsonParser(){}
}
