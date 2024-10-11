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

import jdk.internal.util.json.JsonNumber;
import jdk.internal.util.json.JsonParser;
import jdk.internal.util.json.JsonValue;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

public class TestJsonNumber {
    @Test
    void testInteger() {
        assertTrue(JsonValue.from(42) instanceof JsonNumber jn &&
                jn.value() instanceof Integer);
    }

    @Test
    void testLong() {
        assertTrue(JsonValue.from(4242424242424242L) instanceof JsonNumber jn &&
                jn.value() instanceof Long);
    }

    @Test
    void testHugeIntegral() {
        var huge = "18446744073709551615";
        if (JsonParser.parse(huge) instanceof JsonNumber jn &&
            jn.value() instanceof Number val) {
            assertInstanceOf(BigInteger.class, val);
            assertEquals(huge, val.toString());
        } else {
            throw new RuntimeException("parse failed");
        }
    }

    @Test
    void testFraction() {
        assertTrue(JsonValue.from(42.42) instanceof JsonNumber jn &&
                jn.value() instanceof Double);
    }

    @Test
    void testExponent() {
        assertTrue(JsonValue.from(42e42) instanceof JsonNumber jn &&
                jn.value() instanceof Double);
    }
}
