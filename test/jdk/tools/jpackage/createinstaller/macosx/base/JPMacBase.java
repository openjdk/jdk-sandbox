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
import java.util.ArrayList;
import java.util.List;

public class JPMacBase {

    private static String TEST_NAME;
    private static String EXT;
    private static String OUTPUT;
    private static String[] CMD;

    private static void copyResults() throws Exception {
        List<String> files = new ArrayList<>();
        files.add(OUTPUT);
        JPackageInstallerHelper.copyTestResults(files);
    }

    private static void testCreateInstaller() throws Exception {
        JPackageHelper.executeCLI(true, CMD);
        JPackageInstallerHelper.validateOutput(OUTPUT);
        copyResults();
    }

    private static void verifyInstall() throws Exception {
        String app = JPackagePath.getOSXInstalledApp(TEST_NAME);
        JPackageInstallerHelper.validateApp(app);
    }

    private static void verifyUnInstall() throws Exception {
        // Not needed on OS X, since we just deleting installed application
        // without using generated installer. We keeping this for consistnency
        // between platforms.
    }

    private static void init(String name, String ext) {
        TEST_NAME = name;
        EXT = ext;
        OUTPUT = "output" + File.separator + TEST_NAME + "-1.0." + EXT;
        CMD = new String[]{
            "--package-type", EXT,
            "--input", "input",
            "--output", "output",
            "--name", TEST_NAME,
            "--main-jar", "hello.jar",
            "--main-class", "Hello"};
    }

    public static void run(String name, String ext) throws Exception {
        init(name, ext);

        if (JPackageInstallerHelper.isVerifyInstall()) {
            verifyInstall();
        } else if (JPackageInstallerHelper.isVerifyUnInstall()) {
            verifyUnInstall();
        } else {
            JPackageHelper.createHelloInstallerJar();
            testCreateInstaller();
        }
    }
}
