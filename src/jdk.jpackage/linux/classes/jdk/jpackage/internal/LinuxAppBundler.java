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
import java.net.MalformedURLException;
import java.net.URL;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.ResourceBundle;

import static jdk.jpackage.internal.StandardBundlerParam.*;

public class LinuxAppBundler extends AbstractImageBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.LinuxResources");

    public static final BundlerParamInfo<File> ICON_PNG =
            new StandardBundlerParam<>(
            I18N.getString("param.icon-png.name"),
            I18N.getString("param.icon-png.description"),
            "icon.png",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".png")) {
                    Log.error(MessageFormat.format(
                            I18N.getString("message.icon-not-png"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

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

    private boolean doValidate(Map<String, ? super Object> p)
            throws UnsupportedPlatformException, ConfigException {
        if (Platform.getPlatform() != Platform.LINUX) {
            throw new UnsupportedPlatformException();
        }

        imageBundleValidation(p);

        return true;
    }

    // it is static for the sake of sharing with "installer" bundlers
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
            File rootDirectory = createRoot(p, outputDirectory, dependentTask,
                    APP_FS_NAME.fetchFrom(p), "linuxapp-image-builder");
            AbstractAppImageBuilder appBuilder = new LinuxAppImageBuilder(
                    APP_NAME.fetchFrom(p), outputDirectory.toPath());
            File predefined = PREDEFINED_RUNTIME_IMAGE.fetchFrom(p);
            if (predefined == null ) {
                JLinkBundlerHelper.generateJre(p, appBuilder);
            } else {
                return predefined;
            }
            return rootDirectory;
        } catch (Exception ex) {
            Log.error("Exception: "+ex);
            Log.debug(ex);
            return null;
        }
    }

    private File doAppBundle(Map<String, ? super Object> p,
            File outputDirectory, boolean dependentTask) {
        try {
            File rootDirectory = createRoot(p, outputDirectory, dependentTask,
                    APP_FS_NAME.fetchFrom(p), "linuxapp-image-builder");
            AbstractAppImageBuilder appBuilder = new LinuxAppImageBuilder(p,
                    outputDirectory.toPath());
            if (PREDEFINED_RUNTIME_IMAGE.fetchFrom(p) == null ) {
                JLinkBundlerHelper.execute(p, appBuilder);
            } else {
                StandardBundlerParam.copyPredefinedRuntimeImage(p, appBuilder);
            }
            return rootDirectory;
        } catch (Exception ex) {
            Log.error("Exception: "+ex);
            Log.debug(ex);
            return null;
        }
    }

    @Override
    public String getName() {
        return I18N.getString("app.bundler.name");
    }

    @Override
    public String getDescription() {
        return I18N.getString("app.bundler.description");
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
                MAIN_CLASS,
                MAIN_JAR,
                PREFERENCES_ID,
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
        return (Platform.getPlatform() == Platform.LINUX);
    }
}
