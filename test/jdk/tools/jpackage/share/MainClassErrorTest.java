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
 * @run main/othervm -Xmx512m MainClassErrorTest
 */
public class MainClassErrorTest {
    private static final String OUTPUT = "output";
    private static final String app = JPackagePath.getApp();
    private static final String appOutput = JPackagePath.getAppOutputFile();

    private static final String[] CMD = {
        "--package-type", "app-image",
        "--input", "input",
        "--output", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar"};

    private static void validate(String output) throws Exception {
        String[] result = JPackageHelper.splitAndFilter(output);
        if (result.length != 2) {
            throw new AssertionError(
                   "Unexpected number of lines: " + result.length);
        }

        if (!result[0].trim().contains("main class was not specified")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().startsWith("Advice to fix: ")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();

        JPackageHelper.deleteOutputFolder(OUTPUT);
        validate(JPackageHelper.executeCLI(false, CMD));

        JPackageHelper.deleteOutputFolder(OUTPUT);
        validate(JPackageHelper.executeToolProvider(false, CMD));
    }

}
