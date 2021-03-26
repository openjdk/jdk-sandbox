/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.parser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

//
//        markup-line = { markup-instruction }
// markup-instruction = "@" , instruction-name , [region-identifier] , {attribute} [":"] ;
//
// If optional trailing ":" is present, the instructions refer to the next line
// rather than to this line.
//
public final class InstructionParser {

    private final static int EOI = 0x1A;
    private char[] buf;
    private int bp;
    private int buflen;
    private char ch;

    public List<Parser.Instruction> parse(String input) throws ParseException {
        buf = new char[input.length() + 1];
        input.getChars(0, input.length(), buf, 0);
        buf[buf.length - 1] = EOI;
        buflen = buf.length - 1;
        bp = -1;

        nextChar();
        return parse();
    }

    protected List<Parser.Instruction> parse() throws ParseException {
        List<Parser.Instruction> instructions = new ArrayList<>();
        // TODO: what to do with leading and trailing unrecognized markup?
        while (bp < buflen) {
            switch (ch) {
                case '@' -> instructions.add(readInstruction());
                default -> nextChar();
            }
        }

        return instructions;
    }

    protected Parser.Instruction readInstruction() throws ParseException {
        nextChar();
        if (!Character.isUnicodeIdentifierStart(ch)) {
            throw new ParseException("Bad character: '%s' (0x%s)".formatted(ch, Integer.toString(ch, 16)));
        }
        String name = readIdentifier();
        skipWhitespace();

        boolean appliesToNextLine = false;
        String id = "";
        Map<String, String> attributes = Map.of();

        if (ch == ':') {
            appliesToNextLine = true;
            nextChar();
        } else {
            // look ahead to disambiguate between a region identifier
            // and an attribute's name
            int x = bp;
            char c = ch;

            while (x < buflen && (Character.isUnicodeIdentifierPart(c) || c == '-')) {
                c = buf[x < buflen ? ++x : buflen];
            }

            while (x < buflen && Character.isWhitespace(c)) {
                c = buf[x < buflen ? ++x : buflen];
            }

            if (c != '=') {
                id = readIdentifier();
                nextChar();
            }

            attributes = tagAttrs();

            skipWhitespace();
            if (ch == ':') {
                appliesToNextLine = true;
                nextChar();
            }
        }

        Parser.Instruction i = new Parser.Instruction();
        i.name = name;
        i.regionIdentifier = id;
        i.attributes = attributes;
        i.appliesToNextLine = appliesToNextLine;

        return i;
    }

    protected String readIdentifier() {
        int start = bp;
        nextChar();
        while (bp < buflen && (Character.isUnicodeIdentifierPart(ch) || ch == '-')) {
            nextChar();
        }
        return new String(buf, start, bp - start);
    }

    protected void skipWhitespace() {
        while (bp < buflen && Character.isWhitespace(ch)) {
            nextChar();
        }
    }

    void nextChar() {
        ch = buf[bp < buflen ? ++bp : buflen];
    }

    protected Map<String, String> tagAttrs() throws ParseException {
        Map<String, String> attrs = new HashMap<>();

        while (bp < buflen && Character.isUnicodeIdentifierStart(ch)) {
            String name = readIdentifier();
            skipWhitespace();

            if (ch != '=')
                throw new ParseException("Expected =");

            nextChar();
            skipWhitespace();

            if (ch != '"' && ch != '\'')
                throw new ParseException("Expected ' or \"");

            char quote = ch; // remember the type of the quote used
            nextChar();
            int valuePos = bp;
            int count = 0;
            while (bp < buflen && ch != quote) {
                nextChar();
                count++;
            }
            if (bp >= buflen) { // TODO: unexpected EOL; fix a similar issue in parsing the @snippet tag
                throw new ParseException("Unexpected EOI");
            }
            nextChar();
            skipWhitespace();
            attrs.put(name, new String(buf, valuePos, count));
        }
        return attrs;
    }
}
