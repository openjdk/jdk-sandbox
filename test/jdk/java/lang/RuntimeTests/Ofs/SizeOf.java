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
 * @summary Test for Runtime.SizeOf with 32-bit compressed oops
 * @library /test/lib
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -Xint
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:TieredStopAtLevel=1
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:-TieredCompilation
 *                   SizeOf
 */

/*
 * @test
 * @summary Test for Runtime.SizeOf with zero-based compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -Xint
 *                   SizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:TieredStopAtLevel=1
 *                   SizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:-TieredCompilation
 *                   SizeOf
 */

/*
 * @test
 * @summary Test for Runtime.SizeOf without compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -Xint
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:TieredStopAtLevel=1
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:-TieredCompilation
 *                   SizeOf
 */

/*
 * @test
 * @summary Test for Runtime.SizeOf with 32-bit compressed oops
 * @library /test/lib
 * @requires vm.debug
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -Xint
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -XX:TieredStopAtLevel=1
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -XX:-TieredCompilation
 *                   SizeOf
 */

/*
 * @test
 * @summary Test for Runtime.SizeOf with zero-based compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 * @requires vm.debug
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -Xint
 *                   SizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -XX:TieredStopAtLevel=1
 *                   SizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -XX:-TieredCompilation
 *                   SizeOf
 */

/*
 * @test
 * @summary Test for Runtime.SizeOf without compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 * @requires vm.debug
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -Xint
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -XX:TieredStopAtLevel=1
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:FastAllocateSizeLimit=0
 *                   -XX:-TieredCompilation
 *                   SizeOf
 */

/*
 * @test
 * @summary Test for Runtime.SizeOf with 32-bit compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 * @requires vm.debug
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:ObjectAlignmentInBytes=32
 *                   -Xint
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:ObjectAlignmentInBytes=32
 *                   -XX:TieredStopAtLevel=1
 *                   SizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:ObjectAlignmentInBytes=32
 *                   -XX:-TieredCompilation
 *                   SizeOf
 */

/*
 * @test
 * @summary Test for Runtime.SizeOf with zero-based compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 * @requires vm.debug
 *
 * @build jdk.test.whitebox.WhiteBox
 * @run driver jdk.test.lib.helpers.ClassFileInstaller jdk.test.whitebox.WhiteBox
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:ObjectAlignmentInBytes=32
 *                   -Xint
 *                   SizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:ObjectAlignmentInBytes=32
 *                   -XX:TieredStopAtLevel=1
 *                   SizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni -XX:+WhiteBoxAPI -Xbootclasspath/a:.
 *                   -XX:ObjectAlignmentInBytes=32
 *                   -XX:-TieredCompilation
 *                   SizeOf
 */

import jdk.test.lib.Platform;
import jdk.test.whitebox.WhiteBox;

public class SizeOf {

    static final Boolean compressedOops = WhiteBox.getWhiteBox().getBooleanVMFlag("UseCompressedOops");
    static final int R = ((compressedOops == null) || (compressedOops == true)) ?  4 : 8;

    static final Long align = WhiteBox.getWhiteBox().getIntVMFlag("ObjectAlignmentInBytes");
    static final int A = (align == null ? 8 : align.intValue());

    public static void main(String ... args) {
        testSize_newObject();
        testSize_localObject();
        testSize_fieldObject();

        testSize_newSmallByteArray();
        testSize_localSmallByteArray();
        testSize_fieldSmallByteArray();

        testSize_newSmallObjArray();
        testSize_localSmallObjArray();
        testSize_fieldSmallObjArray();

        testNulls();
    }

    private static int roundUp(int v, int a) {
        return (v + a - 1) / a * a;
    }

    private static void testSize_newObject() {
        int expected = roundUp(Platform.is64bit() ? 16 : 8, A);
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(new Object()));
        }
    }

    private static void testSize_localObject() {
        int expected = roundUp(Platform.is64bit() ? 16 : 8, A);
        Object o = new Object();
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(o));
        }
    }

    static Object staticO = new Object();

    private static void testSize_fieldObject() {
        int expected = roundUp(Platform.is64bit() ? 16 : 8, A);
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(staticO));
        }
    }

    private static void testSize_newSmallByteArray() {
        int expected = roundUp(1024 + 16, A);
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(new byte[1024]));
        }
    }

    private static void testSize_localSmallByteArray() {
        byte[] arr = new byte[1024];
        int expected = roundUp(arr.length + 16, A);
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(arr));
        }
    }

    static byte[] smallArr = new byte[1024];

    private static void testSize_fieldSmallByteArray() {
        int expected = roundUp(smallArr.length + 16, A);
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(smallArr));
        }
    }

    private static void testSize_newSmallObjArray() {
        int expected = roundUp(1024*R + 16, A);
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(new Object[1024]));
        }
    }

    private static void testSize_localSmallObjArray() {
        Object[] arr = new Object[1024];
        int expected = roundUp(arr.length*R + 16, A);
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(arr));
        }
    }

    static Object[] smallObjArr = new Object[1024];

    private static void testSize_fieldSmallObjArray() {
        int expected = roundUp(smallArr.length*R + 16, A);
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(expected, Runtime.sizeOf(smallObjArr));
        }
    }

    private static void testNulls() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            try {
                Runtime.sizeOf(null);
                RuntimeOfUtil.assertFail();
            } catch (NullPointerException e) {
                // expected
            }
        }
    }

}
