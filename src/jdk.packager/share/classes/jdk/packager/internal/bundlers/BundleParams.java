/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.packager.internal.bundlers;

import jdk.packager.internal.*;
import jdk.packager.internal.bundlers.Bundler.BundleType;
import jdk.packager.internal.JLinkBundlerHelper;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static jdk.packager.internal.StandardBundlerParam.*;

public class BundleParams {

    final protected Map<String, ? super Object> params;

    // RelativeFileSet
    public static final String PARAM_APP_RESOURCES      = "appResources";

    // BundlerType
    public static final String PARAM_TYPE               = "type";

    // String
    public static final String PARAM_BUNDLE_FORMAT      = "bundleFormat";
    // String
    public static final String PARAM_ICON               = "icon";

    // String - Name of bundle file and native launcher
    public static final String PARAM_NAME               = "name";

    // String - application vendor, used by most of the bundlers
    public static final String PARAM_VENDOR             = "vendor";

    // String - email name and email, only used for debian */
    public static final String PARAM_EMAIL              = "email";

    /* String - Copyright. Used on Mac */
    public static final String PARAM_COPYRIGHT          = "copyright";

    // String - GUID on windows for MSI, CFBundleIdentifier on Mac
    // If not compatible with requirements then bundler either do not bundle
    // or autogenerate
    public static final String PARAM_IDENTIFIER         = "identifier";

    /* boolean - shortcut preferences */
    public static final String PARAM_SHORTCUT           = "shortcutHint";
    // boolean - menu shortcut preference
    public static final String PARAM_MENU               = "menuHint";

    // String - Application version. Format may differ for different bundlers
    public static final String PARAM_VERSION            = "appVersion";

    // String - Application category. Used at least on Mac/Linux.
    // Value is platform specific
    public static final String PARAM_CATEGORY       = "applicationCategory";

    // String - Optional short application
    public static final String PARAM_TITLE              = "title";

    // String - Optional application description. Used by MSI and on Linux
    public static final String PARAM_DESCRIPTION        = "description";

    // String - License type. Needed on Linux (rpm)
    public static final String PARAM_LICENSE_TYPE       = "licenseType";

    // List<String> - File(s) with license. Format is OS/bundler specific
    public static final String PARAM_LICENSE_FILE       = "licenseFile";

    // boolean - service/daemon install.  null means "default"
    public static final String PARAM_SERVICE_HINT       = "serviceHint";


    // String Main application class.
    // Not used directly but used to derive default values
    public static final String PARAM_APPLICATION_CLASS  = "applicationClass";

    // boolean - Adds a dialog to let the user choose a directory
    // where the product will be installed.
    public static final String PARAM_INSTALLDIR_CHOOSER = "installdirChooser";

    // boolean - Prevents from launching multiple instances of application. 
    public static final String PARAM_SINGLETON          = "singleton";

    /**
     * create a new bundle with all default values
     */
    public BundleParams() {
        params = new HashMap<>();
    }

    /**
     * Create a bundle params with a copy of the params
     * @param params map of initial parameters to be copied in.
     */
    public BundleParams(Map<String, ?> params) {
        this.params = new HashMap<>(params);
    }

    public void addAllBundleParams(Map<String, ? super Object> p) {
        params.putAll(p);
    }

    public <C> C fetchParam(BundlerParamInfo<C> paramInfo) {
        return paramInfo.fetchFrom(params);
    }

    @SuppressWarnings("unchecked")
    public <C> C fetchParamWithDefault(
            Class<C> klass, C defaultValue, String... keys) {
        for (String key : keys) {
            Object o = params.get(key);
            if (klass.isInstance(o)) {
                return (C) o;
            } else if (params.containsKey(key) && o == null) {
                return null;
            } else if (o != null) {
                Log.debug("Bundle param " + key + " is not type " + klass);
            }
        }
        return defaultValue;
    }

    public <C> C fetchParam(Class<C> klass, String... keys) {
        return fetchParamWithDefault(klass, null, keys);
    }

    // NOTE: we do not care about application parameters here
    // as they will be embeded into jar file manifest and
    // java launcher will take care of them!

    public Map<String, ? super Object> getBundleParamsAsMap() {
        return new HashMap<>(params);
    }

    public void setJvmargs(List<String> jvmargs) {
        putUnlessNullOrEmpty(JVM_OPTIONS.getID(), jvmargs);
    }

    public void setJvmProperties(Map<String, String> jvmProperties) {
        putUnlessNullOrEmpty(JVM_PROPERTIES.getID(), jvmProperties);
    }

    public void setArguments(List<String> arguments) {
        putUnlessNullOrEmpty(ARGUMENTS.getID(), arguments);
    }

    public void setAddModules(String value) {
        putUnlessNull(StandardBundlerParam.ADD_MODULES.getID(), value);
    }

    public void setLimitModules(String value)  {
        putUnlessNull(StandardBundlerParam.LIMIT_MODULES.getID(), value);
    }

    public void setStripNativeCommands(boolean value) {
        putUnlessNull(StandardBundlerParam.STRIP_NATIVE_COMMANDS.getID(),
                value);
    }

    public void setModulePath(String value) {
        putUnlessNull(StandardBundlerParam.MODULE_PATH.getID(), value);
    }

    public void setMainModule(String value) {
        putUnlessNull(StandardBundlerParam.MODULE.getID(), value);
    }

    public void setDebug(String value) {
        putUnlessNull(JLinkBundlerHelper.DEBUG.getID(), value);
    }

    public String getApplicationID() {
        return fetchParam(IDENTIFIER);
    }

    public String getPreferencesID() {
        return fetchParam(PREFERENCES_ID);
    }

    public String getTitle() {
        return fetchParam(TITLE);
    }

    public void setTitle(String title) {
        putUnlessNull(PARAM_TITLE, title);
    }

    public String getApplicationClass() {
        return fetchParam(MAIN_CLASS);
    }

    public void setApplicationClass(String applicationClass) {
        putUnlessNull(PARAM_APPLICATION_CLASS, applicationClass);
    }

    public String getAppVersion() {
        return fetchParam(VERSION);
    }

    public void setAppVersion(String version) {
        putUnlessNull(PARAM_VERSION, version);
    }

    public String getDescription() {
        return fetchParam(DESCRIPTION);
    }

    public void setDescription(String s) {
        putUnlessNull(PARAM_DESCRIPTION, s);
    }

    //path is relative to the application root
    public void addLicenseFile(String path) {
        List<String> licenseFiles = fetchParam(LICENSE_FILE);
        if (licenseFiles == null || licenseFiles.isEmpty()) {
            licenseFiles = new ArrayList<>();
            params.put(PARAM_LICENSE_FILE, licenseFiles);
        }
        licenseFiles.add(path);
    }

    public void setServiceHint(Boolean b) {
        putUnlessNull(PARAM_SERVICE_HINT, b);
    }

    public void setInstalldirChooser(Boolean b) {
        putUnlessNull(PARAM_INSTALLDIR_CHOOSER, b);
    }

    public void setSingleton(Boolean b) {
        putUnlessNull(PARAM_SINGLETON, b);
    }

    public String getName() {
        return fetchParam(APP_NAME);
    }

    public void setName(String name) {
        putUnlessNull(PARAM_NAME, name);
    }

    @SuppressWarnings("deprecation")
    public BundleType getType() {
        return fetchParam(BundleType.class, PARAM_TYPE);
    }

    @SuppressWarnings("deprecation")
    public void setType(BundleType type) {
        putUnlessNull(PARAM_TYPE, type);
    }

    public String getBundleFormat() {
        return fetchParam(String.class, PARAM_BUNDLE_FORMAT);
    }

    public void setBundleFormat(String t) {
        putUnlessNull(PARAM_BUNDLE_FORMAT, t);
    }

    public boolean getVerbose() {
        return fetchParam(VERBOSE);
    }

    public void setVerbose(Boolean verbose) {
        putUnlessNull(VERBOSE.getID(), verbose);
    }

    public List<String> getLicenseFile() {
        return fetchParam(LICENSE_FILE);
    }

    public List<String> getJvmargs() {
        return JVM_OPTIONS.fetchFrom(params);
    }

    public List<String> getArguments() {
        return ARGUMENTS.fetchFrom(params);
    }

    // Validation approach:
    //  - javac and
    //
    //  - /jmods dir
    // or
    //  - JRE marker (rt.jar)
    //  - FX marker (jfxrt.jar)
    //  - JDK marker (tools.jar)
    private static boolean checkJDKRoot(File jdkRoot) {
        String exe = (Platform.getPlatform() == Platform.WINDOWS) ?
                ".exe" : "";
        File javac = new File(jdkRoot, "bin/javac" + exe);
        if (!javac.exists()) {
            Log.verbose("javac is not found at " + javac.getAbsolutePath());
            return false;
        }

        File jmods = new File(jdkRoot, "jmods");
        if (!jmods.exists()) {
            Log.verbose("jmods is not found in " + jdkRoot.getAbsolutePath());
            return false;
        }
        return true;
    }

    public jdk.packager.internal.RelativeFileSet getAppResource() {
        return fetchParam(APP_RESOURCES);
    }

    public void setAppResource(jdk.packager.internal.RelativeFileSet fs) {
        putUnlessNull(PARAM_APP_RESOURCES, fs);
    }

    public void setAppResourcesList(
            List<jdk.packager.internal.RelativeFileSet> rfs) {
        putUnlessNull(APP_RESOURCES_LIST.getID(), rfs);
    }

    public String getApplicationCategory() {
        return fetchParam(CATEGORY);
    }

    public void setApplicationCategory(String category) {
        putUnlessNull(PARAM_CATEGORY, category);
    }

    public String getMainClassName() {
        String applicationClass = getApplicationClass();

        if (applicationClass == null) {
            return null;
        }

        int idx = applicationClass.lastIndexOf(".");
        if (idx >= 0) {
            return applicationClass.substring(idx+1);
        }
        return applicationClass;
    }

    public String getCopyright() {
        return fetchParam(COPYRIGHT);
    }

    public void setCopyright(String c) {
        putUnlessNull(PARAM_COPYRIGHT, c);
    }

    public String getIdentifier() {
        return fetchParam(IDENTIFIER);
    }

    public void setIdentifier(String s) {
        putUnlessNull(PARAM_IDENTIFIER, s);
    }

    private String mainJar = null;
    private String mainJarClassPath = null;
    private boolean useFXPackaging = true;

    // For regular executable Jars we need to take care of classpath
    // For JavaFX executable jars we do not need to pay attention to
    // ClassPath entry in manifest
    public String getAppClassPath() {
        if (mainJar == null) {
            // this will find out answer
            getMainApplicationJar();
        }
        if (useFXPackaging || mainJarClassPath == null) {
            return "";
        }
        return mainJarClassPath;
    }

    // assuming that application was packaged according to the rules
    // we must have application jar, i.e. jar where we embed launcher
    // and have main application class listed as main class!
    // If there are more than one, or none - it will be treated as
    // deployment error
    //
    // Note we look for both JavaFX executable jars and regular executable jars
    // As long as main "application" entry point is the same it is main class
    // (i.e. for FX jar we will use JavaFX manifest entry ...)
    public String getMainApplicationJar() {
        jdk.packager.internal.RelativeFileSet appResources = getAppResource();
        if (mainJar != null) {
            if (getApplicationClass() == null) try {
                if (appResources != null) {
                    File srcdir = appResources.getBaseDirectory();
                    JarFile jf = new JarFile(new File(srcdir, mainJar));
                    Manifest m = jf.getManifest();
                    Attributes attrs = (m != null) ?
                            m.getMainAttributes() : null;
                    if (attrs != null) {
                        setApplicationClass(
                                attrs.getValue(Attributes.Name.MAIN_CLASS));
                    }
                }
            } catch (IOException ignore) {
            }
            return mainJar;
        }

        String applicationClass = getApplicationClass();

        if (appResources == null || applicationClass == null) {
            return null;
        }
        File srcdir = appResources.getBaseDirectory();
        for (String fname : appResources.getIncludedFiles()) {
            JarFile jf;
            try {
                jf = new JarFile(new File(srcdir, fname));
                Manifest m = jf.getManifest();
                Attributes attrs = (m != null) ? m.getMainAttributes() : null;
                if (attrs != null) {
                    boolean javaMain = applicationClass.equals(
                               attrs.getValue(Attributes.Name.MAIN_CLASS));

                    if (javaMain) {
                        mainJar = fname;
                        mainJarClassPath = attrs.getValue(
                               Attributes.Name.CLASS_PATH);
                        return mainJar;
                    }
                }
            } catch (IOException ignore) {
            }
        }
        return null;
    }

    public String getVendor() {
        return fetchParam(VENDOR);
    }

    public void setVendor(String vendor) {
       putUnlessNull(PARAM_VENDOR, vendor);
    }

    public String getEmail() {
        return fetchParam(String.class, PARAM_EMAIL);
    }

    public void setEmail(String email) {
        putUnlessNull(PARAM_EMAIL, email);
    }

    public void putUnlessNull(String param, Object value) {
        if (value != null) {
            params.put(param, value);
        }
    }

    public void putUnlessNullOrEmpty(String param, Collection value) {
        if (value != null && !value.isEmpty()) {
            params.put(param, value);
        }
    }

    public void putUnlessNullOrEmpty(String param, Map value) {
        if (value != null && !value.isEmpty()) {
            params.put(param, value);
        }
    }

}
