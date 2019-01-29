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
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import jdk.jpackage.internal.Arguments;

import static jdk.jpackage.internal.StandardBundlerParam.*;

public class WindowsAppImageBuilder extends AbstractAppImageBuilder {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.WinResources");

    private final static String LIBRARY_NAME = "applauncher.dll";
    private final static String REDIST_MSVCR = "vcruntimeVS_VER.dll";
    private final static String REDIST_MSVCP = "msvcpVS_VER.dll";

    private final static String TEMPLATE_APP_ICON ="javalogo_white_48.ico";

    private static final String EXECUTABLE_PROPERTIES_TEMPLATE =
            "WinLauncher.template";

    private final Path root;
    private final Path appDir;
    private final Path appModsDir;
    private final Path runtimeDir;
    private final Path mdir;

    private final Map<String, ? super Object> params;

    public static final BundlerParamInfo<Boolean> REBRAND_EXECUTABLE =
            new WindowsBundlerParam<>(
            I18N.getString("param.rebrand-executable.name"),
            I18N.getString("param.rebrand-executable.description"),
            "win.launcher.rebrand",
            Boolean.class,
            params -> Boolean.TRUE,
            (s, p) -> Boolean.valueOf(s));

    public static final BundlerParamInfo<File> ICON_ICO =
            new StandardBundlerParam<>(
            I18N.getString("param.icon-ico.name"),
            I18N.getString("param.icon-ico.description"),
            "icon.ico",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".ico")) {
                    Log.error(MessageFormat.format(
                            I18N.getString("message.icon-not-ico"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public static final StandardBundlerParam<Boolean> CONSOLE_HINT =
            new WindowsBundlerParam<>(
            I18N.getString("param.console-hint.name"),
            I18N.getString("param.console-hint.description"),
            Arguments.CLIOptions.WIN_CONSOLE_HINT.getId(),
            Boolean.class,
            params -> false,
            // valueOf(null) is false,
            // and we actually do want null in some cases
            (s, p) -> (s == null
            || "null".equalsIgnoreCase(s)) ? true : Boolean.valueOf(s));

    public WindowsAppImageBuilder(Map<String, Object> config, Path imageOutDir)
            throws IOException {
        super(config,
                imageOutDir.resolve(APP_NAME.fetchFrom(config) + "/runtime"));

        Objects.requireNonNull(imageOutDir);

        this.params = config;

        this.root = imageOutDir.resolve(APP_NAME.fetchFrom(params));
        this.appDir = root.resolve("app");
        this.appModsDir = appDir.resolve("mods");
        this.runtimeDir = root.resolve("runtime");
        this.mdir = runtimeDir.resolve("lib");
        Files.createDirectories(appDir);
        Files.createDirectories(runtimeDir);
    }

    public WindowsAppImageBuilder(String jreName, Path imageOutDir)
            throws IOException {
        super(null, imageOutDir.resolve(jreName));

        Objects.requireNonNull(imageOutDir);

        this.params = null;
        this.root = imageOutDir.resolve(jreName);
        this.appDir = null;
        this.appModsDir = null;
        this.runtimeDir = root;
        this.mdir = runtimeDir.resolve("lib");
        Files.createDirectories(runtimeDir);
    }

    private Path destFile(String dir, String filename) {
        return runtimeDir.resolve(dir).resolve(filename);
    }

    private void writeEntry(InputStream in, Path dstFile) throws IOException {
        Files.createDirectories(dstFile.getParent());
        Files.copy(in, dstFile);
    }

    private void writeSymEntry(Path dstFile, Path target) throws IOException {
        Files.createDirectories(dstFile.getParent());
        Files.createLink(dstFile, target);
    }

    /**
     * chmod ugo+x file
     */
    private void setExecutable(Path file) {
        try {
            Set<PosixFilePermission> perms =
                Files.getPosixFilePermissions(file);
            perms.add(PosixFilePermission.OWNER_EXECUTE);
            perms.add(PosixFilePermission.GROUP_EXECUTE);
            perms.add(PosixFilePermission.OTHERS_EXECUTE);
            Files.setPosixFilePermissions(file, perms);
        } catch (IOException ioe) {
            throw new UncheckedIOException(ioe);
        }
    }

    private static void createUtf8File(File file, String content)
            throws IOException {
        try (OutputStream fout = new FileOutputStream(file);
             Writer output = new OutputStreamWriter(fout, "UTF-8")) {
            output.write(content);
        }
    }

    public static String getLauncherName(Map<String, ? super Object> p) {
        return APP_NAME.fetchFrom(p) + ".exe";
    }

    // Returns launcher resource name for launcher we need to use.
    public static String getLauncherResourceName(
            Map<String, ? super Object> p) {
        if (CONSOLE_HINT.fetchFrom(p)) {
            return "jpackageapplauncher.exe";
        } else {
            return "jpackageapplauncherw.exe";
        }
    }

    public static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "app/" + APP_NAME.fetchFrom(p) +".cfg";
    }

    private File getConfig_AppIcon(Map<String, ? super Object> params) {
        return new File(getConfigRoot(params),
                APP_NAME.fetchFrom(params) + ".ico");
    }

    private File getConfig_ExecutableProperties(
           Map<String, ? super Object> params) {
        return new File(getConfigRoot(params),
                APP_NAME.fetchFrom(params) + ".properties");
    }

    File getConfigRoot(Map<String, ? super Object> params) {
        return CONFIG_ROOT.fetchFrom(params);
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
    public void prepareApplicationFiles() throws IOException {
        Map<String, ? super Object> originalParams = new HashMap<>(params);
        File rootFile = root.toFile();
        if (!rootFile.isDirectory() && !rootFile.mkdirs()) {
            throw new RuntimeException(MessageFormat.format(I18N.getString(
                "error.cannot-create-output-dir"), rootFile.getAbsolutePath()));
        }
        if (!rootFile.canWrite()) {
            throw new RuntimeException(MessageFormat.format(
                    I18N.getString("error.cannot-write-to-output-dir"),
                    rootFile.getAbsolutePath()));
        }
        // create the .exe launchers
        createLauncherForEntryPoint(params);

        // copy the jars
        copyApplication(params);

        // copy in the needed libraries
        try (InputStream is_lib = getResourceAsStream(LIBRARY_NAME)) {
            Files.copy(is_lib, root.resolve(LIBRARY_NAME));
        }

        copyMSVCDLLs();

        // create the secondary launchers, if any
        List<Map<String, ? super Object>> entryPoints =
                StandardBundlerParam.SECONDARY_LAUNCHERS.fetchFrom(params);
        for (Map<String, ? super Object> entryPoint : entryPoints) {
            Map<String, ? super Object> tmp = new HashMap<>(originalParams);
            tmp.putAll(entryPoint);
            createLauncherForEntryPoint(tmp);
        }
    }

    @Override
    public void prepareJreFiles() throws IOException {}

    private void copyMSVCDLLs() throws IOException {
        AtomicReference<IOException> ioe = new AtomicReference<>();
        try (Stream<Path> files = Files.list(runtimeDir.resolve("bin"))) {
            files.filter(p -> Pattern.matches(
                    "^(vcruntime|msvcp|msvcr|ucrtbase|api-ms-win-).*\\.dll$",
                    p.toFile().getName().toLowerCase()))
                 .forEach(p -> {
                    try {
                        Files.copy(p, root.resolve((p.toFile().getName())));
                    } catch (IOException e) {
                        ioe.set(e);
                    }
                });
        }

        IOException e = ioe.get();
        if (e != null) {
            throw e;
        }
    }

    // TODO: do we still need this?
    private boolean copyMSVCDLLs(String VS_VER) throws IOException {
        final InputStream REDIST_MSVCR_URL = getResourceAsStream(
                REDIST_MSVCR.replaceAll("VS_VER", VS_VER));
        final InputStream REDIST_MSVCP_URL = getResourceAsStream(
                REDIST_MSVCP.replaceAll("VS_VER", VS_VER));

        if (REDIST_MSVCR_URL != null && REDIST_MSVCP_URL != null) {
            Files.copy(
                    REDIST_MSVCR_URL,
                    root.resolve(REDIST_MSVCR.replaceAll("VS_VER", VS_VER)));
            Files.copy(
                    REDIST_MSVCP_URL,
                    root.resolve(REDIST_MSVCP.replaceAll("VS_VER", VS_VER)));
            return true;
        }

        return false;
    }

    private void validateValueAndPut(
            Map<String, String> data, String key,
            BundlerParamInfo<String> param,
            Map<String, ? super Object> params) {
        String value = param.fetchFrom(params);
        if (value.contains("\r") || value.contains("\n")) {
            Log.error("Configuration Parameter " + param.getID()
                    + " contains multiple lines of text, ignore it");
            data.put(key, "");
            return;
        }
        data.put(key, value);
    }

    protected void prepareExecutableProperties(
           Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = new HashMap<>();

        // mapping Java parameters in strings for version resource
        data.put("COMMENTS", "");
        validateValueAndPut(data, "COMPANY_NAME", VENDOR, params);
        validateValueAndPut(data, "FILE_DESCRIPTION", DESCRIPTION, params);
        validateValueAndPut(data, "FILE_VERSION", VERSION, params);
        data.put("INTERNAL_NAME", getLauncherName(params));
        validateValueAndPut(data, "LEGAL_COPYRIGHT", COPYRIGHT, params);
        data.put("LEGAL_TRADEMARK", "");
        data.put("ORIGINAL_FILENAME", getLauncherName(params));
        data.put("PRIVATE_BUILD", "");
        validateValueAndPut(data, "PRODUCT_NAME", APP_NAME, params);
        validateValueAndPut(data, "PRODUCT_VERSION", VERSION, params);
        data.put("SPECIAL_BUILD", "");

        Writer w = new BufferedWriter(
                new FileWriter(getConfig_ExecutableProperties(params)));
        String content = preprocessTextResource(
                getConfig_ExecutableProperties(params).getName(),
                I18N.getString("resource.executable-properties-template"),
                EXECUTABLE_PROPERTIES_TEMPLATE, data,
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));
        w.write(content);
        w.close();
    }

    private void createLauncherForEntryPoint(
            Map<String, ? super Object> p) throws IOException {

        File launcherIcon = ICON_ICO.fetchFrom(p);
        File icon = launcherIcon != null ?
                launcherIcon : ICON_ICO.fetchFrom(params);
        File iconTarget = getConfig_AppIcon(p);

        InputStream in = locateResource(
                APP_NAME.fetchFrom(params) + ".ico",
                "icon",
                TEMPLATE_APP_ICON,
                icon,
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));

        Files.copy(in, iconTarget.toPath(),
                StandardCopyOption.REPLACE_EXISTING);

        writeCfgFile(p, root.resolve(
                getLauncherCfgName(p)).toFile(), "$APPDIR\\runtime");

        prepareExecutableProperties(p);

        // Copy executable root folder
        Path executableFile = root.resolve(getLauncherName(p));
        try (InputStream is_launcher =
                getResourceAsStream(getLauncherResourceName(p))) {
            writeEntry(is_launcher, executableFile);
        }

        File launcher = executableFile.toFile();
        launcher.setWritable(true, true);

        // Update branding of EXE file
        if (REBRAND_EXECUTABLE.fetchFrom(p)) {
            File tool = new File(
                System.getProperty("java.home") + "\\bin\\jpackage.exe");

            // Run tool on launcher file to change the icon and the metadata.
            try {
                if (WindowsDefender.isThereAPotentialWindowsDefenderIssue()) {
                    Log.error(MessageFormat.format(I18N.getString(
                            "message.potential.windows.defender.issue"),
                            WindowsDefender.getUserTempDirectory()));
                }

                launcher.setWritable(true);

                if (iconTarget.exists()) {
                    ProcessBuilder pb = new ProcessBuilder(
                            tool.getAbsolutePath(),
                            "--icon-swap",
                            iconTarget.getAbsolutePath(),
                            launcher.getAbsolutePath());
                    IOUtils.exec(pb, false);
                }

                File executableProperties = getConfig_ExecutableProperties(p);

                if (executableProperties.exists()) {
                    ProcessBuilder pb = new ProcessBuilder(
                            tool.getAbsolutePath(),
                            "--version-swap",
                            executableProperties.getAbsolutePath(),
                            launcher.getAbsolutePath());
                    IOUtils.exec(pb, false);
                }
            }
            finally {
                executableFile.toFile().setReadOnly();
            }
        }

        Files.copy(iconTarget.toPath(),
                root.resolve(APP_NAME.fetchFrom(p) + ".ico"));
    }

    private void copyApplication(Map<String, ? super Object> params)
            throws IOException {
        List<RelativeFileSet> appResourcesList =
                APP_RESOURCES_LIST.fetchFrom(params);
        if (appResourcesList == null) {
            throw new RuntimeException("Null app resources?");
        }
        for (RelativeFileSet appResources : appResourcesList) {
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
