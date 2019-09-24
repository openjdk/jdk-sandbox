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
 * @summary jpackage create image using version from main module
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m AppVersionModuleTest
 */
public class AppVersionModuleTest {
    private static final String OUTPUT = "output";
    private static final String appCfg = JPackagePath.getAppCfg();
    private static final String MODULE_VERSION = "2.7";
    private static final String CLI_VERSION = "3.5";

    private static final String[] CMD_MODULE_VERSION = {
        "--package-type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--module", "com.hello/com.hello.Hello",
        "--module-path", "input",
   };

    private static final String[] CMD_CLI_VERSION = {
        "--package-type", "app-image",
        "--input", "input",
        "--dest", OUTPUT,
        "--name", "test",
        "--module", "com.hello/com.hello.Hello",
        "--module-path", "input",
        "--app-version", CLI_VERSION};

    private static void validate(String version)
            throws Exception {
        File outfile = new File(appCfg);
        if (!outfile.exists()) {
            throw new AssertionError(appCfg + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        if (version == null) {
            version = MODULE_VERSION;
        }

        String expected = "app.version=" + version;
        if (!output.contains(expected)) {
            System.err.println("Expected: " + expected);
            throw new AssertionError("Cannot find expected entry in config file");
        }
    }

    private static void testVersion() throws Exception {
        JPackageHelper.executeCLI(true, CMD_MODULE_VERSION);
        validate(null);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeCLI(true, CMD_CLI_VERSION);
        validate(CLI_VERSION);
    }

    private static void testVersionToolProvider() throws Exception {
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeToolProvider(true, CMD_MODULE_VERSION);
        validate(null);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.executeToolProvider(true, CMD_CLI_VERSION);
        validate(CLI_VERSION);
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloModule(MODULE_VERSION);
        testVersion();
        testVersionToolProvider();
    }

}
