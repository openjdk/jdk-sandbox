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

/**
 * Helper class which contains functions to get different system dependent paths used by tests
 */
public class JPackagePath {

    // Path to Windows "Program Files" folder
    // Probably better to figure this out programattically
    private static final String WIN_PROGRAM_FILES = "C:\\Program Files";

    // Path to Windows Start menu items
    private static final String WIN_START_MENU = "C:\\ProgramData\\Microsoft\\Windows\\Start Menu\\Programs";

    // Path to Windows public desktop location
    private static final String WIN_PUBLIC_DESKTOP = "C:\\Users\\Public\\Desktop";

    // Return path to test src adjusted to location of caller
    public static String getTestSrcRoot() {
        return JPackageHelper.TEST_SRC_ROOT;
    }

    // Return path to calling test
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
            return "output" + File.separator + "test"
                    + File.separator + "runtime" + File.separator
                    + "bin" + File.separator + "java.exe";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app" + File.separator
                    + "Contents" + File.separator
                    + "runtime" + File.separator + "Contents" + File.separator
                    + "Home" + File.separator + "bin" + File.separator + "java";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test"
                    + File.separator + "runtime" + File.separator
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
            return "output" + File.separator + "test"
                    + File.separator + "runtime" + File.separator + "bin";
        } else if (JPackageHelper.isOSX()) {
            return "output" + File.separator + "test.app"
                    + File.separator + "Contents"
                    + File.separator + "runtime"
                    + File.separator + "Contents"
                    + File.separator + "Home" + File.separator + "bin";
        } else if (JPackageHelper.isLinux()) {
            return "output" + File.separator + "test"
                    + File.separator + "runtime" + File.separator + "bin";
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    public static String getWinProgramFiles() {
        return WIN_PROGRAM_FILES;
    }

    public static String getWinUserLocal() {
        return System.getProperty("user.home") + File.separator + "AppData"
                 + File.separator + "Local";
    }

    public static String getWinStartMenu() {
        return WIN_START_MENU;
    }

    public static String getWinPublicDesktop() {
        return WIN_PUBLIC_DESKTOP;
    }

    public static String getWinUserLocalStartMenu() {
        return System.getProperty("user.home") + File.separator + "AppData"
                + File.separator + "Roaming" + File.separator + "Microsoft"
                + File.separator + "Windows" + File.separator + "Start Menu"
                + File.separator + "Programs";

    }

    public static String getWinInstalledApp(String testName) {
        return getWinProgramFiles() + File.separator + testName + File.separator
                + testName + ".exe";
    }

    public static String getWinInstalledApp(String installDir, String testName) {
        return getWinProgramFiles() + File.separator + installDir + File.separator
                + testName + ".exe";
    }

    public static String getOSXInstalledApp(String testName) {
        return File.separator + "Applications" + File.separator + testName
                + ".app" + File.separator + "Contents" + File.separator
                + "MacOS" + File.separator + testName;
    }

    public static String getLinuxInstalledApp(String testName) {
        return File.separator + "opt" + File.separator + testName +
                File.separator + testName;
    }

    public static String getOSXInstalledApp(String subDir, String testName) {
        return File.separator + "Applications" + File.separator + subDir
                + File.separator + testName + ".app" + File.separator
                + "Contents" + File.separator + "MacOS" + File.separator
                + testName;
    }

    public static String getLinuxInstalledApp(String subDir, String testName) {
        return File.separator + "opt" + File.separator + subDir + File.separator
                + testName + File.separator + testName;
    }

    public static String getWinInstallFolder(String testName) {
        return getWinProgramFiles() + File.separator + testName;
    }

    public static String getLinuxInstallFolder(String testName) {
        return File.separator + "opt" + File.separator + testName;
    }

    public static String getLinuxInstallFolder(String subDir, String testName) {
        if (testName == null) {
            return File.separator + "opt" + File.separator + subDir;
        } else {
            return File.separator + "opt" + File.separator + subDir
                    + File.separator + testName;
        }
    }

    public static String getWinUserLocalInstalledApp(String testName) {
        return getWinUserLocal() + File.separator + testName + File.separator + testName + ".exe";
    }

    public static String getWinUserLocalInstallFolder(String testName) {
        return getWinUserLocal() + File.separator + testName;
    }

    // Returs path to test license file
    public static String getLicenseFilePath() {
        String path = JPackagePath.getTestSrcRoot() + File.separator + "resources"
                + File.separator + "license.txt";

        return path;
    }

    // Returns path to app folder of installed application
    public static String getWinInstalledAppFolder(String testName) {
        return getWinProgramFiles() + File.separator + testName + File.separator
                + "app";
    }
}
