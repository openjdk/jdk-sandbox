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
 * @run junit TestJsonNumber
 */

import java.math.BigDecimal;
import java.util.json.*;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.*;

public class TestJsonNumber {

    @Test
    void testInfinity() {
        assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(Double.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.NEGATIVE_INFINITY));
    }

    @Test
    void testNan() {
        assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> JsonNumber.of(Double.NaN));
        // parse test not required for Nan, cannot parse "NaN"
    }

    @Test
    void testValue() {
        assertTrue(Json.fromUntyped(42) instanceof JsonNumber jn &&
                jn.value().longValue() == 42);
        assertTrue(Json.fromUntyped(4242424242424242L) instanceof JsonNumber jn &&
                jn.value().longValue() == 4242424242424242L);
        assertTrue(Json.fromUntyped(42.42) instanceof JsonNumber jn &&
                jn.value().doubleValue() == 42.42);
        assertTrue(Json.fromUntyped(42e42) instanceof JsonNumber jn &&
                jn.value().doubleValue() == 42e42);
        var huge = "18446744073709551615e999";
        assertTrue(Json.parse(huge) instanceof JsonNumber jn &&
                jn.value() instanceof BigDecimal bd &&
                bd.equals(new BigDecimal(huge)));

        // factories
        assertEquals(JsonNumber.of((byte)42).value(), 42L);
        assertEquals(JsonNumber.of((short)42).value(), 42L);
        assertEquals(JsonNumber.of(42).value(), 42L);
        assertEquals(JsonNumber.of(42L).value(), 42L);
        assertEquals(JsonNumber.of(0.1f).value(), (double)0.1f); // TBD
        assertEquals(JsonNumber.of(0.1d).value(), 0.1d);
    }


    @Test
    void testToString_factory() {
        assertEquals(JsonNumber.of((byte)42).toString(), "42");
        assertEquals(JsonNumber.of((short)42).toString(), "42");
        assertEquals(JsonNumber.of(42).toString(), "42");
        assertEquals(JsonNumber.of(42L).toString(), "42");
        assertEquals(JsonNumber.of(0.1f).toString(), "0.10000000149011612"); // TBD
        assertEquals(JsonNumber.of(0.1d).toString(), "0.1");
    }

    private static Stream<String> testToString_Parsed() {
        return Stream.of(
            "3",
            "3.0",
            "3.000",
            "3e0",
            "0.0",
            "-0.0",
            "3.141592653589793238462643383279",
            "-0.000",
            "1.00",
            "12.3e0000",
            "0.0000123E-0000000045"
        );
    }

    @ParameterizedTest
    @MethodSource
    void testToString_Parsed(String src) {
        // assert their toString() returns the original text
        assertEquals(src, Json.parse(src).toString());
    }
}
