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
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static jdk.jpackage.internal.StandardBundlerParam.*;
import static jdk.jpackage.internal.LinuxAppBundler.LINUX_INSTALL_DIR;
import static jdk.jpackage.internal.LinuxAppBundler.LINUX_PACKAGE_DEPENDENCIES;

public class LinuxRpmBundler extends AbstractBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.LinuxResources");

    public static final BundlerParamInfo<LinuxAppBundler> APP_BUNDLER =
            new StandardBundlerParam<>(
            I18N.getString("param.rpm-app-bundler.name"),
            I18N.getString("param.rpm-app-bundler.description"),
            "linux.app.bundler",
            LinuxAppBundler.class,
            params -> new LinuxAppBundler(),
            null);

    public static final BundlerParamInfo<File> RPM_IMAGE_DIR =
            new StandardBundlerParam<>(
            I18N.getString("param.image-dir.name"),
            I18N.getString("param.image-dir.description"),
            "linux.rpm.imageDir",
            File.class,
            params -> {
                File imagesRoot = IMAGES_ROOT.fetchFrom(params);
                if (!imagesRoot.exists()) imagesRoot.mkdirs();
                return new File(imagesRoot, "linux-rpm.image");
            },
            (s, p) -> new File(s));

    // Fedora rules for package naming are used here
    // https://fedoraproject.org/wiki/Packaging:NamingGuidelines?rd=Packaging/NamingGuidelines
    //
    // all Fedora packages must be named using only the following ASCII
    // characters. These characters are displayed here:
    //
    // abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789-._+
    //
    private static final Pattern RPM_BUNDLE_NAME_PATTERN =
            Pattern.compile("[a-z\\d\\+\\-\\.\\_]+", Pattern.CASE_INSENSITIVE);

    public static final BundlerParamInfo<String> BUNDLE_NAME =
            new StandardBundlerParam<> (
            I18N.getString("param.bundle-name.name"),
            I18N.getString("param.bundle-name.description"),
            Arguments.CLIOptions.LINUX_BUNDLE_NAME.getId(),
            String.class,
            params -> {
                String nm = APP_NAME.fetchFrom(params);
                if (nm == null) return null;

                // make sure to lower case and spaces become dashes
                nm = nm.toLowerCase().replaceAll("[ ]", "-");

                return nm;
            },
            (s, p) -> {
                if (!RPM_BUNDLE_NAME_PATTERN.matcher(s).matches()) {
                    String msgKey = "error.invalid-value-for-package-name";
                    throw new IllegalArgumentException(
                            new ConfigException(MessageFormat.format(
                                    I18N.getString(msgKey), s),
                                    I18N.getString(msgKey + ".advice")));
                }

                return s;
            }
        );

    public static final BundlerParamInfo<String> LICENSE_TYPE =
        new StandardBundlerParam<>(
                I18N.getString("param.license-type.name"),
                I18N.getString("param.license-type.description"),
                Arguments.CLIOptions.LINUX_RPM_LICENSE_TYPE.getId(),
                String.class,
                params -> I18N.getString("param.license-type.default"),
                (s, p) -> s
        );

    public static final BundlerParamInfo<String> XDG_FILE_PREFIX =
            new StandardBundlerParam<> (
            I18N.getString("param.xdg-prefix.name"),
            I18N.getString("param.xdg-prefix.description"),
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

                    return (vendor + "-" + appName).replaceAll("\\s", "");
                } catch (Exception e) {
                    if (Log.isDebug()) {
                        e.printStackTrace();
                    }
                }
                return "unknown-MimeInfo.xml";
            },
            (s, p) -> s);

    private final static String DEFAULT_ICON = "javalogo_white_32.png";
    private final static String DEFAULT_SPEC_TEMPLATE = "template.spec";
    private final static String DEFAULT_DESKTOP_FILE_TEMPLATE =
            "template.desktop";

    public final static String TOOL_RPMBUILD = "rpmbuild";
    public final static double TOOL_RPMBUILD_MIN_VERSION = 4.0d;

    public static boolean testTool(String toolName, double minVersion) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
                PrintStream ps = new PrintStream(baos)) {
            ProcessBuilder pb = new ProcessBuilder(toolName, "--version");
            IOUtils.exec(pb, Log.isDebug(), false, ps);
                    //not interested in the above's output
            String content = new String(baos.toByteArray());
            Pattern pattern = Pattern.compile(" (\\d+\\.\\d+)");
            Matcher matcher = pattern.matcher(content);

            if (matcher.find()) {
                String v = matcher.group(1);
                double version = Double.parseDouble(v);
                return minVersion <= version;
            } else {
               return false;
            }
        } catch (Exception e) {
            Log.verbose(MessageFormat.format(I18N.getString(
                    "message.test-for-tool"), toolName, e.getMessage()));
            return false;
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

            // validate presense of required tools
            if (!testTool(TOOL_RPMBUILD, TOOL_RPMBUILD_MIN_VERSION)){
                throw new ConfigException(
                    MessageFormat.format(
                        I18N.getString("error.cannot-find-rpmbuild"),
                        TOOL_RPMBUILD_MIN_VERSION),
                    MessageFormat.format(
                        I18N.getString("error.cannot-find-rpmbuild.advice"),
                        TOOL_RPMBUILD_MIN_VERSION));
            }

            // only one mime type per association, at least one file extension
            List<Map<String, ? super Object>> associations =
                    FILE_ASSOCIATIONS.fetchFrom(p);
            if (associations != null) {
                for (int i = 0; i < associations.size(); i++) {
                    Map<String, ? super Object> assoc = associations.get(i);
                    List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
                    if (mimes == null || mimes.isEmpty()) {
                        String msgKey =
                                "error.no-content-types-for-file-association";
                        throw new ConfigException(
                                MessageFormat.format(I18N.getString(msgKey), i),
                                I18N.getString(msgKey + ".advice"));
                    } else if (mimes.size() > 1) {
                        String msgKey =
                                "error.no-content-types-for-file-association";
                        throw new ConfigException(
                                MessageFormat.format(I18N.getString(msgKey), i),
                                I18N.getString(msgKey + ".advice"));
                    }
                }
            }

            // bundle name has some restrictions
            // the string converter will throw an exception if invalid
            BUNDLE_NAME.getStringConverter().apply(BUNDLE_NAME.fetchFrom(p), p);

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    private boolean prepareProto(Map<String, ? super Object> p)
            throws IOException {
        File appImage = StandardBundlerParam.getPredefinedAppImage(p);
        File appDir = null;

        // we either have an application image or need to build one
        if (appImage != null) {
            appDir = new File(RPM_IMAGE_DIR.fetchFrom(p),
                APP_NAME.fetchFrom(p));
            // copy everything from appImage dir into appDir/name
            IOUtils.copyRecursive(appImage.toPath(), appDir.toPath());
        } else {
            appDir = APP_BUNDLER.fetchFrom(p).doBundle(p,
                    RPM_IMAGE_DIR.fetchFrom(p), true);
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

        File imageDir = RPM_IMAGE_DIR.fetchFrom(p);
        try {

            imageDir.mkdirs();

            if (prepareProto(p) && prepareProjectConfig(p)) {
                return buildRPM(p, outdir);
            }
            return null;
        } catch (IOException ex) {
            ex.printStackTrace();
            return null;
        }
    }

    private String getLicenseFileString(Map<String, ? super Object> params)
            throws IOException {
        StringBuilder sb = new StringBuilder();

        String licenseStr = LICENSE_FILE.fetchFrom(params);
        if (licenseStr != null) {
            File licenseFile = new File(licenseStr);
            File rootDir =
                    LinuxAppBundler.getRootDir(RPM_IMAGE_DIR.fetchFrom(params),
                            params);
            File target = new File(rootDir + File.separator + "app"
                    + File.separator + licenseFile.getName());
            Files.copy(licenseFile.toPath(), target.toPath());

            sb.append("%license ");
            sb.append(LINUX_INSTALL_DIR.fetchFrom(params));
            sb.append("/");
            sb.append(APP_NAME.fetchFrom(params));
            sb.append("/app/");
            sb.append(licenseFile.getName());
        }

        return sb.toString();
    }

    private boolean prepareProjectConfig(Map<String, ? super Object> params)
            throws IOException {
        Map<String, String> data = createReplacementData(params);
        File rootDir =
            LinuxAppBundler.getRootDir(RPM_IMAGE_DIR.fetchFrom(params), params);

        // prepare installer icon
        File iconTarget = getConfig_IconFile(rootDir, params);
        File icon = LinuxAppBundler.ICON_PNG.fetchFrom(params);
        if (!Arguments.CREATE_JRE_INSTALLER.fetchFrom(params)) {
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
        for (Map<String, ? super Object> secondaryLauncher :
                SECONDARY_LAUNCHERS.fetchFrom(params)) {
            Map<String, String> secondaryLauncherData =
                    createReplacementData(secondaryLauncher);
            secondaryLauncherData.put("APPLICATION_FS_NAME",
                    data.get("APPLICATION_FS_NAME"));
            secondaryLauncherData.put("DESKTOP_MIMES", "");

            // prepare desktop shortcut
            Writer w = new BufferedWriter(new FileWriter(
                    getConfig_DesktopShortcutFile(rootDir, secondaryLauncher)));
            String content = preprocessTextResource(
                    getConfig_DesktopShortcutFile(rootDir,
                    secondaryLauncher).getName(),
                    I18N.getString("resource.menu-shortcut-descriptor"),
                    DEFAULT_DESKTOP_FILE_TEMPLATE, secondaryLauncherData,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
            w.close();

            // prepare installer icon
            iconTarget = getConfig_IconFile(rootDir, secondaryLauncher);
            icon = LinuxAppBundler.ICON_PNG.fetchFrom(secondaryLauncher);
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

            // post copying of desktop icon
            installScripts.append("xdg-desktop-menu install --novendor ");
            installScripts.append(LINUX_INSTALL_DIR.fetchFrom(params));
            installScripts.append("/");
            installScripts.append(data.get("APPLICATION_FS_NAME"));
            installScripts.append("/");
            installScripts.append(secondaryLauncherData.get(
                    "APPLICATION_LAUNCHER_FILENAME"));
            installScripts.append(".desktop\n");

            // preun cleanup of desktop icon
            removeScripts.append("xdg-desktop-menu uninstall --novendor ");
            removeScripts.append(LINUX_INSTALL_DIR.fetchFrom(params));
            removeScripts.append("/");
            removeScripts.append(data.get("APPLICATION_FS_NAME"));
            removeScripts.append("/");
            removeScripts.append(secondaryLauncherData.get(
                    "APPLICATION_LAUNCHER_FILENAME"));
            removeScripts.append(".desktop\n");

        }
        data.put("SECONDARY_LAUNCHERS_INSTALL", installScripts.toString());
        data.put("SECONDARY_LAUNCHERS_REMOVE", removeScripts.toString());

        StringBuilder cdsScript = new StringBuilder();

        data.put("APP_CDS_CACHE", cdsScript.toString());

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
                +"'http://www.freedesktop.org/standards/shared-mime-info'>\n");
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
                File faIcon = FA_ICON.fetchFrom(assoc); //TODO FA_ICON_PNG
                List<String> extensions = FA_EXTENSIONS.fetchFrom(assoc);
                if (extensions == null) {
                    Log.verbose(I18N.getString(
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
                    registrations.append("xdg-mime install ")
                            .append(LINUX_INSTALL_DIR.fetchFrom(params))
                            .append("/")
                            .append(data.get("APPLICATION_FS_NAME"))
                            .append("/")
                            .append(mimeInfoFile)
                            .append("\n");

                    deregistrations.append("xdg-mime uninstall ")
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
                                "xdg-icon-resource install "
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

                        // xdg-icon-resource uninstall --context mimetypes
                        // --size 64 awesomeapp_fa_1.png
                        // application-x.vnd-awesome
                        deregistrations.append(
                                "xdg-icon-resource uninstall "
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
                Writer w = new BufferedWriter(new FileWriter(
                        new File(rootDir, mimeInfoFile)));
                w.write(mimeInfo.toString());
                w.close();
                data.put("FILE_ASSOCIATION_INSTALL", registrations.toString());
                data.put("FILE_ASSOCIATION_REMOVE", deregistrations.toString());
                data.put("DESKTOP_MIMES", desktopMimes.toString());
            }
        }

        if (!Arguments.CREATE_JRE_INSTALLER.fetchFrom(params)) {
            //prepare desktop shortcut
            Writer w = new BufferedWriter(new FileWriter(
                    getConfig_DesktopShortcutFile(rootDir, params)));
            String content = preprocessTextResource(
                    getConfig_DesktopShortcutFile(rootDir, params).getName(),
                    I18N.getString("resource.menu-shortcut-descriptor"),
                    DEFAULT_DESKTOP_FILE_TEMPLATE, data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
            w.close();
        }

        // prepare spec file
        Writer w = new BufferedWriter(
                new FileWriter(getConfig_SpecFile(params)));
        String content = preprocessTextResource(
                getConfig_SpecFile(params).getName(),
                I18N.getString("resource.rpm-spec-file"),
                DEFAULT_SPEC_TEMPLATE, data,
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));
        w.write(content);
        w.close();

        return true;
    }

    private Map<String, String> createReplacementData(
            Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_NAME", APP_NAME.fetchFrom(params));
        data.put("APPLICATION_FS_NAME", APP_NAME.fetchFrom(params));
        data.put("APPLICATION_PACKAGE", BUNDLE_NAME.fetchFrom(params));
        data.put("APPLICATION_VENDOR", VENDOR.fetchFrom(params));
        data.put("APPLICATION_VERSION", VERSION.fetchFrom(params));
        data.put("APPLICATION_LAUNCHER_FILENAME", APP_NAME.fetchFrom(params));
        data.put("INSTALLATION_DIRECTORY", LINUX_INSTALL_DIR.fetchFrom(params));
        data.put("XDG_PREFIX", XDG_FILE_PREFIX.fetchFrom(params));
        data.put("DEPLOY_BUNDLE_CATEGORY", CATEGORY.fetchFrom(params));
        // TODO rpm categories
        data.put("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(params));
        data.put("APPLICATION_SUMMARY", TITLE.fetchFrom(params));
        data.put("APPLICATION_LICENSE_TYPE", LICENSE_TYPE.fetchFrom(params));
        data.put("APPLICATION_LICENSE_FILE", getLicenseFileString(params));
        String deps = LINUX_PACKAGE_DEPENDENCIES.fetchFrom(params);
        data.put("PACKAGE_DEPENDENCIES",
                deps.isEmpty() ? "" : "Requires: " + deps);
        data.put("CREATE_JRE_INSTALLER",
                Arguments.CREATE_JRE_INSTALLER.fetchFrom(params).toString());
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

    private File getConfig_SpecFile(Map<String, ? super Object> params) {
        return new File(RPM_IMAGE_DIR.fetchFrom(params),
                APP_NAME.fetchFrom(params) + ".spec");
    }

    private File buildRPM(Map<String, ? super Object> params,
            File outdir) throws IOException {
        Log.verbose(MessageFormat.format(I18N.getString(
                "message.outputting-bundle-location"),
                outdir.getAbsolutePath()));

        File broot = new File(BUILD_ROOT.fetchFrom(params), "rmpbuildroot");

        outdir.mkdirs();

        //run rpmbuild
        ProcessBuilder pb = new ProcessBuilder(
                TOOL_RPMBUILD,
                "-bb", getConfig_SpecFile(params).getAbsolutePath(),
                "--define", "%_sourcedir "
                        + RPM_IMAGE_DIR.fetchFrom(params).getAbsolutePath(),
                // save result to output dir
                "--define", "%_rpmdir " + outdir.getAbsolutePath(),
                // do not use other system directories to build as current user
                "--define", "%_topdir " + broot.getAbsolutePath()
        );
        pb = pb.directory(RPM_IMAGE_DIR.fetchFrom(params));
        IOUtils.exec(pb, false);

        Log.verbose(MessageFormat.format(
                I18N.getString("message.output-bundle-location"),
                outdir.getAbsolutePath()));

        // presume the result is the ".rpm" file with the newest modified time
        // not the best solution, but it is the most reliable
        File result = null;
        long lastModified = 0;
        File[] list = outdir.listFiles();
        if (list != null) {
            for (File f : list) {
                if (f.getName().endsWith(".rpm") &&
                        f.lastModified() > lastModified) {
                    result = f;
                    lastModified = f.lastModified();
                }
            }
        }

        return result;
    }

    @Override
    public String getName() {
        return I18N.getString("rpm.bundler.name");
    }

    @Override
    public String getDescription() {
        return I18N.getString("rpm.bundler.description");
    }

    @Override
    public String getID() {
        return "rpm";
    }

    @Override
    public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        Collection<BundlerParamInfo<?>> results = new LinkedHashSet<>();
        results.addAll(LinuxAppBundler.getAppBundleParameters());
        results.addAll(getRpmBundleParameters());
        return results;
    }

    public static Collection<BundlerParamInfo<?>> getRpmBundleParameters() {
        return Arrays.asList(
                BUNDLE_NAME,
                CATEGORY,
                DESCRIPTION,
                LinuxAppBundler.ICON_PNG,
                LICENSE_FILE,
                LICENSE_TYPE,
                TITLE,
                VENDOR
        );
    }

    @Override
    public File execute(
            Map<String, ? super Object> params, File outputParentDir) {
        return bundle(params, outputParentDir);
    }

    @Override
    public boolean supported() {
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
            e.printStackTrace();
            return 0;
        }
    }
}
