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

public class JPackageCreateInstallerLicenseBase {

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
        String app = JPackagePath.getWinInstalledApp(TEST_NAME);
        JPackageInstallerHelper.validateApp(app);

        // Validate start menu
        JPackageInstallerHelper.validateStartMenu("Unknown", TEST_NAME, true);
    }

    private static void verifyUnInstall() throws Exception {
        String folderPath = JPackagePath.getWinInstallFolder(TEST_NAME);
        File folder = new File(folderPath);
        if (folder.exists()) {
            throw new AssertionError("Error: " + folder.getAbsolutePath() + " exist");
        }

        // Validate start menu
        JPackageInstallerHelper.validateStartMenu("Unknown", TEST_NAME, false);
    }

    private static void init(String name, String ext) {
        TEST_NAME = name;
        EXT = ext;
        OUTPUT = "output" + File.separator + TEST_NAME + "-1.0." + EXT;
        CMD = new String [] {
        "--package-type", EXT,
        "--input", "input",
        "--output", "output",
        "--name", TEST_NAME,
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--license-file", JPackagePath.getLicenseFilePath()};
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
