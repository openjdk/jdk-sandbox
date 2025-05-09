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

import java.util.Objects;
import java.util.function.Supplier;

import jdk.internal.ValueBased;
import jdk.internal.lang.stable.StableSupplier;

/**
 * JsonString implementation class
 */
@ValueBased
final class JsonStringImpl implements JsonString {

    private final char[] doc;
    private final int startOffset;
    private final int endOffset;
    private final Supplier<String> str = StableSupplier.of(this::unescape);

    JsonStringImpl(String str) {
        doc = ("\"" + str + "\"").toCharArray();
        startOffset = 0;
        endOffset = doc.length;
        // Eagerly compute the unescaped JSON string to validate escape sequences
        // On failure, re-throw ISE as IAE, adhering to JsonString.of() contract
        try {
            value();
        } catch (IllegalStateException ise) {
            throw new IllegalArgumentException(ise);
        }
    }

    JsonStringImpl(char[] doc, int start, int end) {
        this.doc = doc;
        startOffset = start;
        endOffset = end;
    }

    @Override
    public String value() {
        var ret = str.get();
        return ret.substring(1, ret.length() - 1);
    }

    @Override
    public String toString() {
        return str.get();
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonString ojs &&
                Objects.equals(value(), ojs.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value());
    }

    String unescape() {
        StringBuilder sb = null; // Only use if required
        var escape = false;
        int offset = startOffset;
        boolean useBldr = false;
        for (; offset < endOffset; offset++) {
            var c = doc[offset];
            if (escape) {
                var length = 0;
                switch (c) {
                    case '"', '\\', '/' -> {}
                    case 'b' -> c = '\b';
                    case 'f' -> c = '\f';
                    case 'n' -> c = '\n';
                    case 'r' -> c = '\r';
                    case 't' -> c = '\t';
                    case 'u' -> {
                        try {
                            c = JsonParser.codeUnit(doc, offset + 1);
                            length = 4;
                        } catch (JsonParseException _) {
                            throw new IllegalStateException("Illegal Unicode escape sequence");
                        }
                    }
                    default -> throw new IllegalStateException("Illegal escape sequence");
                }
                if (!useBldr) {
                    useBldr = true;
                    // At best, we know the size of the first escaped value
                    sb = new StringBuilder(endOffset - startOffset - length - 1)
                            .append(doc, startOffset, offset - 1 - startOffset);
                }
                offset+=length;
                escape = false;
            } else if (c == '\\') {
                escape = true;
                continue;
            }
            if (useBldr) {
                sb.append(c);
            }
        }
        if (useBldr) {
            return sb.toString();
        } else {
            return new String(doc, startOffset, endOffset - startOffset);
        }
    }
}
