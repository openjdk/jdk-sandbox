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
import java.math.BigInteger;
import java.util.List;
import java.util.json.Json;
import java.util.json.JsonNumber;
import java.util.json.JsonParseException;
import java.util.stream.Stream;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.FieldSource;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestJsonNumber {

    @Nested
    class TestValue {

        @Test
        void testToNumber() {
            assertTrue(Json.fromUntyped(42) instanceof JsonNumber jn &&
                    jn.toNumber() instanceof long l &&
                    l == 42);
            assertTrue(Json.fromUntyped(4242424242424242L) instanceof JsonNumber jn &&
                    jn.toNumber() instanceof long l &&
                    l == 4242424242424242L);
            assertTrue(Json.fromUntyped(42.42) instanceof JsonNumber jn &&
                    jn.toNumber() instanceof double d &&
                    d == 42.42);
            assertTrue(Json.fromUntyped(42e42) instanceof JsonNumber jn &&
                    jn.toNumber() instanceof double d &&
                    d == 42e42);
            assertTrue(Json.fromUntyped(new BigInteger("1".repeat(400))) instanceof JsonNumber jn &&
                    jn.toNumber() instanceof BigInteger bi &&
                    bi.compareTo(new BigInteger("1".repeat(400))) == 0);
            assertTrue(Json.fromUntyped(new BigDecimal("1e400")) instanceof JsonNumber jn &&
                    jn.toNumber() instanceof BigDecimal bd &&
                    bd.compareTo(new BigDecimal("1e400")) == 0);

            // factories
            assertEquals(JsonNumber.of((byte)42).toNumber(), 42L);
            assertEquals(JsonNumber.of((short)42).toNumber(), 42L);
            assertEquals(JsonNumber.of(42).toNumber(), 42L);
            assertEquals(JsonNumber.of(42L).toNumber(), 42L);
            assertEquals(JsonNumber.of(Long.MAX_VALUE).toNumber(), Long.MAX_VALUE);
            assertEquals(JsonNumber.of(0.1f).toNumber(), (double)0.1f);
            assertEquals(JsonNumber.of(0.1d).toNumber(), 0.1d);
            assertEquals(JsonNumber.of(1e0).toNumber(), 1d);
            assertEquals(JsonNumber.of(Double.MAX_VALUE).toNumber(), Double.MAX_VALUE);
        }

        @Test
        void toNumberThrowsTest() {
            var jn = (JsonNumber) Json.parse("9e111111111111");
            assertThrows(NumberFormatException.class, jn::toNumber);
        }

        @Test
        void toBigDecimalThrowsTest() {
            var jn = (JsonNumber) Json.parse("9e111111111111");
            assertThrows(NumberFormatException.class, jn::toBigDecimal);
        }
    }

    @Nested
    class TestParse {

        @ParameterizedTest
        @MethodSource("parseCases")
        void testToString_Parsed(String src) {
            // assert their toString() returns the original text
            assertEquals(src, Json.parse(src).toString());
        }

        @ParameterizedTest
        @MethodSource("parseCases")
        void toNumberParseTest(String json, Class<?> type) {
            // assert that toNumber() returns the expected Number subtype
            Number num = ((JsonNumber) Json.parse(json)).toNumber();
            assertInstanceOf(type, num);
            if (type == Double.class) {assertEquals(Double.parseDouble(json), num);}
            else if (type == Long.class) {assertEquals(new BigDecimal(json).longValueExact(), num);}
            else if (type == BigDecimal.class) {assertEquals(new BigDecimal(json), num);}
            else if (type == BigInteger.class) {assertEquals(new BigDecimal(json).toBigIntegerExact(), num);}
        }

        @ParameterizedTest
        @MethodSource("parseCases")
        void toBigDecimalParseTest(String json) {
            assertTrue(new BigDecimal(json).stripTrailingZeros()
                    .compareTo(((JsonNumber) Json.parse(json)).toBigDecimal()) == 0);
        }

        private static Stream<Arguments> parseCases() {
            return Stream.of(
                    // Long cases
                    Arguments.of("1", Long.class),
                    Arguments.of("0", Long.class),
                    Arguments.of("9223372036854775807", Long.class),
                    Arguments.of("-9223372036854775808", Long.class),
                    // BigInteger cases
                    Arguments.of("9".repeat(309), BigInteger.class),
                    // Less than Long.MIN
                    Arguments.of("-9223372036854775809", BigInteger.class),
                    // Greater than Long.MAX
                    Arguments.of("9223372036854775808", BigInteger.class),

                    // Double cases
                    Arguments.of("1.0", Double.class),
                    Arguments.of("9223372036854775807.0", Double.class),
                    Arguments.of("-9223372036854775808.0", Double.class),
                    Arguments.of("9223372036854775807e0", Double.class),
                    Arguments.of("-9223372036854775807e0", Double.class),
                    Arguments.of("1e0", Double.class),
                    Arguments.of("1e-0", Double.class),
                    Arguments.of("0.0", Double.class),
                    Arguments.of("-0.0", Double.class),
                    Arguments.of("0e0", Double.class),
                    Arguments.of("0e1", Double.class),
                    Arguments.of("0e-0", Double.class),
                    Arguments.of("0e-1", Double.class),
                    Arguments.of("5.5e1", Double.class),
                    Arguments.of("1.0e1", Double.class),
                    Arguments.of("1.0", Double.class),
                    Arguments.of("1.000", Double.class),
                    Arguments.of("0.001e3", Double.class),
                    // Basic Fraction
                    Arguments.of("5.5", Double.class),
                    // Rounds to 4.99999989...
                    Arguments.of("4.9999999", Double.class),
                    // Rounds to integral double value 5.0
                    Arguments.of("4.999999999999999999999999999999999999", Double.class),
                    // Round to integrals
                    Arguments.of("9007199254740989.5", Double.class),
                    Arguments.of("9007199254740990.999999999999", Double.class),
                    Arguments.of("0.0000123E-0000000045", Double.class),
                    // Fraction w/ more sig digs that Db supports
                    Arguments.of("5."+"5".repeat(17), Double.class),
                    Arguments.of("55.55e1", Double.class),
                    Arguments.of("1e-1", Double.class),
                    // Less than Long.MAX, but contains fraction
                    Arguments.of("9223372036854775806.5", Double.class),
                    // Greater than Long.MIN, but contains fraction
                    Arguments.of("-9223372036854775807.5", Double.class),
                    // Double.MIN
                    Arguments.of("4.9E-324", Double.class),
                    Arguments.of("9223372036854775807.5e0", Double.class),
                    Arguments.of("9223372036854775808e0", Double.class),
                    // Double.MAX
                    Arguments.of("1.7976931348623157E308", Double.class),
                    // BigDecimal cases
                    // Arbitrary large value surpassing Double.MAX
                    Arguments.of("9e999", BigDecimal.class),
                    // Greater than Double.MAX
                    Arguments.of("1.7976931348623157E309", BigDecimal.class),
                    // Less than -Double.MAX
                    Arguments.of("-1.7976931348623157E309", BigDecimal.class),
                    Arguments.of("18446744073709551615e999", BigDecimal.class),
                    Arguments.of("9".repeat(309)+".9", BigDecimal.class)
            );
        }

        private static Stream<Arguments> testNumberEquality() {
            return Stream.of(
                    // true
                    Arguments.of("3", "3"),
                    Arguments.of("3", "   3   "),
                    Arguments.of("3.0", "3.0"),
                    Arguments.of("3e0", "3E0"),
                    Arguments.of("3.141592653589793238462643383279", "3.141592653589793238462643383279"),

                    // false
                    Arguments.of("3", "3.0"),
                    Arguments.of("3.0", "3.000"),
                    Arguments.of("3", "3e0"),
                    Arguments.of("0.0", "-0.0"),
                    Arguments.of("3", "4"),
                    Arguments.of("3.0", "3.1"),
                    Arguments.of("3", "3.1"),
                    Arguments.of("3.0", "3.001"),
                    Arguments.of("3", "3e1"),
                    Arguments.of("3.141592653589793238462643383279", "3.141592653589793238462643383278")
            );
        }

        @ParameterizedTest
        @MethodSource
        void testNumberEquality(String arg1, String arg2) {
            var jv1 = Json.parse(arg1);
            var jv2 = Json.parse(arg2);

            // assert their toString() returns the original text (w/o leading/trailing spaces)
            var a1 = arg1.trim();
            var a2 = arg2.trim();
            assertEquals(a1, jv1.toString());
            assertEquals(a2, jv2.toString());

            // equality should be decided by the equality of the string representation,
            // ignoring the case.
            assertEquals(a1.compareToIgnoreCase(a2) == 0, jv1.equals(jv2),
                    "jv1: %s, jv2: %s".formatted(jv1, jv2));
        }

        private static final List<Arguments> INVALID_NUMBER = List.of(
                Arguments.of("01", "Invalid '0' position"),
                Arguments.of("5e-2+2", "Invalid '+' position"),
                Arguments.of("+5", "Invalid '+' position"),
                Arguments.of("5e+2-2", "Invalid '-' position"),
                Arguments.of(".5", "Invalid '.' position"),
                Arguments.of("5e.2", "Invalid '.' position"),
                Arguments.of("5.5.5", "Invalid '.' position"),
                Arguments.of("5e3e", "Invalid '[e|E]' position"),
                Arguments.of("e2", "Invalid '[e|E]' position"),
                Arguments.of("e", "Invalid '[e|E]' position"),
                Arguments.of("5.", "Input expected after '[.|e|E]'"),
                Arguments.of("5e", "Input expected after '[.|e|E]'"));

        @ParameterizedTest
        @FieldSource("INVALID_NUMBER")
        void testMessages(String json, String err) {
            Exception e =  assertThrows(JsonParseException.class, () -> Json.parse(json));
            var msg = e.getMessage();
            assertTrue(msg.contains(err), "Got: \"%s\"\n\tExpected: \"%s\"".formatted(msg, err));
        }
    }

    @Nested
    class TestFactory {

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
        void testToString_factory() {
            assertEquals(JsonNumber.of((byte)42).toString(), "42");
            assertEquals(JsonNumber.of((short)42).toString(), "42");
            assertEquals(JsonNumber.of(42).toString(), "42");
            assertEquals(JsonNumber.of(42L).toString(), "42");
            assertEquals(JsonNumber.of(Long.MAX_VALUE).toString(), Long.valueOf(Long.MAX_VALUE).toString());
            assertEquals(JsonNumber.of(0.1f).toString(), Double.valueOf(0.1f).toString());
            assertEquals(JsonNumber.of(0.1d).toString(), "0.1");
            assertEquals(JsonNumber.of(Double.MAX_VALUE).toString(), Double.valueOf(Double.MAX_VALUE).toString());
        }

        @ParameterizedTest
        @MethodSource
        void factoryTest(Number n) {
            var str = switch (n) {
                case byte b -> JsonNumber.of(b).toString();
                case short s -> JsonNumber.of(s).toString();
                case int i -> JsonNumber.of(i).toString();
                case long l -> JsonNumber.of(l).toString();
                case float f -> JsonNumber.of(f).toString();
                case double d -> JsonNumber.of(d).toString();
                case BigInteger bi -> JsonNumber.of(bi).toString();
                case BigDecimal bd -> JsonNumber.of(bd).toString();
                default -> throw new IllegalArgumentException("incorrect test argument");
            };
            var expected = switch (n) {
                case byte b -> Long.toString(b);
                case short s -> Long.toString(s);
                case int i -> Long.toString(i);
                case long l -> Long.toString(l);
                case float f -> Double.toString(f);
                case double d -> Double.toString(d);
                case BigInteger bi -> bi.toString();
                case BigDecimal bd -> bd.toString();
                default -> throw new IllegalArgumentException("incorrect test argument");
            };
            assertEquals(str, expected);
        }

        private static Stream<Arguments> factoryTest() {
            return Stream.of(
                    Arguments.of((byte)1),
                    Arguments.of((short)1),
                    Arguments.of((int)1),
                    Arguments.of(1L),
                    Arguments.of(1.0000000596046448f), // 1.0000001f -> 1.0000001192092896d
                    Arguments.of(1.0000000596046448d),
                    Arguments.of(new BigInteger("10000000000000000000000000000000000000000000000000000000000000")),
                    Arguments.of(new BigDecimal("1.0000000596046448")),
                    Arguments.of(new BigDecimal(1.0000000596046448d))
            );
        }
    }
}
