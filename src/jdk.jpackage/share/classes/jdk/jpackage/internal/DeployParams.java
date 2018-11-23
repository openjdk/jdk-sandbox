/*
 * Copyright (c) 2011, 2018, Oracle and/or its affiliates. All rights reserved.
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

import jdk.jpackage.internal.BundlerType;
import jdk.jpackage.internal.BundleParams;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * DeployParams
 *
 * This class is generated and used in Arguments.processArguments() as
 * intermediate step in generating the BundleParams and ultimately the Bundles
 */
public class DeployParams {

    final List<RelativeFileSet> resources = new ArrayList<>();

    String id;
    String title;
    String vendor;
    String email;
    String description;
    String category;
    String licenseType;
    String copyright;
    String version;
    Boolean systemWide;
    Boolean serviceHint;
    Boolean signBundle;
    Boolean installdirChooser;
    Boolean singleton;

    String applicationClass;

    List<Param> params;
    List<String> arguments; //unnamed arguments

    // Java 9 modules support
    String addModules = null;
    String limitModules = null;
    Boolean stripNativeCommands = null;
    String modulePath = null;
    String module = null;
    String debugPort = null;

    boolean jreInstaller = false;

    File outdir = null;

    String appId = null;

    // list of jvm args
    // (in theory string can contain spaces and need to be escaped
    List<String> jvmargs = new LinkedList<>();

    // list of jvm properties (can also be passed as VM args
    // but keeping them separate make it a bit more convinient
    Map<String, String> properties = new LinkedHashMap<>();

    // raw arguments to the bundler
    Map<String, ? super Object> bundlerArguments = new LinkedHashMap<>();

    public void setId(String id) {
        this.id = id;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    public void setCopyright(String copyright) {
        this.copyright = copyright;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public void setSystemWide(Boolean systemWide) {
        this.systemWide = systemWide;
    }

    public void setServiceHint(Boolean serviceHint) {
        this.serviceHint = serviceHint;
    }

    public void setInstalldirChooser(Boolean installdirChooser) {
        this.installdirChooser = installdirChooser;
    }

    public void setSingleton(Boolean singleton) {
        this.singleton = singleton;
    }

    public void setSignBundle(Boolean signBundle) {
        this.signBundle = signBundle;
    }

    public void addJvmArg(String v) {
        jvmargs.add(v);
    }

    public void addJvmProperty(String n, String v) {
        properties.put(n, v);
    }

    public void setArguments(List<String> args) {
        this.arguments = args;
    }

    public List<String> getArguments() {
        return this.arguments;
    }

    public void addArgument(String arg) {
        this.arguments.add(arg);
    }

    public void addAddModule(String value) {
        if (addModules == null) {
            addModules = value;
        }
        else {
            addModules += "," + value;
        }
    }

    public void addLimitModule(String value) {
        if (limitModules == null) {
            limitModules = value;
        }
        else {
            limitModules += "," + value;
        }
    }

    public String getModulePath() {
        return this.modulePath;
    }

    public void setModulePath(String value) {
        this.modulePath = value;
    }

    public void setModule(String value) {
        this.module = value;
    }

    public void setDebug(String value) {
        this.debugPort = value;
    }

    public void setStripNativeCommands(boolean value) {
        this.stripNativeCommands = value;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setAppId(String id) {
        appId = id;
    }

    public void setParams(List<Param> params) {
        this.params = params;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setApplicationClass(String applicationClass) {
        this.applicationClass = applicationClass;
    }

    public void setJreInstaller(boolean value) {
        jreInstaller = value;
    }

    public File getOutput() {
        return outdir;
    }

    public void setOutput(File output) {
        outdir = output;
    }

    static class Template {
        File in;
        File out;

        Template(File in, File out) {
            this.in = in;
            this.out = out;
        }
    }

    // we need to expand as in some cases
    // (most notably jpackage)
    // we may get "." as filename and assumption is we include
    // everything in the given folder
    // (IOUtils.copyfiles() have recursive behavior)
    List<File> expandFileset(File root) {
        List<File> files = new LinkedList<>();
        if (!Files.isSymbolicLink(root.toPath())) {
            if (root.isDirectory()) {
                File[] children = root.listFiles();
                if (children != null) {
                    for (File f : children) {
                        files.addAll(expandFileset(f));
                    }
                }
            } else {
                files.add(root);
            }
        }
        return files;
    }

    public void addResource(File baseDir, String path) {
        File file = new File(baseDir, path);
        // normalize top level dir
        // to strip things like "." in the path
        // or it can confuse symlink detection logic
        file = file.getAbsoluteFile();

        if (baseDir == null) {
            baseDir = file.getParentFile();
        }
        resources.add(new RelativeFileSet(
                baseDir, new LinkedHashSet<>(expandFileset(file))));
    }

    public void addResource(File baseDir, File file) {
        // normalize initial file
        // to strip things like "." in the path
        // or it can confuse symlink detection logic
        file = file.getAbsoluteFile();

        if (baseDir == null) {
            baseDir = file.getParentFile();
        }
        resources.add(new RelativeFileSet(
                baseDir, new LinkedHashSet<>(expandFileset(file))));
    }

    private static File createFile(final File baseDir, final String path) {
        final File testFile = new File(path);
        return testFile.isAbsolute() ?
                testFile : new File(baseDir == null ?
                        null : baseDir.getAbsolutePath(), path);
    }

    public static void validateAppName(String s) throws PackagerException {
        if (s == null || s.length() == 0) {
            // empty or null string - there is no unsupported char
            return;
        }

        int last = s.length() - 1;

        char fc = s.charAt(0);
        char lc = s.charAt(last);

        // illegal to end in backslash escape char
        if (lc == '\\') {
            throw new PackagerException("ERR_InvalidCharacterInArgument", "--name");
        }

        for (int i = 0; i < s.length(); i++) {
            char a = s.charAt(i);
            // We check for ASCII codes first which we accept. If check fails,
            // then check if it is acceptable extended ASCII or unicode character.
            if (a < ' ' || a > '~' || a == '%') {
                // Reject '%', whitespaces and ISO Control.
                // Accept anything else including special characters like copyright
                // symbols. Note: space will be included by ASCII check above,
                // but other whitespace like tabs or new line will be ignored.
                if (Character.isISOControl(a) || Character.isWhitespace(a) || a == '%') {
                    throw new PackagerException("ERR_InvalidCharacterInArgument", "--name");
                }
            }
            if (a == '"') {
                throw new PackagerException("ERR_InvalidCharacterInArgument", "--name");
            }
        }
    }

    public void validate() throws PackagerException {
        if (outdir == null) {
            throw new PackagerException("ERR_MissingArgument", "--output");
        }

        boolean hasModule = (bundlerArguments.get(
                Arguments.CLIOptions.MODULE.getId()) != null);
        boolean hasImage = (bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_APP_IMAGE.getId()) != null);
        boolean hasClass = (bundlerArguments.get(
                Arguments.CLIOptions.APPCLASS.getId()) != null);
        boolean hasMain = (bundlerArguments.get(
                Arguments.CLIOptions.MAIN_JAR.getId()) != null);
        boolean hasRuntimeImage = (bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_RUNTIME_IMAGE.getId()) != null);
        boolean hasInput = (bundlerArguments.get(
                Arguments.CLIOptions.INPUT.getId()) != null);
        boolean hasModulePath = (bundlerArguments.get(
                Arguments.CLIOptions.MODULE_PATH.getId()) != null);
        boolean hasAppImage = (bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_APP_IMAGE.getId()) != null);

        if (getBundleType() == BundlerType.IMAGE) {
            // Module application requires --runtime-image or --module-path
            if (hasModule) {
                if (!hasModulePath && !hasRuntimeImage) {
                    throw new PackagerException("ERR_MissingArgument",
                            "--runtime-image or --module-path");
                }
            } else {
                if (!hasInput) {
                    throw new PackagerException("ERR_MissingArgument", "--input");
                }
            }
        } else if (getBundleType() == BundlerType.INSTALLER) {
            if (!Arguments.isJreInstaller()) {
                if (hasModule) {
                    if (!hasModulePath && !hasRuntimeImage && !hasAppImage) {
                        throw new PackagerException("ERR_MissingArgument",
                            "--runtime-image, --module-path or --app-image");
                    }
                } else {
                    if (!hasInput && !hasAppImage) {
                        throw new PackagerException("ERR_MissingArgument", "--input or --app-image");
                    }
                }
            }
        }

        // if bundling non-modular image, or installer without app-image
        // then we need some resources and a main class
        if (!hasModule && !hasImage && !jreInstaller) {
            if (resources.isEmpty()) {
                throw new PackagerException("ERR_MissingAppResources");
            }
            if (!hasClass) {
                throw new PackagerException("ERR_MissingArgument", "--class");
            }
            if (!hasMain) {
                throw new PackagerException("ERR_MissingArgument",
                        "--main-jar");
            }
        }

        String name = (String)bundlerArguments.get(Arguments.CLIOptions.NAME.getId());
        validateAppName(name);

        // Validate app image if set
        String appImage = (String)bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_APP_IMAGE.getId());
        if (appImage != null) {
            File appImageDir = new File(appImage);
            if (!appImageDir.exists()) {
                throw new PackagerException("ERR_AppImageNotExist", appImage);
            }

            File appImageAppDir = new File(appImage + File.separator + "app");
            File appImageRuntimeDir = new File(appImage
                    + File.separator + "runtime");
            if (!appImageAppDir.exists() || !appImageRuntimeDir.exists()) {
                throw new PackagerException("ERR_AppImageInvalid", appImage);
            }
        }
    }

    public boolean validateForBundle() {
        boolean result = false;

        // Success
        if (((applicationClass != null && !applicationClass.isEmpty()) ||
            (module != null && !module.isEmpty()))) {
            result = true;
        }

        return result;
    }

    BundlerType bundleType = BundlerType.NONE;
    String targetFormat = null; //means any

    public void setBundleType(BundlerType type) {
        bundleType = type;
    }

    public BundlerType getBundleType() {
        return bundleType;
    }

    public void setTargetFormat(String t) {
        targetFormat = t;
    }

    public String getTargetFormat() {
        return targetFormat;
    }

    private String getArch() {
        String arch = System.getProperty("os.arch").toLowerCase();

        if ("x86".equals(arch) || "i386".equals(arch) || "i486".equals(arch)
                || "i586".equals(arch) || "i686".equals(arch)) {
            arch = "x86";
        } else if ("x86_64".equals(arch) || "amd64".equals("arch")) {
            arch = "x86_64";
        }

        return arch;
    }

    static final Set<String> multi_args = new TreeSet<>(Arrays.asList(
            StandardBundlerParam.JVM_PROPERTIES.getID(),
            StandardBundlerParam.JVM_OPTIONS.getID(),
            StandardBundlerParam.ARGUMENTS.getID(),
            StandardBundlerParam.MODULE_PATH.getID(),
            StandardBundlerParam.ADD_MODULES.getID(),
            StandardBundlerParam.LIMIT_MODULES.getID(),
            StandardBundlerParam.FILE_ASSOCIATIONS.getID()
    ));

    @SuppressWarnings("unchecked")
    public void addBundleArgument(String key, Object value) {
        // special hack for multi-line arguments
        if (multi_args.contains(key)) {
            Object existingValue = bundlerArguments.get(key);
            if (existingValue instanceof String && value instanceof String) {
                bundlerArguments.put(key, existingValue + "\n\n" + value);
            } else if (existingValue instanceof List && value instanceof List) {
                ((List)existingValue).addAll((List)value);
            } else if (existingValue instanceof Map &&
                value instanceof String && ((String)value).contains("=")) {
                String[] mapValues = ((String)value).split("=", 2);
                ((Map)existingValue).put(mapValues[0], mapValues[1]);
            } else {
                bundlerArguments.put(key, value);
            }
        } else {
            bundlerArguments.put(key, value);
        }
    }

    public BundleParams getBundleParams() {
        BundleParams bundleParams = new BundleParams();

        //construct app resources
        //  relative to output folder!
        String currentOS = System.getProperty("os.name").toLowerCase();
        String currentArch = getArch();

        bundleParams.setAppResourcesList(resources);

        bundleParams.setIdentifier(id);

        bundleParams.setApplicationClass(applicationClass);
        bundleParams.setAppVersion(version);
        bundleParams.setType(bundleType);
        bundleParams.setBundleFormat(targetFormat);
        bundleParams.setVendor(vendor);
        bundleParams.setEmail(email);
        bundleParams.setServiceHint(serviceHint);
        bundleParams.setInstalldirChooser(installdirChooser);
        bundleParams.setSingleton(singleton);
        bundleParams.setCopyright(copyright);
        bundleParams.setApplicationCategory(category);
        bundleParams.setDescription(description);
        bundleParams.setTitle(title);

        bundleParams.setJvmProperties(properties);
        bundleParams.setJvmargs(jvmargs);
        bundleParams.setArguments(arguments);

        if (addModules != null && !addModules.isEmpty()) {
            bundleParams.setAddModules(addModules);
        }

        if (limitModules != null && !limitModules.isEmpty()) {
            bundleParams.setLimitModules(limitModules);
        }

        if (stripNativeCommands != null) {
            bundleParams.setStripNativeCommands(stripNativeCommands);
        }

        if (modulePath != null && !modulePath.isEmpty()) {
            bundleParams.setModulePath(modulePath);
        }

        if (module != null && !module.isEmpty()) {
            bundleParams.setMainModule(module);
        }

        if (debugPort != null && !debugPort.isEmpty()) {
            bundleParams.setDebug(debugPort);
        }

        Map<String, String> paramsMap = new TreeMap<>();
        if (params != null) {
            for (Param p : params) {
                paramsMap.put(p.name, p.value);
            }
        }

        Map<String, String> unescapedHtmlParams = new TreeMap<>();
        Map<String, String> escapedHtmlParams = new TreeMap<>();

        // check for collisions
        TreeSet<String> keys = new TreeSet<>(bundlerArguments.keySet());
        keys.retainAll(bundleParams.getBundleParamsAsMap().keySet());

        if (!keys.isEmpty()) {
            throw new RuntimeException("Deploy Params and Bundler Arguments "
                    + "overlap in the following values:" + keys.toString());
        }

        bundleParams.addAllBundleParams(bundlerArguments);

        return bundleParams;
    }

    public Map<String, ? super Object> getBundlerArguments() {
        return this.bundlerArguments;
    }

    public void putUnlessNull(String param, Object value) {
        if (value != null) {
            bundlerArguments.put(param, value);
        }
    }

    public void putUnlessNullOrEmpty(String param, Map<?, ?> value) {
        if (value != null && !value.isEmpty()) {
            bundlerArguments.put(param, value);
        }
    }

    public void putUnlessNullOrEmpty(String param, Collection<?> value) {
        if (value != null && !value.isEmpty()) {
            bundlerArguments.put(param, value);
        }
    }

    @Override
    public String toString() {
        return "DeployParams{" + "outdir=" + outdir + '}';
    }

}
