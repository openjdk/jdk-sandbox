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
    private final int startOffset, endOffset;
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

    JsonNumberImpl(JsonDocumentInfo docInfo, int offset, int index) {
        this.docInfo = docInfo;
        startOffset = offset;
        endIndex = docInfo.nextIndex(index);
        endOffset = endIndex != -1 ? docInfo.getOffset(endIndex) : docInfo.getEndOffset();
    }

    @Override
    public Number value() {
        if (theNumber == null) {
            theNumber = parseNumber();
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

    private Number parseNumber() {
        // check syntax
        boolean sawDecimal = false;
        boolean sawExponent = false;
        boolean sawZero = false;
        boolean sawWhitespace = false;
        boolean havePart = false;

        int start = JsonParser.skipWhitespaces(docInfo, startOffset);
        int offset = start;
        for (; offset < endOffset && !sawWhitespace; offset++) {
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
                default -> throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Number not recognized.", offset), offset);
            }
        }

        if (!JsonParser.checkWhitespaces(docInfo, offset, endOffset)) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Garbage after the number.", offset), offset);
        }
        if (!havePart) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Dangling decimal point or exponent symbol.", offset), offset);
        }

        numString = docInfo.substring(start, offset);
        if (sawDecimal || sawExponent) {
            var num = Double.parseDouble(numString);

            if (num == Double.POSITIVE_INFINITY ||
                    num == Double.NEGATIVE_INFINITY) {
                throw new JsonParseException(docInfo.composeParseExceptionMessage(
                        "Number too large or small.", offset), offset);
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
    public Number toUntyped() {
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
            return numString;
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
