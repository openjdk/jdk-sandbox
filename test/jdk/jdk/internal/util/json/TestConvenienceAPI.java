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
 * @run junit TestConvenienceAPI
 */

import jdk.internal.util.json.JsonArray;
import jdk.internal.util.json.JsonObject;
import jdk.internal.util.json.JsonParser;
import jdk.internal.util.json.JsonString;
import jdk.internal.util.json.JsonValue;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;


// Tests the public convenience methods of JsonObject/Array
public class TestConvenienceAPI {

    private static final String basicObj =
            """
            { "name": "Brian", "shoeSize": 10 }
            """;

    private static final String arrWithNested =
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

    private static Stream<JsonValue> testGetOrDefault() {
        return Stream.of(JsonParser.parse(basicObj), JsonParser.parseEagerly(basicObj));
    }

    @MethodSource
    @ParameterizedTest
    public void testGetOrDefault(JsonValue doc) {
        var obj = (JsonObject) doc;
        var fallback = JsonString.from("unknown");
        assertEquals(JsonString.from("Brian"),
                obj.getOrDefault("name", fallback));
        assertEquals(fallback, obj.getOrDefault("height", fallback));
    }

    private static Stream<JsonValue> testArrStream() {
        return Stream.of(JsonParser.parse(arrWithNested), JsonParser.parseEagerly(arrWithNested));
    }

    @MethodSource
    @ParameterizedTest
    public void testArrStream(JsonValue doc) {
        var arr = (JsonArray) doc;
        assertEquals(arr.stream().toList(), arr.values());
    }

    private static Stream<JsonValue> testContainsKey() {
        return Stream.of(JsonParser.parse(basicObj), JsonParser.parseEagerly(basicObj));
    }

    @MethodSource
    @ParameterizedTest
    public void testContainsKey(JsonValue doc) {
        var obj = (JsonObject) doc;
        assertTrue(obj.contains("name"));
        assertFalse(obj.contains("height"));
    }

    private static Stream<JsonValue> testSize() {
        return Stream.of(JsonParser.parse(arrWithNested), JsonParser.parseEagerly(arrWithNested));
    }

    @MethodSource
    @ParameterizedTest
    public void testSize(JsonValue doc) {
        var arr = (JsonArray) doc;
        assertEquals(8, arr.size());
        var obj = (JsonObject) arr.get(0);
        assertEquals(3, obj.size());
    }
}
