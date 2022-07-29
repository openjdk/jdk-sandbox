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
 * @summary Test for disabled Runtime.fieldOffsetOf with 32-bit compressed oops
 * @library /test/lib
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -Xint
 *                   FieldOffsetOfDisabled
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -XX:TieredStopAtLevel=1
 *                   FieldOffsetOfDisabled
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -XX:-TieredCompilation
 *                   FieldOffsetOfDisabled
 */

/*
 * @test
 * @summary Test for disabled Runtime.fieldOffsetOf with zero-based compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -Xint
 *                   FieldOffsetOfDisabled
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -XX:TieredStopAtLevel=1
 *                   FieldOffsetOfDisabled
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -XX:-TieredCompilation
 *                   FieldOffsetOfDisabled
 */

/*
 * @test
 * @summary Test for disabled Runtime.fieldOffsetOf without compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -Xint
 *                   FieldOffsetOfDisabled
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -XX:TieredStopAtLevel=1
 *                   FieldOffsetOfDisabled
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeFieldOf
 *                   -XX:-TieredCompilation
 *                   FieldOffsetOfDisabled
 */

import java.lang.reflect.Field;

public class FieldOffsetOfDisabled {

    public static void main(String ... args) throws Exception {
        testInstanceOffset();
        testStaticOffset();
    }

    private static void testInstanceOffset() throws Exception {
        Field f = Holder.class.getDeclaredField("x");
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(-1, Runtime.fieldOffsetOf(f));
        }
    }

    private static void testStaticOffset() throws Exception {
        Field f = Holder.class.getDeclaredField("staticX");
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(-1, Runtime.fieldOffsetOf(f));
        }
    }

    static class Holder {
        static int staticX;
        int x;
    }

}
