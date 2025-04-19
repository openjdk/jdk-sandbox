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
import java.util.Objects;
import java.util.json.Json;
import java.util.json.JsonObject;
import java.util.json.JsonString;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestOtherImpl {

    static final JsonString standardJsonString = JsonString.of("bar");
    static final JsonString altJsonString = new JsonFooString("bar".getBytes(StandardCharsets.UTF_8));

    @Test
    void equalsHashcodeTest() {
        assertTrue(altJsonString.equals(standardJsonString));
        assertTrue(standardJsonString.equals(altJsonString));
        assertEquals(standardJsonString.hashCode(), altJsonString.hashCode());
        assertEquals(standardJsonString.toString(), altJsonString.toString());
    }

    @Test
    void toUntypedTest() {
        assertEquals(Json.toUntyped(standardJsonString), Json.toUntyped(altJsonString));
        // There will be no fromUntypedTest(), we do not want to offer a way
        // to provide non-standard implementations of product types
    }

    @Test
    void displayStringTest() {
        assertEquals(Json.toDisplayString(standardJsonString), Json.toDisplayString(altJsonString));
        // Wrap it in a JsonObject, and check display string equality again
        assertEquals(Json.toDisplayString(JsonObject.of(Map.of("foo", standardJsonString))),
                Json.toDisplayString(JsonObject.of(Map.of("foo", altJsonString))));
    }

    static class JsonFooString implements JsonString {

        private final String theString;

        public JsonFooString(byte[] bytes) {
            theString = new String(bytes, StandardCharsets.UTF_8);
        }

        @Override
        public String value() {
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
                    o instanceof JsonString ojs && Objects.equals(value(), ojs.value());
        }

        @Override
        public int hashCode() {
            return Objects.hash(value());
        }
    }
}

