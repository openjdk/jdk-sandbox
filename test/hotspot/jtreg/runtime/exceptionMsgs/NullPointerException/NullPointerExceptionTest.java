/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2019 SAP SE. All rights reserved.
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

/**
 * @test
 * @summary Test extended NullPointerException message for class
 *   files generated without debugging information. The message lists
 *   detailed information about the entity that is null.
 * @modules java.base/java.lang:open
 *          java.base/jdk.internal.org.objectweb.asm
 * @library /test/lib
 * @compile NullPointerExceptionTest.java
 * @run main NullPointerExceptionTest
 */
/**
 * @test
 * @summary Test extended NullPointerException message for
 *   classfiles generated with debug information. In this case the name
 *   of the variable containing the array is printed.
 * @modules java.base/java.lang:open
 *          java.base/jdk.internal.org.objectweb.asm
 * @library /test/lib
 * @compile -g NullPointerExceptionTest.java
 * @run main/othervm -XX:+WizardMode NullPointerExceptionTest hasDebugInfo
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import jdk.test.lib.Asserts;

import java.lang.reflect.*;
import java.lang.invoke.MethodHandles.Lookup;
import static java.lang.invoke.MethodHandles.*;
import static java.lang.invoke.MethodHandles.Lookup.*;

import jdk.internal.org.objectweb.asm.ClassWriter;
import jdk.internal.org.objectweb.asm.MethodVisitor;
import jdk.internal.org.objectweb.asm.Type;
import jdk.internal.org.objectweb.asm.Label;
import static jdk.internal.org.objectweb.asm.Opcodes.*;

/**
 * Tests NullPointerExceptions
 */
public class NullPointerExceptionTest {

    // Some fields used in the test.
    static Object nullStaticField;
    NullPointerExceptionTest nullInstanceField;
    static int[][][][] staticArray;
    static long[][] staticLongArray = new long[1000][];
    DoubleArrayGen dag;
    ArrayList<String> names = new ArrayList<>();
    ArrayList<String> curr;
    static boolean hasDebugInfo = false;

    static {
        staticArray       = new int[1][][][];
        staticArray[0]    = new int[1][][];
        staticArray[0][0] = new int[1][];
    }

    public static void checkMessage(String expression,
                                    String obtainedMsg, String expectedMsg) {
        System.out.println();
        System.out.println(" source code: " + expression);
        System.out.println("  thrown msg: " + obtainedMsg);
        if (obtainedMsg.equals(expectedMsg)) return;
        System.out.println("expected msg: " + expectedMsg);
        Asserts.assertEquals(obtainedMsg, expectedMsg);
    }

    public static void main(String[] args) throws Exception {
        NullPointerExceptionTest t = new NullPointerExceptionTest();
        if (args.length > 0) {
            hasDebugInfo = true;
        }

        // Test the message printed for the failed action.
        t.testFailedAction();

        // Test the method printed for the null entity.
        t.testNullEntity();
        
        // Test that no message is printed for exceptions
        // allocated explicitly.
        t.testCreation();
        
        // Test that no message is printed for exceptions
        // thrown in native methods.
        t.testNative();

        // Test that two calls to getMessage() return the same
        // message.
        // It is a design decision that it returns two different
        // String objects.
        t.testSameMessage();
        
        // Test serialization.
        // It is a design decision that after serialization the
        // the message is lost.
        t.testSerialization();

        // Test that messages are printed for code generated 
        // on-the-fly.
        t.testGeneratedCode();
        

        // Some more interesting complex messages.
        t.testComplexMessages();
    }

    // Helper method to cause test case.
    private double callWithTypes(String[][] dummy1, int[][][] dummy2, float dummy3, long dummy4, short dummy5, 
                                 boolean dummy6, byte dummy7, double dummy8, char dummy9) {
        return 0.0;
    }

    public void testFailedAction() {
        int[]     ia1 = null;
        float[]   fa1 = null;
        Object[]  oa1 = null;
        boolean[] za1 = null;
        byte[]    ba1 = null;
        char[]    ca1 = null;
        short[]   sa1 = null;
        long[]    la1 = null;
        double[]  da1 = null;

        // iaload
        try {
            System.out.println(ia1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ia1[0]", e.getMessage(),
                         (hasDebugInfo ? "'ia1'" : "'<local1>'") + " is null. " +
                         "Can not load from null int array.");
        }
        // faload
        try {
            System.out.println(fa1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("fa1[0]", e.getMessage(),
                         (hasDebugInfo ? "'fa1'" : "'<local2>'") + " is null. " +
                         "Can not load from null float array.");
        }
        // aaload
        try {
            System.out.println(oa1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("oa1[0]", e.getMessage(),
                         (hasDebugInfo ? "'oa1'" : "'<local3>'") + " is null. " +
                         "Can not load from null object array.");
        }
        // baload (boolean)
        try {
            System.out.println(za1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("za1[0]", e.getMessage(),
                         (hasDebugInfo ? "'za1'" : "'<local4>'") + " is null. " +
                         "Can not load from null byte/boolean array.");
        }
        // baload (byte)
        try {
            System.out.println(ba1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ba1[0]", e.getMessage(),
                         (hasDebugInfo ? "'ba1'" : "'<local5>'") + " is null. " +
                         "Can not load from null byte/boolean array.");
        }
        // caload
        try {
            System.out.println(ca1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ca1[0]", e.getMessage(),
                         (hasDebugInfo ? "'ca1'" : "'<local6>'") + " is null. " +
                         "Can not load from null char array.");
        }
        // saload
        try {
            System.out.println(sa1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("sa1[0]", e.getMessage(),
                         (hasDebugInfo ? "'sa1'" : "'<local7>'") + " is null. " +
                         "Can not load from null short array.");
        }
        // laload
        try {
            System.out.println(la1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("la1[0]", e.getMessage(),
                         (hasDebugInfo ? "'la1'" : "'<local8>'") + " is null. " +
                         "Can not load from null long array.");
        }
        // daload
        try {
            System.out.println(da1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("da1[0]", e.getMessage(),
                         (hasDebugInfo ? "'da1'" : "'<local9>'") + " is null. " +
                         "Can not load from null double array.");
        }

        // iastore
        try {
            System.out.println(ia1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ia1[0] = 0", e.getMessage(),
                         (hasDebugInfo ? "'ia1'" : "'<local1>'") + " is null. " +
                         "Can not store to null int array.");
        }
        // fastore
        try {
            System.out.println(fa1[0] = 0.7f);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("fa1[0] = false", e.getMessage(),
                         (hasDebugInfo ? "'fa1'" : "'<local2>'") + " is null. " +
                         "Can not store to null float array.");
        }
        // aastore
        try {
            System.out.println(oa1[0] = null);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("oa1[0] = null", e.getMessage(),
                         (hasDebugInfo ? "'oa1'" : "'<local3>'") + " is null. " +
                         "Can not store to null object array.");
        }
        // bastore (boolean)
        try {
            System.out.println(za1[0] = false);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("za1[0] = false", e.getMessage(),
                         (hasDebugInfo ? "'za1'" : "'<local4>'") + " is null. " +
                         "Can not store to null byte/boolean array.");
        }
        // bastore (byte)
        try {
            System.out.println(ba1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ba1[0] = 0", e.getMessage(),
                         (hasDebugInfo ? "'ba1'" : "'<local5>'") + " is null. " +
                         "Can not store to null byte/boolean array.");
        }
        // castore
        try {
            System.out.println(ca1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ca1[0] = 0", e.getMessage(),
                         (hasDebugInfo ? "'ca1'" : "'<local6>'") + " is null. " +
                         "Can not store to null char array.");
        }
        // sastore
        try {
            System.out.println(sa1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("sa1[0] = 0", e.getMessage(),
                         (hasDebugInfo ? "'sa1'" : "'<local7>'") + " is null. " +
                         "Can not store to null short array.");
        }
        // lastore
        try {
            System.out.println(la1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("la1[0] = 0", e.getMessage(),
                         (hasDebugInfo ? "'la1'" : "'<local8>'") + " is null. " +
                         "Can not store to null long array.");
        }
        // dastore
        try {
            System.out.println(da1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("da1[0] = 0", e.getMessage(),
                         (hasDebugInfo ? "'da1'" : "'<local9>'") + " is null. " +
                         "Can not store to null double array.");
        }

        // arraylength
        try {
            System.out.println(za1.length);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("za1.length", e.getMessage(),
                         (hasDebugInfo ? "'za1'" : "'<local4>'") + " is null. " +
                         "Can not read the array length.");
        }
        // athrow
        try {
            throw null;
        } catch (NullPointerException e) {
            checkMessage("throw null", e.getMessage(),
                         "'null' is null. " +
                         "Can not throw a null exception object.");
        }
        // monitorenter
        try {
            synchronized (nullInstanceField) {
                // desired
            }
        } catch (NullPointerException e) {
            checkMessage("synchronized (nullInstanceField)", e.getMessage(),
                         "'this.nullInstanceField' is null. " +
                         "Can not enter a null monitor.");
        }
        // monitorexit
        // No test available

        // getfield
        try {
            System.out.println(nullInstanceField.nullInstanceField);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("nullInstanceField.nullInstanceField", e.getMessage(),
                         "'this.nullInstanceField' is null. " +
                         "Can not read field 'nullInstanceField'.");
        }
        // putfield
        try {
            System.out.println(nullInstanceField.nullInstanceField = null);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("nullInstanceField.nullInstanceField = null", e.getMessage(),
                         "'this.nullInstanceField' is null. " +
                         "Can not write field 'nullInstanceField'.");
        }
        // invoke
        try {
            nullInstanceField.toString();
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("nullInstanceField.toString()", e.getMessage(),
                         "'this.nullInstanceField' is null. " +
                         "Can not invoke method 'java.lang.String java.lang.Object.toString()'.");
        }
        // Test parameter and return types
        try {
            Asserts.assertTrue(nullInstanceField.callWithTypes(null, null, 0.0f, 0L, (short)0, false, (byte)0, 0.0, 'x') == 0.0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("nullInstanceField.callWithTypes(null, null, 0.0f, 0L, (short)0, false, (byte)0, 0.0, 'x')", e.getMessage(),
                         "'this.nullInstanceField' is null. " +
                         "Can not invoke method 'double NullPointerExceptionTest.callWithTypes(java.lang.String[][], int[][][], float, long, short, boolean, byte, double, char)'.");
        }
    }

    static void test_iload() {
        int i0 = 0;
        int i1 = 1;
        int i2 = 2;
        int i3 = 3;
        int i4 = 4;
        int i5 = 5;
        
        int[][] a = new int[6][];

        // iload_0
        try {
            a[i0][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[i0][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[i0]'" : "'<local6>[<local0>]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iload_1
        try {
            a[i1][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[i1][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[i1]'" : "'<local6>[<local1>]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iload_2
        try {
            a[i2][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[i2][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[i2]'" : "'<local6>[<local2>]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iload_3
        try {
            a[i3][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[i3][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[i3]'" : "'<local6>[<local3>]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iload
        try {
            a[i5][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[i5][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[i5]'" : "'<local6>[<local5>]'") + " is null. " +
                         "Can not store to null int array.");
        }
    }

    // Other datatyes than int are not needed.
    // If we implement l2d and similar bytecodes, we can print
    // long expressions as array indexes. Then these here could
    // be used.
    static void test_lload() {
        long l0 = 0L;
        long l1 = 1L;
        long l2 = 2L;
        long l3 = 3L;
        long l4 = 4L;
        long l5 = 5L;
        
        int[][] a = new int[6][];

        // lload_0
        try {
            a[(int)l0][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)l0][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local12>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // lload_1
        try {
            a[(int)l1][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)l1][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local12>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // lload_2
        try {
            a[(int)l2][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)l2][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local12>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // lload_3
        try {
            a[(int)l3][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)l3][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local12>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // lload
        try {
            a[(int)l5][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)l5][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local12>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }        
    }

    static void test_fload() {
        float f0 = 0.0f;
        float f1 = 1.0f;
        float f2 = 2.0f;
        float f3 = 3.0f;
        float f4 = 4.0f;
        float f5 = 5.0f;
        
        int[][] a = new int[6][];

        // fload_0
        try {
            a[(int)f0][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)f0][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local6>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // fload_1
        try {
            a[(int)f1][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)f1][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local6>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // fload_2
        try {
            a[(int)f2][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)f2][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local6>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // fload_3
        try {
            a[(int)f3][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)f3][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local6>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // fload
        try {
            a[(int)f5][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)f5][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[...]'" : "'<local6>[...]'") + " is null. " +
                         "Can not store to null int array.");
        }        
    }

    static void test_aload() {
        F f0 = null;
        F f1 = null;
        F f2 = null;
        F f3 = null;
        F f4 = null;
        F f5 = null;

        // aload_0
        try {
            f0.i = 33;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("f0.i", e.getMessage(),
                         (hasDebugInfo ? "'f0'" : "'<local0>'") + " is null. " +
                         "Can not write field 'i'.");
        }
        // aload_1
        try {
            f1.i = 33;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("f1.i", e.getMessage(),
                         (hasDebugInfo ? "'f1'" : "'<local1>'") + " is null. " +
                         "Can not write field 'i'.");
        }
        // aload_2
        try {
            f2.i = 33;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("f2.i", e.getMessage(),
                         (hasDebugInfo ? "'f2'" : "'<local2>'") + " is null. " +
                         "Can not write field 'i'.");
        }
        // aload_3
        try {
            f3.i = 33;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("f3.i", e.getMessage(),
                         (hasDebugInfo ? "'f3'" : "'<local3>'") + " is null. " +
                         "Can not write field 'i'.");
        }
        // aload
        try {
            f5.i = 33;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("f5.i", e.getMessage(),
                         (hasDebugInfo ? "'f5'" : "'<local5>'") + " is null. " +
                         "Can not write field 'i'.");
        } 
    }

    // Helper class for test cases.
    class A {
        public B to_b;
        public B getB() { return to_b; }
    }

    // Helper class for test cases.
    class B {
        public C to_c;
        public B to_b;
        public C getC() { return to_c; }
        public B getBfromB() { return to_b; }
    }

    // Helper class for test cases.
    class C {
        public D to_d;
        public D getD() { return to_d; }
    }

    // Helper class for test cases.
    class D {
        public int num;
        public int[][] ar;
    }


    public void testArrayChasing() {
        int[][][][][][] a = null;
        try {
            a[0][0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a is null", e.getMessage(),
                         (hasDebugInfo ? "'a'" : "'<local1>'") + " is null. " +
                         "Can not load from null object array.");
        }
        a = new int[1][][][][][];
        try {
            a[0][0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0] is null", e.getMessage(),
                         (hasDebugInfo ? "'a[0]'" : "'<local1>[0]'") + " is null. " +
                         "Can not load from null object array.");
        }
        a[0] = new int[1][][][][];
        try {
            a[0][0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0][0] is null", e.getMessage(),
                         (hasDebugInfo ? "'a[0][0]'" : "'<local1>[0][0]'") + " is null. " +
                         "Can not load from null object array.");
        }
        a[0][0] = new int[1][][][];
        try {
            a[0][0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0][0][0] is null", e.getMessage(),
                         (hasDebugInfo ? "'a[0][0][0]'" : "'<local1>[0][0][0]'") + " is null. " +
                         "Can not load from null object array.");
        }
        a[0][0][0] = new int[1][][];
        try {
            a[0][0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0][0][0][0] is null", e.getMessage(),
                         (hasDebugInfo ? "'a[0][0][0][0]'" : "'<local1>[0][0][0][0]'") + " is null. " +
                         "Can not load from null object array.");
        }
        a[0][0][0][0] = new int[1][];
        // Reaching max recursion depth. Prints <array>.
        try {
            a[0][0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0][0][0][0][0] is null", e.getMessage(),
                         "'<array>[0][0][0][0][0]' is null. " +
                         "Can not store to null int array.");
        }
        a[0][0][0][0][0] = new int[1];
        try {
            a[0][0][0][0][0][0] = 99;
        } catch (NullPointerException e) {
            Asserts.fail();
        }
    }

    public void testPointerChasing() {
        A a = null;
        try {
            a.to_b.to_c.to_d.num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.to_b.to_c.to_d.num = 99 // a is null", e.getMessage(),
                         (hasDebugInfo ? "'a'" : "'<local1>'") + " is null. " +
                         "Can not read field 'to_b'.");
        }
        a = new A();
        try {
            a.to_b.to_c.to_d.num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.to_b.to_c.to_d.num = 99 // a.to_b is null", e.getMessage(),
                         (hasDebugInfo ? "'a.to_b'" : "'<local1>.to_b'") + " is null. " +
                         "Can not read field 'to_c'.");
        }
        a.to_b = new B();
        try {
            a.to_b.to_c.to_d.num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.to_b.to_c.to_d.num = 99 // a.to_b.to_c is null", e.getMessage(),
                         (hasDebugInfo ? "'a.to_b.to_c'" : "'<local1>.to_b.to_c'") + " is null. " +
                         "Can not read field 'to_d'.");
        }
        a.to_b.to_c = new C();
        try {
            a.to_b.to_c.to_d.num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.to_b.to_c.to_d.num = 99 // a.to_b.to_c.to_d is null", e.getMessage(),
                         (hasDebugInfo ? "'a.to_b.to_c.to_d'" : "'<local1>.to_b.to_c.to_d'") + " is null. " +
                         "Can not write field 'num'.");
        }
    }

    public void testMethodChasing() {
        A a = null;
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a is null", e.getMessage(),
                         (hasDebugInfo ? "'a" : "'<local1>") + "' is null. " +
                         "Can not invoke method 'NullPointerExceptionTest$B NullPointerExceptionTest$A.getB()'.");
        }
        a = new A();
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a.getB() is null", e.getMessage(),
                         "The return value of 'NullPointerExceptionTest$B NullPointerExceptionTest$A.getB()' is null. " +
                         "Can not invoke method 'NullPointerExceptionTest$B NullPointerExceptionTest$B.getBfromB()'.");
        }
        a.to_b = new B();
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a.getB().getBfromB() is null", e.getMessage(),
                         "The return value of 'NullPointerExceptionTest$B NullPointerExceptionTest$B.getBfromB()' is null. " +
                         "Can not invoke method 'NullPointerExceptionTest$C NullPointerExceptionTest$B.getC()'.");
        }
        a.to_b.to_b = new B();
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a.getB().getBfromB().getC() is null", e.getMessage(),
                         "The return value of 'NullPointerExceptionTest$C NullPointerExceptionTest$B.getC()' is null. " +
                         "Can not invoke method 'NullPointerExceptionTest$D NullPointerExceptionTest$C.getD()'.");
        }
        a.to_b.to_b.to_c = new C();
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a.getB().getBfromB().getC().getD() is null", e.getMessage(),
                         "The return value of 'NullPointerExceptionTest$D NullPointerExceptionTest$C.getD()' is null. " +
                         "Can not write field 'num'.");
        }
    }

    public void testMixedChasing() {
        A a = null;
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a is null", e.getMessage(),
                         (hasDebugInfo ? "'a'" : "'<local1>'") + " is null. " +
                         "Can not invoke method 'NullPointerExceptionTest$B NullPointerExceptionTest$A.getB()'.");
        }
        a = new A();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB() is null", e.getMessage(),
                         "The return value of 'NullPointerExceptionTest$B NullPointerExceptionTest$A.getB()' is null. " +
                         "Can not invoke method 'NullPointerExceptionTest$B NullPointerExceptionTest$B.getBfromB()'.");
        }
        a.to_b = new B();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB() is null", e.getMessage(),
                         "The return value of 'NullPointerExceptionTest$B NullPointerExceptionTest$B.getBfromB()' is null. " +
                         "Can not read field 'to_c'.");
        }
        a.to_b.to_b = new B();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB().to_c is null", e.getMessage(),
                         "'NullPointerExceptionTest$B NullPointerExceptionTest$B.getBfromB().to_c' is null. " +
                         "Can not read field 'to_d'.");
        }
        a.to_b.to_b.to_c = new C();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB().to_c.to_d is null", e.getMessage(),
                         "'NullPointerExceptionTest$B NullPointerExceptionTest$B.getBfromB().to_c.to_d' is null. " +
                         "Can not read field 'ar'.");
        }
        a.to_b.to_b.to_c.to_d = new D();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB().to_c.to_d.ar is null", e.getMessage(),
                         "'NullPointerExceptionTest$B NullPointerExceptionTest$B.getBfromB().to_c.to_d.ar' is null. " +
                         "Can not load from null object array.");
        }
        try {
            a.getB().getBfromB().getC().getD().ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().ar[0][0] = 99; // a.getB().getBfromB().getC().getD().ar is null", e.getMessage(),
                         "'NullPointerExceptionTest$D NullPointerExceptionTest$C.getD().ar' is null. " +
                         "Can not load from null object array.");
        }
        a.to_b.to_b.to_c.to_d.ar = new int[1][];
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB().to_c.to_d.ar[0] is null", e.getMessage(),
                         "'NullPointerExceptionTest$B NullPointerExceptionTest$B.getBfromB().to_c.to_d.ar[0]' is null. " +
                         "Can not store to null int array.");
        }
        try {
            a.getB().getBfromB().getC().getD().ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().ar[0][0] = 99; // a.getB().getBfromB().getC().getD().ar[0] is null", e.getMessage(),
                         "'NullPointerExceptionTest$D NullPointerExceptionTest$C.getD().ar[0]' is null. " +
                         "Can not store to null int array.");
        }
    }

    // Helper method to cause test case.
    private Object returnNull(String[][] dummy1, int[][][] dummy2, float dummy3) {
        return null;
    }

    // Helper method to cause test case.
    private NullPointerExceptionTest returnMeAsNull(Throwable dummy1, int dummy2, char dummy3) {
        return null;
    }

    // Helper interface for test cases.
    static interface DoubleArrayGen {
        public double[] getArray();
    }

    // Helper class for test cases.
    static class DoubleArrayGenImpl implements DoubleArrayGen {
        @Override
        public double[] getArray() {
            return null;
        }
    }

    // Helper class for test cases.
    static class NullPointerGenerator {
        public static Object nullReturner(boolean dummy1) {
            return null;
        }

        public Object returnMyNull(double dummy1, long dummy2, short dummy3) {
            return null;
        }
    }

    // Helper method to cause test case.
    public void ImplTestLoadedFromMethod(DoubleArrayGen gen) {
        try {
            (gen.getArray())[0] = 1.0;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("(gen.getArray())[0]", e.getMessage(),
                         "The return value of 'double[] NullPointerExceptionTest$DoubleArrayGen.getArray()' is null. Can not store to null double array.");
        }
    }

    public void testNullEntity() {
        int[][] a = new int[820][];
        
        test_iload();
        test_lload();
        test_fload();
        // test_dload();
        test_aload();
        // aload_0: 'this'
        try {
            this.nullInstanceField.nullInstanceField = null;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("this.nullInstanceField.nullInstanceField = null", e.getMessage(),
                         "'this.nullInstanceField' is null. Can not write field 'nullInstanceField'.");
        }

        // aconst_null
        try {
            throw null;
        } catch (NullPointerException e) {
            checkMessage("throw null", e.getMessage(),
                         "'null' is null. Can not throw a null exception object.");
        }        
        // iconst_0
        try {
            a[0][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[0][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[0]'" : "'<local1>[0]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iconst_1
        try {
            a[1][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[1][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[1]'" : "'<local1>[1]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iconst_2
        try {
            a[2][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[2][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[2]'" : "'<local1>[2]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iconst_3
        try {
            a[3][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[3][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[3]'" : "'<local1>[3]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iconst_4
        try {
            a[4][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[4][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[4]'" : "'<local1>[4]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // iconst_5
        try {
            a[5][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[5][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[5]'" : "'<local1>[5]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // long --> iconst
        try {
            a[(int)0L][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[(int)0L][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[0]'" : "'<local1>[0]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // bipush
        try {
            a[139 /*0x77*/][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[139][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[139]'" : "'<local1>[139]'") + " is null. " +
                         "Can not store to null int array.");
        }
        // sipush
        try {
            a[819 /*0x333*/][0] = 77;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a[819][0]", e.getMessage(),
                         (hasDebugInfo ? "'a[819]'" : "'<local1>[819]'") + " is null. " +
                         "Can not store to null int array.");
        }

        // aaload, with recursive descend.
        testArrayChasing();

        // getstatic
        try {
            Asserts.assertTrue(((float[]) nullStaticField)[0] == 1.0f);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((float[]) nullStaticField)[0]", e.getMessage(),
                         "'static NullPointerExceptionTest.nullStaticField' is null. Can not load from null float array.");
        }

        // getfield, with recursive descend.
        testPointerChasing();

        // invokestatic
        try {
            Asserts.assertTrue(((char[]) NullPointerGenerator.nullReturner(false))[0] == 'A');
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((char[]) NullPointerGenerator.nullReturner(false))[0]", e.getMessage(),
                         "The return value of 'java.lang.Object NullPointerExceptionTest$NullPointerGenerator.nullReturner(boolean)' is null. Can not load from null char array.");
        }
        // invokevirtual
        try {
            Asserts.assertTrue(((char[]) (new NullPointerGenerator().returnMyNull(1, 1, (short) 1)))[0] == 'a');
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((char[]) (new NullPointerGenerator().returnMyNull(1, 1, (short) 1)))[0]", e.getMessage(), 
                         "The return value of 'java.lang.Object NullPointerExceptionTest$NullPointerGenerator.returnMyNull(double, long, short)' is null. Can not load from null char array.");
        }
        // Call with array arguments.
        try {
            Asserts.assertTrue(((double[]) returnNull(null, null, 1f))[0] == 1.0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((double[]) returnNull(null, null, 1f))[0] ", e.getMessage(),
                         "The return value of 'java.lang.Object NullPointerExceptionTest.returnNull(java.lang.String[][], int[][][], float)' is null. Can not load from null double array.");
        }
        // invokeinterface
        ImplTestLoadedFromMethod(new DoubleArrayGenImpl());
        try {
            returnMeAsNull(null, 1, 'A').dag = null;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("returnMeAsNull(null, 1, 'A').dag = null", e.getMessage(),
                         "The return value of 'NullPointerExceptionTest NullPointerExceptionTest.returnMeAsNull(java.lang.Throwable, int, char)' is null. Can not write field 'dag'.");
        }
        testMethodChasing();

        // Mixed recursive descend.
        testMixedChasing();

    }


    public void testCreation() throws Exception {
        // If allocated with new, the message should not be generated.
        Asserts.assertNull(new NullPointerException().getMessage());
        String msg = new String("A pointless message.");
        Asserts.assertTrue(new NullPointerException(msg).getMessage() == msg);
        
        // If created via reflection, the message should not be generated.
        Exception ex = NullPointerException.class.getDeclaredConstructor().newInstance();
        Asserts.assertNull(ex.getMessage());        
    }

    public void testNative() throws Exception {
        // If NPE is thrown in a native method, the message should
        // not be generated.
        try {
            Class.forName(null);
            Asserts.fail();
        } catch (NullPointerException e) {
            Asserts.assertNull(e.getMessage());
        }
        
    }

    // Test we get the same message calling npe.getMessage() twice.
    public void testSameMessage() throws Exception {
        Object null_o = null;
        String expectedMsg =
            (hasDebugInfo ? "'null_o" : "'<local1>") + "' is null. " +
            "Can not invoke method 'int java.lang.Object.hashCode()'.";

        try {
            null_o.hashCode();
            Asserts.fail();
        } catch (NullPointerException npe) {
            String msg1 = npe.getMessage();
            checkMessage("null_o.hashCode()", msg1, expectedMsg);
            String msg2 = npe.getMessage();
            Asserts.assertTrue(msg1.equals(msg2));
            // It was decided that getMessage should generate the
            // message anew on every call, so this does not hold.
            //Asserts.assertTrue(msg1 == msg2);
            Asserts.assertFalse(msg1 == msg2);
        }
    }

    public void testSerialization() throws Exception {
        // NPE without message.
        Object o1 = new NullPointerException();
        ByteArrayOutputStream bos1 = new ByteArrayOutputStream();
        ObjectOutputStream oos1 = new ObjectOutputStream(bos1);
        oos1.writeObject(o1);
        ByteArrayInputStream bis1 = new ByteArrayInputStream(bos1.toByteArray());
        ObjectInputStream ois1 = new ObjectInputStream(bis1);
        Exception ex1 = (Exception) ois1.readObject();
        Asserts.assertNull(ex1.getMessage());

        // NPE with custom message.
        String msg2 = "A useless message";
        Object o2 = new NullPointerException(msg2);
        ByteArrayOutputStream bos2 = new ByteArrayOutputStream();
        ObjectOutputStream oos2 = new ObjectOutputStream(bos2);
        oos2.writeObject(o2);
        ByteArrayInputStream bis2 = new ByteArrayInputStream(bos2.toByteArray());
        ObjectInputStream ois2 = new ObjectInputStream(bis2);
        Exception ex2 = (Exception) ois2.readObject();
        Asserts.assertEquals(ex2.getMessage(), msg2);

        // NPE with generated message.
        Object null_o3 = null;
        Object o3 = null;
        String msg3 = null;
        try {
            null_o3.hashCode();
            Asserts.fail();
        } catch (NullPointerException npe3) {
            o3 = npe3;
            msg3 = npe3.getMessage();
            checkMessage("null_o3.hashCode()", msg3, 
                         (hasDebugInfo ? "'null_o3'" : "'<local14>'") + " is null. " +
                         "Can not invoke method 'int java.lang.Object.hashCode()'.");
        }
        ByteArrayOutputStream bos3 = new ByteArrayOutputStream();
        ObjectOutputStream oos3 = new ObjectOutputStream(bos3);
        oos3.writeObject(o3);
        ByteArrayInputStream bis3 = new ByteArrayInputStream(bos3.toByteArray());
        ObjectInputStream ois3 = new ObjectInputStream(bis3);
        Exception ex3 = (Exception) ois3.readObject();
        // It was decided that getMessage should not store the
        // message in Throwable.detailMessage, thus it can not
        // be recovered by serialization.
        //Asserts.assertEquals(ex3.getMessage(), msg3);
        Asserts.assertEquals(ex3.getMessage(), null);
    }

    public void testComplexMessages() {
        try {
            staticLongArray[0][0] = 2L;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("staticLongArray[0][0] = 2L", e.getMessage(),
                         "'static NullPointerExceptionTest.staticLongArray[0]' is null. " +
                         "Can not store to null long array.");
        }

        try {
            Asserts.assertTrue(this.nullInstanceField.nullInstanceField == null);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("this.nullInstanceField.nullInstanceField", e.getMessage(),
                         "'this.nullInstanceField' is null. " +
                         "Can not read field 'nullInstanceField'.");
        }

        try {
            NullPointerExceptionTest obj = this;
            Asserts.assertNull(obj.dag.getArray().clone());
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("obj.dag.getArray().clone()", e.getMessage(),
                         (hasDebugInfo ? "'obj" : "'<local1>") + ".dag' is null. " +
                         "Can not invoke method 'double[] NullPointerExceptionTest$DoubleArrayGen.getArray()'.");
        }
        try {
            int indexes[] = new int[1];
            NullPointerExceptionTest[] objs = new NullPointerExceptionTest[] {this};
            Asserts.assertNull(objs[indexes[0]].nullInstanceField.returnNull(null, null, 1f));
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("objs[indexes[0]].nullInstanceField.returnNull(null, null, 1f)", e.getMessage(),
                         (hasDebugInfo ? "'objs[indexes" : "'<local2>[<local1>") + "[0]].nullInstanceField' is null. " +
                         "Can not invoke method 'java.lang.Object NullPointerExceptionTest.returnNull(java.lang.String[][], int[][][], float)'.");
        }

        try {
            int indexes[] = new int[1];
            NullPointerExceptionTest[][] objs =
                new NullPointerExceptionTest[][] {new NullPointerExceptionTest[] {this}};
            synchronized (objs[indexes[0]][0].nullInstanceField) {
                Asserts.fail();
            }
        } catch (NullPointerException e) {
            checkMessage("synchronized (objs[indexes[0]][0].nullInstanceField)", e.getMessage(),
                         (hasDebugInfo ? "'objs[indexes" : "'<local2>[<local1>" ) + "[0]][0].nullInstanceField' is null. " +
                         "Can not enter a null monitor.");
        }
    }

    // Generates:
    // class E implements E0 {
    //     public int throwNPE(F f) {
    //         return f.i;
    //     }
    // }
    static byte[] generateTestClass() {
        ClassWriter cw = new ClassWriter(0);
        MethodVisitor mv;

        cw.visit(57, ACC_SUPER, "E", null, "java/lang/Object", new String[] { "E0" });

        {
            mv = cw.visitMethod(0, "<init>", "()V", null, null);
            mv.visitCode();
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false);
            mv.visitInsn(RETURN);
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        {
            mv = cw.visitMethod(ACC_PUBLIC, "throwNPE", "(LF;)I", null, null);
            mv.visitCode();
            Label label0 = new Label();
            mv.visitLabel(label0);
            mv.visitLineNumber(118, label0);
            mv.visitVarInsn(ALOAD, 1);
            mv.visitFieldInsn(GETFIELD, "F", "i", "I");
            mv.visitInsn(IRETURN);
            Label label1 = new Label();
            mv.visitLabel(label1);
            mv.visitLocalVariable("this", "LE;", null, label0, label1, 0);
            mv.visitLocalVariable("f", "LE;", null, label0, label1, 1);
            mv.visitMaxs(1, 2);
            mv.visitEnd();
        }
        cw.visitEnd();

        return cw.toByteArray();
    }

    // Tests that a class generated on the fly is handled properly.
    public void testGeneratedCode() throws Exception {
        byte[] classBytes = generateTestClass();
        Lookup lookup = lookup();
        Class<?> clazz = lookup.defineClass(classBytes);
        E0 e = (E0) clazz.getDeclaredConstructor().newInstance();
        try {
            e.throwNPE(null);
        } catch (NullPointerException ex) {
            checkMessage("return f.i;",
                         ex.getMessage(),
                         "'f' is null. Can not read field 'i'.");
        }
    }
}

// Helper interface for test cases needed for generateTestClass().
interface E0 {
    public int throwNPE(F f);
}

// Helper class for test cases needed for generateTestClass().
class F {
    int i;
}
