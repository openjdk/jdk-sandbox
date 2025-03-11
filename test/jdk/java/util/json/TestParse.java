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
 * @run junit TestParse
 */

import org.junit.jupiter.api.Test;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.json.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TestParse {

    private static final String basicJson =
            """
            { "name": "Brian", "shoeSize": 10 }
            """;

    private static final String TEMPLATE =
        """
        { 
          "obj": {
            "foo": "bar",
            "baz": 20,
            "array": [
              "foo",
              "bar",
              {
                "foo": %s
              }
            ]
          }
        }
        """;

    private static Stream<Arguments> validValues() {
        return Stream.of(
            Arguments.of("[ 1, 2, 3, \"bar\" ]"),
            Arguments.of("{\"bar\": \"baz\"}"),
            Arguments.of(" 42 "),
            Arguments.of(" true "),
            Arguments.of(" null "),
            Arguments.of("\"foo\"")
        );
    }

    @ParameterizedTest
    @MethodSource("validValues")
    void validLazyParse(String value) {
        Json.parse(TEMPLATE.formatted(value));
    }

    @Test
    void testBasicPrimitiveParse() throws Exception {
        Json.parse("true");
        Json.parse("[true]");
        Json.parse("{\"a\":true}");
        Json.parse("false");
        Json.parse("[false]");
        Json.parse("{\"a\":false}");
        Json.parse("null");
        Json.parse("[null]");
        Json.parse("{\"a\":null}");
    }

    @Test
    void testBasicParse() {
        Json.parse(basicJson);
    }

    @Test
    void testBasicParseAndMatch() {
        var doc = Json.parse(basicJson);
        if (doc instanceof JsonObject o && o.keys() instanceof Map<String, JsonValue> keys
                && keys.get("name") instanceof JsonString js && js.value() instanceof String name
                && keys.get("shoeSize") instanceof JsonNumber jn && jn.toBigDecimal() instanceof BigDecimal size) {
            assertEquals("Brian", name);
            assertEquals(10, size.intValue());
        } else {
            throw new RuntimeException("Test data incorrect");
        }
    }

    // Ensure modifying input char array has no impact on JsonValue
    @Test
    void testDefensiveCopy() {
        char[] in = basicJson.toCharArray();
        var doc = Json.parse(in);

        // Mutate original char array with nonsense
        Arrays.fill(in, 'A');

        if (doc instanceof JsonObject o
                && o.keys().get("name") instanceof JsonString name
                && o.keys().get("shoeSize") instanceof JsonNumber size) {
            assertEquals("Brian", name.value());
            assertEquals(10, size.toBigDecimal().intValue());
        } else {
            throw new RuntimeException("JsonValue corrupted by input array");
        }
    }
}
