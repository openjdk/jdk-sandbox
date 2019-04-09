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

import java.io.File;
import java.nio.file.Files;

/*
 * @test
 * @summary jpackage create image with no main class arguments and with main-class attribute
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m JPackageCreateAppImageModuleMainClassErrorTest
 */
public class JPackageCreateAppImageModuleMainClassErrorTest {
    private static final String OUTPUT = "output";
    private static final String app = JPackagePath.getApp();
    private static final String appOutput = JPackagePath.getAppOutputFile();
    private static final String appWorkingDir = JPackagePath.getAppWorkingDir();

    private static final String [] CMD = {
        "create-app-image",
        "--output", OUTPUT,
        "--name", "test",
        "--module", "com.hello",
        "--module-path", "input"};

    private static void validate(String buildOutput) throws Exception {

        File outfile = new File(appWorkingDir + File.separator + appOutput);
        int retVal = JPackageHelper.execute(outfile, app);
        if (retVal == 0) {
            throw new AssertionError(
                   "Test application exited without error: ");
        }

        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }
        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");

        if (result.length != 1) {
            System.out.println("outfile (" + outfile + ") content: " + output);
            throw new AssertionError(
                   "Unexpected number of lines: " + result.length);
        }

        if (!result[0].trim().contains(
                "does not have a ModuleMainClass attribute")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloModule();

        JPackageHelper.deleteOutputFolder(OUTPUT);
        validate(JPackageHelper.executeCLI(true, CMD));

        JPackageHelper.deleteOutputFolder(OUTPUT);
        validate(JPackageHelper.executeToolProvider(true, CMD));
    }

}
