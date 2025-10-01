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

package jdk.incubator.json.impl;

import jdk.incubator.json.JsonString;
import jdk.internal.ValueBased;

/**
 * JsonString implementation class
 */
@ValueBased
public final class JsonStringImpl implements JsonString, JsonValueImpl {

    private final char[] doc;
    private final int startOffset;
    private final int endOffset;
    private final boolean hasEscape;

    // The String instance representing this JSON string for `toString()`.
    // It always conforms to JSON syntax. If created by parsing a JSON document,
    // it matches the original text exactly. If created via the factory method,
    // non-conformant characters are properly escaped.
    private final StableValue<String> jsonStr = StableValue.of();

    // The String instance returned by `value()`.
    // If created by parsing a JSON document, escaped characters are unescaped.
    // If created via the factory method, the input String is used as-is.
    private final StableValue<String> value = StableValue.of();

    // Called by JsonString.of() factory. The passed String represents the
    // unescaped value.
    public JsonStringImpl(String str) {
        jsonStr.setOrThrow('"' + Utils.escape(value.orElseSet(() -> str)) + '"');
        // unused
        doc = null;
        startOffset = -1;
        endOffset = -1;
        hasEscape = false;
    }

    public JsonStringImpl(char[] doc, int start, int end, boolean escape) {
        this.doc = doc;
        startOffset = start;
        endOffset = end;
        hasEscape = escape;
    }

    @Override
    public String value() {
        return value.orElseSet(this::unescape);
    }

    @Override
    public char[] doc() {
        return doc;
    }

    @Override
    public int offset() {
        return startOffset;
    }

    @Override
    public String toString() {
        return jsonStr.orElseSet(
                () -> new String(doc, startOffset, endOffset - startOffset));
    }

    /*
     * Provides the fully unescaped value with surrounding quotes trimmed.
     * This method fully unescapes 2 char sequences as well as U escape sequences.
     * As a result of un-escaping, the resultant String may not be JSON conformant.
     */
    private String unescape() {
        // If no escapes exist, produce from doc directly without un-escaping
        if (!hasEscape) {
            var ret = toString(); // Also loads JSON String value
            return ret.substring(1, ret.length() - 1);
        }

        // Unescape any escape sequences that exist and build the new String value
        StringBuilder sb = new StringBuilder(endOffset - startOffset - 2);
        var escape = false;
        for (int offset = startOffset + 1; offset < endOffset - 1; offset++) {
            var c = doc[offset];
            if (escape) {
                switch (c) {
                    case '"', '\\', '/' -> {}
                    case 'b' -> c = '\b';
                    case 'f' -> c = '\f';
                    case 'n' -> c = '\n';
                    case 'r' -> c = '\r';
                    case 't' -> c = '\t';
                    case 'u' -> {
                        // Will not throw NFE, document parse already validated input
                        c = (char) Integer.parseInt(new String(doc, offset + 1, 4), 16);
                        offset += 4;
                    }
                }
                escape = false;
            } else if (c == '\\') {
                escape = true;
                continue;
            }
            sb.append(c);
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonString ojs &&
                value().equals(ojs.value());
    }

    @Override
    public int hashCode() {
        return value().hashCode();
    }
}
