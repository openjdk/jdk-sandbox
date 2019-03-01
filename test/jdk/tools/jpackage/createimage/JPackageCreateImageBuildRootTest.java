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

 /*
 * @test
 * @requires (os.family == "windows")
 * @summary jpackage create image to test --build-root
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m JPackageCreateImageBuildRootTest
 */
public class JPackageCreateImageBuildRootTest {
    private static final String OUTPUT = "output";
    private static String buildRoot = null;
    private static final String BUILD_ROOT = "buildRoot";
    private static final String BUILD_ROOT_TB = "buildRootToolProvider";

    private static final String [] CMD = {
        "create-image",
        "--input", "input",
        "--output", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--files", "hello.jar" };

    private static final String [] CMD_BUILD_ROOT = {
        "create-image",
        "--input", "input",
        "--output", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--files", "hello.jar",
        "--build-root", "TBD"};

    private static void validate(boolean retain) throws Exception {
        File br = new File(buildRoot);
        if (retain) {
            if (!br.exists()) {
                throw new AssertionError(br.getAbsolutePath() + " does not exist");
            }
        } else {
            if (br.exists()) {
                throw new AssertionError(br.getAbsolutePath() + " exist");
            }
        }
    }

    private static void init(boolean toolProvider) {
        if (toolProvider) {
            buildRoot = BUILD_ROOT_TB;
        } else {
            buildRoot = BUILD_ROOT;
        }

        CMD_BUILD_ROOT[CMD_BUILD_ROOT.length - 1] = buildRoot;
    }

    private static void testBuildRoot() throws Exception {
        init(false);
        JPackageHelper.executeCLI(true, CMD);
        validate(false);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeCLI(true, CMD_BUILD_ROOT);
        validate(true);
    }

    private static void testBuildRootToolProvider() throws Exception {
        init(true);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeToolProvider(true, CMD);
        validate(false);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeToolProvider(true, CMD_BUILD_ROOT);
        validate(true);
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        testBuildRoot();
        testBuildRootToolProvider();
    }

}
