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
 * @summary JsonBoolean and JsonNull
 * @run junit TestJsonLiteral
 */

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;

import java.util.List;
import java.util.json.Json;
import java.util.json.JsonBoolean;
import java.util.json.JsonNull;
import java.util.json.JsonParseException;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestJsonLiteral {

    @Nested
    class TestParse {

        @ParameterizedTest
        @FieldSource("BASIC_VALID")
        void basicValidParse(String json) {
            assertDoesNotThrow(() -> Json.parse(json),
                    "String parse failed for %s".formatted(json));
            assertDoesNotThrow(() -> Json.parse(json.toCharArray()),
                    "Char parse failed for %s".formatted(json));
        }

        // Basic JSON primitives
        static List<String> BASIC_VALID =
                Stream.of("%s", " %s", "%s ", " %s ", "[%s]", "{\"foo\":%s}")
                        .flatMap(s -> Stream.of("true", "false", "null").map(s::formatted)).toList();

        @ParameterizedTest
        @FieldSource("BASIC_FAIL")
        void basicFailParse(String json) {
            assertThrows(JsonParseException.class, () -> Json.parse(json),
                    "String parse did not fail for %s".formatted(json));
            assertThrows(JsonParseException.class, () -> Json.parse(json.toCharArray()),
                    "Char parse did not fail for %s".formatted(json));
        }

        // Basic JSON primitives and expected parse exception message
        static List<Arguments> BASIC_FAIL =
                List.of(
                        // Null
                        Arguments.of("nul", "Expected null"),
                        Arguments.of("n", "Expected null"),
                        // Boolean
                        Arguments.of("fals", "Expected false"),
                        Arguments.of("f", "Expected false"),
                        Arguments.of("tru", "Expected true"),
                        Arguments.of("t", "Expected true")
                );
    }

    @Nested
    class TestFactory {

        @Test
        void booleanOfTest() {
            assertEquals(Json.parse("true"), JsonBoolean.of(true));
            assertEquals(Json.parse("false"), JsonBoolean.of(false));
        }

        @Test
        void booleanUntypedTest() {
            assertEquals(Json.parse("true"), Json.fromUntyped(true));
            assertEquals(Json.parse("false"), Json.fromUntyped(false));
        }

        @Test
        void nullOfTest() {
            assertEquals(Json.parse("null"), JsonNull.of());
        }

        @Test
        void nullUntypedTest() {
            assertEquals(Json.parse("null"), Json.fromUntyped(null));
        }
    }
}
