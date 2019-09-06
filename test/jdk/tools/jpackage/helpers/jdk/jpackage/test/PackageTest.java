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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
     * <li>Set --input and --output parameters.
     * <li>Set --name parameter. Value of the parameter is the name of the first
     * class with main function found in the callers stack.
     * Defaults can be
     * overridden with custom initializers set with subsequent addInitializer()
     * function calls.
     */
    public PackageTest() {
        setJPackageExitCode(0);
        handlers = new HashMap<>();
        Arrays.asList(PackageType.values()).stream().forEach(
                v -> handlers.put(v, new Handler(v)));
    }

    public PackageTest setJPackageExitCode(int v) {
        expectedJPackageExitCode = 0;
        return this;
    }

    public PackageTest addInitializer(Consumer<JPackageCommand> v,
            PackageType... types) {
        normailize(types).forEach(
                type -> handlers.get(type).addInitializer(v));
        return this;
    }

    public PackageTest addBundleVerifier(
            BiConsumer<JPackageCommand, Executor.Result> v, PackageType... types) {
        normailize(types).forEach(
                type -> handlers.get(type).addBundleVerifier(v));
        return this;
    }

    public PackageTest addBundleVerifier(
            Consumer<JPackageCommand> v, PackageType... types) {
        return addBundleVerifier((cmd, unused) -> v.accept(cmd), types);
    }

    public PackageTest addInstallVerifier(Consumer<JPackageCommand> v,
            PackageType... types) {
        normailize(types).forEach(
                type -> handlers.get(type).addInstallVerifier(v));
        return this;
    }

    public PackageTest addUninstallVerifier(Consumer<JPackageCommand> v,
            PackageType... types) {
        normailize(types).forEach(
                type -> handlers.get(type).addUninstallVerifier(v));
        return this;
    }

    public PackageTest configureHelloApp(PackageType... types) {
        addInitializer(cmd -> cmd.setHelloApp(), types);
        addInstallVerifier(cmd -> JPackageCommand.verifyHelloApp(
                cmd.launcherInstallationPath()), types);
        return this;
    }

    public void run() {
        List<Handler> supportedHandlers = handlers.values().stream()
                .filter(entry -> !entry.isVoid())
                .collect(Collectors.toList());

        if (supportedHandlers.isEmpty()) {
            return;
        }

        Supplier<JPackageCommand> initializer = new Supplier<>() {
            @Override
            public JPackageCommand get() {
                JPackageCommand cmd = new JPackageCommand().setDefaultInputOutput();
                if (bundleOutputDir != null) {
                    cmd.setArgumentValue("--output", bundleOutputDir.toString());
                }
                setDefaultAppName(cmd);
                return cmd;
            }
        };

        supportedHandlers.forEach(handler -> handler.accept(initializer.get()));
    }

    private class Handler implements Consumer<JPackageCommand> {

        Handler(PackageType type) {
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
            if (isSupported()) {
                initializers.add(v);
            }
        }

        void addBundleVerifier(BiConsumer<JPackageCommand, Executor.Result> v) {
            if (isSupported()) {
                bundleVerifiers.add(v);
            }
        }

        void addInstallVerifier(Consumer<JPackageCommand> v) {
            if (isSupported()) {
                installVerifiers.add(v);
            }
        }

        void addUninstallVerifier(Consumer<JPackageCommand> v) {
            if (isSupported()) {
                uninstallVerifiers.add(v);
            }
        }

        @Override
        public void accept(JPackageCommand cmd) {
            type.applyTo(cmd);

            initializers.stream().forEach(v -> v.accept(cmd));
            switch (action) {
                case CREATE:
                    Executor.Result result = cmd.execute();
                    result.assertExitCodeIs(expectedJPackageExitCode);
                    final File bundle = cmd.outputBundle().toFile();
                    if (expectedJPackageExitCode == 0) {
                        Test.assertTrue(bundle.exists(), String.format(
                                "Check file [%s] exists", bundle));
                    } else {
                        Test.assertFalse(bundle.exists(), String.format(
                                "Check file [%s] doesn't exist", bundle));
                    }

                    verifyPackageBundle(JPackageCommand.createImmutable(cmd), result);
                    break;

                case VERIFY_INSTALLED:
                    verifyPackageInstalled(JPackageCommand.createImmutable(cmd));
                    break;

                case VERIFY_UNINSTALLED:
                    verifyPackageUninstalled(JPackageCommand.createImmutable(cmd));
                    break;
            }
        }

        private void verifyPackageBundle(JPackageCommand cmd, Executor.Result result) {
            bundleVerifiers.stream().forEach(v -> v.accept(cmd, result));
        }

        private void verifyPackageInstalled(JPackageCommand cmd) {
            verifyInstalledLauncher(cmd.launcherInstallationPath().toFile());
            installVerifiers.stream().forEach(v -> v.accept(cmd));
        }

        private void verifyPackageUninstalled(JPackageCommand cmd) {
            verifyUninstalledLauncher(cmd.launcherInstallationPath().toFile());
            uninstallVerifiers.stream().forEach(v -> v.accept(cmd));
        }

        private boolean isSupported() {
            return type.getName() != null && type.isSupported();
        }

        private final PackageType type;
        private final List<Consumer<JPackageCommand>> initializers;
        private final List<BiConsumer<JPackageCommand, Executor.Result>> bundleVerifiers;
        private final List<Consumer<JPackageCommand>> installVerifiers;
        private final List<Consumer<JPackageCommand>> uninstallVerifiers;
    }

    private void setDefaultAppName(JPackageCommand cmd) {
        StackTraceElement st[] = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : st) {
            if ("main".equals(ste.getMethodName())) {
                String name = ste.getClassName();
                name = name.substring(name.lastIndexOf('.') + 1);
                cmd.addArguments("--name", name);
                break;
            }
        }
    }

    private Stream<PackageType> normailize(PackageType[] types) {
        if (types == null || types.length == 0) {
            return Arrays.stream(PackageType.values());
        }
        return Arrays.stream(types).distinct();
    }

    private void verifyInstalledLauncher(File launcher) {
        Test.assertTrue(launcher.isFile(), String.format(
                "Check application launcher [%s] is a file", launcher));
        Test.assertTrue(launcher.canExecute(), String.format(
                "Check application launcher [%s] can be executed", launcher));
    }

    private void verifyUninstalledLauncher(File launcher) {
        Test.assertFalse(launcher.exists(), String.format(
                "Check application launcher [%s] is not installed", launcher));
        File installDir = launcher.getParentFile().getParentFile();
        Test.assertFalse(installDir.exists(), String.format(
                "Check application installation directory [%s] is not available",
                installDir));
    }

    private int expectedJPackageExitCode;
    private Map<PackageType, Handler> handlers;

    /**
     * Test action.
     */
    static private enum Action {
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
    private final static Action action;
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
            action = Action.VERIFY_INSTALLED;
        } else if (System.getProperty("jpackage.verify.uninstall") != null) {
            action = Action.VERIFY_UNINSTALLED;
        } else {
            action = Action.CREATE;
        }
    }
}
