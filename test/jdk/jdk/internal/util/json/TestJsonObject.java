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
 * @modules java.base/jdk.internal.util.json:+open
 * @run junit TestJsonObject
 */

import jdk.internal.util.json.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Test the API of JsonObject.Builder and JsonObject.of()
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
    public void emptyBuildTest() {
        var expectedJson = JsonParser.parse(jsonObjStr);
        var builtJson = new JsonObject.Builder()
                .put("name", Json.fromUntyped("Brian"))
                .put("shoeSize", Json.fromUntyped(10)).build();
        assertEquals(expectedJson, builtJson);
    }

    @Test
    public void existingBuildTest() {
        var sourceJson = JsonParser.parse(jsonObjStr);
        var builtJson = new JsonObject.Builder((JsonObject) sourceJson).build();
        assertEquals(builtJson, sourceJson);
    }

    @Test
    public void removalTest() {
        var expectedJson = JsonParser.parse(halfJsonObjStr);
        var sourceJson = JsonParser.parse(jsonObjStr);
        var builtJson = new JsonObject.Builder((JsonObject) sourceJson)
                .remove("name").build();
        assertEquals(builtJson, expectedJson);
    }

    @Test
    public void clearTest() {
        var expectedJson = JsonParser.parse(emptyJsonObjStr);
        var sourceJson = JsonParser.parse(jsonObjStr);
        var builtJson = new JsonObject.Builder((JsonObject) sourceJson)
                .clear().build();
        assertEquals(builtJson, expectedJson);
    }

    // Basic test to check of factory for JsonObject
    @Test
    public void ofFactoryTest() {
        HashMap<String, JsonValue> map = new HashMap<>();
        map.put("foo", Json.fromUntyped(5));
        map.put("bar", Json.fromUntyped("value"));
        map.put("baz", Json.fromUntyped((Object) null));
        assertEquals(JsonObject.of(map),
                JsonParser.parse("{ \"foo\" : 5, \"bar\" : \"value\", \"baz\" : null}"));
    }

    // of(Objects...)
    private static Stream<Arguments> of_vararg() {
        return Stream.of(
           Arguments.of(new Object[] {"key1", Json.fromUntyped(Boolean.TRUE), "key2", JsonNull.ofNull()},
                   JsonParser.parse("{\"key1\": true, \"key2\": null}"))
        );
    }
    @MethodSource
    @ParameterizedTest
    public void of_vararg(Object[] input, JsonValue expected) {
        assertEquals(JsonObject.of(input), expected);
    }

    private static Stream<Arguments> of_illegal_vararg() {
        return Stream.of(
            // dummy is needed to fool jupiter for failing argument conversion
            Arguments.of(new Object[] {"key1", Json.fromUntyped(Boolean.TRUE), "key2"}, 0),
            Arguments.of(new Object[] {"key1", "val1", "key2", JsonNull.ofNull()}, 0)
        );
    }
    @MethodSource
    @ParameterizedTest
    public void of_illegal_vararg(Object[] input, Object dummy) {
        assertThrows(IllegalArgumentException.class, () -> JsonObject.of(input));
    }
}
