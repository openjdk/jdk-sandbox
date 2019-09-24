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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Arrays;
import java.util.function.Function;
import java.util.stream.Collectors;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageType;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.LinuxHelper;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.Test;

/**
 * Test --license-file parameter. Output of the test should be licensetest*.*
 * package bundle. The output package should provide the same functionality as
 * the default package and also incorporate license information from
 * test/jdk/tools/jpackage/resources/license.txt file from OpenJDK repo.
 *
 * deb:
 *
 * Package should install license file /usr/share/doc/licensetest/copyright
 * file.
 *
 * rpm:
 *
 * Package should install license file in
 * %{_defaultlicensedir}/licensetest-1.0/license.txt file.
 *
 * Mac:
 *
 * Windows
 *
 * Installer should display license text matching contents of the license file
 * during installation.
 */

/*
 * @test
 * @summary jpackage with --license-file
 * @library ../helpers
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm/timeout=360 -Xmx512m LicenseTest
 */
public class LicenseTest {
    public static void main(String[] args) {
        Test.run(args, () -> {
            new PackageTest().configureHelloApp()
            .addInitializer(cmd -> {
                cmd.addArguments("--license-file", LICENSE_FILE);
            })
            .forTypes(PackageType.LINUX_DEB)
            .addBundleVerifier(cmd -> {
                verifyLicenseFileInLinuxPackage(cmd, debLicenseFile(cmd));
            })
            .addInstallVerifier(cmd -> {
                verifyLicenseFileInstalledDebian(debLicenseFile(cmd));
            })
            .addUninstallVerifier(cmd -> {
                verifyLicenseFileNotInstalledLinux(debLicenseFile(cmd));
            })
            .forTypes(PackageType.LINUX_RPM)
            .addBundleVerifier(cmd -> {
                verifyLicenseFileInLinuxPackage(cmd,rpmLicenseFile(cmd));
            })
            .addInstallVerifier(cmd -> {
                verifyLicenseFileInstalledRpm(rpmLicenseFile(cmd));
            })
            .addUninstallVerifier(cmd -> {
                verifyLicenseFileNotInstalledLinux(rpmLicenseFile(cmd));
            })
            .run();
        });
     }

    private static Path rpmLicenseFile(JPackageCommand cmd) {
        final Path licenseRoot = Path.of(
                new Executor()
                .setExecutable("rpm")
                .addArguments("--eval", "%{_defaultlicensedir}")
                .executeAndGetFirstLineOfOutput());
        final Path licensePath = licenseRoot.resolve(String.format("%s-%s",
                LinuxHelper.getPackageName(cmd), cmd.version())).resolve(
                LICENSE_FILE.getFileName());
        return licensePath;
    }

    private static Path debLicenseFile(JPackageCommand cmd) {
        return cmd.appInstallationDirectory().resolve("share/doc/copyright");
    }

    private static void verifyLicenseFileInLinuxPackage(JPackageCommand cmd,
            Path expectedLicensePath) {
        Test.assertTrue(LinuxHelper.getPackageFiles(cmd).filter(path -> path.equals(
                expectedLicensePath)).findFirst().orElse(null) != null,
                String.format("Check license file [%s] is in %s package",
                        expectedLicensePath, LinuxHelper.getPackageName(cmd)));
    }

    private static void verifyLicenseFileInstalledRpm(Path licenseFile) {
        Test.assertTrue(Files.isReadable(licenseFile), String.format(
                "Check license file [%s] is readable", licenseFile));
        try {
            Test.assertTrue(Files.readAllLines(licenseFile).equals(
                    Files.readAllLines(LICENSE_FILE)), String.format(
                    "Check contents of package license file [%s] are the same as contents of source license file [%s]",
                    licenseFile, LICENSE_FILE));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void verifyLicenseFileInstalledDebian(Path licenseFile) {
        Test.assertTrue(Files.isReadable(licenseFile), String.format(
                "Check license file [%s] is readable", licenseFile));

        Function<List<String>, List<String>> stripper = (lines) -> Arrays.asList(
                String.join("\n", lines).stripTrailing().split("\n"));

        try {
            List<String> actualLines = Files.readAllLines(licenseFile).stream().dropWhile(
                    line -> !line.startsWith("License:")).collect(
                            Collectors.toList());
            // Remove leading `License:` followed by the whitespace from the first text line.
            actualLines.set(0, actualLines.get(0).split("\\s+", 2)[1]);

            actualLines = stripper.apply(actualLines);

            Test.assertNotEquals(0, String.join("\n", actualLines).length(),
                    "Check stripped license text is not empty");

            Test.assertTrue(actualLines.equals(
                    stripper.apply(Files.readAllLines(LICENSE_FILE))),
                    String.format(
                            "Check subset of package license file [%s] is a match of the source license file [%s]",
                            licenseFile, LICENSE_FILE));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static void verifyLicenseFileNotInstalledLinux(Path licenseFile) {
        Test.assertDirectoryExists(licenseFile.getParent(), false);
    }

    private static final Path LICENSE_FILE = Test.TEST_SRC_ROOT.resolve(
            Path.of("resources", "license.txt"));
}
