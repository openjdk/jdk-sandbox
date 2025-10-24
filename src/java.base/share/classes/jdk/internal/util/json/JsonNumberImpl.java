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

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

import java.util.json.JsonNumber;
import jdk.internal.ValueBased;

/**
 * JsonNumber implementation class
 */
@ValueBased
public final class JsonNumberImpl implements JsonNumber, JsonValueImpl {

    private final char[] doc;
    private final int startOffset;
    private final int endOffset;
    private final boolean isFp;
    private final StableValue<Number> theNumber = StableValue.of();
    private final StableValue<String> numString = StableValue.of();
    private final StableValue<BigDecimal> cachedBD = StableValue.of();

    public JsonNumberImpl(Number num) {
        // Called by factories. Input is Double, Long, BI, or BD.
        if (num == null ||
            num instanceof Double d && (d.isNaN() || d.isInfinite())) {
            throw new IllegalArgumentException("Not a valid JSON number");
        }
        theNumber.setOrThrow(num);
        numString.setOrThrow(num.toString());
        // unused
        startOffset = -1;
        endOffset = -1;
        isFp = false;
        doc = null;
    }

    public JsonNumberImpl(char[] doc, int start, int end, boolean fp) {
        this.doc = doc;
        startOffset = start;
        endOffset = end;
        isFp = fp;
    }

    @Override
    public Number number() {
        return theNumber.orElseSet(() -> {
            var str = toString();
            if (isFp) {
                var db = Double.parseDouble(str);
                if (Double.isInfinite(db)) {
                    return toBigDecimal();
                } else {
                    return db;
                }
            } else {
                try {
                    return Long.parseLong(str);
                } catch (NumberFormatException _) {
                    return new BigInteger(str);
                }
            }
        });
    }

    @Override
    public BigDecimal toBigDecimal() {
        return cachedBD.orElseSet(() -> {
            // If we already computed theNumber, check if it's BD
            if (theNumber.orElse(null) instanceof BigDecimal bd) {
                return bd;
            } else {
                return new BigDecimal(toString());
            }
        });
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
        return numString.orElseSet(
                () -> new String(doc, startOffset, endOffset - startOffset));
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof JsonNumber ojn &&
                toString().compareToIgnoreCase(ojn.toString()) == 0;
    }

    @Override
    public int hashCode() {
        return toString().toLowerCase(Locale.ROOT).hashCode();
    }
}
