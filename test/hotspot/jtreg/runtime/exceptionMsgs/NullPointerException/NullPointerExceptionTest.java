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
 * @library /test/lib
 * @compile NullPointerExceptionTest.java
 * @run main NullPointerExceptionTest
 */
/**
 * @test
 * @summary Test extended NullPointerException message for
 *   classfiles generated with debug information. In this case the name
 *   of the variable containing the array is printed.
 * @library /test/lib
 * @compile -g NullPointerExceptionTest.java
 * @run main/othervm -XX:+WizardMode -DhasDebugInfo NullPointerExceptionTest
 */

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import jdk.test.lib.Asserts;

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
    static boolean hasDebugInfo = true;

    static {
        try {
            hasDebugInfo = System.getProperty("hasDebugInfo") != null;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }

        staticArray       = new int[1][][][];
        staticArray[0]    = new int[1][][];
        staticArray[0][0] = new int[1][];
    }

    public static void checkMessage(String expression,
                                    String obtainedMsg, String expectedMsg) {
        System.out.println();
        System.out.println(" source code: " + expression);
        System.out.println("  thrown msg: " + obtainedMsg);
        //System.out.println("expected msg: " + expectedMsg);
        //Asserts.assertEquals(obtainedMsg, expectedMsg);
    }

    public static void main(String[] args) throws Exception {
        NullPointerExceptionTest t = new NullPointerExceptionTest();
        t.testPointerChasing();
        t.testArrayChasing();
        t.testMethodChasing();
        t.testMixedChasing();
        t.testSameMessage();
        t.testCreationViaNew();
        t.testCreationViaReflection();
        t.testCreationViaSerialization();
        t.testLoadedFromLocalVariable1();
        t.testLoadedFromLocalVariable2();
        t.testLoadedFromLocalVariable3();
        t.testLoadedFromLocalVariable4();
        t.testLoadedFromLocalVariable5();
        t.testLoadedFromLocalVariable6();
        t.testLoadedFromLocalVariable7();
        t.testLoadedFromMethod1();
        t.testLoadedFromMethod2();
        t.testLoadedFromMethod3();
        t.testLoadedFromMethod4();
        t.testLoadedFromMethod5();
        t.testLoadedFromMethod6();
        t.testLoadedFromMethod7();
        t.testLoadedFromStaticField1();
        t.testLoadedFromStaticField2();
        t.testLoadedFromStaticField3();
        t.testLoadedFromStaticField4(0, 0);
        t.testLoadedFromStaticField5();
        t.testLoadedFromStaticField5a();
        t.testLoadedFromStaticField5b();
        t.testLoadedFromStaticField6();
        t.testLoadedFromInstanceField1();
        t.testLoadedFromInstanceField2();
        t.testLoadedFromInstanceField3();
        t.testLoadedFromInstanceField4();
        t.testLoadedFromInstanceField5();
        t.testLoadedFromInstanceField6();
        t.testInNative();
        t.testMissingLocalVariableTable();
        t.testNullMessages();
    }

    class A {
        public B to_b;
        public B getB() { return to_b; }
    }

    class B {
        public C to_c;
        public B to_b;
        public C getC() { return to_c; }
        public B getBfromB() { return to_b; }
    }

    class C {
        public D to_d;
        public D getD() { return to_d; }
    }

    class D {
        public int num;
        public int[][] ar;
    }

    public void testPointerChasing() {
        A a = null;
        try {
            a.to_b.to_c.to_d.num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.to_b.to_c.to_d.num = 99 // a is null", e.getMessage(),
                         "while trying to read the field 'to_b' of a null object loaded from " +
                         (hasDebugInfo ? "local variable 'a'" : "a local variable at slot 1"));
        }
        a = new A();
        try {
            a.to_b.to_c.to_d.num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.to_b.to_c.to_d.num = 99 // a.to_b is null", e.getMessage(),
                         "while trying to read the field 'to_c' of a null object loaded from field 'NullPointerExceptionTest$A.to_b' of an object loaded from " +
                         (hasDebugInfo ? "local variable 'a'" : "a local variable at slot 1"));
        }
        a.to_b = new B();
        try {
            a.to_b.to_c.to_d.num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.to_b.to_c.to_d.num = 99 // a.to_b.to_c is null", e.getMessage(),
                         "while trying to read the field 'to_d' of a null object loaded from field 'NullPointerExceptionTest$B.to_c' of an object loaded from field 'NullPointerExceptionTest$A.to_b' of an object");
        }
        a.to_b.to_c = new C();
        try {
            a.to_b.to_c.to_d.num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.to_b.to_c.to_d.num = 99 // a.to_b.to_c.to_d is null", e.getMessage(),
                         "while trying to write the field 'NullPointerExceptionTest$D.num' of a null object loaded from field 'NullPointerExceptionTest$C.to_d' of an object loaded from field 'NullPointerExceptionTest$B.to_c' of an object");
        }
    }

    public void testArrayChasing() {
        int[][][][][] a = null;
        try {
            a[0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a is null", e.getMessage(),
                         "while trying to load from a null object array loaded from " +
                         (hasDebugInfo ? "local variable 'a'" : "a local variable at slot 1"));
        }
        a = new int[1][][][][];
        try {
            a[0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0] is null", e.getMessage(),
                         "while trying to load from a null object array loaded from an array (which itself was loaded from " +
                         (hasDebugInfo ? "local variable 'a'" : "a local variable at slot 1") +
                         ") with an index loaded from a constant");
        }
        a[0] = new int[1][][][];
        try {
            a[0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0][0] is null", e.getMessage(),
                         "while trying to load from a null object array loaded from an array (which itself was loaded from an array) with an index loaded from a constant");
        }
        a[0][0] = new int[1][][];
        try {
            a[0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0][0][0] is null", e.getMessage(),
                         "while trying to load from a null object array loaded from an array (which itself was loaded from an array) with an index loaded from a constant");
        }
        a[0][0][0] = new int[1][];
        try {
            a[0][0][0][0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("int[0][0][0][0][0] = 99 // a[0][0][0][0] is null", e.getMessage(),
                         "while trying to store to a null int array loaded from an array (which itself was loaded from an array) with an index loaded from a constant");
        }
        a[0][0][0][0] = new int[1];
        try {
            a[0][0][0][0][0] = 99;
        } catch (NullPointerException e) {
            Asserts.fail();
        }
    }

    public void testMethodChasing() {
        A a = null;
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a is null", e.getMessage(),
                         "while trying to invoke the method 'NullPointerExceptionTest$A.getB()LNullPointerExceptionTest$B;' on a null reference loaded from " +
                         (hasDebugInfo ? "local variable 'a'" : "a local variable at slot 1"));
        }
        a = new A();
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a.getB() is null", e.getMessage(),
                         "while trying to invoke the method 'NullPointerExceptionTest$B.getBfromB()LNullPointerExceptionTest$B;' on a null reference returned from 'NullPointerExceptionTest$A.getB()LNullPointerExceptionTest$B;'");
        }
        a.to_b = new B();
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a.getB().getBfromB() is null", e.getMessage(),
                         "while trying to invoke the method 'NullPointerExceptionTest$B.getC()LNullPointerExceptionTest$C;' on a null reference returned from 'NullPointerExceptionTest$B.getBfromB()LNullPointerExceptionTest$B;'");
        }
        a.to_b.to_b = new B();
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a.getB().getBfromB().getC() is null", e.getMessage(),
                         "while trying to invoke the method 'NullPointerExceptionTest$C.getD()LNullPointerExceptionTest$D;' on a null reference returned from 'NullPointerExceptionTest$B.getC()LNullPointerExceptionTest$C;'");
        }
        a.to_b.to_b.to_c = new C();
        try {
            a.getB().getBfromB().getC().getD().num = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().num = 99 // a.getB().getBfromB().getC().getD() is null", e.getMessage(),
                         "while trying to write the field 'NullPointerExceptionTest$D.num' of a null object returned from 'NullPointerExceptionTest$C.getD()LNullPointerExceptionTest$D;'");
        }
    }

    public void testMixedChasing() {
        A a = null;
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a is null", e.getMessage(),
                         "while trying to invoke the method 'NullPointerExceptionTest$A.getB()LNullPointerExceptionTest$B;' on a null reference loaded from " +
                         (hasDebugInfo ? "local variable 'a'" : "a local variable at slot 1"));
        }
        a = new A();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB() is null", e.getMessage(),
                         "while trying to invoke the method 'NullPointerExceptionTest$B.getBfromB()LNullPointerExceptionTest$B;' on a null reference returned from 'NullPointerExceptionTest$A.getB()LNullPointerExceptionTest$B;'");
        }
        a.to_b = new B();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB() is null", e.getMessage(),
                         "while trying to read the field 'to_c' of a null object returned from 'NullPointerExceptionTest$B.getBfromB()LNullPointerExceptionTest$B;'");
        }
        a.to_b.to_b = new B();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB().to_c is null", e.getMessage(),
                         "while trying to read the field 'to_d' of a null object loaded from field 'NullPointerExceptionTest$B.to_c' of an object returned from 'NullPointerExceptionTest$B.getBfromB()LNullPointerExceptionTest$B;'");
        }
        a.to_b.to_b.to_c = new C();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB().to_c.to_d is null", e.getMessage(),
                         "while trying to read the field 'ar' of a null object loaded from field 'NullPointerExceptionTest$C.to_d' of an object loaded from field 'NullPointerExceptionTest$B.to_c' of an object");
        }
        a.to_b.to_b.to_c.to_d = new D();
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB().to_c.to_d.ar is null", e.getMessage(),
                         "while trying to load from a null object array loaded from field 'NullPointerExceptionTest$D.ar' of an object loaded from field 'NullPointerExceptionTest$C.to_d' of an object");
        }
        try {
            a.getB().getBfromB().getC().getD().ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().ar[0][0] = 99; // a.getB().getBfromB().getC().getD().ar is null", e.getMessage(),
                         "while trying to load from a null object array loaded from field 'NullPointerExceptionTest$D.ar' of an object returned from 'NullPointerExceptionTest$C.getD()LNullPointerExceptionTest$D;'");
        }
        a.to_b.to_b.to_c.to_d.ar = new int[1][];
        try {
            a.getB().getBfromB().to_c.to_d.ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().to_c.to_d.ar[0][0] = 99; // a.getB().getBfromB().to_c.to_d.ar[0] is null", e.getMessage(),
                         "while trying to store to a null int array loaded from an array (which itself was loaded from field 'NullPointerExceptionTest$D.ar' of an object) with an index loaded from a constant");
        }
        try {
            a.getB().getBfromB().getC().getD().ar[0][0] = 99;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("a.getB().getBfromB().getC().getD().ar[0][0] = 99; // a.getB().getBfromB().getC().getD().ar[0] is null", e.getMessage(),
                         "while trying to store to a null int array loaded from an array (which itself was loaded from field 'NullPointerExceptionTest$D.ar' of an object) with an index loaded from a constant");
        }
    }


    // Test we get the same message calling npe.getMessage() twice.
    public void testSameMessage() throws Exception {
        Object null_o = null;
        String expectedMsg =
            "while trying to invoke the method 'java.lang.Object.hashCode()I'" +
            " on a null reference loaded from " +
            (hasDebugInfo ? "local variable 'null_o'" : "a local variable at slot 1");

        try {
            null_o.hashCode();
            Asserts.fail();
        } catch (NullPointerException npe) {
            String msg1 = npe.getMessage();
            checkMessage("null_o.hashCode()", msg1, expectedMsg);
            String msg2 = npe.getMessage();
            Asserts.assertTrue(msg1.equals(msg2));
            // It was decided that getMessage should generate the
            // message anew on every call, so this does not hold any more.
            Asserts.assertFalse(msg1 == msg2);
        }
    }

    /**
     *
     */
    public void testCreationViaNew() {
        Asserts.assertNull(new NullPointerException().getMessage());
    }

    /**
     * @throws Exception
     */
    public void testCreationViaReflection() throws Exception {
        Exception ex = NullPointerException.class.getDeclaredConstructor().newInstance();
        Asserts.assertNull(ex.getMessage());
    }

    /**
     * @throws Exception
     */
    public void testCreationViaSerialization() throws Exception {
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
            checkMessage("null_o3.hashCode()", msg3, "while trying to invoke the method 'java.lang.Object.hashCode()I'" +
                                 " on a null reference loaded from " +
                                 (hasDebugInfo ? "local variable 'null_o3'" : "a local variable at slot 14"));
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

    /**
     *
     */
    @SuppressWarnings("null")
    public void testLoadedFromLocalVariable1() {
        Object o = null;

        try {
            o.hashCode();
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("o.hashCode()", e.getMessage(), "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference loaded from " + (hasDebugInfo ? "local variable 'o'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    @SuppressWarnings("null")
    public void testLoadedFromLocalVariable2() {
        Exception[] myVariable = null;

        try {
            Asserts.assertNull(myVariable[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("myVariable[0]", e.getMessage(), "while trying to load from a null object array loaded from " + (hasDebugInfo ? "local variable 'myVariable'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    @SuppressWarnings("null")
    public void testLoadedFromLocalVariable3() {
        Exception[] myVariable = null;

        try {
            myVariable[0] = null;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("myVariable[0] = null", e.getMessage(), "while trying to store to a null object array loaded from " + (hasDebugInfo ? "local variable 'myVariable'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    @SuppressWarnings("null")
    public void testLoadedFromLocalVariable4() {
        Exception[] myVariable\u0096 = null;

        try {
            Asserts.assertTrue(myVariable\u0096.length == 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("myVariable\u0096.length", e.getMessage(), "while trying to get the length of a null array loaded from " + (hasDebugInfo ? "local variable 'myVariable'" : "a local variable at slot 1"));
        }
    }

    /**
     * @throws Exception
     */
    @SuppressWarnings("null")
    public void testLoadedFromLocalVariable5() throws Exception {
        Exception myException = null;

        try {
            throw myException;
        } catch (NullPointerException e) {
            checkMessage("throw myException", e.getMessage(), "while trying to throw a null exception object loaded from " + (hasDebugInfo ? "local variable 'myException'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    @SuppressWarnings("null")
    public void testLoadedFromLocalVariable6() {
        byte[] myVariable = null;
        int my_index = 1;

        try {
            Asserts.assertTrue(myVariable[my_index] == 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("myVariable[my_index]", e.getMessage(), "while trying to load from a null byte (or boolean) array loaded from " + (hasDebugInfo ? "local variable 'myVariable'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    @SuppressWarnings("null")
    public void testLoadedFromLocalVariable7() {
        byte[] myVariable = null;

        try {
            myVariable[System.out.hashCode()] = (byte) 0;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("myVariable[System.out.hashCode()]", e.getMessage(), "while trying to store to a null byte (or boolean) array loaded from " + (hasDebugInfo ? "local variable 'myVariable'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    public void testLoadedFromMethod1() {
        try {
            Asserts.assertTrue(((char[]) NullPointerGenerator.nullReturner(false))[0] == 'A');
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((char[]) NullPointerGenerator.nullReturner(false))[0]", e.getMessage(), "while trying to load from a null char array returned from 'NullPointerExceptionTest$NullPointerGenerator.nullReturner(Z)Ljava/lang/Object;'");
        }
    }

    /**
     *
     */
    public void testLoadedFromMethod2() {
        try {
            Asserts.assertTrue(((char[]) (new NullPointerGenerator().returnMyNull(1, 1, (short) 1)))[0] == 'a');
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((char[]) (new NullPointerGenerator().returnMyNull(1, 1, (short) 1)))[0]", e.getMessage(), "while trying to load from a null char array returned from 'NullPointerExceptionTest$NullPointerGenerator.returnMyNull(DJS)Ljava/lang/Object;'");
        }
    }

    /**
     *
     */
    public void testLoadedFromMethod3() {
        try {
            Asserts.assertTrue(((double[]) returnNull(null, null, 1f))[0] == 1.0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((double[]) returnNull(null, null, 1f))[0] ", e.getMessage(), "while trying to load from a null double array returned from 'NullPointerExceptionTest.returnNull([[Ljava/lang/String;[[[IF)Ljava/lang/Object;'");
        }
    }

    /**
     *
     */
    public void testLoadedFromMethod4() {
        ImplTestLoadedFromMethod4(new DoubleArrayGenImpl());
    }

    /**
     * @param gen
     */
    public void ImplTestLoadedFromMethod4(DoubleArrayGen gen) {
        try {
            (gen.getArray())[0] = 1.0;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("(gen.getArray())[0]", e.getMessage(), "while trying to store to a null double array returned from 'NullPointerExceptionTest$DoubleArrayGen.getArray()[D'");
        }
    }

    /**
     *
     */
    public void testLoadedFromMethod5() {
        try {
            returnMeAsNull(null, 1, 'A').dag = null;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("returnMeAsNull(null, 1, 'A').dag = null", e.getMessage(), "while trying to write the field 'NullPointerExceptionTest.dag' of a null object returned from 'NullPointerExceptionTest.returnMeAsNull(Ljava/lang/Throwable;IC)LNullPointerExceptionTest;'");
        }
        /*
        try {
            returnMeAsNull(null, 1, 'A').dag.dag = null;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("returnMeAsNull(null, 1, 'A').dag.dag = null", e.getMessage(), "while trying to write the field 'NullPointerExceptionTest.dag' of a null object returned from 'NullPointerExceptionTest.returnMeAsNull(Ljava/lang/Throwable;IC)LNullPointerExceptionTest;'");
        }
        */
    }

    /**
     *
     */
    @SuppressWarnings("null")
    public void testLoadedFromMethod6() {
        short[] sa = null;

        try {
            Asserts.assertTrue(sa[0] == (short) 1);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("sa[0]", e.getMessage(), "while trying to load from a null short array loaded from " + (hasDebugInfo ? "local variable 'sa'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    @SuppressWarnings("null")
    public void testLoadedFromMethod7() {
        short[] sa = null;

        try {
            sa[0] = 1;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("sa[0] = 1", e.getMessage(), "while trying to store to a null short array loaded from " + (hasDebugInfo ? "local variable 'sa'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    public void testLoadedFromStaticField1() {
        try {
            Asserts.assertTrue(((float[]) nullStaticField)[0] == 1.0f);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((float[]) nullStaticField)[0]", e.getMessage(), "while trying to load from a null float array loaded from static field 'NullPointerExceptionTest.nullStaticField'");
        }
    }

    /**
     *
     */
    public void testLoadedFromStaticField2() {
        try {
            ((float[]) nullStaticField)[0] = 1.0f;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("((float[]) nullStaticField)[0] = 1.0f", e.getMessage(), "while trying to store to a null float array loaded from static field 'NullPointerExceptionTest.nullStaticField'");
        }
    }

    /**
     *
     */
    public void testLoadedFromStaticField3() {
        try {
            Asserts.assertTrue(staticArray[0][0][0][0] == 1);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("staticArray[0][0][0][0] // staticArray[0][0][0] is null.", e.getMessage(), "while trying to load from a null int array loaded from an array (which itself was loaded from an array) with an index loaded from a constant");
        }
    }

    /**
     *
     */
    public void testLoadedFromStaticField4(int myIdx, int pos) {
        try {
            staticArray[0][0][pos][myIdx] = 2;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage(" staticArray[0][0][pos][myIdx] = 2", e.getMessage(), "while trying to store to a null int array loaded from an array (which itself was loaded from an array) with an index loaded from " + (hasDebugInfo ? "local variable 'pos'" : "the parameter nr. 2 of the method"));
        }
    }

    /**
     *
     */
    public void testLoadedFromStaticField5() {
        try {
            Asserts.assertTrue(staticLongArray[0][0] == 1L);
        } catch (NullPointerException e) {
            checkMessage("staticLongArray[0][0]", e.getMessage(), "while trying to load from a null long array loaded from an array (which itself was loaded from static field 'NullPointerExceptionTest.staticLongArray') with an index loaded from a constant");
        }
    }

    /**
     * Test bipush for index.
     */
    public void testLoadedFromStaticField5a() {
        try {
            Asserts.assertTrue(staticLongArray[139 /*0x77*/][0] == 1L);
        } catch (NullPointerException e) {
            checkMessage("staticLongArray[139][0]", e.getMessage(), "while trying to load from a null long array loaded from an array (which itself was loaded from static field 'NullPointerExceptionTest.staticLongArray') with an index loaded from a constant");
        }
    }

    /**
     * Test sipush for index.
     */
    public void testLoadedFromStaticField5b() {
        try {
            Asserts.assertTrue(staticLongArray[819 /*0x333*/][0] == 1L);
        } catch (NullPointerException e) {
            checkMessage("staticLongArray[819][0]",  e.getMessage(), "while trying to load from a null long array loaded from an array (which itself was loaded from static field 'NullPointerExceptionTest.staticLongArray') with an index loaded from a constant");
        }
    }

    /**
     *
     */
    public void testLoadedFromStaticField6() {
        try {
            staticLongArray[0][0] = 2L;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("staticLongArray[0][0] = 2L", e.getMessage(), "while trying to store to a null long array loaded from an array (which itself was loaded from static field 'NullPointerExceptionTest.staticLongArray') with an index loaded from a constant");
        }
    }

    /**
     *
     */
    public void testLoadedFromInstanceField1() {
        try {
            Asserts.assertTrue(this.nullInstanceField.nullInstanceField == null);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("this.nullInstanceField.nullInstanceField", e.getMessage(), "while trying to read the field 'nullInstanceField' of a null object loaded from field 'NullPointerExceptionTest.nullInstanceField' of an object loaded from 'this'");
        }
    }

    /**
     *
     */
    public void testLoadedFromInstanceField2() {
        try {
            this.nullInstanceField.nullInstanceField = null;
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("this.nullInstanceField.nullInstanceField = null", e.getMessage(), "while trying to write the field 'NullPointerExceptionTest.nullInstanceField' of a null object loaded from field 'NullPointerExceptionTest.nullInstanceField' of an object loaded from 'this'");
        }
    }

    /**
     *
     */
    public void testLoadedFromInstanceField3() {
        NullPointerExceptionTest obj = this;

        try {
            Asserts.assertNull(obj.dag.getArray().clone());
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("obj.dag.getArray().clone()", e.getMessage(), "while trying to invoke the method 'NullPointerExceptionTest$DoubleArrayGen.getArray()[D' on a null reference loaded from field 'NullPointerExceptionTest.dag' of an object loaded from " + (hasDebugInfo ? "local variable 'obj'" : "a local variable at slot 1"));
        }
    }

    /**
     *
     */
    public void testLoadedFromInstanceField4() {
        int indexes[] = new int[1];

        NullPointerExceptionTest[] objs = new NullPointerExceptionTest[] {this};

        try {
            Asserts.assertNull(objs[indexes[0]].nullInstanceField.returnNull(null, null, 1f));
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("objs[indexes[0]].nullInstanceField.returnNull(null, null, 1f", e.getMessage(), "while trying to invoke the method 'NullPointerExceptionTest.returnNull([[Ljava/lang/String;[[[IF)Ljava/lang/Object;' on a null reference loaded from field 'NullPointerExceptionTest.nullInstanceField' of an object loaded from an array");
        }
    }

    /**
     *
     */
    public void testLoadedFromInstanceField5() {
        int indexes[] = new int[1];

        NullPointerExceptionTest[] objs = new NullPointerExceptionTest[] {this};

        try {
            Asserts.assertNull(objs[indexes[0]].nullInstanceField.toString().toCharArray().clone());
        } catch (NullPointerException e) {
            checkMessage("objs[indexes[0]].nullInstanceField.toString().toCharArray().clone()", e.getMessage(), "while trying to invoke the method 'java.lang.Object.toString()Ljava/lang/String;' on a null reference loaded from field 'NullPointerExceptionTest.nullInstanceField' of an object loaded from an array");
        }
    }

    /**
     *
     */
    public void testLoadedFromInstanceField6() {
        int indexes[] = new int[1];

        NullPointerExceptionTest[][] objs =
            new NullPointerExceptionTest[][] {new NullPointerExceptionTest[] {this}};

        try {
            // Check monitorenter only, since we cannot probe monitorexit from Java.
            synchronized (objs[indexes[0]][0].nullInstanceField) {
                Asserts.fail();
            }
        } catch (NullPointerException e) {
            checkMessage("synchronized (objs[indexes[0]][0].nullInstanceField)", e.getMessage(), "while trying to enter a null monitor loaded from field 'NullPointerExceptionTest.nullInstanceField' of an object loaded from an array");
        }
    }

    /**
     * @throws ClassNotFoundException
     */
    public void testInNative() throws ClassNotFoundException {
        try {
            Class.forName(null);
            Asserts.fail();
        } catch (NullPointerException e) {
            Asserts.assertNull(e.getMessage());
        }
    }

    private Object returnNull(String[][] dummy1, int[][][] dummy2, float dummy3) {
        return null;
    }

    private NullPointerExceptionTest returnMeAsNull(Throwable dummy1, int dummy2, char dummy3){
        return null;
    }

    static interface DoubleArrayGen {
        public double[] getArray();
    }

    static class DoubleArrayGenImpl implements DoubleArrayGen {
        @Override
        public double[] getArray() {
            return null;
        }
    }

    static class NullPointerGenerator {
        public static Object nullReturner(boolean dummy1) {
            return null;
        }

        public Object returnMyNull(double dummy1, long dummy2, short dummy3) {
            return null;
        }
    }

    /**
     *
     */
    public void testMissingLocalVariableTable() {
        doTestMissingLocalVariableTable(names);

        String[] expectedHasDebugInfoGoodNames = new String[] {
            "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference " +
                "loaded from field 'NullPointerExceptionTest.nullInstanceField' " +
                "of an object loaded from 'this'",
            "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference loaded from " +
                "local variable 'a1'",
            "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference loaded from " +
                "local variable 'o1'",
            "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference loaded from " +
                "local variable 'aa1'"
        };

        String[] expectedNoDebugInfoGoodNames = new String[] {
            "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference " +
                "loaded from field 'NullPointerExceptionTest.nullInstanceField' " +
                "of an object loaded from 'this'",
            "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference loaded from " +
                "the parameter nr. 5 of the method",
            "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference loaded from " +
                "the parameter nr. 2 of the method",
            "while trying to invoke the method 'java.lang.Object.hashCode()I' on a null reference loaded from " +
                "the parameter nr. 9 of the method"
        };

        String[] expectedNames;
        if (hasDebugInfo) {
            expectedNames = expectedHasDebugInfoGoodNames;
        } else {
            expectedNames = expectedNoDebugInfoGoodNames;
        }

        // The two lists of messages should have the same length.
        Asserts.assertEquals(names.size(), expectedNames.length);

        for (int i = 0; i < expectedNames.length; ++i) {
            // GLGLGL not for now Asserts.assertEquals(names.get(i), expectedNames[i]);
        }
    }

    private void doTestMissingLocalVariableTable(ArrayList<String> names) {
        curr = names;
        doTestMissingLocalVariableTable1();
        doTestMissingLocalVariableTable2(-1, null, false, 0.0, null, 0.1f, (byte) 0, (short) 0, null);
    }

    private void doTestMissingLocalVariableTable1() {
        try {
            this.nullInstanceField.hashCode();
            Asserts.fail();
        } catch (NullPointerException e) {
            curr.add(e.getMessage());
        }
    }

    private void doTestMissingLocalVariableTable2(long l1, Object o1, boolean z1, double d1, Object[] a1,
            float f1, byte b1, short s1, Object[][] aa1) {
        try {
            a1.hashCode();
            Asserts.fail();
        }
        catch (NullPointerException e) {
            curr.add(e.getMessage());
        }

        try {
            o1.hashCode();
            Asserts.fail();
        }
        catch (NullPointerException e) {
            curr.add(e.getMessage());
        }

        try {
            aa1.hashCode();
            Asserts.fail();
        }
        catch (NullPointerException e) {
            curr.add(e.getMessage());
        }
    }

    /**
     *
     */
    @SuppressWarnings("null")
    public void testNullMessages() {
        boolean[] za1 = null;
        byte[] ba1 = null;
        short[] sa1 = null;
        char[] ca1 = null;
        int[] ia1 = null;
        long[] la1 = null;
        float[] fa1 = null;
        double[] da1 = null;
        Object[] oa1 = null;

        Object[][] oa2 = new Object[2][];
        oa2[1] = oa1;

        try {
            System.out.println(oa2[1][0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("oa2[1][0]", e.getMessage(),
                                 "while trying to load from a null object array loaded from an array " +
                                 "(which itself was loaded from " +
                                 (hasDebugInfo ? "local variable 'oa2'" : "a local variable at slot 10") + ") " +
                                 "with an index loaded from a constant");
        }


        try {
            System.out.println(za1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("za1[0]", e.getMessage(),
                                 "while trying to load from a null byte (or boolean) array loaded from " +
                                 (hasDebugInfo ? "local variable 'za1'" : "a local variable at slot 1"));
        }

        try {
            System.out.println(ba1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ba1[0]", e.getMessage(),
                                 "while trying to load from a null byte (or boolean) array loaded from " +
                                 (hasDebugInfo ? "local variable 'ba1'" : "a local variable at slot 2"));
        }

        try {
            System.out.println(sa1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("sa1[0]", e.getMessage(),
                                 "while trying to load from a null short array loaded from " +
                                 (hasDebugInfo ? "local variable 'sa1'" : "a local variable at slot 3"));
        }

        try {
            System.out.println(ca1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ca1[0]", e.getMessage(),
                                 "while trying to load from a null char array loaded from " +
                                 (hasDebugInfo ? "local variable 'ca1'" : "a local variable at slot 4"));
        }

        try {
            System.out.println(ia1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ia1[0]", e.getMessage(),
                                 "while trying to load from a null int array loaded from " +
                                 (hasDebugInfo ? "local variable 'ia1'" : "a local variable at slot 5"));
        }

        try {
            System.out.println(la1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("la1[0]", e.getMessage(),
                                 "while trying to load from a null long array loaded from " +
                                 (hasDebugInfo ? "local variable 'la1'" : "a local variable at slot 6"));
        }

        try {
            System.out.println(fa1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("fa1[0]", e.getMessage(),
                                 "while trying to load from a null float array loaded from " +
                                 (hasDebugInfo ? "local variable 'fa1'" : "a local variable at slot 7"));
        }

        try {
            System.out.println(da1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("da1[0]", e.getMessage(),
                                 "while trying to load from a null double array loaded from " +
                                 (hasDebugInfo ? "local variable 'da1'" : "a local variable at slot 8"));
        }

        try {
            System.out.println(oa1[0]);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("oa1[0]", e.getMessage(),
                                 "while trying to load from a null object array loaded from " +
                                 (hasDebugInfo ? "local variable 'oa1'" : "a local variable at slot 9"));
        }

        try {
            System.out.println(za1[0] = false);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("za1[0] = false", e.getMessage(),
                                 "while trying to store to a null byte (or boolean) array loaded from " +
                                 (hasDebugInfo ? "local variable 'za1'" : "a local variable at slot 1"));
        }

        try {
            System.out.println(ba1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ba1[0] = 0", e.getMessage(),
                                 "while trying to store to a null byte (or boolean) array loaded from " +
                                 (hasDebugInfo ? "local variable 'ba1'" : "a local variable at slot 2"));
        }

        try {
            System.out.println(sa1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("sa1[0] = 0", e.getMessage(),
                                 "while trying to store to a null short array loaded from " +
                                 (hasDebugInfo ? "local variable 'sa1'" : "a local variable at slot 3"));
        }

        try {
            System.out.println(ca1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ca1[0] = 0", e.getMessage(),
                                 "while trying to store to a null char array loaded from " +
                                 (hasDebugInfo ? "local variable 'ca1'" : "a local variable at slot 4"));
        }

        try {
            System.out.println(ia1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("ia1[0] = 0", e.getMessage(),
                                 "while trying to store to a null int array loaded from " +
                                 (hasDebugInfo ? "local variable 'ia1'" : "a local variable at slot 5"));
        }

        try {
            System.out.println(la1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("la1[0] = 0", e.getMessage(),
                                 "while trying to store to a null long array loaded from " +
                                 (hasDebugInfo ? "local variable 'la1'" : "a local variable at slot 6"));
        }

        try {
            System.out.println(fa1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("fa1[0] = 0", e.getMessage(),
                                 "while trying to store to a null float array loaded from " +
                                 (hasDebugInfo ? "local variable 'fa1'" : "a local variable at slot 7"));
        }

        try {
            System.out.println(da1[0] = 0);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("da1[0] = 0", e.getMessage(),
                                 "while trying to store to a null double array loaded from " +
                                 (hasDebugInfo ? "local variable 'da1'" : "a local variable at slot 8"));
        }

        try {
            System.out.println(oa1[0] = null);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("oa1[0] = null", e.getMessage(),
                                 "while trying to store to a null object array loaded from " +
                                 (hasDebugInfo ? "local variable 'oa1'" : "a local variable at slot 9"));
        }

        try {
            System.out.println(nullInstanceField.nullInstanceField);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("nullInstanceField.nullInstanceField", e.getMessage(),
                                 "while trying to read the field 'nullInstanceField' of a null object loaded " +
                                 "from field 'NullPointerExceptionTest.nullInstanceField' of an object " +
                                 "loaded from 'this'");
        }

        try {
            System.out.println(nullInstanceField.nullInstanceField = null);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("nullInstanceField.nullInstanceField = null", e.getMessage(),
                                 "while trying to write the field 'NullPointerExceptionTest.nullInstanceField' " +
                                 "of a null object loaded from field 'NullPointerExceptionTest.nullInstanceField' " +
                                 "of an object loaded from 'this'");
        }

        try {
            System.out.println(za1.length);
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("za1.length", e.getMessage(),
                                 "while trying to get the length of a null array loaded from " +
                                 (hasDebugInfo ? "local variable 'za1'" : "a local variable at slot 1"));
        }

        try {
            throw null;
        } catch (NullPointerException e) {
            checkMessage("throw null", e.getMessage(),
                                 "while trying to throw a null exception object loaded " +
                                 "from a constant");
        }

        try {
            synchronized (nullInstanceField) {
                // desired
            }
        } catch (NullPointerException e) {
            checkMessage("synchronized (nullInstanceField)", e.getMessage(),
                                 "while trying to enter a null monitor loaded from field " +
                                 "'NullPointerExceptionTest.nullInstanceField' of an object loaded from " +
                                 "'this'");
        }

        try {
            nullInstanceField.testCreationViaNew();
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("nullInstanceField.testCreationViaNew()", e.getMessage(),
                                 "while trying to invoke the method 'NullPointerExceptionTest.testCreationViaNew()V' on a null reference " +
                                 "loaded from field 'NullPointerExceptionTest.nullInstanceField' of an " +
                                 "object loaded from 'this'");
        }

        try {
            nullInstanceField.testNullMessages();
            Asserts.fail();
        } catch (NullPointerException e) {
            checkMessage("nullInstanceField.testNullMessages()", e.getMessage(),
                                 "while trying to invoke the method 'NullPointerExceptionTest.testNullMessages()V' on a null reference " +
                                 "loaded from field 'NullPointerExceptionTest.nullInstanceField' of an " +
                                 "object loaded from 'this'");
        }

        try {
            // If we can get the value from more than one bci, we cannot know which one.
            (Math.random() < 0.5 ? oa1 : (new Object[1])[0]).equals("");
        } catch (NullPointerException e) {
            checkMessage("(Math.random() < 0.5 ? oa1 : (new Object[1])[0]).equals(\"\")", e.getMessage(),
                                 "while trying to invoke the method 'java.lang.Object.equals(Ljava/lang/Object;)Z' on a null reference");
        }
    }
}
