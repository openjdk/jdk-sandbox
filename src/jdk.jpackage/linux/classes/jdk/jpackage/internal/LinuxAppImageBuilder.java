/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;

import static jdk.jpackage.internal.StandardBundlerParam.*;

public class LinuxAppImageBuilder extends AbstractAppImageBuilder {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
        "jdk.jpackage.internal.resources.LinuxResources");

    private static final String LIBRARY_NAME = "libapplauncher.so";
    final static String DEFAULT_ICON = "java32.png";

    private final Path root;
    private final Path appDir;
    private final Path appModsDir;
    private final Path runtimeDir;
    private final Path binDir;
    private final Path mdir;

    public static final BundlerParamInfo<File> ICON_PNG =
            new StandardBundlerParam<>(
            "icon.png",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".png")) {
                    Log.error(MessageFormat.format(I18N.getString(
                            "message.icon-not-png"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public LinuxAppImageBuilder(Map<String, Object> params, Path imageOutDir)
            throws IOException {
        super(params,
                imageOutDir.resolve(APP_NAME.fetchFrom(params) + "/runtime"));

        Objects.requireNonNull(imageOutDir);

        this.root = imageOutDir.resolve(APP_NAME.fetchFrom(params));
        this.appDir = root.resolve("app");
        this.appModsDir = appDir.resolve("mods");
        this.runtimeDir = root.resolve("runtime");
        this.binDir = root.resolve("bin");
        this.mdir = runtimeDir.resolve("lib");
        Files.createDirectories(appDir);
        Files.createDirectories(runtimeDir);
    }

    public LinuxAppImageBuilder(String appName, Path imageOutDir)
            throws IOException {
        super(null, imageOutDir.resolve(appName));

        Objects.requireNonNull(imageOutDir);

        this.root = imageOutDir.resolve(appName);
        this.appDir = null;
        this.appModsDir = null;
        this.runtimeDir = null;
        this.binDir = null;
        this.mdir = null;
    }

    private void writeEntry(InputStream in, Path dstFile) throws IOException {
        Files.createDirectories(dstFile.getParent());
        Files.copy(in, dstFile);
    }

    public static String getLauncherName(Map<String, ? super Object> params) {
        return APP_NAME.fetchFrom(params);
    }

    public static String getLauncherCfgName(
            Map<String, ? super Object> params) {
        return "app" + File.separator + APP_NAME.fetchFrom(params) + ".cfg";
    }

    @Override
    public Path getAppDir() {
        return appDir;
    }

    @Override
    public Path getAppModsDir() {
        return appModsDir;
    }

    @Override
    public void prepareApplicationFiles(Map<String, ? super Object> params)
            throws IOException {
        Map<String, ? super Object> originalParams = new HashMap<>(params);

        try {
            IOUtils.writableOutputDir(root);
            IOUtils.writableOutputDir(binDir);
        } catch (PackagerException pe) {
            throw new RuntimeException(pe);
        }

        // create the primary launcher
        createLauncherForEntryPoint(params);

        // Copy library to the launcher folder
        try (InputStream is_lib = getResourceAsStream(LIBRARY_NAME)) {
            writeEntry(is_lib, binDir.resolve(LIBRARY_NAME));
        }

        // create the additional launchers, if any
        List<Map<String, ? super Object>> entryPoints
                = StandardBundlerParam.ADD_LAUNCHERS.fetchFrom(params);
        for (Map<String, ? super Object> entryPoint : entryPoints) {
            createLauncherForEntryPoint(
                    AddLauncherArguments.merge(originalParams, entryPoint));
        }

        // Copy class path entries to Java folder
        copyApplication(params);

        // Copy icon to Resources folder
        copyIcon(params);
    }

    @Override
    public void prepareJreFiles(Map<String, ? super Object> params)
            throws IOException {}

    private void createLauncherForEntryPoint(
            Map<String, ? super Object> params) throws IOException {
        // Copy executable to Linux folder
        Path executableFile = binDir.resolve(getLauncherName(params));
        try (InputStream is_launcher =
                getResourceAsStream("jpackageapplauncher")) {
            writeEntry(is_launcher, executableFile);
        }

        executableFile.toFile().setExecutable(true, false);
        executableFile.toFile().setWritable(true, true);

        writeCfgFile(params, root.resolve(getLauncherCfgName(params)).toFile());
    }

    private void copyIcon(Map<String, ? super Object> params)
            throws IOException {

        File icon = ICON_PNG.fetchFrom(params);
        File iconTarget = binDir.resolve(APP_NAME.fetchFrom(params) + ".png").toFile();

        InputStream in = locateResource(
                iconTarget.getName(),
                "icon",
                DEFAULT_ICON,
                icon,
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));

        Files.copy(in, iconTarget.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    private void copyApplication(Map<String, ? super Object> params)
            throws IOException {
        for (RelativeFileSet appResources :
                APP_RESOURCES_LIST.fetchFrom(params)) {
            if (appResources == null) {
                throw new RuntimeException("Null app resources?");
            }
            File srcdir = appResources.getBaseDirectory();
            for (String fname : appResources.getIncludedFiles()) {
                copyEntry(appDir, srcdir, fname);
            }
        }
    }

}
