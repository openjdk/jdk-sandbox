/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.Writer;
import java.net.URLEncoder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import static jdk.jpackage.internal.StandardBundlerParam.*;
import static jdk.jpackage.internal.MacBaseInstallerBundler.SIGNING_KEYCHAIN;
import static jdk.jpackage.internal.MacBaseInstallerBundler.SIGNING_KEY_USER;

public class MacPkgBundler extends MacBaseInstallerBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MacResources");

    private static final String DEFAULT_BACKGROUND_IMAGE = "background_pkg.png";

    private static final String TEMPLATE_PREINSTALL_SCRIPT =
            "preinstall.template";
    private static final String TEMPLATE_POSTINSTALL_SCRIPT =
            "postinstall.template";

    private static final BundlerParamInfo<File> PACKAGES_ROOT =
            new StandardBundlerParam<>(
            "mac.pkg.packagesRoot",
            File.class,
            params -> {
                File packagesRoot =
                        new File(TEMP_ROOT.fetchFrom(params), "packages");
                packagesRoot.mkdirs();
                return packagesRoot;
            },
            (s, p) -> new File(s));


    protected final BundlerParamInfo<File> SCRIPTS_DIR =
            new StandardBundlerParam<>(
            "mac.pkg.scriptsDir",
            File.class,
            params -> {
                File scriptsDir =
                        new File(CONFIG_ROOT.fetchFrom(params), "scripts");
                scriptsDir.mkdirs();
                return scriptsDir;
            },
            (s, p) -> new File(s));

    public static final
            BundlerParamInfo<String> DEVELOPER_ID_INSTALLER_SIGNING_KEY =
            new StandardBundlerParam<>(
            "mac.signing-key-developer-id-installer",
            String.class,
            params -> {
                    String result = MacBaseInstallerBundler.findKey(
                            "Developer ID Installer: "
                            + SIGNING_KEY_USER.fetchFrom(params),
                            SIGNING_KEYCHAIN.fetchFrom(params),
                            VERBOSE.fetchFrom(params));
                    if (result != null) {
                        MacCertificate certificate = new MacCertificate(
                                result, VERBOSE.fetchFrom(params));

                        if (!certificate.isValid()) {
                            Log.error(MessageFormat.format(
                                    I18N.getString("error.certificate.expired"),
                                    result));
                        }
                    }

                    return result;
                },
            (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_INSTALL_DIR =
            new StandardBundlerParam<>(
            "mac-install-dir",
            String.class,
             params -> {
                 String dir = INSTALL_DIR.fetchFrom(params);
                 return (dir != null) ? dir : "/Applications";
             },
            (s, p) -> s
    );

    public static final BundlerParamInfo<String> INSTALLER_SUFFIX =
            new StandardBundlerParam<> (
            "mac.pkg.installerName.suffix",
            String.class,
            params -> "",
            (s, p) -> s);

    public File bundle(Map<String, ? super Object> params,
            File outdir) throws PackagerException {
        Log.verbose(MessageFormat.format(I18N.getString("message.building-pkg"),
                APP_NAME.fetchFrom(params)));

        IOUtils.writableOutputDir(outdir.toPath());

        try {
            File appImageDir = prepareAppBundle(params);

            if (appImageDir != null && prepareConfigFiles(params)) {

                File configScript = getConfig_Script(params);
                if (configScript.exists()) {
                    Log.verbose(MessageFormat.format(I18N.getString(
                            "message.running-script"),
                            configScript.getAbsolutePath()));
                    IOUtils.run("bash", configScript);
                }

                return createPKG(params, outdir, appImageDir);
            }
            return null;
        } catch (IOException ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    private File getPackages_AppPackage(Map<String, ? super Object> params) {
        return new File(PACKAGES_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-app.pkg");
    }

    private File getPackages_DaemonPackage(Map<String, ? super Object> params) {
        return new File(PACKAGES_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-daemon.pkg");
    }

    private File getConfig_DistributionXMLFile(
            Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params), "distribution.dist");
    }

    private File getConfig_BackgroundImage(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-background.png");
    }

    private File getScripts_PreinstallFile(Map<String, ? super Object> params) {
        return new File(SCRIPTS_DIR.fetchFrom(params), "preinstall");
    }

    private File getScripts_PostinstallFile(
            Map<String, ? super Object> params) {
        return new File(SCRIPTS_DIR.fetchFrom(params), "postinstall");
    }

    private String getAppIdentifier(Map<String, ? super Object> params) {
        return IDENTIFIER.fetchFrom(params);
    }

    private String getDaemonIdentifier(Map<String, ? super Object> params) {
        return IDENTIFIER.fetchFrom(params) + ".daemon";
    }

    private void preparePackageScripts(Map<String, ? super Object> params)
            throws IOException {
        Log.verbose(I18N.getString("message.preparing-scripts"));

        Map<String, String> data = new HashMap<>();

        data.put("INSTALL_LOCATION", MAC_INSTALL_DIR.fetchFrom(params));

        try (Writer w = Files.newBufferedWriter(
                getScripts_PreinstallFile(params).toPath())) {
            String content = preprocessTextResource(
                    getScripts_PreinstallFile(params).getName(),
                    I18N.getString("resource.pkg-preinstall-script"),
                    TEMPLATE_PREINSTALL_SCRIPT,
                    data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
        }
        getScripts_PreinstallFile(params).setExecutable(true, false);

        try (Writer w = Files.newBufferedWriter(
                getScripts_PostinstallFile(params).toPath())) {
            String content = preprocessTextResource(
                    getScripts_PostinstallFile(params).getName(),
                    I18N.getString("resource.pkg-postinstall-script"),
                    TEMPLATE_POSTINSTALL_SCRIPT,
                    data,
                    VERBOSE.fetchFrom(params),
                    RESOURCE_DIR.fetchFrom(params));
            w.write(content);
        }
        getScripts_PostinstallFile(params).setExecutable(true, false);
    }

    private void prepareDistributionXMLFile(Map<String, ? super Object> params)
            throws IOException {
        File f = getConfig_DistributionXMLFile(params);

        Log.verbose(MessageFormat.format(I18N.getString(
                "message.preparing-distribution-dist"), f.getAbsolutePath()));

        try (PrintStream out = new PrintStream(f)) {

            out.println(
                "<?xml version=\"1.0\" encoding=\"utf-8\" standalone=\"no\"?>");
            out.println("<installer-gui-script minSpecVersion=\"1\">");

            out.println("<title>" + APP_NAME.fetchFrom(params) + "</title>");
            out.println("<background" + " file=\""
                    + getConfig_BackgroundImage(params).getName()
                    + "\""
                    + " mime-type=\"image/png\""
                    + " alignment=\"bottomleft\" "
                    + " scaling=\"none\""
                    + "/>");

            String licFileStr = LICENSE_FILE.fetchFrom(params);
            if (licFileStr != null) {
                File licFile = new File(licFileStr);
                out.println("<license"
                        + " file=\"" + licFile.getAbsolutePath() + "\""
                        + " mime-type=\"text/rtf\""
                        + "/>");
            }

            /*
             * Note that the content of the distribution file
             * below is generated by productbuild --synthesize
             */

            String appId = getAppIdentifier(params);

            out.println("<pkg-ref id=\"" + appId + "\"/>");
            out.println(
                    "<options customize=\"never\" require-scripts=\"false\"/>");
            out.println("<choices-outline>");
            out.println("    <line choice=\"default\">");
            out.println("        <line choice=\"" + appId + "\"/>");
            out.println("    </line>");
            out.println("</choices-outline>");
            out.println("<choice id=\"default\"/>");
            out.println("<choice id=\"" + appId + "\" visible=\"false\">");
            out.println("    <pkg-ref id=\"" + appId + "\"/>");
            out.println("</choice>");
            out.println("<pkg-ref id=\"" + appId + "\" version=\""
                    + VERSION.fetchFrom(params) + "\" onConclusion=\"none\">"
                    + URLEncoder.encode(
                    getPackages_AppPackage(params).getName(),
                    "UTF-8") + "</pkg-ref>");

            out.println("</installer-gui-script>");

        }
    }

    private boolean prepareConfigFiles(Map<String, ? super Object> params)
            throws IOException {
        File imageTarget = getConfig_BackgroundImage(params);
        fetchResource(imageTarget.getName(),
                I18N.getString("resource.pkg-background-image"),
                DEFAULT_BACKGROUND_IMAGE,
                imageTarget,
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));

        prepareDistributionXMLFile(params);

        fetchResource(getConfig_Script(params).getName(),
                I18N.getString("resource.post-install-script"),
                (String) null,
                getConfig_Script(params),
                VERBOSE.fetchFrom(params),
                RESOURCE_DIR.fetchFrom(params));

        return true;
    }

    // name of post-image script
    private File getConfig_Script(Map<String, ? super Object> params) {
        return new File(CONFIG_ROOT.fetchFrom(params),
                APP_NAME.fetchFrom(params) + "-post-image.sh");
    }

    private void patchCPLFile(File cpl) throws IOException {
        String cplData = Files.readString(cpl.toPath());
        String[] lines = cplData.split("\n");
        try (PrintWriter out = new PrintWriter(Files.newBufferedWriter(
                cpl.toPath()))) {
            int skip = 0;
            // Used to skip Java.runtime bundle, since
            // pkgbuild with --root will find two bundles app and Java runtime.
            // We cannot generate component proprty list when using
            // --component argument.
            for (int i = 0; i < lines.length; i++) {
                if (lines[i].trim().equals("<key>BundleIsRelocatable</key>")) {
                    out.println(lines[i]);
                    out.println("<false/>");
                    i++;
                } else if (lines[i].trim().equals("<key>ChildBundles</key>")) {
                    ++skip;
                } else if ((skip > 0) && lines[i].trim().equals("</array>")) {
                    --skip;
                } else {
                    if (skip == 0) {
                        out.println(lines[i]);
                    }
                }
            }
        }
    }

    // pkgbuild includes all components from "--root" and subfolders,
    // so if we have app image in folder which contains other images, then they
    // will be included as well. It does have "--filter" option which use regex
    // to exclude files/folder, but it will overwrite default one which excludes
    // based on doc "any .svn or CVS directories, and any .DS_Store files".
    // So easy aproach will be to copy user provided app-image into temp folder
    // if root path contains other files.
    private String getRoot(Map<String, ? super Object> params,
            File appLocation) throws IOException {
        String root = appLocation.getParent() == null ?
                "." : appLocation.getParent();
        File rootDir = new File(root);
        File[] list = rootDir.listFiles();
        if (list != null) { // Should not happend
            // We should only have app image and/or .DS_Store
            if (list.length == 1) {
                return root;
            } else if (list.length == 2) {
                // Check case with app image and .DS_Store
                if (list[0].toString().toLowerCase().endsWith(".ds_store") ||
                    list[1].toString().toLowerCase().endsWith(".ds_store")) {
                    return root; // Only app image and .DS_Store
                }
            }
        }

        // Copy to new root
        Path newRoot = Files.createTempDirectory(
                TEMP_ROOT.fetchFrom(params).toPath(),
                "root-");

        IOUtils.copyRecursive(appLocation.toPath(),
                newRoot.resolve(appLocation.getName()));

        return newRoot.toString();
    }

    private File createPKG(Map<String, ? super Object> params,
            File outdir, File appLocation) {
        // generic find attempt
        try {
            File appPKG = getPackages_AppPackage(params);

            String root = getRoot(params, appLocation);

            // Generate default CPL file
            File cpl = new File(CONFIG_ROOT.fetchFrom(params).getAbsolutePath()
                    + File.separator + "cpl.plist");
            ProcessBuilder pb = new ProcessBuilder("pkgbuild",
                    "--root",
                    root,
                    "--install-location",
                    MAC_INSTALL_DIR.fetchFrom(params),
                    "--analyze",
                    cpl.getAbsolutePath());

            IOUtils.exec(pb);

            patchCPLFile(cpl);

            preparePackageScripts(params);

            // build application package
            pb = new ProcessBuilder("pkgbuild",
                    "--root",
                    root,
                    "--install-location",
                    MAC_INSTALL_DIR.fetchFrom(params),
                    "--component-plist",
                    cpl.getAbsolutePath(),
                    "--scripts",
                    SCRIPTS_DIR.fetchFrom(params).getAbsolutePath(),
                    appPKG.getAbsolutePath());
            IOUtils.exec(pb);

            // build final package
            File finalPKG = new File(outdir, INSTALLER_NAME.fetchFrom(params)
                    + INSTALLER_SUFFIX.fetchFrom(params)
                    + ".pkg");
            outdir.mkdirs();

            List<String> commandLine = new ArrayList<>();
            commandLine.add("productbuild");

            commandLine.add("--resources");
            commandLine.add(CONFIG_ROOT.fetchFrom(params).getAbsolutePath());

            // maybe sign
            if (Optional.ofNullable(MacAppImageBuilder.
                    SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.TRUE)) {
                if (Platform.getMajorVersion() > 10 ||
                    (Platform.getMajorVersion() == 10 &&
                    Platform.getMinorVersion() >= 12)) {
                    // we need this for OS X 10.12+
                    Log.verbose(I18N.getString("message.signing.pkg"));
                }

                String signingIdentity =
                        DEVELOPER_ID_INSTALLER_SIGNING_KEY.fetchFrom(params);
                if (signingIdentity != null) {
                    commandLine.add("--sign");
                    commandLine.add(signingIdentity);
                }

                String keychainName = SIGNING_KEYCHAIN.fetchFrom(params);
                if (keychainName != null && !keychainName.isEmpty()) {
                    commandLine.add("--keychain");
                    commandLine.add(keychainName);
                }
            }

            commandLine.add("--distribution");
            commandLine.add(
                    getConfig_DistributionXMLFile(params).getAbsolutePath());
            commandLine.add("--package-path");
            commandLine.add(PACKAGES_ROOT.fetchFrom(params).getAbsolutePath());

            commandLine.add(finalPKG.getAbsolutePath());

            pb = new ProcessBuilder(commandLine);
            IOUtils.exec(pb);

            return finalPKG;
        } catch (Exception ignored) {
            Log.verbose(ignored);
            return null;
        }
    }

    //////////////////////////////////////////////////////////////////////////
    // Implement Bundler
    //////////////////////////////////////////////////////////////////////////

    @Override
    public String getName() {
        return I18N.getString("pkg.bundler.name");
    }

    @Override
    public String getID() {
        return "pkg";
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        try {
            if (params == null) throw new ConfigException(
                    I18N.getString("error.parameters-null"),
                    I18N.getString("error.parameters-null.advice"));

            // run basic validation to ensure requirements are met
            // we are not interested in return code, only possible exception
            validateAppImageAndBundeler(params);

            // reject explicitly set sign to true and no valid signature key
            if (Optional.ofNullable(MacAppImageBuilder.
                    SIGN_BUNDLE.fetchFrom(params)).orElse(Boolean.FALSE)) {
                String signingIdentity =
                        DEVELOPER_ID_INSTALLER_SIGNING_KEY.fetchFrom(params);
                if (signingIdentity == null) {
                    throw new ConfigException(
                            I18N.getString("error.explicit-sign-no-cert"),
                            I18N.getString(
                            "error.explicit-sign-no-cert.advice"));
                }
            }

            // hdiutil is always available so there's no need
            // to test for availability.

            return true;
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    @Override
    public File execute(Map<String, ? super Object> params,
            File outputParentDir) throws PackagerException {
        return bundle(params, outputParentDir);
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return true;
    }

}
