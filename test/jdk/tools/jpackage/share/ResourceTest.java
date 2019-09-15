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
 * @summary jpackage create image to verify --icon
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @requires ((os.family == "windows") | (os.family == "mac"))
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m ResourceTest
 */
public class ResourceTest {
    private static final String OUTPUT = "output";
    private static final String app = JPackagePath.getApp("icon");
    private static final String appOutput = JPackagePath.getAppOutputFile();
    private static final String resourceDir =
            JPackagePath.getTestSrcRoot() + File.separator + "resources";

    private static final String[] CMD = {
        "--package-type", "app-image",
        "--input", "input",
        "--name", "icon",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--resource-dir", resourceDir,
        "--dest", OUTPUT};

    private static void validateResult(String[] result) throws Exception {
        if (result.length != 2) {
            throw new AssertionError(
                   "Unexpected number of lines: " + result.length);
        }

        if (!result[0].trim().equals("jpackage test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: 0")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }
    }

    private static void validate() throws Exception {
        int retVal = JPackageHelper.execute(null, app);
        if (retVal != 0) {
            throw new AssertionError(
                   "Test application exited with error: " + retVal);
        }

        File outfile = new File(appOutput);
        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");
        validateResult(result);
    }

    private static void validateIcon() throws Exception {
        File origIcon = new File(getIconPath());
        File icon = new File(JPackagePath.getAppIcon("icon"));
        if (origIcon.length() != icon.length()) {
            System.err.println("file: " + origIcon + " - origIcon.length(): "
                    + origIcon.length());
            System.err.println("file: " + icon + " - icon.length(): "
                    + icon.length());
            throw new AssertionError("Icons size does not match");
        }
    }

    private static void testIcon() throws Exception {
        JPackageHelper.executeCLI(true, CMD);
        validate();
        validateIcon();
    }

    private static void testIconToolProvider() throws Exception {
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeToolProvider(true, CMD);
        validate();
        validateIcon();
    }

    private static String getResourcenPath() {
        return JPackagePath.getTestSrcRoot() + File.separator + "resources";
    }

    private static String getIconPath() {
        String ext = ".ico";
        if (JPackageHelper.isOSX()) {
            ext = ".icns";
        } else if (JPackageHelper.isLinux()) {
            ext = ".png";
        }
        return resourceDir + File.separator + "icon" + ext;
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        testIcon();
        testIconToolProvider();
    }
}
