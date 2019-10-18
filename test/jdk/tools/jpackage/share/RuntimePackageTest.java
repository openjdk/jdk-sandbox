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

import java.nio.file.Path;
import java.util.Optional;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.JPackageCommand;

/**
 * Test --runtime-image parameter.
 * Output of the test should be RuntimePackageTest*.* installer.
 * The installer should install Java Runtime without an application.
 * Installation directory should not have "app" subfolder and should not have
 * an application launcher.
 *
 *
 * Windows:
 *
 * Java runtime should be installed in %ProgramFiles%\RuntimePackageTest directory.
 */

/*
 * @test
 * @summary jpackage with --runtime-image
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @comment Temporary disable for Linux and OSX until functionality implemented
 * @requires (os.family != "mac")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm/timeout=720 -Xmx512m RuntimePackageTest
 */
public class RuntimePackageTest {

    public static void main(String[] args) {
        TKit.run(args, () -> {
            new PackageTest()
            .addInitializer(cmd -> {
                cmd.addArguments("--runtime-image", Optional.ofNullable(
                        JPackageCommand.DEFAULT_RUNTIME_IMAGE).orElse(Path.of(
                                System.getProperty("java.home"))));
                // Remove --input parameter from jpackage command line as we don't
                // create input directory in the test and jpackage fails
                // if --input references non existant directory.
                cmd.removeArgumentWithValue("--input");
            })
            .run();
        });
    }
}
