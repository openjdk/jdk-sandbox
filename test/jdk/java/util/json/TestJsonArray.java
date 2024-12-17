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
 * @run junit TestJsonArray
 */


import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.json.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class TestJsonArray {

    private static final String ARRAY = """
      [1, "two", false, null, {"key": 42}]
    """;
    private static final String DOUBLEDARRAY = """
      [1, "two", false, null, {"key": 42},
       1, "two", false, null, {"key": 42}]
    """;

    private static Stream<JsonValue> testFactory() {
        return Stream.of(Json.parse(ARRAY));
    }

    @MethodSource
    @ParameterizedTest
    void testFactory(JsonValue doc) {
        if (doc instanceof JsonArray ja) {
            var jsonValues = ja.values();
            var doubled = JsonArray.of(
                    Stream.concat(jsonValues.stream(), jsonValues.stream()).toList());
            assertEquals(Json.parse(DOUBLEDARRAY), doubled);
        } else {
            throw new RuntimeException();
        };
    }

    // Test that the Json::fromUntyped and JsonArray::of factories
    // take the expected element types
    @Test
    void testFromUntyped() {
        var untyped = Arrays.asList(new Object[]{1, null, false, "test"});
        var typed = List.of(JsonNumber.of(1), JsonNull.of(),
                JsonBoolean.of(false), JsonString.of("test"));
        assertEquals(Json.fromUntyped(untyped), JsonArray.of(typed));
    }
}
