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

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.text.MessageFormat;
import java.util.*;
import java.util.regex.Pattern;

import static jdk.jpackage.internal.StandardBundlerParam.*;
import static jdk.jpackage.internal.LinuxAppBundler.ICON_PNG;
import static jdk.jpackage.internal.LinuxAppBundler.LINUX_INSTALL_DIR;
import static jdk.jpackage.internal.LinuxAppBundler.LINUX_PACKAGE_DEPENDENCIES;

public class LinuxDebBundler extends AbstractBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
                    "jdk.jpackage.internal.resources.LinuxResources");

    public static final BundlerParamInfo<LinuxAppBundler> APP_BUNDLER =
            new StandardBundlerParam<>(
            "linux.app.bundler",
            LinuxAppBundler.class,
            params -> new LinuxAppBundler(),
            (s, p) -> null);

    // Debian rules for package naming are used here
    // https://www.debian.org/doc/debian-policy/ch-controlfields.html#s-f-Source
    //
    // Package names must consist only of lower case letters (a-z),
    // digits (0-9), plus (+) and minus (-) signs, and periods (.).
    // They must be at least two characters long and
    // must start with an alphanumeric character.
    //
    private static final Pattern DEB_BUNDLE_NAME_PATTERN =
            Pattern.compile("^[a-z][a-z\\d\\+\\-\\.]+");

    public static final BundlerParamInfo<String> BUNDLE_NAME =
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
                if (!DEB_BUNDLE_NAME_PATTERN.matcher(s).matches()) {
                    throw new IllegalArgumentException(new ConfigException(
                            MessageFormat.format(I18N.getString(
                            "error.invalid-value-for-package-name"), s),
                            I18N.getString(
                            "error.invalid-value-for-package-name.advice")));
                }

                return s;
            });

    public static final BundlerParamInfo<String> FULL_PACKAGE_NAME =
            new StandardBundlerParam<> (
            "linux.deb.fullPackageName",
            String.class,
            params -> BUNDLE_NAME.fetchFrom(params) + "-"
                    + VERSION.fetchFrom(params),
            (s, p) -> s);

    public static final BundlerParamInfo<File> DEB_IMAGE_DIR =
            new StandardBundlerParam<>(
            "linux.deb.imageDir",
            File.class,
            params -> {
                File imagesRoot = IMAGES_ROOT.fetchFrom(params);
                if (!imagesRoot.exists()) imagesRoot.mkdirs();
                return new File(new File(imagesRoot, "linux-deb.image"),
                        FULL_PACKAGE_NAME.fetchFrom(params));
            },
            (s, p) -> new File(s));

    public static final BundlerParamInfo<File> APP_IMAGE_ROOT =
            new StandardBundlerParam<>(
            "linux.deb.imageRoot",
            File.class,
            params -> {
                File imageDir = DEB_IMAGE_DIR.fetchFrom(params);
                return new File(imageDir, LINUX_INSTALL_DIR.fetchFrom(params));
            },
            (s, p) -> new File(s));

    public static final BundlerParamInfo<File> CONFIG_DIR =
            new StandardBundlerParam<>(
            "linux.deb.configDir",
            File.class,
            params ->  new File(DEB_IMAGE_DIR.fetchFrom(params), "DEBIAN"),
            (s, p) -> new File(s));

    public static final BundlerParamInfo<String> EMAIL =
            new StandardBundlerParam<> (
            Arguments.CLIOptions.LINUX_DEB_MAINTAINER.getId(),
            String.class,
            params -> "Unknown",
            (s, p) -> s);

    public static final BundlerParamInfo<String> MAINTAINER =
            new StandardBundlerParam<> (
            BundleParams.PARAM_MAINTAINER,
            String.class,
            params -> VENDOR.fetchFrom(params) + " <"
                    + EMAIL.fetchFrom(params) + ">",
            (s, p) -> s);

    public static final BundlerParamInfo<String> LICENSE_TEXT =
            new StandardBundlerParam<> (
            "linux.deb.licenseText",
            String.class,
            params -> {
                try {
                    String licenseFile = LICENSE_FILE.fetchFrom(params);
                    if (licenseFile != null) {
                        return Files.readString(new File(licenseFile).toPath());
                    }
                } catch (Exception e) {
                    Log.verbose(e);
                }
                return "Unknown";
            },
            (s, p) -> s);

    public static final BundlerParamInfo<String> XDG_FILE_PREFIX =
            new StandardBundlerParam<> (
            "linux.xdg-prefix",
            String.class,
            params -> {
                try {
                    String vendor;
                    if (params.containsKey(VENDOR.getID())) {
                        vendor = VENDOR.fetchFrom(params);
                    } else {
                        vendor = "jpackage";
                    }
                    String appName = APP_NAME.fetchFrom(params);

                    return (appName + "-" + vendor).replaceAll("\\s", "");
                } catch (Exception e) {
                    Log.verbose(e);
                }
                return "unknown-MimeInfo.xml";
            },
            (s, p) -> s);

    public static final BundlerParamInfo<String> MENU_GROUP =
        new StandardBundlerParam<>(
                Arguments.CLIOptions.LINUX_MENU_GROUP.getId(),
                String.class,
                params -> I18N.getString("param.menu-group.default"),
                (s, p) -> s
        );

    private final static String DEFAULT_ICON = "javalogo_white_32.png";
    private final static String DEFAULT_CONTROL_TEMPLATE = "template.control";
    private final static String DEFAULT_PRERM_TEMPLATE = "template.prerm";
    private final static String DEFAULT_PREINSTALL_TEMPLATE =
            "template.preinst";
    private final static String DEFAULT_POSTRM_TEMPLATE = "template.postrm";
    private final static String DEFAULT_POSTINSTALL_TEMPLATE =
            "template.postinst";
    private final static String DEFAULT_COPYRIGHT_TEMPLATE =
            "template.copyright";
    private final static String DEFAULT_DESKTOP_FILE_TEMPLATE =
            "template.desktop";

    public final static String TOOL_DPKG = "dpkg-deb";

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

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws UnsupportedPlatformException, ConfigException {
        try {
            if (params == null) throw new ConfigException(
                    I18N.getString("error.parameters-null"),
                    I18N.getString("error.parameters-null.advice"));

            //run basic validation to ensure requirements are met
            //we are not interested in return code, only possible exception
            APP_BUNDLER.fetchFrom(params).validate(params);

            // NOTE: Can we validate that the required tools are available
            // before we start?
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

            // only one mime type per association, at least one file extention
            List<Map<String, ? super Object>> associations =
                    FILE_ASSOCIATIONS.fetchFrom(params);
            if (associations != null) {
                for (int i = 0; i < associations.size(); i++) {
                    Map<String, ? super Object> assoc = associations.get(i);
                    List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
                    if (mimes == null || mimes.isEmpty()) {
                        String msgKey =
                            "error.no-content-types-for-file-association";
                        throw new ConfigException(
                                MessageFormat.format(I18N.getString(msgKey), i),
                                I18N.getString(msgKey + ".advise"));

                    } else if (mimes.size() > 1) {
                        String msgKey =
                            "error.too-many-content-types-for-file-association";
                        throw new ConfigException(
                                MessageFormat.format(I18N.getString(msgKey), i),
                                I18N.getString(msgKey + ".advise"));
                    }
                }
            }

            // bundle name has some restrictions
            // the string converter will throw an exception if invalid
            BUNDLE_NAME.getStringConverter().apply(
                    BUNDLE_NAME.fetchFrom(params), params);

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    private boolean prepareProto(Map<String, ? super Object> params)
            throws PackagerException, IOException {
        File appImage = StandardBundlerParam.getPredefinedAppImage(params);
        File appDir = null;

        // we either have an application image or need to build one
        if (appImage != null) {
            appDir = new File(APP_IMAGE_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params));
            // copy everything from appImage dir into appDir/name
            IOUtils.copyRecursive(appImage.toPath(), appDir.toPath());
        } else {
            appDir = APP_BUNDLER.fetchFrom(params).doBundle(params,
                    APP_IMAGE_ROOT.fetchFrom(params), true);
        }
        return appDir != null;
    }

    public File bundle(Map<String, ? super Object> params,
            File outdir) throws PackagerException {
        if (!outdir.isDirectory() && !outdir.mkdirs()) {
            throw new PackagerException ("error.cannot-create-output-dir",
                    outdir.getAbsolutePath());
        }
        if (!outdir.canWrite()) {
            throw new PackagerException("error.cannot-write-to-output-dir",
                    outdir.getAbsolutePath());
        }

        // we want to create following structure
        //   <package-name>
        //        DEBIAN
        //          control   (file with main package details)
        //          menu      (request to create menu)
        //          ... other control files if needed ....
        //        opt  (by default)
        //          AppFolder (this is where app image goes)
        //             launcher executable
        //             app
        //             runtime

        File imageDir = DEB_IMAGE_DIR.fetchFrom(params);
        File configDir = CONFIG_DIR.fetchFrom(params);

        try {

            imageDir.mkdirs();
            configDir.mkdirs();
            if (prepareProto(params) && prepareProjectConfig(params)) {
                return buildDeb(params, outdir);
            }
            return null;
        } catch (IOException ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
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

    private String getArch() {
        String arch = System.getProperty("os.arch");
        if ("i386".equals(arch))
            return "i386";
        else
            return "amd64";
    }

    private long getInstalledSizeKB(Map<String, ? super Object> params) {
        return getInstalledSizeKB(APP_IMAGE_ROOT.fetchFrom(params)) >> 10;
    }

    private long getInstalledSizeKB(File dir) {
        long count = 0;
        File[] children = dir.listFiles();
        if (children != null) {
            for (File file : children) {
                if (file.isFile()) {
                    count += file.length();
                }
                else if (file.isDirectory()) {
                    count += getInstalledSizeKB(file);
                }
            }
        }
        return count;
    }

    private boolean prepareProjectConfig(Map<String, ? super Object> params)
            throws IOException {
        Map<String, String> data = createReplacementData(params);
        File rootDir = LinuxAppBundler.getRootDir(APP_IMAGE_ROOT.fetchFrom(
                params), params);

        File iconTarget = getConfig_IconFile(rootDir, params);
        File icon = ICON_PNG.fetchFrom(params);
        if (!StandardBundlerParam.isRuntimeInstaller(params)) {
            // prepare installer icon
            if (icon == null || !icon.exists()) {
                fetchResource(iconTarget.getName(),
                        I18N.getString("resource.menu-icon"),
                        DEFAULT_ICON,
                        iconTarget,
                        VERBOSE.fetchFrom(params),
                        RESOURCE_DIR.fetchFrom(params));
            } else {
                fetchResource(iconTarget.getName(),
                        I18N.getString("resource.menu-icon"),
                        icon,
                        iconTarget,
                        VERBOSE.fetchFrom(params),
                        RESOURCE_DIR.fetchFrom(params));
            }
        }

        StringBuilder installScripts = new StringBuilder();
        StringBuilder removeScripts = new StringBuilder();
        for (Map<String, ? super Object> addLauncher :
                ADD_LAUNCHERS.fetchFrom(params)) {
            Map<String, String> addLauncherData =
                    createReplacementData(addLauncher);
            addLauncherData.put("APPLICATION_FS_NAME",
                    data.get("APPLICATION_FS_NAME"));
            addLauncherData.put("DESKTOP_MIMES", "");

            if (!StandardBundlerParam.isRuntimeInstaller(params)) {
                // prepare desktop shortcut
                try (Writer w = Files.newBufferedWriter(
                        getConfig_DesktopShortcutFile(
                                rootDir, addLauncher).toPath())) {
                    String content = preprocessTextResource(
                            getConfig_DesktopShortcutFile(rootDir,
                            addLauncher).getName(),
                            I18N.getString("resource.menu-shortcut-descriptor"),
                            DEFAULT_DESKTOP_FILE_TEMPLATE,
                            addLauncherData,
                            VERBOSE.fetchFrom(params),
                            RESOURCE_DIR.fetchFrom(params));
                    w.write(content);
                }
            }

            // prepare installer icon
            iconTarget = getConfig_IconFile(rootDir, addLauncher);
            icon = ICON_PNG.fetchFrom(addLauncher);
            if (icon == null || !icon.exists()) {
                fetchResource(iconTarget.getName(),
                        I18N.getString("resource.menu-icon"),
                        DEFAULT_ICON,
                        iconTarget,
                        VERBOSE.fetchFrom(params),
                        RESOURCE_DIR.fetchFrom(params));
            } else {
                fetchResource(iconTarget.getName(),
                        I18N.getString("resource.menu-icon"),
                        icon,
                        iconTarget,
                        VERBOSE.fetchFrom(params),
                        RESOURCE_DIR.fetchFrom(params));
            }

            // postinst copying of desktop icon
            installScripts.append(
                    "        xdg-desktop-menu install --novendor ");
            installScripts.append(LINUX_INSTALL_DIR.fetchFrom(params));
            installScripts.append("/");
            installScripts.append(data.get("APPLICATION_FS_NAME"));
            installScripts.append("/");
            installScripts.append(
                    addLauncherData.get("APPLICATION_LAUNCHER_FILENAME"));
            installScripts.append(".desktop\n");

            // postrm cleanup of desktop icon
            removeScripts.append(
                    "        xdg-desktop-menu uninstall --novendor ");
            removeScripts.append(LINUX_INSTALL_DIR.fetchFrom(params));
            removeScripts.append("/");
            removeScripts.append(data.get("APPLICATION_FS_NAME"));
            removeScripts.append("/");
            removeScripts.append(
                    addLauncherData.get("APPLICATION_LAUNCHER_FILENAME"));
            removeScripts.append(".desktop\n");
        }
        data.put("ADD_LAUNCHERS_INSTALL", installScripts.toString());
        data.put("ADD_LAUNCHERS_REMOVE", removeScripts.toString());

        List<Map<String, ? super Object>> associations =
                FILE_ASSOCIATIONS.fetchFrom(params);
        data.put("FILE_ASSOCIATION_INSTALL", "");
        data.put("FILE_ASSOCIATION_REMOVE", "");
        data.put("DESKTOP_MIMES", "");
        if (associations != null) {
            String mimeInfoFile = XDG_FILE_PREFIX.fetchFrom(params)
                    + "-MimeInfo.xml";
            StringBuilder mimeInfo = new StringBuilder(
                "<?xml version=\"1.0\"?>\n<mime-info xmlns="
                + "'http://www.freedesktop.org/standards/shared-mime-info'>\n");
            StringBuilder registrations = new StringBuilder();
            StringBuilder deregistrations = new StringBuilder();
            StringBuilder desktopMimes = new StringBuilder("MimeType=");
            boolean addedEntry = false;

            for (Map<String, ? super Object> assoc : associations) {
                //  <mime-type type="application/x-vnd.awesome">
                //    <comment>Awesome document</comment>
                //    <glob pattern="*.awesome"/>
                //    <glob pattern="*.awe"/>
                //  </mime-type>

                if (assoc == null) {
                    continue;
                }

                String description = FA_DESCRIPTION.fetchFrom(assoc);
                File faIcon = FA_ICON.fetchFrom(assoc);
                List<String> extensions = FA_EXTENSIONS.fetchFrom(assoc);
                if (extensions == null) {
                    Log.error(I18N.getString(
                          "message.creating-association-with-null-extension"));
                }

                List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
                if (mimes == null || mimes.isEmpty()) {
                    continue;
                }
                String thisMime = mimes.get(0);
                String dashMime = thisMime.replace('/', '-');

                mimeInfo.append("  <mime-type type='")
                        .append(thisMime)
                        .append("'>\n");
                if (description != null && !description.isEmpty()) {
                    mimeInfo.append("    <comment>")
                            .append(description)
                            .append("</comment>\n");
                }

                if (extensions != null) {
                    for (String ext : extensions) {
                        mimeInfo.append("    <glob pattern='*.")
                                .append(ext)
                                .append("'/>\n");
                    }
                }

                mimeInfo.append("  </mime-type>\n");
                if (!addedEntry) {
                    registrations.append("        xdg-mime install ")
                            .append(LINUX_INSTALL_DIR.fetchFrom(params))
                            .append("/")
                            .append(data.get("APPLICATION_FS_NAME"))
                            .append("/")
                            .append(mimeInfoFile)
                            .append("\n");

                    deregistrations.append("        xdg-mime uninstall ")
                            .append(LINUX_INSTALL_DIR.fetchFrom(params))
                            .append("/")
                            .append(data.get("APPLICATION_FS_NAME"))
                            .append("/")
                            .append(mimeInfoFile)
                            .append("\n");
                    addedEntry = true;
                } else {
                    desktopMimes.append(";");
                }
                desktopMimes.append(thisMime);

                if (faIcon != null && faIcon.exists()) {
                    int size = getSquareSizeOfImage(faIcon);

                    if (size > 0) {
                        File target = new File(rootDir,
                                APP_NAME.fetchFrom(params)
                                + "_fa_" + faIcon.getName());
                        IOUtils.copyFile(faIcon, target);

                        // xdg-icon-resource install --context mimetypes
                        // --size 64 awesomeapp_fa_1.png
                        // application-x.vnd-awesome
                        registrations.append(
                                "        xdg-icon-resource install "
                                        + "--context mimetypes --size ")
                                .append(size)
                                .append(" ")
                                .append(LINUX_INSTALL_DIR.fetchFrom(params))
                                .append("/")
                                .append(data.get("APPLICATION_FS_NAME"))
                                .append("/")
                                .append(target.getName())
                                .append(" ")
                                .append(dashMime)
                                .append("\n");

                        // x dg-icon-resource uninstall --context mimetypes
                        // --size 64 awesomeapp_fa_1.png
                        // application-x.vnd-awesome
                        deregistrations.append(
                                "        xdg-icon-resource uninstall "
                                        + "--context mimetypes --size ")
                                .append(size)
                                .append(" ")
                                .append(LINUX_INSTALL_DIR.fetchFrom(params))
                                .append("/")
                                .append(data.get("APPLICATION_FS_NAME"))
                                .append("/")
                                .append(target.getName())
                                .append(" ")
                                .append(dashMime)
                                .append("\n");
                    }
                }
            }
            mimeInfo.append("</mime-info>");

            if (addedEntry) {
                try (Writer w = Files.newBufferedWriter(
                        new File(rootDir, mimeInfoFile).toPath())) {
                    w.write(mimeInfo.toString());
                }
                data.put("FILE_ASSOCIATION_INSTALL", registrations.toString());
                data.put("FILE_ASSOCIATION_REMOVE", deregistrations.toString());
                data.put("DESKTOP_MIMES", desktopMimes.toString());
            }
        }

        if (!StandardBundlerParam.isRuntimeInstaller(params)) {
            //prepare desktop shortcut
            try (Writer w = Files.newBufferedWriter(
                    getConfig_DesktopShortcutFile(rootDir, params).toPath())) {
                String content = preprocessTextResource(
                        getConfig_DesktopShortcutFile(
                        rootDir, params).getName(),
                        I18N.getString("resource.menu-shortcut-descriptor"),
                        DEFAULT_DESKTOP_FILE_TEMPLATE,
                        data,
                        VERBOSE.fetchFrom(params),
                        RESOURCE_DIR.fetchFrom(params));
                w.write(content);
            }
        }
        // prepare control file
        try (Writer w = Files.newBufferedWriter(
                getConfig_ControlFile(params).toPath())) {
            String content = preprocessTextResource(
                    getConfig_ControlFile(params).getName(),
                    I18N.getString("resource.deb-control-file"),
                    DEFAULT_CONTROL_TEMPLATE,
                    data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
        }

        try (Writer w = Files.newBufferedWriter(
                getConfig_PreinstallFile(params).toPath())) {
            String content = preprocessTextResource(
                    getConfig_PreinstallFile(params).getName(),
                    I18N.getString("resource.deb-preinstall-script"),
                    DEFAULT_PREINSTALL_TEMPLATE,
                    data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
        }
        setPermissions(getConfig_PreinstallFile(params), "rwxr-xr-x");

        try (Writer w = Files.newBufferedWriter(
                    getConfig_PrermFile(params).toPath())) {
            String content = preprocessTextResource(
                    getConfig_PrermFile(params).getName(),
                    I18N.getString("resource.deb-prerm-script"),
                    DEFAULT_PRERM_TEMPLATE,
                    data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
        }
        setPermissions(getConfig_PrermFile(params), "rwxr-xr-x");

        try (Writer w = Files.newBufferedWriter(
                getConfig_PostinstallFile(params).toPath())) {
            String content = preprocessTextResource(
                    getConfig_PostinstallFile(params).getName(),
                    I18N.getString("resource.deb-postinstall-script"),
                    DEFAULT_POSTINSTALL_TEMPLATE,
                    data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
        }
        setPermissions(getConfig_PostinstallFile(params), "rwxr-xr-x");

        try (Writer w = Files.newBufferedWriter(
                getConfig_PostrmFile(params).toPath())) {
            String content = preprocessTextResource(
                    getConfig_PostrmFile(params).getName(),
                    I18N.getString("resource.deb-postrm-script"),
                    DEFAULT_POSTRM_TEMPLATE,
                    data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
        }
        setPermissions(getConfig_PostrmFile(params), "rwxr-xr-x");

        try (Writer w = Files.newBufferedWriter(
                getConfig_CopyrightFile(params).toPath())) {
            String content = preprocessTextResource(
                    getConfig_CopyrightFile(params).getName(),
                    I18N.getString("resource.deb-copyright-file"),
                    DEFAULT_COPYRIGHT_TEMPLATE,
                    data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
        }

        return true;
    }

    private Map<String, String> createReplacementData(
            Map<String, ? super Object> params) {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_NAME", APP_NAME.fetchFrom(params));
        data.put("APPLICATION_FS_NAME", APP_NAME.fetchFrom(params));
        data.put("APPLICATION_PACKAGE", BUNDLE_NAME.fetchFrom(params));
        data.put("APPLICATION_VENDOR", VENDOR.fetchFrom(params));
        data.put("APPLICATION_MAINTAINER", MAINTAINER.fetchFrom(params));
        data.put("APPLICATION_VERSION", VERSION.fetchFrom(params));
        data.put("APPLICATION_LAUNCHER_FILENAME", APP_NAME.fetchFrom(params));
        data.put("INSTALLATION_DIRECTORY", LINUX_INSTALL_DIR.fetchFrom(params));
        data.put("XDG_PREFIX", XDG_FILE_PREFIX.fetchFrom(params));
        data.put("DEPLOY_BUNDLE_CATEGORY", MENU_GROUP.fetchFrom(params));
        data.put("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(params));
        data.put("APPLICATION_COPYRIGHT", COPYRIGHT.fetchFrom(params));
        data.put("APPLICATION_LICENSE_TEXT", LICENSE_TEXT.fetchFrom(params));
        data.put("APPLICATION_ARCH", getArch());
        data.put("APPLICATION_INSTALLED_SIZE",
                Long.toString(getInstalledSizeKB(params)));
        String deps = LINUX_PACKAGE_DEPENDENCIES.fetchFrom(params);
        data.put("PACKAGE_DEPENDENCIES",
                deps.isEmpty() ? "" : "Depends: " + deps);
        data.put("RUNTIME_INSTALLER", "" +
                StandardBundlerParam.isRuntimeInstaller(params));

        return data;
    }

    private File getConfig_DesktopShortcutFile(File rootDir,
            Map<String, ? super Object> params) {
        return new File(rootDir, APP_NAME.fetchFrom(params) + ".desktop");
    }

    private File getConfig_IconFile(File rootDir,
            Map<String, ? super Object> params) {
        return new File(rootDir, APP_NAME.fetchFrom(params) + ".png");
    }

    private File getConfig_InitScriptFile(Map<String, ? super Object> params) {
        return new File(LinuxAppBundler.getRootDir(
                APP_IMAGE_ROOT.fetchFrom(params), params),
                        BUNDLE_NAME.fetchFrom(params) + ".init");
    }

    private File getConfig_ControlFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "control");
    }

    private File getConfig_PreinstallFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "preinst");
    }

    private File getConfig_PrermFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "prerm");
    }

    private File getConfig_PostinstallFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "postinst");
    }

    private File getConfig_PostrmFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "postrm");
    }

    private File getConfig_CopyrightFile(Map<String, ? super Object> params) {
        return new File(CONFIG_DIR.fetchFrom(params), "copyright");
    }

    private File buildDeb(Map<String, ? super Object> params,
            File outdir) throws IOException {
        File outFile = new File(outdir,
                FULL_PACKAGE_NAME.fetchFrom(params)+".deb");
        Log.verbose(MessageFormat.format(I18N.getString(
                "message.outputting-to-location"), outFile.getAbsolutePath()));

        outFile.getParentFile().mkdirs();

        // run dpkg
        ProcessBuilder pb = new ProcessBuilder(
                "fakeroot", TOOL_DPKG, "-b",
                FULL_PACKAGE_NAME.fetchFrom(params),
                outFile.getAbsolutePath());
        pb = pb.directory(DEB_IMAGE_DIR.fetchFrom(params).getParentFile());
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
    public String getDescription() {
        return I18N.getString("deb.bundler.description");
    }

    @Override
    public String getID() {
        return "deb";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(LinuxAppBundler.getAppBundleParameters());
        results.addAll(getDebBundleParameters());
        return results;
    }

    public static Collection<BundlerParamInfo<?>> getDebBundleParameters() {
        return Arrays.asList(
                BUNDLE_NAME,
                COPYRIGHT,
                MENU_GROUP,
                DESCRIPTION,
                EMAIL,
                ICON_PNG,
                LICENSE_FILE,
                VENDOR
        );
    }

    @Override
    public File execute(Map<String, ? super Object> params,
            File outputParentDir) throws PackagerException {
        return bundle(params, outputParentDir);
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return (Platform.getPlatform() == Platform.LINUX);
    }

    public int getSquareSizeOfImage(File f) {
        try {
            BufferedImage bi = ImageIO.read(f);
            if (bi.getWidth() == bi.getHeight()) {
                return bi.getWidth();
            } else {
                return 0;
            }
        } catch (Exception e) {
            Log.verbose(e);
            return 0;
        }
    }
}
