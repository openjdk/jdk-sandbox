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
 * @run junit TestEquality
 */

import jdk.internal.util.json.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestEquality {
    private static final String json =
            """
            [
                {"key1": "val1", "key2": 10, "key3": true, "key4": [1, 2, 3]},
                {"key1": "val1", "key2": 10, "key3": true, "key4": [1,2,3]},
                "test",
                "test",
                30,
                30,
                false,
                false,
                null,
                null
            ]
            """;

    private static final List<Object> untyped = Arrays.asList(
            Map.ofEntries(Map.entry("key1", "val1"),
                    Map.entry("key2", 10),
                    Map.entry("key3", Boolean.TRUE),
                    Map.entry("key4", List.of(1, 2, 3))),
            "test",
            30,
            Boolean.FALSE,
            null
    );

    private static Stream<JsonValue> testArrayEquality() {
        return Stream.of(Json.parse(json));
    }

    @MethodSource
    @ParameterizedTest
    public void testArrayEquality(JsonValue doc) {
        if (doc instanceof JsonArray jsonArray) {
            for (int i = 0; i < jsonArray.values().size(); i += 2) {
                assertEquals(jsonArray.values().get(i), jsonArray.values().get(i + 1));
                assertEquals(jsonArray.values().get(i), Json.fromUntyped(untyped.get(i / 2)));
            }
            System.out.println("Test passed");
        } else {
            throw new RuntimeException("document is not an array");
        }
    }

    private static Stream<Arguments> dataEqualityByOriginalText() {
        return Stream.of(
                Arguments.of("3.0", "3.000", false),
                Arguments.of("3.0", "3.0", true),
                Arguments.of("\"afo\"", "\"afo\"", true),
                Arguments.of("\"afo\"", new char[]{'"', 'a', 'f', 'o', '"'}, true),
                Arguments.of("\"afo\"", new char[]{'"', '\\', 'u', '0', '0', '6', '1', 'f', 'o', '"'}, false)
        );
    }

    @ParameterizedTest
    @MethodSource("dataEqualityByOriginalText")
    public void testEqualityByOriginalText(Object arg1, Object arg2, boolean expected) {
        var jv1 = arg1 instanceof String s ? Json.parse(s) :
                arg1 instanceof char[] ca ? Json.parse(ca) : null;
        var jv2 = arg2 instanceof String s ? Json.parse(s) :
                arg2 instanceof char[] ca ? Json.parse(ca) : null;
        var val1 = jv1 instanceof JsonNumber jn ? jn.value() :
                jv1 instanceof JsonString js ? js.value() : null;
        var val2 = jv2 instanceof JsonNumber jn ? jn.value() :
                jv2 instanceof JsonString js ? js.value() : null;
        // two JsonValue arguments should have the same value()
        assertEquals(val1, val2);

        // assert their toString() returns the original text
        assertEquals(arg1 instanceof char[] ca ? new String(ca) : arg1, jv1.toString());
        assertEquals(arg2 instanceof char[] ca ? new String(ca) : arg2, jv2.toString());

        // equality should be decided by their original text
        assertEquals(expected, jv1.equals(jv2),
                "jv1: %s, jv2: %s (jv1.value(): %s, jv2.value(): %s)".formatted(jv1, jv2, val1, val2));
    }
}
