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
 * @run junit TestJsonNumber
 */

import jdk.internal.util.json.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class TestJsonNumber {

    @Test
    void testInfinity() {
        assertThrows(IllegalArgumentException.class, () -> Json.from(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.from(Double.NEGATIVE_INFINITY));
        assertThrows(JsonParseException.class, () -> ((JsonNumber) Json.parse("1e309")).value());
        assertThrows(JsonParseException.class, () -> ((JsonNumber) Json.parse("-1e309")).value());
        assertThrows(JsonParseException.class, () -> ((JsonNumber) Json.parse("1e309", Option.Parse.EAGER_PARSING)).value());
        assertThrows(JsonParseException.class, () -> ((JsonNumber) Json.parse("-1e309", Option.Parse.EAGER_PARSING)).value());
    }

    @Test
    void testNan() {
        assertThrows(IllegalArgumentException.class, () -> Json.from(Double.NaN));
        // parse test not required for Nan, cannot parse "NaN"
    }

    @Test
    void testInteger() {
        assertTrue(Json.from(42) instanceof JsonNumber jn &&
                jn.value() instanceof Integer);
    }

    @Test
    void testLong() {
        assertTrue(Json.from(4242424242424242L) instanceof JsonNumber jn &&
                jn.value() instanceof Long);
    }

    @Test
    void testHugeIntegral() {
        var huge = "18446744073709551615";
        if (Json.parse(huge) instanceof JsonNumber jn &&
            jn.value() instanceof Number val) {
            assertEquals(Double.valueOf(huge), val);
        } else {
            throw new RuntimeException("parse failed");
        }
    }

    @Test
    void testFraction() {
        assertTrue(Json.from(42.42) instanceof JsonNumber jn &&
                jn.value() instanceof Double);
    }

    @Test
    void testExponent() {
        assertTrue(Json.from(42e42) instanceof JsonNumber jn &&
                jn.value() instanceof Double);
    }
}
