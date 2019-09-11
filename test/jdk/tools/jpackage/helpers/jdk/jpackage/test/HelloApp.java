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
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;


public class HelloApp {
    static void addTo(JPackageCommand cmd) {
        cmd.addAction(new Runnable() {
            @Override
            public void run() {
                String mainClass = "Hello";
                Path jar = cmd.inputDir().resolve("hello.jar");
                new JarBuilder()
                        .setOutputJar(jar.toFile())
                        .setMainClass(mainClass)
                        .addSourceFile(Test.TEST_SRC_ROOT.resolve(
                                Path.of("apps", "image", mainClass + ".java")))
                        .create();
                cmd.addArguments("--main-jar", jar.getFileName().toString());
                cmd.addArguments("--main-class", mainClass);
            }
        });
        if (PackageType.WINDOWS.contains(cmd.packageType())) {
            cmd.addArguments("--win-console");
        }
    }

    public static void verifyOutputFile(Path outputFile, String ... args) {
        Test.assertFileExists(outputFile, true);

        List<String> output = null;
        try {
            output = Files.readAllLines(outputFile);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }

        final int expectedNumberOfLines = 2 + args.length;
        Test.assertEquals(expectedNumberOfLines, output.size(), String.format(
                "Check file [%s] contains %d text lines", outputFile,
                expectedNumberOfLines));

        Test.assertEquals("jpackage test application", output.get(0),
                String.format(
                        "Check contents of the first text line in [%s] file",
                        outputFile));

        Test.assertEquals(String.format("args.length: %d", args.length),
                output.get(1), String.format(
                "Check contents of the second text line in [%s] file",
                outputFile));

        Enumeration<String> argsEnum = Collections.enumeration(List.of(args));
        AtomicInteger counter = new AtomicInteger(2);
        output.stream().skip(2).sequential().forEach(line -> Test.assertEquals(
                argsEnum.nextElement(), line, String.format(
                "Check contents of %d text line in [%s] file",
                counter.incrementAndGet(), outputFile)));
    }

    public static void executeAndVerifyOutput(Path helloAppLauncher,
            String... defaultLauncherArgs) {
        File outputFile = Test.workDir().resolve(OUTPUT_FILENAME).toFile();
        new Executor()
                .setDirectory(outputFile.getParentFile().toPath())
                .setExecutable(helloAppLauncher.toString())
                .execute()
                .assertExitCodeIsZero();

        verifyOutputFile(outputFile.toPath(), defaultLauncherArgs);
    }

    public final static String OUTPUT_FILENAME = "appOutput.txt";
}
