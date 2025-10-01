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
 * @summary Checks traversal methods at JsonValue level as well as correct
 *          assertion errors.
 * @modules jdk.incubator.json
 * @run junit TestAssertion
 */

import jdk.incubator.json.Json;
import jdk.incubator.json.JsonAssertionException;
import jdk.incubator.json.JsonBoolean;
import jdk.incubator.json.JsonNull;
import jdk.incubator.json.JsonObject;
import jdk.incubator.json.JsonString;
import jdk.incubator.json.JsonValue;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

public class TestAssertion {

    private static final JsonValue JSON_ROOT_ARRAY = Json.parse(
            """
            [
              {     "name": "John",
                "age": 42
              },
              {
                "name": "Mary",
                "age": 31
              }
            ]
            """);

    private static final JsonValue JSON_ROOT_OBJECT = Json.parse(
            """
               {
                "id" : 1,
                "values" : [ "value", null ], "valuesWithCommas" : [ "v[a]lu}\\"e", "va,,,[lue,", "value,,", 15],
                "foo" : { "bar" : "baz" },
                "qux" : [ [true], { "in" : { } } ],
                "ba\\"zz" : ["Key with escape"],
                "obj" : { "1" : "{", "z" : 2}
            }
            """);

    private static final JsonValue JSON_NESTED_ARRAY =
            // Don't use the API we are testing (member(String))
            ((JsonObject) JSON_ROOT_OBJECT).members().get("values");

    @Test
    void basicTraverseTest() {
        JSON_ROOT_OBJECT.member("id");
        assertEquals(JsonString.of("value"), JSON_ROOT_OBJECT.member("values").element(0));
        assertEquals(JsonNull.of(), JSON_ROOT_OBJECT.member("values").element(1));
        assertEquals(JsonBoolean.of(true), JSON_ROOT_OBJECT.member("qux").element(0).element(0));
    }

    @Test
    void basicTraverseAbsenceTest() {
        var json = Json.parse("{ \"foo\" : null, \"bar\" : \"words\" }");
        assertEquals(Optional.empty(), json.memberOrAbsent("baz"));
        assertNull(json.memberOrAbsent("baz").map(JsonValue::string).orElse(null));
        assertEquals("words", json.memberOrAbsent("bar").map(JsonValue::string).orElse(null));
        assertThrows(JsonAssertionException.class, () -> json.member("foo").memberOrAbsent("baz"));
    }

    @Test
    void basicTraverseNullTest() {
        var json = Json.parse("{ \"foo\" : null, \"bar\" : \"words\" }");
        assertEquals(Optional.empty(), json.member("foo").orNull());
        assertNull(json.member("foo").orNull().map(JsonValue::string).orElse(null));
        assertEquals("words", json.member("bar").orNull().map(JsonValue::string).orElse(null));
    }

    // Ensure that syntactical chars w/in JsonString do not affect path building
    @Test
    void stringTest() {
        assertEquals("JsonNumber is not a JsonString. Path: \"{valuesWithCommas[3\". Location: row 2, col 96.",
                assertThrows(JsonAssertionException.class,
                        () -> JSON_ROOT_OBJECT.member("valuesWithCommas").element(3).string()).getMessage());
        assertEquals("JsonNumber is not a JsonBoolean. Path: \"{obj{z\". Location: row 6, col 31.",
                assertThrows(JsonAssertionException.class,
                        () -> JSON_ROOT_OBJECT.member("obj").member("z").boolean_()).getMessage());
    }

    @Test
    void leafExceptionTest() {
        assertEquals("JsonNumber is not a JsonString. Path: \"[1{age\". Location: row 6, col 11.",
                assertThrows(JsonAssertionException.class,
                        () -> JSON_ROOT_ARRAY.element(1).member("age").string()).getMessage());
    }

    @Test
    void rootArrayTest() {
        assertEquals("JsonObject member \"asge\" does not exist. Path: \"[1\". Location: row 4, col 2.",
                assertThrows(IllegalArgumentException.class,
                        () -> JSON_ROOT_ARRAY.element(1).member("asge").number()).getMessage());
    }

    // Ensure member name with escapes works
    @Test
    void escapedKeyTest() {
        assertEquals("JsonArray index 1 out of bounds for length 1. Path: \"{ba\\\"zz\". Location: row 5, col 15.",
                assertThrows(IllegalArgumentException.class,
                        () -> JSON_ROOT_OBJECT.member("ba\"zz").element(1)).getMessage());
    }

    @Test
    void multiNestedTest() {
        assertEquals("JsonObject member \"zap\" does not exist. Path: \"{qux[1{in\". Location: row 4, col 31.",
                assertThrows(IllegalArgumentException.class,
                        () -> JSON_ROOT_OBJECT.member("qux").element(1).member("in").member("zap")).getMessage());
    }

    // Check array path building behavior for first element, expects '['.
    @Test
    void firstArrayElementTest() {
        assertEquals("JsonArray index 5 out of bounds for length 1. Path: \"{qux[0\". Location: row 4, col 14.",
                assertThrows(IllegalArgumentException.class,
                    () -> JSON_ROOT_OBJECT.member("qux").element(0).element(5)).getMessage());
    }

    // Operations on JsonObject
    @Test
    void failObjectTraverseTest() {
        // Points to the start of the root object -> { ...
        assertEquals("JsonObject is not a JsonArray. Path: \"\". Location: row 0, col 3.",
                assertThrows(JsonAssertionException.class, () -> JSON_ROOT_OBJECT.element(0)).getMessage());
        assertEquals("JsonObject member \"car\" does not exist. Path: \"\". Location: row 0, col 3.",
                assertThrows(IllegalArgumentException.class, () -> JSON_ROOT_OBJECT.member("car")).getMessage());
    }

    // Operations on JsonArray
    @Test
    void failArrayTraverseTest() {
        // Points to the JsonArray value of "values"; starts at -> [ "value", null ] ...
        assertEquals("JsonArray is not a JsonObject. Path: \"{values\". Location: row 2, col 15.",
                assertThrows(JsonAssertionException.class, () -> JSON_NESTED_ARRAY.member("foo")).getMessage());
        assertEquals("JsonArray index 3 out of bounds for length 2. Path: \"{values\". Location: row 2, col 15.",
                assertThrows(IllegalArgumentException.class, () -> JSON_NESTED_ARRAY.element(3)).getMessage());
    }

    @Test
    void failNPETest() {
        // NPE at JsonObject
        assertThrows(NullPointerException.class, () -> JSON_ROOT_OBJECT.member(null));
        // NPE at JsonValue
        assertThrows(NullPointerException.class, () -> JSON_NESTED_ARRAY.member(null));
    }
}
