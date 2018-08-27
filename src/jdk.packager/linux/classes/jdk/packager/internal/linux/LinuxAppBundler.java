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

package jdk.packager.internal.linux;

import jdk.packager.internal.AbstractImageBundler;
import jdk.packager.internal.BundlerParamInfo;
import jdk.packager.internal.ConfigException;
import jdk.packager.internal.IOUtils;
import jdk.packager.internal.JreUtils;
import jdk.packager.internal.JreUtils.Rule;
import jdk.packager.internal.Log;
import jdk.packager.internal.Platform;
import jdk.packager.internal.RelativeFileSet;
import jdk.packager.internal.StandardBundlerParam;
import jdk.packager.internal.Arguments;
import jdk.packager.internal.UnsupportedPlatformException;
import jdk.packager.internal.bundlers.BundleParams;
import jdk.packager.internal.builders.linux.LinuxAppImageBuilder;
import jdk.packager.internal.resources.linux.LinuxResources;

import jdk.packager.internal.JLinkBundlerHelper;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.ResourceBundle;

import static jdk.packager.internal.StandardBundlerParam.*;
import jdk.packager.internal.builders.AbstractAppImageBuilder;

public class LinuxAppBundler extends AbstractImageBundler {

    private static final ResourceBundle I18N =
            ResourceBundle.getBundle(
                    "jdk.packager.internal.resources.linux.LinuxAppBundler");

    protected static final String LINUX_BUNDLER_PREFIX =
            BUNDLER_PREFIX + "linux" + File.separator;
    private static final String EXECUTABLE_NAME = "JavaAppLauncher";

    public static final BundlerParamInfo<File> ICON_PNG =
            new StandardBundlerParam<>(
            I18N.getString("param.icon-png.name"),
            I18N.getString("param.icon-png.description"),
            "icon.png",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".png")) {
                    Log.info(MessageFormat.format(
                            I18N.getString("message.icon-not-png"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public static final BundlerParamInfo<URL> RAW_EXECUTABLE_URL =
            new StandardBundlerParam<>(
            I18N.getString("param.raw-executable-url.name"),
            I18N.getString("param.raw-executable-url.description"),
            "linux.launcher.url",
            URL.class,
            params -> LinuxResources.class.getResource(EXECUTABLE_NAME),
            (s, p) -> {
                try {
                    return new URL(s);
                } catch (MalformedURLException e) {
                    Log.info(e.toString());
                    return null;
                }
            });

    //Subsetting of JRE is restricted.
    //JRE README defines what is allowed to strip:
    //   http://www.oracle.com/technetwork/java/javase/jre-8-readme-2095710.html
    //
    public static final BundlerParamInfo<Rule[]> LINUX_JRE_RULES =
            new StandardBundlerParam<>(
            "",
            "",
            ".linux.runtime.rules",
            Rule[].class,
            params -> new Rule[]{
                    Rule.prefixNeg("/bin"),
                    Rule.prefixNeg("/plugin"),
                    //Rule.prefixNeg("/lib/ext"), 
                    //need some of jars there for https to work
                    Rule.suffix("deploy.jar"), //take deploy.jar
                    Rule.prefixNeg("/lib/deploy"),
                    Rule.prefixNeg("/lib/desktop"),
                    Rule.substrNeg("libnpjp2.so")
            },
            (s, p) ->  null
    );

    public static final BundlerParamInfo<RelativeFileSet> LINUX_RUNTIME =
            new StandardBundlerParam<>(
            I18N.getString("param.runtime.name"),
            I18N.getString("param.runtime.description"),
            BundleParams.PARAM_RUNTIME,
            RelativeFileSet.class,
            params -> JreUtils.extractJreAsRelativeFileSet(
                    System.getProperty("java.home"),
                    LINUX_JRE_RULES.fetchFrom(params)),
            (s, p) -> JreUtils.extractJreAsRelativeFileSet(s,
                    LINUX_JRE_RULES.fetchFrom(p))
    );

    public static final BundlerParamInfo<String> LINUX_INSTALL_DIR =
            new StandardBundlerParam<>(
            I18N.getString("param.linux-install-dir.name"),
            I18N.getString("param.linux-install-dir.description"),
            "linux-install-dir",
            String.class,
            params -> {
                 String dir = INSTALL_DIR.fetchFrom(params);
                 if (dir != null) {
                     if (dir.endsWith("/")) {
                         dir = dir.substring(0, dir.length()-1);
                     }
                     return dir;
                 }
                 return "/opt";
             },
            (s, p) -> s
    );
    
    public static final BundlerParamInfo<String> LINUX_PACKAGE_DEPENDENCIES =
            new StandardBundlerParam<>(
            I18N.getString("param.linux-package-dependencies.name"),
            I18N.getString("param.linux-package-dependencies.description"),
            Arguments.CLIOptions.LINUX_PACKAGE_DEPENDENCIES.getId(),
            String.class,
            params -> {
                 return "";
             },
            (s, p) -> s
    );

    @Override
    public boolean validate(Map<String, ? super Object> p)
            throws UnsupportedPlatformException, ConfigException {
        try {
            if (p == null) throw new ConfigException(
                    I18N.getString("error.parameters-null"),
                    I18N.getString("error.parameters-null.advice"));

            return doValidate(p);
        } catch (RuntimeException re) {
            if (re.getCause() instanceof ConfigException) {
                throw (ConfigException) re.getCause();
            } else {
                throw new ConfigException(re);
            }
        }
    }

    //used by chained bundlers to reuse validation logic
    boolean doValidate(Map<String, ? super Object> p)
            throws UnsupportedPlatformException, ConfigException {
        if (Platform.getPlatform() != Platform.LINUX) {
            throw new UnsupportedPlatformException();
        }

        imageBundleValidation(p);

        return true;
    }

    //it is static for the sake of sharing with "installer" bundlers
    // that may skip calls to validate/bundle in this class!
    public static File getRootDir(File outDir, Map<String, ? super Object> p) {
        return new File(outDir, APP_FS_NAME.fetchFrom(p));
    }

    public static String getLauncherCfgName(Map<String, ? super Object> p) {
        return "app/" + APP_FS_NAME.fetchFrom(p) +".cfg";
    }

    File doBundle(Map<String, ? super Object> p, File outputDirectory,
            boolean dependentTask) {
        if (Arguments.CREATE_JRE_INSTALLER.fetchFrom(p)) {
            return doJreBundle(p, outputDirectory, dependentTask);
        } else {
            return doAppBundle(p, outputDirectory, dependentTask);
        }
    }

    private File doJreBundle(Map<String, ? super Object> p,
            File outputDirectory, boolean dependentTask) {
        try {
            File rootDirectory = createRoot(p, outputDirectory, dependentTask);
            AbstractAppImageBuilder appBuilder = new LinuxAppImageBuilder(
                    APP_NAME.fetchFrom(p), outputDirectory.toPath());
            File predefined = PREDEFINED_RUNTIME_IMAGE.fetchFrom(p);
            if (predefined == null ) {
                JLinkBundlerHelper.generateServerJre(p, appBuilder);
            } else {
                return predefined;
            }
            return rootDirectory;
        } catch (IOException ex) {
            Log.info("Exception: "+ex);
            Log.debug(ex);
            return null;
        } catch (Exception ex) {
            Log.info("Exception: "+ex);
            Log.debug(ex);
            return null;
        }
    }

    private File doAppBundle(Map<String, ? super Object> p,
            File outputDirectory, boolean dependentTask) {
        try {
            File rootDirectory = createRoot(p, outputDirectory, dependentTask);
            AbstractAppImageBuilder appBuilder = new LinuxAppImageBuilder(p,
                    outputDirectory.toPath());
            if (PREDEFINED_RUNTIME_IMAGE.fetchFrom(p) == null ) {
                JLinkBundlerHelper.execute(p, appBuilder);
            } else {
                StandardBundlerParam.copyPredefinedRuntimeImage(p, appBuilder);
            }
            return rootDirectory;
        } catch (IOException ex) {
            Log.info("Exception: "+ex);
            Log.debug(ex);
            return null;
        } catch (Exception ex) {
            Log.info("Exception: "+ex);
            Log.debug(ex);
            return null;
        }
    }

    private File createRoot(Map<String, ? super Object> p,
            File outputDirectory, boolean dependentTask) throws IOException {
        if (!outputDirectory.isDirectory() && !outputDirectory.mkdirs()) {
            throw new RuntimeException(MessageFormat.format(
                    I18N.getString("error.cannot-create-output-dir"),
                    outputDirectory.getAbsolutePath()));
        }
        if (!outputDirectory.canWrite()) {
            throw new RuntimeException(MessageFormat.format(
                    I18N.getString("error.cannot-write-to-output-dir"),
                    outputDirectory.getAbsolutePath()));
        }

        // Create directory structure
        File rootDirectory = getRootDir(outputDirectory, p);
        IOUtils.deleteRecursive(rootDirectory);
        rootDirectory.mkdirs();

        if (!dependentTask) {
            Log.info(MessageFormat.format(I18N.getString(
                    "message.creating-bundle-location"),
                    rootDirectory.getAbsolutePath()));
        }

        if (!p.containsKey(JLinkBundlerHelper.JLINK_BUILDER.getID())) {
            p.put(JLinkBundlerHelper.JLINK_BUILDER.getID(),
                    "linuxapp-image-builder");
        }
 
        return rootDirectory;
    }

    @Override
    public String getName() {
        return I18N.getString("bundler.name");
    }

    @Override
    public String getDescription() {
        return I18N.getString("bundler.description");
    }

    @Override
    public String getID() {
        return "linux.app";
    }

    @Override
    public String getBundleType() {
        return "IMAGE";
    }

    @Override
    public Collection<BundlerParamInfo<?>> getBundleParameters() {
        return getAppBundleParameters();
    }

    public static Collection<BundlerParamInfo<?>> getAppBundleParameters() {
        return Arrays.asList(
                APP_NAME,
                APP_RESOURCES,
                ARGUMENTS,
                CLASSPATH,
                JVM_OPTIONS,
                JVM_PROPERTIES,
                LINUX_RUNTIME,
                MAIN_CLASS,
                MAIN_JAR,
                PREFERENCES_ID,
                PRELOADER_CLASS,
                USER_JVM_OPTIONS,
                VERSION,
                VERBOSE
        );
    }

    @Override
    public File execute(Map<String, ? super Object> params,
            File outputParentDir) {
        return doBundle(params, outputParentDir, false);
    }
    
    @Override    
    public boolean supported() {
        // TODO: check that it really works on Solaris (in case we need it)
        return (Platform.getPlatform() == Platform.LINUX) ||
                (Platform.getPlatform() == Platform.SOLARIS);
    }
}
