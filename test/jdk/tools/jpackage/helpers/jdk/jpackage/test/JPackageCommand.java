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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

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

    public JPackageCommand removeArgument(String argName) {
        return setArgumentValue(argName, (String)null);
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
        return getArgumentValue("--package-type",
                () -> PackageType.DEFAULT,
                (v) -> PACKAGE_TYPES.get(v));
    }

    public Path outputDir() {
        return getArgumentValue("--dest", () -> Test.defaultOutputDir(), Path::of);
    }

    public Path inputDir() {
        return getArgumentValue("--input", () -> Test.defaultInputDir(),Path::of);
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
        addArguments("--input", Test.defaultInputDir());
        addArguments("--dest", Test.defaultOutputDir());
        return this;
    }

    public JPackageCommand setFakeRuntime() {
        verifyMutable();

        try {
            Path fakeRuntimeDir = Test.workDir().resolve("fake_runtime");
            Files.createDirectories(fakeRuntimeDir);

            if (Test.isWindows() || Test.isLinux()) {
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

    JPackageCommand addAction(Consumer<JPackageCommand> action) {
        verifyMutable();
        actions.add(action);
        return this;
    }

    public static JPackageCommand helloAppImage() {
        JPackageCommand cmd = new JPackageCommand();
        cmd.setDefaultInputOutput().setDefaultAppName();
        PackageType.IMAGE.applyTo(cmd);
        HelloApp.addTo(cmd);
        return cmd;
    }

    public JPackageCommand setPackageType(PackageType type) {
        verifyMutable();
        type.applyTo(this);
        return this;
    }

    JPackageCommand setDefaultAppName() {
        addArguments("--name", Test.enclosingMainMethodClass().getSimpleName());
        return this;
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

        throw new IllegalArgumentException("Unexpected package type");
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
        return appInstallationDirectory().resolve("runtime");
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

        throw new IllegalArgumentException("Unexpected package type");
    }

    /**
     * Returns path to application image directory.
     *
     * E.g. if --dest is set to `foo` and --name is set to `bar` the function
     * will return `foo/bar` path.
     *
     * @throws IllegalArgumentException is command is doing platform packaging
     */
    public Path appImage() {
        final PackageType type = packageType();
        if (PackageType.IMAGE != type) {
            throw new IllegalArgumentException("Unexpected package type");
        }

        return outputDir().resolve(name());
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
        final PackageType type = packageType();
        if (PackageType.IMAGE != type) {
            throw new IllegalArgumentException("Unexpected package type");
        }

        if (Test.isLinux()) {
            return Path.of("bin", name());
        }

        if (Test.isOSX()) {
            return Path.of("Contents", "MacOS", name());
        }

        if (Test.isWindows()) {
            return Path.of(name() + ".exe");
        }

        throw new IllegalArgumentException("Unexpected package type");
    }

    /**
     * Returns path to runtime directory relative to image directory.
     *
     * Function will always return "runtime".
     *
     * @throws IllegalArgumentException if command is configured for platform
     * packaging
     */
    public Path appRuntimeDirectoryInAppImage() {
        final PackageType type = packageType();
        if (PackageType.IMAGE != type) {
            throw new IllegalArgumentException("Unexpected package type");
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
        final List<Path> criticalRuntimeFiles;
        if (Test.isWindows()) {
            criticalRuntimeFiles = List.of(Path.of("server\\jvm.dll"));
        } else if (Test.isLinux()) {
            criticalRuntimeFiles = List.of(Path.of("server/libjvm.so"));
        } else if (Test.isOSX()) {
            criticalRuntimeFiles = List.of(Path.of("server/libjvm.dylib"));
        } else {
            throw new IllegalArgumentException("Unknwon platform");
        }

        if (criticalRuntimeFiles.stream().filter(v -> v.toFile().exists())
                .findFirst().orElse(null) == null) {
            // Fake runtime
            Test.trace(String.format(
                    "%s because application runtime directory [%s] is incomplete",
                    msg, runtimeDir));
            return true;
        }
        return false;
    }

    public Executor.Result execute() {
        verifyMutable();
        if (actions != null) {
            actions.stream().forEach(r -> r.accept(this));
        }

        return new Executor()
                .setExecutable(JavaTool.JPACKAGE)
                .dumpOtput()
                .addArguments(new JPackageCommand().addArguments(
                                args).adjustArgumentsBeforeExecution().args)
                .execute();
    }

    private JPackageCommand adjustArgumentsBeforeExecution() {
        if (!hasArgument("--runtime-image") && !hasArgument("--app-image") && DEFAULT_RUNTIME_IMAGE != null) {
            addArguments("--runtime-image", DEFAULT_RUNTIME_IMAGE);
        }

        if (!hasArgument("--verbose") && Test.VERBOSE_JPACKAGE) {
            addArgument("--verbose");
        }

        return this;
    }

    String getPrintableCommandLine() {
        return new Executor()
                .setExecutable(JavaTool.JPACKAGE)
                .addArguments(args)
                .getPrintableCommandLine();
    }

    void verifyIsOfType(Collection<PackageType> types) {
        verifyIsOfType(types.toArray(PackageType[]::new));
    }

    void verifyIsOfType(PackageType ... types) {
        if (!Arrays.asList(types).contains(packageType())) {
            throw new IllegalArgumentException("Unexpected package type");
        }
    }

    @Override
    protected boolean isMutable() {
        return !immutable;
    }

    private final List<Consumer<JPackageCommand>> actions;
    private boolean immutable;

    private final static Map<String, PackageType> PACKAGE_TYPES
            = new Supplier<Map<String, PackageType>>() {
                @Override
                public Map<String, PackageType> get() {
                    Map<String, PackageType> reply = new HashMap<>();
                    for (PackageType type : PackageType.values()) {
                        reply.put(type.getName(), type);
                    }
                    return reply;
                }
            }.get();

    public final static Path DEFAULT_RUNTIME_IMAGE;

    static {
        // Set the property to the path of run-time image to speed up
        // building app images and platform bundles by avoiding running jlink
        // The value of the property will be automativcally appended to
        // jpackage command line if the command line doesn't have
        // `--runtime-image` parameter set.
        String val = Test.getConfigProperty("runtime-image");
        if (val != null) {
            DEFAULT_RUNTIME_IMAGE = Path.of(val);
        } else {
            DEFAULT_RUNTIME_IMAGE = null;
        }
    }
}
