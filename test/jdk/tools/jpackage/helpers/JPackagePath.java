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
import java.nio.file.Path;

/**
 * Helper class which contains functions to get different system
 * dependent paths used by tests
 */
public class JPackagePath {

    // Path to Windows "Program Files" folder
    // Probably better to figure this out programattically
    private static final String WIN_PROGRAM_FILES = "C:\\Program Files";

    // Path to Windows Start menu items
    private static final String WIN_START_MENU =
            "C:\\ProgramData\\Microsoft\\Windows\\Start Menu\\Programs";

    // Path to Windows public desktop location
    private static final String WIN_PUBLIC_DESKTOP =
            "C:\\Users\\Public\\Desktop";

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
        return getApp("test");
    }

    public static String getApp(String name) {
        if (JPackageHelper.isWindows()) {
            return Path.of("output", name, "bin", name + ".exe").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", name + ".app",
                    "Contents", "MacOS", name).toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", name, "bin", name).toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to generate test application icon
    public static String getAppIcon() {
        return getAppIcon("test");
    }

    public static String getAppIcon(String name) {
        if (JPackageHelper.isWindows()) {
            return Path.of("output", name, "bin", name + ".ico").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", name + ".app",
                    "Contents", "Resources", name + ".icns").toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", name, "bin", name + ".png").toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to generate secondary launcher of given application
    public static String getAppSL(String sl) {
        return getAppSL("test", sl);
    }

    public static String getAppSL(String app, String sl) {
        if (JPackageHelper.isWindows()) {
            return Path.of("output", app, "bin", sl + ".exe").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", app + ".app",
                    "Contents", "MacOS", sl).toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", app, "bin", sl).toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path to test application cfg file
    public static String getAppCfg() {
        return getAppCfg("test");
    }

    public static String getAppCfg(String name) {
         if (JPackageHelper.isWindows()) {
            return Path.of("output", name, "app", name + ".cfg").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", name + ".app",
                    "Contents", "Java", name + ".cfg").toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", name, "app", name + ".cfg").toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    // Returns path including executable to java in image runtime folder
    public static String getRuntimeJava() {
        return getRuntimeJava("test");
    }

    public static String getRuntimeJava(String name) {
        if (JPackageHelper.isWindows()) {
            return Path.of("output", name,
                    "runtime", "bin", "java.exe").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", name + ".app", "Contents",
                    "runtime", "Contents", "Home", "bin", "java").toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", name,
                    "runtime", "bin", "java").toString();
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
        return getRuntimeBin("test");
    }

    public static String getRuntimeBin(String name) {
        if (JPackageHelper.isWindows()) {
            return Path.of("output", name, "runtime", "bin").toString();
        } else if (JPackageHelper.isOSX()) {
            return Path.of("output", name + ".app",
                    "Contents", "runtime",
                    "Contents", "Home", "bin").toString();
        } else if (JPackageHelper.isLinux()) {
            return Path.of("output", name, "runtime", "bin").toString();
        } else {
            throw new AssertionError("Cannot detect platform");
        }
    }

    public static String getWinProgramFiles() {
        return WIN_PROGRAM_FILES;
    }

    public static String getWinUserLocal() {
        return Path.of(System.getProperty("user.home"),
                "AppData", "Local").toString();
    }

    public static String getWinStartMenu() {
        return WIN_START_MENU;
    }

    public static String getWinPublicDesktop() {
        return WIN_PUBLIC_DESKTOP;
    }

    public static String getWinUserLocalStartMenu() {
        return Path.of(System.getProperty("user.home"), "AppData", "Roaming",
                "Microsoft", "Windows", "Start Menu", "Programs").toString();
    }

    public static String getWinInstalledApp(String testName) {
        return Path.of(getWinProgramFiles(), testName,
                testName + ".exe").toString();
    }

    public static String getWinInstalledApp(String installDir,
            String testName) {
        return Path.of(getWinProgramFiles(), installDir, "bin",
                testName + ".exe").toString();
    }

    public static String getOSXInstalledApp(String testName) {
        return File.separator + "Applications"
                + File.separator + testName + ".app"
                + File.separator + "Contents"
                + File.separator + "MacOS"
                + File.separator + testName;
    }

    public static String getLinuxInstalledApp(String testName) {
        return File.separator + "opt"
                + File.separator + testName
                + File.separator + testName;
    }

    public static String getOSXInstalledApp(String subDir, String testName) {
        return File.separator + "Applications"
                + File.separator + subDir
                + File.separator + testName + ".app"
                + File.separator + "Contents"
                + File.separator + "MacOS"
                + File.separator + testName;
    }

    public static String getLinuxInstalledApp(String subDir, String testName) {
        return File.separator + "opt"
                + File.separator + subDir
                + File.separator + testName
                + File.separator + testName;
    }

    public static String getWinInstallFolder(String testName) {
        return getWinProgramFiles()
                + File.separator + testName;
    }

    public static String getLinuxInstallFolder(String testName) {
        return File.separator + "opt"
                + File.separator + testName;
    }

    public static String getLinuxInstallFolder(String subDir, String testName) {
        if (testName == null) {
            return File.separator + "opt"
                    + File.separator + subDir;
        } else {
            return File.separator + "opt"
                    + File.separator + subDir
                    + File.separator + testName;
        }
    }

    public static String getWinUserLocalInstalledApp(String testName) {
        return getWinUserLocal()
                + File.separator + testName
                + File.separator + "bin"
                + File.separator + testName + ".exe";
    }

    public static String getWinUserLocalInstallFolder(String testName) {
        return getWinUserLocal() + File.separator + testName;
    }

    // Returs path to test license file
    public static String getLicenseFilePath() {
        String path = JPackagePath.getTestSrcRoot()
                + File.separator + "resources"
                + File.separator + "license.txt";

        return path;
    }

    // Returns path to app folder of installed application
    public static String getWinInstalledAppFolder(String testName) {
        return getWinProgramFiles()
                + File.separator + testName
                + File.separator + "app";
    }
}
