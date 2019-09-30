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
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jdk.jpackage.test.Functional.ThrowingFunction;
import jdk.jpackage.test.Functional.ThrowingSupplier;

public class HelloApp {

    private HelloApp() {
        setClassName(CLASS_NAME).setJarFileName("hello.jar");
    }

    /**
     * Set fully qualified class name. E.g: foo.bar.Buzz.
     */
    private HelloApp setClassName(String v) {
        qualifiedClassName = v;
        return this;
    }

    private HelloApp setModuleName(String v) {
        moduleName = v;
        return this;
    }

    private HelloApp setJarFileName(String v) {
        jarFileName = v;
        return this;
    }

    private HelloApp setModuleVersion(String v) {
        moduleVersion = v;
        return this;
    }

    private JarBuilder prepareSources(Path srcDir) throws IOException {
        final String className = qualifiedClassName.substring(
                qualifiedClassName.lastIndexOf('.') + 1);
        final String packageName = packageName();

        final Path srcFile = srcDir.resolve(Path.of(String.join(
                File.separator, qualifiedClassName.split("\\.")) + ".java"));
        Files.createDirectories(srcFile.getParent());

        JarBuilder jarBuilder = createJarBuilder().addSourceFile(srcFile);
        if (moduleName != null) {
            Path moduleInfoFile = srcDir.resolve("module-info.java");
            TKit.createTextFile(moduleInfoFile, List.of(
                    String.format("module %s {", moduleName),
                    String.format("    exports %s;", packageName),
                    "}"
            ));
            jarBuilder.addSourceFile(moduleInfoFile);
            if (moduleVersion != null) {
                jarBuilder.setModuleVersion(moduleVersion);
            }
        }

        // Add package directive and replace class name in java source file.
        // Works with simple test Hello.java.
        // Don't expect too much from these regexps!
        Pattern classDeclaration = Pattern.compile("(^.*\\bclass\\s+)Hello(.*$)");
        Pattern importDirective = Pattern.compile(
                "(?<=import (?:static )?+)[^;]+");
        AtomicBoolean classDeclared = new AtomicBoolean();
        AtomicBoolean packageInserted = new AtomicBoolean(packageName == null);

        var packageInserter = Functional.identityFunction((line) -> {
            packageInserted.setPlain(true);
            return String.format("package %s;%s%s", packageName,
                    System.lineSeparator(), line);
        });

        Files.write(srcFile, Files.readAllLines(HELLO_JAVA).stream().map(line -> {
            if (classDeclared.getPlain()) {
                return line;
            }

            Matcher m;
            if (!packageInserted.getPlain() && importDirective.matcher(line).find()) {
                line = packageInserter.apply(line);
            } else if ((m = classDeclaration.matcher(line)).find()) {
                classDeclared.setPlain(true);
                line = m.group(1) + className + m.group(2);
                if (!packageInserted.getPlain()) {
                    line = packageInserter.apply(line);
                }
            }
            return line;
        }).collect(Collectors.toList()));

        return jarBuilder;
    }

    private JarBuilder createJarBuilder() {
        return new JarBuilder().setMainClass(qualifiedClassName);
    }

    private void addTo(JPackageCommand cmd) {
        if (moduleName != null && packageName() == null) {
            throw new IllegalArgumentException(String.format(
                    "Module [%s] with default package", moduleName));
        }

        if (moduleName == null && CLASS_NAME.equals(qualifiedClassName)) {
            // Use Hello.java as is.
            cmd.addAction((self) -> {
                File jarFile = self.inputDir().resolve(jarFileName).toFile();
                createJarBuilder().setOutputJar(jarFile).addSourceFile(
                        HELLO_JAVA).create();
            });
        } else {
            cmd.addAction((self) -> {
                final Path jarFile;
                if (moduleName == null) {
                    jarFile = self.inputDir().resolve(jarFileName);
                } else {
                    // `--module-path` option should be set by the moment
                    // when this action is being executed.
                    jarFile = Path.of(self.getArgumentValue("--module-path",
                            () -> self.inputDir().toString()), jarFileName);
                    Files.createDirectories(jarFile.getParent());
                }

                TKit.withTempDirectory("src",
                        workDir -> prepareSources(workDir).setOutputJar(
                                jarFile.toFile()).create());
            });
        }

        if (moduleName == null) {
            cmd.addArguments("--main-jar", jarFileName);
            cmd.addArguments("--main-class", qualifiedClassName);
        } else {
            cmd.addArguments("--module-path", TKit.workDir().resolve(
                    "input-modules"));
            cmd.addArguments("--module", String.join("/", moduleName,
                    qualifiedClassName));
            // For modular app assume nothing will go in input directory and thus
            // nobody will create input directory, so remove corresponding option
            // from jpackage command line.
            cmd.removeArgumentWithValue("--input");
        }
        if (TKit.isWindows()) {
            cmd.addArguments("--win-console");
        }
    }

    private String packageName() {
        int lastDotIdx = qualifiedClassName.lastIndexOf('.');
        if (lastDotIdx == -1) {
            return null;
        }
        return qualifiedClassName.substring(0, lastDotIdx);
    }

    /**
     * Configures Java application to be used with the given jpackage command.
     * Syntax of encoded Java application description is
     * [jar_file:][module_name/]qualified_class_name[@module_version].
     *
     * E.g.: duke.jar:com.other/com.other.foo.bar.Buz@3.7 encodes modular
     * application. Module name is `com.other`. Main class is
     * `com.other.foo.bar.Buz`. Module version is `3.7`. Application will be
     * compiled and packed in `duke.jar` jar file.
     *
     * @param cmd jpackage command to configure
     * @param javaAppDesc encoded Java application description
     */
    static void addTo(JPackageCommand cmd, String javaAppDesc) {
        HelloApp helloApp = new HelloApp();
        if (javaAppDesc != null) {
            String moduleNameAndOther = Functional.identity(() -> {
                String[] components = javaAppDesc.split(":", 2);
                if (components.length == 2) {
                    helloApp.setJarFileName(components[0]);
                }
                return components[components.length - 1];
            }).get();

            String classNameAndOther = Functional.identity(() -> {
                String[] components = moduleNameAndOther.split("/", 2);
                if (components.length == 2) {
                    helloApp.setModuleName(components[0]);
                }
                return components[components.length - 1];
            }).get();

            Functional.identity(() -> {
                String[] components = classNameAndOther.split("@", 2);
                helloApp.setClassName(components[0]);
                if (components.length == 2) {
                    helloApp.setModuleVersion(components[1]);
                }
            }).run();
        }
        helloApp.addTo(cmd);
    }

    static void verifyOutputFile(Path outputFile, List<String> args) {
        if (!outputFile.isAbsolute()) {
            verifyOutputFile(outputFile.toAbsolutePath().normalize(), args);
            return;
        }

        TKit.assertFileExists(outputFile);

        List<String> contents = ThrowingSupplier.toSupplier(
                () -> Files.readAllLines(outputFile)).get();

        List<String> expected = new ArrayList<>(List.of(
                "jpackage test application",
                String.format("args.length: %d", args.size())
        ));
        expected.addAll(args);

        TKit.assertStringListEquals(expected, contents, String.format(
                "Check contents of [%s] file", outputFile));
    }

    public static void executeLauncherAndVerifyOutput(JPackageCommand cmd) {
        final Path launcherPath;
        if (cmd.packageType() == PackageType.IMAGE) {
            launcherPath = cmd.appImage().resolve(cmd.launcherPathInAppImage());
            if (cmd.isFakeRuntimeInAppImage(String.format(
                    "Not running [%s] launcher from application image",
                    launcherPath))) {
                return;
            }
        } else {
            launcherPath = cmd.launcherInstallationPath();
            if (cmd.isFakeRuntimeInstalled(String.format(
                    "Not running [%s] launcher", launcherPath))) {
                return;
            }
        }

        executeAndVerifyOutput(launcherPath, cmd.getAllArgumentValues(
                "--arguments"));
    }

    public static void executeAndVerifyOutput(Path helloAppLauncher,
            String... defaultLauncherArgs) {
        executeAndVerifyOutput(helloAppLauncher, List.of(defaultLauncherArgs));
    }

    public static void executeAndVerifyOutput(Path helloAppLauncher,
            List<String> defaultLauncherArgs) {
        // Output file will be created in the current directory.
        Path outputFile = Path.of(OUTPUT_FILENAME);
        ThrowingFunction.toFunction(Files::deleteIfExists).apply(outputFile);
        new Executor()
                .setDirectory(outputFile.getParent())
                .setExecutable(helloAppLauncher)
                .execute()
                .assertExitCodeIsZero();

        verifyOutputFile(outputFile, defaultLauncherArgs);
    }

    final static String OUTPUT_FILENAME = "appOutput.txt";

    private String qualifiedClassName;
    private String moduleName;
    private String jarFileName;
    private String moduleVersion;

    private static final Path HELLO_JAVA = TKit.TEST_SRC_ROOT.resolve(
            "apps/image/Hello.java");

    private final static String CLASS_NAME = HELLO_JAVA.getFileName().toString().split(
            "\\.", 2)[0];
}
