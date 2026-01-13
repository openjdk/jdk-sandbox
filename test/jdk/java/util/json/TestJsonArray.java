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
 * @run junit TestJsonArray
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.json.Json;
import java.util.json.JsonArray;
import java.util.json.JsonBoolean;
import java.util.json.JsonNull;
import java.util.json.JsonNumber;
import java.util.json.JsonObject;
import java.util.json.JsonParseException;
import java.util.json.JsonString;
import java.util.json.JsonValue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJsonArray {

    @Nested
    class TestParse {

        // Some basic malformed JSON arrays and expected error message
        static List<Arguments> BASIC_FAIL = List.of(
                Arguments.of("[ \"foo\"  ", "Array was not closed with ']'"),
                Arguments.of("[ \"foo\",  ", "Missing JSON value"),
                Arguments.of("[ ", "Array was not closed with ']'"),
                Arguments.of("null ]", "Unexpected character(s)"));

        @ParameterizedTest
        @FieldSource("BASIC_FAIL")
        void basicFailParse(String json) {
            assertThrows(JsonParseException.class, () -> Json.parse(json),
                    "String parse did not fail for %s".formatted(json));
            assertThrows(JsonParseException.class, () -> Json.parse(json.toCharArray()),
                    "Char parse did not fail for %s".formatted(json));
        }
    }

    @Nested
    class TestFactory {

        // Ensure equivalence of JsonArray created from parse vs of factory
        @Test
        void testFactory() {

            var doc = Json.parse(
            """
            [1, "two", false, null, {"name": 42}, [1]]
            """);

            var expected = JsonArray.of(
                    List.of(
                            JsonNumber.of(1),
                            JsonString.of("two"),
                            JsonBoolean.of(Boolean.FALSE),
                            JsonNull.of(),
                            JsonObject.of(Map.of("name", JsonNumber.of(42))),
                            JsonArray.of(List.of(JsonNumber.of(1)))
                    )
            ).elements();
            if (doc instanceof JsonArray ja) {
                //only compare types
                compareTypes(expected, ja.elements());
            } else {
                throw new RuntimeException("JsonArray expected");
            }
        }

        private static void compareTypes(List<JsonValue> expected, List<JsonValue> actual) {
            assertEquals(expected.size(), actual.size());
            for (int index = 0; index < expected.size(); index++) {
                assertEquals(expected.get(index).getClass(), actual.get(index).getClass());
            }
        }

        @Test
        void immutabilityOfTest() {
            var list = new ArrayList<JsonValue>();
            list.add(JsonString.of("foo"));
            var ja = JsonArray.of(list);
            assertEquals(1, ja.elements().size());
            // Modifications to backed list should not change JsonArray
            list.add(JsonString.of("foo"));
            assertEquals(1, ja.elements().size());
            // Modifications to JsonArray elements() should throw
            assertThrows(UnsupportedOperationException.class,
                    () -> ja.elements().add(JsonNull.of()),
                    "Array values able to be modified");
        }

        @Test
        void nullTest() {
            // null list to of factory
            assertThrows(NullPointerException.class, () -> JsonArray.of(null));
            List<JsonValue> list = new ArrayList<>();
            list.add(null);
            // JsonArray.of() should throw as typed to JsonValue
            assertThrows(NullPointerException.class, () -> JsonArray.of(list));
        }

        private static final String json =
                """
                [
                    {"name1": "val1", "name2": 10, "name3": true, "name4": [1, 2, 3]},
                    {"name1": "val1", "name2": 10, "name3": true, "name4": [1,2,3]},
                    "test",
                    "test",
                    30,
                    30,
                    false,
                    false,
                    null,
                    null
                ]
                """;

        @Test
        public void testArrayEquality() {
            JsonArray jsonArray = (JsonArray) Json.parse(json);
            for (int i = 0; i < jsonArray.elements().size(); i += 2) {
                assertEquals(jsonArray.elements().get(i), jsonArray.elements().get(i + 1));
            }
        }
    }
}
