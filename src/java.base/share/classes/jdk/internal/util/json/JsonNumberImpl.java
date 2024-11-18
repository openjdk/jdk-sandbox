/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.util.Objects;

/**
 * JsonNumber implementation class
 */
sealed class JsonNumberImpl implements JsonNumber, JsonValueImpl permits JsonNumberLazyImpl {

    JsonDocumentInfo docInfo;
    int startOffset;
    int endOffset;
    Number theNumber;
    String numString;

    // For use by subclasses
    JsonNumberImpl() {}

    JsonNumberImpl(Number num) {
        docInfo = null;
        startOffset = 0;
        endOffset = 0;
        theNumber = num;
        numString = num.toString();
    }

    JsonNumberImpl(JsonDocumentInfo docInfo, int offset) {
        this.docInfo = docInfo;
        startOffset = offset;
        theNumber = parseNumber(docInfo.getEndOffset()); // Sets "endOffset", "numString"
    }

    @Override
    public Number value() {
        return theNumber;
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public boolean equals(Object o) {
        return this == o ||
            o instanceof JsonNumberImpl ojni &&
            Objects.equals(toString(), ojni.toString());
    }

    @Override
    public int hashCode() {
        return Objects.hash(toString());
    }

    // Eager must parse until non-numerical, since the end offset is not known
    // However, it should not fail, since the non-numerical can be a ',', ']', '}'
    Number parseNumber(int endOffset) {
        boolean sawDecimal = false;
        boolean sawExponent = false;
        boolean sawZero = false;
        boolean sawWhitespace = false;
        boolean havePart = false;
        boolean sawInvalid = false;
        int start = JsonParser.skipWhitespaces(docInfo, startOffset);
        int offset = start;

        for (; offset < endOffset && !sawWhitespace && !sawInvalid; offset++) {
            switch (docInfo.charAt(offset)) {
                case '-' -> {
                    if (offset != start && !sawExponent) {
                        throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                "Minus sign in the middle.", offset), offset);
                    }
                }
                case '+' -> {
                    if (!sawExponent || havePart) {
                        throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                "Plus sign appears in a wrong place.", offset), offset);
                    }
                }
                case '0' -> {
                    if (!havePart) {
                        sawZero = true;
                    }
                    havePart = true;
                }
                case '1', '2', '3', '4', '5', '6', '7', '8', '9' -> {
                    if (!sawDecimal && !sawExponent && sawZero) {
                        throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                "Zero not allowed here.", offset), offset);
                    }
                    havePart = true;
                }
                case '.' -> {
                    if (sawDecimal) {
                        throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                "More than one decimal point.", offset), offset);
                    } else {
                        if (!havePart) {
                            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                    "No integer part.", offset), offset);
                        }
                        sawDecimal = true;
                        havePart = false;
                    }
                }
                case 'e', 'E' -> {
                    if (sawExponent) {
                        throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                "More than one exponent symbol.", offset), offset);
                    } else {
                        if (!havePart) {
                            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                    "No integer or fraction part.", offset), offset);
                        }
                        sawExponent = true;
                        havePart = false;
                    }
                }
                case ' ', '\t', '\r', '\n' -> {
                    sawWhitespace = true;
                    offset --;
                }
                default -> {
                    offset--;
                    sawInvalid = true;
                }
            }
        }

        if (!havePart) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Dangling decimal point or exponent symbol.", offset), offset);
        }
        numString = docInfo.substring(start, offset);
        this.endOffset = this.startOffset + (offset - start);
        return numToString(numString, sawDecimal || sawExponent, offset);
    }

    Number numToString(String numStr, boolean fp, int offset) {
        if (!fp) {
            // integral numbers
            try {
                return Integer.parseInt(numStr);
            } catch (NumberFormatException _) {
                // int overflow. try long
                try {
                    return Long.parseLong(numStr);
                } catch (NumberFormatException _) {
                    // long overflow. convert to Double
                }
            }
        }

        var num = Double.parseDouble(numStr);
        if (Double.isInfinite(num)) { // don't need to check NaN, parsing forbids non int start
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Number cannot be infinite.", offset), offset);
        }
        return num;
    }

    @Override
    public Number to() {
        return value();
    }

    @Override
    public String toString() {
        return formatCompact();
    }

    @Override
    public String formatCompact() {
        return numString;
    }

    @Override
    public String formatReadable() {
        return formatReadable(0, false);
    }

    @Override
    public String formatReadable(int indent, boolean isField) {
        return " ".repeat(isField ? 1 : indent) + toString();
    }
}
