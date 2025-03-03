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
import java.math.BigInteger;

/**
 * JsonNumber implementation class
 */
final class JsonNumberImpl implements JsonNumber, JsonValueImpl {

    private final JsonDocumentInfo docInfo;
    private final int startOffset;
    private final int endOffset;
    private final int endIndex;
    private BigDecimal theNumber;
    private String numString;

    JsonNumberImpl(Number num) {
        theNumber = switch (num) {
            case Long l -> BigDecimal.valueOf(l);
            case Double d -> BigDecimal.valueOf(d);
            case BigInteger bi -> new BigDecimal(bi);
            case BigDecimal bd -> bd;
            default -> new BigDecimal(num.toString());
        };
        numString = theNumber.toString();
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

    @Override
    public BigDecimal toBigDecimal() {
        if (theNumber == null) {
            theNumber = new BigDecimal(string());
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
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonNumberImpl ojni &&
            toBigDecimal().compareTo(ojni.toBigDecimal()) == 0;
    }

    @Override
    public int hashCode() {
        return toBigDecimal().stripTrailingZeros().hashCode();
    }

    @Override
    public Number toUntyped() {
        return toBigDecimal();
    }

    @Override
    public String toString() {
        return string();
    }
}
