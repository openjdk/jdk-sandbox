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

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import static jdk.jpackage.internal.StandardBundlerParam.*;

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

    // String - vendor <email>, only used for debian */
    public static final String PARAM_MAINTAINER         = "maintainer";

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

    // String - Optional application description. Used by MSI and on Linux
    public static final String PARAM_DESCRIPTION        = "description";

    // String - License type. Needed on Linux (rpm)
    public static final String PARAM_LICENSE_TYPE       = "licenseType";

    // String - File with license. Format is OS/bundler specific
    public static final String PARAM_LICENSE_FILE       = "licenseFile";

    // String Main application class.
    // Not used directly but used to derive default values
    public static final String PARAM_APPLICATION_CLASS  = "applicationClass";

    // boolean - Adds a dialog to let the user choose a directory
    // where the product will be installed.
    public static final String PARAM_INSTALLDIR_CHOOSER = "installdirChooser";

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
        putUnlessNullOrEmpty(JAVA_OPTIONS.getID(), jvmargs);
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

    public void setInstalldirChooser(Boolean b) {
        putUnlessNull(PARAM_INSTALLDIR_CHOOSER, b);
    }

    public String getName() {
        return fetchParam(APP_NAME);
    }

    public void setName(String name) {
        putUnlessNull(PARAM_NAME, name);
    }

    @SuppressWarnings("deprecation")
    public BundlerType getType() {
        return fetchParam(BundlerType.class, PARAM_TYPE);
    }

    @SuppressWarnings("deprecation")
    public void setType(BundlerType type) {
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

    public List<String> getJvmargs() {
        return JAVA_OPTIONS.fetchFrom(params);
    }

    public List<String> getArguments() {
        return ARGUMENTS.fetchFrom(params);
    }

    public jdk.jpackage.internal.RelativeFileSet getAppResource() {
        return fetchParam(APP_RESOURCES);
    }

    public void setAppResource(jdk.jpackage.internal.RelativeFileSet fs) {
        putUnlessNull(PARAM_APP_RESOURCES, fs);
    }

    public void setAppResourcesList(
            List<jdk.jpackage.internal.RelativeFileSet> rfs) {
        putUnlessNull(APP_RESOURCES_LIST.getID(), rfs);
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

    private String mainJar = null;

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
        jdk.jpackage.internal.RelativeFileSet appResources = getAppResource();
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

    public void putUnlessNullOrEmpty(String param, Collection<?> value) {
        if (value != null && !value.isEmpty()) {
            params.put(param, value);
        }
    }

    public void putUnlessNullOrEmpty(String param, Map<?,?> value) {
        if (value != null && !value.isEmpty()) {
            params.put(param, value);
        }
    }

}
