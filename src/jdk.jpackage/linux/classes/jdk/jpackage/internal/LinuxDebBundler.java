/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jpackage.internal;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static jdk.jpackage.internal.StandardBundlerParam.*;
import static jdk.jpackage.internal.LinuxPackageBundler.I18N;

public class LinuxDebBundler extends LinuxPackageBundler {

    // Debian rules for package naming are used here
    // https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-Source
    //
    // Package names must consist only of lower case letters (a-z),
    // digits (0-9), plus (+) and minus (-) signs, and periods (.).
    // They must be at least two characters long and
    // must start with an alphanumeric character.
    //
    private static final Pattern DEB_PACKAGE_NAME_PATTERN =
            Pattern.compile("^[a-z][a-z\\d\\+\\-\\.]+");

    private static final BundlerParamInfo<String> PACKAGE_NAME =
            new StandardBundlerParam<> (
            Arguments.CLIOptions.LINUX_BUNDLE_NAME.getId(),
            String.class,
            params -> {
                String nm = APP_NAME.fetchFrom(params);

                if (nm == null) return null;

                // make sure to lower case and spaces/underscores become dashes
                nm = nm.toLowerCase().replaceAll("[ _]", "-");
                return nm;
            },
            (s, p) -> {
                if (!DEB_PACKAGE_NAME_PATTERN.matcher(s).matches()) {
                    throw new IllegalArgumentException(new ConfigException(
                            MessageFormat.format(I18N.getString(
                            "error.invalid-value-for-package-name"), s),
                            I18N.getString(
                            "error.invalid-value-for-package-name.advice")));
                }

                return s;
            });

    private static final BundlerParamInfo<String> FULL_PACKAGE_NAME =
            new StandardBundlerParam<>(
                    "linux.deb.fullPackageName", String.class, params -> {
                        try {
                            return PACKAGE_NAME.fetchFrom(params)
                            + "_" + VERSION.fetchFrom(params)
                            + "-" + RELEASE.fetchFrom(params)
                            + "_" + getDebArch();
                        } catch (IOException ex) {
                            Log.verbose(ex);
                            return null;
                        }
                    }, (s, p) -> s);

    private static final BundlerParamInfo<String> EMAIL =
            new StandardBundlerParam<> (
            Arguments.CLIOptions.LINUX_DEB_MAINTAINER.getId(),
            String.class,
            params -> "Unknown",
            (s, p) -> s);

    private static final BundlerParamInfo<String> MAINTAINER =
            new StandardBundlerParam<> (
            BundleParams.PARAM_MAINTAINER,
            String.class,
            params -> VENDOR.fetchFrom(params) + " <"
                    + EMAIL.fetchFrom(params) + ">",
            (s, p) -> s);

    private static final BundlerParamInfo<String> SECTION =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.LINUX_CATEGORY.getId(),
            String.class,
            params -> "misc",
            (s, p) -> s);

    private static final BundlerParamInfo<String> LICENSE_TEXT =
            new StandardBundlerParam<> (
            "linux.deb.licenseText",
            String.class,
            params -> {
                try {
                    String licenseFile = LICENSE_FILE.fetchFrom(params);
                    if (licenseFile != null) {
                        StringBuilder contentBuilder = new StringBuilder();
                        try (Stream<String> stream = Files.lines(Path.of(
                                licenseFile), StandardCharsets.UTF_8)) {
                            stream.forEach(s -> contentBuilder.append(s).append(
                                    "\n"));
                        }
                        return contentBuilder.toString();
                    }
                } catch (Exception e) {
                    Log.verbose(e);
                }
                return "Unknown";
            },
            (s, p) -> s);

    private static final BundlerParamInfo<String> COPYRIGHT_FILE =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.LINUX_DEB_COPYRIGHT_FILE.getId(),
            String.class,
            params -> null,
            (s, p) -> s);

    private final static String TOOL_DPKG_DEB = "dpkg-deb";
    private final static String TOOL_DPKG = "dpkg";

    public static boolean testTool(String toolName, String minVersion) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    toolName,
                    "--version");
            // not interested in the output
            IOUtils.exec(pb, true, null);
        } catch (Exception e) {
            Log.verbose(MessageFormat.format(I18N.getString(
                    "message.test-for-tool"), toolName, e.getMessage()));
            return false;
        }
        return true;
    }

    public LinuxDebBundler() {
        super(PACKAGE_NAME);
    }

    @Override
    public void doValidate(Map<String, ? super Object> params)
            throws ConfigException {
        // NOTE: Can we validate that the required tools are available
        // before we start?
        if (!testTool(TOOL_DPKG_DEB, "1")){
            throw new ConfigException(MessageFormat.format(
                    I18N.getString("error.tool-not-found"), TOOL_DPKG_DEB),
                    I18N.getString("error.tool-not-found.advice"));
        }
        if (!testTool(TOOL_DPKG, "1")){
            throw new ConfigException(MessageFormat.format(
                    I18N.getString("error.tool-not-found"), TOOL_DPKG),
                    I18N.getString("error.tool-not-found.advice"));
        }


        // Show warning is license file is missing
        String licenseFile = LICENSE_FILE.fetchFrom(params);
        if (licenseFile == null) {
            Log.verbose(I18N.getString("message.debs-like-licenses"));
        }
    }

    @Override
    protected File buildPackageBundle(
            Map<String, String> replacementData,
            Map<String, ? super Object> params, File outputParentDir) throws
            PackagerException, IOException {

        prepareProjectConfig(replacementData, params);
        adjustPermissionsRecursive(createMetaPackage(params).sourceRoot().toFile());
        return buildDeb(params, outputParentDir);
    }

    /*
     * set permissions with a string like "rwxr-xr-x"
     *
     * This cannot be directly backport to 22u which is built with 1.6
     */
    private void setPermissions(File file, String permissions) {
        Set<PosixFilePermission> filePermissions =
                PosixFilePermissions.fromString(permissions);
        try {
            if (file.exists()) {
                Files.setPosixFilePermissions(file.toPath(), filePermissions);
            }
        } catch (IOException ex) {
            Log.error(ex.getMessage());
            Log.verbose(ex);
        }

    }

    private static String getDebArch() throws IOException {
        try (var baos = new ByteArrayOutputStream();
                var ps = new PrintStream(baos)) {
            var pb = new ProcessBuilder(TOOL_DPKG, "--print-architecture");
            IOUtils.exec(pb, false, ps);
            return baos.toString().split("\n", 2)[0];
        }
    }

    public static boolean isDebian() {
        // we are just going to run "dpkg -s coreutils" and assume Debian
        // or deritive if no error is returned.
        var pb = new ProcessBuilder(TOOL_DPKG, "-s", "coreutils");
        try {
            int ret = pb.start().waitFor();
            return (ret == 0);
        } catch (IOException | InterruptedException e) {
            // just fall thru
        }
        return false;
    }

    private void adjustPermissionsRecursive(File dir) throws IOException {
        Files.walkFileTree(dir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attrs)
                    throws IOException {
                if (file.endsWith(".so") || !Files.isExecutable(file)) {
                    setPermissions(file.toFile(), "rw-r--r--");
                } else if (Files.isExecutable(file)) {
                    setPermissions(file.toFile(), "rwxr-xr-x");
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                if (e == null) {
                    setPermissions(dir.toFile(), "rwxr-xr-x");
                    return FileVisitResult.CONTINUE;
                } else {
                    // directory iteration failed
                    throw e;
                }
            }
        });
    }

    private class DebianFile {

        DebianFile(Path dstFilePath, String comment) {
            this.dstFilePath = dstFilePath;
            this.comment = comment;
        }

        DebianFile setExecutable() {
            permissions = "rwxr-xr-x";
            return this;
        }

        void create(Map<String, String> data, Map<String, ? super Object> params)
                throws IOException {
            Files.createDirectories(dstFilePath.getParent());
            try (Writer w = Files.newBufferedWriter(dstFilePath)) {
                String content = preprocessTextResource(
                        dstFilePath.getFileName().toString(),
                        I18N.getString(comment),
                        "template." + dstFilePath.getFileName().toString(),
                        data,
                        VERBOSE.fetchFrom(params),
                        RESOURCE_DIR.fetchFrom(params));
                w.write(content);
            }
            if (permissions != null) {
                setPermissions(dstFilePath.toFile(), permissions);
            }
        }

        private final Path dstFilePath;
        private final String comment;
        private String permissions;
    }

    private void prepareProjectConfig(Map<String, String> data,
            Map<String, ? super Object> params) throws IOException {

        Path configDir = createMetaPackage(params).sourceRoot().resolve("DEBIAN");
        List<DebianFile> debianFiles = new ArrayList<>();
        debianFiles.add(new DebianFile(
                configDir.resolve("control"),
                "resource.deb-control-file"));
        debianFiles.add(new DebianFile(
                configDir.resolve("preinst"),
                "resource.deb-preinstall-script").setExecutable());
        debianFiles.add(new DebianFile(
                configDir.resolve("prerm"),
                "resource.deb-prerm-script").setExecutable());
        debianFiles.add(new DebianFile(
                configDir.resolve("postinst"),
                "resource.deb-postinstall-script").setExecutable());
        debianFiles.add(new DebianFile(
                configDir.resolve("postrm"),
                "resource.deb-postrm-script").setExecutable());

        getConfig_CopyrightFile(params).getParentFile().mkdirs();
        String customCopyrightFile = COPYRIGHT_FILE.fetchFrom(params);
        if (customCopyrightFile != null) {
            IOUtils.copyFile(new File(customCopyrightFile),
                    getConfig_CopyrightFile(params));
        } else {
            debianFiles.add(new DebianFile(
                    getConfig_CopyrightFile(params).toPath(),
                    "resource.copyright-file"));
        }

        for (DebianFile debianFile : debianFiles) {
            debianFile.create(data, params);
        }
    }

    @Override
    protected Map<String, String> createReplacementData(
            Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_MAINTAINER", MAINTAINER.fetchFrom(params));
        data.put("APPLICATION_SECTION", SECTION.fetchFrom(params));
        data.put("APPLICATION_COPYRIGHT", COPYRIGHT.fetchFrom(params));
        data.put("APPLICATION_LICENSE_TEXT", LICENSE_TEXT.fetchFrom(params));
        data.put("APPLICATION_ARCH", getDebArch());
        data.put("APPLICATION_INSTALLED_SIZE", Long.toString(
                createMetaPackage(params).sourceApplicationLayout().sizeInBytes() >> 10));

        return data;
    }

    private File getConfig_CopyrightFile(Map<String, ? super Object> params) {
        PlatformPackage thePackage = createMetaPackage(params);
        return thePackage.sourceRoot().resolve(Path.of("usr/share/doc",
                thePackage.name(), "copyright")).toFile();
    }

    private File buildDeb(Map<String, ? super Object> params,
            File outdir) throws IOException {
        File outFile = new File(outdir,
                FULL_PACKAGE_NAME.fetchFrom(params)+".deb");
        Log.verbose(MessageFormat.format(I18N.getString(
                "message.outputting-to-location"), outFile.getAbsolutePath()));

        PlatformPackage thePackage = createMetaPackage(params);

        // run dpkg
        ProcessBuilder pb = new ProcessBuilder(
                "fakeroot", TOOL_DPKG_DEB, "-b",
                thePackage.sourceRoot().toString(),
                outFile.getAbsolutePath());
        IOUtils.exec(pb);

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.output-to-location"), outFile.getAbsolutePath()));

        return outFile;
    }

    @Override
    public String getName() {
        return I18N.getString("deb.bundler.name");
    }

    @Override
    public String getID() {
        return "deb";
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        if (Platform.getPlatform() == Platform.LINUX) {
            if (testTool(TOOL_DPKG_DEB, "1")) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isDefault() {
        return isDebian();
    }

}
