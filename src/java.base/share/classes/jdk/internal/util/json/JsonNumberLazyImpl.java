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

/**
 * JsonNumber lazy implementation subclass
 */
final class JsonNumberLazyImpl extends JsonNumberImpl implements JsonValueLazyImpl {

    private final int endIndex;

    JsonNumberLazyImpl(JsonLazyDocumentInfo docInfo, int offset, int index) {
        this.docInfo = docInfo;
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

    // Lazy already knows the start and end offset, simply create/validate the value
    @Override
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

        JsonParser.failIfWhitespaces(docInfo, offset, endOffset, "Garbage after the number.");

        if (!havePart) {
            throw new JsonParseException(docInfo.composeParseExceptionMessage(
                    "Dangling decimal point or exponent symbol.", offset), offset);
        }

        numString = docInfo.substring(start, offset);
        return numToString(numString, sawDecimal || sawExponent, offset);
    }

    @Override
    public int getEndIndex() {
        return endIndex;
    }

    @Override
    public String formatCompact() {
        value(); // ensure "numString" is set
        return numString;
    }
}
