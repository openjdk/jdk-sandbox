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
final class JsonNumberImpl implements JsonNumber, JsonValueImpl {
    private final JsonDocumentInfo docInfo;
    private final int startOffset;
    private int endOffset;
    private final int endIndex;
    private Number theNumber;
    private String numString;

    JsonNumberImpl(Number num) {
        docInfo = null;
        startOffset = 0;
        endOffset = 0;
        endIndex = 0;
        theNumber = num;
    }

    JsonNumberImpl(JsonDocumentInfo docInfo, int offset) {
        this.docInfo = docInfo;
        startOffset = offset;
        theNumber = parseNumber(); // Sets "endOffset"
        endIndex = 0;
    }

    JsonNumberImpl(JsonLazyDocumentInfo docInfo, int offset, int index) {
        this.docInfo = docInfo;
        startOffset = offset;
        endIndex = docInfo.nextIndex(index);
        endOffset = endIndex != -1 ? docInfo.getOffset(endIndex) : docInfo.getEndOffset();
    }

    @Override
    public Number value() {
        if (theNumber == null) {
            theNumber = createNumber(endOffset);
        }
        return theNumber;
    }

    @Override
    public int getEndOffset() {
        return endOffset;
    }

    @Override
    public int getEndIndex() {
        return endIndex;
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

    // Parse a number. This should only end on a non-numerical value or
    // the end of the docInfo, as there is no known end offset yet
    public Number parseNumber() {
        return createNumber(docInfo.getEndOffset());
    }

    // Create a number from the startOffset to the desired end offset
    // This method also sets "numString"
    public Number createNumber(int endOffset) {
        boolean sawDecimal = false;
        boolean sawExponent = false;
        boolean sawZero = false;
        boolean sawWhitespace = false;
        boolean havePart = false;
        boolean sawInvalid = false;
        boolean lazy = docInfo instanceof JsonLazyDocumentInfo;
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
                    if (lazy) {
                        throw new JsonParseException(docInfo.composeParseExceptionMessage(
                                "Number not recognized.", offset), offset);
                    } else {
                        offset--;
                        sawInvalid = true;
                    }
                }
            }
        }

        if (lazy) {
            JsonParser.failIfWhitespaces(docInfo, offset, endOffset, "Garbage after the number.");
        }

        if (!havePart) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Dangling decimal point or exponent symbol.", offset), offset);
        }

        numString = docInfo.substring(start, offset);
        if (!lazy) {
            this.endOffset = this.startOffset + (offset - start);
        }

        if (sawDecimal || sawExponent) {
            var num = Double.parseDouble(numString);
            if (Double.isInfinite(num)) { // don't need to check NaN, parsing forbids non int start
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Number cannot be infinite.", offset), offset);
            }
            return num;
        } else {
            // integral numbers
            try {
                return Integer.parseInt(numString);
            } catch (NumberFormatException _) {
                // int overflow. try long
                try {
                    return Long.parseLong(numString);
                } catch (NumberFormatException _) {
                    // long overflow. convert to BigInteger
                    return new BigInteger(numString);
                }
            }
        }
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
        if (numString != null) {
            return numString;
        } else if (theNumber != null) {
            return theNumber.toString(); // use theNumber if we have it
        } else {
            value(); // make sure parse is done
            return theNumber.toString();
        }
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
