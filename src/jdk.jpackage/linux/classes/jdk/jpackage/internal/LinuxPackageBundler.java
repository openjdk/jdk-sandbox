/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import static jdk.jpackage.internal.LinuxAppBundler.LINUX_INSTALL_DIR;
import static jdk.jpackage.internal.LinuxAppBundler.LINUX_PACKAGE_DEPENDENCIES;
import static jdk.jpackage.internal.LinuxAppImageBuilder.DEFAULT_ICON;
import static jdk.jpackage.internal.LinuxAppImageBuilder.ICON_PNG;
import static jdk.jpackage.internal.StandardBundlerParam.*;


abstract class LinuxPackageBundler extends AbstractBundler {

    protected static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.LinuxResources");

    private static final String DESKTOP_COMMANDS_INSTALL = "DESKTOP_COMMANDS_INSTALL";
    private static final String DESKTOP_COMMANDS_UNINSTALL = "DESKTOP_COMMANDS_UNINSTALL";
    private static final String UTILITY_SCRIPTS = "UTILITY_SCRIPTS";

    private static final BundlerParamInfo<LinuxAppBundler> APP_BUNDLER =
        new StandardBundlerParam<>(
                "linux.app.bundler",
                LinuxAppBundler.class,
                (params) -> new LinuxAppBundler(),
                null
        );

    private static final BundlerParamInfo<String> MENU_GROUP =
        new StandardBundlerParam<>(
                Arguments.CLIOptions.LINUX_MENU_GROUP.getId(),
                String.class,
                params -> I18N.getString("param.menu-group.default"),
                (s, p) -> s
        );

    private static final StandardBundlerParam<Boolean> SHORTCUT_HINT =
        new StandardBundlerParam<>(
                Arguments.CLIOptions.LINUX_SHORTCUT_HINT.getId(),
                Boolean.class,
                params -> false,
                (s, p) -> (s == null || "null".equalsIgnoreCase(s))
                        ? false : Boolean.valueOf(s)
        );

    LinuxPackageBundler(BundlerParamInfo<String> packageName) {
        this.packageName = packageName;
    }

    private final BundlerParamInfo<String> packageName;

    @Override
    final public boolean validate(Map<String, ? super Object> params)
            throws ConfigException {
        try {
            if (params == null) throw new ConfigException(
                    I18N.getString("error.parameters-null"),
                    I18N.getString("error.parameters-null.advice"));

            // run basic validation to ensure requirements are met
            // we are not interested in return code, only possible exception
            APP_BUNDLER.fetchFrom(params).validate(params);

            validateFileAssociations(FILE_ASSOCIATIONS.fetchFrom(params));

            // If package name has some restrictions, the string converter will
            // throw an exception if invalid
            packageName.getStringConverter().apply(packageName.fetchFrom(params),
                params);

            // Packaging specific validation
            doValidate(params);

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
    final public String getBundleType() {
        return "INSTALLER";
    }

    @Override
    final public File execute(Map<String, ? super Object> params,
            File outputParentDir) throws PackagerException {
        IOUtils.writableOutputDir(outputParentDir.toPath());

        PlatformPackage thePackage = createMetaPackage(params);

        try {
            File appImage = StandardBundlerParam.getPredefinedAppImage(params);

            // we either have an application image or need to build one
            if (appImage != null) {
                appImageLayout(params).resolveAt(appImage.toPath()).copy(
                        thePackage.sourceApplicationLayout());
            } else {
                appImage = APP_BUNDLER.fetchFrom(params).doBundle(params,
                        thePackage.sourceRoot().toFile(), true);
                ApplicationLayout srcAppLayout = appImageLayout(params).resolveAt(
                        appImage.toPath());
                if (appImage.equals(PREDEFINED_RUNTIME_IMAGE.fetchFrom(params))) {
                    // Application image points to run-time image.
                    // Copy it.
                    srcAppLayout.copy(thePackage.sourceApplicationLayout());
                } else {
                    // Application image is a newly created directory tree.
                    // Move it.
                    srcAppLayout.move(thePackage.sourceApplicationLayout());
                    if (appImage.exists()) {
                        // Empty app image directory might remain after all application
                        // directories have been moved.
                        appImage.delete();
                    }
                }
            }

            Map<String, String> data = createDefaultReplacementData(params);
            if (StandardBundlerParam.isRuntimeInstaller(params)) {
                Stream.of(DESKTOP_COMMANDS_INSTALL, DESKTOP_COMMANDS_UNINSTALL,
                        UTILITY_SCRIPTS).forEach(v -> data.put(v, ""));
            } else {
                data.putAll(
                        new DesktopIntegration(thePackage, params).prepareForApplication());
            }

            data.putAll(createReplacementData(params));

            return buildPackageBundle(Collections.unmodifiableMap(data), params,
                    outputParentDir);
        } catch (IOException ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    private Map<String, String> createDefaultReplacementData(
            Map<String, ? super Object> params) throws IOException {
        Map<String, String> data = new HashMap<>();

        data.put("APPLICATION_PACKAGE", createMetaPackage(params).name());
        data.put("APPLICATION_VENDOR", VENDOR.fetchFrom(params));
        data.put("APPLICATION_VERSION", VERSION.fetchFrom(params));
        data.put("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(params));
        data.put("APPLICATION_RELEASE", RELEASE.fetchFrom(params));
        data.put("PACKAGE_DEPENDENCIES", LINUX_PACKAGE_DEPENDENCIES.fetchFrom(
                params));

        return data;
    }

    abstract void doValidate(Map<String, ? super Object> params)
            throws ConfigException;

    abstract protected Map<String, String> createReplacementData(
            Map<String, ? super Object> params) throws IOException;

    abstract protected File buildPackageBundle(
            Map<String, String> replacementData,
            Map<String, ? super Object> params, File outputParentDir) throws
            PackagerException, IOException;

    final protected PlatformPackage createMetaPackage(
            Map<String, ? super Object> params) {
        return new PlatformPackage() {
            @Override
            public String name() {
                return packageName.fetchFrom(params);
            }

            @Override
            public Path sourceRoot() {
                return IMAGES_ROOT.fetchFrom(params).toPath().toAbsolutePath();
            }

            @Override
            public ApplicationLayout sourceApplicationLayout() {
                return appImageLayout(params).resolveAt(
                        applicationInstallDir(sourceRoot()));
            }

            @Override
            public ApplicationLayout installedApplicationLayout() {
                return appImageLayout(params).resolveAt(
                        applicationInstallDir(Path.of("/")));
            }

            private Path applicationInstallDir(Path root) {
                Path installDir = Path.of(LINUX_INSTALL_DIR.fetchFrom(params),
                        name());
                if (installDir.isAbsolute()) {
                    installDir = Path.of("." + installDir.toString()).normalize();
                }
                return root.resolve(installDir);
            }
        };
    }

    private ApplicationLayout appImageLayout(
            Map<String, ? super Object> params) {
        if (StandardBundlerParam.isRuntimeInstaller(params)) {
            return ApplicationLayout.javaRuntime();
        }
        return ApplicationLayout.unixApp();
    }

    private static void validateFileAssociations(
            List<Map<String, ? super Object>> associations) throws
            ConfigException {
        // only one mime type per association, at least one file extention
        int assocIdx = 0;
        for (var assoc : associations) {
            ++assocIdx;
            List<String> mimes = FA_CONTENT_TYPE.fetchFrom(assoc);
            if (mimes == null || mimes.isEmpty()) {
                String msgKey = "error.no-content-types-for-file-association";
                throw new ConfigException(
                        MessageFormat.format(I18N.getString(msgKey), assocIdx),
                        I18N.getString(msgKey + ".advise"));

            }

            if (mimes.size() > 1) {
                String msgKey = "error.too-many-content-types-for-file-association";
                throw new ConfigException(
                        MessageFormat.format(I18N.getString(msgKey), assocIdx),
                        I18N.getString(msgKey + ".advise"));
            }
        }
    }

    /**
     * Helper to create files for desktop integration.
     */
    private class DesktopIntegration {

        DesktopIntegration(PlatformPackage thePackage,
                Map<String, ? super Object> params) {

            associations = FILE_ASSOCIATIONS.fetchFrom(params).stream().filter(
                    a -> {
                        if (a == null) {
                            return false;
                        }
                        List<String> mimes = FA_CONTENT_TYPE.fetchFrom(a);
                        return (mimes != null && !mimes.isEmpty());
                    }).collect(Collectors.toUnmodifiableList());

            launchers = ADD_LAUNCHERS.fetchFrom(params);

            this.thePackage = thePackage;

            customIconFile = ICON_PNG.fetchFrom(params);

            verbose = VERBOSE.fetchFrom(params);
            resourceDir = RESOURCE_DIR.fetchFrom(params);

            // XDG recommends to use vendor prefix in desktop file names as xdg
            // commands copy files to system directories.
            // Package name should be a good prefix.
            final String desktopFileName = String.format("%s-%s.desktop",
                        thePackage.name(), APP_NAME.fetchFrom(params));
            final String mimeInfoFileName = String.format("%s-%s-MimeInfo.xml",
                        thePackage.name(), APP_NAME.fetchFrom(params));

            mimeInfoFile = new DesktopFile(mimeInfoFileName);

            if (!associations.isEmpty() || SHORTCUT_HINT.fetchFrom(params) || customIconFile != null) {
                //
                // Create primary .desktop file if one of conditions is met:
                // - there are file associations configured
                // - user explicitely requested to create a shortcut
                // - custom icon specified
                //
                desktopFile = new DesktopFile(desktopFileName);
                iconFile = new DesktopFile(String.format("%s.png",
                        APP_NAME.fetchFrom(params)));
            } else {
                desktopFile = null;
                iconFile = null;
            }

            this.desktopFileData = Collections.unmodifiableMap(
                    createDataForDesktopFile(params));
        }

        Map<String, String> prepareForApplication() throws IOException {
            if (iconFile != null) {
                // Create application icon file.
                prepareSrcIconFile();
            }

            Map<String, String> data = new HashMap<>(desktopFileData);

            final ShellCommands shellCommands;
            if (desktopFile != null) {
                // Create application desktop description file.
                createDesktopFile(data);

                // Shell commands will be created only if desktop file
                // should be installed.
                shellCommands = new ShellCommands();
            } else {
                shellCommands = null;
            }

            if (!associations.isEmpty()) {
                // Create XML file with mime types corresponding to file associations.
                createFileAssociationsMimeInfoFile();

                shellCommands.setFileAssociations();

                // Create icon files corresponding to file associations
                Map<String, Path> mimeTypeWithIconFile = createFileAssociationIconFiles();
                mimeTypeWithIconFile.forEach((k, v) -> {
                    shellCommands.addIcon(k, v);
                });
            }

            // Create shell commands to install/uninstall integration with desktop of the app.
            if (shellCommands != null) {
                shellCommands.applyTo(data);
            }

            boolean needCleanupScripts = !associations.isEmpty();

            // Take care of additional launchers if there are any.
            // Process every additional launcher as the main application launcher.
            // Collect shell commands to install/uninstall integration with desktop
            // of the additional launchers and append them to the corresponding
            // commands of the main launcher.
            List<String> installShellCmds = new ArrayList<>(Arrays.asList(
                    data.get(DESKTOP_COMMANDS_INSTALL)));
            List<String> uninstallShellCmds = new ArrayList<>(Arrays.asList(
                    data.get(DESKTOP_COMMANDS_UNINSTALL)));
            for (Map<String, ? super Object> params : launchers) {
                DesktopIntegration integration = new DesktopIntegration(
                        thePackage, params);

                if (!integration.associations.isEmpty()) {
                    needCleanupScripts = true;
                }

                Map<String, String> launcherData = integration.prepareForApplication();

                installShellCmds.add(launcherData.get(DESKTOP_COMMANDS_INSTALL));
                uninstallShellCmds.add(launcherData.get(
                        DESKTOP_COMMANDS_UNINSTALL));
            }

            data.put(DESKTOP_COMMANDS_INSTALL, stringifyShellCommands(
                    installShellCmds));
            data.put(DESKTOP_COMMANDS_UNINSTALL, stringifyShellCommands(
                    uninstallShellCmds));

            if (needCleanupScripts) {
                // Pull in utils.sh scrips library.
                try (InputStream is = getResourceAsStream("utils.sh");
                        InputStreamReader isr = new InputStreamReader(is);
                        BufferedReader reader = new BufferedReader(isr)) {
                    data.put(UTILITY_SCRIPTS, reader.lines().collect(
                            Collectors.joining(System.lineSeparator())));
                }
            } else {
                data.put(UTILITY_SCRIPTS, "");
            }

            return data;
        }

        private Map<String, String> createDataForDesktopFile(
                Map<String, ? super Object> params) {
            Map<String, String> data = new HashMap<>();
            data.put("APPLICATION_NAME", APP_NAME.fetchFrom(params));
            data.put("APPLICATION_DESCRIPTION", DESCRIPTION.fetchFrom(params));
            data.put("APPLICATION_ICON",
                    iconFile != null ? iconFile.installPath().toString() : null);
            data.put("DEPLOY_BUNDLE_CATEGORY", MENU_GROUP.fetchFrom(params));
            data.put("APPLICATION_LAUNCHER",
                    thePackage.installedApplicationLayout().launchersDirectory().resolve(
                            LinuxAppImageBuilder.getLauncherName(params)).toString());

            return data;
        }

        /**
         * Shell commands to integrate something with desktop.
         */
        private class ShellCommands {

            ShellCommands() {
                registerIconCmds = new ArrayList<>();
                unregisterIconCmds = new ArrayList<>();

                registerDesktopFileCmd = String.join(" ", "xdg-desktop-menu",
                        "install", desktopFile.installPath().toString());
                unregisterDesktopFileCmd = String.join(" ", "xdg-desktop-menu",
                        "uninstall", desktopFile.installPath().toString());
            }

            void setFileAssociations() {
                registerFileAssociationsCmd = String.join(" ", "xdg-mime",
                        "install",
                        mimeInfoFile.installPath().toString());
                unregisterFileAssociationsCmd = String.join(" ", "xdg-mime",
                        "uninstall", mimeInfoFile.installPath().toString());

                //
                // Add manual cleanup of system files to get rid of
                // the default mime type handlers.
                //
                // Even after mime type is unregisterd with `xdg-mime uninstall`
                // command and desktop file deleted with `xdg-desktop-menu uninstall`
                // command, records in
                // `/usr/share/applications/defaults.list` (Ubuntu 16) or
                // `/usr/local/share/applications/defaults.list` (OracleLinux 7)
                // files remain referencing deleted mime time and deleted
                // desktop file which makes `xdg-mime query default` output name
                // of non-existing desktop file.
                //
                String cleanUpCommand = String.join(" ",
                        "uninstall_default_mime_handler",
                        desktopFile.installPath().getFileName().toString(),
                        String.join(" ", getMimeTypeNamesFromFileAssociations()));

                unregisterFileAssociationsCmd = stringifyShellCommands(
                        unregisterFileAssociationsCmd, cleanUpCommand);
            }

            void addIcon(String mimeType, Path iconFile) {
                final int imgSize = getSquareSizeOfImage(iconFile.toFile());
                final String dashMime = mimeType.replace('/', '-');
                registerIconCmds.add(String.join(" ", "xdg-icon-resource",
                        "install", "--context", "mimetypes", "--size ",
                        Integer.toString(imgSize), iconFile.toString(), dashMime));
                unregisterIconCmds.add(String.join(" ", "xdg-icon-resource",
                        "uninstall", dashMime));
            }

            void applyTo(Map<String, String> data) {
                List<String> cmds = new ArrayList<>();

                cmds.add(registerDesktopFileCmd);
                cmds.add(registerFileAssociationsCmd);
                cmds.addAll(registerIconCmds);
                data.put(DESKTOP_COMMANDS_INSTALL, stringifyShellCommands(cmds));

                cmds.clear();
                cmds.add(unregisterDesktopFileCmd);
                cmds.add(unregisterFileAssociationsCmd);
                cmds.addAll(unregisterIconCmds);
                data.put(DESKTOP_COMMANDS_UNINSTALL, stringifyShellCommands(cmds));
            }

            private String registerDesktopFileCmd;
            private String unregisterDesktopFileCmd;

            private String registerFileAssociationsCmd;
            private String unregisterFileAssociationsCmd;

            private List<String> registerIconCmds;
            private List<String> unregisterIconCmds;
        }

        private final PlatformPackage thePackage;

        private final List<Map<String, ? super Object>> associations;

        private final List<Map<String, ? super Object>> launchers;

        /**
         * Desktop integration file. xml, icon, etc.
         * Resides somewhere in application installation tree.
         * Has two paths:
         *  - path where it should be placed at package build time;
         *  - path where it should be installed by package manager;
         */
        private class DesktopFile {

            DesktopFile(String fileName) {
                installPath = thePackage
                        .installedApplicationLayout()
                        .destktopIntegrationDirectory().resolve(fileName);
                srcPath = thePackage
                        .sourceApplicationLayout()
                        .destktopIntegrationDirectory().resolve(fileName);
            }

            private final Path installPath;
            private final Path srcPath;

            Path installPath() {
                return installPath;
            }

            Path srcPath() {
                return srcPath;
            }
        }

        private final boolean verbose;
        private final File resourceDir;

        private final DesktopFile mimeInfoFile;
        private final DesktopFile desktopFile;
        private final DesktopFile iconFile;

        private final Map<String, String> desktopFileData;

        /**
         * Path to icon file provided by user or null.
         */
        private final File customIconFile;

        private void appendFileAssociation(XMLStreamWriter xml,
                Map<String, ? super Object> assoc) throws XMLStreamException {

            xml.writeStartElement("mime-type");
            final String thisMime = FA_CONTENT_TYPE.fetchFrom(assoc).get(0);
            xml.writeAttribute("type", thisMime);

            final String description = FA_DESCRIPTION.fetchFrom(assoc);
            if (description != null && !description.isEmpty()) {
                xml.writeStartElement("comment");
                xml.writeCharacters(description);
                xml.writeEndElement();
            }

            final List<String> extensions = FA_EXTENSIONS.fetchFrom(assoc);
            if (extensions == null) {
                Log.error(I18N.getString(
                        "message.creating-association-with-null-extension"));
            } else {
                for (String ext : extensions) {
                    xml.writeStartElement("glob");
                    xml.writeAttribute("pattern", "*." + ext);
                    xml.writeEndElement();
                }
            }

            xml.writeEndElement();
        }

        private void createFileAssociationsMimeInfoFile() throws IOException {
            XMLOutputFactory xmlFactory = XMLOutputFactory.newInstance();

            try (Writer w = new BufferedWriter(new FileWriter(
                    mimeInfoFile.srcPath().toFile()))) {
                XMLStreamWriter xml = xmlFactory.createXMLStreamWriter(w);

                xml.writeStartDocument();
                xml.writeStartElement("mime-info");
                xml.writeNamespace("xmlns",
                        "http://www.freedesktop.org/standards/shared-mime-info");

                for (var assoc : associations) {
                    appendFileAssociation(xml, assoc);
                }

                xml.writeEndElement();
                xml.writeEndDocument();
                xml.flush();
                xml.close();

            } catch (XMLStreamException ex) {
                Log.verbose(ex);
                throw new IOException(ex);
            }
        }

        private Map<String, Path> createFileAssociationIconFiles() throws
                IOException {
            Map<String, Path> mimeTypeWithIconFile = new HashMap<>();
            for (var assoc : associations) {
                File customFaIcon = FA_ICON.fetchFrom(assoc);
                if (customFaIcon == null || !customFaIcon.exists() || getSquareSizeOfImage(
                        customFaIcon) == 0) {
                    continue;
                }

                String fname = iconFile.srcPath().getFileName().toString();
                if (fname.indexOf(".") > 0) {
                    fname = fname.substring(0, fname.lastIndexOf("."));
                }

                DesktopFile faIconFile = new DesktopFile(
                        fname + "_fa_" + customFaIcon.getName());

                IOUtils.copyFile(customFaIcon, faIconFile.srcPath().toFile());

                mimeTypeWithIconFile.put(FA_CONTENT_TYPE.fetchFrom(assoc).get(0),
                        faIconFile.installPath());
            }
            return mimeTypeWithIconFile;
        }

        private void createDesktopFile(Map<String, String> data) throws IOException {
            List<String> mimeTypes = getMimeTypeNamesFromFileAssociations();
            data.put("DESKTOP_MIMES", "MimeType=" + String.join(";", mimeTypes));

            // prepare desktop shortcut
            try (Writer w = Files.newBufferedWriter(desktopFile.srcPath())) {
                String content = preprocessTextResource(
                        desktopFile.srcPath().getFileName().toString(),
                        I18N.getString("resource.menu-shortcut-descriptor"),
                        "template.desktop",
                        data,
                        verbose,
                        resourceDir);
                w.write(content);
            }
        }

        private void prepareSrcIconFile() throws IOException {
            if (customIconFile == null || !customIconFile.exists()) {
                fetchResource(iconFile.srcPath().getFileName().toString(),
                        I18N.getString("resource.menu-icon"),
                        DEFAULT_ICON,
                        iconFile.srcPath().toFile(),
                        verbose,
                        resourceDir);
            } else {
                fetchResource(iconFile.srcPath().getFileName().toString(),
                        I18N.getString("resource.menu-icon"),
                        customIconFile,
                        iconFile.srcPath().toFile(),
                        verbose,
                        resourceDir);
            }
        }

        private List<String> getMimeTypeNamesFromFileAssociations() {
            return associations.stream().map(
                    a -> FA_CONTENT_TYPE.fetchFrom(a).get(0)).collect(
                            Collectors.toUnmodifiableList());
        }
    }

    private static int getSquareSizeOfImage(File f) {
        try {
            BufferedImage bi = ImageIO.read(f);
            if (bi.getWidth() == bi.getHeight()) {
                return bi.getWidth();
            }
        } catch (IOException e) {
            Log.verbose(e);
        }
        return 0;
    }

    private static String stringifyShellCommands(String ... commands) {
        return stringifyShellCommands(Arrays.asList(commands));
    }

    private static String stringifyShellCommands(List<String> commands) {
        return String.join(System.lineSeparator(), commands.stream().filter(
                s -> s != null && !s.isEmpty()).collect(Collectors.toList()));
    }
}
