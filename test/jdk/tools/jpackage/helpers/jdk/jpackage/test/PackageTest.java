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

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
        expectedJPackageExitCode = 0;
        return this;
    }

    public PackageTest addInitializer(Consumer<JPackageCommand> v) {
        currentTypes.stream().forEach(type -> handlers.get(type).addInitializer(
                v));
        return this;
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
                    propertyValue = LinuxHelper.geRpmBundleProperty(
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

    public PackageTest configureHelloApp() {
        addInitializer(cmd -> HelloApp.addTo(cmd));
        addInstallVerifier(cmd -> HelloApp.executeAndVerifyOutput(
                cmd.launcherInstallationPath(), cmd.getAllArgumentValues(
                "--arguments")));
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
                    verifyPackageBundle(JPackageCommand.createImmutable(cmd),
                            result);
                    break;

                case VERIFY_INSTALLED:
                    verifyPackageInstalled(JPackageCommand.createImmutable(cmd));
                    break;

                case VERIFY_UNINSTALLED:
                    verifyPackageUninstalled(
                            JPackageCommand.createImmutable(cmd));
                    break;
            }
        }

        private void verifyPackageBundle(JPackageCommand cmd,
                Executor.Result result) {
            bundleVerifiers.stream().forEach(v -> v.accept(cmd, result));
        }

        private void verifyPackageInstalled(JPackageCommand cmd) {
            Test.trace(String.format("Verify installed: %s",
                    cmd.getPrintableCommandLine()));
            if (cmd.isRuntime()) {
                Test.assertDirectoryExists(
                        cmd.appInstallationDirectory().resolve("runtime"), false);
                Test.assertDirectoryExists(
                        cmd.appInstallationDirectory().resolve("app"), false);
            }

            Test.assertExecutableFileExists(cmd.launcherInstallationPath(),
                    !cmd.isRuntime());

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
        VERIFY_INSTALLED,
        /**
         * Verify bundle uninstalled.
         */
        VERIFY_UNINSTALLED
    };
    private final static Action DEFAULT_ACTION;
    private final static File bundleOutputDir;

    static {
        final String JPACKAGE_TEST_OUTPUT = "jpackage.test.output";

        String val = System.getProperty(JPACKAGE_TEST_OUTPUT);
        if (val == null) {
            bundleOutputDir = null;
        } else {
            bundleOutputDir = new File(val).getAbsoluteFile();

            Test.assertTrue(bundleOutputDir.isDirectory(), String.format(
                    "Check value of %s property [%s] references a directory",
                    JPACKAGE_TEST_OUTPUT, bundleOutputDir));
            Test.assertTrue(bundleOutputDir.canWrite(), String.format(
                    "Check value of %s property [%s] references writable directory",
                    JPACKAGE_TEST_OUTPUT, bundleOutputDir));
        }
    }

    static {
        if (System.getProperty("jpackage.verify.install") != null) {
            DEFAULT_ACTION = Action.VERIFY_INSTALLED;
        } else if (System.getProperty("jpackage.verify.uninstall") != null) {
            DEFAULT_ACTION = Action.VERIFY_UNINSTALLED;
        } else {
            DEFAULT_ACTION = Action.CREATE;
        }
    }
}
