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

/**
 * Helper class which contains functions to get different system dependent paths used by tests
 */
public class JPackagePath {

    // Return path to test src adjusted to location of caller
    public static String getTestSrc() {
        return JPackageHelper.TEST_SRC;
    }

    // Returns path to generate test application
    public static String getApp() {
        if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "test" + File.separator + "test.exe";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app" + File.separator + "Contents"
                    + File.separator + "MacOS" + File.separator + "test";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test" + File.separator + "test";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to generate test application icon
    public static String getAppIcon() {
        if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "test" + File.separator + "test.ico";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app" + File.separator + "Contents"
                    + File.separator + "Resources" + File.separator + "test.icns";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test" + File.separator
                    + File.separator + "resources"+ File.separator + "test.png";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to generate test application without --name argument
    public static String getAppNoName() {
        if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "Hello" + File.separator + "Hello.exe";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "Hello.app" + File.separator + "Contents"
                    + File.separator + "MacOS" + File.separator + "Hello";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "Hello" + File.separator + "Hello";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to generate secondary launcher of test application
    public static String getAppSL() {
        if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "test" + File.separator + "test2.exe";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app" + File.separator + "Contents"
                    + File.separator + "MacOS" + File.separator + "test2";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test" + File.separator + "test2";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to app working directory (where test application generates its output)
    public static String getAppWorkingDir() {
         if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "test" + File.separator + "app";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app" + File.separator + "Contents"
                    + File.separator + "Java";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test" + File.separator + "app";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to test application cfg file
    public static String getAppCfg() {
         if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "test" + File.separator + "app" + File.separator
                    + "test.cfg";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app" + File.separator + "Contents"
                    + File.separator + "Java" + File.separator + "test.cfg";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test" + File.separator + "app" + File.separator
                    + "test.cfg";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to app working directory without --name (where test application generates its output)
    public static String getAppWorkingDirNoName() {
         if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "Hello" + File.separator + "app";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "Hello.app" + File.separator + "Contents"
                    + File.separator + "Java";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "Hello" + File.separator + "app";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path including executable to java in image runtime folder
    public static String getRuntimeJava() {
        if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "test" + File.separator + "runtime" + File.separator
                    + "bin" + File.separator + "java.exe";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app" + File.separator
                    + "Contents" + File.separator + "PlugIns" + File.separator
                    + "Java.runtime" + File.separator + "Contents" + File.separator
                    + "Home" + File.separator + "bin" + File.separator + "java";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test" + File.separator + "runtime" + File.separator
                    + "bin" + File.separator + "java";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns output file name generate by test application
    public static String getAppOutputFile() {
        return "appOutput.txt";
    }

    // Returns path to bin folder in image runtime
    public static String getRuntimeBin() {
        if (JPackageHelper.isWindows()) {
            return "output" + File.separator + "test" + File.separator + "runtime"
                    + File.separator + "bin";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app" + File.separator + "Contents"
                    + File.separator + "PlugIns" + File.separator + "Java.runtime"
                    + File.separator + "Contents" + File.separator + "Home" + File.separator
                    + "bin";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test" + File.separator + "runtime"
                    + File.separator + "bin";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }
}
