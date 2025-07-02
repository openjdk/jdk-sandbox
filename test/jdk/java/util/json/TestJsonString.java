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
 * @run junit TestJsonString
 */

import java.util.List;
import java.util.json.Json;
import java.util.json.JsonParseException;
import java.util.json.JsonString;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsonString {

    @Nested
    class TestValue {

        // Escape sequence tests on value()
        @ParameterizedTest
        @MethodSource
        void escapeTest(String src, String expected) {
            assertEquals(((JsonString)Json.parse(src)).value(), expected);
        }
        private static Stream<Arguments> escapeTest() {
            return Stream.of(
                    Arguments.of("\"\\\"\"", "\""),
                    Arguments.of("\"\\\\\"", "\\"),
                    Arguments.of("\"\\/\"", "/"),
                    Arguments.of("\"\\b\"", "\b"),
                    Arguments.of("\"\\f\"", "\f"),
                    Arguments.of("\"\\n\"", "\n"),
                    Arguments.of("\"\\r\"", "\r"),
                    Arguments.of("\"\\t\"", "\t"),
                    Arguments.of("\"\\uD834\\uDD1E\"", "\uD834\uDD1E")
            );
        }
    }

    @Nested
    class TestParse {

        private static final List<Arguments> FAIL_STRING = List.of(
                Arguments.of("\"\u001b\"", "Unescaped control code"),
                Arguments.of("\"foo\\a \"", "Illegal escape"),
                Arguments.of("\"foo\\u0\"", "Invalid Unicode escape sequence"),
                Arguments.of("\"foo\\uZZZZ\"", "Invalid Unicode escape sequence"),
                Arguments.of("\"foo ", "Closing quote missing"));

        @ParameterizedTest
        @FieldSource("FAIL_STRING")
        void testMessages(String json, String err) {
            Exception e =  assertThrows(JsonParseException.class, () -> Json.parse(json));
            var msg = e.getMessage();
            assertTrue(msg.contains(err), "Got: \"%s\"\n\tExpected: \"%s\"".formatted(msg, err));
        }

        private static Stream<Arguments> testStringEquality() {
            return Stream.of(
                    Arguments.of("\"afo\"", "\"afo\"", true),
                    Arguments.of("\"afo\"", new char[]{'"', 'a', 'f', 'o', '"'}, true),
                    Arguments.of("\"afo\"", new char[]{'"', '\\', 'u', '0', '0', '6', '1', 'f', 'o', '"'}, true)
            );
        }

        @ParameterizedTest
        @MethodSource
        void testStringEquality(Object arg1, Object arg2) {
            var jv1 = arg1 instanceof String s ? Json.parse(s) :
                    arg1 instanceof char[] ca ? Json.parse(ca) : null;
            var jv2 = arg2 instanceof String s ? Json.parse(s) :
                    arg2 instanceof char[] ca ? Json.parse(ca) : null;
            var val1 = jv1 instanceof JsonString js ? js.value() : null;
            var val2 = jv2 instanceof JsonString js ? js.value() : null;

            // two JsonValue arguments should have the same value()
            assertEquals(val1, val2);

            // assert their toString() returns the original text
            assertEquals(arg1 instanceof char[] ca ? new String(ca) : arg1, jv1.toString());
        }

        @Test
        void toStringTest() {
            // 2 char sequence first
            var str = "\" \\t \\u0021 \\u0022 \\u005c \\u0008 test \"";
            assertEquals("\" \\t ! \\\" \\\\ \\b test \"", Json.parse(str).toString());
            // Unicode escape sequence first
            var str2 = "\" \\u0021 \\t \\u0022 \\u005c \\u0008 test \"";
            assertEquals("\" ! \\t \\\" \\\\ \\b test \"", Json.parse(str2).toString());
        }
    }

    @Nested
    class TestFactory {

        @Test
        void invalidStringTest() {
            // Pass some invalid Strings to the factory
            assertThrows(IllegalArgumentException.class, () -> JsonString.of("Foo \t"));
            assertThrows(IllegalArgumentException.class, () -> JsonString.of("Foo \""));
            assertThrows(IllegalArgumentException.class, () -> JsonString.of("Foo \\"));
            assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped("Foo \\"));

            // Equivalents as above, but properly escaped
            assertDoesNotThrow(() -> JsonString.of("Foo \\t"));
            assertDoesNotThrow(() -> JsonString.of("Foo \\\""));
        }

        @Test
        void toStringTest() {
            var str = " \\t \\u0021 \\u0022 \\u005c \\u0008 test ";
            assertEquals("\" \\t ! \\\" \\\\ \\b test \"", JsonString.of(str).toString());
            // Unicode escape sequence first
            var str2 = " \\u0021 \\t \\u0022 \\u005c \\u0008 test ";
            assertEquals("\" ! \\t \\\" \\\\ \\b test \"", JsonString.of(str2).toString());
        }

        @Test
        void untypedStringTest() {
            var s = Json.fromUntyped("afo");
            var c = Json.fromUntyped(new String(new char[]{'\\', 'u', '0', '0', '6', '1', 'f', 'o'}));
            assertEquals(Json.toUntyped(s), Json.toUntyped(c));
            assertEquals(s.toString(), c.toString());
        }

        @Test
        void illegalEscapeTest() {
            assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped("a\\afo"));
            assertThrows(IllegalArgumentException.class, () -> JsonString.of("a\\afo"));
            assertThrows(IllegalArgumentException.class, () -> JsonString.of("a\\u00AZ"));
        }
    }
}
