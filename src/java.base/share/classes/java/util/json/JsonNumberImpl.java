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

import java.math.BigDecimal;

/**
 * JsonNumber implementation class
 */
final class JsonNumberImpl implements JsonNumber, JsonValueImpl {
    private static final BigDecimal MIN_LONG = BigDecimal.valueOf(Long.MIN_VALUE);
    private static final BigDecimal MAX_LONG = BigDecimal.valueOf(Long.MAX_VALUE);
    private static final long MIN_POW_2_53 = -9_007_199_254_740_991L;
    private static final long MAX_POW_2_53 = 9_007_199_254_740_991L;

    private final JsonDocumentInfo docInfo;
    private final int startOffset;
    private final int endOffset;
    private final int endIndex;
    private Number theNumber;
    private String numString;

    JsonNumberImpl(Number num) {
        if (num == null ||
            num instanceof Double d && (d.isNaN() || d.isInfinite())) {
            throw new IllegalArgumentException("Not a valid JSON number");
        }
        theNumber = num;
        numString = num.toString();
        startOffset = 0;
        endOffset = 0;
        endIndex = 0;
        docInfo = null;
    }

    JsonNumberImpl(JsonDocumentInfo doc, int offset, int index) {
        docInfo = doc;
        startOffset = offset;
        endIndex = docInfo.nextIndex(index);
        endOffset = endIndex != -1 ? docInfo.getOffset(endIndex) : docInfo.getEndOffset();
    }

    public Number toNumber() {
        if (theNumber == null) {
            boolean integral = true;
            var str = string();
            // Fast path for longs
            for (int index = 0; index < str.length(); index++) {
                char c = str.charAt(index);
                if (c == '.' || c == 'e' || c == 'E') {
                    integral = false;
                    break;
                }
            }
            if (integral) {
                try {
                    theNumber = Long.parseLong(str);
                    return theNumber;
                } catch (NumberFormatException _) {}
            }

            // Fast path for doubles
            // False negatives go down slow-path. E.g. 4.999999999...
            // Can't false positive b/c whole numbers are exactly representable
            // within +/- Math.pow(2,53) range
            var db = Double.parseDouble(str);
            if (db >= MIN_POW_2_53 && db <= MAX_POW_2_53 && db % 1L != 0) {
                theNumber = db;
                return theNumber;
            }

            // Slow path
            var bd = new BigDecimal(str); // Can throw NFE
            if (bd.scale() <= 0 || bd.stripTrailingZeros().scale() <= 0) {
                // integrals
                if (bd.compareTo(MIN_LONG) >= 0 &&
                    bd.compareTo(MAX_LONG) <= 0) {
                    theNumber = bd.longValueExact();
                } else {
                    theNumber = bd.toBigIntegerExact();
                }
            } else {
                // fractions
                if (Double.isInfinite(db)) {
                    theNumber = bd;
                } else {
                    theNumber = db;
                }
            }
        }
        return theNumber;
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
    public int getEndIndex() {
        return endIndex;
    }

    @Override
    public Number toUntyped() {
        return toNumber();
    }

    @Override
    public String toString() {
        return string();
    }
}
