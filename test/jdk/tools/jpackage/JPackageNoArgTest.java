/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @summary jpackage no argument test
 * @library helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m JPackageNoArgTest
 */
public class JPackageNoArgTest {

    private static final String RESULT1 = "Usage: jpackage <mode> <options>";
    private static final String[] EXPECTED =
            {"--help", "list of possible options"};

    private static void validate(String output) throws Exception {
        String[] result = JPackageHelper.splitAndFilter(output);
        if (result.length != 2) {
            System.err.println(output);
            throw new AssertionError(
                    "Invalid number of lines in output: " + result.length);
        }

        if (!result[0].trim().equals(RESULT1)) {
            System.err.println("Expected: " + RESULT1);
            System.err.println("Actual: " + result[0]);
            throw new AssertionError("Unexpected line 1");
        }

        for (String expected : EXPECTED) {
            if (!result[1].contains(expected)) {
                System.err.println("Expected to contain: " + expected);
                System.err.println("Actual: " + result[1]);
                throw new AssertionError("Unexpected line 2");
            }
        }
    }

    private static void testNoArg() throws Exception {
        String output = JPackageHelper.executeCLI(true, new String[0]);
        validate(output);
    }

    private static void testNoArgToolProvider() throws Exception {
        String output =
                JPackageHelper.executeToolProvider(true, new String[0]);
        validate(output);
    }

    public static void main(String[] args) throws Exception {
        testNoArg();
        testNoArgToolProvider();
    }

}
