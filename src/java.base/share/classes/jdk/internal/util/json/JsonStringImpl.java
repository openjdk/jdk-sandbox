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

import java.util.function.Supplier;
import java.util.json.JsonString;

import jdk.internal.ValueBased;
import jdk.internal.lang.stable.StableSupplier;

/**
 * JsonString implementation class
 */
@ValueBased
public final class JsonStringImpl implements JsonString {

    private final char[] doc;
    private final int startOffset;
    private final int endOffset;
    private final Supplier<String> source = StableSupplier.of(this::source);
    private final Supplier<String> unescaped = StableSupplier.of(this::unescape);

    public JsonStringImpl(String str) {
        doc = ("\"" + str + "\"").toCharArray();
        startOffset = 0;
        endOffset = doc.length;
        source.get(); // Validates the input String as proper JSON
    }

    public JsonStringImpl(char[] doc, int start, int end) {
        this.doc = doc;
        startOffset = start;
        endOffset = end;
    }

    @Override
    public String value() {
        return unescaped.get();
    }

    @Override
    public String toString() {
        return source.get();
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

    // Provides the fully unescaped value with surrounding quotes trimmed.
    // This method fully unescapes 2 char sequences as well as U escape sequences.
    // As a result of un-escaping, the resultant String may not be JSON conformant.
    private String unescape() {
        StringBuilder sb = null; // Only use if required
        var escape = false;
        int start = startOffset + 1;
        int end = endOffset - 1;
        boolean useBldr = false;
        for (int offset = start; offset < end; offset++) {
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
                        if (offset + 4 < endOffset) {
                            c = Utils.codeUnit(doc, offset + 1);
                            length = 4;
                        }
                    }
                }
                if (!useBldr) {
                    useBldr = true;
                    // At best, we know the size of the first escaped value
                    sb = new StringBuilder(end - start - length - 1)
                            .append(doc, start, offset - 1 - start);
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
            return new String(doc, start, end - start);
        }
    }

    private String source() {
        // getSource throws on quotes, so bypass and re-insert
        return '"' + Utils.getCompliantString(doc, startOffset + 1, endOffset - 1) + '"';
    }
}
