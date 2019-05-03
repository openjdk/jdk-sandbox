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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static jdk.jpackage.internal.StandardBundlerParam.*;
import static jdk.jpackage.internal.MacBaseInstallerBundler.*;
import static jdk.jpackage.internal.MacAppBundler.*;

public class MacAppImageBuilder extends AbstractAppImageBuilder {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MacResources");

    private static final String LIBRARY_NAME = "libapplauncher.dylib";
    private static final String TEMPLATE_BUNDLE_ICON = "GenericApp.icns";
    private static final String OS_TYPE_CODE = "APPL";
    private static final String TEMPLATE_INFO_PLIST_LITE =
            "Info-lite.plist.template";
    private static final String TEMPLATE_RUNTIME_INFO_PLIST =
            "Runtime-Info.plist.template";

    private final Path root;
    private final Path contentsDir;
    private final Path javaDir;
    private final Path javaModsDir;
    private final Path resourcesDir;
    private final Path macOSDir;
    private final Path runtimeDir;
    private final Path runtimeRoot;
    private final Path mdir;

    private final Map<String, ? super Object> params;

    private static List<String> keyChains;

    public static final BundlerParamInfo<Boolean>
            MAC_CONFIGURE_LAUNCHER_IN_PLIST = new StandardBundlerParam<>(
                    "mac.configure-launcher-in-plist",
                    Boolean.class,
                    params -> Boolean.FALSE,
                    (s, p) -> Boolean.valueOf(s));

    public static final EnumeratedBundlerParam<String> MAC_CATEGORY =
            new EnumeratedBundlerParam<>(
                    Arguments.CLIOptions.MAC_APP_STORE_CATEGORY.getId(),
                    String.class,
                    params -> "Unknown",
                    (s, p) -> s,
                    MacAppBundler.getMacCategories(),
                    false //strict - for MacStoreBundler this should be strict
            );

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_NAME =
            new StandardBundlerParam<>(
                    "mac.CFBundleName",
                    String.class,
                    params -> null,
                    (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_IDENTIFIER =
            new StandardBundlerParam<>(
                    Arguments.CLIOptions.MAC_BUNDLE_IDENTIFIER.getId(),
                    String.class,
                    IDENTIFIER::fetchFrom,
                    (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_VERSION =
            new StandardBundlerParam<>(
                    "mac.CFBundleVersion",
                    String.class,
                    p -> {
                        String s = VERSION.fetchFrom(p);
                        if (validCFBundleVersion(s)) {
                            return s;
                        } else {
                            return "100";
                        }
                    },
                    (s, p) -> s);

    public static final BundlerParamInfo<String> DEFAULT_ICNS_ICON =
            new StandardBundlerParam<>(
            ".mac.default.icns",
            String.class,
            params -> TEMPLATE_BUNDLE_ICON,
            (s, p) -> s);

    public static final BundlerParamInfo<File> ICON_ICNS =
            new StandardBundlerParam<>(
            "icon.icns",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".icns")) {
                    Log.error(MessageFormat.format(
                            I18N.getString("message.icon-not-icns"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public static final StandardBundlerParam<Boolean> SIGN_BUNDLE  =
            new StandardBundlerParam<>(
            Arguments.CLIOptions.MAC_SIGN.getId(),
            Boolean.class,
            params -> false,
            // valueOf(null) is false, we actually do want null in some cases
            (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                    null : Boolean.valueOf(s)
        );

    public MacAppImageBuilder(Map<String, Object> config, Path imageOutDir)
            throws IOException {
        super(config, imageOutDir.resolve(APP_NAME.fetchFrom(config)
                + ".app/Contents/runtime/Contents/Home"));

        Objects.requireNonNull(imageOutDir);

        this.params = config;
        this.root = imageOutDir.resolve(APP_NAME.fetchFrom(params) + ".app");
        this.contentsDir = root.resolve("Contents");
        this.javaDir = contentsDir.resolve("Java");
        this.javaModsDir = javaDir.resolve("mods");
        this.resourcesDir = contentsDir.resolve("Resources");
        this.macOSDir = contentsDir.resolve("MacOS");
        this.runtimeDir = contentsDir.resolve("runtime");
        this.runtimeRoot = runtimeDir.resolve("Contents/Home");
        this.mdir = runtimeRoot.resolve("lib");
        Files.createDirectories(javaDir);
        Files.createDirectories(resourcesDir);
        Files.createDirectories(macOSDir);
        Files.createDirectories(runtimeDir);
    }

    public MacAppImageBuilder(Map<String, Object> config, String jreName,
            Path imageOutDir) throws IOException {
        super(null, imageOutDir.resolve(jreName + "/Contents/Home"));

        Objects.requireNonNull(imageOutDir);

        this.params = config;
        this.root = imageOutDir.resolve(jreName );
        this.contentsDir = root.resolve("Contents");
        this.javaDir = null;
        this.javaModsDir = null;
        this.resourcesDir = null;
        this.macOSDir = null;
        this.runtimeDir = this.root;
        this.runtimeRoot = runtimeDir.resolve("Contents/Home");
        this.mdir = runtimeRoot.resolve("lib");

        Files.createDirectories(runtimeDir);
    }

    private void writeEntry(InputStream in, Path dstFile) throws IOException {
        Files.createDirectories(dstFile.getParent());
        Files.copy(in, dstFile);
    }

    // chmod ugo+x file
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

    public static boolean validCFBundleVersion(String v) {
        // CFBundleVersion (String - iOS, OS X) specifies the build version
        // number of the bundle, which identifies an iteration (released or
        // unreleased) of the bundle. The build version number should be a
        // string comprised of three non-negative, period-separated integers
        // with the first integer being greater than zero. The string should
        // only contain numeric (0-9) and period (.) characters. Leading zeros
        // are truncated from each integer and will be ignored (that is,
        // 1.02.3 is equivalent to 1.2.3). This key is not localizable.

        if (v == null) {
            return false;
        }

        String p[] = v.split("\\.");
        if (p.length > 3 || p.length < 1) {
            Log.verbose(I18N.getString(
                    "message.version-string-too-many-components"));
            return false;
        }

        try {
            BigInteger n = new BigInteger(p[0]);
            if (BigInteger.ONE.compareTo(n) > 0) {
                Log.verbose(I18N.getString(
                        "message.version-string-first-number-not-zero"));
                return false;
            }
            if (p.length > 1) {
                n = new BigInteger(p[1]);
                if (BigInteger.ZERO.compareTo(n) > 0) {
                    Log.verbose(I18N.getString(
                            "message.version-string-no-negative-numbers"));
                    return false;
                }
            }
            if (p.length > 2) {
                n = new BigInteger(p[2]);
                if (BigInteger.ZERO.compareTo(n) > 0) {
                    Log.verbose(I18N.getString(
                            "message.version-string-no-negative-numbers"));
                    return false;
                }
            }
        } catch (NumberFormatException ne) {
            Log.verbose(I18N.getString("message.version-string-numbers-only"));
            Log.verbose(ne);
            return false;
        }

        return true;
    }

    @Override
    public Path getAppDir() {
        return javaDir;
    }

    @Override
    public Path getAppModsDir() {
        return javaModsDir;
    }

    @Override
    public void prepareApplicationFiles() throws IOException {
        Map<String, ? super Object> originalParams = new HashMap<>(params);
        // Generate PkgInfo
        File pkgInfoFile = new File(contentsDir.toFile(), "PkgInfo");
        pkgInfoFile.createNewFile();
        writePkgInfo(pkgInfoFile);

        Path executable = macOSDir.resolve(getLauncherName(params));

        // create the main app launcher
        try (InputStream is_launcher =
                getResourceAsStream("jpackageapplauncher");
            InputStream is_lib = getResourceAsStream(LIBRARY_NAME)) {
            // Copy executable and library to MacOS folder
            writeEntry(is_launcher, executable);
            writeEntry(is_lib, macOSDir.resolve(LIBRARY_NAME));
        }
        executable.toFile().setExecutable(true, false);
        // generate main app launcher config file
        File cfg = new File(root.toFile(), getLauncherCfgName(params));
        writeCfgFile(params, cfg, "$APPDIR/runtime");

        // create additional app launcher(s) and config file(s)
        List<Map<String, ? super Object>> entryPoints =
                StandardBundlerParam.ADD_LAUNCHERS.fetchFrom(params);
        for (Map<String, ? super Object> entryPoint : entryPoints) {
            Map<String, ? super Object> tmp =
                    AddLauncherArguments.merge(originalParams, entryPoint);

            // add executable for add launcher
            Path addExecutable = macOSDir.resolve(getLauncherName(tmp));
            try (InputStream is = getResourceAsStream("jpackageapplauncher");) {
                writeEntry(is, addExecutable);
            }
            addExecutable.toFile().setExecutable(true, false);

            // add config file for add launcher
            cfg = new File(root.toFile(), getLauncherCfgName(tmp));
            writeCfgFile(tmp, cfg, "$APPDIR/runtime");
        }

        // Copy class path entries to Java folder
        copyClassPathEntries(javaDir);

        /*********** Take care of "config" files *******/
        File icon = ICON_ICNS.fetchFrom(params);

        InputStream in = locateResource(
                APP_NAME.fetchFrom(params) + ".icns",
                "icon",
                DEFAULT_ICNS_ICON.fetchFrom(params),
                icon,
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));
        Files.copy(in,
                resourcesDir.resolve(APP_NAME.fetchFrom(params) + ".icns"),
                StandardCopyOption.REPLACE_EXISTING);

        // copy file association icons
        for (Map<String, ?
                super Object> fa : FILE_ASSOCIATIONS.fetchFrom(params)) {
            File f = FA_ICON.fetchFrom(fa);
            if (f != null && f.exists()) {
                try (InputStream in2 = new FileInputStream(f)) {
                    Files.copy(in2, resourcesDir.resolve(f.getName()));
                }

            }
        }

        copyRuntimeFiles();
        sign();
    }

    @Override
    public void prepareJreFiles() throws IOException {
        copyRuntimeFiles();
        sign();
    }

    private void copyRuntimeFiles() throws IOException {
        // Generate Info.plist
        writeInfoPlist(contentsDir.resolve("Info.plist").toFile());

        // generate java runtime info.plist
        writeRuntimeInfoPlist(
                runtimeDir.resolve("Contents/Info.plist").toFile());

        // copy library
        Path runtimeMacOSDir = Files.createDirectories(
                runtimeDir.resolve("Contents/MacOS"));

        // JDK 9, 10, and 11 have extra '/jli/' subdir
        Path jli = runtimeRoot.resolve("lib/libjli.dylib");
        if (!Files.exists(jli)) {
            jli = runtimeRoot.resolve("lib/jli/libjli.dylib");
        }

        Files.copy(jli, runtimeMacOSDir.resolve("libjli.dylib"));
    }

    private void sign() throws IOException {
        if (Optional.ofNullable(
                SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.TRUE)) {
            try {
                addNewKeychain(params);
            } catch (InterruptedException e) {
                Log.error(e.getMessage());
            }
            String signingIdentity =
                    DEVELOPER_ID_APP_SIGNING_KEY.fetchFrom(params);
            if (signingIdentity != null) {
                signAppBundle(params, root, signingIdentity,
                        BUNDLE_ID_SIGNING_PREFIX.fetchFrom(params), null, null);
            }
            restoreKeychainList(params);
        }
    }

    private String getLauncherName(Map<String, ? super Object> params) {
        if (APP_NAME.fetchFrom(params) != null) {
            return APP_NAME.fetchFrom(params);
        } else {
            return MAIN_CLASS.fetchFrom(params);
        }
    }

    public static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "Contents/Java/" + APP_NAME.fetchFrom(p) + ".cfg";
    }

    private void copyClassPathEntries(Path javaDirectory) throws IOException {
        List<RelativeFileSet> resourcesList =
                APP_RESOURCES_LIST.fetchFrom(params);
        if (resourcesList == null) {
            throw new RuntimeException(
                    I18N.getString("message.null-classpath"));
        }

        for (RelativeFileSet classPath : resourcesList) {
            File srcdir = classPath.getBaseDirectory();
            for (String fname : classPath.getIncludedFiles()) {
                copyEntry(javaDirectory, srcdir, fname);
            }
        }
    }

    private String getBundleName(Map<String, ? super Object> params) {
        if (MAC_CF_BUNDLE_NAME.fetchFrom(params) != null) {
            String bn = MAC_CF_BUNDLE_NAME.fetchFrom(params);
            if (bn.length() > 16) {
                Log.error(MessageFormat.format(I18N.getString(
                        "message.bundle-name-too-long-warning"),
                        MAC_CF_BUNDLE_NAME.getID(), bn));
            }
            return MAC_CF_BUNDLE_NAME.fetchFrom(params);
        } else if (APP_NAME.fetchFrom(params) != null) {
            return APP_NAME.fetchFrom(params);
        } else {
            String nm = MAIN_CLASS.fetchFrom(params);
            if (nm.length() > 16) {
                nm = nm.substring(0, 16);
            }
            return nm;
        }
    }

    private void writeRuntimeInfoPlist(File file) throws IOException {
        Map<String, String> data = new HashMap<>();
        String identifier = StandardBundlerParam.isRuntimeInstaller(params) ?
                MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params) :
                "com.oracle.java." + MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params);
        data.put("CF_BUNDLE_IDENTIFIER", identifier);
        String name = StandardBundlerParam.isRuntimeInstaller(params) ?
                getBundleName(params): "Java Runtime Image";
        data.put("CF_BUNDLE_NAME", name);
        data.put("CF_BUNDLE_VERSION", VERSION.fetchFrom(params));
        data.put("CF_BUNDLE_SHORT_VERSION_STRING", VERSION.fetchFrom(params));

        Writer w = new BufferedWriter(new FileWriter(file));
        w.write(preprocessTextResource("Runtime-Info.plist",
                I18N.getString("resource.runtime-info-plist"),
                TEMPLATE_RUNTIME_INFO_PLIST,
                data,
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params)));
        w.close();
    }

    private void writeInfoPlist(File file) throws IOException {
        Log.verbose(MessageFormat.format(I18N.getString(
                "message.preparing-info-plist"), file.getAbsolutePath()));

        //prepare config for exe
        //Note: do not need CFBundleDisplayName if we don't support localization
        Map<String, String> data = new HashMap<>();
        data.put("DEPLOY_ICON_FILE", APP_NAME.fetchFrom(params) + ".icns");
        data.put("DEPLOY_BUNDLE_IDENTIFIER",
                MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params));
        data.put("DEPLOY_BUNDLE_NAME",
                getBundleName(params));
        data.put("DEPLOY_BUNDLE_COPYRIGHT",
                COPYRIGHT.fetchFrom(params) != null ?
                COPYRIGHT.fetchFrom(params) : "Unknown");
        data.put("DEPLOY_LAUNCHER_NAME", getLauncherName(params));
        data.put("DEPLOY_JAVA_RUNTIME_NAME", "$APPDIR/runtime");
        data.put("DEPLOY_BUNDLE_SHORT_VERSION",
                VERSION.fetchFrom(params) != null ?
                VERSION.fetchFrom(params) : "1.0.0");
        data.put("DEPLOY_BUNDLE_CFBUNDLE_VERSION",
                MAC_CF_BUNDLE_VERSION.fetchFrom(params) != null ?
                MAC_CF_BUNDLE_VERSION.fetchFrom(params) : "100");
        data.put("DEPLOY_BUNDLE_CATEGORY", MAC_CATEGORY.fetchFrom(params));

        boolean hasMainJar = MAIN_JAR.fetchFrom(params) != null;
        boolean hasMainModule =
                StandardBundlerParam.MODULE.fetchFrom(params) != null;

        if (hasMainJar) {
            data.put("DEPLOY_MAIN_JAR_NAME", MAIN_JAR.fetchFrom(params).
                    getIncludedFiles().iterator().next());
        }
        else if (hasMainModule) {
            data.put("DEPLOY_MODULE_NAME",
                    StandardBundlerParam.MODULE.fetchFrom(params));
        }

        StringBuilder sb = new StringBuilder();
        List<String> jvmOptions = JAVA_OPTIONS.fetchFrom(params);

        String newline = ""; //So we don't add extra line after last append
        for (String o : jvmOptions) {
            sb.append(newline).append(
                    "    <string>").append(o).append("</string>");
            newline = "\n";
        }

        data.put("DEPLOY_JAVA_OPTIONS", sb.toString());

        sb = new StringBuilder();
        List<String> args = ARGUMENTS.fetchFrom(params);
        newline = "";
        // So we don't add unneccessary extra line after last append

        for (String o : args) {
            sb.append(newline).append("    <string>").append(o).append(
                    "</string>");
            newline = "\n";
        }
        data.put("DEPLOY_ARGUMENTS", sb.toString());

        newline = "";

        data.put("DEPLOY_LAUNCHER_CLASS", MAIN_CLASS.fetchFrom(params));

        StringBuilder macroedPath = new StringBuilder();
        for (String s : CLASSPATH.fetchFrom(params).split("[ ;:]+")) {
            macroedPath.append(s);
            macroedPath.append(":");
        }
        macroedPath.deleteCharAt(macroedPath.length() - 1);

        data.put("DEPLOY_APP_CLASSPATH", macroedPath.toString());

        StringBuilder bundleDocumentTypes = new StringBuilder();
        StringBuilder exportedTypes = new StringBuilder();
        for (Map<String, ? super Object>
                fileAssociation : FILE_ASSOCIATIONS.fetchFrom(params)) {

            List<String> extensions = FA_EXTENSIONS.fetchFrom(fileAssociation);

            if (extensions == null) {
                Log.verbose(I18N.getString(
                        "message.creating-association-with-null-extension"));
            }

            List<String> mimeTypes = FA_CONTENT_TYPE.fetchFrom(fileAssociation);
            String itemContentType = MAC_CF_BUNDLE_IDENTIFIER.fetchFrom(params)
                    + "." + ((extensions == null || extensions.isEmpty())
                    ? "mime" : extensions.get(0));
            String description = FA_DESCRIPTION.fetchFrom(fileAssociation);
            File icon = FA_ICON.fetchFrom(fileAssociation); //TODO FA_ICON_ICNS

            bundleDocumentTypes.append("    <dict>\n")
                    .append("      <key>LSItemContentTypes</key>\n")
                    .append("      <array>\n")
                    .append("        <string>")
                    .append(itemContentType)
                    .append("</string>\n")
                    .append("      </array>\n")
                    .append("\n")
                    .append("      <key>CFBundleTypeName</key>\n")
                    .append("      <string>")
                    .append(description)
                    .append("</string>\n")
                    .append("\n")
                    .append("      <key>LSHandlerRank</key>\n")
                    .append("      <string>Owner</string>\n")
                            // TODO make a bundler arg
                    .append("\n")
                    .append("      <key>CFBundleTypeRole</key>\n")
                    .append("      <string>Editor</string>\n")
                            // TODO make a bundler arg
                    .append("\n")
                    .append("      <key>LSIsAppleDefaultForType</key>\n")
                    .append("      <true/>\n")
                            // TODO make a bundler arg
                    .append("\n");

            if (icon != null && icon.exists()) {
                bundleDocumentTypes
                        .append("      <key>CFBundleTypeIconFile</key>\n")
                        .append("      <string>")
                        .append(icon.getName())
                        .append("</string>\n");
            }
            bundleDocumentTypes.append("    </dict>\n");

            exportedTypes.append("    <dict>\n")
                    .append("      <key>UTTypeIdentifier</key>\n")
                    .append("      <string>")
                    .append(itemContentType)
                    .append("</string>\n")
                    .append("\n")
                    .append("      <key>UTTypeDescription</key>\n")
                    .append("      <string>")
                    .append(description)
                    .append("</string>\n")
                    .append("      <key>UTTypeConformsTo</key>\n")
                    .append("      <array>\n")
                    .append("          <string>public.data</string>\n")
                            //TODO expose this?
                    .append("      </array>\n")
                    .append("\n");

            if (icon != null && icon.exists()) {
                exportedTypes.append("      <key>UTTypeIconFile</key>\n")
                        .append("      <string>")
                        .append(icon.getName())
                        .append("</string>\n")
                        .append("\n");
            }

            exportedTypes.append("\n")
                    .append("      <key>UTTypeTagSpecification</key>\n")
                    .append("      <dict>\n")
                            // TODO expose via param? .append(
                            // "        <key>com.apple.ostype</key>\n");
                            // TODO expose via param? .append(
                            // "        <string>ABCD</string>\n")
                    .append("\n");

            if (extensions != null && !extensions.isEmpty()) {
                exportedTypes.append(
                        "        <key>public.filename-extension</key>\n")
                        .append("        <array>\n");

                for (String ext : extensions) {
                    exportedTypes.append("          <string>")
                            .append(ext)
                            .append("</string>\n");
                }
                exportedTypes.append("        </array>\n");
            }
            if (mimeTypes != null && !mimeTypes.isEmpty()) {
                exportedTypes.append("        <key>public.mime-type</key>\n")
                        .append("        <array>\n");

                for (String mime : mimeTypes) {
                    exportedTypes.append("          <string>")
                            .append(mime)
                            .append("</string>\n");
                }
                exportedTypes.append("        </array>\n");
            }
            exportedTypes.append("      </dict>\n")
                    .append("    </dict>\n");
        }
        String associationData;
        if (bundleDocumentTypes.length() > 0) {
            associationData =
                    "\n  <key>CFBundleDocumentTypes</key>\n  <array>\n"
                    + bundleDocumentTypes.toString()
                    + "  </array>\n\n"
                    + "  <key>UTExportedTypeDeclarations</key>\n  <array>\n"
                    + exportedTypes.toString()
                    + "  </array>\n";
        } else {
            associationData = "";
        }
        data.put("DEPLOY_FILE_ASSOCIATIONS", associationData);


        Writer w = new BufferedWriter(new FileWriter(file));
        w.write(preprocessTextResource(
                // getConfig_InfoPlist(params).getName(),
                "Info.plist",
                I18N.getString("resource.app-info-plist"),
                TEMPLATE_INFO_PLIST_LITE,
                data, VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params)));
        w.close();
    }

    private void writePkgInfo(File file) throws IOException {
        //hardcoded as it does not seem we need to change it ever
        String signature = "????";

        try (Writer out = new BufferedWriter(new FileWriter(file))) {
            out.write(OS_TYPE_CODE + signature);
            out.flush();
        }
    }

    public static void addNewKeychain(Map<String, ? super Object> params)
                                    throws IOException, InterruptedException {
        if (Platform.getMajorVersion() < 10 ||
                (Platform.getMajorVersion() == 10 &&
                Platform.getMinorVersion() < 12)) {
            // we need this for OS X 10.12+
            return;
        }

        String keyChain = SIGNING_KEYCHAIN.fetchFrom(params);
        if (keyChain == null || keyChain.isEmpty()) {
            return;
        }

        // get current keychain list
        String keyChainPath = new File (keyChain).getAbsolutePath().toString();
        List<String> keychainList = new ArrayList<>();
        int ret = IOUtils.getProcessOutput(
                keychainList, "security", "list-keychains");
        if (ret != 0) {
            Log.error(I18N.getString("message.keychain.error"));
            return;
        }

        boolean contains = keychainList.stream().anyMatch(
                    str -> str.trim().equals("\""+keyChainPath.trim()+"\""));
        if (contains) {
            // keychain is already added in the search list
            return;
        }

        keyChains = new ArrayList<>();
        // remove "
        keychainList.forEach((String s) -> {
            String path = s.trim();
            if (path.startsWith("\"") && path.endsWith("\"")) {
                path = path.substring(1, path.length()-1);
            }
            keyChains.add(path);
        });

        List<String> args = new ArrayList<>();
        args.add("security");
        args.add("list-keychains");
        args.add("-s");

        args.addAll(keyChains);
        args.add(keyChain);

        ProcessBuilder  pb = new ProcessBuilder(args);
        IOUtils.exec(pb, false);
    }

    public static void restoreKeychainList(Map<String, ? super Object> params)
            throws IOException{
        if (Platform.getMajorVersion() < 10 ||
                (Platform.getMajorVersion() == 10 &&
                Platform.getMinorVersion() < 12)) {
            // we need this for OS X 10.12+
            return;
        }

        if (keyChains == null || keyChains.isEmpty()) {
            return;
        }

        List<String> args = new ArrayList<>();
        args.add("security");
        args.add("list-keychains");
        args.add("-s");

        args.addAll(keyChains);

        ProcessBuilder  pb = new ProcessBuilder(args);
        IOUtils.exec(pb, false);
    }

    public static void signAppBundle(
            Map<String, ? super Object> params, Path appLocation,
            String signingIdentity, String identifierPrefix,
            String entitlementsFile, String inheritedEntitlements)
            throws IOException {
        AtomicReference<IOException> toThrow = new AtomicReference<>();
        String appExecutable = "/Contents/MacOS/" + APP_NAME.fetchFrom(params);
        String keyChain = SIGNING_KEYCHAIN.fetchFrom(params);

        // sign all dylibs and jars
        Files.walk(appLocation)
                // fix permissions
                .peek(path -> {
                    try {
                        Set<PosixFilePermission> pfp =
                            Files.getPosixFilePermissions(path);
                        if (!pfp.contains(PosixFilePermission.OWNER_WRITE)) {
                            pfp = EnumSet.copyOf(pfp);
                            pfp.add(PosixFilePermission.OWNER_WRITE);
                            Files.setPosixFilePermissions(path, pfp);
                        }
                    } catch (IOException e) {
                        Log.debug(e);
                    }
                })
                .filter(p -> Files.isRegularFile(p) &&
                        !(p.toString().contains("/Contents/MacOS/libjli.dylib")
                        || p.toString().contains(
                                "/Contents/MacOS/JavaAppletPlugin")
                        || p.toString().endsWith(appExecutable))
                ).forEach(p -> {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (toThrow.get() != null) return;

            // If p is a symlink then skip the signing process.
            if (Files.isSymbolicLink(p)) {
                if (VERBOSE.fetchFrom(params)) {
                    Log.verbose(MessageFormat.format(I18N.getString(
                            "message.ignoring.symlink"), p.toString()));
                }
            }
            else {
                List<String> args = new ArrayList<>();
                args.addAll(Arrays.asList("codesign",
                        "-s", signingIdentity, // sign with this key
                        "--prefix", identifierPrefix,
                                // use the identifier as a prefix
                        "-vvvv"));
                if (entitlementsFile != null &&
                        (p.toString().endsWith(".jar")
                                || p.toString().endsWith(".dylib"))) {
                    args.add("--entitlements");
                    args.add(entitlementsFile); // entitlements
                } else if (inheritedEntitlements != null &&
                        Files.isExecutable(p)) {
                    args.add("--entitlements");
                    args.add(inheritedEntitlements);
                            // inherited entitlements for executable processes
                }
                if (keyChain != null && !keyChain.isEmpty()) {
                    args.add("--keychain");
                    args.add(keyChain);
                }
                args.add(p.toString());

                try {
                    Set<PosixFilePermission> oldPermissions =
                            Files.getPosixFilePermissions(p);
                    File f = p.toFile();
                    f.setWritable(true, true);

                    ProcessBuilder pb = new ProcessBuilder(args);
                    IOUtils.exec(pb, false);

                    Files.setPosixFilePermissions(p, oldPermissions);
                } catch (IOException ioe) {
                    toThrow.set(ioe);
                }
            }
        });

        IOException ioe = toThrow.get();
        if (ioe != null) {
            throw ioe;
        }

        // sign all runtime and frameworks
        Consumer<? super Path> signIdentifiedByPList = path -> {
            //noinspection ThrowableResultOfMethodCallIgnored
            if (toThrow.get() != null) return;

            try {
                List<String> args = new ArrayList<>();
                args.addAll(Arrays.asList("codesign",
                        "-s", signingIdentity, // sign with this key
                        "--prefix", identifierPrefix,
                                // use the identifier as a prefix
                        "-vvvv"));
                if (keyChain != null && !keyChain.isEmpty()) {
                    args.add("--keychain");
                    args.add(keyChain);
                }
                args.add(path.toString());
                ProcessBuilder pb = new ProcessBuilder(args);
                IOUtils.exec(pb, false);

                args = new ArrayList<>();
                args.addAll(Arrays.asList("codesign",
                        "-s", signingIdentity, // sign with this key
                        "--prefix", identifierPrefix,
                                // use the identifier as a prefix
                        "-vvvv"));
                if (keyChain != null && !keyChain.isEmpty()) {
                    args.add("--keychain");
                    args.add(keyChain);
                }
                args.add(path.toString()
                        + "/Contents/_CodeSignature/CodeResources");
                pb = new ProcessBuilder(args);
                IOUtils.exec(pb, false);
            } catch (IOException e) {
                toThrow.set(e);
            }
        };

        Path javaPath = appLocation.resolve("Contents/runtime");
        if (Files.isDirectory(javaPath)) {
            Files.list(javaPath)
                    .forEach(signIdentifiedByPList);

            ioe = toThrow.get();
            if (ioe != null) {
                throw ioe;
            }
        }
        Path frameworkPath = appLocation.resolve("Contents/Frameworks");
        if (Files.isDirectory(frameworkPath)) {
            Files.list(frameworkPath)
                    .forEach(signIdentifiedByPList);

            ioe = toThrow.get();
            if (ioe != null) {
                throw ioe;
            }
        }

        // sign the app itself
        List<String> args = new ArrayList<>();
        args.addAll(Arrays.asList("codesign",
                "-s", signingIdentity, // sign with this key
                "-vvvv")); // super verbose output
        if (entitlementsFile != null) {
            args.add("--entitlements");
            args.add(entitlementsFile); // entitlements
        }
        if (keyChain != null && !keyChain.isEmpty()) {
            args.add("--keychain");
            args.add(keyChain);
        }
        args.add(appLocation.toString());

        ProcessBuilder pb =
                new ProcessBuilder(args.toArray(new String[args.size()]));
        IOUtils.exec(pb, false);
    }

}
