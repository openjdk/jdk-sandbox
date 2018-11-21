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

 /*
 * @test
 * @summary jpackager create image to test --build-root
 * @library ../helpers
 * @build JPackagerHelper
 * @build JPackagerPath
 * @modules jdk.jpackager
 * @run main/othervm -Xmx512m JPackagerCreateImageBuildRootTest
 */
public class JPackagerCreateImageBuildRootTest {
    private static String buildRoot = null;
    private static final String BUILD_ROOT = "buildRoot";
    private static final String BUILD_ROOT_TB = "buildRootToolProvider";

    private static final String [] CMD = {
        "create-image",
        "--input", "input",
        "--output", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--files", "hello.jar",
        "--force",
        "--build-root", "TBD"};

    private static final String [] CMD_VERBOSE = {
        "create-image",
        "--input", "input",
        "--output", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--files", "hello.jar",
        "--force",
        "--verbose",
        "--build-root", "TBD"};

    private static void validate(boolean verbose) throws Exception {
        File br = new File(buildRoot);
        if (verbose) {
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

        CMD[CMD.length - 1] = buildRoot;
        CMD_VERBOSE[CMD_VERBOSE.length - 1] = buildRoot;
    }

    private static void testBuildRoot() throws Exception {
        init(false);
        JPackagerHelper.executeCLI(true, CMD);
        validate(false);
        JPackagerHelper.executeCLI(true, CMD_VERBOSE);
        validate(true);
    }

    private static void testBuildRootToolProvider() throws Exception {
        init(true);
        JPackagerHelper.executeToolProvider(true, CMD);
        validate(false);
        JPackagerHelper.executeToolProvider(true, CMD_VERBOSE);
        validate(true);
    }

    public static void main(String[] args) throws Exception {
        JPackagerHelper.createHelloJar();
        testBuildRoot();
        testBuildRootToolProvider();
    }

}
