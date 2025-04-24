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

import jdk.internal.ValueBased;
import jdk.internal.vm.annotation.Stable;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Locale;

/**
 * JsonNumber implementation class
 */
@ValueBased
final class JsonNumberImpl implements JsonNumber, JsonValueImpl {

    private final JsonDocumentInfo docInfo;
    private final int startOffset;
    private final int endOffset;
    @Stable
    private Number theNumber;
    @Stable
    private String numString;
    @Stable
    private BigDecimal cachedBD;

    JsonNumberImpl(Number num) {
        if (num == null ||
            num instanceof Double d && (d.isNaN() || d.isInfinite())) {
            throw new IllegalArgumentException("Not a valid JSON number");
        }
        theNumber = num;
        numString = num.toString();
        // unused
        startOffset = -1;
        endOffset = -1;
        docInfo = null;
    }

    JsonNumberImpl(JsonDocumentInfo doc, int start, int end) {
        docInfo = doc;
        startOffset = start;
        endOffset = end;
    }

    public Number toNumber() {
        if (theNumber == null) {
            var str = string();

            // Check if integral (Java literal format)
            boolean integerOnly = true;
            for (int index = 0; index < str.length(); index++) {
                char c = str.charAt(index);
                if (c == '.' || c == 'e' || c == 'E') {
                    integerOnly = false;
                    break;
                }
            }
            if (integerOnly) {
                try {
                    theNumber = Long.parseLong(str);
                } catch (NumberFormatException _) {
                    theNumber = new BigInteger(str);
                }
            } else {
                var db = Double.parseDouble(str);
                if (Double.isInfinite(db)) {
                    theNumber = toBigDecimal();
                } else {
                    theNumber = db;
                }
            }
        }
        return theNumber;
    }

    public BigDecimal toBigDecimal() {
        if (cachedBD == null) {
            if (theNumber instanceof BigDecimal bd) {
                cachedBD = bd;
            } else {
                cachedBD = new BigDecimal(string());
            }
        }
        return cachedBD;
    }

    private String string() {
        if (numString == null) {
            // Strip any trailing white space from the number
            var offset = endOffset - 1;
            while (JsonParser.isWhitespace(docInfo, offset)) {
                offset--;
            }
            numString = docInfo.substring(startOffset, offset + 1);
        }
        return numString;
    }

    @Override
    public String toString() {
        return string();
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonNumber ojn &&
                toString().compareToIgnoreCase(ojn.toString()) == 0;
    }

    @Override
    public int hashCode() {
        return toString().toLowerCase(Locale.ROOT).hashCode();
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }
}
