/*
 * Copyright (c) 2015, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.text.MessageFormat;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.File;
import java.io.IOException;

import static jdk.jpackage.internal.StandardBundlerParam.*;

/**
 * AbstractImageBundler
 *
 * This is the base class for each of the Application Image Bundlers.
 *
 * It contains methods and parameters common to all Image Bundlers.
 *
 * Application Image Bundlers are created in "create-image" mode,
 * or as an intermeadiate step in "create-installer" mode.
 *
 * The concrete implementations are in the platform specific Bundlers.
 */
public abstract class AbstractImageBundler extends AbstractBundler {

    private final static String JAVA_VERSION_SPEC =
        "java version \"((\\d+).(\\d+).(\\d+).(\\d+))(-(.*))?(\\+[^\"]*)?\"";

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MainResources");

    public void imageBundleValidation(Map<String, ? super Object> p)
             throws ConfigException {
        StandardBundlerParam.validateMainClassInfoFromAppResources(p);

        boolean hasMainJar = MAIN_JAR.fetchFrom(p) != null;
        boolean hasMainModule =
                StandardBundlerParam.MODULE.fetchFrom(p) != null;
        boolean hasMainClass = MAIN_CLASS.fetchFrom(p) != null;
        boolean runtime = RUNTIME_INSTALLER.fetchFrom(p);

        if (!hasMainJar && !hasMainModule && !hasMainClass && !runtime) {
            throw new ConfigException(
                    I18N.getString("error.no-application-class"),
                    I18N.getString("error.no-application-class.advice"));
        }
    }

    public static void extractFlagsFromVersion(
            Map<String, ? super Object> params, String versionOutput) {
        Pattern bitArchPattern = Pattern.compile("(\\d*)[- ]?[bB]it");
        Matcher matcher = bitArchPattern.matcher(versionOutput);
        if (matcher.find()) {
            params.put(".runtime.bit-arch", matcher.group(1));
        } else {
            // presume 32 bit on no match
            params.put(".runtime.bit-arch", "32");
        }

        Pattern oldVersionMatcher = Pattern.compile(
                "java version \"((\\d+.(\\d+).\\d+)(_(\\d+)))?(-(.*))?\"");
        matcher = oldVersionMatcher.matcher(versionOutput);
        if (matcher.find()) {
            params.put(".runtime.version", matcher.group(1));
            params.put(".runtime.version.release", matcher.group(2));
            params.put(".runtime.version.major", matcher.group(3));
            params.put(".runtime.version.update", matcher.group(5));
            params.put(".runtime.version.minor", matcher.group(5));
            params.put(".runtime.version.security", matcher.group(5));
            params.put(".runtime.version.patch", "0");
            params.put(".runtime.version.modifiers", matcher.group(7));
        } else {
            Pattern newVersionMatcher = Pattern.compile(JAVA_VERSION_SPEC);
            matcher = newVersionMatcher.matcher(versionOutput);
            if (matcher.find()) {
                params.put(".runtime.version", matcher.group(1));
                params.put(".runtime.version.release", matcher.group(1));
                params.put(".runtime.version.major", matcher.group(2));
                params.put(".runtime.version.update", matcher.group(3));
                params.put(".runtime.version.minor", matcher.group(3));
                params.put(".runtime.version.security", matcher.group(4));
                params.put(".runtime.version.patch", matcher.group(5));
                params.put(".runtime.version.modifiers", matcher.group(7));
            } else {
                params.put(".runtime.version", "");
                params.put(".runtime.version.release", "");
                params.put(".runtime.version.major", "");
                params.put(".runtime.version.update", "");
                params.put(".runtime.version.minor", "");
                params.put(".runtime.version.security", "");
                params.put(".runtime.version.patch", "");
                params.put(".runtime.version.modifiers", "");
            }
        }
    }

    protected File createRoot(Map<String, ? super Object> p,
            File outputDirectory, boolean dependentTask,
            String name, String jlinkKey) throws PackagerException {
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
        if (!dependentTask) {
            Log.verbose(MessageFormat.format(
                    I18N.getString("message.creating-app-bundle"),
                    name, outputDirectory.getAbsolutePath()));
        }

        // Create directory structure
        File rootDirectory = new File(outputDirectory, name);

        if (rootDirectory.exists()) {
            if (!(OVERWRITE.fetchFrom(p))) {
                throw new PackagerException("error.root-exists-without-force",
                        rootDirectory.getAbsolutePath());
            }
            try {
                IOUtils.deleteRecursive(rootDirectory);
            } catch (IOException ioe) {
                throw new PackagerException(ioe);
            }
        }
        rootDirectory.mkdirs();

        if (!p.containsKey(JLinkBundlerHelper.JLINK_BUILDER.getID())) {
            p.put(JLinkBundlerHelper.JLINK_BUILDER.getID(), jlinkKey);
        }

        return rootDirectory;
    }

}
