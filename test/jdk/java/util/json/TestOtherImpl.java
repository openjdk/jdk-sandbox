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
 * @run junit TestOtherImpl
 */

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.json.Json;
import java.util.json.JsonObject;
import java.util.json.JsonString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOtherImpl {

    private static final JsonString STANDARD_JSON_STRING = JsonString.of("bar");
    private static final JsonString ALT_JSON_STRING =
            new JsonFooString("bar".getBytes(StandardCharsets.UTF_8));

    @Test
    void equalsHashcodeTest() {
        assertEquals(ALT_JSON_STRING, STANDARD_JSON_STRING);
        assertEquals(STANDARD_JSON_STRING, ALT_JSON_STRING);
        assertEquals(STANDARD_JSON_STRING.hashCode(), ALT_JSON_STRING.hashCode());
        assertEquals(STANDARD_JSON_STRING.toString(), ALT_JSON_STRING.toString());
    }

    @Test
    void displayStringTest() {
        assertEquals(Json.toDisplayString(STANDARD_JSON_STRING, 2), Json.toDisplayString(ALT_JSON_STRING, 2));
        // Wrap it in a JsonObject, and check display string equality again
        assertEquals(Json.toDisplayString(JsonObject.of(Map.of("foo", STANDARD_JSON_STRING)), 2),
                Json.toDisplayString(JsonObject.of(Map.of("foo", ALT_JSON_STRING)), 2));
    }

    static class JsonFooString implements JsonString {

        private final String theString;

        public JsonFooString(byte[] bytes) {
            theString = new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public String string() {
            // For testing purposes, just return the String.
            // Real implementations must adhere to un-escaping as specified.
            return theString;
        }

        @Override
        public String toString() {
            return "\""+theString+"\"";
        }

        @Override
        public boolean equals(Object o) {
            return this == o ||
                    o instanceof JsonString ojs && string().equals(ojs.string());
        }

        @Override
        public int hashCode() {
            return string().hashCode();
        }
    }
}

