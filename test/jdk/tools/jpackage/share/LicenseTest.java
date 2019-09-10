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
import java.io.File;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.PackageType;
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
    public static void main(String[] args) throws Exception {
        new PackageTest().configureHelloApp()
        .addInitializer(cmd -> {
            cmd.addArguments("--license-file", LICENSE_FILE.toString());
        })
        .forTypes(PackageType.LINUX_DEB)
        .addBundleVerifier(cmd -> {
            verifyLicenseFileInLinuxPackage(cmd, debLicenseFile(cmd));
        })
        .addInstallVerifier(cmd -> {
            verifyLicenseFileInstalledLinux(debLicenseFile(cmd));
        })
        .addUninstallVerifier(cmd -> {
            verifyLicenseFileNotInstalledLinux(debLicenseFile(cmd));
        })
        .forTypes(PackageType.LINUX_RPM)
        .addBundleVerifier(cmd -> {
            verifyLicenseFileInLinuxPackage(cmd,rpmLicenseFile(cmd));
        })
        .addInstallVerifier(cmd -> {
            verifyLicenseFileInstalledLinux(rpmLicenseFile(cmd));
        })
        .addUninstallVerifier(cmd -> {
            verifyLicenseFileNotInstalledLinux(rpmLicenseFile(cmd));
        })
        .run();
    }

    private static Path rpmLicenseFile(JPackageCommand cmd) {
        final Path licenseRoot = Path.of(
                new Executor()
                .setExecutable("rpm")
                .addArguments("--eval", "%{_defaultlicensedir}")
                .saveFirstLineOfOutput()
                .execute()
                .assertExitCodeIsZero().getFirstLineOfOutput());
        final Path licensePath = licenseRoot.resolve(String.format("%s-%s",
                LinuxHelper.getPackageName(cmd), cmd.version())).resolve(
                LICENSE_FILE.getFileName());
        return licensePath;
    }

    private static Path debLicenseFile(JPackageCommand cmd) {
        final Path licensePath = Path.of("/usr", "share", "doc",
                LinuxHelper.getPackageName(cmd), "copyright");
        return licensePath;
    }

    private static void verifyLicenseFileInLinuxPackage(JPackageCommand cmd,
            Path expectedLicensePath) {
        Test.assertTrue(LinuxHelper.getPackageFiles(cmd).filter(path -> path.equals(
                expectedLicensePath)).findFirst().orElse(null) != null,
                String.format("Check license file [%s] is in %s package",
                        expectedLicensePath, LinuxHelper.getPackageName(cmd)));
    }

    private static void verifyLicenseFileInstalledLinux(Path licenseFile) {
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

    private static void verifyLicenseFileNotInstalledLinux(Path licenseFile) {
        Test.assertDirectoryExists(licenseFile.getParent(), false);
    }

    private static final Path LICENSE_FILE = Test.TEST_SRC_ROOT.resolve(
            Path.of("resources", "license.txt"));
}
