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
package jdk.jpackage.test;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Stream;

public class LinuxHelper {
    private static String getRelease(JPackageCommand cmd) {
        return cmd.getArgumentValue("--linux-app-release", () -> "1");
    }

    public static String getPackageName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);
        return cmd.getArgumentValue("--linux-bundle-name",
                () -> cmd.name().toLowerCase());
    }

    static String getBundleName(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final PackageType packageType = cmd.packageType();
        String format = null;
        switch (packageType) {
            case LINUX_DEB:
                format = "%s_%s-%s_%s";
                break;

            case LINUX_RPM:
                format = "%s-%s-%s.%s";
                break;
        }

        final String release = getRelease(cmd);
        final String version = cmd.version();

        return String.format(format,
                getPackageName(cmd), version, release, getPackageArch(packageType))
                + packageType.getSuffix();
    }

    public static Stream<Path> getPackageFiles(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final PackageType packageType = cmd.packageType();
        final Path packageFile = cmd.outputBundle();

        Executor exec = new Executor();
        exec.saveOutput();
        switch (packageType) {
            case LINUX_DEB:
                exec.setExecutable("dpkg")
                        .addArgument("--contents")
                        .addArgument(packageFile);
                break;

            case LINUX_RPM:
                exec.setExecutable("rpm")
                        .addArgument("-qpl")
                        .addArgument(packageFile);
                break;
        }

        Stream<String> lines = exec.execute().assertExitCodeIsZero().getOutput().stream();
        if (packageType == PackageType.LINUX_DEB) {
            // Typical text lines produced by dpkg look like:
            // drwxr-xr-x root/root         0 2019-08-30 05:30 ./opt/appcategorytest/runtime/lib/
            // -rw-r--r-- root/root    574912 2019-08-30 05:30 ./opt/appcategorytest/runtime/lib/libmlib_image.so
            // Need to skip all fields but absolute path to file.
            lines = lines.map(line -> line.substring(line.indexOf(" ./") + 2));
        }
        return lines.map(Path::of);
    }

    static Path getLauncherPath(JPackageCommand cmd) {
        cmd.verifyIsOfType(PackageType.LINUX);

        final String launcherName = cmd.name();
        final String launcherRelativePath = Path.of("/bin", launcherName).toString();

        return getPackageFiles(cmd).filter(path -> path.toString().endsWith(
                launcherRelativePath)).findFirst().or(() -> {
            Test.assertUnexpected(String.format(
                    "Failed to find %s in %s package", launcherName,
                    getPackageName(cmd)));
            return null;
        }).get();
    }

    static String getDebBundleProperty(Path bundle, String fieldName) {
        return new Executor()
                .saveFirstLineOfOutput()
                .setExecutable("dpkg-deb")
                .addArguments("-f", bundle.toString(), fieldName)
                .execute()
                .assertExitCodeIsZero().getFirstLineOfOutput();
    }

    static String geRpmBundleProperty(Path bundle, String fieldName) {
        return new Executor()
                .saveFirstLineOfOutput()
                .setExecutable("rpm")
                .addArguments(
                        "-qp",
                        "--queryformat",
                        String.format("%%{%s}", fieldName),
                        bundle.toString())
                .execute()
                .assertExitCodeIsZero().getFirstLineOfOutput();
    }

    private static String getPackageArch(PackageType type) {
        if (archs == null) {
            archs = new HashMap<>();
        }

        String arch = archs.get(type);
        if (arch == null) {
            Executor exec = new Executor();
            exec.saveFirstLineOfOutput();
            switch (type) {
                case LINUX_DEB:
                    exec.setExecutable("dpkg").addArgument(
                            "--print-architecture");
                    break;

                case LINUX_RPM:
                    exec.setExecutable("rpmbuild").addArgument(
                            "--eval=%{_target_cpu}");
                    break;
            }
            arch = exec.execute().assertExitCodeIsZero().getFirstLineOfOutput();
            archs.put(type, arch);
        }
        return arch;
    }

    static private Map<PackageType, String> archs;
}
