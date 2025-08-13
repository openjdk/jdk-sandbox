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

import java.util.List;
import java.util.Map;
import java.util.json.JsonArray;
import java.util.json.JsonAssertionException;
import java.util.json.JsonBoolean;
import java.util.json.JsonNull;
import java.util.json.JsonNumber;
import java.util.json.JsonObject;
import java.util.json.JsonString;
import java.util.json.JsonValue;

/**
 * Shared utilities for Json classes.
 */
public class Utils {

    // Non instantiable
    private Utils() {}

    // Equivalent to JsonObject/Array.of() factories without the need for defensive copy
    // and other input validation
    public static JsonArray arrayOf(List<JsonValue> list) {
        return new JsonArrayImpl(list);
    }

    public static JsonObject objectOf(Map<String, JsonValue> map) {
        return new JsonObjectImpl(map);
    }

    /*
     * Escapes a String to ensure it is a valid JSON String.
     * Backslash, double quote, and control chars are escaped.
     * Providing this method in Utils allows for a bypass of `JsonString.of(str).value()`
     * for the toString representation of JsonObject member names.
     */
    public static String escape(String str) {
        StringBuilder sb = null; // Lazy init
        for (int i = 0; i < str.length(); i++) {
            char c = str.charAt(i);
            // Does not require escaping
            if (c >= 32 && c != '\\' && c != '"') {
                if (sb != null) {
                    sb.append(c);
                }
            // Requires escaping
            } else {
                if (sb == null) {
                    sb = new StringBuilder().append(str, 0, i);
                }
                // 2 Char escapes (Non-control characters)
                if (c == '\\') {
                    sb.append('\\').append(c);
                } else if (c == '"') {
                    sb.append('\\').append(c);
                    // 2 Char escapes (Control characters)
                } else if (c == '\b') {
                    sb.append('\\').append('b');
                } else if (c == '\f') {
                    sb.append('\\').append('f');
                } else if (c == '\n') {
                    sb.append('\\').append('n');
                } else if (c == '\r') {
                    sb.append('\\').append('r');
                } else if (c == '\t') {
                    sb.append('\\').append('t');
                    // All other chars requiring Unicode escape sequence
                } else {
                    sb.append('\\').append('u').append(String.format("%04X", (int) c));
                }
            }
        }
        return sb == null ? str : sb.toString();
    }

    // Use to compose an exception when casting to an incorrect type
    public static JsonAssertionException composeTypeError(JsonValue jv, String expected) {
        var actual = switch (jv) {
            case JsonObject _ -> "JsonObject";
            case JsonArray _ -> "JsonArray";
            case JsonBoolean _ -> "JsonBoolean";
            case JsonNull _ -> "JsonNull";
            case JsonNumber _ -> "JsonNumber";
            case JsonString _ -> "JsonString";
        };
        return new JsonAssertionException(
                "%s is not a %s.".formatted(actual, expected) +
                (jv instanceof JsonValueImpl jvi && jvi.row() > -1 && jvi.col() > -1 ?
                " Path: \"%s\". Location: row %d, col %d."
                .formatted(toPath(jvi), jvi.row(), jvi.col()) : ""));
    }

    public static String toPath(JsonValueImpl jvi) {
        var sb = new StringBuilder();
        toPath(jvi.offset(), jvi.doc(), sb);
        return sb.toString();
    }

    private static void toPath(int offset, char[] doc, StringBuilder sb) {
        // Walk past starting char and white space
        offset = walkWhitespace(offset - 1, doc);
        // If offset is -1, found ROOT and we are finished
        if (offset != -1) {
            // Node case
            offset = switch (doc[offset]) {
                // Does the actual appending
                // Walks to the node's starting [ or {
                case ',', '[' -> arrayNode(offset, doc, sb);
                case ':' -> objectNode(offset, doc, sb);
                default -> throw new InternalError();
            };
            toPath(offset, doc, sb);
        }
    }

    private static int walkWhitespace(int offset, char[] doc) {
        while (offset >= 0) {
            var ws = switch (doc[offset]) {
                case ' ', '\t','\r','\n' -> true;
                default -> false;
            };
            if (!ws) {
                break;
            }
            offset--;
        }
        return offset;
    }

    // Backtracking from an element in a JsonArray either expects a ',' or '['
    // E.g. " [ val ... " or " [ foo, val "
    private static int arrayNode(int offset, char[] doc, StringBuilder sb) {
        int aDepth = 0;
        int oDepth = 0;
        int values = 0;
        while (offset > 0) {
            var c = doc[offset];
            if (c == '[') {
                aDepth++;
            } else if (c == ']') {
                aDepth--;
            } else if (c == '{') {
                oDepth++;
            } else if (c == '}') {
                oDepth--;
            } else if (c == ',' && aDepth == 0 && oDepth == 0) {
                values++;
            }
            if (aDepth > 0) {
                break;
            }
            offset--;
        }
        sb.insert(0, '[' + String.valueOf(values));
        return offset;
    }

    // Unlike arrayNode, always expects a ':'
    // Regardless of value position, always preceded by a member name and colon
    private static int objectNode(int offset, char[] doc, StringBuilder sb) {
        offset--; // Walk past ':'
        int depth = 0;
        int nameStart = 0;
        int nameEnd = 0;
        boolean inName = false;

        // Append member name first
        while (offset > 0) {
            var c = doc[offset];
            if (c == '"' && !inName) {
                nameEnd = offset;
                inName = true;
            } else if (c == '"' && doc[offset - 1] != '\\') {
                // Pre-escape check should not throw AIOOBE because guaranteed
                // to have enclosing opening bracket
                nameStart = offset + 1;
                break;
            }
            offset--;
        }

        // Add the name
        sb.insert(0, '{' + new String(doc, nameStart, nameEnd - nameStart));

        // Move to parent offset
        while (offset > 0) {
            var c = doc[offset];
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
            }
            if (depth > 0) {
                break;
            }
            offset--;
        }
        return offset;
    }
}
