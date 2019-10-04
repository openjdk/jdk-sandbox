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

package jdk.jpackage.tests;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import jdk.jpackage.test.Executor;
import jdk.jpackage.test.JPackageCommand;
import jdk.jpackage.test.TKit;
import jdk.jpackage.test.JavaTool;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.Annotations.*;

/*
 * @test
 * @summary jpackage basic testing
 * @library ../../../../helpers
 * @build jdk.jpackage.test.*
 * @modules jdk.jpackage
 * @compile BasicTest.java
 * @run main/othervm/timeout=360 -Xmx512m jdk.jpackage.test.Main
 *  --jpt-run=jdk.jpackage.tests.BasicTest
 */

public class BasicTest {
    @Test
    public void testNoArgs() {
        List<String> output = JPackageCommand.filterOutput(
                getJPackageToolProvider().executeAndGetOutput());
        TKit.assertStringListEquals(List.of("Usage: jpackage <mode> <options>",
                "Use jpackage --help (or -h) for a list of possible options"),
                output, "Check jpackage output");
    }

    @Test
    public void testVersion() {
        List<String> output = JPackageCommand.filterOutput(
                getJPackageToolProvider()
                        .addArgument("--version")
                        .executeAndGetOutput());
        TKit.assertStringListEquals(List.of(System.getProperty("java.version")),
                output, "Check jpackage output");
    }

    @Test
    public void testNoName() {
        final String mainClassName = "Greetings";

        JPackageCommand cmd = new JPackageCommand()
                .helloAppImage(mainClassName)
                .removeArgumentWithValue("--name");

        Path expectedImageDir = cmd.outputDir().resolve(mainClassName);
        if (TKit.isOSX()) {
            expectedImageDir = expectedImageDir.getParent().resolve(
                    expectedImageDir.getFileName().toString() + ".app");
        }

        cmd.executeAndAssertHelloAppImageCreated();
        TKit.assertEquals(expectedImageDir.toAbsolutePath().normalize().toString(),
                cmd.appImage().toAbsolutePath().normalize().toString(),
                String.format(
                        "Check [%s] directory is filled with application image data",
                        expectedImageDir));
    }

    @Test
    public void testApp() {
        new JPackageCommand()
        .helloAppImage()
        .executeAndAssertHelloAppImageCreated();
    }

    @Test
    public void testModularApp() {
        new JPackageCommand()
        .helloAppImage("com.other/com.other.Hello")
        .executeAndAssertHelloAppImageCreated();
    }

    @Test
    @Parameter("ALL-MODULE-PATH")
    @Parameter("ALL-DEFAULT")
    @Parameter("jdk.desktop,jdk.jartool")
    public void testAddModules(String addModulesArg) {
        JPackageCommand cmd = new JPackageCommand()
            .helloAppImage("com.other/com.other.Hello");
        if (!addModulesArg.isEmpty()) {
            cmd.addArguments("--add-modules", addModulesArg);
        }
        cmd.executeAndAssertHelloAppImageCreated();
    }

    /**
     * Test --temp option. Doesn't make much sense for app image as temporary
     * directory is used only on Windows.
     * @throws IOException
     */
//    @Test
    public void testTemp() throws IOException {
        JPackageCommand cmd = new JPackageCommand().helloAppImage();
        TKit.withTempDirectory("temp-root", tempDir -> {
            cmd.addArguments("--temp", tempDir);

            cmd.executeAndAssertHelloAppImageCreated();

            // Check jpackage actually used the supplied directory.
            TKit.assertNotEquals(0, tempDir.toFile().list().length,
                    String.format(
                            "Check jpackage wrote some data in the supplied temporary directory [%s]",
                            tempDir));

            // Temporary directory should not be empty,
            // jpackage should exit with error.
            cmd.execute().assertExitCodeIs(1);
        });
    }

    @Test
    public void testAtFile() throws IOException {
        JPackageCommand cmd = new JPackageCommand().helloAppImage();

        // Init options file with the list of options configured
        // for JPackageCommand instance.
        final Path optionsFile = TKit.workDir().resolve("options");
        Files.write(optionsFile,
                List.of(String.join(" ", cmd.getAllArguments())));

        // Build app jar file.
        cmd.executePrerequisiteActions();

        // Make sure output directory is empty. Normally JPackageCommand would
        // do this automatically.
        TKit.deleteDirectoryContentsRecursive(cmd.outputDir());

        // Instead of running jpackage command through configured
        // JPackageCommand instance, run vanilla jpackage command with @ file.
        getJPackageToolProvider()
                .addArgument(String.format("@%s", optionsFile))
                .execute().assertExitCodeIsZero();

        // Verify output of jpackage command.
        cmd.assertImageCreated();
        HelloApp.executeLauncherAndVerifyOutput(cmd);
    }

    @Parameter("Hello")
    @Parameter("com.foo/com.foo.main.Aloha")
    @Test
    public void testJLinkRuntime(String javaAppDesc) {
        JPackageCommand cmd = JPackageCommand.helloAppImage(javaAppDesc);

        // If `--module` parameter was set on jpackage command line, get its
        // value and extract module name.
        // E.g.: foo.bar2/foo.bar.Buz -> foo.bar2
        // Note: HelloApp class manages `--module` parameter on jpackage command line
        final String moduleName = cmd.getArgumentValue("--module", () -> null,
                (v) -> v.split("/", 2)[0]);

        if (moduleName != null) {
            // Build module jar.
            cmd.executePrerequisiteActions();
        }

        TKit.withTempDirectory("runtime", runtimeDir -> {
            TKit.deleteDirectoryRecursive(runtimeDir, String.format(
                    "Delete [%s] output directory for jlink command", runtimeDir));
            Executor jlink = getToolProvider(JavaTool.JLINK)
            .saveOutput(false)
            .addArguments(
                    "--add-modules", "java.base",
                    "--output", runtimeDir.toString(),
                    "--strip-debug",
                    "--no-header-files",
                    "--no-man-pages");

            if (moduleName != null) {
                jlink.addArguments("--add-modules", moduleName, "--module-path",
                        Path.of(cmd.getArgumentValue("--module-path")).resolve(
                                "hello.jar").toString());
            }

            jlink.execute().assertExitCodeIsZero();

            cmd.addArguments("--runtime-image", runtimeDir);
            cmd.executeAndAssertHelloAppImageCreated();

            final Path appImageRuntimePath = cmd.appImage().resolve(
                    cmd.appRuntimeDirectoryInAppImage());

            //
            // This is an overkill to list modules in jlink output as we have
            // already verified that Java app is functional and thus app's runtime
            // is likely to be OK, but let's double check.
            //
            // Filter out all first strings with whitespace. They are java
            // launcher output like `Picked up ...` unrelated to module names.
            //
            Pattern whitespaceChar = Pattern.compile("\\s");
            List<String> moduleList = new Executor().dumpOutput().setExecutable(
                    appImageRuntimePath.resolve(
                            JPackageCommand.relativePathInRuntime(JavaTool.JAVA))).addArguments(
                    "--list-modules").executeAndGetOutput().stream().dropWhile(
                            s -> whitespaceChar.matcher(s).find()).sorted().collect(
                            Collectors.toList());

            List<String> expectedModules = new ArrayList<>();
            expectedModules.add(String.format("java.base@%s",
                    System.getProperty("java.version")));
            if (moduleName != null) {
                expectedModules.add(moduleName);
            }
            expectedModules = expectedModules.stream().sorted().collect(
                    Collectors.toList());

            TKit.assertStringListEquals(expectedModules, moduleList,
                    String.format(
                            "Check modules in application image runtime directory at [%s]",
                            appImageRuntimePath));
        });
    }

    private static Executor getJPackageToolProvider() {
        return getToolProvider(JavaTool.JPACKAGE);
    }

    private static Executor getToolProvider(JavaTool tool) {
        return new Executor()
                .dumpOutput().saveOutput()
                .setToolProvider(tool.asToolProvider());
    }
}
