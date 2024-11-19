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
 * @run junit TestJsonArray
 */

import jdk.internal.util.json.JsonArray;
import jdk.internal.util.json.JsonParser;
import jdk.internal.util.json.JsonValue;
import jdk.internal.util.json.Option;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

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

    private static Stream<JsonValue> testVarargsFactory() {
        return Stream.of(JsonParser.parse(ARRAY), JsonParser.parse(ARRAY, Option.Parse.EAGER_PARSING));
    }

    @MethodSource
    @ParameterizedTest
    void testVarargsFactory(JsonValue doc) {
        if (doc instanceof JsonArray ja) {
            var jsonValues = ja.values().toArray(new JsonValue[0]);
            var newValues = new JsonValue[jsonValues.length * 2];
            System.arraycopy(jsonValues, 0, newValues, 0, jsonValues.length);
            System.arraycopy(jsonValues, 0, newValues, jsonValues.length, jsonValues.length);
            var doubled = JsonArray.ofValues(newValues);
            assertEquals(JsonParser.parse(DOUBLEDARRAY), doubled);
        } else {
            throw new RuntimeException();
        };
    }
}
