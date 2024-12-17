/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit TestSimple
 */

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.json.*;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class TestSimple {

    private static final String sample1 =
            """
            { "name": "Brian", "shoeSize": 10 }
            """;

    private static final String sample2 =
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

    private static final String spacesSample =
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

    private static final String sample4 =
            """
            [ "\\u306a\\u304A\\u3068", "\\r\\n", "\\u0000", "\\"" ]
            """;

    // Basic test to ensure untyped value() returns unescaped, but toString
    // returns the source
    @Test
    public void untypedStringTest() {
        var s = Json.fromUntyped("\"afo\"");
        var c = Json.fromUntyped(new String(new char[]{'"', '\\', 'u', '0', '0', '6', '1', 'f', 'o', '"'}));
        assertEquals(Json.toUntyped(s), Json.toUntyped(c));
        assertNotEquals(s.toString(), c.toString());
    }

    // White space is allowed but should have no effect
    // on the underlying structure
    @Test
    public void testWhiteSpaceAllowed() {
        var obj = Json.parse(spacesSample);
        var str = assertDoesNotThrow(() -> obj.toString()); // build the map/arr
        var expStr = Json.parse(sample2).toString();
        // Ensure equivalent Json (besides white space) generates equivalent
        // toString values
        assertEquals(expStr, str);
    }

    @Test
    public void testBasicPrimitives() throws Exception {
        Json.parse("true");
        Json.parse("[true]");
        Json.parse("{\"a\":true}");
        Json.parse("false");
        Json.parse("[false]");
        Json.parse("{\"a\":false}");
        Json.parse("null");
        Json.parse("[null]");
        Json.parse("{\"a\":null}");
    }

    @Test
    public void testSimple1() throws Exception {
        JsonValue doc = Json.parse(sample4);
        System.out.println(doc.toString());
    }

    @Test
    public void testSimple2() {
        var doc = Json.parse(sample1);
        if (doc instanceof JsonObject o && o.keys() instanceof Map<String, JsonValue> keys
                && keys.get("name") instanceof JsonString js1 && js1.value() instanceof String name
                && keys.get("shoeSize") instanceof JsonNumber js2 && js2.value() instanceof Number size) {
            System.out.printf("%s's shoe size is %s%n", name, size);
        }
        System.out.println(doc.toString());
    }

    @Test
    public void testSimple3() {
        var doc = Json.parse(sample2);
        if (doc instanceof JsonArray employees) {
            for (JsonValue v : employees.values()) {
                if (v instanceof JsonObject o && o.keys() instanceof Map<String, JsonValue> keys
                        && keys.get("name") instanceof JsonString js1 && js1.value() instanceof String name
                        && keys.get("age") instanceof JsonNumber jnumber && jnumber.value() instanceof Number age
                        && keys.get("city") instanceof JsonString js2 && js2.value() instanceof String city) {
                    System.out.printf("%s is %s years old from %s%n", name, age.intValue(), city);
                }

            }
        }
        System.out.println(doc);
    }

    @Test
    public void testUntyped() {
        var doc = Json.parse(sample2);
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
}
