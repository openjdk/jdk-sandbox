/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;

 /*
 * @test
 * @summary jpackager create image with --strip-native-commands test
 * @library ../helpers
 * @build JPackagerHelper
 * @build JPackagerPath
 * @modules jdk.jpackager
 * @run main/othervm -Xmx512m JPackagerCreateImageStripNativeCommandsTest
 */
public class JPackagerCreateImageStripNativeCommandsTest {
    private static final String runtimeBinPath = JPackagerPath.getRuntimeBin();

    private static final String [] CMD = {
        "create-image",
        "--input", "input",
        "--output", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--files", "hello.jar",
        "--force",
        "--strip-native-commands"};

    private static void validate() throws Exception {
        if (JPackagerHelper.isWindows()) {
            Path binPath = Paths.get(runtimeBinPath).toAbsolutePath();
            List<Path> files = Files.walk(binPath).collect(Collectors.toList());
            files.forEach((f) -> {
                if (f.toString().endsWith(".exe")) {
                    throw new AssertionError(
                            "Found executable file in runtime bin folder: "
                            + f.toString());
                }
            });
        } else {
            File binFolder = new File(runtimeBinPath);
            if (binFolder.exists()) {
                throw new AssertionError("Found bin folder in runtime: "
                            + binFolder.toString());
            }
        }
    }

    private static void testCreateImage() throws Exception {
        JPackagerHelper.executeCLI(true, CMD);
        validate();
    }

    private static void testCreateImageToolProvider() throws Exception {
        JPackagerHelper.executeToolProvider(true, CMD);
        validate();
    }

    public static void main(String[] args) throws Exception {
        JPackagerHelper.createHelloJar();
        testCreateImage();
        testCreateImageToolProvider();
    }

}
