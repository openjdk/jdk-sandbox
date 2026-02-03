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

import java.util.Locale;

import java.util.Optional;
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
    private final int decimalOffset;
    private final int exponentOffset;

    private final LazyConstant<String> numString = LazyConstant.of(this::initNumString);
    private final LazyConstant<Optional<Integer>> numInteger = LazyConstant.of(this::initNumInteger);
    private final LazyConstant<Optional<Long>> numLong = LazyConstant.of(this::initNumLong);
    private final LazyConstant<Optional<Double>> numDouble = LazyConstant.of(this::initNumDouble);

    public JsonNumberImpl(char[] doc, int start, int end, int dec, int exp) {
        this.doc = doc;
        startOffset = start;
        endOffset = end;
        decimalOffset = dec;
        exponentOffset = exp;
    }

    @Override
    public int toInt() {
        return numInteger.get().orElseThrow(() ->
            Utils.composeError(this, this + " cannot be represented as an int."));
    }

    @Override
    public long toLong() {
        return numLong.get().orElseThrow(() ->
                Utils.composeError(this, this + " cannot be represented as a long."));
    }

    @Override
    public double toDouble() {
        return numDouble.get().orElseThrow(() ->
                Utils.composeError(this, this + " cannot be represented as a double."));
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
        return numString.get();
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

    // LazyConstants initializers
    private String initNumString() {
        return new String(doc, startOffset, endOffset - startOffset);
    }

    private Optional<Integer> initNumInteger() {
        try {
            return numLong.get().map(Math::toIntExact);
        } catch (ArithmeticException _) {
            return Optional.empty();
        }
    }

    // 4 cases: Fully integral, has decimal, has exponent, has decimal and exponent
    private Optional<Long> initNumLong() {
        try {
            if (decimalOffset == -1 && exponentOffset == -1) {
                // Parseable Long format
                return Optional.of(Long.parseLong(numString.get()));
            } else {
                // Decimal or exponent exists, can't parse w/ Long::parseLong
                if (exponentOffset != -1) {
                    // Exponent exists
                    // Calculate exponent value
                    int exp = Math.abs(Integer.parseInt(new String(doc,
                            exponentOffset + 1, endOffset - exponentOffset - 1), 10));
                    long sig;
                    long scale;
                    if (decimalOffset == -1) {
                        // Exponent with no decimal
                        sig = Long.parseLong(new String(doc, startOffset, exponentOffset - startOffset));
                    } else {
                        // Exponent with decimal
                        for (int i = decimalOffset + exp + 1; i < exponentOffset; i++) {
                            if (doc[i] != '0') {
                                return Optional.empty();
                            }
                        }
                        var shiftedFractionPart = new String(doc, decimalOffset + 1, Math.min(exp, exponentOffset - decimalOffset - 1));
                        exp = exp - shiftedFractionPart.length();
                        sig = Long.parseLong(new String(doc, startOffset, decimalOffset - startOffset) + shiftedFractionPart);
                    }
                    scale = Math.powExact(10L, exp);
                    if (doc[exponentOffset + 1] != '-') {
                        return Optional.of(Math.multiplyExact(sig, scale));
                    } else {
                        if (sig % scale == 0) {
                            return Optional.of(Math.divideExact(sig, scale));
                        } else {
                            return Optional.empty();
                        }
                    }
                } else {
                    // Decimal with no exponent
                    for (int i = decimalOffset + 1; i < endOffset; i++) {
                        if (doc[i] != '0') {
                            return Optional.empty();
                        }
                    }
                    return Optional.of(Long.parseLong(new String(doc,
                            startOffset, decimalOffset - startOffset), 10));
                }
            }
        } catch (NumberFormatException | ArithmeticException _) {}
        return Optional.empty();
    }

    private Optional<Double> initNumDouble() {
        var db = Double.parseDouble(numString.get());
        if (Double.isFinite(db)) {
            return Optional.of(db);
        }
        return Optional.empty();
    }
}
