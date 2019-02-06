
import java.io.File;
import java.nio.file.Files;

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

/*
 * @test
 * @summary jpackage create image --app-version test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m JPackageCreateImageVersionTest
 */
public class JPackageCreateImageVersionTest {
    private static final String appCfg = JPackagePath.getAppCfg();
    private static final String VERSION = "2.3";
    private static final String VERSION_DEFAULT = "1.0";

    private static final String[] CMD = {
        "create-image",
        "--input", "input",
        "--output", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--overwrite",
        "--files", "hello.jar"};

    private static final String[] CMD_VERSION = {
        "create-image",
        "--input", "input",
        "--output", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--files", "hello.jar",
        "--overwrite",
        "--app-version", VERSION};

    private static void validate(String version)
            throws Exception {
        File outfile = new File(appCfg);
        if (!outfile.exists()) {
            throw new AssertionError(appCfg + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        if (version == null) {
            version = VERSION_DEFAULT;
        }

        String expected = "app.version=" + version;
        if (!output.contains(expected)) {
            System.err.println("Expected: " + expected);
            throw new AssertionError("Cannot find expected entry in config file");
        }
    }

    private static void testVersion() throws Exception {
        JPackageHelper.executeCLI(true, CMD);
        validate(null);
        JPackageHelper.executeCLI(true, CMD_VERSION);
        validate(VERSION);
    }

    private static void testVersionToolProvider() throws Exception {
        JPackageHelper.executeToolProvider(true, CMD);
        validate(null);
        JPackageHelper.executeToolProvider(true, CMD_VERSION);
        validate(VERSION);
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        testVersion();
        testVersionToolProvider();
    }

}
