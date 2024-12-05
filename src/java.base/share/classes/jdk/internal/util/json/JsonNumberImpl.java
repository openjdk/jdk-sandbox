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

import java.util.Objects;

/**
 * JsonNumber implementation class
 */
final class JsonNumberImpl implements JsonNumber, JsonValueImpl {

    private final JsonDocumentInfo docInfo;
    private final int startOffset;
    private final int endOffset;
    private final int endIndex;
    private Number theNumber;
    private String numString;

    JsonNumberImpl(Number num) {
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

    @Override
    public Number value() {
        if (theNumber == null) {
            theNumber = parseNumber(endOffset);
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

    Number parseNumber(int endOffset) {
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
                        throw new JsonParseException(docInfo,
                                "Minus sign in the middle.", offset);
                    }
                }
                case '+' -> {
                    if (!sawExponent || havePart) {
                        throw new JsonParseException(docInfo,
                                "Plus sign appears in a wrong place.", offset);
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
                        throw new JsonParseException(docInfo,
                                "Zero not allowed here.", offset);
                    }
                    havePart = true;
                }
                case '.' -> {
                    if (sawDecimal) {
                        throw new JsonParseException(docInfo,
                                "More than one decimal point.", offset);
                    } else {
                        if (!havePart) {
                            throw new JsonParseException(docInfo,
                                    "No integer part.", offset);
                        }
                        sawDecimal = true;
                        havePart = false;
                    }
                }
                case 'e', 'E' -> {
                    if (sawExponent) {
                        throw new JsonParseException(docInfo,
                                "More than one exponent symbol.", offset);
                    } else {
                        if (!havePart) {
                            throw new JsonParseException(docInfo,
                                    "No integer or fraction part.", offset);
                        }
                        sawExponent = true;
                        havePart = false;
                    }
                }
                case ' ', '\t', '\r', '\n' -> {
                    sawWhitespace = true;
                    offset --;
                }
                default -> throw new JsonParseException(docInfo,
                        "Number not recognized.", offset);
            }
        }

        if (!JsonParser.checkWhitespaces(docInfo, offset, endOffset)) {
            throw new JsonParseException(docInfo,
                    "Garbage after the number.", offset);
        }


        if (!havePart) {
            throw new JsonParseException(docInfo,
                    "Dangling decimal point or exponent symbol.", offset);
        }

        numString = docInfo.substring(start, offset);
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
            throw new JsonParseException(docInfo,
                    "Number cannot be infinite.", offset);
        }
        return num;
    }

    Number toUntyped() {
        return value();
    }

    @Override
    public String toString() {
        value(); // ensure "numString" is set
        return numString;
    }

    @Override
    public String toDisplayString() {
        return toDisplayString(0, false);
    }

    @Override
    public String toDisplayString(int indent, boolean isField) {
        return " ".repeat(isField ? 1 : indent) + toString();
    }
}
