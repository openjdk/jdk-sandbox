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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.Map;
import java.util.json.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJsonObject {

    private static final String jsonObjStr =
            """
            { "name": "Brian", "shoeSize": 10 }
            """;

    private static final String halfJsonObjStr =
            """
            { "shoeSize": 10 }
            """;

    private static final String emptyJsonObjStr =
            """
            { }
            """;

    @Test
    void emptyBuildTest() {
        var expectedJson = Json.parse(jsonObjStr);
        var builtJson = new HashMap<String, JsonValue>();
        builtJson.put("name", Json.fromUntyped("Brian"));
        builtJson.put("shoeSize", Json.fromUntyped(10));
        compareValueTypes(((JsonObject)expectedJson).members(), JsonObject.of(builtJson).members());
    }

    @Test
    void existingBuildTest() {
        var sourceJson = Json.parse(jsonObjStr);
        var builtJson = JsonObject.of(((JsonObject)sourceJson).members());
        compareValueTypes(((JsonObject)sourceJson).members(), builtJson.members());
    }

    @Test
    void removalTest() {
        var expectedJson = Json.parse(halfJsonObjStr);
        var sourceJson = Json.parse(jsonObjStr);
        var builtJson = new HashMap<>(((JsonObject) sourceJson).members());
        builtJson.remove("name");
        compareValueTypes(((JsonObject)expectedJson).members(), builtJson);
    }

    @Test
    void clearTest() {
        var expectedJson = Json.parse(emptyJsonObjStr);
        var builtJson = JsonObject.of(Map.of());
        compareValueTypes(((JsonObject)expectedJson).members(), builtJson.members());
    }

    // Basic test to check of factory for JsonObject
    @Test
    void ofFactoryTest() {
        HashMap<String, JsonValue> map = new HashMap<>();
        map.put("foo", Json.fromUntyped(5));
        map.put("bar", Json.fromUntyped("value"));
        map.put("baz", Json.fromUntyped((Object) null));
        compareValueTypes(JsonObject.of(map).members(),
                ((JsonObject)Json.parse("{ \"foo\" : 5, \"bar\" : \"value\", \"baz\" : null}")).members());
    }

    private static void compareValueTypes(Map<String, JsonValue> expected, Map<String, JsonValue> actual) {
        assertEquals(expected.size(), actual.size());
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue().getClass(), actual.get(entry.getKey()).getClass());
        }
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

    private static Stream<String> malformedObjectParseTest() {
        return Stream.of(
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
                "{ \"name\": \"Brian\" \"another String\"}"
        );
    }

    @ParameterizedTest
    @MethodSource
    void malformedObjectParseTest(String badJson) {
        assertThrows(JsonParseException.class, () -> Json.parse(badJson));
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

    private static final String json =
            """
            [{"name":"John","age":30,"city":"New York"},{"name":"Jane","age":20,"city":"Boston"},true,false,null,["array","inside",{"inner obj":true,"top-level":false}],"foo",42]""";

    private static final String jsonWithSpaces =
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

    private static final String jsonExtraSpaces =
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
        var obj = Json.parse(jsonExtraSpaces);
        var str = assertDoesNotThrow(() -> obj.toString()); // build the map/arr
        var expStr = Json.parse(jsonWithSpaces).toString();
        // Ensure equivalent Json (besides white space) generates equivalent
        // toString values
        assertEquals(expStr, str);
    }

    @Test
    void orderingParseTest() {
        assertEquals(json, Json.parse(jsonWithSpaces).toString());
    }

    @Test
    void nullKeyTest() {
        Map<String, JsonValue> map = new HashMap<>();
        map.put(null, JsonNull.of());
        assertThrows(NullPointerException.class, () -> JsonObject.of(map));
        assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(map));
    }

    @Test
    void orderingUntypedTest() {
        var jsonFromUntyped = Json.toUntyped(Json.parse(jsonWithSpaces));
        assertEquals(json, Json.fromUntyped(jsonFromUntyped).toString());
    }

    @Test
    void orderingOfTest() {
        var jsonFromOf = ((JsonArray)Json.parse(jsonWithSpaces)).values();
        assertEquals(json, JsonArray.of(jsonFromOf).toString());
    }

    @Test
    void testToDisplayStringOrder() {
        var json = """
            {
              "a": 1,
              "c": 2,
              "b": 3
            }""";
        assertEquals(json, Json.toDisplayString(Json.parse(json)));
    }

}
