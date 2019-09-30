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

import java.nio.file.Files;
import jdk.jpackage.internal.ApplicationLayout;
import java.nio.file.Path;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.Functional;
import jdk.jpackage.test.JPackageCommand;

/*
 * @test
 * @summary jpackage create image to verify --icon
 * @library ../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm -Xmx512m IconTest
 */
public class IconTest {
    public static void main(String[] args) {
        TKit.run(args, () -> {
            JPackageCommand cmd = JPackageCommand.helloAppImage().addArguments("--icon", GOLDEN_ICON);
            cmd.useToolProvider(true).executeAndAssertHelloAppImageCreated();

            Path iconPath = ApplicationLayout.platformAppImage().resolveAt(
                    cmd.appImage()).destktopIntegrationDirectory().resolve(
                    cmd.launcherPathInAppImage().getFileName().toString().replaceAll(
                            "\\.[^.]*$", "") + ICON_SUFFIX);

            TKit.assertFileExists(iconPath);
            TKit.assertTrue(-1 == Files.mismatch(GOLDEN_ICON, iconPath),
                    String.format(
                            "Check application icon file [%s] is a copy of source icon file [%s]",
                            iconPath, GOLDEN_ICON));
        });
    }

    private final static String ICON_SUFFIX = Functional.identity(() -> {
        if (TKit.isOSX()) {
            return ".icns";
        }

        if (TKit.isLinux()) {
            return ".png";
        }

        if (TKit.isWindows()) {
            return ".ico";
        }

        throw TKit.throwUnknownPlatformError();
    }).get();

    private final static Path GOLDEN_ICON = TKit.TEST_SRC_ROOT.resolve(Path.of(
            "resources", "icon" + ICON_SUFFIX));
}
