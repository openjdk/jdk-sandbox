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
 * @summary Checks traversal methods at JsonValue level as well as correct
 *          assertion errors.
 * @run junit TestAssertion
 */

import java.util.json.Json;
import java.util.json.JsonAssertionException;
import java.util.json.JsonNull;
import java.util.json.JsonString;
import java.util.json.JsonValue;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestAssertion {

    private static final JsonValue JSON = Json.parse(
            """
            {
                "id" : 1,
                "values" : [ "value", null ]
            }
            """);


    @Test
    void basicTraverseTest() {
        JSON.member("id");
        assertEquals(JsonString.of("value"), JSON.member("values").element(0));
        assertEquals(JsonNull.of(), JSON.member("values").element(1));
    }

    @Test
    void failTraverseTest() {
        // Points to the start of the root object -> { ...
        assertEquals("Not a JsonArray. Location in the document: row 0, col 0.",
                assertThrows(JsonAssertionException.class, () -> JSON.element(0)).getMessage());
        assertEquals("Object member 'car' does not exist. Location in the document: row 0, col 0.",
                assertThrows(IllegalArgumentException.class, () -> JSON.member("car")).getMessage());
        // Points to the JsonArray value of "values"; starts at -> [ "value", null ] ...
        assertEquals("Not a JsonObject. Location in the document: row 2, col 15.",
                assertThrows(JsonAssertionException.class, () -> JSON.member("values").member("foo")).getMessage());
        assertEquals("Array index '3' is out of bounds. Location in the document: row 2, col 15.",
                assertThrows(IllegalArgumentException.class, () -> JSON.member("values").element(3)).getMessage());
    }
}
