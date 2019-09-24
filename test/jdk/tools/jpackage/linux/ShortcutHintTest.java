/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Map;
import java.nio.file.Path;
import jdk.jpackage.test.FileAssociations;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Test;

/**
 * Test --linux-shortcut parameter. Output of the test should be
 * shortcuthinttest_1.0-1_amd64.deb or shortcuthinttest-1.0-1.amd64.rpm package
 * bundle. The output package should provide the same functionality as the
 * default package and also create a desktop shortcut.
 *
 * Finding a shortcut of the application launcher through GUI depends on desktop
 * environment.
 *
 * deb:
 * Search online for `Ways To Open A Ubuntu Application` for instructions.
 *
 * rpm:
 *
 */

/*
 * @test
 * @summary jpackage with --linux-shortcut
 * @library ../helpers
 * @requires (os.family == "linux")
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm/timeout=360 -Xmx512m ShortcutHintTest
 * @run main/othervm/timeout=360 -Xmx512m ShortcutHintTest testCustomIcon
 * @run main/othervm/timeout=360 -Xmx512m ShortcutHintTest testFileAssociations
 * @run main/othervm/timeout=360 -Xmx512m ShortcutHintTest testAdditionaltLaunchers
 */
public class ShortcutHintTest {

    public static void main(String[] args) {
        Test.run(args, () -> {
            if (args.length != 0) {
                Test.getTestClass().getDeclaredMethod(args[0]).invoke(null);
                return;
            }

            createTest(null).addInitializer(cmd -> {
                cmd.addArgument("--linux-shortcut");
            }).run();
        });
    }

    private static PackageTest createTest(String name) {
        PackageTest reply = new PackageTest()
                .forTypes(PackageType.LINUX)
                .configureHelloApp()
                .addBundleDesktopIntegrationVerifier(true);
        if (name != null) {
            reply.addInitializer(cmd -> cmd.setArgumentValue("--name",
                    String.format("%s8%s", Test.getTestClass().getSimpleName(),
                            name)));
        }
        return reply;
    }

    /**
     * Adding `--icon` to jpackage command line should create desktop shortcut
     * even though `--linux-shortcut` is omitted.
     */
    static void testCustomIcon() {
        createTest(new Object() {
        }.getClass().getEnclosingMethod().getName()).addInitializer(cmd -> {
            cmd.setFakeRuntime();
            cmd.addArguments("--icon", Test.TEST_SRC_ROOT.resolve(
                    "apps/dukeplug.png"));
        }).run();
    }

    /**
     * Adding `--file-associations` to jpackage command line should create
     * desktop shortcut even though `--linux-shortcut` is omitted.
     */
    static void testFileAssociations() {
        createTest(new Object() {
        }.getClass().getEnclosingMethod().getName()).addInitializer(cmd -> {
            cmd.setFakeRuntime();

            FileAssociations fa = new FileAssociations(
                    "ShortcutHintTest_testFileAssociations");
            fa.createFile();
            cmd.addArguments("--file-associations", fa.getPropertiesFile());
        }).run();
    }

    /**
     * Additional launcher with icon should create desktop shortcut even though
     * `--linux-shortcut` is omitted.
     */
    static void testAdditionaltLaunchers() {
        createTest(new Object() {
        }.getClass().getEnclosingMethod().getName()).addInitializer(cmd -> {
            cmd.setFakeRuntime();

            final String launcherName = "Foo";
            final Path propsFile = Test.workDir().resolve(
                    launcherName + ".properties");

            cmd.addArguments("--add-launcher", String.format("%s=%s",
                    launcherName, propsFile));

            Test.createPropertiesFile(propsFile, Map.entry("icon",
                    Test.TEST_SRC_ROOT.resolve("apps/dukeplug.png").toString()));
        }).run();
    }
}
