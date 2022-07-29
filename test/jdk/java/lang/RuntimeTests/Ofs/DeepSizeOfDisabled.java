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
 * @summary Test for disabled Runtime.deepSizeOf with 32-bit compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -Xint
 *                   DeepSizeOfDisabled
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -XX:TieredStopAtLevel=1
 *                   DeepSizeOfDisabled
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -XX:-TieredCompilation
 *                   DeepSizeOfDisabled
 */

/*
 * @test
 * @summary Test for disabled Runtime.deepSizeOf with zero-based compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -Xint
 *                   DeepSizeOfDisabled
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -XX:TieredStopAtLevel=1
 *                   DeepSizeOfDisabled
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -XX:-TieredCompilation
 *                   DeepSizeOfDisabled
 */

/*
 * @test
 * @summary Test for disabled Runtime.deepSizeOf without compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -Xint
 *                   DeepSizeOfDisabled
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -XX:TieredStopAtLevel=1
 *                   DeepSizeOfDisabled
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-RuntimeSizeOf
 *                   -XX:-TieredCompilation
 *                   DeepSizeOfDisabled
 */

public class DeepSizeOfDisabled {

    public static void main(String ... args) throws Exception {
        Object o = new Object();
        Object[] arr = new Object[10];
        for (int d = 0; d < 10; d++) {
            arr[d] = new Object();
        }

        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            RuntimeOfUtil.assertEquals(-1, Runtime.deepSizeOf(o));
            RuntimeOfUtil.assertEquals(-1, Runtime.deepSizeOf(arr));
            RuntimeOfUtil.assertEquals(-1, Runtime.deepSizeOf(arr, (obj) -> Runtime.DEEP_SIZE_OF_SHALLOW));
        }
    }

}
