/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VersionExtractor extends PrintStream {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.WinResources");

    private final String pattern;
    private String version = null;

    public VersionExtractor(String pattern) {
        super(new ByteArrayOutputStream());

        this.pattern = pattern;
    }

    public String getVersion() {
        if (version == null) {
            String content
                    = new String(((ByteArrayOutputStream) out).toByteArray());
            Pattern p = Pattern.compile(pattern);
            Matcher matcher = p.matcher(content);
            if (matcher.find()) {
                version = matcher.group(1);
            }
        }
        return version;
    }

    public static boolean isLessThan(String version, String compareTo)
            throws RuntimeException {
        if (version == null || version.isEmpty()) {
            throw new RuntimeException(MessageFormat.format(
                    I18N.getString("error.version-compare"),
                    version, compareTo));
        }

        if (compareTo == null || compareTo.isEmpty()) {
            throw new RuntimeException(MessageFormat.format(
                    I18N.getString("error.version-compare"),
                    version, compareTo));
        }

        String [] versionArray = version.trim().split(Pattern.quote("."));
        String [] compareToArray = compareTo.trim().split(Pattern.quote("."));

        for (int i = 0; i < versionArray.length; i++) {
            int v1 = Integer.parseInt(versionArray[i]);
            int v2 = Integer.parseInt(compareToArray[i]);
            if (v1 < v2) {
                return true;
            } else if (v1 > v2) {
                return false;
            }
        }

        return false;
    }
}
