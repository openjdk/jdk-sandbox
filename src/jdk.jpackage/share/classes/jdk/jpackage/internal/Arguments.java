/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Arguments
 *
 * This class encapsulates and processes the command line arguments,
 * in effect, implementing all the work of jpackage tool.
 *
 * The primary entry point, processArguments():
 * Processes and validates command line arguments, constructing DeployParams.
 * Validates the DeployParams, and generate the BundleParams.
 * Generates List of Bundlers from BundleParams valid for this platform.
 * Executes each Bundler in the list.
 */
public class Arguments {
    private static final ResourceBundle I18N = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MainResources");

    private static final String IMAGE_MODE = "image";
    private static final String INSTALLER_MODE = "installer";

    private static final String FA_EXTENSIONS = "extension";
    private static final String FA_CONTENT_TYPE = "mime-type";
    private static final String FA_DESCRIPTION = "description";
    private static final String FA_ICON = "icon";

    public static final BundlerParamInfo<Boolean> CREATE_IMAGE =
            new StandardBundlerParam<>(
                    I18N.getString("param.create-image.name"),
                    I18N.getString("param.create-image.description"),
                    IMAGE_MODE,
                    Boolean.class,
                    p -> Boolean.FALSE,
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                            true : Boolean.valueOf(s));

    public static final BundlerParamInfo<Boolean> CREATE_INSTALLER =
            new StandardBundlerParam<>(
                    I18N.getString("param.create-installer.name"),
                    I18N.getString("param.create-installer.description"),
                    INSTALLER_MODE,
                    Boolean.class,
                    p -> Boolean.FALSE,
                    (s, p) -> (s == null || "null".equalsIgnoreCase(s)) ?
                            true : Boolean.valueOf(s));

    // regexp for parsing args (for example, for secondary launchers)
    private static Pattern pattern = Pattern.compile(
          "(?:(?:([\"'])(?:\\\\\\1|.)*?(?:\\1|$))|(?:\\\\[\"'\\s]|[^\\s]))++");

    private DeployParams deployParams = null;
    private BundlerType bundleType = null;

    private int pos = 0;
    private List<String> argList = null;

    private List<CLIOptions> allOptions = null;

    private ArrayList<String> files = null;

    private String input = null;
    private String output = null;

    private boolean hasMainJar = false;
    private boolean hasMainClass = false;
    private boolean hasMainModule = false;
    private boolean hasTargetFormat = false;
    private boolean hasAppImage = false;
    public boolean userProvidedBuildRoot = false;

    private String buildRoot = null;
    private String mainJarPath = null;

    private static boolean runtimeInstaller = false;

    private List<jdk.jpackage.internal.Bundler> platformBundlers = null;

    private List<SecondaryLauncherArguments> secondaryLaunchers = null;

    private static Map<String, CLIOptions> argIds = new HashMap<>();
    private static Map<String, CLIOptions> argShortIds = new HashMap<>();

    {
        // init maps for parsing arguments
        EnumSet<CLIOptions> options = EnumSet.allOf(CLIOptions.class);

        options.forEach(option -> {
            argIds.put(option.getIdWithPrefix(), option);
            if (option.getShortIdWithPrefix() != null) {
                argShortIds.put(option.getShortIdWithPrefix(), option);
            }
        });
    }

    public Arguments(String[] args) throws PackagerException {
        initArgumentList(args);
    }

    // CLIOptions is public for DeployParamsTest
    public enum CLIOptions {
        CREATE_IMAGE(IMAGE_MODE, OptionCategories.MODE, () -> {
            context().bundleType = BundlerType.IMAGE;
            context().deployParams.setTargetFormat("image");
            setOptionValue(IMAGE_MODE, true);
        }),

        CREATE_INSTALLER(INSTALLER_MODE, OptionCategories.MODE, () -> {
            setOptionValue(INSTALLER_MODE, true);
            context().bundleType = BundlerType.INSTALLER;
            String format = "installer";
            context().deployParams.setTargetFormat(format);
        }),

        RUNTIME_INSTALLER("runtime-installer",
                OptionCategories.PROPERTY, () -> {
            runtimeInstaller = true;
            setOptionValue("runtime-installer", true);
        }),

        INSTALLER_TYPE("installer-type", OptionCategories.PROPERTY, () -> {
            String type = popArg();
            context().deployParams.setTargetFormat(type);
            context().hasTargetFormat = true;
            setOptionValue("installer-type", type);
        }),

        INPUT ("input", "i", OptionCategories.PROPERTY, () -> {
            context().input = popArg();
            setOptionValue("input", context().input);
        }),

        OUTPUT ("output", "o", OptionCategories.PROPERTY, () -> {
            context().output = popArg();
            context().deployParams.setOutput(new File(context().output));
        }),

        DESCRIPTION ("description", "d", OptionCategories.PROPERTY),

        VENDOR ("vendor", OptionCategories.PROPERTY),

        APPCLASS ("main-class", "c", OptionCategories.PROPERTY, () -> {
            context().hasMainClass = true;
            setOptionValue("main-class", popArg());
        }),

        NAME ("name", "n", OptionCategories.PROPERTY),

        IDENTIFIER ("identifier", OptionCategories.PROPERTY),

        VERBOSE ("verbose", OptionCategories.PROPERTY, () -> {
            setOptionValue("verbose", true);
            Log.setVerbose(true);
        }),

        OVERWRITE ("overwrite", OptionCategories.PROPERTY, () -> {
            setOptionValue("overwrite", true);
        }),

        RESOURCE_DIR("resource-dir",
                OptionCategories.PROPERTY, () -> {
            String resourceDir = popArg();
            setOptionValue("resource-dir", resourceDir);
        }),

        FILES ("files", "f", OptionCategories.PROPERTY, () -> {
              context().files = new ArrayList<>();
              String files = popArg();
              context().files.addAll(
                      Arrays.asList(files.split(File.pathSeparator)));
        }),

        ARGUMENTS ("arguments", "a", OptionCategories.PROPERTY, () -> {
            List<String> arguments = getArgumentList(popArg());
            setOptionValue("arguments", arguments);
        }),

        STRIP_NATIVE_COMMANDS ("strip-native-commands",
                   OptionCategories.PROPERTY, () -> {
            setOptionValue("strip-native-commands", true);
        }),

        ICON ("icon", OptionCategories.PROPERTY),
        CATEGORY ("category", OptionCategories.PROPERTY),
        COPYRIGHT ("copyright", OptionCategories.PROPERTY),

        LICENSE_FILE ("license-file", OptionCategories.PROPERTY),

        VERSION ("app-version", OptionCategories.PROPERTY),

        JVM_ARGS ("jvm-args", OptionCategories.PROPERTY, () -> {
            List<String> args = getArgumentList(popArg());
            args.forEach(a -> setOptionValue("jvm-args", a));
        }),

        FILE_ASSOCIATIONS ("file-associations",
                OptionCategories.PROPERTY, () -> {
            Map<String, ? super Object> args = new HashMap<>();

            // load .properties file
            Map<String, String> initialMap = getPropertiesFromFile(popArg());

            String ext = initialMap.get(FA_EXTENSIONS);
            if (ext != null) {
                args.put(StandardBundlerParam.FA_EXTENSIONS.getID(), ext);
            }

            String type = initialMap.get(FA_CONTENT_TYPE);
            if (type != null) {
                args.put(StandardBundlerParam.FA_CONTENT_TYPE.getID(), type);
            }

            String desc = initialMap.get(FA_DESCRIPTION);
            if (desc != null) {
                args.put(StandardBundlerParam.FA_DESCRIPTION.getID(), desc);
            }

            String icon = initialMap.get(FA_ICON);
            if (icon != null) {
                args.put(StandardBundlerParam.FA_ICON.getID(), icon);
            }

            ArrayList<Map<String, ? super Object>> associationList =
                new ArrayList<Map<String, ? super Object>>();

            associationList.add(args);

            // check that we really add _another_ value to the list
            setOptionValue("file-associations", associationList);

        }),

        SECONDARY_LAUNCHER ("secondary-launcher",
                    OptionCategories.PROPERTY, () -> {
            context().secondaryLaunchers.add(
                new SecondaryLauncherArguments(popArg()));
        }),

        BUILD_ROOT ("build-root", OptionCategories.PROPERTY, () -> {
            context().buildRoot = popArg();
            context().userProvidedBuildRoot = true;
            setOptionValue("build-root", context().buildRoot);
        }),

        INSTALL_DIR ("install-dir", OptionCategories.PROPERTY),

        PREDEFINED_APP_IMAGE ("app-image", OptionCategories.PROPERTY, ()-> {
            setOptionValue("app-image", popArg());
            context().hasAppImage = true;
        }),

        PREDEFINED_RUNTIME_IMAGE ("runtime-image", OptionCategories.PROPERTY),

        MAIN_JAR ("main-jar", "j", OptionCategories.PROPERTY, () -> {
            context().mainJarPath = popArg();
            context().hasMainJar = true;
            setOptionValue("main-jar", context().mainJarPath);
        }),

        MODULE ("module", "m", OptionCategories.MODULAR, () -> {
            context().hasMainModule = true;
            setOptionValue("module", popArg());
        }),

        ADD_MODULES ("add-modules", OptionCategories.MODULAR),

        MODULE_PATH ("module-path", "p", OptionCategories.MODULAR),

        MAC_SIGN ("mac-sign", "s", OptionCategories.PLATFORM_MAC, () -> {
            setOptionValue("mac-sign", true);
        }),

        MAC_BUNDLE_NAME ("mac-bundle-name", OptionCategories.PLATFORM_MAC),

        MAC_BUNDLE_IDENTIFIER("mac-bundle-identifier",
                    OptionCategories.PLATFORM_MAC),

        MAC_APP_STORE_CATEGORY ("mac-app-store-category",
                    OptionCategories.PLATFORM_MAC),

        MAC_BUNDLE_SIGNING_PREFIX ("mac-bundle-signing-prefix",
                    OptionCategories.PLATFORM_MAC),

        MAC_SIGNING_KEY_NAME ("mac-signing-key-user-name",
                    OptionCategories.PLATFORM_MAC),

        MAC_SIGNING_KEYCHAIN ("mac-signing-keychain",
                    OptionCategories.PLATFORM_MAC),

        MAC_APP_STORE_ENTITLEMENTS ("mac-app-store-entitlements",
                    OptionCategories.PLATFORM_MAC),

        WIN_MENU_HINT ("win-menu", OptionCategories.PLATFORM_WIN, () -> {
            setOptionValue("win-menu", true);
        }),

        WIN_MENU_GROUP ("win-menu-group", OptionCategories.PLATFORM_WIN),

        WIN_SHORTCUT_HINT ("win-shortcut",
                OptionCategories.PLATFORM_WIN, () -> {
            setOptionValue("win-shortcut", true);
        }),

        WIN_PER_USER_INSTALLATION ("win-per-user-install",
                OptionCategories.PLATFORM_WIN, () -> {
            setOptionValue("win-per-user-install", false);
        }),

        WIN_DIR_CHOOSER ("win-dir-chooser",
                OptionCategories.PLATFORM_WIN, () -> {
            setOptionValue("win-dir-chooser", true);
        }),

        WIN_REGISTRY_NAME ("win-registry-name", OptionCategories.PLATFORM_WIN),

        WIN_UPGRADE_UUID ("win-upgrade-uuid",
                OptionCategories.PLATFORM_WIN),

        WIN_CONSOLE_HINT ("win-console", OptionCategories.PLATFORM_WIN, () -> {
            setOptionValue("win-console", true);
        }),

        LINUX_BUNDLE_NAME ("linux-bundle-name",
                OptionCategories.PLATFORM_LINUX),

        LINUX_DEB_MAINTAINER ("linux-deb-maintainer",
                OptionCategories.PLATFORM_LINUX),

        LINUX_RPM_LICENSE_TYPE ("linux-rpm-license-type",
                OptionCategories.PLATFORM_LINUX),

        LINUX_PACKAGE_DEPENDENCIES ("linux-package-deps",
                OptionCategories.PLATFORM_LINUX);

        private final String id;
        private final String shortId;
        private final OptionCategories category;
        private final ArgAction action;
        private static Arguments argContext;

        private CLIOptions(String id, OptionCategories category) {
            this(id, null, category, null);
        }

        private CLIOptions(String id, String shortId,
                           OptionCategories category) {
            this(id, shortId, category, null);
        }

        private CLIOptions(String id,
                OptionCategories category, ArgAction action) {
            this(id, null, category, action);
        }

        private CLIOptions(String id, String shortId,
                           OptionCategories category, ArgAction action) {
            this.id = id;
            this.shortId = shortId;
            this.action = action;
            this.category = category;
        }

        static void setContext(Arguments context) {
            argContext = context;
        }

        public static Arguments context() {
            if (argContext != null) {
                return argContext;
            } else {
                throw new RuntimeException("Argument context is not set.");
            }
        }

        public String getId() {
            return this.id;
        }

        String getIdWithPrefix() {
            String prefix = isMode() ? "create-" : "--";
            return prefix + this.id;
        }

        String getShortIdWithPrefix() {
            return this.shortId == null ? null : "-" + this.shortId;
        }

        void execute() {
            if (action != null) {
                action.execute();
            } else {
                defaultAction();
            }
        }

        boolean isMode() {
            return category == OptionCategories.MODE;
        }

        OptionCategories getCategory() {
            return category;
        }

        private void defaultAction() {
            context().deployParams.addBundleArgument(id, popArg());
        }

        private static void setOptionValue(String option, Object value) {
            context().deployParams.addBundleArgument(option, value);
        }

        private static String popArg() {
            nextArg();
            return (context().pos >= context().argList.size()) ?
                            "" : context().argList.get(context().pos);
        }

        private static String getArg() {
            return (context().pos >= context().argList.size()) ?
                        "" : context().argList.get(context().pos);
        }

        private static void nextArg() {
            context().pos++;
        }

        private static void prevArg() {
            context().pos--;
        }

        private static boolean hasNextArg() {
            return context().pos < context().argList.size();
        }
    }

    enum OptionCategories {
        MODE,
        MODULAR,
        PROPERTY,
        PLATFORM_MAC,
        PLATFORM_WIN,
        PLATFORM_LINUX;
    }

    private void initArgumentList(String[] args) throws PackagerException {
        argList = new ArrayList<String>(args.length);
        for (String arg : args) {
            if (arg.startsWith("@")) {
                if (arg.length() > 1) {
                    String filename = arg.substring(1);
                    argList.addAll(extractArgList(filename));
                } else {
                    throw new PackagerException("ERR_InvalidOption", arg);
                }
            } else {
                argList.add(arg);
            }
        }
        Log.debug ("\nJPackage argument list: \n" + argList + "\n");
        pos = 0;

        deployParams = new DeployParams();
        bundleType = BundlerType.NONE;

        allOptions = new ArrayList<>();

        secondaryLaunchers = new ArrayList<>();
    }

    private List<String> extractArgList(String filename) {
        List<String> args = new ArrayList<String>();
        try {
            File f = new File(filename);
            if (f.exists()) {
                List<String> lines = Files.readAllLines(f.toPath());
                for (String line : lines) {
                    String [] qsplit;
                    String quote = "\"";
                    if (line.contains("\"")) {
                        qsplit = line.split("\"");
                    } else {
                        qsplit = line.split("\'");
                        quote = "\'";
                    }
                    for (int i=0; i<qsplit.length; i++) {
                        // every other qsplit of line is a quoted string
                        if ((i & 1) == 0) {
                            // non-quoted string - split by whitespace
                            String [] newargs = qsplit[i].split("\\s");
                            for (String newarg : newargs) {
                                args.add(newarg);
                            }
                        } else {
                            // quoted string - don't split by whitespace
                            args.add(qsplit[i]);
                        }
                    }
                }
            } else {
                Log.info(MessageFormat.format(I18N.getString(
                        "warning.missing.arg.file"), f));
            }
        } catch (IOException ioe) {
            Log.verbose(ioe.getMessage());
            Log.verbose(ioe);
        }
        return args;
    }


    public boolean processArguments() throws Exception {
        try {

            // init context of arguments
            CLIOptions.setContext(this);

            // parse cmd line
            String arg;
            CLIOptions option;
            for (; CLIOptions.hasNextArg(); CLIOptions.nextArg()) {
                arg = CLIOptions.getArg();
                if ((option = toCLIOption(arg)) != null) {
                    // found a CLI option
                    allOptions.add(option);
                    option.execute();
                } else {
                    throw new PackagerException("ERR_InvalidOption", arg);
                }
            }

            if (allOptions.isEmpty() || !allOptions.get(0).isMode()) {
                // first argument should always be a mode.
                throw new PackagerException("ERR_MissingMode");
            }

            if (hasMainJar && !hasMainClass) {
                // try to get main-class from manifest
                String mainClass = getMainClassFromManifest();
                if (mainClass != null) {
                    CLIOptions.setOptionValue(
                            CLIOptions.APPCLASS.getId(), mainClass);
                }
            }

            // display warning for arguments that are not supported
            // for current configuration.

            validateArguments();

            addResources(deployParams, input, files);

            deployParams.setBundleType(bundleType);

            List<Map<String, ? super Object>> launchersAsMap =
                    new ArrayList<>();

            for (SecondaryLauncherArguments sl : secondaryLaunchers) {
                launchersAsMap.add(sl.getLauncherMap());
            }

            deployParams.addBundleArgument(
                    StandardBundlerParam.SECONDARY_LAUNCHERS.getID(),
                    launchersAsMap);

            // at this point deployParams should be already configured

            deployParams.validate();

            BundleParams bp = deployParams.getBundleParams();

            // validate name(s)
            ArrayList<String> usedNames = new ArrayList<String>();
            usedNames.add(bp.getName()); // add main app name

            for (SecondaryLauncherArguments sl : secondaryLaunchers) {
                Map<String, ? super Object> slMap = sl.getLauncherMap();
                String slName =
                        (String) slMap.get(Arguments.CLIOptions.NAME.getId());
                if (slName == null) {
                    throw new PackagerException("ERR_NoSecondaryLauncherName");
                }
                // same rules apply to secondary launcher names as app name
                DeployParams.validateName(slName, false);
                for (String usedName : usedNames) {
                    if (slName.equals(usedName)) {
                        throw new PackagerException("ERR_NoUniqueName");
                    }
                }
                usedNames.add(slName);
            }
            if (runtimeInstaller && bp.getName() == null) {
                throw new PackagerException("ERR_NoJreInstallerName");
            }

            return generateBundle(bp.getBundleParamsAsMap());
        } catch (Exception e) {
            if (Log.isVerbose()) {
                throw e;
            } else {
                Log.error(e.getMessage());
                if (e.getCause() != null && e.getCause() != e) {
                    Log.error(e.getCause().getMessage());
                }
                return false;
            }
        }
    }

    private void validateArguments() {
        CLIOptions mode = allOptions.get(0);
        for (CLIOptions option : allOptions) {
            if(!ValidOptions.checkIfSupported(mode, option)) {
                Log.info(MessageFormat.format(I18N.getString(
                        "warning.unsupported.option"), option.getId(), mode));
            }
        }
    }

    private List<jdk.jpackage.internal.Bundler> getPlatformBundlers() {

        if (platformBundlers != null) {
            return platformBundlers;
        }

        platformBundlers = new ArrayList<>();
        for (jdk.jpackage.internal.Bundler bundler :
                Bundlers.createBundlersInstance().getBundlers(
                        bundleType.toString())) {
            if (hasTargetFormat && deployParams.getTargetFormat() != null &&
                    !deployParams.getTargetFormat().equalsIgnoreCase(
                    bundler.getID())) {
                continue;
            }
            if (bundler.supported(runtimeInstaller)) {
                 platformBundlers.add(bundler);
            }
        }

        return platformBundlers;
    }

    private boolean generateBundle(Map<String,? super Object> params)
            throws PackagerException {

        boolean bundleCreated = false;

        // the build-root needs to be fetched from the params early,
        // to prevent each copy of the params (such as may be used for
        // secondary launchers) from generating a separate build-root when
        // the default is used (the default is a new temp directory)
        // The bundler.cleanup() below would not otherwise be able to
        // clean these extra (and unneeded) temp directories.
        StandardBundlerParam.BUILD_ROOT.fetchFrom(params);

        for (jdk.jpackage.internal.Bundler bundler : getPlatformBundlers()) {
            Map<String, ? super Object> localParams = new HashMap<>(params);
            try {
                if (bundler.validate(localParams)) {
                    File result =
                            bundler.execute(localParams, deployParams.outdir);
                    if (!userProvidedBuildRoot) {
                        bundler.cleanup(localParams);
                    }
                    if (result == null) {
                        throw new PackagerException("MSG_BundlerFailed",
                                bundler.getID(), bundler.getName());
                    }
                    bundleCreated = true; // at least one bundle was created
                }
            } catch (UnsupportedPlatformException e) {
                throw new PackagerException(e,
                        "MSG_BundlerPlatformException",
                        bundler.getName());
            } catch (ConfigException e) {
                Log.debug(e);
                if (e.getAdvice() != null) {
                    throw new PackagerException(e,
                            "MSG_BundlerConfigException",
                            bundler.getName(), e.getMessage(), e.getAdvice());
                } else {
                    throw new PackagerException(e,
                           "MSG_BundlerConfigExceptionNoAdvice",
                            bundler.getName(), e.getMessage());
                }
            } catch (RuntimeException re) {
                Log.debug(re);
                throw new PackagerException(re, "MSG_BundlerRuntimeException",
                        bundler.getName(), re.toString());
            } finally {
                if (userProvidedBuildRoot) {
                    Log.verbose(MessageFormat.format(
                            I18N.getString("message.debug-working-directory"),
                            (new File(buildRoot)).getAbsolutePath()));
                }
            }
        }

        return bundleCreated;
    }

    private void addResources(DeployParams deployParams,
            String inputdir, List<String> inputfiles) {

        if (inputdir == null || inputdir.isEmpty()) {
            return;
        }

        File baseDir = new File(inputdir);

        if (!baseDir.isDirectory()) {
            Log.error(
                    "Unable to add resources: \"--input\" is not a directory.");
            return;
        }

        List<String> fileNames;
        if (inputfiles != null) {
            fileNames = inputfiles;
        } else {
            // "-files" is omitted, all files in input cdir (which
            // is a mandatory argument in this case) will be packaged.
            fileNames = new ArrayList<>();
            try (Stream<Path> files = Files.list(baseDir.toPath())) {
                files.forEach(file -> fileNames.add(
                        file.getFileName().toString()));
            } catch (IOException e) {
                Log.error("Unable to add resources: " + e.getMessage());
            }
        }
        fileNames.forEach(file -> deployParams.addResource(baseDir, file));

        deployParams.setClasspath();
    }

    static boolean isCLIOption(String arg) {
        return toCLIOption(arg) != null;
    }

    static CLIOptions toCLIOption(String arg) {
        CLIOptions option;
        if ((option = argIds.get(arg)) == null) {
            option = argShortIds.get(arg);
        }
        return option;
    }

    static Map<String, String> getArgumentMap(String inputString) {
        Map<String, String> map = new HashMap<>();
        List<String> list = getArgumentList(inputString);
        for (String pair : list) {
            int equals = pair.indexOf("=");
            if (equals != -1) {
                String key = pair.substring(0, equals);
                String value = pair.substring(equals+1, pair.length());
                map.put(key, value);
            }
        }
        return map;
    }

    static Map<String, String> getPropertiesFromFile(String filename) {
        Map<String, String> map = new HashMap<>();
        // load properties file
        File file = new File(filename);
        Properties properties = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            properties.load(in);
        } catch (IOException e) {
            Log.error("Exception: " + e.getMessage());
        }

        for (final String name: properties.stringPropertyNames()) {
            map.put(name, properties.getProperty(name));
        }

        return map;
    }

    static List<String> getArgumentList(String inputString) {
        List<String> list = new ArrayList<>();
        if (inputString == null || inputString.isEmpty()) {
             return list;
        }

        // The "pattern" regexp attempts to abide to the rule that
        // strings are delimited by whitespace unless surrounded by
        // quotes, then it is anything (including spaces) in the quotes.
        Matcher m = pattern.matcher(inputString);
        while (m.find()) {
            String s = inputString.substring(m.start(), m.end()).trim();
            // Ensure we do not have an empty string. trim() will take care of
            // whitespace only strings. The regex preserves quotes and escaped
            // chars so we need to clean them before adding to the List
            if (!s.isEmpty()) {
                list.add(unquoteIfNeeded(s));
            }
        }
        return list;
    }

    private static String unquoteIfNeeded(String in) {
        if (in == null) {
            return null;
        }

        if (in.isEmpty()) {
            return "";
        }

        // Use code points to preserve non-ASCII chars
        StringBuilder sb = new StringBuilder();
        int codeLen = in.codePointCount(0, in.length());
        int quoteChar = -1;
        for (int i = 0; i < codeLen; i++) {
            int code = in.codePointAt(i);
            if (code == '"' || code == '\'') {
                // If quote is escaped make sure to copy it
                if (i > 0 && in.codePointAt(i - 1) == '\\') {
                    sb.deleteCharAt(sb.length() - 1);
                    sb.appendCodePoint(code);
                    continue;
                }
                if (quoteChar != -1) {
                    if (code == quoteChar) {
                        // close quote, skip char
                        quoteChar = -1;
                    } else {
                        sb.appendCodePoint(code);
                    }
                } else {
                    // opening quote, skip char
                    quoteChar = code;
                }
            } else {
                sb.appendCodePoint(code);
            }
        }
        return sb.toString();
    }

    private String getMainClassFromManifest() {
        if (mainJarPath == null ||
            input == null ) {
            return null;
        }

        JarFile jf;
        try {
            File file = new File(input, mainJarPath);
            if (!file.exists()) {
                return null;
            }
            jf = new JarFile(file);
            Manifest m = jf.getManifest();
            Attributes attrs = (m != null) ? m.getMainAttributes() : null;
            if (attrs != null) {
                return attrs.getValue(Attributes.Name.MAIN_CLASS);
            }
        } catch (IOException ignore) {}
        return null;
    }

}
