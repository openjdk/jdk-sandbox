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


import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.json.*;

import static org.junit.jupiter.api.Assertions.*;

public class TestJsonArray {

    @Test
    void testFactory() {
        var doc = Json.parse("""
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
        ).values();
        if (doc instanceof JsonArray ja) {
            //only compare types
            compareTypes(expected, ja.values());
        } else {
            throw new RuntimeException("JsonArray expected");
        }
    }

    // Test that the Json::fromUntyped and JsonArray::of factories
    // take the expected element types
    @Test
    void testFromUntyped() {
        var untyped = Json.fromUntyped(Arrays.asList(new Object[]{1, null, false, "test"}));
        var typed = JsonArray.of(List.of(JsonNumber.of(1), JsonNull.of(),
                JsonBoolean.of(false), JsonString.of("test"))).values();
        if (untyped instanceof JsonArray ja) {
            //only compare types
            compareTypes(typed, ja.values());
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
        assertEquals(1, ja.values().size());
        // Modifications to backed list should not change JsonArray
        list.add(JsonString.of("foo"));
        assertEquals(1, ja.values().size());
        // Modifications to JsonArray values() should throw
        assertThrows(UnsupportedOperationException.class,
                () -> ja.values().add(JsonNull.of()),
                "Array values able to be modified");
    }

    @Test
    void immutabilityUntypedTest() {
        var list = new ArrayList<String>();
        list.add("foo");
        var ja = (JsonArray) Json.fromUntyped(list);
        assertEquals(1, ja.values().size());
        // Modifications to backed list should not change JsonArray
        list.add("foo");
        assertEquals(1, ja.values().size());
        // Modifications to JsonArray values() should throw
        assertThrows(UnsupportedOperationException.class,
                () -> ja.values().add(JsonNull.of()),
                "Array values able to be modified");
    }

    @Test
    void nullValueTest() {
        List<JsonValue> list = new ArrayList<>();
        list.add(null);
        // JsonArray.of() should throw as typed to JsonValue
        assertThrows(NullPointerException.class, () -> JsonArray.of(list));
        // Json.fromUntyped() should map null to JsonNull
        assertDoesNotThrow(() -> Json.fromUntyped(list));
    }
}
