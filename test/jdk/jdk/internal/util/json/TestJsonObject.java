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

import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        var expectedJson = Json.parse(jsonObjStr);
        var builtJson = new JsonObject.Builder()
                .put("name", Json.from("Brian"))
                .put("shoeSize", Json.from(10)).build();
        assertEquals(expectedJson, builtJson);
    }

    @Test
    public void existingBuildTest() {
        var sourceJson = Json.parse(jsonObjStr);
        var builtJson = new JsonObject.Builder((JsonObject) sourceJson).build();
        assertEquals(builtJson, sourceJson);
    }

    @Test
    public void removalTest() {
        var expectedJson = Json.parse(halfJsonObjStr);
        var sourceJson = Json.parse(jsonObjStr);
        var builtJson = new JsonObject.Builder((JsonObject) sourceJson)
                .remove("name").build();
        assertEquals(builtJson, expectedJson);
    }

    @Test
    public void clearTest() {
        var expectedJson = Json.parse(emptyJsonObjStr);
        var sourceJson = Json.parse(jsonObjStr);
        var builtJson = new JsonObject.Builder((JsonObject) sourceJson)
                .clear().build();
        assertEquals(builtJson, expectedJson);
    }

    // Basic test to check of factory for JsonObject
    @Test
    public void ofFactoryTest() {
        HashMap<String, JsonValue> map = new HashMap<>();
        map.put("foo", Json.from(5));
        map.put("bar", Json.from("value"));
        map.put("baz", Json.from((Object) null));
        assertEquals(JsonObject.of(map),
                Json.parse("{ \"foo\" : 5, \"bar\" : \"value\", \"baz\" : null}"));
    }
}
