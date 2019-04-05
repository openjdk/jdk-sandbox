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

import java.awt.Desktop;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class JPackageCreateInstallerFileAssociationsBase {

    private static String TEST_NAME;
    private static String EXT;
    private static String TEST_EXT;
    private static String OUTPUT;
    private static String[] CMD;

    private static void copyResults() throws Exception {
        List<String> files = new ArrayList<>();
        files.add(OUTPUT.toLowerCase());
        JPackageInstallerHelper.copyTestResults(files);
    }

    private static void testCreateInstaller() throws Exception {
        JPackageHelper.executeCLI(true, CMD);
        JPackageInstallerHelper.validateOutput(OUTPUT);
        copyResults();
    }

    private static void validateAppOutput() throws Exception {
        File outFile = new File("appOutput.txt");
        int count = 10;
        boolean bOutputCreated = false;
        while (count > 0) {
            if (!outFile.exists()) {
                Thread.sleep(3000);
                count--;
            } else {
                bOutputCreated = true;
                break;
            }
        }

        if (!bOutputCreated) {
            throw new AssertionError(outFile.getAbsolutePath() + " was not created");
        }

        String output = Files.readString(outFile.toPath());
        String[] result = output.split("\n");
        if (result.length != 3) {
            System.err.println(output);
            throw new AssertionError(
                    "Unexpected number of lines: " + result.length);
        }

        if (!result[0].trim().equals("jpackage test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: 1")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }

        File faFile = new File(TEST_NAME + "." + TEST_EXT);
        if (!result[2].trim().equals(faFile.getAbsolutePath())) {
            throw new AssertionError("Unexpected result[2]: " + result[2]);
        }
    }

    private static void verifyInstall() throws Exception {
        createFileAssociationsTestFile();
        Desktop.getDesktop().open(new File(TEST_NAME + "." + TEST_EXT));
        validateAppOutput();
    }

    private static void verifyUnInstall() throws Exception {
        String folderPath = JPackagePath.getLinuxInstallFolder(TEST_NAME);
        File folder = new File(folderPath);
        if (folder.exists()) {
            throw new AssertionError("Error: " + folder.getAbsolutePath() + " exist");
        }
    }

    private static void createFileAssociationsTestFile() throws Exception {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(
                new FileWriter(TEST_NAME + "." + TEST_EXT)))) {
            out.println(TEST_NAME);
        }
    }

    private static void createFileAssociationsProperties() throws Exception {
        try (PrintWriter out = new PrintWriter(new BufferedWriter(
                new FileWriter("fa.properties")))) {
            out.println("extension=" + TEST_EXT);
            out.println("mime-type=application/" + TEST_EXT);
            out.println("description=jpackage test extention");
        }
    }

    private static void init(String name, String ext) {
        TEST_NAME = name;
        EXT = ext;
        TEST_EXT = "jptest1";
        if (EXT.equals("rpm")) {
            OUTPUT = "output" + File.separator + TEST_NAME + "-1.0-1.x86_64." + EXT;
        } else {
            OUTPUT = "output" + File.separator + TEST_NAME + "-1.0." + EXT;
        }
        CMD = new String[]{
            "create-installer",
            "--installer-type", EXT,
            "--input", "input",
            "--output", "output",
            "--name", TEST_NAME,
            "--main-jar", "hello.jar",
            "--main-class", "Hello",
            "--file-associations", "fa.properties"};
    }

    public static void run(String name, String ext) throws Exception {
        init(name, ext);

        if (JPackageInstallerHelper.isVerifyInstall()) {
            verifyInstall();
        } else if (JPackageInstallerHelper.isVerifyUnInstall()) {
            verifyUnInstall();
        } else {
            JPackageHelper.createHelloInstallerJar();
            createFileAssociationsProperties();
            testCreateInstaller();
        }
    }
}
