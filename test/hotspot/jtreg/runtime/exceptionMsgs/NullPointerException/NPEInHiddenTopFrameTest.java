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
 * @summary Test NullPointerException messages thrown in frames that
 *   are hidden in the backtrace/stackTrace.
 * @library /test/lib
 * @compile -g NPEInHiddenTopFrameTest.java
 * @run main/othervm -XX:-ShowHiddenFrames NPEInHiddenTopFrameTest hidden
 * @run main/othervm -XX:+ShowHiddenFrames NPEInHiddenTopFrameTest visible
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
public class NPEInHiddenTopFrameTest {

    @FunctionalInterface
    private static interface SomeFunctionalInterface {
        String someMethod(String a, String b);
    }

    public static void checkMessage(String expression,
                                    String obtainedMsg, String expectedMsg) {
        System.out.println();
        System.out.println(" source code: " + expression);
        System.out.println("  thrown msg: " + obtainedMsg);
        System.out.println("expected msg: " + expectedMsg);
        if (obtainedMsg == null && expectedMsg == null) return;
        Asserts.assertEquals(obtainedMsg, expectedMsg);
    }

    public static void main(String[] args) throws Exception {
        boolean framesAreHidden = false;
        if (args.length > 0) {
            String arg = (String)args[0];
            if (arg.equals("hidden")) framesAreHidden = true;
        }

        try {
            final SomeFunctionalInterface concatter = String::concat;
            final String nullString = null;
            if (concatter != null) {
                concatter.someMethod(nullString, "validString");
            }
        } catch (NullPointerException e) {
            checkMessage("concatter.someMethod(nullString, \"validString\");", e.getMessage(),
                         framesAreHidden ?
                         // This is the message printed if the wrong method/bci are used.
                         //"'concatter' is null. " +
                         //"Can not invoke method 'NPEInHiddenTopFrameTest$SomeFunctionalInterface.someMethod(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;'." :
                         // With this fix no message is printed.
                         null :
                         // This is the corerct message, but it describes code generated on-the-fly.
                         // There is no debug information in this code.
                         "'<parameter1>' is null. " +
                         "Can not invoke method 'java.lang.String.concat(Ljava/lang/String;)Ljava/lang/String;'." );
        }
    }
}

