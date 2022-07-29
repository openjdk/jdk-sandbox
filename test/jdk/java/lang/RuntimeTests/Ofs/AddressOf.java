/*
 * Copyright (c) 2020, Red Hat, Inc. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
 * @summary Test for Runtime.addressOf with 32-bit compressed oops
 * @library /test/lib
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -Xint
 *                   AddressOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=1
 *                   AddressOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-TieredCompilation
 *                   AddressOf
 */

/*
 * @test
 * @summary Test for Runtime.addressOf with zero-based compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -Xint
 *                   AddressOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=1
 *                   AddressOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-TieredCompilation
 *                   AddressOf
 */

/*
 * @test
 * @summary Test for Runtime.addressOf without compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -Xint
 *                   AddressOf
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=1
 *                   AddressOf
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-TieredCompilation
 *                   AddressOf
 */

import java.lang.reflect.Field;

public class AddressOf {

    public static void main(String ... args) throws Exception {
        testAddress_newInteger();
        testAddress_IntegerValueOf();
        testAddress_newObject();
        testAddress_localObject();
        testAddress_fieldObject();
        testNulls();
    }

    private static void testAddress_newInteger() throws Exception {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertNotEquals(0, Runtime.addressOf(new Integer(c)));
        }
    }

    private static void testAddress_IntegerValueOf() throws Exception {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertNotEquals(0, Runtime.addressOf(Integer.valueOf(c)));
        }
    }

    private static void testAddress_newObject() throws Exception {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertNotEquals(0, Runtime.addressOf(new Object()));
        }
    }

    private static void testAddress_localObject() throws Exception {
        Object o = new Object();
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertNotEquals(0, Runtime.addressOf(o));
        }
    }

    static Object staticO = new Object();

    private static void testAddress_fieldObject() throws Exception {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertNotEquals(0, Runtime.addressOf(staticO));
        }
    }

    private static void testNulls() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            try {
                Runtime.addressOf(null);
                RuntimeOfUtil.assertFail();
            } catch (NullPointerException e) {
                // expected
            }
        }
    }

}
