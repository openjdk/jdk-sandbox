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
 * @summary jpackager create image to verify --force
 * @library ../helpers
 * @build JPackagerHelper
 * @build JPackagerPath
 * @modules jdk.jpackager
 * @run main/othervm -Xmx512m JPackagerCreateImageForceTest
 */
public class JPackagerCreateImageForceTest {
    private static final String app = JPackagerPath.getApp();
    private static final String appOutput = JPackagerPath.getAppOutputFile();
    private static final String appWorkingDir = JPackagerPath.getAppWorkingDir();

    private static final String[] CMD = {
        "create-image",
        "--input", "input",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--files", "hello.jar",
        "--output", "TBD"};

    private static final String[] CMD_FORCE = {
        "create-image",
        "--input", "input",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--force",
        "--files", "hello.jar",
        "--output", "TBD"};

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

    private static void validate(String result) throws Exception {
        if (!result.contains("java.io.IOException") &&
                !result.contains("already exists") &&
                !result.contains("--force is not specified")) {
            System.err.println(result);
            throw new AssertionError("Unexpected error message");
        }
    }

    private static void validateForce() throws Exception {
        int retVal = JPackagerHelper.execute(null, app);
        if (retVal != 0) {
            throw new AssertionError(
                   "Test application exited with error: " + retVal);
        }

        File outfile = new File(appWorkingDir + File.separator + appOutput);
        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");
        validateResult(result);
    }

    private static void testForce() throws Exception {
        CMD[CMD.length - 1] = "output";
        CMD_FORCE[CMD_FORCE.length - 1] = "output";

        String appFolder = "test";
        if (JPackagerHelper.isOSX()) {
            appFolder = "test.app";
        }

        File output = new File("output" + File.separator + appFolder);
        if (output.exists()) {
            throw new AssertionError(
                   "Output folder already exist");
        }
        output.mkdirs();

        String result = JPackagerHelper.executeCLI(false, CMD);
        validate(result);

        JPackagerHelper.executeCLI(true, CMD_FORCE);
        validateForce();
    }

    private static void testForceToolProvider() throws Exception {
        CMD[CMD.length - 1] = "outputToolProvider";
        CMD_FORCE[CMD_FORCE.length - 1] = "outputToolProvider";

        String appFolder = "test";
        if (JPackagerHelper.isOSX()) {
            appFolder = "test.app";
        }

        File output = new File("outputToolProvider" + File.separator + appFolder);
        if (output.exists()) {
            throw new AssertionError(
                   "Output folder already exist");
        }
        output.mkdirs();

        String result = JPackagerHelper.executeToolProvider(false, CMD);
        validate(result);

        JPackagerHelper.executeToolProvider(true, CMD_FORCE);
        validateForce();
    }

    public static void main(String[] args) throws Exception {
        JPackagerHelper.createHelloJar();
        testForce();
        testForceToolProvider();
    }

}
