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
 * @summary jpackage help test
 * @library helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m JPackageHelpTest
 */
public class JPackageHelpTest {

    // Platform specific help messages.
    private static final String WINDOWS_HELP =
            "--win-dir-chooser";
    private static final String OSX_HELP =
            "--mac-bundle-identifier";
    private static final String LINUX_HELP =
            "--linux-bundle-name";

    private static void validate(String output1, String output2)
            throws Exception {
        if (output1.split("\n").length < 25) {
            throw new AssertionError("jpacakger --help failed");
        }

        if (output2.split("\n").length < 25) {
            throw new AssertionError("jpacakger -h failed");
        }

        // Make sure output matches for --help and -h
        if (!output1.equals(output2)) {
            System.err.println("================= --help =================");
            System.err.println(output1);

            System.err.println("=================== -h ===================");
            System.err.println(output2);

            throw new AssertionError(
                    "jpacakger help text does not match between --help and -h");
        }

        if (JPackageHelper.isWindows()) {
            if (!output1.contains(WINDOWS_HELP)) {
                throw new AssertionError(
                  "jpacakger help text missing Windows specific help");
            }

            if (output1.contains(OSX_HELP) || output1.contains(LINUX_HELP)) {
                throw new AssertionError(
                  "jpacakger help text contains other platforms specific help");

            }
        } else if (JPackageHelper.isOSX()) {
            if (!output1.contains(OSX_HELP)) {
                throw new AssertionError(
                  "jpacakger help text missing OS X specific help");
            }

            if (output1.contains(WINDOWS_HELP) ||
                    output1.contains(LINUX_HELP)) {
                throw new AssertionError(
                 "jpacakger help text contains other platforms specific help");
            }
        } else if (JPackageHelper.isLinux()) {
            if (!output1.contains(LINUX_HELP)) {
                throw new AssertionError(
                  "jpacakger help text missing Linux specific help");
            }

            if (output1.contains(OSX_HELP) || output1.contains(WINDOWS_HELP)) {
                throw new AssertionError(
                  "jpacakger help text contains other platforms specific help");
            }
        }
    }

    private static void testHelp() throws Exception {
        String output1 = JPackageHelper.executeCLI(true, "--help");
        String output2 = JPackageHelper.executeCLI(true, "-h");
        validate(output1, output2);
    }

    private static void testHelpToolProvider() throws Exception {
        String output1 = JPackageHelper.executeToolProvider(true, "--help");
        String output2 = JPackageHelper.executeToolProvider(true, "-h");
        validate(output1, output2);
    }

    public static void main(String[] args) throws Exception {
        testHelp();
        testHelpToolProvider();
    }

}
