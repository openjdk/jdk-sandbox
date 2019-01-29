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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jdk.jpackage.internal.WindowsBundlerParam.*;

public class WinMsiBundler  extends AbstractBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.WinResources");

    public static final BundlerParamInfo<WinAppBundler> APP_BUNDLER =
            new WindowsBundlerParam<>(
            I18N.getString("param.msi-bundler.name"),
            I18N.getString("param.msi-bundler.description"),
            "win.app.bundler",
            WinAppBundler.class,
            params -> new WinAppBundler(),
            null);

    public static final BundlerParamInfo<Boolean> CAN_USE_WIX36 =
            new WindowsBundlerParam<>(
            I18N.getString("param.can-use-wix36.name"),
            I18N.getString("param.can-use-wix36.description"),
            "win.msi.canUseWix36",
            Boolean.class,
            params -> false,
            (s, p) -> Boolean.valueOf(s));

    public static final BundlerParamInfo<File> MSI_IMAGE_DIR =
            new WindowsBundlerParam<>(
            I18N.getString("param.image-dir.name"),
            I18N.getString("param.image-dir.description"),
            "win.msi.imageDir",
            File.class,
            params -> {
                File imagesRoot = IMAGES_ROOT.fetchFrom(params);
                if (!imagesRoot.exists()) imagesRoot.mkdirs();
                return new File(imagesRoot, "win-msi.image");
            },
            (s, p) -> null);

    public static final BundlerParamInfo<File> WIN_APP_IMAGE =
            new WindowsBundlerParam<>(
            I18N.getString("param.app-dir.name"),
            I18N.getString("param.app-dir.description"),
            "win.app.image",
            File.class,
            null,
            (s, p) -> null);

    public static final StandardBundlerParam<Boolean> MSI_SYSTEM_WIDE  =
            new StandardBundlerParam<>(
                    I18N.getString("param.system-wide.name"),
                    I18N.getString("param.system-wide.description"),
                    Arguments.CLIOptions.WIN_PER_USER_INSTALLATION.getId(),
                    Boolean.class,
                    params -> true, // MSIs default to system wide
                    // valueOf(null) is false,
                    // and we actually do want null
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s))? null
                            : Boolean.valueOf(s)
            );


    public static final StandardBundlerParam<String> PRODUCT_VERSION =
            new StandardBundlerParam<>(
                    I18N.getString("param.product-version.name"),
                    I18N.getString("param.product-version.description"),
                    "win.msi.productVersion",
                    String.class,
                    VERSION::fetchFrom,
                    (s, p) -> s
            );

    public static final BundlerParamInfo<UUID> UPGRADE_UUID =
            new WindowsBundlerParam<>(
            I18N.getString("param.upgrade-uuid.name"),
            I18N.getString("param.upgrade-uuid.description"),
            Arguments.CLIOptions.WIN_UPGRADE_UUID.getId(),
            UUID.class,
            params -> UUID.randomUUID(),
            (s, p) -> UUID.fromString(s));

    private static final String TOOL_CANDLE = "candle.exe";
    private static final String TOOL_LIGHT = "light.exe";
    // autodetect just v3.7, v3.8, 3.9, 3.10 and 3.11
    private static final String AUTODETECT_DIRS =
            ";C:\\Program Files (x86)\\WiX Toolset v3.11\\bin;"
            + "C:\\Program Files\\WiX Toolset v3.11\\bin;"
            + "C:\\Program Files (x86)\\WiX Toolset v3.10\\bin;"
            + "C:\\Program Files\\WiX Toolset v3.10\\bin;"
            + "C:\\Program Files (x86)\\WiX Toolset v3.9\\bin;"
            + "C:\\Program Files\\WiX Toolset v3.9\\bin;"
            + "C:\\Program Files (x86)\\WiX Toolset v3.8\\bin;"
            + "C:\\Program Files\\WiX Toolset v3.8\\bin;"
            + "C:\\Program Files (x86)\\WiX Toolset v3.7\\bin;"
            + "C:\\Program Files\\WiX Toolset v3.7\\bin";

    public static final BundlerParamInfo<String> TOOL_CANDLE_EXECUTABLE =
            new WindowsBundlerParam<>(
            I18N.getString("param.candle-path.name"),
            I18N.getString("param.candle-path.description"),
            "win.msi.candle.exe",
            String.class,
            params -> {
                for (String dirString : (System.getenv("PATH") +
                        AUTODETECT_DIRS).split(";")) {
                    File f = new File(dirString.replace("\"", ""), TOOL_CANDLE);
                    if (f.isFile()) {
                        return f.toString();
                    }
                }
                return null;
            },
            null);

    public static final BundlerParamInfo<String> TOOL_LIGHT_EXECUTABLE =
            new WindowsBundlerParam<>(
            I18N.getString("param.light-path.name"),
            I18N.getString("param.light-path.description"),
            "win.msi.light.exe",
            String.class,
            params -> {
                for (String dirString : (System.getenv("PATH") +
                        AUTODETECT_DIRS).split(";")) {
                    File f = new File(dirString.replace("\"", ""), TOOL_LIGHT);
                    if (f.isFile()) {
                        return f.toString();
                    }
                }
                return null;
            },
            null);

    public static final StandardBundlerParam<Boolean> MENU_HINT =
        new WindowsBundlerParam<>(
                I18N.getString("param.menu-shortcut-hint.name"),
                I18N.getString("param.menu-shortcut-hint.description"),
                Arguments.CLIOptions.WIN_MENU_HINT.getId(),
                Boolean.class,
                params -> false,
                // valueOf(null) is false,
                // and we actually do want null in some cases
                (s, p) -> (s == null ||
                        "null".equalsIgnoreCase(s))? true : Boolean.valueOf(s)
        );

    public static final StandardBundlerParam<Boolean> SHORTCUT_HINT =
        new WindowsBundlerParam<>(
                I18N.getString("param.desktop-shortcut-hint.name"),
                I18N.getString("param.desktop-shortcut-hint.description"),
                Arguments.CLIOptions.WIN_SHORTCUT_HINT.getId(),
                Boolean.class,
                params -> false,
                // valueOf(null) is false,
                // and we actually do want null in some cases
                (s, p) -> (s == null ||
                       "null".equalsIgnoreCase(s))? false : Boolean.valueOf(s)
        );

    @Override
    public String getName() {
        return I18N.getString("msi.bundler.name");
    }

    @Override
    public String getDescription() {
        return I18N.getString("msi.bundler.description");
    }

    @Override
    public String getID() {
        return "msi";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(WinAppBundler.getAppBundleParameters());
        results.addAll(getMsiBundleParameters());
        return results;
    }

    public static Collection<BundlerParamInfo<?>> getMsiBundleParameters() {
        return Arrays.asList(
                DESCRIPTION,
                MENU_GROUP,
                MENU_HINT,
                PRODUCT_VERSION,
                SHORTCUT_HINT,
                MSI_SYSTEM_WIDE,
                VENDOR,
                LICENSE_FILE,
                INSTALLDIR_CHOOSER
        );
    }

    @Override
    public File execute(
            Map<String, ? super Object> params, File outputParentDir) {
        return bundle(params, outputParentDir);
    }

    @Override
    public boolean supported() {
        return (Platform.getPlatform() == Platform.WINDOWS);
    }

    static class VersionExtractor extends PrintStream {
        double version = 0f;

        public VersionExtractor() {
            super(new ByteArrayOutputStream());
        }

        double getVersion() {
            if (version == 0f) {
                String content =
                        new String(((ByteArrayOutputStream) out).toByteArray());
                Pattern pattern = Pattern.compile("version (\\d+.\\d+)");
                Matcher matcher = pattern.matcher(content);
                if (matcher.find()) {
                    String v = matcher.group(1);
                    version = Double.parseDouble(v);
                }
            }
            return version;
        }
    }

    private static double findToolVersion(String toolName) {
        try {
            if (toolName == null || "".equals(toolName)) return 0f;

            ProcessBuilder pb = new ProcessBuilder(
                    toolName,
                    "/?");
            VersionExtractor ve = new VersionExtractor();
            // not interested in the output
            IOUtils.exec(pb, Log.isDebug(), true, ve);
            double version = ve.getVersion();
            Log.verbose(MessageFormat.format(
                    I18N.getString("message.tool-version"),
                    toolName, version));
            return version;
        } catch (Exception e) {
            if (Log.isDebug()) {
                Log.verbose(e);
            }
            return 0f;
        }
    }

    @Override
    public boolean validate(Map<String, ? super Object> p)
            throws UnsupportedPlatformException, ConfigException {
        try {
            if (p == null) throw new ConfigException(
                    I18N.getString("error.parameters-null"),
                    I18N.getString("error.parameters-null.advice"));

            // run basic validation to ensure requirements are met
            // we are not interested in return code, only possible exception
            APP_BUNDLER.fetchFrom(p).validate(p);

            double candleVersion =
                    findToolVersion(TOOL_CANDLE_EXECUTABLE.fetchFrom(p));
            double lightVersion =
                    findToolVersion(TOOL_LIGHT_EXECUTABLE.fetchFrom(p));

            // WiX 3.0+ is required
            double minVersion = 3.0f;
            boolean bad = false;

            if (candleVersion < minVersion) {
                Log.verbose(MessageFormat.format(
                        I18N.getString("message.wrong-tool-version"),
                        TOOL_CANDLE, candleVersion, minVersion));
                bad = true;
            }
            if (lightVersion < minVersion) {
                Log.verbose(MessageFormat.format(
                        I18N.getString("message.wrong-tool-version"),
                        TOOL_LIGHT, lightVersion, minVersion));
                bad = true;
            }

            if (bad){
                throw new ConfigException(
                        I18N.getString("error.no-wix-tools"),
                        I18N.getString("error.no-wix-tools.advice"));
            }

            if (lightVersion >= 3.6f) {
                Log.verbose(I18N.getString("message.use-wix36-features"));
                p.put(CAN_USE_WIX36.getID(), Boolean.TRUE);
            }

            /********* validate bundle parameters *************/

            String version = PRODUCT_VERSION.fetchFrom(p);
            if (!isVersionStringValid(version)) {
                throw new ConfigException(
                        MessageFormat.format(I18N.getString(
                                "error.version-string-wrong-format"), version),
                        MessageFormat.format(I18N.getString(
                                "error.version-string-wrong-format.advice"),
                                PRODUCT_VERSION.getID()));
            }

            // only one mime type per association, at least one file extension
            List<Map<String, ? super Object>> associations =
                    FILE_ASSOCIATIONS.fetchFrom(p);
            if (associations != null) {
                for (int i = 0; i < associations.size(); i++) {
                    Map<String, ? super Object> assoc = associations.get(i);
                    List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
                    if (mimes.size() > 1) {
                        throw new ConfigException(MessageFormat.format(
                                I18N.getString("error.too-many-content-"
                                + "types-for-file-association"), i),
                                I18N.getString("error.too-many-content-"
                                + "types-for-file-association.advice"));
                    }
                }
            }

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    // http://msdn.microsoft.com/en-us/library/aa370859%28v=VS.85%29.aspx
    // The format of the string is as follows:
    //     major.minor.build
    // The first field is the major version and has a maximum value of 255.
    // The second field is the minor version and has a maximum value of 255.
    // The third field is called the build version or the update version and
    // has a maximum value of 65,535.
    static boolean isVersionStringValid(String v) {
        if (v == null) {
            return true;
        }

        String p[] = v.split("\\.");
        if (p.length > 3) {
            Log.verbose(I18N.getString(
                    "message.version-string-too-many-components"));
            return false;
        }

        try {
            int val = Integer.parseInt(p[0]);
            if (val < 0 || val > 255) {
                Log.verbose(I18N.getString(
                        "error.version-string-major-out-of-range"));
                return false;
            }
            if (p.length > 1) {
                val = Integer.parseInt(p[1]);
                if (val < 0 || val > 255) {
                    Log.verbose(I18N.getString(
                            "error.version-string-minor-out-of-range"));
                    return false;
                }
            }
            if (p.length > 2) {
                val = Integer.parseInt(p[2]);
                if (val < 0 || val > 65535) {
                    Log.verbose(I18N.getString(
                            "error.version-string-build-out-of-range"));
                    return false;
                }
            }
        } catch (NumberFormatException ne) {
            Log.verbose(I18N.getString("error.version-string-part-not-number"));
            Log.verbose(ne);
            return false;
        }

        return true;
    }

    private boolean prepareProto(Map<String, ? super Object> p)
                throws IOException {
        File appImage = StandardBundlerParam.getPredefinedAppImage(p);
        File appDir = null;

        // we either have an application image or need to build one
        if (appImage != null) {
            appDir = new File(
                    MSI_IMAGE_DIR.fetchFrom(p), APP_NAME.fetchFrom(p));
            // copy everything from appImage dir into appDir/name
            IOUtils.copyRecursive(appImage.toPath(), appDir.toPath());
        } else {
            appDir = APP_BUNDLER.fetchFrom(p).doBundle(p,
                    MSI_IMAGE_DIR.fetchFrom(p), true);
        }

        p.put(WIN_APP_IMAGE.getID(), appDir);

        String licenseFile = LICENSE_FILE.fetchFrom(p);
        if (licenseFile != null) {
            // need to copy license file to the working directory and convert to rtf if needed
            File lfile = new File(licenseFile);
            File destFile = new File(CONFIG_ROOT.fetchFrom(p), lfile.getName());
            IOUtils.copyFile(lfile, destFile);
            ensureByMutationFileIsRTF(destFile);
        }

        // copy file association icons
        List<Map<String, ? super Object>> fileAssociations =
                FILE_ASSOCIATIONS.fetchFrom(p);
        for (Map<String, ? super Object> fa : fileAssociations) {
            File icon = FA_ICON.fetchFrom(fa); // TODO FA_ICON_ICO
            if (icon == null) {
                continue;
            }

            File faIconFile = new File(appDir, icon.getName());

            if (icon.exists()) {
                try {
                    IOUtils.copyFile(icon, faIconFile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return appDir != null;
    }

    public File bundle(Map<String, ? super Object> p, File outdir) {
        if (!outdir.isDirectory() && !outdir.mkdirs()) {
            throw new RuntimeException(MessageFormat.format(
                    I18N.getString("error.cannot-create-output-dir"),
                    outdir.getAbsolutePath()));
        }
        if (!outdir.canWrite()) {
            throw new RuntimeException(MessageFormat.format(
                    I18N.getString("error.cannot-write-to-output-dir"),
                    outdir.getAbsolutePath()));
        }

        // validate we have valid tools before continuing
        String light = TOOL_LIGHT_EXECUTABLE.fetchFrom(p);
        String candle = TOOL_CANDLE_EXECUTABLE.fetchFrom(p);
        if (light == null || !new File(light).isFile() ||
            candle == null || !new File(candle).isFile()) {
            Log.error(I18N.getString("error.no-wix-tools"));
            Log.verbose(MessageFormat.format(
                   I18N.getString("message.light-file-string"), light));
            Log.verbose(MessageFormat.format(
                   I18N.getString("message.candle-file-string"), candle));
            return null;
        }

        File imageDir = MSI_IMAGE_DIR.fetchFrom(p);
        try {
            imageDir.mkdirs();

            boolean menuShortcut = MENU_HINT.fetchFrom(p);
            boolean desktopShortcut = SHORTCUT_HINT.fetchFrom(p);
            if (!menuShortcut && !desktopShortcut) {
                // both can not be false - user will not find the app
                Log.verbose(I18N.getString("message.one-shortcut-required"));
                p.put(MENU_HINT.getID(), true);
            }

            if (prepareProto(p) && prepareWiXConfig(p)
                    && prepareBasicProjectConfig(p)) {
                File configScriptSrc = getConfig_Script(p);
                if (configScriptSrc.exists()) {
                    // we need to be running post script in the image folder

                    // NOTE: Would it be better to generate it to the image
                    // folder and save only if "verbose" is requested?

                    // for now we replicate it
                    File configScript =
                        new File(imageDir, configScriptSrc.getName());
                    IOUtils.copyFile(configScriptSrc, configScript);
                    Log.verbose(MessageFormat.format(
                            I18N.getString("message.running-wsh-script"),
                            configScript.getAbsolutePath()));
                    IOUtils.run("wscript",
                             configScript, false);
                }
                return buildMSI(p, outdir);
            }
            return null;
        } catch (IOException ex) {
            Log.verbose(ex);
            return null;
        }
    }

    // name of post-image script
    private File getConfig_Script(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-post-image.wsf");
    }

    private boolean prepareBasicProjectConfig(
        Map<String, ? super Object> params) throws IOException {
        fetchResource(getConfig_Script(params).getName(),
                I18N.getString("resource.post-install-script"),
                (String) null,
                getConfig_Script(params),
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));
        return true;
    }

    private String relativePath(File basedir, File file) {
        return file.getAbsolutePath().substring(
                basedir.getAbsolutePath().length() + 1);
    }

    boolean prepareMainProjectFile(
            Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = new HashMap<>();

        UUID productGUID = UUID.randomUUID();

        Log.verbose(MessageFormat.format(
                I18N.getString("message.generated-product-guid"),
                productGUID.toString()));

        // we use random GUID for product itself but
        // user provided for upgrade guid
        // Upgrade guid is important to decide whether it is an upgrade of
        // installed app.  I.e. we need it to be the same for
        // 2 different versions of app if possible
        data.put("PRODUCT_GUID", productGUID.toString());
        data.put("PRODUCT_UPGRADE_GUID",
                UPGRADE_UUID.fetchFrom(params).toString());

        data.put("APPLICATION_NAME", APP_NAME.fetchFrom(params));
        data.put("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(params));
        data.put("APPLICATION_VENDOR", VENDOR.fetchFrom(params));
        data.put("APPLICATION_VERSION", PRODUCT_VERSION.fetchFrom(params));

        // WinAppBundler will add application folder again => step out
        File imageRootDir = WIN_APP_IMAGE.fetchFrom(params);
        File launcher = new File(imageRootDir,
                WinAppBundler.getLauncherName(params));

        String launcherPath = relativePath(imageRootDir, launcher);
        data.put("APPLICATION_LAUNCHER", launcherPath);

        String iconPath = launcherPath.replace(".exe", ".ico");

        data.put("APPLICATION_ICON", iconPath);

        data.put("REGISTRY_ROOT", getRegistryRoot(params));

        boolean canUseWix36Features = CAN_USE_WIX36.fetchFrom(params);
        data.put("WIX36_ONLY_START",
                canUseWix36Features ? "" : "<!--");
        data.put("WIX36_ONLY_END",
                canUseWix36Features ? "" : "-->");

        if (MSI_SYSTEM_WIDE.fetchFrom(params)) {
            data.put("INSTALL_SCOPE", "perMachine");
        } else {
            data.put("INSTALL_SCOPE", "perUser");
        }

        if (BIT_ARCH_64.fetchFrom(params)) {
            data.put("PLATFORM", "x64");
            data.put("WIN64", "yes");
        } else {
            data.put("PLATFORM", "x86");
            data.put("WIN64", "no");
        }

        data.put("UI_BLOCK", getUIBlock(params));

        List<Map<String, ? super Object>> secondaryLaunchers =
                SECONDARY_LAUNCHERS.fetchFrom(params);

        StringBuilder secondaryLauncherIcons = new StringBuilder();
        for (int i = 0; i < secondaryLaunchers.size(); i++) {
            Map<String, ? super Object> sl = secondaryLaunchers.get(i);
            // <Icon Id="DesktopIcon.exe" SourceFile="APPLICATION_ICON" />
            if (SHORTCUT_HINT.fetchFrom(sl) || MENU_HINT.fetchFrom(sl)) {
                File secondaryLauncher = new File(imageRootDir,
                        WinAppBundler.getLauncherName(sl));
                String secondaryLauncherPath =
                        relativePath(imageRootDir, secondaryLauncher);
                String secondaryLauncherIconPath =
                        secondaryLauncherPath.replace(".exe", ".ico");

                secondaryLauncherIcons.append("        <Icon Id=\"Launcher");
                secondaryLauncherIcons.append(i);
                secondaryLauncherIcons.append(".exe\" SourceFile=\"");
                secondaryLauncherIcons.append(secondaryLauncherIconPath);
                secondaryLauncherIcons.append("\" />\r\n");
            }
        }
        data.put("SECONDARY_LAUNCHER_ICONS", secondaryLauncherIcons.toString());

        String wxs = Arguments.CREATE_JRE_INSTALLER.fetchFrom(params) ?
                MSI_PROJECT_TEMPLATE_SERVER_JRE : MSI_PROJECT_TEMPLATE;

        Writer w = new BufferedWriter(
                new FileWriter(getConfig_ProjectFile(params)));

        String content = preprocessTextResource(
                getConfig_ProjectFile(params).getName(),
                I18N.getString("resource.wix-config-file"),
                wxs, data, VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));
        w.write(content);
        w.close();
        return true;
    }
    private int id;
    private int compId;
    private final static String LAUNCHER_ID = "LauncherId";
    private final static String LAUNCHER_SVC_ID = "LauncherSvcId";

    /**
     * Overrides the dialog sequence in built-in dialog set "WixUI_InstallDir"
     * to exclude license dialog
     */
    private static final String TWEAK_FOR_EXCLUDING_LICENSE =
              "     <Publish Dialog=\"WelcomeDlg\" Control=\"Next\""
            + "              Event=\"NewDialog\" Value=\"InstallDirDlg\""
            + " Order=\"2\"> 1"
            + "     </Publish>\n"
            + "     <Publish Dialog=\"InstallDirDlg\" Control=\"Back\""
            + "              Event=\"NewDialog\" Value=\"WelcomeDlg\""
            + " Order=\"2\"> 1"
            + "     </Publish>\n";

    /**
     * Creates UI element using WiX built-in dialog sets
     *     - WixUI_InstallDir/WixUI_Minimal.
     * The dialog sets are the closest to what we want to implement.
     *
     * WixUI_Minimal for license dialog only
     * WixUI_InstallDir for installdir dialog only or for both
     * installdir/license dialogs
     */
    private String getUIBlock(Map<String, ? super Object> params) {
        String uiBlock = "     <UI/>\n"; // UI-less element

        if (INSTALLDIR_CHOOSER.fetchFrom(params)) {
            boolean enableTweakForExcludingLicense =
                    (getLicenseFile(params) == null);
            uiBlock = "     <UI>\n"
                    + "     <Property Id=\"WIXUI_INSTALLDIR\""
                    + " Value=\"APPLICATIONFOLDER\" />\n"
                    + "     <UIRef Id=\"WixUI_InstallDir\" />\n"
                    + (enableTweakForExcludingLicense ?
                            TWEAK_FOR_EXCLUDING_LICENSE : "")
                    +"     </UI>\n";
        } else if (getLicenseFile(params) != null) {
            uiBlock = "     <UI>\n"
                    + "     <UIRef Id=\"WixUI_Minimal\" />\n"
                    + "     </UI>\n";
        }

        return uiBlock;
    }

    private void walkFileTree(Map<String, ? super Object> params,
            File root, PrintStream out, String prefix) {
        List<File> dirs = new ArrayList<>();
        List<File> files = new ArrayList<>();

        if (!root.isDirectory()) {
            throw new RuntimeException(
                    MessageFormat.format(
                            I18N.getString("error.cannot-walk-directory"),
                            root.getAbsolutePath()));
        }

        // sort to files and dirs
        File[] children = root.listFiles();
        if (children != null) {
            for (File f : children) {
                if (f.isDirectory()) {
                    dirs.add(f);
                } else {
                    files.add(f);
                }
            }
        }

        // have files => need to output component
        out.println(prefix + " <Component Id=\"comp" + (compId++)
                + "\" DiskId=\"1\""
                + " Guid=\"" + UUID.randomUUID().toString() + "\""
                + (BIT_ARCH_64.fetchFrom(params) ? " Win64=\"yes\"" : "")
                + ">");
        out.println(prefix + "  <CreateFolder/>");
        out.println(prefix + "  <RemoveFolder Id=\"RemoveDir"
                + (id++) + "\" On=\"uninstall\" />");

        boolean needRegistryKey = !MSI_SYSTEM_WIDE.fetchFrom(params);
        File imageRootDir = WIN_APP_IMAGE.fetchFrom(params);
        File launcherFile =
                new File(imageRootDir, WinAppBundler.getLauncherName(params));

        // Find out if we need to use registry. We need it if
        //  - we doing user level install as file can not serve as KeyPath
        //  - if we adding shortcut in this component

        for (File f: files) {
            boolean isLauncher = f.equals(launcherFile);
            if (isLauncher) {
                needRegistryKey = true;
            }
        }

        if (needRegistryKey) {
            // has to be under HKCU to make WiX happy
            out.println(prefix + "    <RegistryKey Root=\"HKCU\" "
                    + " Key=\"Software\\" + VENDOR.fetchFrom(params) + "\\"
                    + APP_NAME.fetchFrom(params) + "\""
                    + (CAN_USE_WIX36.fetchFrom(params) ?
                    ">" : " Action=\"createAndRemoveOnUninstall\">"));
            out.println(prefix
                    + "     <RegistryValue Name=\"Version\" Value=\""
                    + VERSION.fetchFrom(params)
                    + "\" Type=\"string\" KeyPath=\"yes\"/>");
            out.println(prefix + "   </RegistryKey>");
        }

        boolean menuShortcut = MENU_HINT.fetchFrom(params);
        boolean desktopShortcut = SHORTCUT_HINT.fetchFrom(params);

        Map<String, String> idToFileMap = new TreeMap<>();
        boolean launcherSet = false;

        for (File f : files) {
            boolean isLauncher = f.equals(launcherFile);

            launcherSet = launcherSet || isLauncher;

            boolean doShortcuts =
                isLauncher && (menuShortcut || desktopShortcut);

            String thisFileId = isLauncher ? LAUNCHER_ID : ("FileId" + (id++));
            idToFileMap.put(f.getName(), thisFileId);

            out.println(prefix + "   <File Id=\"" +
                    thisFileId + "\""
                    + " Name=\"" + f.getName() + "\" "
                    + " Source=\"" + relativePath(imageRootDir, f) + "\""
                    + (BIT_ARCH_64.fetchFrom(params) ?
                    " ProcessorArchitecture=\"x64\"" : "") + ">");
            if (doShortcuts && desktopShortcut) {
                out.println(prefix
                        + "  <Shortcut Id=\"desktopShortcut\" Directory="
                        + "\"DesktopFolder\""
                        + " Name=\"" + APP_NAME.fetchFrom(params)
                        + "\" WorkingDirectory=\"INSTALLDIR\""
                        + " Advertise=\"no\" Icon=\"DesktopIcon.exe\""
                        + " IconIndex=\"0\" />");
            }
            if (doShortcuts && menuShortcut) {
                out.println(prefix
                        + "     <Shortcut Id=\"ExeShortcut\" Directory="
                        + "\"ProgramMenuDir\""
                        + " Name=\"" + APP_NAME.fetchFrom(params)
                        + "\" Advertise=\"no\" Icon=\"StartMenuIcon.exe\""
                        + " IconIndex=\"0\" />");
            }

            List<Map<String, ? super Object>> secondaryLaunchers =
                    SECONDARY_LAUNCHERS.fetchFrom(params);
            for (int i = 0; i < secondaryLaunchers.size(); i++) {
                Map<String, ? super Object> sl = secondaryLaunchers.get(i);
                File secondaryLauncherFile = new File(imageRootDir,
                        WinAppBundler.getLauncherName(sl));
                if (f.equals(secondaryLauncherFile)) {
                    if (SHORTCUT_HINT.fetchFrom(sl)) {
                        out.println(prefix
                                + "  <Shortcut Id=\"desktopShortcut"
                                + i + "\" Directory=\"DesktopFolder\""
                                + " Name=\"" + APP_NAME.fetchFrom(sl)
                                + "\" WorkingDirectory=\"INSTALLDIR\""
                                + " Advertise=\"no\" Icon=\"Launcher"
                                + i + ".exe\" IconIndex=\"0\" />");
                    }
                    if (MENU_HINT.fetchFrom(sl)) {
                        out.println(prefix
                                + "     <Shortcut Id=\"ExeShortcut"
                                + i + "\" Directory=\"ProgramMenuDir\""
                                + " Name=\"" + APP_NAME.fetchFrom(sl)
                                + "\" Advertise=\"no\" Icon=\"Launcher"
                                + i + ".exe\" IconIndex=\"0\" />");
                        // Should we allow different menu groups?  Not for now.
                    }
                }
            }
            out.println(prefix + "   </File>");
        }

        if (launcherSet) {
            List<Map<String, ? super Object>> fileAssociations =
                FILE_ASSOCIATIONS.fetchFrom(params);
            String regName = APP_REGISTRY_NAME.fetchFrom(params);
            Set<String> defaultedMimes = new TreeSet<>();
            int count = 0;
            for (Map<String, ? super Object> fa : fileAssociations) {
                String description = FA_DESCRIPTION.fetchFrom(fa);
                List<String> extensions = FA_EXTENSIONS.fetchFrom(fa);
                List<String> mimeTypes = FA_CONTENT_TYPE.fetchFrom(fa);
                File icon = FA_ICON.fetchFrom(fa); // TODO FA_ICON_ICO

                String mime = (mimeTypes == null ||
                    mimeTypes.isEmpty()) ? null : mimeTypes.get(0);

                if (extensions == null) {
                    Log.verbose(I18N.getString(
                          "message.creating-association-with-null-extension"));

                    String entryName = regName + "File";
                    if (count > 0) {
                        entryName += "." + count;
                    }
                    count++;
                    out.print(prefix + "   <ProgId Id='" + entryName
                            + "' Description='" + description + "'");
                    if (icon != null && icon.exists()) {
                        out.print(" Icon='" + idToFileMap.get(icon.getName())
                                + "' IconIndex='0'");
                    }
                    out.println(" />");
                } else {
                    for (String ext : extensions) {
                        String entryName = regName + "File";
                        if (count > 0) {
                            entryName += "." + count;
                        }
                        count++;

                        out.print(prefix + "   <ProgId Id='" + entryName
                                + "' Description='" + description + "'");
                        if (icon != null && icon.exists()) {
                            out.print(" Icon='"
                                    + idToFileMap.get(icon.getName())
                                    + "' IconIndex='0'");
                        }
                        out.println(">");

                        if (extensions == null) {
                            Log.verbose(I18N.getString(
                            "message.creating-association-with-null-extension"));
                        } else {
                            out.print(prefix + "    <Extension Id='"
                                    + ext + "' Advertise='no'");
                            if (mime == null) {
                                out.println(">");
                            } else {
                                out.println(" ContentType='" + mime + "'>");
                                if (!defaultedMimes.contains(mime)) {
                                    out.println(prefix
                                            + "      <MIME ContentType='"
                                            + mime + "' Default='yes' />");
                                    defaultedMimes.add(mime);
                                }
                            }
                            out.println(prefix
                                    + "      <Verb Id='open' Command='Open' "
                                    + "TargetFile='" + LAUNCHER_ID
                                    + "' Argument='\"%1\"' />");
                            out.println(prefix + "    </Extension>");
                        }
                        out.println(prefix + "   </ProgId>");
                    }
                }
            }
        }

        out.println(prefix + " </Component>");

        for (File d : dirs) {
            out.println(prefix + " <Directory Id=\"dirid" + (id++)
                    + "\" Name=\"" + d.getName() + "\">");
            walkFileTree(params, d, out, prefix + " ");
            out.println(prefix + " </Directory>");
        }
    }

    String getRegistryRoot(Map<String, ? super Object> params) {
        if (MSI_SYSTEM_WIDE.fetchFrom(params)) {
            return "HKLM";
        } else {
            return "HKCU";
        }
    }

    boolean prepareContentList(Map<String, ? super Object> params)
            throws FileNotFoundException {
        File f = new File(
                CONFIG_ROOT.fetchFrom(params), MSI_PROJECT_CONTENT_FILE);
        PrintStream out = new PrintStream(f);

        // opening
        out.println("<?xml version=\"1.0\" encoding=\"UTF-8\" ?>");
        out.println("<Include>");

        out.println(" <Directory Id=\"TARGETDIR\" Name=\"SourceDir\">");
        if (MSI_SYSTEM_WIDE.fetchFrom(params)) {
            // install to programfiles
            if (BIT_ARCH_64.fetchFrom(params)) {
                out.println("  <Directory Id=\"ProgramFiles64Folder\" "
                        + "Name=\"PFiles\">");
            } else {
                out.println("  <Directory Id=\"ProgramFilesFolder\" "
                        + "Name=\"PFiles\">");
            }
        } else {
            // install to user folder
            out.println(
                    "  <Directory Name=\"AppData\" Id=\"LocalAppDataFolder\">");
        }
        out.println("   <Directory Id=\"APPLICATIONFOLDER\" Name=\""
                + APP_NAME.fetchFrom(params) + "\">");

        // dynamic part
        id = 0;
        compId = 0; // reset counters
        walkFileTree(params, WIN_APP_IMAGE.fetchFrom(params), out, "    ");

        // closing
        out.println("   </Directory>");
        out.println("  </Directory>");

        // for shortcuts
        if (SHORTCUT_HINT.fetchFrom(params)) {
            out.println("  <Directory Id=\"DesktopFolder\" />");
        }
        if (MENU_HINT.fetchFrom(params)) {
            out.println("  <Directory Id=\"ProgramMenuFolder\">");
            out.println("    <Directory Id=\"ProgramMenuDir\" Name=\""
                    + MENU_GROUP.fetchFrom(params) + "\">");
            out.println("      <Component Id=\"comp" + (compId++) + "\""
                    + " Guid=\"" + UUID.randomUUID().toString() + "\""
                    + (BIT_ARCH_64.fetchFrom(params) ? " Win64=\"yes\"" : "")
                    + ">");
            out.println("        <RemoveFolder Id=\"ProgramMenuDir\" "
                    + "On=\"uninstall\" />");
            // This has to be under HKCU to make WiX happy.
            // There are numberous discussions on this amoung WiX users
            // (if user A installs and user B uninstalls key is left behind)
            // there are suggested workarounds but none of them are appealing.
            // Leave it for now
            out.println(
                    "         <RegistryValue Root=\"HKCU\" Key=\"Software\\"
                    + VENDOR.fetchFrom(params) + "\\"
                    + APP_NAME.fetchFrom(params)
                    + "\" Type=\"string\" Value=\"\" />");
            out.println("      </Component>");
            out.println("    </Directory>");
            out.println(" </Directory>");
        }

        out.println(" </Directory>");

        out.println(" <Feature Id=\"DefaultFeature\" "
                + "Title=\"Main Feature\" Level=\"1\">");
        for (int j = 0; j < compId; j++) {
            out.println("    <ComponentRef Id=\"comp" + j + "\" />");
        }
        // component is defined in the template.wsx
        out.println("    <ComponentRef Id=\"CleanupMainApplicationFolder\" />");
        out.println(" </Feature>");
        out.println("</Include>");

        out.close();
        return true;
    }

    private File getConfig_ProjectFile(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + ".wxs");
    }

    private String getLicenseFile(Map<String, ? super Object> p) {
        String licenseFile = LICENSE_FILE.fetchFrom(p);
        if (licenseFile != null) {
            File lfile = new File(licenseFile);
            File destFile = new File(CONFIG_ROOT.fetchFrom(p), lfile.getName());
            String filePath = destFile.getAbsolutePath();
            if (filePath.contains(" ")) {
                return "\"" + filePath + "\"";
            } else {
                return filePath;
            }
        }

        return null;
    }

    private boolean prepareWiXConfig(
            Map<String, ? super Object> params) throws IOException {
        return prepareMainProjectFile(params) && prepareContentList(params);

    }
    private final static String MSI_PROJECT_TEMPLATE = "template.wxs";
    private final static String MSI_PROJECT_TEMPLATE_SERVER_JRE =
            "template.jre.wxs";
    private final static String MSI_PROJECT_CONTENT_FILE = "bundle.wxi";

    private File buildMSI(Map<String, ? super Object> params, File outdir)
            throws IOException {
        File tmpDir = new File(BUILD_ROOT.fetchFrom(params), "tmp");
        File candleOut = new File(
                tmpDir, APP_NAME.fetchFrom(params) +".wixobj");
        File msiOut = new File(
                outdir, INSTALLER_FILE_NAME.fetchFrom(params) + ".msi");

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.preparing-msi-config"), msiOut.getAbsolutePath()));

        msiOut.getParentFile().mkdirs();

        // run candle
        ProcessBuilder pb = new ProcessBuilder(
                TOOL_CANDLE_EXECUTABLE.fetchFrom(params),
                "-nologo",
                getConfig_ProjectFile(params).getAbsolutePath(),
                "-ext", "WixUtilExtension",
                "-out", candleOut.getAbsolutePath());
        pb = pb.directory(WIN_APP_IMAGE.fetchFrom(params));
        IOUtils.exec(pb, false);

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.generating-msi"), msiOut.getAbsolutePath()));

        boolean enableLicenseUI = (getLicenseFile(params) != null);
        boolean enableInstalldirUI = INSTALLDIR_CHOOSER.fetchFrom(params);

        List<String> commandLine = new ArrayList<>();

        commandLine.add(TOOL_LIGHT_EXECUTABLE.fetchFrom(params));
        if (enableLicenseUI) {
            commandLine.add("-dWixUILicenseRtf="+getLicenseFile(params));
        }
        commandLine.add("-nologo");
        commandLine.add("-spdb");
        commandLine.add("-sice:60");
                // ignore warnings due to "missing launcguage info" (ICE60)
        commandLine.add(candleOut.getAbsolutePath());
        commandLine.add("-ext");
        commandLine.add("WixUtilExtension");
        if (enableLicenseUI || enableInstalldirUI) {
            commandLine.add("-ext");
            commandLine.add("WixUIExtension.dll");
        }
        commandLine.add("-out");
        commandLine.add(msiOut.getAbsolutePath());

        // create .msi
        pb = new ProcessBuilder(commandLine);

        pb = pb.directory(WIN_APP_IMAGE.fetchFrom(params));
        IOUtils.exec(pb, false);

        candleOut.delete();
        IOUtils.deleteRecursive(tmpDir);

        return msiOut;
    }

    public static void ensureByMutationFileIsRTF(File f) {
        if (f == null || !f.isFile()) return;

        try {
            boolean existingLicenseIsRTF = false;

            try (FileInputStream fin = new FileInputStream(f)) {
                byte[] firstBits = new byte[7];

                if (fin.read(firstBits) == firstBits.length) {
                    String header = new String(firstBits);
                    existingLicenseIsRTF = "{\\rtf1\\".equals(header);
                }
            }

            if (!existingLicenseIsRTF) {
                List<String> oldLicense = Files.readAllLines(f.toPath());
                try (Writer w = Files.newBufferedWriter(
                        f.toPath(), Charset.forName("Windows-1252"))) {
                    w.write("{\\rtf1\\ansi\\ansicpg1252\\deff0\\deflang1033"
                            + "{\\fonttbl{\\f0\\fnil\\fcharset0 Arial;}}\n"
                            + "\\viewkind4\\uc1\\pard\\sa200\\sl276"
                            + "\\slmult1\\lang9\\fs20 ");
                    oldLicense.forEach(l -> {
                        try {
                            for (char c : l.toCharArray()) {
                                // 0x00 <= ch < 0x20 Escaped (\'hh)
                                // 0x20 <= ch < 0x80 Raw(non - escaped) char
                                // 0x80 <= ch <= 0xFF Escaped(\ 'hh)
                                // 0x5C, 0x7B, 0x7D (special RTF characters
                                // \,{,})Escaped(\'hh)
                                // ch > 0xff Escaped (\\ud###?)
                                if (c < 0x10) {
                                    w.write("\\'0");
                                    w.write(Integer.toHexString(c));
                                } else if (c > 0xff) {
                                    w.write("\\ud");
                                    w.write(Integer.toString(c));
                                    // \\uc1 is in the header and in effect
                                    // so we trail with a replacement char if
                                    // the font lacks that character - '?'
                                    w.write("?");
                                } else if ((c < 0x20) || (c >= 0x80) ||
                                        (c == 0x5C) || (c == 0x7B) ||
                                        (c == 0x7D)) {
                                    w.write("\\'");
                                    w.write(Integer.toHexString(c));
                                } else {
                                    w.write(c);
                                }
                            }
                            // blank lines are interpreted as paragraph breaks
                            if (l.length() < 1) {
                                w.write("\\par");
                            } else {
                                w.write(" ");
                            }
                            w.write("\r\n");
                        } catch (IOException e) {
                            Log.verbose(e);
                        }
                    });
                    w.write("}\r\n");
                }
            }
        } catch (IOException e) {
            Log.verbose(e);
        }

    }
}
