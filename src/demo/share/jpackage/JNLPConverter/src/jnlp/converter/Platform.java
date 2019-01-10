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

import java.util.regex.Pattern;

public enum Platform {
    UNKNOWN, WINDOWS, LINUX, MAC;
    private static final Platform platform;
    private static final int majorVersion;
    private static final int minorVersion;

    static {
        String os = System.getProperty("os.name").toLowerCase();

        if (os.contains("win")) {
            platform = Platform.WINDOWS;
        } else if (os.contains("nix") || os.contains("nux")) {
            platform = Platform.LINUX;
        } else if (os.contains("mac")) {
            platform = Platform.MAC;
        } else {
            platform = Platform.UNKNOWN;
        }

        String version = System.getProperty("os.version");
        String[] parts = version.split(Pattern.quote("."));

        if (parts.length > 0) {
            majorVersion = Integer.parseInt(parts[0]);

            if (parts.length > 1) {
                minorVersion = Integer.parseInt(parts[0]);
            } else {
                minorVersion = -1;
            }
        } else {
            majorVersion = -1;
            minorVersion = -1;
        }
    }

    private Platform() {
    }

    public static Platform getPlatform() {
        return platform;
    }

    public static boolean isWindows() {
        return (platform == Platform.WINDOWS);
    }

    public static int getMajorVersion() {
        return majorVersion;
    }

    public static int getMinorVersion() {
        return minorVersion;
    }
}
