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
    private final LazyConstant<String> numString = LazyConstant.of(this::initNumString);
    private final LazyConstant<Optional<Long>> cachedLong = LazyConstant.of(this::cachedLongInit);
    private final LazyConstant<Optional<Double>> cachedDouble = LazyConstant.of(this::cachedDoubleInit);

    public JsonNumberImpl(char[] doc, int start, int end, boolean fp) {
        this.doc = doc;
        startOffset = start;
        endOffset = end;
        isFp = fp;
    }

    @Override
    public JsonNumber number() {
        return this;
    }

    @Override
    public long toLong() {
        return cachedLong.get().orElseThrow(() ->
                new JsonAssertionException(this + " cannot be represented as a long"));
    }

    @Override
    public double toDouble() {
        return cachedDouble.get().orElseThrow(() ->
                new JsonAssertionException(this + " cannot be represented as a double"));
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

    private Optional<Long> cachedLongInit() {
        if (!isFp) {
            try {
                return Optional.of(Long.parseLong(numString.get()));
            } catch (NumberFormatException _) {}
        } else {
            if (Double.parseDouble(numString.get()) instanceof long l) {
                return Optional.of(l);
            }
        }
        return Optional.empty();
    }

    private Optional<Double> cachedDoubleInit() {
        if (isFp) {
            var db = Double.parseDouble(numString.get());
            if (Double.isFinite(db)) {
                return Optional.of(db);
            }
        } else {
            try {
                if (Long.parseLong(numString.get()) instanceof double d) {
                    return Optional.of(d);
                }
            } catch (NumberFormatException _) {}
        }
        return Optional.empty();
    }
}
