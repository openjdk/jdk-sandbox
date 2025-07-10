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
 * @run junit TestJsonObject
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.json.Json;
import java.util.json.JsonArray;
import java.util.json.JsonBoolean;
import java.util.json.JsonNull;
import java.util.json.JsonObject;
import java.util.json.JsonParseException;
import java.util.json.JsonString;
import java.util.json.JsonValue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsonObject {

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

    private static final String JSON_NO_NEWLINE =
            """
            [{"name":"John","age":30,"city":"New York"},{"name":"Jane","age":20,"city":"Boston"},true,false,null,["array","inside",{"inner obj":true,"top-level":false}],"foo",42]""";

    @Nested
    class TestParse {

        // Ensure storage is done with the unescaped version
        @Test
        void retrievalTest() {
            // parse
            var jo = (JsonObject) Json.parse("{ \"foo\\t\" : false}");
            assertEquals(JsonBoolean.of(false), jo.members().get("foo\t"));
            jo = (JsonObject) Json.parse("{ \"foo\\u0009\" : false}");
            assertEquals(JsonBoolean.of(false), jo.members().get("foo\t"));
            // jo factory
            jo = JsonObject.of(Map.of("foo\t", JsonBoolean.of(false)));
            assertEquals(JsonBoolean.of(false), jo.members().get("foo\t"));
            // untyped factory
            jo = (JsonObject) Json.fromUntyped(Map.of("foo\t", JsonBoolean.of(false)));
            assertEquals(JsonBoolean.of(false), jo.members().get("foo\t"));
        }

        @Test
        void toStringTest() {
            // 2 char sequence first
            var key = "\" \\t \\u0021 \\u0022 \\u005c \\u0008 test \"";
            var map = "{" + key + ":null}";
            assertEquals("{\" \\t ! \\\" \\\\ \\b test \":null}", Json.parse(map).toString());
            // Unicode escape sequence first
            var key2 = "\" \\u0021 \\t \\u0022 \\u005c \\u0008 test \"";
            var map2 = "{" + key2 + ":null}";
            assertEquals("{\" ! \\t \\\" \\\\ \\b test \":null}", Json.parse(map2).toString());
        }

        // Check for basic duplicate name
        @Test
        void testDuplicateKeys() {
            var json =
                    """
                    { "clone": "bob", "clone": "foo" }
                    """;
            assertThrows(JsonParseException.class, () -> Json.parse(json));
        }

        // https://datatracker.ietf.org/doc/html/rfc8259#section-8.3
        // Check for equality via unescaped value
        @Test
        void testDuplicateKeyEqualityUnescaped() {
            var json =
                    """
                    { "clone": "bob", "clon\\u0065": "foo" }
                    """;
            assertThrows(JsonParseException.class, () -> Json.parse(json));
        }

        @Test
        void testDuplicateKeyEqualityMultipleUnescaped() {
            var json =
                    """
                    { "clonee": "bob", "clon\\u0065\\u0065": "foo" }
                    """;
            assertThrows(JsonParseException.class, () -> Json.parse(json));
        }

        @Test
        void testDuplicateKeyEqualityUnescapedVariant() {
            var json =
                    """
                    { "c\\b": "bob", "c\b": "foo" }
                    """;
            assertThrows(JsonParseException.class, () -> Json.parse(json));
        }

        private static final List<String> INVALID_OBJECTS = List.of(
                "{ :name\": \"Brian\"}",
                "{ \"name:: \"Brian\"}",
                "{ \"name\": :Brian\"}",
                "{ \"name\": \"Brian:}",
                "{ \"name\": ,Brian\"}",
                "{ foo \"name\": \"Brian\"}", // Garbage before name
                "{ \"name\" foo : \"Brian\"}", // Garbage after name, but before colon
                // Garbage in second name/val
                "{ \"name\": \"Brian\" , \"name2\": \"Brian\" 5}",
                "{ \"name\": \"Brian\" 5}", // Garbage next to closing bracket
                "{ \"name\": \"Brian\"5   }", // Garbage next to value
                "{ \"name\": \"Brian\" 5 }", // Garbage with ws
                // Other cases, where non index based JsonValue occurs first
                "{ \"name\": 5 \"Brian\"  }",
                "{ \"name\": 5  null  }",
                // Garbage after JsonValue in the form of index based JsonValue
                "{ \"name\": \"Brian\" { \"name2\": \"another String\"} }",
                "{ \"name\": \"Brian\" [\"another String\"] }",
                "{ \"name\": \"Brian\" \"another String\"}");

        @ParameterizedTest
        @FieldSource("INVALID_OBJECTS")
        void malformedObjectParseTest(String badJson) {
            assertThrows(JsonParseException.class, () -> Json.parse(badJson));
        }

        private static final List<Arguments> INVALID_OBJECTS_MESSAGES = List.of(
                Arguments.of("{ \"foo\" : ", "Missing JSON value"),
                Arguments.of("{ \"foo\" ", "Expected ':' after the member name"),
                Arguments.of("{ \"foo\" : \"bar\" ", "Object was not closed with '}'"),
                Arguments.of("{ \"foo\" : \"bar\",  ", "Object was not closed with '}'"),
                Arguments.of("{ \"foo\" : 1, \"foo\" : 1  ", "The duplicate member name: 'foo'"),
                Arguments.of("{ foo : \"bar\" ", "Invalid member name"),
                Arguments.of("{ \"foo : ", "Closing quote missing"),
                Arguments.of("{ ", "Object was not closed with '}'"),
                // Escaped names
                Arguments.of("{ \"foo\" : null, \"\\u0066oo\" : null ", "The duplicate member name: 'foo'"),
                Arguments.of("{ \"\\u000\" ", "Invalid Unicode escape sequence"));

        @ParameterizedTest
        @FieldSource("INVALID_OBJECTS_MESSAGES")
        void testMessages(String json, String err) {
            Exception e =  assertThrows(JsonParseException.class, () -> Json.parse(json));
            var msg = e.getMessage();
            assertTrue(msg.contains(err), "Got: \"%s\"\n\tExpected: \"%s\"".formatted(msg, err));
        }

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
            var str = assertDoesNotThrow(() -> obj.toString()); // build the map/arr
            var expStr = Json.parse(JSON_WITH_SPACES).toString();
            // Ensure equivalent Json (besides white space) generates equivalent
            // toString values
            assertEquals(expStr, str);
        }

        @Test
        void orderingParseTest() {
            assertEquals(JSON_NO_NEWLINE, Json.parse(JSON_WITH_SPACES).toString());
        }

        @Test
        void testToDisplayStringOrder() {
            var json = """
            {
              "a": 1,
              "c": 2,
              "b": 3
            }""";
            assertEquals(json, Json.toDisplayString(Json.parse(json), 2));
        }

        // Ensure decoded escape sequences are translated to valid JSON
        // Supported 2 char escapes should be translated, otherwise U sequence
        // needs to be preserved.
        @Test
        void controlCodeRoundTripTest() {
            for (int i = 0; i < 32; i++) {
                var mapWithSequence = "{ \" \\u" + String.format("%04x", i) + "\" : true }";
                Json.parse(Json.parse(mapWithSequence).toString());
            }
        }
    }

    @Nested
    class TestFactory {

        private static final String JSON_OBJ =
                """
                { "name": "Brian", "shoeSize": 10 }
                """;

        private static final String SMALL_JSON_OBJ =
                """
                { "shoeSize": 10 }
                """;

        private static final String EMPTY_JSON_OBJ =
                """
                { }
                """;

        @Test
        void unexpectedTypeTest() {
            var df = new DecimalFormat();
            var exception = assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(df));
            assertEquals("DecimalFormat is not a recognized type", exception.getMessage());
        }

        @Test
        void emptyBuildTest() {
            var expectedJson = Json.parse(JSON_OBJ);
            var builtJson = new HashMap<String, JsonValue>();
            builtJson.put("name", Json.fromUntyped("Brian"));
            builtJson.put("shoeSize", Json.fromUntyped(10));
            compareValueTypes(((JsonObject)expectedJson).members(), JsonObject.of(builtJson).members());
        }

        @Test
        void existingBuildTest() {
            var sourceJson = Json.parse(JSON_OBJ);
            var builtJson = JsonObject.of(((JsonObject)sourceJson).members());
            compareValueTypes(((JsonObject)sourceJson).members(), builtJson.members());
        }

        @Test
        void removalTest() {
            var expectedJson = Json.parse(SMALL_JSON_OBJ);
            var sourceJson = Json.parse(JSON_OBJ);
            var builtJson = new HashMap<>(((JsonObject) sourceJson).members());
            builtJson.remove("name");
            compareValueTypes(((JsonObject)expectedJson).members(), builtJson);
        }

        @Test
        void clearTest() {
            var expectedJson = Json.parse(EMPTY_JSON_OBJ);
            var builtJson = JsonObject.of(Map.of());
            compareValueTypes(((JsonObject)expectedJson).members(), builtJson.members());
        }

        // Basic test to check of factory for JsonObject
        @Test
        void ofFactoryTest() {
            HashMap<String, JsonValue> map = new HashMap<>();
            map.put("foo", Json.fromUntyped(5));
            map.put("bar", Json.fromUntyped("value"));
            map.put("baz", Json.fromUntyped(null));
            compareValueTypes(JsonObject.of(map).members(),
                    ((JsonObject)Json.parse("{ \"foo\" : 5, \"bar\" : \"value\", \"baz\" : null}")).members());
        }

        private static void compareValueTypes(Map<String, JsonValue> expected, Map<String, JsonValue> actual) {
            assertEquals(expected.size(), actual.size());
            for (var entry : expected.entrySet()) {
                assertEquals(entry.getValue().getClass(), actual.get(entry.getKey()).getClass());
            }
        }

        @Test
        void immutabilityTest() {
            var map = new HashMap<String, JsonValue>();
            map.put("foo", JsonString.of("foo"));
            var jo = JsonObject.of(map);
            assertEquals(1, jo.members().size());
            // Modifications to original backed map should not change JsonObject
            map.put("bar", JsonString.of("foo"));
            assertEquals(1, jo.members().size());
            // Modifications to JsonObject members() should not be possible
            assertThrows(UnsupportedOperationException.class,
                    () -> jo.members().put("bar", JsonNull.of()),
                    "Object members able to be modified");
        }

        @Test
        void immutabilityUntypedTest() {
            var map = new HashMap<String, String>();
            map.put("foo", "foo");
            var jo = (JsonObject) Json.fromUntyped(map);
            assertEquals(1, jo.members().size());
            // Modifications to backed map should not change JsonObject
            map.put("bar", "foo");
            assertEquals(1, jo.members().size());
            // Modifications to JsonObject members() should not be possible
            assertThrows(UnsupportedOperationException.class,
                    () -> jo.members().put("bar", JsonNull.of()),
                    "Object members able to be modified");
        }

        @Test
        void orderingUntypedTest() {
            var jsonFromUntyped = Json.toUntyped(Json.parse(JSON_WITH_SPACES));
            assertEquals(JSON_NO_NEWLINE, Json.fromUntyped(jsonFromUntyped).toString());
        }

        @Test
        void orderingOfTest() {
            var jsonFromOf = ((JsonArray)Json.parse(JSON_WITH_SPACES)).values();
            assertEquals(JSON_NO_NEWLINE, JsonArray.of(jsonFromOf).toString());
        }

        @Test
        void nullTest() {
            // null map to of factory
            assertThrows(NullPointerException.class, () -> JsonObject.of(null));
            Map<String, JsonValue> map = new HashMap<>();
            // Check null key
            map.put(null, JsonNull.of());
            assertThrows(NullPointerException.class, () -> JsonObject.of(map));
            assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(map));
            map.clear();
            // Check null value
            map.put("foo", null);
            assertThrows(NullPointerException.class, () -> JsonObject.of(map));
            assertDoesNotThrow(() -> Json.fromUntyped(map));
        }

        @Test
        void testUntyped() {
            var doc = Json.parse(JSON_NO_NEWLINE);
            var raw = Json.toUntyped(doc);
            System.out.println(raw);
            System.out.println(Json.fromUntyped(raw));

            var m = HashMap.newHashMap(10);
            m.put("3", 3);
            m.put("4", Boolean.TRUE);
            m.put("5", null);
            var a = new ArrayList();
            a.add(m);
            a.add(null);
            a.add("arrayElement");
            a.add(Boolean.FALSE);
            System.out.println(Json.fromUntyped(a));
            try {
                Json.fromUntyped(Map.of(1, 1));
                throw new RuntimeException("non string key was sneaked in");
            } catch (Exception _) {}
        }

        // Ensure decoded escape sequences are translated to valid JSON
        // Supported 2 char escapes should be translated, otherwise U sequence
        // needs to be preserved.
        @Test
        void controlCodeRoundTripTest() {
            for (int i = 0; i < 32; i++) {
                var sequence = Map.of("\\u" + String.format("%04x", i), JsonNull.of());
                var jo = JsonObject.of(sequence).members();
                JsonObject.of(jo);
                jo = ((JsonObject)Json.fromUntyped(sequence)).members();
                Json.fromUntyped(jo);
            }
        }
    }
}
