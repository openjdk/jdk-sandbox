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

 public class JPackagerCreateImageRuntimeBase {
    private static final String app = JPackagerPath.getApp();
    private static final String appWorkingDir = JPackagerPath.getAppWorkingDir();
    private static final String runtimeJava = JPackagerPath.getRuntimeJava();
    private static final String runtimeJavaOutput = "javaOutput.txt";
    private static final String appOutput = JPackagerPath.getAppOutputFile();

    private static void validateResult(String[] result) throws Exception {
        if (result.length != 2) {
            throw new AssertionError("Unexpected number of lines: " + result.length);
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
            throw new AssertionError("Test application exited with error: " + retVal);
        }

        File outfile = new File(appWorkingDir + File.separator + appOutput);
        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");
        validateResult(result);
    }

    private static void validateRuntime() throws Exception {
        int retVal = JPackagerHelper.execute(new File(runtimeJavaOutput), runtimeJava, "--list-modules");
        if (retVal != 0) {
            throw new AssertionError("Test application exited with error: " + retVal);
        }

        File outfile = new File(runtimeJavaOutput);
        if (!outfile.exists()) {
            throw new AssertionError(runtimeJavaOutput + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");
        if (result.length != 1) {
            throw new AssertionError("Unexpected number of lines: " + result.length);
        }

        if (!result[0].startsWith("java.base")) {
            throw new AssertionError("Unexpected result: " + result[0]);
        }
    }

    public static void testCreateImage(String [] cmd) throws Exception {
        JPackagerHelper.executeCLI(true, cmd);
        validate();
        validateRuntime();
    }

    public static void testCreateImageToolProvider(String [] cmd) throws Exception {
        JPackagerHelper.executeToolProvider(true, cmd);
        validate();
        validateRuntime();
    }

}
