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
 * @summary Test for Runtime.deepSizeOf with 32-bit compressed oops
 * @library /test/lib
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -Xint
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=1
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=2
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=3
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=4
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx128m
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-TieredCompilation
 *                   DeepSizeOf
 */

/*
 * @test
 * @summary Test for Runtime.deepSizeOf with zero-based compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -Xint
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=1
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=2
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=3
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=4
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx4g
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-TieredCompilation
 *                   DeepSizeOf
 */

/*
 * @test
 * @summary Test for Runtime.deepSizeOf without compressed oops
 * @library /test/lib
 * @requires vm.bits == 64
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -Xint
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:TieredStopAtLevel=1
 *                   DeepSizeOf
 *
 * @run main/othervm -Xmx128m -XX:-UseCompressedOops
 *                   -XX:+UnlockDiagnosticVMOptions -XX:+AbortVMOnCompilationFailure -Xcheck:jni
 *                   -XX:-TieredCompilation
 *                   DeepSizeOf
 */

public class DeepSizeOf {

    public static void main(String ... args) {
        testSame_newObject();
        testSimpleHierarchy();
        testPartialNulls();

        testNodeChain(0);
        testNodeChain(1);
        testNodeChain(10);
        testNodeChain(100);

        testNodeTree();

        testObjArray(0);
        testObjArray(1);
        testObjArray(10);
        testObjArray(100);

        testNulls();

        testIncludeCheck();
        testIncludeCheckDeep();
    }

    private static void testSame_newObject() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            Object o = new Object();
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(o), Runtime.deepSizeOf(o));
        }
    }

    private static void testNodeChain(int depth) {
        Node n = new Node(null);
        for (int d = 0; d < depth; d++) {
            n = new Node(n);
        }

        for (int c = 0; c < RuntimeOfUtil.SHORT_ITERS; c++) {
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(n)*(depth + 1), Runtime.deepSizeOf(n));
        }
    }

    private static class Node {
       Node next;
       public Node(Node n) { next = n; }
    }

    private static void testNodeTree() {
        TreeNode r = new TreeNode(new TreeNode(new TreeNode(null, null), null), new TreeNode(null, null));
        for (int c = 0; c < RuntimeOfUtil.SHORT_ITERS; c++) {
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(r)*4, Runtime.deepSizeOf(r));
        }
    }

    private static class TreeNode {
       TreeNode l, r;
       public TreeNode(TreeNode l, TreeNode r) { this.l = l; this.r = r; }
    }

    private static void testObjArray(int size) {
        Object o = new Object();
        Object[] arr = new Object[size];
        for (int d = 0; d < size; d++) {
            arr[d] = new Object();
        }

        for (int c = 0; c < RuntimeOfUtil.SHORT_ITERS; c++) {
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(arr) + Runtime.sizeOf(o)*size, Runtime.deepSizeOf(arr));
        }
    }

    private static class A {
        Object o1;
    }

    private static class B extends A {
        Object o2;
    }

    private static void testSimpleHierarchy() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            B b = new B();
            b.o1 = new Object();
            b.o2 = new Object();
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(b) + Runtime.sizeOf(b.o1) + Runtime.sizeOf(b.o2), Runtime.deepSizeOf(b));
        }
    }

    private static class D {
        Object o1;
        Object o2;
    }

    private static void testPartialNulls() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            D d = new D();
            d.o1 = null;
            d.o2 = new Object();
            RuntimeOfUtil.assertEquals(Runtime.sizeOf(d) + Runtime.sizeOf(d.o2), Runtime.deepSizeOf(d));
        }
    }

    private static void testNulls() {
        for (int c = 0; c < RuntimeOfUtil.ITERS; c++) {
            try {
                Runtime.deepSizeOf(null);
                RuntimeOfUtil.assertFail();
            } catch (NullPointerException e) {
                // expected
            }
        }
    }

    private static void testIncludeCheck() {
        for (int i = 0; i < RuntimeOfUtil.ITERS; i++) {
            Object o = new Object();
            RuntimeOfUtil.assertEquals(42L, Runtime.deepSizeOf(o, (obj) -> -42L));
        }
    }

    private static void testIncludeCheckDeep() {
        for (int i = 0; i < RuntimeOfUtil.ITERS; i++) {
            DeepA a = new DeepA();
            DeepB b = new DeepB();
            DeepC c = new DeepC();
            a.b = b;
            b.c = c;
            c.x = new Object();

            long sA = Runtime.sizeOf(a);
            long sB = Runtime.sizeOf(b);
            long sC = Runtime.sizeOf(c);
            long sCX = Runtime.sizeOf(c.x);

            RuntimeOfUtil.assertEquals(sA,
                                       Runtime.deepSizeOf(a, (obj) -> {
                                           if (obj instanceof DeepB)
                                               return 0L; // don't consider DeepB (don't go deeper)
                                           return Runtime.DEEP_SIZE_OF_SHALLOW | Runtime.DEEP_SIZE_OF_TRAVERSE;
                                       }));

            RuntimeOfUtil.assertEquals(sA + sC + sCX,
                                       Runtime.deepSizeOf(a, (obj) -> {
                                           if (obj instanceof DeepB)
                                               return Runtime.DEEP_SIZE_OF_TRAVERSE; // don't consider DeepB (but its references!)
                                           return Runtime.DEEP_SIZE_OF_SHALLOW | Runtime.DEEP_SIZE_OF_TRAVERSE;
                                       }));

            RuntimeOfUtil.assertEquals(sA,
                                       Runtime.deepSizeOf(a, (obj) -> {
                                           if (obj instanceof DeepA)
                                               return Runtime.DEEP_SIZE_OF_SHALLOW; // consider DeepA, but don't go deeper
                                           return Runtime.DEEP_SIZE_OF_SHALLOW | Runtime.DEEP_SIZE_OF_TRAVERSE;
                                       }));

            RuntimeOfUtil.assertEquals(sA + sB,
                                       Runtime.deepSizeOf(a, (obj) -> {
                                           if (obj instanceof DeepB)
                                               return Runtime.DEEP_SIZE_OF_SHALLOW; // consider DeepB, but don't go deeper
                                           return Runtime.DEEP_SIZE_OF_SHALLOW | Runtime.DEEP_SIZE_OF_TRAVERSE;
                                       }));
        }
    }

    private static class DeepA {
        DeepB b;
    }

    private static class DeepB {
        DeepC c;
    }

    private static class DeepC {
        Object x;
    }

}
