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

/*
 * @test
 * @enablePreview
 * @run junit TestParseException
 */

import java.util.json.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Test both the expected error messages are emitted and
// the correct row column info
public class TestParseException {

    private static final String basic = "foobarbaz";

    private static final String structural =
            """
            [
              null,   foobarbaz
            ]
            """;

    private static final String structuralWithNested =
            """
            {
                "name" :
                [
                    "value",
                    null, foobarbaz
                ]
            }
            """;

    @ParameterizedTest
    @MethodSource
    void testMessages(String json, String err) {
        Exception e =  assertThrows(JsonParseException.class, () -> Json.parse(json));
        var msg = e.getMessage();
        assertTrue(msg.contains(err), "Got: \"%s\"\n\tExpected: \"%s\"".formatted(msg, err));
    }

    // Supplies the invalid JSON string to parse with the expected failure message
    private static Stream<Arguments> testMessages() {
        return Stream.of(
                // Object
                Arguments.of("{ \"foo\" : ", "Missing JSON value"),
                Arguments.of("{ \"foo\" ", "Expected ':' after the member name"),
                Arguments.of("{ \"foo\" : \"bar\" ", "Object was not closed with '}'"),
                Arguments.of("{ \"foo\" : \"bar\",  ", "Expected a member after ','"),
                Arguments.of("{ \"foo\" : 1, \"foo\" : 1  ", "The duplicate member name: 'foo'"),
                Arguments.of("{ foo : \"bar\" ", "Invalid member name"),
                Arguments.of("{ \"foo : ", "Closing quote missing"),
                Arguments.of("{ ", "Object was not closed with '}'"),
                // Escaped names
                Arguments.of("{ \"foo\" : null, \"\\u0066oo\" : null ", "The duplicate member name: 'foo'"),
                Arguments.of("{ \"\\u000\" ", "Invalid Unicode escape sequence"),
                // Array
                Arguments.of("[ \"foo\"  ", "Array was not closed with ']'"),
                Arguments.of("[ \"foo\",  ", "Expected a value after ','"),
                Arguments.of("[ ", "Array was not closed with ']'"),
                // String
                Arguments.of("\"\u001b\"", "Unescaped control code"),
                Arguments.of("\"foo\\a \"", "Illegal escape"),
                Arguments.of("\"foo\\u0\"", "Invalid Unicode escape sequence"),
                Arguments.of("\"foo\\uZZZZ\"", "Invalid Unicode escape sequence"),
                Arguments.of("\"foo ", "Closing quote missing"),
                // Null
                Arguments.of("nul", "Expected null"),
                Arguments.of("n", "Expected null"),
                // Boolean
                Arguments.of("fals", "Expected false"),
                Arguments.of("f", "Expected false"),
                Arguments.of("tru", "Expected true"),
                Arguments.of("t", "Expected true"),
                // Number
                Arguments.of("01", "Invalid '0' position"),
                Arguments.of("5e-2+2", "Invalid '+' position"),
                Arguments.of("+5", "Invalid '+' position"),
                Arguments.of("5e+2-2", "Invalid '-' position"),
                Arguments.of(".5", "Invalid '.' position"),
                Arguments.of("5e.2", "Invalid '.' position"),
                Arguments.of("5.5.5", "Invalid '.' position"),
                Arguments.of("5e3e", "Invalid '[e|E]' position"),
                Arguments.of("e2", "Invalid '[e|E]' position"),
                Arguments.of("e", "Invalid '[e|E]' position"),
                Arguments.of("5.", "Input expected after '[.|e|E]'"),
                Arguments.of("5e", "Input expected after '[.|e|E]'"),
                // Misc
                Arguments.of("", "Missing JSON value"),
                Arguments.of(" ", "Missing JSON value"),
                Arguments.of("z", "Unexpected character(s)"),
                Arguments.of("null ]", "Unexpected character(s)"),
                Arguments.of("null, true", "Unexpected character(s)"),
                Arguments.of("null 5", "Unexpected character(s)")
        );
    }

    @Test
    void testBasicRowCol() {
        Exception e = assertThrows(JsonParseException.class, () -> Json.parse(basic));
        assertEquals("Expected false: (foobarba) at Row 0, Col 0.", e.getMessage());
    }

    @Test
    void testStructuralRowCol() {
        Exception e = assertThrows(JsonParseException.class, () -> Json.parse(structural));
        assertEquals("Expected false: (foobarba) at Row 1, Col 10.", e.getMessage());
    }

    @Test
    void testStructuralWithNestedRowCol() {
        Exception e = assertThrows(JsonParseException.class, () -> Json.parse(structuralWithNested));
        assertEquals("Expected false: (foobarba) at Row 4, Col 14.", e.getMessage());
    }
}
