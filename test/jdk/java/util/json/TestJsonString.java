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
 * @run junit TestJsonString
 */

import java.util.json.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJsonString {

    // Basic test to ensure untyped value() returns unescaped, but toString
    // returns the source
    @Test
    public void untypedStringTest() {
        var s = Json.fromUntyped("\"afo\"");
        var c = Json.fromUntyped(new String(new char[]{'"', '\\', 'u', '0', '0', '6', '1', 'f', 'o', '"'}));
        assertEquals(Json.toUntyped(s), Json.toUntyped(c));
        assertNotEquals(s.toString(), c.toString());
    }

    @Test
    public void illegalEscapeTest() {
        // RE for now
        assertThrows(RuntimeException.class, () -> Json.fromUntyped("\"a\\afo\""));
    }

    // Escape sequence tests
    @ParameterizedTest
    @MethodSource
    public void escapeTest(String src, String expected) {
        assertEquals(((JsonString)Json.parse(src)).value(), expected);
    }
    private static Stream<Arguments> escapeTest() {
        return Stream.of(
                Arguments.of("\"\\\"\"", "\""),
                Arguments.of("\"\\\\\"", "\\"),
                Arguments.of("\"\\/\"", "/"),
                Arguments.of("\"\\b\"", "\b"),
                Arguments.of("\"\\f\"", "\f"),
                Arguments.of("\"\\n\"", "\n"),
                Arguments.of("\"\\r\"", "\r"),
                Arguments.of("\"\\t\"", "\t"),
                Arguments.of("\"\\uD834\\uDD1E\"", "\uD834\uDD1E")
        );
    }
}
