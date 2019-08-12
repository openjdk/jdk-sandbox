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

import java.io.IOException;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.FileInputStream;

/*
 * @test
 * @summary jpackage create image win console test
 * @library ../helpers
 * @library ../share
 * @build JPackageHelper
 * @build JPackagePath
 * @build Base
 * @requires (os.family == "windows")
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m WinConsoleTest
 */

public class WinConsoleTest {
    private static final String NAME = "test";
    private static final String OUTPUT = "output";
    private static final String OUTPUT_WIN_CONSOLE = "outputWinConsole";
    private static final int BUFFER_SIZE = 512;
    private static final int GUI_SUBSYSTEM = 2;
    private static final int CONSOLE_SUBSYSTEM = 3;

    private static final String [] CMD = {
        "--input", "input",
        "--output", OUTPUT,
        "--name", NAME,
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
    };

    private static final String [] CMD_WIN_CONSOLE = {
        "--input", "input",
        "--output", OUTPUT_WIN_CONSOLE,
        "--name", NAME,
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--win-console"
    };

    private static void checkSubsystem(boolean console) throws Exception {
        Path path = Path.of(console ? OUTPUT_WIN_CONSOLE : OUTPUT,
                NAME, NAME + ".exe");

        System.out.println("validate path: " + path.toString());
        Base.validate(path.toString());

        try (InputStream inputStream = new FileInputStream(path.toString())) {
            byte [] bytes = new byte[BUFFER_SIZE];
            if (inputStream.read(bytes) != BUFFER_SIZE) {
                throw new AssertionError("Wrong number of bytes read");
            }

            // Check PE header for console or Win GUI app.
            // https://docs.microsoft.com/en-us/windows/desktop/api/winnt/ns-winnt-_image_nt_headers
            for (int i = 0;  i < (bytes.length - 4); i++) {
                if (bytes[i] == 0x50 && bytes[i + 1] == 0x45 &&
                        bytes[i + 2] == 0x0 && bytes[i + 3] == 0x0) {

                    // Signature, File Header and subsystem offset.
                    i = i + 4 + 20 + 68;
                    byte subsystem = bytes[i];
                    if (console) {
                        if (subsystem != CONSOLE_SUBSYSTEM) {
                            throw new AssertionError("Unexpected subsystem: "
                                    + subsystem);
                        } else {
                            return;
                        }
                    } else {
                        if (subsystem != GUI_SUBSYSTEM) {
                            throw new AssertionError("Unexpected subsystem: "
                                    + subsystem);
                        } else {
                            return;
                        }
                    }
                }
            }
        }

        throw new AssertionError("Unable to verify PE header");
    }

    private static void validate() throws Exception {
        checkSubsystem(false);
        checkSubsystem(true);
    }

    private static void testCreateAppImage(String [] cmd) throws Exception {
        JPackageHelper.executeCLI(true, cmd);
    }

    private static void testCreateAppImageToolProvider(String [] cmd)
                throws Exception {
        JPackageHelper.executeToolProvider(true, cmd);
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        testCreateAppImage(CMD);
        testCreateAppImage(CMD_WIN_CONSOLE);
        validate();
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.deleteOutputFolder(OUTPUT_WIN_CONSOLE);
        testCreateAppImageToolProvider(CMD);
        testCreateAppImageToolProvider(CMD_WIN_CONSOLE);
        validate();
    }
}
