/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.jmx.remote.rest.json;

/**
 * @author harsha
 */
public class JSONPrimitive implements JSONElement {

    private final Object value;

    public JSONPrimitive(long i) {
        value = i;
    }

    public JSONPrimitive(double i) {
        value = i;
    }

    public JSONPrimitive(Boolean i) {
        value = i;
    }

    public JSONPrimitive(String s) {
        value = s != null ? unescape(s) : s;
    }

    public JSONPrimitive() {
        value = null;
    }

    public static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '\b':
                    sb.append("\\b");
                    break;
                case '\f':
                    sb.append("\\f");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                default:
                    sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    public static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ++i) {
            if (s.charAt(i) == '\\') {
                if (i + 1 < s.length() - 1) {
                    ++i;
                    switch (s.charAt(i)) {
                        case '\\':
                            sb.append('\\');
                            break;
                        case '\b':
                            sb.append('\b');
                            break;
                        case '\f':
                            sb.append('\f');
                            break;
                        case '\n':
                            sb.append('\n');
                            break;
                        case '\r':
                            sb.append('\r');
                            break;
                        case '\t':
                            sb.append('\t');
                            break;
                        case '\"':
                            sb.append('\"');
                            break;
                        default:
                            sb.append(s.charAt(i));
                    }
                }
            } else {
                sb.append(s.charAt(i));
            }
        }
        return sb.toString();
    }

    public Object getValue() {
        return value;
    }

    @Override
    public String toJsonString() {
        if (value instanceof String) {
            return "\"" + escape(value.toString()) + "\"";
        }
        return value != null ? value.toString() : null;
    }
}
