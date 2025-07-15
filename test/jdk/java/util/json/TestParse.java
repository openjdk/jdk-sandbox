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
 * @summary Checks non JSON subtype specific parse behavior
 * @run junit TestParse
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.json.Json;
import java.util.json.JsonNumber;
import java.util.json.JsonObject;
import java.util.json.JsonParseException;
import java.util.json.JsonString;
import java.util.json.JsonValue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestParse {

    private static final String JSON =
            """
            { "name": "Brian", "shoeSize": 10 }
            """;

    // A basic parse and match example
    @Test
    void testBasicParseAndMatch() {
        var doc = Json.parse(JSON);
        if (doc instanceof JsonObject o && o.members() instanceof Map<String, JsonValue> members
                && members.get("name") instanceof JsonString js && js.value() instanceof String name
                && members.get("shoeSize") instanceof JsonNumber jn && jn.toNumber() instanceof long size) {
            assertEquals("Brian", name);
            assertEquals(10, size);
        } else {
            throw new RuntimeException("Test data incorrect");
        }
    }

    // Ensure modifying input char array passed to Json.parse has no impact on JsonValue
    @Test
    void testDefensiveCopy() {
        char[] in = JSON.toCharArray();
        var doc = Json.parse(in);

        // Mutate original char array with nonsense
        Arrays.fill(in, 'A');

        if (doc instanceof JsonObject o
                && o.members().get("name") instanceof JsonString js && js.value() instanceof String name
                && o.members().get("shoeSize") instanceof JsonNumber jn && jn.toNumber() instanceof long size) {
            assertEquals("Brian", name);
            assertEquals(10, size);
        } else {
            throw new RuntimeException("JsonValue corrupted by input array");
        }
    }

    private static final String JSON_WITH_SPACES =
            """
            [
                { "name": "John", "age": 30, "city": "New York" },
                { "name": "Jane", "age": 20, "city": "Boston" },
                true,
                false,
                null,
                [ "array", "inside", {"inner obj": true, "top-level": false}],
                "foo",
                42
            ]
            """;

    private static final String JSON_EXTRA_SPACES =
            """
            [
           \s
                { "name"    : "John",    "age"  : 30, "city": "New York" },
                {  "name": "Jane"  , "age": 20, "city": "Boston" },
                \s
               \s
                true,   \s
                false   ,
                null, \s
                [    "array"  , "inside", {"inner obj": true, "top-level" : false  } ] ,\s
                "foo",\s
                42
              ]
           \s""";

    // White space is allowed but should have no effect
    // on the underlying structure, and should not play a role during equality
    @Test
    void testWhiteSpaceEquality() {
        var obj = Json.parse(JSON_EXTRA_SPACES);
        var str = assertDoesNotThrow(obj::toString);
        var expStr = Json.parse(JSON_WITH_SPACES).toString();
        // Ensure equivalent Json (besides white space) generates equivalent
        // toString values
        assertEquals(expStr, str);
    }

    @Nested
    class TestExceptions {

        // General exceptions not particularly tied to a sub-interface of JsonValue
        private static final List<Arguments> INVALID_JSON = List.of(
                Arguments.of("", "Expected a JSON Object, Array, String, Number, Boolean, or Null. Location: row 0, col 0."),
                Arguments.of(" ", "Expected a JSON Object, Array, String, Number, Boolean, or Null. Location: row 0, col 1."),
                Arguments.of("z", "Unexpected value. Expected a JSON Object, Array, String, Number, Boolean, or Null. Location: row 0, col 0."),
                Arguments.of("null, true", "Additional value(s) were found after the JSON Value. Location: row 0, col 4."),
                Arguments.of("null 5", "Additional value(s) were found after the JSON Value. Location: row 0, col 5.")
        );

        @ParameterizedTest
        @FieldSource("INVALID_JSON")
        void testMessages(String json, String err) {
            Exception e =  assertThrows(JsonParseException.class, () -> Json.parse(json));
            assertEquals(err, e.getMessage());
        }


        // Row Col focused exceptions

        private static final String BASIC = "foobarbaz";

        @Test
        void testBasicRowCol() {
            JsonParseException e = assertThrows(JsonParseException.class, () -> Json.parse(BASIC));
            assertTrue(e.getMessage().contains("Location: row 0, col 0."),
                    "Expected row 0, col 0 but got row "
                            + e.getErrorRow() + ", col " + e.getErrorColumn());
        }

        private static final String STRUCTURAL =
                """
                [
                  null,   foobarbaz
                ]
                """;

        @Test
        void testStructuralRowCol() {
            JsonParseException e = assertThrows(JsonParseException.class, () -> Json.parse(STRUCTURAL));
            assertTrue(e.getMessage().contains("Location: row 1, col 10."),
                    "Expected row 1, col 10 but got row "
                            + e.getErrorRow() + ", col " + e.getErrorColumn());
        }

        private static final String STRUCTURAL_WITH_NESTED =
                """
                {
                    "name" :
                    [
                        "value",
                        null, foobarbaz
                    ]
                }
                """;

        @Test
        void testStructuralWithNestedRowCol() {
            JsonParseException e = assertThrows(JsonParseException.class, () -> Json.parse(STRUCTURAL_WITH_NESTED));
            assertTrue(e.getMessage().contains("Location: row 4, col 14."),
                    "Expected row 4, col 14 but got row "
                            + e.getErrorRow() + ", col " + e.getErrorColumn());
        }
    }
}
