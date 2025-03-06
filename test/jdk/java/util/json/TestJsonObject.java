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

import java.util.ArrayList;
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
        compareValueTypes(((JsonObject)expectedJson).keys(), JsonObject.of(builtJson).keys());
    }

    @Test
    void existingBuildTest() {
        var sourceJson = Json.parse(jsonObjStr);
        var builtJson = JsonObject.of(((JsonObject)sourceJson).keys());
        compareValueTypes(((JsonObject)sourceJson).keys(), builtJson.keys());
    }

    @Test
    void removalTest() {
        var expectedJson = Json.parse(halfJsonObjStr);
        var sourceJson = Json.parse(jsonObjStr);
        var builtJson = new HashMap<>(((JsonObject) sourceJson).keys());
        builtJson.remove("name");
        compareValueTypes(((JsonObject)expectedJson).keys(), builtJson);
    }

    @Test
    void clearTest() {
        var expectedJson = Json.parse(emptyJsonObjStr);
        var builtJson = JsonObject.of(Map.of());
        compareValueTypes(((JsonObject)expectedJson).keys(), builtJson.keys());
    }

    // Basic test to check of factory for JsonObject
    @Test
    void ofFactoryTest() {
        HashMap<String, JsonValue> map = new HashMap<>();
        map.put("foo", Json.fromUntyped(5));
        map.put("bar", Json.fromUntyped("value"));
        map.put("baz", Json.fromUntyped((Object) null));
        compareValueTypes(JsonObject.of(map).keys(),
                ((JsonObject)Json.parse("{ \"foo\" : 5, \"bar\" : \"value\", \"baz\" : null}")).keys());
    }

    private static void compareValueTypes(Map<String, JsonValue> expected, Map<String, JsonValue> actual) {
        assertEquals(expected.size(), actual.size());
        for (var entry : expected.entrySet()) {
            assertEquals(entry.getValue().getClass(), actual.get(entry.getKey()).getClass());
        }
    }

    // Check for basic duplicate key
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
                "{ foo \"name\": \"Brian\"}", // Garbage before key
                "{ \"name\" foo : \"Brian\"}", // Garbage after key, but before colon
                // Garbage in second key/val
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

    // Enforce nest limit in of factory
    @Test
    void ofFactoryNestTest() {
        // Make a max value nested JV
        var root = new ArrayList<>();
        var node = root;
        for (int i = 0; i < 31; i++) {
            var childNode = new ArrayList<>();
            node.add(childNode);
            node = childNode;
        }
        var jv = Json.fromUntyped(root);
        // Try to sneak into of factory, to create nest past limit
        assertThrows(IllegalArgumentException.class,
                () -> JsonObject.of(Map.of("foo", jv)));
    }

    @Test
    void immutabilityTest() {
        var map = new HashMap<String, JsonValue>();
        map.put("foo", JsonString.of("foo"));
        var jo = JsonObject.of(map);
        assertEquals(1, jo.keys().size());
        // Modifications to backed map should not change JsonObject
        map.put("bar", JsonString.of("foo"));
        assertEquals(1, jo.keys().size());
    }

    @Test
    void immutabilityUntypedTest() {
        var map = new HashMap<String, String>();
        map.put("foo", "foo");
        var jo = (JsonObject) Json.fromUntyped(map);
        assertEquals(1, jo.keys().size());
        // Modifications to backed map should not change JsonObject
        map.put("bar", "foo");
        assertEquals(1, jo.keys().size());
    }

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
    public void testWhiteSpaceEquality() {
        var obj = Json.parse(jsonExtraSpaces);
        var str = assertDoesNotThrow(() -> obj.toString()); // build the map/arr
        var expStr = Json.parse(jsonWithSpaces).toString();
        // Ensure equivalent Json (besides white space) generates equivalent
        // toString values
        assertEquals(expStr, str);
    }
}
