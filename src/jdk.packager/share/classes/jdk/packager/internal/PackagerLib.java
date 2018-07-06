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

package jdk.packager.internal;

import jdk.packager.internal.bundlers.BundleParams;
import jdk.packager.internal.bundlers.Bundler.BundleType;
import java.io.File;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;


// this class must be removed in the future
@Deprecated
public class PackagerLib {
    public static final String JAVA_VERSION = System.getProperty("java.version");

    private static final ResourceBundle bundle =
            ResourceBundle.getBundle("jdk.packager.internal.resources.Bundle");

    private enum Filter {ALL, CLASSES_ONLY, RESOURCES}

    public void generateDeploymentPackages(DeployParams deployParams) throws PackagerException {
        if (deployParams == null) {
            throw new IllegalArgumentException("Parameters must not be null.");
        }

        try {
            BundleParams bp = deployParams.getBundleParams();

            if (bp != null) {
                switch(deployParams.getBundleType()) {
                    case NATIVE: {
                        // Generate disk images.
                        generateNativeBundles(deployParams.outdir,
                                              bp.getBundleParamsAsMap(),
                                              BundleType.IMAGE.toString(),
                                              deployParams.getTargetFormat());

                        // Generate installers.
                        generateNativeBundles(deployParams.outdir,
                                              bp.getBundleParamsAsMap(),
                                              BundleType.INSTALLER.toString(),
                                              deployParams.getTargetFormat());
                        break;
                    }

                    case NONE: {
                        break;
                    }

                    default: {
                        // A specefic output format, just generate that.
                        generateNativeBundles(deployParams.outdir,
                                              bp.getBundleParamsAsMap(),
                                              deployParams.getBundleType().toString(),
                                              deployParams.getTargetFormat());
                    }
                }
            }
        } catch (PackagerException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new PackagerException(ex, "ERR_DeployFailed", ex.getMessage());
        }

    }

    private void generateNativeBundles(File outdir, Map<String, ? super Object> params,
            String bundleType, String bundleFormat) throws PackagerException {
        for (Bundler bundler : Bundlers.createBundlersInstance().getBundlers(bundleType)) {
            // if they specify the bundle format, require we match the ID
            if (bundleFormat != null && !bundleFormat.equalsIgnoreCase(bundler.getID())) continue;

            Map<String, ? super Object> localParams = new HashMap<>(params);
            try {
                if (bundler.validate(localParams)) {
                    File result = bundler.execute(localParams, outdir);
                    bundler.cleanup(localParams);
                    if (result == null) {
                        throw new PackagerException("MSG_BundlerFailed", bundler.getID(), bundler.getName());
                    }
                }
            } catch (UnsupportedPlatformException e) {
                Log.debug(MessageFormat.format(bundle.getString("MSG_BundlerPlatformException"), bundler.getName()));
            } catch (ConfigException e) {
                Log.debug(e);
                if (e.getAdvice() != null) {
                    Log.info(MessageFormat.format(bundle.getString("MSG_BundlerConfigException"), bundler.getName(), e.getMessage(), e.getAdvice()));
                } else {
                    Log.info(MessageFormat.format(bundle.getString("MSG_BundlerConfigExceptionNoAdvice"), bundler.getName(), e.getMessage()));
                }
            } catch (RuntimeException re) {
                Log.info(MessageFormat.format(bundle.getString("MSG_BundlerRuntimeException"), bundler.getName(), re.toString()));
                Log.debug(re);
            }
        }
    }
}
