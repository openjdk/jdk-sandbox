/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import jdk.jpackage.test.HelloApp;
import jdk.jpackage.test.PackageTest;
import jdk.jpackage.test.Test;

/**
 * Test --file-associations parameter.
 * Output of the test should be fileassociationstest*.* installer.
 * The output installer should provide the same functionality as the default
 * installer (see description of the default installer in SimplePackageTest.java)
 * plus configure file associations.
 * After installation files with ".jptest1" suffix should be associated with
 * the test app.
 *
 * Suggested test scenario is to create empty file with ".jptest1" suffix,
 * double click on it and make sure that test application was launched in
 * response to double click event with the path to test .jptest1 file
 * on the commend line.
 *
 * On Linux use "echo > foo.jptest1" and not "touch foo.jptest1" to create
 * test file as empty files are always interpreted as plain text and will not
 * be opened with the test app. This is a known bug.
 */

/*
 * @test
 * @summary jpackage with --file-associations
 * @library ../helpers
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm/timeout=360 -Xmx512m FileAssociationsTest
 */
public class FileAssociationsTest {
    public static void main(String[] args) throws Exception {
        new PackageTest().configureHelloApp()
        .addInitializer(cmd -> {
            initFaPropsFile();
            cmd.addArguments("--file-associations", FA_PROPS_FILE.toString());
        })
        .addInstallVerifier(cmd -> {
            Path testFile = null;
            try {
                testFile = Test.createTempFile("." + FA_SUFFIX);
                // Write something in test file.
                // On Ubuntu and Oracle Linux empty files are considered
                // plain text. Seems like a system bug.
                //
                // [asemenyu@spacewalk ~]$ rm gg.jptest1
                // $ touch foo.jptest1
                // $ xdg-mime query filetype foo.jptest1
                // text/plain
                // $ echo > foo.jptest1
                // $ xdg-mime query filetype foo.jptest1
                // application/x-jpackage-jptest1
                //
                Files.write(testFile, Arrays.asList(""));

                final Path appOutput = Path.of(HelloApp.OUTPUT_FILENAME);
                Files.deleteIfExists(appOutput);

                Test.trace(String.format("Use desktop to open [%s] file", testFile));
                Desktop.getDesktop().open(testFile.toFile());
                Test.waitForFileCreated(appOutput, 7);

                // Wait a little bit after file has been created to
                // make sure there are no pending writes into it.
                Thread.sleep(3000);
                HelloApp.verifyOutputFile(appOutput, testFile.toString());
            } catch (IOException | InterruptedException ex) {
                throw new RuntimeException(ex);
            } finally {
                if (testFile != null) {
                    try {
                        Files.deleteIfExists(testFile);
                    } catch (IOException ex) {
                        throw new RuntimeException(ex);
                    }
                }
            }
        })
        .run();
    }

    private static void initFaPropsFile() {
        try {
            Files.write(FA_PROPS_FILE, Arrays.asList(
                "extension=" + FA_SUFFIX,
                "mime-type=application/x-jpackage-" + FA_SUFFIX,
                "description=jpackage test extention"));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private final static String FA_SUFFIX = "jptest1";
    private final static Path FA_PROPS_FILE = Test.workDir().resolve("fa.properties");
}
