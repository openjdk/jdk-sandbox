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
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
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

    static JPackageCommand createImmutable(JPackageCommand v) {
        JPackageCommand reply = new JPackageCommand();
        reply.immutable = true;
        reply.args.addAll(v.args);
        return reply;
    }

    public void setArgumentValue(String argName, String newValue) {
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
                return;
            }
            prevArg = value;
        }

        if (newValue != null) {
            addArguments(argName, newValue);
        }
    }

    public <T> T getArgumentValue(String argName,
            Supplier<T> defaultValueSupplier,
            Function<String, T> stringConverter) {
        String prevArg = null;
        for (String arg : args) {
            if (prevArg != null && prevArg.equals(argName)) {
                return stringConverter.apply(arg);
            }
            prevArg = arg;
        }
        if (defaultValueSupplier != null) {
            return defaultValueSupplier.get();
        }
        return null;
    }

    public String getArgumentValue(String argName,
            Supplier<String> defaultValueSupplier) {
        return getArgumentValue(argName, defaultValueSupplier,
                (v) -> v);
    }

    public PackageType packageType() {
        return getArgumentValue("--package-type",
                () -> PackageType.IMAGE,
                (v) -> PACKAGE_TYPES.get(v));
    }

    public Path outputDir() {
        return getArgumentValue("--output",
                () -> Test.defaultOutputDir(),
                (v) -> Path.of(v));
    }

    public Path inputDir() {
        return getArgumentValue("--input",
                () -> Test.defaultInputDir(),
                (v) -> Path.of(v));
    }

    public String version() {
        return getArgumentValue("--version", () -> "1.0");
    }

    public String name() {
        return getArgumentValue("--name",
                () -> getArgumentValue("--main-class", null));
    }

    public JPackageCommand setDefaultInputOutput() {
        verifyMutable();
        addArguments("--input", Test.defaultInputDir().toString());
        addArguments("--output", Test.defaultOutputDir().toString());
        return this;
    }

    public JPackageCommand setHelloApp() {
        verifyMutable();
        actions.add(new Runnable() {
            @Override
            public void run() {
                String mainClass = "Hello";
                Path jar = inputDir().resolve("hello.jar");
                new JarBuilder()
                        .setOutputJar(jar.toFile())
                        .setMainClass(mainClass)
                        .addSourceFile(Test.TEST_SRC_ROOT.resolve(
                                Path.of("apps", "image", mainClass + ".java")))
                        .create();
                addArguments("--main-jar", jar.getFileName().toString());
                addArguments("--main-class", mainClass);
            }
        });
        return this;
    }

    public JPackageCommand setPackageType(PackageType type) {
        verifyMutable();
        type.applyTo(this);
        return this;
    }

    public Path outputBundle() {
        switch (packageType()) {
            case LINUX_RPM:
            case LINUX_DEB:
                return outputDir().resolve(LinuxHelper.getBundleName(this));
        }
        return null;
    }

    public Path launcherInstallationPath() {
        switch (packageType()) {
            case LINUX_RPM:
            case LINUX_DEB:
                return LinuxHelper.getLauncherPath(this);
        }
        return null;
    }

    public Executor.Result execute() {
        verifyMutable();
        if (actions != null) {
            actions.stream().forEach(r -> r.run());
        }
        return new Executor()
                .setExecutable(JavaTool.JPACKAGE)
                .addArguments(args)
                .execute();
    }

    static void verifyHelloApp(Path helloAppLauncher) {
        File outputFile = Test.workDir().resolve("appOutput.txt").toFile();
        new Executor()
                .setDirectory(outputFile.getParentFile().toPath())
                .setExecutable(helloAppLauncher.toString())
                .execute()
                .assertExitCodeIsZero();

        Test.assertTrue(outputFile.exists(), String.format(
                "Check file [%s] exists", outputFile));

        List<String> output = null;
        try {
            output = Files.readAllLines(outputFile.toPath());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        Test.assertEquals(2, output.size(), String.format(
                "Check file [%s] contains %d text lines", outputFile, 2));

        Test.assertEquals("jpackage test application", output.get(0),
                String.format(
                        "Check contents of the first text line in [%s] file",
                        outputFile));

        Test.assertEquals("args.length: 0", output.get(1), String.format(
                "Check contents of the second text line in [%s] file",
                outputFile));
    }

    @Override
    protected boolean isMutable() {
        return !immutable;
    }

    private final List<Runnable> actions;
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
}
