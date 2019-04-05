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
 * @summary jpackage create image verbose test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m JPackageCreateAppImageVerboseTest
 */
public class JPackageCreateAppImageVerboseTest {
    private static final String OUTPUT = "output";
    private static final String[] CMD = {
        "create-app-image",
        "--input", "input",
        "--output", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
    };

    private static final String[] CMD_VERBOSE = {
        "create-app-image",
        "--input", "input",
        "--output", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--verbose"};

    private static void validate(String result, String resultVerbose)
            throws Exception {
        String[] r = result.split("\n");
        String[] rv = resultVerbose.split("\n");

        if (r.length >= rv.length) {
            System.err.println("r.length: " + r.length);
            System.err.println(result);
            System.err.println("rv.length: " + rv.length);
            System.err.println(resultVerbose);
            throw new AssertionError(
                    "non-verbose output is less or equal to verbose output");
        }
    }

    private static void testCreateAppImage() throws Exception {
        String result = JPackageHelper.executeCLI(true, CMD);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        String resultVerbose = JPackageHelper.executeCLI(true, CMD_VERBOSE);
        validate(result, resultVerbose);
    }

    private static void testCreateAppImageToolProvider() throws Exception {
        JPackageHelper.deleteOutputFolder(OUTPUT);
        String result = JPackageHelper.executeToolProvider(true, CMD);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        String resultVerbose =
                JPackageHelper.executeToolProvider(true, CMD_VERBOSE);
        validate(result, resultVerbose);
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        testCreateAppImage();
        testCreateAppImageToolProvider();
    }

}
