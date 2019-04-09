/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jnlp.converter;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import jnlp.converter.parser.JNLPDesc;
import jnlp.converter.parser.JNLPDesc.AssociationDesc;
import jnlp.converter.parser.JNLPDesc.IconDesc;
import jnlp.converter.parser.ResourcesDesc.JARDesc;
import jnlp.converter.parser.XMLFormat;

public class JNLPConverter {

    private final Options options;
    private JNLPDesc jnlpd = null;
    private final List<String> launchArgs = new ArrayList<>();

    private String downloadFolder = null;
    private String jnlpDownloadFolder = null;
    private static String jnlpDownloadFolderStatic;
    private String jarDownloadFolder = null;
    private String iconDownloadFolder = null;
    private String propDownloadFolder = null;

    private static String jpackagePath = null;

    private static boolean markFileToDelete = false;

    private static final String FA_EXTENSIONS = "extension";
    private static final String FA_CONTENT_TYPE = "mime-type";
    private static final String FA_DESCRIPTION = "description";
    private static final String FA_ICON = "icon";

    public JNLPConverter(Options options) {
        this.options = options;
        jnlpDownloadFolderStatic = getJnlpDownloadFolder();
        markFileToDelete = (options.keep() == null);
    }

    public String [] getLaunchArgs() {
        return launchArgs.toArray(new String[0]);
    }

    public void convert() {
        try {
            loadJNLPDesc();
            downloadResources();
            validate();
            buildLaunchArgs();
            saveLaunchArgs();
            runJPackage();
        } catch (Exception ex) {
            Log.error(ex.getMessage());
        }
    }

    private JNLPDesc getJNLPD(String jnlp) throws Exception {
        URL codebase = getCodeBase(jnlp);
        byte[] bits = HTTPHelper.getJNLPBits(jnlp, jnlp);
        return XMLFormat.parse(bits, codebase, jnlp);
    }

    private void loadJNLPDesc() throws Exception {
        String jnlp = options.getJNLP();
        jnlpd = getJNLPD(jnlp);

        // Check for required options in case of FX
        if (jnlpd.isFXApp()) {
            if (!options.isRuntimeImageSet()) {
                throw new Exception("This is a JavaFX Web-Start application which requires a runtime image capable of running JavaFX applications, which can be specified by the jpackage option --runtime-image (using --jpackage-options).");
            }
        }

        // Check href. It can be same as URL we provided or new one
        // if JNLP has different href or codebase. We assume that
        // XMLFormat.parse() will handle any errors in href and codebase
        // correctly.
        String href = jnlpd.getHref();
        if (href != null && !href.equalsIgnoreCase(jnlp)) {
            if (href.startsWith("file:")) {
                URI hrefURI = new URI(href);
                URI jnlpURI = new URI(jnlp);

                String hrefPath = hrefURI.getPath();
                String jnlpPath = jnlpURI.getPath();

                if (!hrefPath.equalsIgnoreCase(jnlpPath)) {
                    jnlp = href;
                    jnlpd = getJNLPD(jnlp);
                }
            } else {
                jnlp = href;
                jnlpd = getJNLPD(jnlp);
            }
        }

        if (jnlpd.getName() == null) {
            jnlpd.setName(getNameFromURL(jnlp));
        }
    }

    private static String getNameFromURL(String url) throws IOException {
        int index;
        int index1 = url.lastIndexOf('/');
        int index2 = url.lastIndexOf('\\');

        if (index1 >= index2) {
            index = index1;
        } else {
            index = index2;
        }

        if (index != -1) {
            String name = url.substring(index + 1, url.length());
            if (name.endsWith(".jnlp")) {
                return name.substring(0, name.length() - 5);
            }
        }

        return null;
    }

    private URL getCodeBase(String jnlp) throws Exception {
        int index = jnlp.lastIndexOf('/');
        if (index != -1) {
            if (HTTPHelper.isHTTPUrl(jnlp)) {
                return new URL(jnlp.substring(0, index + 1));
            } else {
                String codeBasePath = jnlp.substring(0, index);
                if (!codeBasePath.endsWith("/")) {
                    codeBasePath += "/";
                }
                return new URI(codeBasePath).toURL();
            }
        }

        return null;
    }

    public static void markFileToDelete(String file) {
        if (file == null || file.isEmpty()) {
            return;
        }

        if (markFileToDelete) {
            try {
                File f = new File(file);
                f.deleteOnExit();
            } catch (Exception e) {
                // Print exception, but do not fail conversion.
                Log.warning(e.getLocalizedMessage());
            }
        }
    }

    public static void deleteFile(String file) {
        try {
            File f = new File(file);
            f.delete();
        } catch (Exception e) {
            Log.warning(e.getLocalizedMessage());
        }
    }

    private void downloadResources() throws Exception {
        List<JARDesc> jars = jnlpd.getResources();
        for (JARDesc jar : jars) {
            if (jar.getVersion() != null) {
                if (!jnlpd.isVersionEnabled()) {
                    throw new Exception("Error: Version based download protocol is not supported without -Djnlp.versionEnabled=true.");
                }
            }

            String destFile = null;
            if (HTTPHelper.isHTTPUrl(jar.getLocation().toString())) {
                if (jar.getVersion() != null) {
                    try {
                        destFile = HTTPHelper.downloadFile(jar.getVersionLocation().toString(), getJarDownloadFolder(), HTTPHelper.getFileNameFromURL(jar.getLocation().toString()));
                    } catch (HTTPHelperException ex) {
                        if (ex.getResponseCode() == HttpURLConnection.HTTP_NOT_FOUND) {
                            System.out.println("Error downloading versioned JAR from " + jar.getVersionLocation());
                            System.out.println(ex.getMessage());
                            System.out.println("Downloading " + jar.getLocation() + " instead.");
                            destFile = HTTPHelper.downloadFile(jar.getLocation().toString(), getJarDownloadFolder(), HTTPHelper.getFileNameFromURL(jar.getLocation().toString()));
                        } else {
                            throw ex;
                        }
                    }
                } else {
                    destFile = HTTPHelper.downloadFile(jar.getLocation().toString(), getJarDownloadFolder(), HTTPHelper.getFileNameFromURL(jar.getLocation().toString()));
                }
                markFileToDelete(destFile);
            } else {
                if (jar.getVersion() != null) {
                    try {
                        destFile = HTTPHelper.copyFile(jar.getVersionLocation().toString(), getJarDownloadFolder(), HTTPHelper.getFileNameFromURL(jar.getLocation().toString()));
                    } catch (FileNotFoundException ex) {
                        System.out.println("Error copying versioned JAR from " + jar.getVersionLocation());
                        System.out.println(ex.getMessage());
                        System.out.println("Copying " + jar.getLocation() + " instead.");
                        destFile = HTTPHelper.copyFile(jar.getLocation().toString(), getJarDownloadFolder(), HTTPHelper.getFileNameFromURL(jar.getLocation().toString()));
                    }
                } else {
                    destFile = HTTPHelper.copyFile(jar.getLocation().toString(), getJarDownloadFolder(), HTTPHelper.getFileNameFromURL(jar.getLocation().toString()));
                }
                markFileToDelete(destFile);
            }

            if (jar.isNativeLib()) {
                unpackNativeLib(destFile);
                deleteFile(destFile);
            } else {
                jnlpd.addFile(jar.getName());
            }
        }

        IconDesc icon = jnlpd.getIcon();
        if (icon != null) {
            String destFile;

            if (HTTPHelper.isHTTPUrl(icon.getLocation())) {
                destFile = HTTPHelper.downloadFile(icon.getLocation(), getIconDownloadFolder(), HTTPHelper.getFileNameFromURL(icon.getLocation()));
            } else {
                destFile = HTTPHelper.copyFile(icon.getLocation(), getIconDownloadFolder(), HTTPHelper.getFileNameFromURL(icon.getLocation()));
            }

            markFileToDelete(destFile);
            icon.setLocalLocation(destFile);
        }

        AssociationDesc [] associations = jnlpd.getAssociations();
        if (associations != null) {
            for (AssociationDesc association : associations) {
                if (association.getIconUrl() != null) {
                    String destFile;
                    if (HTTPHelper.isHTTPUrl(association.getIconUrl())) {
                        destFile = HTTPHelper.downloadFile(association.getIconUrl(), getIconDownloadFolder(), HTTPHelper.getFileNameFromURL(association.getIconUrl()));
                    } else {
                        destFile = HTTPHelper.copyFile(association.getIconUrl(), getIconDownloadFolder(), HTTPHelper.getFileNameFromURL(association.getIconUrl()));
                    }

                    markFileToDelete(destFile);
                    association.setIconLocalLocation(destFile);
                }
            }
        }
    }

    public void unpackNativeLib(String file) throws IOException {
        try (JarFile jarFile = new JarFile(file)) {
            Enumeration entries = jarFile.entries();

            while (entries.hasMoreElements()) {
                JarEntry entry = (JarEntry) entries.nextElement();

                // Skip directories
                if (entry.isDirectory()) {
                    continue;
                }

                String entryName = entry.getName();
                // Skip anything in sub-directories
                if (entryName.contains("\\") || entryName.contains("/")) {
                    continue;
                }

                // Skip anything not ending with .dll, .dylib or .so
                if (!entryName.endsWith(".dll") && !entryName.endsWith(".dylib") && !entryName.endsWith(".so")) {
                    continue;
                }

                File destFile = new File(getJarDownloadFolder(), entryName);
                if (destFile.exists()) {
                    Log.warning(destFile.getAbsolutePath() + " already exist and will not be overwriten by native library from " + file + ".");
                    continue;
                }

                InputStream inputStream = jarFile.getInputStream(entry);
                FileOutputStream outputStream = new FileOutputStream(destFile);

                byte[] buffer = new byte[HTTPHelper.BUFFER_SIZE];
                int length;
                do {
                    length = inputStream.read(buffer);
                    if (length > 0) {
                        outputStream.write(buffer, 0, length);
                    }
                } while (length > 0);

                jnlpd.addFile(entryName);
            }
        }
    }

    private void validate() {
        if (jnlpd.getMainJar() == null) {
            Log.error("Cannot find main jar");
        }

        if (jnlpd.getMainClass() == null) {
            Log.error("Cannot find main class");
        }
    }

    private void addLaunchArg(String arg, List<String> launchArgs) {
        if (arg != null && !arg.isEmpty()) {
            if (!options.isOptionPresent(arg)){
                launchArgs.add(arg);
            } else {
                Log.info(arg + " generated by JNLPConverter is dropped, since it is overwriten via --jpackage-options");
            }
        }
    }

    private void addLaunchArg(String arg, String value, List<String> launchArgs) {
        if (arg != null && !arg.isEmpty() && value != null && !value.isEmpty()) {
            if (!options.isOptionPresent(arg)){
                launchArgs.add(arg);
                launchArgs.add(value);
            } else {
                Log.info(arg + "=" + value +" generated by JNLPConverter is dropped, since it is overwriten via --jpackage-options");
            }
        }
    }

    private void displayLaunchArgs() {
        if (Log.isVerbose()) {
            System.out.println();
            System.out.println("jpackage launch arguments (each argument starts on new line):");
            launchArgs.forEach((arg) -> {
                System.out.println(arg);
            });
        }
    }

    private static int fileAssociationsCount = 0;
    private String getFileAssociationsFile() {
        String file = getPropDownloadFolder();
        file += File.separator;
        file += "fileAssociation";
        file += String.valueOf(fileAssociationsCount);
        file += ".properties";

        fileAssociationsCount++;

        return file;
    }

    private void buildLaunchArgs() {
        if (options.createAppImage()) {
            addLaunchArg("create-app-image", launchArgs);
        } else if (options.createInstaller()) {
            if (options.getInstallerType() == null) {
                addLaunchArg("create-installer", launchArgs);
            } else {
                addLaunchArg("create-installer", launchArgs);
                if (options.getInstallerType() != null) {
                    addLaunchArg("--installer-type", options.getInstallerType(), launchArgs);
                }
            }
        }

        // Set verbose for jpackage if it is set for us.
        if (options.verbose()) {
            addLaunchArg("--verbose", launchArgs);
        }

        addLaunchArg("--input", getJarDownloadFolder(), launchArgs);
        addLaunchArg("--output", options.getOutput(), launchArgs);
        addLaunchArg("--name", jnlpd.getName(), launchArgs);
        addLaunchArg("--app-version", jnlpd.getVersion(), launchArgs);
        addLaunchArg("--vendor", jnlpd.getVendor(), launchArgs);
        addLaunchArg("--description", jnlpd.getDescription(), launchArgs);
        addLaunchArg("--icon", jnlpd.getIconLocation(), launchArgs);
        addLaunchArg("--main-jar", jnlpd.getMainJar(), launchArgs);
        addLaunchArg("--main-class", jnlpd.getMainClass(), launchArgs);

        addArguments(launchArgs);
        addJVMArgs(launchArgs);

        if (options.createInstaller()) {
            if (jnlpd.isDesktopHint()) {
                if (Platform.isWindows()) {
                    addLaunchArg("--win-shortcut", launchArgs);
                } else {
                    Log.warning("Ignoring shortcut hint, since it is not supported on current platform.");
                }
            }

            if (jnlpd.isMenuHint()) {
                if (Platform.isWindows()) {
                    addLaunchArg("--win-menu", launchArgs);
                    addLaunchArg("--win-menu-group", jnlpd.getSubMenu(), launchArgs);
                } else {
                    Log.warning("Ignoring menu hint, since it is not supported on current platform.");
                }
            }

            AssociationDesc[] associations = jnlpd.getAssociations();
            if (associations != null) {
                for (AssociationDesc association : associations) {
                    String file = getFileAssociationsFile();
                    markFileToDelete(file);

                    try (PrintWriter out = new PrintWriter(new BufferedWriter(new FileWriter(file)))) {
                        if (association.getExtensions() != null && association.getMimeType() != null) {
                            out.println(FA_EXTENSIONS + "=" + quote(association.getExtensions()));
                            out.println(FA_CONTENT_TYPE + "=" + quote(association.getMimeType()));

                            if (association.getMimeDescription() != null) {
                                out.println(FA_DESCRIPTION + "=" + association.getMimeDescription());
                            }

                            if (association.getIconLocalLocation() != null) {
                                out.println(FA_ICON + "=" + quote(association.getIconLocalLocation()));
                            }

                            addLaunchArg("--file-associations", file, launchArgs);
                        }
                    } catch (Exception ex) {
                        Log.warning(ex.toString());
                        if (association.getExtensions() != null) {
                            Log.warning("File assoication for " + association.getExtensions() + " will be ignored due to exception above.");
                        }
                    }
                }
            }
        }

        // Add options from --jpackage-options
        List<String> jpackageOptions = options.getJPackageOptions();
        jpackageOptions.forEach((option) -> {
            launchArgs.add(option);
        });

        displayLaunchArgs();
    }

    private String getCommandFileName() {
        Platform platform = Platform.getPlatform();
        switch (platform) {
            case WINDOWS:
                return "run_jpackage.bat";
            case LINUX:
                return "run_jpackage.sh";
            case MAC:
                return "run_jpackage.sh";
            default:
                Log.error("Cannot determine platform type.");
                return "";
        }
    }

    private void saveLaunchArgs() {
        if (options.keep() != null) {
            File keepFolder = new File(options.keep());
            String cmdFile = keepFolder.getAbsolutePath() + File.separator + getCommandFileName();
            try (PrintWriter out = new PrintWriter(cmdFile)) {
                out.print(getJPackagePath());
                launchArgs.forEach((arg) -> {
                    out.print(" ");

                    if (arg.contains(" ")) {
                        int len = arg.length();
                        if (len >= 1) {
                            if (arg.charAt(0) != '"' && arg.charAt(len - 1) != '"') {
                                out.print("\"" + arg + "\"");
                            } else {
                                if (Platform.isWindows()) {
                                    out.print(arg);
                                } else {
                                    arg = escapeQuote(arg);
                                    out.print("\"" + arg + "\"");
                                }
                            }
                        }
                    } else {
                        out.print(arg);
                    }
                });
            } catch (FileNotFoundException ex) {
                Log.error("Cannot save file with command line: " + ex.getLocalizedMessage());
            }
        }
    }

    private void runJPackage() {
        List<String> command = new ArrayList<>();
        command.add(getJPackagePath());
        command.addAll(launchArgs);

        ProcessBuilder builder = new ProcessBuilder();
        builder.inheritIO();
        builder.command(command);

        try {
            Process process = builder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                Log.warning("jpackage retrun non zero code: " + exitCode);
            }
        } catch (IOException | InterruptedException ex) {
            Log.error(ex.getMessage());
        }
    }

    private void addArguments(List<String> launchArgs) {
        List<String> arguments = jnlpd.getArguments();
        if (arguments.isEmpty()) {
            return;
        }

        String argsStr = "";
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            argsStr += quote(arg);
            if ((i + 1) != arguments.size()) {
                argsStr += " ";
            }
        }

        launchArgs.add("--arguments");
        if (Platform.isWindows()) {
            if (argsStr.contains(" ")) {
                if (argsStr.contains("\"")) {
                    argsStr = escapeQuote(argsStr);
                }
                argsStr = "\"" + argsStr + "\"";
            }
        }
        launchArgs.add(argsStr);
    }

    private void addJVMArgs(List<String> launchArgs) {
        List<String> jvmArgs = jnlpd.getVMArgs();
        if (jvmArgs.isEmpty()) {
            return;
        }

        String jvmArgsStr = "";
        for (int i = 0; i < jvmArgs.size(); i++) {
            String arg = jvmArgs.get(i);
            jvmArgsStr += quote(arg);
            if ((i + 1) != jvmArgs.size()) {
                jvmArgsStr += " ";
            }
        }

        launchArgs.add("--java-options");
        if (Platform.isWindows()) {
            if (jvmArgsStr.contains(" ")) {
                if (jvmArgsStr.contains("\"")) {
                    jvmArgsStr = escapeQuote(jvmArgsStr);
                }
                jvmArgsStr = "\"" + jvmArgsStr + "\"";
            }
        }
        launchArgs.add(jvmArgsStr);
    }

    private String quote(String in) {
        if (in == null) {
            return null;
        }

        if (in.isEmpty()) {
            return "";
        }

        if (!in.contains("=")) {
            // Not a property
            if (in.contains(" ")) {
                in = escapeQuote(in);
                return "\"" + in + "\"";
            }
            return in;
        }

        if (!in.contains(" ")) {
            return in; // No need to quote
        }

        int paramIndex = in.indexOf("=");
        if (paramIndex <= 0) {
            return in; // Something wrong, just skip quoting
        }

        String param = in.substring(0, paramIndex);
        String value = in.substring(paramIndex + 1);

        if (value.length() == 0) {
            return in; // No need to quote
        }

        value = escapeQuote(value);

        return param + "=" + "\"" + value + "\"";
    }

    private String escapeQuote(String in) {
        if (in == null) {
            return null;
        }

        if (in.isEmpty()) {
            return "";
        }

        if (in.contains("\"")) {
            // Use code points to preserve non-ASCII chars
            StringBuilder sb = new StringBuilder();
            int codeLen = in.codePointCount(0, in.length());
            for (int i = 0; i < codeLen; i++) {
                int code = in.codePointAt(i);
                // Note: No need to escape '\' on Linux or OS X.
                // jpackage expects us to pass arguments and properties with quotes and spaces as a map
                // with quotes being escaped with additional \ for internal quotes.
                // So if we want two properties below:
                // -Djnlp.Prop1=Some "Value" 1
                // -Djnlp.Prop2=Some Value 2
                // jpackage will need:
                // "-Djnlp.Prop1=\"Some \\"Value\\" 1\" -Djnlp.Prop2=\"Some Value 2\""
                // but since we using ProcessBuilder to run jpackage we will need to escape
                // our escape symbols as well, so we will need to pass string below to ProcessBuilder:
                // "-Djnlp.Prop1=\\\"Some \\\\\\\"Value\\\\\\\" 1\\\" -Djnlp.Prop2=\\\"Some Value 2\\\""
                switch (code) {
                    case '"':
                        // " -> \" -> \\\"
                        if (i == 0 || in.codePointAt(i - 1) != '\\') {
                                sb.appendCodePoint('\\');
                            sb.appendCodePoint(code);
                        }
                        break;
                    case '\\':
                        // We need to escape already escaped symbols as well
                        if ((i + 1) < codeLen) {
                            int nextCode = in.codePointAt(i + 1);
                            if (nextCode == '"') {
                                // \" -> \\\"
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint(nextCode);
                            } else {
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint(code);
                            }
                        } else {
                            sb.appendCodePoint(code);
                        }
                        break;
                    default:
                        sb.appendCodePoint(code);
                        break;
                }
            }
            return sb.toString();
        }

        return in;
    }

    public synchronized String getDownloadFolder() {
        if (downloadFolder == null) {
            try {
                File file;
                if (options.keep() == null) {
                    Path path = Files.createTempDirectory("JNLPConverter");
                    file = path.toFile();
                    file.deleteOnExit();
                } else {
                    file = new File(options.keep());
                    if (!file.exists()) {
                        file.mkdir();
                    }
                }

                downloadFolder = file.getAbsolutePath();
            } catch (IOException e) {
                Log.error(e.getMessage());
            }
        }

        return downloadFolder;
    }

    public final synchronized String getJnlpDownloadFolder() {
        if (jnlpDownloadFolder == null) {
            File file = new File(getDownloadFolder() + File.separator + "jnlp");
            file.mkdir();
            markFileToDelete(getDownloadFolder() + File.separator + "jnlp");
            jnlpDownloadFolder = file.getAbsolutePath();
        }

        return jnlpDownloadFolder;
    }

    public static String getJnlpDownloadFolderStatic() {
        return jnlpDownloadFolderStatic;
    }

    public synchronized String getJarDownloadFolder() {
        if (jarDownloadFolder == null) {
            File file = new File(getDownloadFolder() + File.separator + "jar");
            file.mkdir();
            markFileToDelete(getDownloadFolder() + File.separator + "jar");
            jarDownloadFolder = file.getAbsolutePath();
        }

        return jarDownloadFolder;
    }

    public synchronized String getIconDownloadFolder() {
        if (iconDownloadFolder == null) {
            File file = new File(getDownloadFolder() + File.separator + "icon");
            file.mkdir();
            markFileToDelete(getDownloadFolder() + File.separator + "icon");
            iconDownloadFolder = file.getAbsolutePath();
        }

        return iconDownloadFolder;
    }

    public synchronized String getPropDownloadFolder() {
        if (propDownloadFolder == null) {
            File file = new File(getDownloadFolder() + File.separator + "prop");
            file.mkdir();
            markFileToDelete(getDownloadFolder() + File.separator + "prop");
            propDownloadFolder = file.getAbsolutePath();
        }

        return propDownloadFolder;
    }

    public synchronized static String getJPackagePath() {
        if (jpackagePath == null) {
            jpackagePath = System.getProperty("java.home");
            jpackagePath += File.separator;
            jpackagePath += "bin";
            jpackagePath += File.separator;

            Platform platform = Platform.getPlatform();
            switch (platform) {
                case WINDOWS:
                    jpackagePath += "jpackage.exe";
                    break;
                case LINUX:
                    jpackagePath += "jpackage";
                    break;
                case MAC:
                    jpackagePath += "jpackage";
                    break;
                default:
                    Log.error("Cannot determine platform type.");
                    break;
            }

            Log.verbose("jpackage: " + jpackagePath);
        }

        return jpackagePath;
    }

    public static String getIconFormat(String icon) {
        // GIF, JPEG, ICO, or PNG
        if (icon.toLowerCase().endsWith(".gif")) {
            return "GIF";
        } else if (icon.toLowerCase().endsWith(".jpg")) {
            return "JPEG";
        } else if (icon.toLowerCase().endsWith(".ico")) {
            return "ICO";
        } else if (icon.toLowerCase().endsWith(".png")) {
            return "PNG";
        }

        return "UNKNOWN";
    }

    public static boolean isIconSupported(String icon) {
        Platform platform = Platform.getPlatform();
        switch (platform) {
            case WINDOWS:
                if (icon.endsWith(".ico")) {
                    return true;
                } else {
                    Log.warning("Icon file format (" + getIconFormat(icon) + ") is not supported on Windows for file " + icon + ".");
                    return false;
                }
            case LINUX:
                if (icon.endsWith(".png")) {
                    return true;
                } else {
                    Log.warning("Icon file format (" + getIconFormat(icon) + ") is not supported on Linux for file " + icon + ".");
                    return false;
                }
            case MAC:
                Log.warning("Icon file format (" + getIconFormat(icon) + ") is not supported on OS X for file " + icon + ".");
                return false;
        }

        return false;
    }
}
