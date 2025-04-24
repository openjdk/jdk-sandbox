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
 * @run junit TestEquality
 */

import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.json.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEquality {

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

    private static final List<Object> untyped = Arrays.asList(
            Map.ofEntries(Map.entry("name1", "val1"),
                    Map.entry("name2", 10),
                    Map.entry("name3", Boolean.TRUE),
                    Map.entry("name4", List.of(1, 2, 3))),
            "test",
            30,
            Boolean.FALSE,
            null
    );

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

    private static Stream<JsonValue> testArrayEquality() {
        return Stream.of(Json.parse(json));
    }

    @MethodSource
    @ParameterizedTest
    public void testArrayEquality(JsonValue doc) {
        if (doc instanceof JsonArray jsonArray) {
            for (int i = 0; i < jsonArray.values().size(); i += 2) {
                assertEquals(jsonArray.values().get(i), jsonArray.values().get(i + 1));
                assertEquals(jsonArray.values().get(i), Json.fromUntyped(untyped.get(i / 2)));
            }
            System.out.println("Test passed");
        } else {
            throw new RuntimeException("document is not an array");
        }
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
    public void testStringEquality(Object arg1, Object arg2, boolean expected) {
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
        assertEquals(arg2 instanceof char[] ca ? new String(ca) : arg2, jv2.toString());

        // equality should be decided by the unescaped value for string
        assertEquals(expected, jv1.equals(jv2),
                "jv1: %s, jv2: %s (jv1.value(): %s, jv2.value(): %s)".formatted(jv1, jv2, val1, val2));
    }

    private static Stream<Arguments> testNumberEquality() {
        return Stream.of(
                // true
                Arguments.of("3", "3"),
                Arguments.of("3", "   3   "),
                Arguments.of("3.0", "3.0"),
                Arguments.of("3e0", "3E0"),
                Arguments.of("3.141592653589793238462643383279", "3.141592653589793238462643383279"),

                // false
                Arguments.of("3", "3.0"),
                Arguments.of("3.0", "3.000"),
                Arguments.of("3", "3e0"),
                Arguments.of("0.0", "-0.0"),
                Arguments.of("3", "4"),
                Arguments.of("3.0", "3.1"),
                Arguments.of("3", "3.1"),
                Arguments.of("3.0", "3.001"),
                Arguments.of("3", "3e1"),
                Arguments.of("3.141592653589793238462643383279", "3.141592653589793238462643383278")
        );
    }

    @ParameterizedTest
    @MethodSource
    public void testNumberEquality(String arg1, String arg2) {
        var jv1 = Json.parse(arg1);
        var jv2 = Json.parse(arg2);

        // assert their toString() returns the original text (w/o leading/trailing spaces)
        var a1 = arg1.trim();
        var a2 = arg2.trim();
        assertEquals(a1, jv1.toString());
        assertEquals(a2, jv2.toString());

        // equality should be decided by the equality of the string representation,
        // ignoring the case.
        assertEquals(a1.compareToIgnoreCase(a2) == 0, jv1.equals(jv2),
                "jv1: %s, jv2: %s".formatted(jv1, jv2));
    }
}

