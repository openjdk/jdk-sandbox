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

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.*;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import jdk.jpackage.test.Functional.ThrowingConsumer;

/**
 * jpackage command line with prerequisite actions. Prerequisite actions can be
 * anything. The simplest is to compile test application and pack in a jar for
 * use on jpackage command line.
 */
public final class JPackageCommand extends CommandArguments<JPackageCommand> {

    public JPackageCommand() {
        actions = new ArrayList<>();
    }

    JPackageCommand createImmutableCopy() {
        JPackageCommand reply = new JPackageCommand();
        reply.immutable = true;
        reply.args.addAll(args);
        return reply;
    }

    public JPackageCommand setArgumentValue(String argName, String newValue) {
        verifyMutable();

        String prevArg = null;
        ListIterator<String> it = args.listIterator();
        while (it.hasNext()) {
            String value = it.next();
            if (prevArg != null && prevArg.equals(argName)) {
                if (newValue != null) {
                    it.set(newValue);
                } else {
                    it.remove();
                    it.previous();
                    it.remove();
                }
                return this;
            }
            prevArg = value;
        }

        if (newValue != null) {
            addArguments(argName, newValue);
        }

        return this;
    }

    public JPackageCommand setArgumentValue(String argName, Path newValue) {
        return setArgumentValue(argName, newValue.toString());
    }

    public JPackageCommand removeArgumentWithValue(String argName) {
        return setArgumentValue(argName, (String)null);
    }

    public JPackageCommand removeArgument(String argName) {
        args = args.stream().filter(arg -> !arg.equals(argName)).collect(
                Collectors.toList());
        return this;
    }

    public boolean hasArgument(String argName) {
        return args.contains(argName);
    }

    public <T> T getArgumentValue(String argName,
            Function<JPackageCommand, T> defaultValueSupplier,
            Function<String, T> stringConverter) {
        String prevArg = null;
        for (String arg : args) {
            if (prevArg != null && prevArg.equals(argName)) {
                return stringConverter.apply(arg);
            }
            prevArg = arg;
        }
        if (defaultValueSupplier != null) {
            return defaultValueSupplier.apply(this);
        }
        return null;
    }

    public String getArgumentValue(String argName,
            Function<JPackageCommand, String> defaultValueSupplier) {
        return getArgumentValue(argName, defaultValueSupplier, v -> v);
    }

    public <T> T getArgumentValue(String argName,
            Supplier<T> defaultValueSupplier,
            Function<String, T> stringConverter) {
        return getArgumentValue(argName, (unused) -> defaultValueSupplier.get(),
                stringConverter);
    }

    public String getArgumentValue(String argName,
            Supplier<String> defaultValueSupplier) {
        return getArgumentValue(argName, defaultValueSupplier, v -> v);
    }

    public String getArgumentValue(String argName) {
        return getArgumentValue(argName, (Supplier<String>)null);
    }

    public String[] getAllArgumentValues(String argName) {
        List<String> values = new ArrayList<>();
        String prevArg = null;
        for (String arg : args) {
            if (prevArg != null && prevArg.equals(argName)) {
                values.add(arg);
            }
            prevArg = arg;
        }
        return values.toArray(String[]::new);
    }

    public JPackageCommand addArguments(String name, Path value) {
        return addArguments(name, value.toString());
    }

    public PackageType packageType() {
        // Don't try to be in sync with jpackage defaults. Keep it simple:
        // if no `--package-type` explicitely set on the command line, consider
        // this is operator's fault.
        return getArgumentValue("--package-type",
                () -> {
                    throw new IllegalStateException("Package type not set");
                }, PACKAGE_TYPES::get);
    }

    public Path outputDir() {
        return getArgumentValue("--dest", () -> Path.of("."), Path::of);
    }

    public Path inputDir() {
        return getArgumentValue("--input", () -> null, Path::of);
    }

    public String version() {
        return getArgumentValue("--app-version", () -> "1.0");
    }

    public String name() {
        return getArgumentValue("--name", () -> getArgumentValue("--main-class"));
    }

    public boolean isRuntime() {
        return  hasArgument("--runtime-image")
                && !hasArgument("--main-jar")
                && !hasArgument("--module")
                && !hasArgument("--app-image");
    }

    public JPackageCommand setDefaultInputOutput() {
        addArguments("--input", TKit.defaultInputDir());
        addArguments("--dest", TKit.defaultOutputDir());
        return this;
    }

    public JPackageCommand setFakeRuntime() {
        verifyMutable();

        try {
            Path fakeRuntimeDir = TKit.workDir().resolve("fake_runtime");
            Files.createDirectories(fakeRuntimeDir);

            if (TKit.isWindows() || TKit.isLinux()) {
                // Needed to make WindowsAppBundler happy as it copies MSVC dlls
                // from `bin` directory.
                // Need to make the code in rpm spec happy as it assumes there is
                // always something in application image.
                fakeRuntimeDir.resolve("bin").toFile().mkdir();
            }

            Path bulk = fakeRuntimeDir.resolve(Path.of("bin", "bulk"));

            // Mak sure fake runtime takes some disk space.
            // Package bundles with 0KB size are unexpected and considered
            // an error by PackageTest.
            Files.createDirectories(bulk.getParent());
            try (FileOutputStream out = new FileOutputStream(bulk.toFile())) {
                byte[] bytes = new byte[4 * 1024];
                new SecureRandom().nextBytes(bytes);
                out.write(bytes);
            }

            addArguments("--runtime-image", fakeRuntimeDir);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        return this;
    }

    JPackageCommand addAction(ThrowingConsumer<JPackageCommand> action) {
        verifyMutable();
        actions.add(ThrowingConsumer.toConsumer(action));
        return this;
    }

    public static JPackageCommand helloAppImage() {
        return helloAppImage(null);
    }

    /**
     * Creates new JPackageCommand instance configured with the test Java app.
     * For the explanation of `javaAppDesc` parameter, see documentation for
     * HelloApp.addTo() method.
     *
     * @param javaAppDesc Java application description
     * @return this
     */
    public static JPackageCommand helloAppImage(String javaAppDesc) {
        JPackageCommand cmd = new JPackageCommand();
        cmd.setDefaultInputOutput().setDefaultAppName();
        PackageType.IMAGE.applyTo(cmd);
        HelloApp.addTo(cmd, javaAppDesc);
        return cmd;
    }

    public JPackageCommand setPackageType(PackageType type) {
        verifyMutable();
        type.applyTo(this);
        return this;
    }

    JPackageCommand setDefaultAppName() {
        return addArguments("--name", TKit.getCurrentDefaultAppName());
    }

    public Path outputBundle() {
        final PackageType type = packageType();
        if (PackageType.IMAGE == type) {
            return null;
        }

        String bundleName = null;
        if (PackageType.LINUX.contains(type)) {
            bundleName = LinuxHelper.getBundleName(this);
        } else if (PackageType.WINDOWS.contains(type)) {
            bundleName = WindowsHelper.getBundleName(this);
        } else if (PackageType.MAC.contains(type)) {
            bundleName = MacHelper.getBundleName(this);
        }

        return outputDir().resolve(bundleName);
    }

    /**
     * Returns path to directory where application will be installed.
     *
     * E.g. on Linux for app named Foo default the function will return
     * `/opt/foo`
     */
    public Path appInstallationDirectory() {
        final PackageType type = packageType();
        if (PackageType.IMAGE == type) {
            return null;
        }

        if (PackageType.LINUX.contains(type)) {
            if (isRuntime()) {
                // Not fancy, but OK.
                return Path.of(getArgumentValue("--install-dir", () -> "/opt"),
                        LinuxHelper.getPackageName(this));
            }

            // Launcher is in "bin" subfolder of the installation directory.
            return launcherInstallationPath().getParent().getParent();
        }

        if (PackageType.WINDOWS.contains(type)) {
            return WindowsHelper.getInstallationDirectory(this);
        }

        if (PackageType.MAC.contains(type)) {
            return MacHelper.getInstallationDirectory(this);
        }

        throw throwUnexpectedPackageTypeError();
    }

    /**
     * Returns path where application's Java runtime will be installed.
     * If the command will package Java run-time only, still returns path to
     * runtime subdirectory.
     *
     * E.g. on Linux for app named `Foo` the function will return
     * `/opt/foo/runtime`
     */
    public Path appRuntimeInstallationDirectory() {
        if (PackageType.IMAGE == packageType()) {
            return null;
        }
        return appInstallationDirectory().resolve(appRuntimePath(packageType()));
    }

    /**
     * Returns path where application launcher will be installed.
     * If the command will package Java run-time only, still returns path to
     * application launcher.
     *
     * E.g. on Linux for app named Foo default the function will return
     * `/opt/foo/bin/Foo`
     */
    public Path launcherInstallationPath() {
        final PackageType type = packageType();
        if (PackageType.IMAGE == type) {
            return null;
        }

        if (PackageType.LINUX.contains(type)) {
            return outputDir().resolve(LinuxHelper.getLauncherPath(this));
        }

        if (PackageType.WINDOWS.contains(type)) {
            return appInstallationDirectory().resolve(name() + ".exe");
        }

        if (PackageType.MAC.contains(type)) {
            return appInstallationDirectory().resolve(Path.of("Contents", "MacOS", name()));
        }

        throw throwUnexpectedPackageTypeError();
    }

    /**
     * Returns path to application image directory.
     *
     * E.g. if --dest is set to `foo` and --name is set to `bar` the function
     * will return `foo/bar` path on Linux and Windows and `foo/bar.app` on macOS.
     *
     * @throws IllegalArgumentException is command is doing platform packaging
     */
    public Path appImage() {
        verifyIsOfType(PackageType.IMAGE);
        String dirName = name();
        if (TKit.isOSX()) {
            dirName = dirName + ".app";
        }
        return outputDir().resolve(dirName);
    }

    /**
     * Returns path to application launcher relative to image directory.
     *
     * E.g. if --name is set to `Foo` the function will return `bin/Foo` path on
     * Linux, and `Foo.exe` on Windows.
     *
     * @throws IllegalArgumentException is command is doing platform packaging
     */
    public Path launcherPathInAppImage() {
        verifyIsOfType(PackageType.IMAGE);

        if (TKit.isLinux()) {
            return Path.of("bin", name());
        }

        if (TKit.isOSX()) {
            return Path.of("Contents", "MacOS", name());
        }

        if (TKit.isWindows()) {
            return Path.of(name() + ".exe");
        }

        throw TKit.throwUnknownPlatformError();
    }

    /**
     * Returns path to runtime directory relative to image directory.
     *
     * @throws IllegalArgumentException if command is configured for platform
     * packaging
     */
    public Path appRuntimeDirectoryInAppImage() {
        verifyIsOfType(PackageType.IMAGE);
        return appRuntimePath(packageType());
    }

    private static Path appRuntimePath(PackageType type) {
        if (TKit.isLinux()) {
            return Path.of("lib/runtime");
        }
        if (TKit.isOSX()) {
            return Path.of("Contents/runtime");
        }

        return Path.of("runtime");
    }

    public boolean isFakeRuntimeInAppImage(String msg) {
        return isFakeRuntime(appImage().resolve(
                appRuntimeDirectoryInAppImage()), msg);
    }

    public boolean isFakeRuntimeInstalled(String msg) {
        return isFakeRuntime(appRuntimeInstallationDirectory(), msg);
    }

    private static boolean isFakeRuntime(Path runtimeDir, String msg) {
        final Collection<Path> criticalRuntimeFiles;
        if (TKit.isWindows()) {
            criticalRuntimeFiles = WindowsHelper.CRITICAL_RUNTIME_FILES;
        } else if (TKit.isLinux()) {
            criticalRuntimeFiles = LinuxHelper.CRITICAL_RUNTIME_FILES;
        } else if (TKit.isOSX()) {
            criticalRuntimeFiles = MacHelper.CRITICAL_RUNTIME_FILES;
        } else {
            throw TKit.throwUnknownPlatformError();
        }

        if (criticalRuntimeFiles.stream().filter(
                v -> runtimeDir.resolve(v).toFile().exists()).findFirst().orElse(
                        null) == null) {
            // Fake runtime
            TKit.trace(String.format(
                    "%s because application runtime directory [%s] is incomplete",
                    msg, runtimeDir));
            return true;
        }
        return false;
    }

    public static void useToolProviderByDefault() {
        defaultWithToolProvider = true;
    }

    public static void useExecutableByDefault() {
        defaultWithToolProvider = false;
    }

    public JPackageCommand useToolProvider(boolean v) {
        verifyMutable();
        withToolProvider = v;
        return this;
    }

    public JPackageCommand saveConsoleOutput(boolean v) {
        verifyMutable();
        saveConsoleOutput = v;
        return this;
    }

    public JPackageCommand dumpOutput(boolean v) {
        verifyMutable();
        suppressOutput = !v;
        return this;
    }

    public boolean isWithToolProvider() {
        return Optional.ofNullable(withToolProvider).orElse(
                defaultWithToolProvider);
    }

    public void executePrerequisiteActions() {
        verifyMutable();
        if (!actionsExecuted) {
            actionsExecuted = true;
            if (actions != null) {
                actions.stream().forEach(r -> r.accept(this));
            }
        }
    }

    public Executor.Result execute() {
        executePrerequisiteActions();

        if (packageType() == PackageType.IMAGE) {
            TKit.deleteDirectoryContentsRecursive(outputDir());
        }

        Executor exec = new Executor()
                .saveOutput(saveConsoleOutput).dumpOutput(!suppressOutput)
                .addArguments(new JPackageCommand().addArguments(
                                args).adjustArgumentsBeforeExecution().args);

        if (isWithToolProvider()) {
            exec.setToolProvider(JavaTool.JPACKAGE.asToolProvider());
        } else {
            exec.setExecutable(JavaTool.JPACKAGE);
        }
        return exec.execute();
    }

    public JPackageCommand executeAndAssertHelloAppImageCreated() {
        executeAndAssertImageCreated();
        HelloApp.executeLauncherAndVerifyOutput(this);
        return this;
    }

    public JPackageCommand executeAndAssertImageCreated() {
        execute().assertExitCodeIsZero();
        return assertImageCreated();
    }

    public JPackageCommand assertImageCreated() {
        verifyIsOfType(PackageType.IMAGE);
        TKit.assertExecutableFileExists(appImage().resolve(
                launcherPathInAppImage()));
        TKit.assertDirectoryExists(appImage().resolve(
                appRuntimeDirectoryInAppImage()));
        return this;
    }

    private JPackageCommand adjustArgumentsBeforeExecution() {
        if (!hasArgument("--runtime-image") && !hasArgument("--app-image") && DEFAULT_RUNTIME_IMAGE != null) {
            addArguments("--runtime-image", DEFAULT_RUNTIME_IMAGE);
        }

        if (!hasArgument("--verbose") && TKit.VERBOSE_JPACKAGE) {
            addArgument("--verbose");
        }

        return this;
    }

    private static RuntimeException throwUnexpectedPackageTypeError() {
        throw new IllegalArgumentException("Unexpected package type");
    }

    String getPrintableCommandLine() {
        return new Executor()
                .setExecutable(JavaTool.JPACKAGE)
                .addArguments(args)
                .getPrintableCommandLine();
    }

    public void verifyIsOfType(Collection<PackageType> types) {
        verifyIsOfType(types.toArray(PackageType[]::new));
    }

    public void verifyIsOfType(PackageType ... types) {
        if (!Arrays.asList(types).contains(packageType())) {
            throwUnexpectedPackageTypeError();
        }
    }

    public static String escapeAndJoin(String... args) {
        return escapeAndJoin(List.of(args));
    }

    public static String escapeAndJoin(List<String> args) {
        Pattern whitespaceRegexp = Pattern.compile("\\s");

        return args.stream().map(v -> {
            String str = v;
            // Escape quotes.
            str = str.replace("\"", "\\\"");
            // Escape backslashes.
            str = str.replace("\\", "\\\\");
            // If value contains whitespace characters, put the value in quotes
            if (whitespaceRegexp.matcher(str).find()) {
                str = "\"" + str + "\"";
            }
            return str;
        }).collect(Collectors.joining(" "));
    }

    public static Path relativePathInRuntime(JavaTool tool) {
        Path path = tool.relativePathInJavaHome();
        if (TKit.isOSX()) {
            path = Path.of("Contents/Home").resolve(path);
        }
        return path;
    }

    public static Stream<String> filterOutput(Stream<String> jpackageOutput) {
        // Skip "WARNING: Using experimental tool jpackage" first line of output
        return jpackageOutput.skip(1);
    }

    public static List<String> filterOutput(List<String> jpackageOutput) {
        return filterOutput(jpackageOutput.stream()).collect(Collectors.toList());
    }

    @Override
    protected boolean isMutable() {
        return !immutable;
    }

    private Boolean withToolProvider;
    private boolean saveConsoleOutput;
    private boolean suppressOutput;
    private boolean immutable;
    private boolean actionsExecuted;
    private final List<Consumer<JPackageCommand>> actions;
    private static boolean defaultWithToolProvider;

    private final static Map<String, PackageType> PACKAGE_TYPES = Functional.identity(
            () -> {
                Map<String, PackageType> reply = new HashMap<>();
                for (PackageType type : PackageType.values()) {
                    reply.put(type.getName(), type);
                }
                return reply;
            }).get();

    public final static Path DEFAULT_RUNTIME_IMAGE = Functional.identity(() -> {
        // Set the property to the path of run-time image to speed up
        // building app images and platform bundles by avoiding running jlink
        // The value of the property will be automativcally appended to
        // jpackage command line if the command line doesn't have
        // `--runtime-image` parameter set.
        String val = TKit.getConfigProperty("runtime-image");
        if (val != null) {
            return Path.of(val);
        }
        return null;
    }).get();
}
