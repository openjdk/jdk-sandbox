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
import java.util.json.JsonObject;
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
     * 1) Converts the input into a JSON compliant String whose Unicode escape
     * sequences have been decoded. The resultant char may be re-escaped if required.
     * 2) This method also ensures the input is JSON compliant (Checks for
     * unescaped control characters or quotation marks).
     */
    public static String getCompliantString(char[] doc, int startOffset, int endOffset) {
        StringBuilder sb = null; // Only use if required
        var escape = false;
        int offset = startOffset;
        boolean useBldr = false;
        for (; offset < endOffset; offset++) {
            var c = doc[offset];
            if (escape) {
                var dropEscape = false;
                var length = 0;
                switch (c) {
                    // Eligible 2 char escapes
                    case '"', '\\', '/', 'b', 'f', 'n', 'r', 't' -> {}
                    case 'u' -> {
                        if (offset + 4 < endOffset) {
                            c = codeUnit(doc, offset + 1);
                            length = 4;
                            c = switch (c) {
                                case '"', '\\', '/' -> c;
                                case '\b' -> 'b';
                                case '\f' -> 'f';
                                case '\n' -> 'n';
                                case '\r' -> 'r';
                                case '\t' -> 't';
                                default -> {
                                    dropEscape = true;
                                    yield c;
                                }
                            };
                        } else {
                            throw new IllegalArgumentException("Illegal Unicode escape sequence");
                        }
                    }
                    default -> throw new IllegalArgumentException("Illegal escape sequence");
                }
                if (!useBldr) {
                    useBldr = true;
                    // At best, we know the size of the first escaped value
                    sb = new StringBuilder(endOffset - startOffset - length)
                            .append(doc, startOffset, offset - startOffset);
                }
                if (dropEscape) {
                    // Remove the backslash on valid converted U escape sequence
                    // that does not require escaping
                    sb.deleteCharAt(sb.length() - 1);
                }
                offset+=length;
                escape = false;
            } else if (c == '\\') {
                escape = true;
            } else if (c < ' ' || c == '"') {
                throw new IllegalArgumentException("Reserved character: '%c' is not escaped".formatted(c));
            }
            if (useBldr) {
                sb.append(c);
            }
        }
        if (escape) {
            throw new IllegalArgumentException("Reserved character: '\\' is not escaped");
        }
        if (useBldr) {
            return sb.toString();
        } else {
            return new String(doc, startOffset, endOffset - startOffset);
        }
    }

    // Validate and construct corresponding value of Unicode escape sequence
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
                        default -> throw new IllegalArgumentException("Illegal Unicode escape sequence");
                    });
        }
        return val;
    }
}
