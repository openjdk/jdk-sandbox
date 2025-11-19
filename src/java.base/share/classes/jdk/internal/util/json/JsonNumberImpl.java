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

import java.util.json.JsonAssertionException;
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
    private /* final StableValue<*/String numString;
    private boolean longInit;
    private /* final StableValue<*/Long cachedLong;
    private boolean doubleInit;
    private /* final StableValue<*/Double cachedDouble;
    private boolean biInit;
    private /*final StableValue<*/BigInteger cachedBI;
    private boolean bdInit;
    private /*final StableValue<*/BigDecimal cachedBD;

    public JsonNumberImpl(Number num) {
        // Called by factories. Input is Double, Long, BI, or BD.
        switch (num) {
            case Long l -> cachedLong = l;
            case Double d -> {
                if (d.isNaN() || d.isInfinite()) {
                    throw new IllegalArgumentException("Not a valid JSON number");
                }
                cachedDouble =  d;
            }
            case BigInteger bi -> cachedBI = bi;
            case BigDecimal bd -> cachedBD = bd;
            case null -> throw new IllegalArgumentException("Not a valid JSON number");
            default -> throw new InternalError("should not happen");
        }
        numString = num.toString();
        // unused
        startOffset = -1;
        endOffset = -1;
        isFp = false;
        doc = null;
    }

    public JsonNumberImpl(char[] doc, int start, int end, boolean fp) {
        this.doc = doc;
        numString = new String(doc, start, end - start);
        startOffset = start;
        endOffset = end;
        isFp = fp;
    }

    @Override
    public JsonNumber number() {
        return this;
    }

    @Override
    public boolean isLong() {
        // refactor with LazyConstant
        if (!longInit) {
            longInit = true;
            try {
                cachedLong = Long.parseLong(toString());
            } catch (NumberFormatException _) {
                return false;
            }
        }
        return cachedLong != null;
    }

    @Override
    public long asLong() {
        if (isLong()) {
            return cachedLong;
        } else {
            throw new JsonAssertionException("not a long");
        }
    }

    @Override
    public boolean isDouble() {
        // refactor with LazyConstant
        if (!doubleInit) {
            doubleInit = true;
            try {
                var db = Double.parseDouble(toString());
                if (!Double.isInfinite(db)) {
                    cachedDouble = db;
                }
            } catch (NumberFormatException _) {
                return false;
            }
        }
        return cachedDouble != null;
    }

    @Override
    public double asDouble() {
        if (isDouble()) {
            return cachedDouble;
        } else {
            throw new JsonAssertionException("not a double");
        }
    }

    @Override
    public boolean isBigInteger() {
        // refactor with LazyConstant
        if (!biInit) {
            biInit = true;
            try {
                cachedBI = new BigInteger(toString());
            } catch (NumberFormatException _) {
                return false;
            }
        }
        return cachedBI != null;
    }

    @Override
    public BigInteger asBigInteger() {
        if (isBigInteger()) {
            return cachedBI;
        } else {
            throw new JsonAssertionException("not a BigInteger");
        }
    }

    @Override
    public boolean isBigDecimal() {
        // refactor with LazyConstant
        if (!bdInit) {
            bdInit = true;
            try {
                cachedBD = new BigDecimal(toString());
            } catch (NumberFormatException _) {
                return false;
            }
        }
        return cachedBD != null;
    }

    @Override
    public BigDecimal asBigDecimal() {
        if (isBigDecimal()) {
            return cachedBD;
        } else {
            throw new JsonAssertionException("not a BigDecimal");
        }
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
        return numString;
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
