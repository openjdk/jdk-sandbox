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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

 /*
 * @test
 * @summary jpackage create image input/files test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m JPackageCreateImageInputFilesTest
 */
public class JPackageCreateImageInputFilesTest {
    private static final String OUTPUT = "output";
    private static final String inputFile =
            "input" + File.separator + "input.txt";
    private static final String jarFile =
            "input" + File.separator + "hello.jar";
    private static final String appInputFilePath;
    private static final String appJarFilePath;

    static {
        appInputFilePath = JPackagePath.getAppWorkingDir() + File.separator + "input.txt";
        appJarFilePath = JPackagePath.getAppWorkingDir() + File.separator + "hello.jar";
    }

    private static final String [] CMD_1 = {
        "create-image",
        "--input", "input",
        "--output", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello"};

    private static final String [] CMD_2 = {
        "create-image",
        "--input", "input",
        "--output", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--files", "hello.jar"};

    private static void validate1() throws Exception {
        File input = new File(appInputFilePath);
        if (!input.exists()) {
            throw new AssertionError("Unexpected file does not exist: "
                    + input.getAbsolutePath());
        }

        File jar = new File(appJarFilePath);
        if (!jar.exists()) {
            throw new AssertionError("Unexpected file does not exist: "
                    + jar.getAbsolutePath());
        }
    }

    private static void validate2() throws Exception {
        File input = new File(appInputFilePath);
        if (input.exists()) {
            throw new AssertionError("Unexpected file exist: "
                    + input.getAbsolutePath());
        }

        File jar = new File(appJarFilePath);
        if (!jar.exists()) {
            throw new AssertionError("Unexpected file does not exist: "
                    + jar.getAbsolutePath());
        }
    }

    private static void testCreateImage() throws Exception {
        JPackageHelper.executeCLI(true, CMD_1);
        validate1();

        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeCLI(true, CMD_2);
        validate2();
    }

    private static void testCreateImageToolProvider() throws Exception {
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeToolProvider(true, CMD_1);
        validate1();

        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeToolProvider(true, CMD_2);
        validate2();
    }

    private static void createInputFile() throws Exception {
        try (PrintWriter out = new PrintWriter(
                new BufferedWriter(new FileWriter(inputFile)))) {
            out.println("jpackgaer resource file");
        }
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();

        createInputFile();

        testCreateImage();
        testCreateImageToolProvider();
    }

}
