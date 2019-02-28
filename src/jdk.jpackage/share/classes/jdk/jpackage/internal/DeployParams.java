/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.text.MessageFormat;
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

    File outdir = null;

    String appId = null;

    // list of jvm args
    // (in theory string can contain spaces and need to be escaped
    List<String> jvmargs = new LinkedList<>();

    // raw arguments to the bundler
    Map<String, ? super Object> bundlerArguments = new LinkedHashMap<>();

    void setCategory(String category) {
        this.category = category;
    }

    void setLicenseType(String licenseType) {
        this.licenseType = licenseType;
    }

    void setCopyright(String copyright) {
        this.copyright = copyright;
    }

    void setVersion(String version) {
        this.version = version;
    }

    void setSystemWide(Boolean systemWide) {
        this.systemWide = systemWide;
    }

    void setInstalldirChooser(Boolean installdirChooser) {
        this.installdirChooser = installdirChooser;
    }

    void setSignBundle(Boolean signBundle) {
        this.signBundle = signBundle;
    }

    void addJvmArg(String v) {
        jvmargs.add(v);
    }

    void setArguments(List<String> args) {
        this.arguments = args;
    }

    List<String> getArguments() {
        return this.arguments;
    }

    void addArgument(String arg) {
        this.arguments.add(arg);
    }

    void addAddModule(String value) {
        if (addModules == null) {
            addModules = value;
        }
        else {
            addModules += "," + value;
        }
    }

    void addLimitModule(String value) {
        if (limitModules == null) {
            limitModules = value;
        }
        else {
            limitModules += "," + value;
        }
    }

    String getModulePath() {
        return this.modulePath;
    }

    void setModulePath(String value) {
        this.modulePath = value;
    }

    void setModule(String value) {
        this.module = value;
    }

    void setDebug(String value) {
        this.debugPort = value;
    }

    void setStripNativeCommands(boolean value) {
        this.stripNativeCommands = value;
    }

    void setDescription(String description) {
        this.description = description;
    }

    public void setAppId(String id) {
        appId = id;
    }

    void setParams(List<Param> params) {
        this.params = params;
    }

    void setTitle(String title) {
        this.title = title;
    }

    void setVendor(String vendor) {
        this.vendor = vendor;
    }

    void setEmail(String email) {
        this.email = email;
    }

    void setApplicationClass(String applicationClass) {
        this.applicationClass = applicationClass;
    }

    File getOutput() {
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

    void setClasspath() {
        String classpath = "";
        for (RelativeFileSet resource : resources) {
             for (String file : resource.getIncludedFiles()) {
                 if (file.endsWith(".jar")) {
                     classpath += file + File.pathSeparator;
                 }
             }
        }
        addBundleArgument(
                StandardBundlerParam.CLASSPATH.getID(), classpath);
    }

    private static File createFile(final File baseDir, final String path) {
        final File testFile = new File(path);
        return testFile.isAbsolute() ?
                testFile : new File(baseDir == null ?
                        null : baseDir.getAbsolutePath(), path);
    }

    static void validateName(String s, boolean forApp)
            throws PackagerException {
        
        String exceptionKey = forApp ?
            "ERR_InvalidAppName" : "ERR_InvalidSLName";
        
        if (s == null) {
            if (forApp) {
                return;
            } else {
                throw new PackagerException(exceptionKey, s);
            }
        }
        if (s.length() == 0 || s.charAt(s.length() - 1) == '\\') {
            throw new PackagerException(exceptionKey, s);
        }
        try {
            // name must be valid path element for this file system
            Path p = (new File(s)).toPath();
            // and it must be a single name element in a path
            if (p.getNameCount() != 1) {
                throw new PackagerException(exceptionKey, s);
            }
        } catch (InvalidPathException ipe) {
            throw new PackagerException(ipe, exceptionKey, s);
        }

        for (int i = 0; i < s.length(); i++) {
            char a = s.charAt(i);
            // We check for ASCII codes first which we accept. If check fails,
            // check if it is acceptable extended ASCII or unicode character.
            if (a < ' ' || a > '~') {
                // Accept anything else including special chars like copyright
                // symbols. Note: space will be included by ASCII check above,
                // but other whitespace like tabs or new line will be rejected.
                if (Character.isISOControl(a)  ||
                        Character.isWhitespace(a)) {
                    throw new PackagerException(exceptionKey, s);
                }
            } else if (a == '"' || a == '%') {
                throw new PackagerException(exceptionKey, s);
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
        boolean runtimeInstaller = (bundlerArguments.get(
                Arguments.CLIOptions.RUNTIME_INSTALLER.getId()) != null);

        if (getBundleType() == BundlerType.IMAGE) {
            // Module application requires --runtime-image or --module-path
            if (hasModule) {
                if (!hasModulePath && !hasRuntimeImage) {
                    throw new PackagerException("ERR_MissingArgument",
                            "--runtime-image or --module-path");
                }
            } else {
                if (!hasInput) {
                    throw new PackagerException(
                           "ERR_MissingArgument", "--input");
                }
            }
        } else if (getBundleType() == BundlerType.INSTALLER) {
            if (!runtimeInstaller) {
                if (hasModule) {
                    if (!hasModulePath && !hasRuntimeImage && !hasAppImage) {
                        throw new PackagerException("ERR_MissingArgument",
                            "--runtime-image, --module-path or --app-image");
                    }
                } else {
                    if (!hasInput && !hasAppImage) {
                        throw new PackagerException("ERR_MissingArgument",
                                "--input or --app-image");
                    }
                }
            }
        }

        // if bundling non-modular image, or installer without app-image
        // then we need some resources and a main class
        if (!hasModule && !hasImage && !runtimeInstaller) {
            if (resources.isEmpty()) {
                throw new PackagerException("ERR_MissingAppResources");
            }
            if (!hasClass) {
                throw new PackagerException("ERR_MissingArgument",
                        "--main-class");
            }
            if (!hasMain) {
                throw new PackagerException("ERR_MissingArgument",
                        "--main-jar");
            }
        }

        String name = (String)bundlerArguments.get(
                Arguments.CLIOptions.NAME.getId());
        validateName(name, true);

        // Validate app image if set
        String appImage = (String)bundlerArguments.get(
                Arguments.CLIOptions.PREDEFINED_APP_IMAGE.getId());
        if (appImage != null) {
            File appImageDir = new File(appImage);
            if (!appImageDir.exists() || appImageDir.list().length == 0) {
                throw new PackagerException("ERR_AppImageNotExist", appImage);
            }
        }

        // Validate build-root
        String root = (String)bundlerArguments.get(
                Arguments.CLIOptions.BUILD_ROOT.getId());
        if (root != null) {
            String [] contents = (new File(root)).list();

            if (contents != null && contents.length > 0) {
                throw new PackagerException("ERR_BuildRootInvalid", root);
            }
        }

        // Validate license file if set
        String license = (String)bundlerArguments.get(
                Arguments.CLIOptions.LICENSE_FILE.getId());
        if (license != null) {
            File licenseFile = new File(license);
            if (!licenseFile.exists()) {
                throw new PackagerException("ERR_LicenseFileNotExit");
            }
        }
    }

    boolean validateForBundle() {
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

    void setBundleType(BundlerType type) {
        bundleType = type;
    }

    BundlerType getBundleType() {
        return bundleType;
    }

    void setTargetFormat(String t) {
        targetFormat = t;
    }

    String getTargetFormat() {
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

    BundleParams getBundleParams() {
        BundleParams bundleParams = new BundleParams();

        // construct app resources relative to output folder!
        bundleParams.setAppResourcesList(resources);

        bundleParams.setIdentifier(id);

        bundleParams.setApplicationClass(applicationClass);
        bundleParams.setAppVersion(version);
        bundleParams.setType(bundleType);
        bundleParams.setBundleFormat(targetFormat);
        bundleParams.setVendor(vendor);
        bundleParams.setEmail(email);
        bundleParams.setInstalldirChooser(installdirChooser);
        bundleParams.setCopyright(copyright);
        bundleParams.setApplicationCategory(category);
        bundleParams.setDescription(description);
        bundleParams.setTitle(title);

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

    Map<String, ? super Object> getBundlerArguments() {
        return this.bundlerArguments;
    }

    void putUnlessNull(String param, Object value) {
        if (value != null) {
            bundlerArguments.put(param, value);
        }
    }

    void putUnlessNullOrEmpty(String param, Map<?, ?> value) {
        if (value != null && !value.isEmpty()) {
            bundlerArguments.put(param, value);
        }
    }

    void putUnlessNullOrEmpty(String param, Collection<?> value) {
        if (value != null && !value.isEmpty()) {
            bundlerArguments.put(param, value);
        }
    }

    @Override
    public String toString() {
        return "DeployParams {" + "output: " + outdir
                + " resources: {" + resources + "}}";
    }

}
