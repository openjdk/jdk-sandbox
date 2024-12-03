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
 * @run junit TestParse
 */

import jdk.internal.util.json.*;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestParse {

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

    private static Stream<Arguments> invalidValues() {
        return Stream.of(
            Arguments.of("[ 1, 2, 3 \"bar\" ]"),
            Arguments.of("{\"bar\": baz}"),
            Arguments.of(" 42-3 "),
            Arguments.of(" trueee "),
            Arguments.of(" nul "),
            Arguments.of("\"foo\\u323\"")
        );
    }

    @ParameterizedTest
    @MethodSource("validValues")
    public void validLazyParse(String value) {
        Json.parse(TEMPLATE.formatted(value));
    }

    @ParameterizedTest
    @MethodSource("invalidValues")
    public void invalidLazyParse(String value) {
        Json.parse(TEMPLATE.formatted(value));
    }
}
