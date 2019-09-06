/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


/**
 * jpackage package type traits.
 */
public enum PackageType {
    WIN_MSI(".msi", "jdk.jpackage.internal.WinMsiBundler"),
    WIN_EXE(".exe", "jdk.jpackage.internal.WinMsiBundler"),
    LINUX_DEB(".deb", "jdk.jpackage.internal.LinuxDebBundler"),
    LINUX_RPM(".rpm", "jdk.jpackage.internal.LinuxRpmBundler"),
    OSX_DMG(".dmg", "jdk.jpackage.internal.MacDmgBundler"),
    IMAGE(null, null);

    PackageType(String bundleSuffix, String bundlerClass) {
        suffix = bundleSuffix;
        if (bundlerClass != null) {
            supported = isBundlerSupported(bundlerClass);
        } else {
            supported = false;
        }

        if (suffix != null && supported) {
            Test.trace(String.format("Bundler %s supported", getName()));
        }
    }

    void applyTo(JPackageCommand cmd) {
        cmd.addArguments("--package-type", getName());
    }

    String getSuffix() {
        return suffix;
    }

    boolean isSupported() {
        return supported;
    }

    String getName() {
        if (suffix == null) {
            return null;
        }
        return suffix.substring(1);
    }

    static PackageType fromSuffix(String packageFilename) {
        if (packageFilename != null) {
            for (PackageType v: values()) {
                if (packageFilename.endsWith(v.getSuffix())) {
                    return v;
                }
            }
        }
        return null;
    }

    private static boolean isBundlerSupported(String bundlerClass) {
        try {
            Class clazz = Class.forName(bundlerClass);
            Method isSupported = clazz.getDeclaredMethod("isSupported");
            return ((Boolean)isSupported.invoke(clazz));
        } catch (ClassNotFoundException ex) {
            return false;
        } catch (NoSuchMethodException|IllegalAccessException|InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    private final String suffix;
    private final boolean supported;
}
