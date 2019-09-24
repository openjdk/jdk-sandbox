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

import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static jdk.jpackage.test.PackageType.LINUX_DEB;
import static jdk.jpackage.test.PackageType.LINUX_RPM;

/**
 * Instance of PackageTest is for configuring and running a single jpackage
 * command to produce platform specific package bundle.
 *
 * Provides methods hook up custom configuration of jpackage command and
 * verification of the output bundle.
 */
public final class PackageTest {

    /**
     * Default test configuration for jpackage command. Default jpackage command
     * initialization includes:
     * <li>Set --input and --dest parameters.
     * <li>Set --name parameter. Value of the parameter is the name of the first
     * class with main function found in the callers stack. Defaults can be
     * overridden with custom initializers set with subsequent addInitializer()
     * function calls.
     */
    public PackageTest() {
        action = DEFAULT_ACTION;
        forTypes();
        setJPackageExitCode(0);
        handlers = new HashMap<>();
        namedInitializers = new HashSet<>();
        currentTypes.forEach(v -> handlers.put(v, new Handler(v)));
    }

    public PackageTest forTypes(PackageType... types) {
        Collection<PackageType> newTypes;
        if (types == null || types.length == 0) {
            newTypes = PackageType.NATIVE;
        } else {
            newTypes = Set.of(types);
        }
        currentTypes = newTypes.stream().filter(type -> type.isSupported()).collect(
                Collectors.toUnmodifiableSet());
        return this;
    }

    public PackageTest forTypes(Collection<PackageType> types) {
        return forTypes(types.toArray(PackageType[]::new));
    }

    public PackageTest setJPackageExitCode(int v) {
        expectedJPackageExitCode = v;
        return this;
    }

    private PackageTest addInitializer(Consumer<JPackageCommand> v, String id) {
        if (id != null) {
            if (namedInitializers.contains(id)) {
                return this;
            }

            namedInitializers.add(id);
        }
        currentTypes.stream().forEach(type -> handlers.get(type).addInitializer(
                v));
        return this;
    }

    public PackageTest addInitializer(Consumer<JPackageCommand> v) {
        return addInitializer(v, null);
    }

    public PackageTest addBundleVerifier(
            BiConsumer<JPackageCommand, Executor.Result> v) {
        currentTypes.stream().forEach(
                type -> handlers.get(type).addBundleVerifier(v));
        return this;
    }

    public PackageTest addBundleVerifier(Consumer<JPackageCommand> v) {
        return addBundleVerifier((cmd, unused) -> v.accept(cmd));
    }

    public PackageTest addBundlePropertyVerifier(String propertyName,
            BiConsumer<String, String> pred) {
        return addBundleVerifier(cmd -> {
            String propertyValue = null;
            switch (cmd.packageType()) {
                case LINUX_DEB:
                    propertyValue = LinuxHelper.getDebBundleProperty(
                            cmd.outputBundle(), propertyName);
                    break;

                case LINUX_RPM:
                    propertyValue = LinuxHelper.getRpmBundleProperty(
                            cmd.outputBundle(), propertyName);
                    break;

                default:
                    throw new UnsupportedOperationException();
            }

            pred.accept(propertyName, propertyValue);
        });
    }

    public PackageTest addBundlePropertyVerifier(String propertyName,
            String expectedPropertyValue) {
        return addBundlePropertyVerifier(propertyName, (unused, v) -> {
            Test.assertEquals(expectedPropertyValue, v, String.format(
                    "Check value of %s property is [%s]", propertyName, v));
        });
    }

    public PackageTest addBundleDesktopIntegrationVerifier(boolean integrated) {
        forTypes(LINUX_DEB, () -> {
            LinuxHelper.addDebBundleDesktopIntegrationVerifier(this, integrated);
        });
        return this;
    }

    public PackageTest addInstallVerifier(Consumer<JPackageCommand> v) {
        currentTypes.stream().forEach(
                type -> handlers.get(type).addInstallVerifier(v));
        return this;
    }

    public PackageTest addUninstallVerifier(Consumer<JPackageCommand> v) {
        currentTypes.stream().forEach(
                type -> handlers.get(type).addUninstallVerifier(v));
        return this;
    }

    public PackageTest addHelloAppFileAssociationsVerifier(FileAssociations fa,
            String... faLauncherDefaultArgs) {

        addInitializer(cmd -> HelloApp.addTo(cmd), "HelloApp");
        addInstallVerifier(cmd -> {
            if (cmd.isFakeRuntimeInstalled(
                    "Not running file associations test")) {
                return;
            }

            Test.withTempFile(fa.getSuffix(), testFile -> {
                if (PackageType.LINUX.contains(cmd.packageType())) {
                    LinuxHelper.initFileAssociationsTestFile(testFile);
                }

                try {
                    final Path appOutput = Path.of(HelloApp.OUTPUT_FILENAME);
                    Files.deleteIfExists(appOutput);

                    Test.trace(String.format("Use desktop to open [%s] file",
                            testFile));
                    Desktop.getDesktop().open(testFile.toFile());
                    Test.waitForFileCreated(appOutput, 7);

                    List<String> expectedArgs = new ArrayList<>(List.of(
                            faLauncherDefaultArgs));
                    expectedArgs.add(testFile.toString());

                    // Wait a little bit after file has been created to
                    // make sure there are no pending writes into it.
                    Thread.sleep(3000);
                    HelloApp.verifyOutputFile(appOutput, expectedArgs.toArray(
                            String[]::new));
                } catch (IOException | InterruptedException ex) {
                    throw new RuntimeException(ex);
                }
            });
        });

        forTypes(PackageType.LINUX, () -> {
            LinuxHelper.addFileAssociationsVerifier(this, fa);
        });

        return this;
    }

    private void forTypes(Collection<PackageType> types, Runnable action) {
        Set<PackageType> oldTypes = Set.of(currentTypes.toArray(
                PackageType[]::new));
        try {
            forTypes(types);
            action.run();
        } finally {
            forTypes(oldTypes);
        }
    }

    private void forTypes(PackageType type, Runnable action) {
        forTypes(List.of(type), action);
    }

    public PackageTest configureHelloApp() {
        addInitializer(cmd -> HelloApp.addTo(cmd), "HelloApp");
        addInstallVerifier(HelloApp::executeLauncherAndVerifyOutput);
        return this;
    }

    public void run() {
        List<Handler> supportedHandlers = handlers.values().stream()
                .filter(entry -> !entry.isVoid())
                .collect(Collectors.toList());

        if (supportedHandlers.isEmpty()) {
            // No handlers with initializers found. Nothing to do.
            return;
        }

        Supplier<JPackageCommand> initializer = new Supplier<>() {
            @Override
            public JPackageCommand get() {
                JPackageCommand cmd = new JPackageCommand().setDefaultInputOutput();
                if (bundleOutputDir != null) {
                    cmd.setArgumentValue("--dest", bundleOutputDir.toString());
                }
                cmd.setDefaultAppName();
                return cmd;
            }
        };

        supportedHandlers.forEach(handler -> handler.accept(initializer.get()));
    }

    public PackageTest setAction(Action value) {
        action = value;
        return this;
    }

    public Action getAction() {
        return action;
    }

    private class Handler implements Consumer<JPackageCommand> {

        Handler(PackageType type) {
            if (!PackageType.NATIVE.contains(type)) {
                throw new IllegalArgumentException(
                        "Attempt to configure a test for image packaging");
            }
            this.type = type;
            initializers = new ArrayList<>();
            bundleVerifiers = new ArrayList<>();
            installVerifiers = new ArrayList<>();
            uninstallVerifiers = new ArrayList<>();
        }

        boolean isVoid() {
            return initializers.isEmpty();
        }

        void addInitializer(Consumer<JPackageCommand> v) {
            initializers.add(v);
        }

        void addBundleVerifier(BiConsumer<JPackageCommand, Executor.Result> v) {
            bundleVerifiers.add(v);
        }

        void addInstallVerifier(Consumer<JPackageCommand> v) {
            installVerifiers.add(v);
        }

        void addUninstallVerifier(Consumer<JPackageCommand> v) {
            uninstallVerifiers.add(v);
        }

        @Override
        public void accept(JPackageCommand cmd) {
            type.applyTo(cmd);

            initializers.stream().forEach(v -> v.accept(cmd));
            switch (action) {
                case CREATE:
                    Executor.Result result = cmd.execute();
                    result.assertExitCodeIs(expectedJPackageExitCode);
                    Test.assertFileExists(cmd.outputBundle(),
                            expectedJPackageExitCode == 0);
                    verifyPackageBundle(cmd.createImmutableCopy(), result);
                    break;

                case VERIFY_INSTALL:
                    verifyPackageInstalled(cmd.createImmutableCopy());
                    break;

                case VERIFY_UNINSTALL:
                    verifyPackageUninstalled(cmd.createImmutableCopy());
                    break;
            }
        }

        private void verifyPackageBundle(JPackageCommand cmd,
                Executor.Result result) {
            if (PackageType.LINUX.contains(cmd.packageType())) {
                Test.assertNotEquals(0L, LinuxHelper.getInstalledPackageSizeKB(
                        cmd), String.format(
                                "Check installed size of [%s] package in KB is not zero",
                                LinuxHelper.getPackageName(cmd)));
            }
            bundleVerifiers.stream().forEach(v -> v.accept(cmd, result));
        }

        private void verifyPackageInstalled(JPackageCommand cmd) {
            Test.trace(String.format("Verify installed: %s",
                    cmd.getPrintableCommandLine()));
            if (cmd.isRuntime()) {
                Test.assertDirectoryExists(
                        cmd.appRuntimeInstallationDirectory(), false);
                Test.assertDirectoryExists(
                        cmd.appInstallationDirectory().resolve("app"), false);
            } else {
                Test.assertExecutableFileExists(cmd.launcherInstallationPath(),
                        true);
            }

            if (PackageType.WINDOWS.contains(cmd.packageType())) {
                new WindowsHelper.AppVerifier(cmd);
            }

            installVerifiers.stream().forEach(v -> v.accept(cmd));
        }

        private void verifyPackageUninstalled(JPackageCommand cmd) {
            Test.trace(String.format("Verify uninstalled: %s",
                    cmd.getPrintableCommandLine()));
            if (!cmd.isRuntime()) {
                Test.assertFileExists(cmd.launcherInstallationPath(), false);
                Test.assertDirectoryExists(cmd.appInstallationDirectory(), false);
            }

            if (PackageType.WINDOWS.contains(cmd.packageType())) {
                new WindowsHelper.AppVerifier(cmd);
            }

            uninstallVerifiers.stream().forEach(v -> v.accept(cmd));
        }

        private final PackageType type;
        private final List<Consumer<JPackageCommand>> initializers;
        private final List<BiConsumer<JPackageCommand, Executor.Result>> bundleVerifiers;
        private final List<Consumer<JPackageCommand>> installVerifiers;
        private final List<Consumer<JPackageCommand>> uninstallVerifiers;
    }

    private Collection<PackageType> currentTypes;
    private int expectedJPackageExitCode;
    private Map<PackageType, Handler> handlers;
    private Set<String> namedInitializers;
    private Action action;

    /**
     * Test action.
     */
    static public enum Action {
        /**
         * Create bundle.
         */
        CREATE,
        /**
         * Verify bundle installed.
         */
        VERIFY_INSTALL,
        /**
         * Verify bundle uninstalled.
         */
        VERIFY_UNINSTALL;

        @Override
        public String toString() {
            return name().toLowerCase().replace('_', '-');
        }
    };
    private final static Action DEFAULT_ACTION;
    private final static File bundleOutputDir;

    static {
        final String propertyName = "output";
        String val = Test.getConfigProperty(propertyName);
        if (val == null) {
            bundleOutputDir = null;
        } else {
            bundleOutputDir = new File(val).getAbsoluteFile();

            if (!bundleOutputDir.isDirectory()) {
                throw new IllegalArgumentException(String.format(
                        "Invalid value of %s sytem property: [%s]. Should be existing directory",
                        Test.getConfigPropertyName(propertyName),
                        bundleOutputDir));
            }
        }
    }

    static {
        final String propertyName = "action";
        String action = Optional.ofNullable(Test.getConfigProperty(propertyName)).orElse(
                Action.CREATE.toString()).toLowerCase();
        DEFAULT_ACTION = Stream.of(Action.values()).filter(
                a -> a.toString().equals(action)).findFirst().orElseThrow(
                        () -> new IllegalArgumentException(String.format(
                                "Unrecognized value of %s property: [%s]",
                                Test.getConfigPropertyName(propertyName), action)));
    }
}
