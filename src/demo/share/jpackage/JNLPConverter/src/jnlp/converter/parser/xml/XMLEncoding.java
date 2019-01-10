/*
 * Copyright (c) 2006, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jnlp.converter.parser.xml;

import java.io.ByteArrayInputStream;
import java.io.EOFException;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.Reader;
import java.io.UnsupportedEncodingException;

public class XMLEncoding {
    /**
     * Decodes a byte stream into a String by testing for a Byte Order Mark
     * (BOM) or an XML declaration.
     * <br />
     * Detection begins by examining the first four octets of the stream for a
     * BOM. If a BOM is not found, then an encoding declaration is looked for
     * at the beginning of the stream. If the encoding still can not be
     * determined at this point, then UTF-8 is assumed.
     *
     * @param data  an array of bytes containing an encoded XML document.
     *
     * @return A string containing the decoded XML document.
     */
    public static String decodeXML(byte [] data) throws IOException {
        int start = 0;
        String encoding;

        if (data.length < BOM_LENGTH) {
            throw (new EOFException("encoding.error.not.xml"));
        }
        // no else required; successfully read stream
        int firstFour = ((0xff000000 & ((int) data[0] << 24)) |
                         (0x00ff0000 & ((int) data[1] << 16)) |
                         (0x0000ff00 & ((int) data[2] <<  8)) |
                         (0x000000ff &  (int) data[3]));

        // start by examining the first four bytes for a BOM
        switch (firstFour) {
            case EBCDIC:
                // examine the encoding declaration
                encoding = examineEncodingDeclaration(data, IBM037_ENC);
                break;

            case XML_DECLARATION:
                // assume UTF-8, but examine the encoding declaration
                encoding = examineEncodingDeclaration(data, UTF_8_ENC);
                break;

            case UTF_16BE:
                encoding = UTF_16BE_ENC;
                break;

            case UTF_16LE:
                encoding = UTF_16LE_ENC;
                break;

            case UNUSUAL_OCTET_1:
            case UNUSUAL_OCTET_2:
                throw (new UnsupportedEncodingException("encoding.error.unusual.octet"));

            case UTF_32_BE_BOM:
            case UTF_32_LE_BOM:
                encoding = UTF_32_ENC;
                break;

            default:
                int firstThree = firstFour & 0xffffff00;

                switch (firstThree) {
                    case UTF_8_BOM:
                        // the InputStreamReader class doen't properly handle
                        // the Byte Order Mark (BOM) in UTF-8 streams, so don't
                        // putback those 3 bytes.
                        start    = 3;
                        encoding = UTF_8_ENC;
                        break;

                    default:
                        int firstTwo = firstFour & 0xffff0000;

                        switch (firstTwo) {
                            case UTF_16_BE_BOM:
                            case UTF_16_LE_BOM:
                                encoding = UTF_16_ENC;
                                break;

                            default:
                                // this is probably UTF-8 without the encoding
                                // declaration
                                encoding = UTF_8_ENC;
                                break;
                        }
                        break;
                }
                break;
        }

        return (new String(data, start, data.length - start, encoding));
    }

    /**
     * [3]  S            ::= ( #x20 | #x09 | #x0d | #x0a )
     * [23] XMLDecl      ::= '<?xml' VersionInfo EncodingDecl? SDDecl? S? '?>'
     * [24] VersionInfo  ::= S 'version' Eq ( '"' VersionNum '"' |
     *                                        "'" VersionNum "'" )
     * [25] Eq           ::= S? '=' S?
     * [26] VersionNum   ::= ([a-zA-Z0-9_.:] | '-')+
     * [80] EncodingDecl ::= S 'encoding' Eq ( '"' EncName '"' |
     *                                         "'" EncName "'" )
     * [81] EncName      ::= [a-zA-Z] ([a-zA-Z0-9_.] | '-')*
     */
    private static String examineEncodingDeclaration(byte [] data,
                          String    encoding) throws IOException {
        boolean loop       = false;
        boolean recognized = false;
        boolean almost     = false;
        boolean question   = false;
        boolean done       = false;
        boolean found      = false;
        int     pos        = 0;
        int     ch         = -1;
        Reader  reader     = null;
        String  result     = ((encoding != null) ? encoding : UTF_8_ENC);

        reader = new InputStreamReader(new ByteArrayInputStream(data), result);
        ch     = reader.read();

        // if this is an XML declaration, it will start with the text '<?xml'
        for (int i = 0; ((i < XML_DECL_START.length()) && (done == false)); i++) {
            if (ch != XML_DECL_START.charAt(i)) {
                // This doesn't look like an XML declaration.  This method
                // should only be called if the stream contains an XML
                // declaration in the encoding that is passed into the method.
                done = true;
                break;
            }
            // no else required; still matches
            ch = reader.read();
        }

        // there must be at least one whitespace character next.
        loop = true;
        while ((loop == true) && (done == false)) {
            switch (ch) {
                case SPACE:
                case TAB:         // intentional
                case LINEFEED:    // fall
                case RETURN:      // through
                    ch = reader.read();
                    break;

                case -1:
                    // unexpected EOF
                    done = true;
                    break;

                default:
                    // non-whitespace
                    loop = false;
                    break;
            }
        }

        // now look for the text 'encoding', but if the end of the XML
        // declaration (signified by the text '?>') comes first, then
        // assume the encoding is UTF-8
        loop = true;
        while ((loop == true) && (done == false)) {
            if (ch == -1) {
                // unexpected EOF
                done = true;
                break;
            } else if (recognized == true) {
                // this is the encoding declaration as long as the next few
                // characters are whitespace and/or the equals ('=') sign
                switch (ch) {
                    case SPACE:       // intentional
                    case TAB:         // fall
                    case LINEFEED:    // through
                    case RETURN:
                        // don't need to do anything
                        break;

                    case EQUAL:
                        if (almost == false) {
                            // got the equal, now find a quote
                            almost = true;
                        } else {
                            // this is not valid XML, so punt
                            recognized = false;
                            done       = true;
                        }
                        break;

                    case DOUBLE_QUOTE:    // intentional
                    case SINGLE_QUOTE:    // fall through
                        if (almost == true) {
                            // got the quote, so move on to get the value
                            loop = false;
                        } else {
                            // got a quote before the equal; this is not valid
                            // XML, so punt
                            recognized = false;
                            done       = true;
                        }
                        break;

                    default:
                        // non-whitespace
                        recognized = false;
                        if (almost == true) {
                            // this is not valid XML, so punt
                            done = true;
                        }
                        // no else required; this wasn't the encoding
                        // declaration
                        break;
                }

                if (recognized == false) {
                    // this isn't the encoding declaration, so go back to the
                    // top without reading the next character
                    pos = 0;
                    continue;
                }
                // no else required; still looking good
            } else if (ch == ENCODING_DECL.charAt(pos++)) {
                if (ENCODING_DECL.length() == pos) {
                    // this looks like the encoding declaration
                    recognized = true;
                }
                // no else required; this might be the encoding declaration
            } else if (ch == '?') {
                question = true;
                pos      = 0;
            } else if ((ch == '>') && (question == true)) {
                // there is no encoding declaration, so assume that the initial
                // encoding guess was correct
                done   = true;
                continue;
            } else {
                // still searching for the encoding declaration
                pos = 0;
            }

            ch = reader.read();
        }

        if (done == false) {
            StringBuilder buffer = new StringBuilder(MAX_ENC_NAME);

            if (((ch >= 'a') && (ch <= 'z')) |
                ((ch >= 'A') && (ch <= 'Z'))) {
                // add the character to the result
                buffer.append((char) ch);

                loop = true;
                while ((loop == true) && (done == false)) {
                    ch = reader.read();

                    if (((ch >= 'a') && (ch <= 'z')) ||
                        ((ch >= 'A') && (ch <= 'Z')) ||
                        ((ch >= '0') && (ch <= '9')) ||
                        (ch == '_') || (ch == '.') || (ch == '-')) {
                        // add the character to the result
                        buffer.append((char) ch);
                    } else if ((ch == DOUBLE_QUOTE) || (ch == SINGLE_QUOTE)) {
                        // finished!
                        found  = true;
                        done   = true;
                        result = buffer.toString();
                    } else {
                        // this is not a valid encoding name, so punt
                        done = true;
                    }
                }
            } else {
                // this is not a valid encoding name, so punt
                done = true;
            }
        }
        // no else required; already failed to find the encoding somewhere else

        return (result);
    }

    private static final int BOM_LENGTH   = 4;
    private static final int MAX_ENC_NAME = 512;

    private static final int SPACE        = 0x00000020;
    private static final int TAB          = 0x00000009;
    private static final int LINEFEED     = 0x0000000a;
    private static final int RETURN       = 0x0000000d;
    private static final int EQUAL        = '=';
    private static final int DOUBLE_QUOTE = '\"';
    private static final int SINGLE_QUOTE = '\'';

    private static final int UTF_32_BE_BOM   = 0x0000feff;
    private static final int UTF_32_LE_BOM   = 0xfffe0000;
    private static final int UTF_16_BE_BOM   = 0xfeff0000;
    private static final int UTF_16_LE_BOM   = 0xfffe0000;
    private static final int UTF_8_BOM       = 0xefbbbf00;
    private static final int UNUSUAL_OCTET_1 = 0x00003c00;
    private static final int UNUSUAL_OCTET_2 = 0x003c0000;
    private static final int UTF_16BE        = 0x003c003f;
    private static final int UTF_16LE        = 0x3c003f00;
    private static final int EBCDIC          = 0x4c6fa794;
    private static final int XML_DECLARATION = 0x3c3f786d;

    private static final String UTF_32_ENC   = "UTF-32";
    private static final String UTF_16_ENC   = "UTF-16";
    private static final String UTF_16BE_ENC = "UTF-16BE";
    private static final String UTF_16LE_ENC = "UTF-16LE";
    private static final String UTF_8_ENC    = "UTF-8";
    private static final String IBM037_ENC   = "IBM037";

    private static final String XML_DECL_START = "<?xml";
    private static final String ENCODING_DECL  = "encoding";
}
