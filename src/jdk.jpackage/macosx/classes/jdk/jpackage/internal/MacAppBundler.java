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
import java.math.BigInteger;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;

import static jdk.jpackage.internal.StandardBundlerParam.*;
import static jdk.jpackage.internal.MacBaseInstallerBundler.*;
import jdk.jpackage.internal.AbstractAppImageBuilder;

public class MacAppBundler extends AbstractImageBundler {

    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MacResources");

    private static final String TEMPLATE_BUNDLE_ICON = "GenericApp.icns";

    private static Map<String, String> getMacCategories() {
        Map<String, String> map = new HashMap<>();
        map.put("Business", "public.app-category.business");
        map.put("Developer Tools", "public.app-category.developer-tools");
        map.put("Education", "public.app-category.education");
        map.put("Entertainment", "public.app-category.entertainment");
        map.put("Finance", "public.app-category.finance");
        map.put("Games", "public.app-category.games");
        map.put("Graphics & Design", "public.app-category.graphics-design");
        map.put("Healthcare & Fitness",
                "public.app-category.healthcare-fitness");
        map.put("Lifestyle", "public.app-category.lifestyle");
        map.put("Medical", "public.app-category.medical");
        map.put("Music", "public.app-category.music");
        map.put("News", "public.app-category.news");
        map.put("Photography", "public.app-category.photography");
        map.put("Productivity", "public.app-category.productivity");
        map.put("Reference", "public.app-category.reference");
        map.put("Social Networking", "public.app-category.social-networking");
        map.put("Sports", "public.app-category.sports");
        map.put("Travel", "public.app-category.travel");
        map.put("Utilities", "public.app-category.utilities");
        map.put("Video", "public.app-category.video");
        map.put("Weather", "public.app-category.weather");

        map.put("Action Games", "public.app-category.action-games");
        map.put("Adventure Games", "public.app-category.adventure-games");
        map.put("Arcade Games", "public.app-category.arcade-games");
        map.put("Board Games", "public.app-category.board-games");
        map.put("Card Games", "public.app-category.card-games");
        map.put("Casino Games", "public.app-category.casino-games");
        map.put("Dice Games", "public.app-category.dice-games");
        map.put("Educational Games", "public.app-category.educational-games");
        map.put("Family Games", "public.app-category.family-games");
        map.put("Kids Games", "public.app-category.kids-games");
        map.put("Music Games", "public.app-category.music-games");
        map.put("Puzzle Games", "public.app-category.puzzle-games");
        map.put("Racing Games", "public.app-category.racing-games");
        map.put("Role Playing Games", "public.app-category.role-playing-games");
        map.put("Simulation Games", "public.app-category.simulation-games");
        map.put("Sports Games", "public.app-category.sports-games");
        map.put("Strategy Games", "public.app-category.strategy-games");
        map.put("Trivia Games", "public.app-category.trivia-games");
        map.put("Word Games", "public.app-category.word-games");

        return map;
    }

    public static final EnumeratedBundlerParam<String> MAC_CATEGORY =
            new EnumeratedBundlerParam<>(
                    I18N.getString("param.category-name"),
                    I18N.getString("param.category-name.description"),
                    Arguments.CLIOptions.MAC_APP_STORE_CATEGORY.getId(),
                    String.class,
                    params -> params.containsKey(CATEGORY.getID())
                            ? CATEGORY.fetchFrom(params)
                            : "Unknown",
                    (s, p) -> s,
                    getMacCategories(),
                    false //strict - for MacStoreBundler this should be strict
            );

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_NAME =
            new StandardBundlerParam<>(
                    I18N.getString("param.cfbundle-name.name"),
                    I18N.getString("param.cfbundle-name.description"),
                    Arguments.CLIOptions.MAC_BUNDLE_NAME.getId(),
                    String.class,
                    params -> null,
                    (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_IDENTIFIER =
            new StandardBundlerParam<>(
                    I18N.getString("param.cfbundle-identifier.name"),
                    I18N.getString("param.cfbundle-identifier.description"),
                    Arguments.CLIOptions.MAC_BUNDLE_IDENTIFIER.getId(),
                    String.class,
                    IDENTIFIER::fetchFrom,
                    (s, p) -> s);

    public static final BundlerParamInfo<String> MAC_CF_BUNDLE_VERSION =
            new StandardBundlerParam<>(
                    I18N.getString("param.cfbundle-version.name"),
                    I18N.getString("param.cfbundle-version.description"),
                    "mac.CFBundleVersion",
                    String.class,
                    p -> {
                        String s = VERSION.fetchFrom(p);
                        if (validCFBundleVersion(s)) {
                            return s;
                        } else {
                            return "100";
                        }
                    },
                    (s, p) -> s);

    public static final BundlerParamInfo<String> DEFAULT_ICNS_ICON =
            new StandardBundlerParam<>(
            I18N.getString("param.default-icon-icns"),
            I18N.getString("param.default-icon-icns.description"),
            ".mac.default.icns",
            String.class,
            params -> TEMPLATE_BUNDLE_ICON,
            (s, p) -> s);

    public static final BundlerParamInfo<String> DEVELOPER_ID_APP_SIGNING_KEY =
            new StandardBundlerParam<>(
            I18N.getString("param.signing-key-developer-id-app.name"),
            I18N.getString("param.signing-key-developer-id-app.description"),
            "mac.signing-key-developer-id-app",
            String.class,
            params -> {
                    String result = MacBaseInstallerBundler.findKey(
                            "Developer ID Application: "
                            + SIGNING_KEY_USER.fetchFrom(params),
                            SIGNING_KEYCHAIN.fetchFrom(params),
                            VERBOSE.fetchFrom(params));
                    if (result != null) {
                        MacCertificate certificate = new MacCertificate(result,
                                VERBOSE.fetchFrom(params));

                        if (!certificate.isValid()) {
                            Log.error(MessageFormat.format(I18N.getString(
                                    "error.certificate.expired"), result));
                        }
                    }

                    return result;
                },
            (s, p) -> s);

    public static final BundlerParamInfo<String> BUNDLE_ID_SIGNING_PREFIX =
            new StandardBundlerParam<>(
            I18N.getString("param.bundle-id-signing-prefix.name"),
            I18N.getString("param.bundle-id-signing-prefix.description"),
            Arguments.CLIOptions.MAC_BUNDLE_SIGNING_PREFIX.getId(),
            String.class,
            params -> IDENTIFIER.fetchFrom(params) + ".",
            (s, p) -> s);

    public static final BundlerParamInfo<File> ICON_ICNS =
            new StandardBundlerParam<>(
            I18N.getString("param.icon-icns.name"),
            I18N.getString("param.icon-icns.description"),
            "icon.icns",
            File.class,
            params -> {
                File f = ICON.fetchFrom(params);
                if (f != null && !f.getName().toLowerCase().endsWith(".icns")) {
                    Log.error(MessageFormat.format(
                            I18N.getString("message.icon-not-icns"), f));
                    return null;
                }
                return f;
            },
            (s, p) -> new File(s));

    public static boolean validCFBundleVersion(String v) {
        // CFBundleVersion (String - iOS, OS X) specifies the build version
        // number of the bundle, which identifies an iteration (released or
        // unreleased) of the bundle. The build version number should be a
        // string comprised of three non-negative, period-separated integers
        // with the first integer being greater than zero. The string should
        // only contain numeric (0-9) and period (.) characters. Leading zeros
        // are truncated from each integer and will be ignored (that is,
        // 1.02.3 is equivalent to 1.2.3). This key is not localizable.

        if (v == null) {
            return false;
        }

        String p[] = v.split("\\.");
        if (p.length > 3 || p.length < 1) {
            Log.verbose(I18N.getString(
                    "message.version-string-too-many-components"));
            return false;
        }

        try {
            BigInteger n = new BigInteger(p[0]);
            if (BigInteger.ONE.compareTo(n) > 0) {
                Log.verbose(I18N.getString(
                        "message.version-string-first-number-not-zero"));
                return false;
            }
            if (p.length > 1) {
                n = new BigInteger(p[1]);
                if (BigInteger.ZERO.compareTo(n) > 0) {
                    Log.verbose(I18N.getString(
                            "message.version-string-no-negative-numbers"));
                    return false;
                }
            }
            if (p.length > 2) {
                n = new BigInteger(p[2]);
                if (BigInteger.ZERO.compareTo(n) > 0) {
                    Log.verbose(I18N.getString(
                            "message.version-string-no-negative-numbers"));
                    return false;
                }
            }
        } catch (NumberFormatException ne) {
            Log.verbose(I18N.getString("message.version-string-numbers-only"));
            Log.verbose(ne);
            return false;
        }

        return true;
    }

    @Override
    public boolean validate(Map<String, ? super Object> params)
            throws UnsupportedPlatformException, ConfigException {
        try {
            return doValidate(params);
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
        if (Platform.getPlatform() != Platform.MAC) {
            throw new UnsupportedPlatformException();
        }

        imageBundleValidation(p);

        if (StandardBundlerParam.getPredefinedAppImage(p) != null) {
            return true;
        }

        // validate short version
        if (!validCFBundleVersion(MAC_CF_BUNDLE_VERSION.fetchFrom(p))) {
            throw new ConfigException(
                    I18N.getString("error.invalid-cfbundle-version"),
                    I18N.getString("error.invalid-cfbundle-version.advice"));
        }

        // reject explicitly set sign to true and no valid signature key
        if (Optional.ofNullable(MacAppImageBuilder.
                    SIGN_BUNDLE.fetchFrom(p)).orElse(Boolean.FALSE)) {
            String signingIdentity = DEVELOPER_ID_APP_SIGNING_KEY.fetchFrom(p);
            if (signingIdentity == null) {
                throw new ConfigException(
                        I18N.getString("error.explicit-sign-no-cert"),
                        I18N.getString("error.explicit-sign-no-cert.advice"));
            }
        }

        return true;
    }

    File doBundle(Map<String, ? super Object> p, File outputDirectory,
            boolean dependentTask) throws PackagerException {
        if (RUNTIME_INSTALLER.fetchFrom(p)) {
            return doJreBundle(p, outputDirectory, dependentTask);
        } else {
            return doAppBundle(p, outputDirectory, dependentTask);
        }
    }

    File doJreBundle(Map<String, ? super Object> p, File outputDirectory,
            boolean dependentTask) throws PackagerException {
        try {
            File rootDirectory = createRoot(p, outputDirectory, dependentTask,
                    APP_NAME.fetchFrom(p), "macapp-image-builder");
            AbstractAppImageBuilder appBuilder = new MacAppImageBuilder(p,
                    APP_NAME.fetchFrom(p), outputDirectory.toPath());
            File predefined = PREDEFINED_RUNTIME_IMAGE.fetchFrom(p);
            if (predefined == null ) {
                JLinkBundlerHelper.generateJre(p, appBuilder);
            } else {
                return predefined;
            }
            return rootDirectory;
        } catch (PackagerException pe) {
            throw pe;
        } catch (Exception ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    File doAppBundle(Map<String, ? super Object> p, File outputDirectory,
            boolean dependentTask) throws PackagerException {
        try {
            File rootDirectory = createRoot(p, outputDirectory, dependentTask,
                    APP_NAME.fetchFrom(p) + ".app", "macapp-image-builder");
            AbstractAppImageBuilder appBuilder =
                    new MacAppImageBuilder(p, outputDirectory.toPath());
            if (PREDEFINED_RUNTIME_IMAGE.fetchFrom(p) == null ) {
                JLinkBundlerHelper.execute(p, appBuilder);
            } else {
                StandardBundlerParam.copyPredefinedRuntimeImage(p, appBuilder);
            }
            return rootDirectory;
        } catch (PackagerException pe) {
            throw pe;
        } catch (Exception ex) {
            Log.verbose(ex);
            throw new PackagerException(ex);
        }
    }

    /////////////////////////////////////////////////////////////////////////
    // Implement Bundler
    /////////////////////////////////////////////////////////////////////////

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
        return "mac.app";
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
                BUNDLE_ID_SIGNING_PREFIX,
                CLASSPATH,
                DEVELOPER_ID_APP_SIGNING_KEY,
                ICON_ICNS,
                JVM_OPTIONS,
                MAC_CATEGORY,
                MAC_CF_BUNDLE_IDENTIFIER,
                MAC_CF_BUNDLE_NAME,
                MAC_CF_BUNDLE_VERSION,
                MAIN_CLASS,
                MAIN_JAR,
                PREFERENCES_ID,
                SIGNING_KEYCHAIN,
                VERSION,
                VERBOSE
        );
    }


    @Override
    public File execute(Map<String, ? super Object> params,
            File outputParentDir) throws PackagerException {
        return doBundle(params, outputParentDir, false);
    }

    @Override
    public boolean supported(boolean runtimeInstaller) {
        return Platform.getPlatform() == Platform.MAC;
    }

}
