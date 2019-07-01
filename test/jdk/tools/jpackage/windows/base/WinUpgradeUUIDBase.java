/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

public class WinUpgradeUUIDBase {

    private static String TEST_NAME;
    private static String EXT;
    private static String OUTPUT_1;
    private static String[] CMD_1;
    private static String OUTPUT_2;
    private static String[] CMD_2;
    private static final String FILE_1 = "file1.txt";
    private static final String FILE_2 = "file2.txt";

    private static void copyResults() throws Exception {
        List<String> files = new ArrayList<>();
        files.add(OUTPUT_1);
        files.add(OUTPUT_2);
        JPackageInstallerHelper.copyTestResults(files);
    }

    private static void testCreateInstaller() throws Exception {
        JPackageHelper.executeCLI(true, CMD_1);
        JPackageInstallerHelper.validateOutput(OUTPUT_1);
        JPackageHelper.executeCLI(true, CMD_2);
        JPackageInstallerHelper.validateOutput(OUTPUT_2);
        copyResults();
    }

    private static void verifyInstall() throws Exception {
        String app = JPackagePath.getWinInstalledApp(TEST_NAME);
        JPackageInstallerHelper.validateApp(app);

        String file1Path = JPackagePath.getWinInstalledAppFolder(TEST_NAME) + File.separator + FILE_1;
        File file1 = new File(file1Path);
        if (EXT.equals("msi")) {
            if (file1.exists()) {
                throw new AssertionError("Unexpected file does exist: "
                        + file1.getAbsolutePath());
            }
        } else if (EXT.equals("exe")) {
            if (!file1.exists()) {
                throw new AssertionError("Unexpected file does not exist: "
                    + file1.getAbsolutePath());
            }
        } else {
            throw new AssertionError("Unknown installer type: " + EXT);
        }

        String file2Path = JPackagePath.getWinInstalledAppFolder(TEST_NAME) + File.separator + FILE_2;
        File file2 = new File(file2Path);
        if (!file2.exists()) {
            throw new AssertionError("Unexpected file does not exist: "
                    + file2.getAbsolutePath());
        }
    }

    private static void verifyUnInstall() throws Exception {
        String folderPath = JPackagePath.getWinInstallFolder(TEST_NAME);
        File folder = new File(folderPath);
        if (folder.exists()) {
            throw new AssertionError("Error: " + folder.getAbsolutePath() + " exist");
        }
    }

    private static void init(String name, String ext) {
        TEST_NAME = name;
        EXT = ext;
        OUTPUT_1 = "output" + File.separator + TEST_NAME + "-1.0." + EXT;
        CMD_1 = new String[]{
            "--package-type", EXT,
            "--input", "input",
            "--output", "output",
            "--name", TEST_NAME,
            "--main-jar", "hello.jar",
            "--main-class", "Hello",
            "--app-version", "1.0",
            "--win-upgrade-uuid", "F0B18E75-52AD-41A2-BC86-6BE4FCD50BEB"};
        OUTPUT_2 = "output" + File.separator + TEST_NAME + "-2.0." + EXT;
        CMD_2 = new String[]{
            "--package-type", EXT,
            "--input", "input",
            "--output", "output",
            "--name", TEST_NAME,
            "--main-jar", "hello.jar",
            "--main-class", "Hello",
            "--app-version", "2.0",
            "--win-upgrade-uuid", "F0B18E75-52AD-41A2-BC86-6BE4FCD50BEB"};
    }

    private static void createInputFile(String name, String context) throws Exception {
        try (PrintWriter out = new PrintWriter(
                new BufferedWriter(new FileWriter("input" + File.separator + name)))) {
            out.println(context);
        }
    }

    public static void run(String name, String ext) throws Exception {
        init(name, ext);

        if (JPackageInstallerHelper.isVerifyInstall()) {
            verifyInstall();
        } else if (JPackageInstallerHelper.isVerifyUnInstall()) {
            verifyUnInstall();
        } else {
            JPackageHelper.createHelloInstallerJar();
            createInputFile(FILE_1, FILE_1);
            createInputFile(FILE_2, FILE_2);
            testCreateInstaller();
        }
    }
}
