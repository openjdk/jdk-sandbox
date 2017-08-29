/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
