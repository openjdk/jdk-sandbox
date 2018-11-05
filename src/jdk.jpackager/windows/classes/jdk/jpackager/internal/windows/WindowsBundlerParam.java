/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackager.internal.windows;

import jdk.jpackager.internal.BundlerParamInfo;
import jdk.jpackager.internal.StandardBundlerParam;
import jdk.jpackager.internal.Arguments;
import jdk.jpackager.internal.RelativeFileSet;
import jdk.jpackager.internal.bundlers.BundleParams;

import java.util.Map;
import java.util.ResourceBundle;
import java.util.function.BiFunction;
import java.util.function.Function;

public class WindowsBundlerParam<T> extends StandardBundlerParam<T> {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackager.internal.resources.windows.WindowsBundlerParam");

    public WindowsBundlerParam(String name, String description, String id,
            Class<T> valueType,
            Function<Map<String, ? super Object>, T> defaultValueFunction,
            BiFunction<String,
            Map<String, ? super Object>, T> stringConverter) {
        super(name, description, id, valueType,
                defaultValueFunction, stringConverter);
    }

    public static final BundlerParamInfo<String> INSTALLER_FILE_NAME =
            new StandardBundlerParam<> (
            I18N.getString("param.installer-name.name"),
            I18N.getString("param.installer-name.description"),
            "win.installerName",
            String.class,
            params -> {
                String nm = APP_NAME.fetchFrom(params);
                if (nm == null) return null;

                String version = VERSION.fetchFrom(params);
                if (version == null) {
                    return nm;
                } else {
                    return nm + "-" + version;
                }
            },
            (s, p) -> s);

    public static final BundlerParamInfo<String> APP_REGISTRY_NAME =
            new StandardBundlerParam<> (
            I18N.getString("param.registry-name.name"),
            I18N.getString("param.registry-name.description"),
            Arguments.CLIOptions.WIN_REGISTRY_NAME.getId(),
            String.class,
            params -> {
                String nm = APP_NAME.fetchFrom(params);
                if (nm == null) return null;

                return nm.replaceAll("[^-a-zA-Z\\.0-9]", "");
            },
            (s, p) -> s);

    public static final StandardBundlerParam<String> MENU_GROUP =
            new StandardBundlerParam<>(
                    I18N.getString("param.menu-group.name"),
                    I18N.getString("param.menu-group.description"),
                    Arguments.CLIOptions.WIN_MENU_GROUP.getId(),
                    String.class,
                    params -> params.containsKey(VENDOR.getID())
                            ? VENDOR.fetchFrom(params)
                            : params.containsKey(CATEGORY.getID())
                            ? CATEGORY.fetchFrom(params)
                            : I18N.getString("param.menu-group.default"),
                    (s, p) -> s
            );

    public static final StandardBundlerParam<Boolean> BIT_ARCH_64 =
            new StandardBundlerParam<>(
                    I18N.getString("param.64-bit.name"),
                    I18N.getString("param.64-bit.description"),
                    "win.64Bit",
                    Boolean.class,
                    params -> System.getProperty("os.arch").contains("64"),
                    (s, p) -> Boolean.valueOf(s)
            );

    public static final StandardBundlerParam<Boolean> BIT_ARCH_64_RUNTIME =
            new StandardBundlerParam<>(
                    I18N.getString("param.runtime-64-bit.name"),
                    I18N.getString("param.runtime-64-bit.description"),
                    "win.64BitJreRuntime",
                    Boolean.class,
                    params -> {
                        WinAppBundler.extractFlagsFromRuntime(params);
                        return "64".equals(params.get(".runtime.bit-arch"));
                    },
                    (s, p) -> Boolean.valueOf(s)
            );

    public static final BundlerParamInfo<Boolean> INSTALLDIR_CHOOSER =
            new StandardBundlerParam<> (
            I18N.getString("param.installdir-chooser.name"),
            I18N.getString("param.installdir-chooser.description"),
            Arguments.CLIOptions.WIN_DIR_CHOOSER.getId(),
            Boolean.class,
            params -> Boolean.FALSE,
            (s, p) -> Boolean.valueOf(s)
    );
}
