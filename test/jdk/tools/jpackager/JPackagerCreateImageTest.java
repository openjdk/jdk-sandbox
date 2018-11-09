/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;

 /*
 * @test
 * @summary jpackager create image test
 * @build JPackagerHelper
 * @modules jdk.jpackager
 * @run main/othervm -Xmx512m JPackagerCreateImageTest
 */
public class JPackagerCreateImageTest {
    private static final String app;
    private static final String appOutput = "appOutput.txt";
    private static final String appOutputPath;
    private static final String MSG = "jpackager test application";
    private static final String [] CMD = {
        "create-image",
        "--input", "input",
        "--output", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--force",
        "--files", "hello.jar"};

    static {
        if (JPackagerHelper.isWindows()) {
            app = "output" + File.separator + "test"
                    + File.separator + "test.exe";
            appOutputPath = "output" + File.separator + "test"
                    + File.separator + "app";
        } else if (JPackagerHelper.isOSX()) {
            app = "output" + File.separator + "test.app" + File.separator
                    + "Contents" + File.separator
                    + "MacOS" + File.separator + "test";
            appOutputPath = "output" + File.separator + "test.app"
                    + File.separator + "Contents" + File.separator + "Java";
        } else {
            app = "output" + File.separator + "test" + File.separator + "test";
            appOutputPath = "output" + File.separator + "test"
                    + File.separator + "app";
        }
    }

    private static void validateResult(String[] result) throws Exception {
        if (result.length != 2) {
            throw new AssertionError(
                   "Unexpected number of lines: " + result.length);
        }

        if (!result[0].trim().equals("jpackager test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: 0")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }
    }

    private static void validate() throws Exception {
        int retVal = JPackagerHelper.execute(null, app);
        if (retVal != 0) {
            throw new AssertionError(
                   "Test application exited with error: " + retVal);
        }

        File outfile = new File(appOutputPath + File.separator + appOutput);
        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");
        validateResult(result);
    }

    private static void testCreateImage() throws Exception {
        JPackagerHelper.executeCLI(true, CMD);
        validate();
    }

    private static void testCreateImageToolProvider() throws Exception {
        JPackagerHelper.executeToolProvider(true, CMD);
        validate();
    }

    public static void main(String[] args) throws Exception {
        JPackagerHelper.createHelloJar();
        testCreateImage();
        testCreateImageToolProvider();
    }

}
