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

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;

public class JPMacOptionsBase {

    static final String TEST_BUNDLE_NAME = "TestBundleName";
    static final String TEST_BUNDLE_IDENTIFIER = "net.java.openjdk.packagerTest";
    static final String TEST_CATECORY = "public.app-category.test";
    private static String TEST_NAME;
    private static String EXT;
    private static String OUTPUT;
    private static String[] CMD;

    private static void testCreateInstaller() throws Exception {
        JPackageHelper.executeCLI(true, CMD);

        if (EXT.equals("dmg")) {
            String disk = null;
            try {
                var log = new File("hdiutil.log");
                JPackageHelper.execute(log, "/usr/bin/hdiutil",
                        "attach", OUTPUT);
                try(var br = new BufferedReader(new FileReader(log))) {
                    var line = br.lines().reduce((a, b) -> b).orElse(null)
                            .split("\t");
                    disk = line[0].trim();
                    testPkg(line[2].trim() + File.separator + TEST_NAME +
                            "-1.0.pkg");
                }
            } finally {
                if (disk != null) {
                    JPackageHelper.execute(null,
                            "/usr/bin/hdiutil", "detach", disk);
                }
            }
        } else {
            testPkg(OUTPUT);
        }
    }

    private static void testPkg(String path) throws Exception {
        JPackageHelper.execute(null, "/usr/sbin/pkgutil",
                "--expand-full", path, "expand");
        var info = new File("expand/" + TEST_NAME + "-app.pkg/Payload/"
                + TEST_NAME + ".app/Contents/Info.plist");
        if (!info.exists()) {
            throw new AssertionError("Info.plist not found");
        }

        String bundleName = null;
        String bundleIdentifier = null;
        String categoryType = null;
        try (FileInputStream fis = new FileInputStream(info)) {
            var xmlInFact = XMLInputFactory.newInstance();
            xmlInFact.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xmlInFact.setProperty(
                        XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            var reader = xmlInFact.createXMLStreamReader(fis);
            while (reader.hasNext()) {
                if (reader.next() == XMLStreamConstants.CHARACTERS) {
                    switch (reader.getText()) {
                        case "CFBundleName": {
                            bundleName = readValue(reader);
                            break;
                        }
                        case "CFBundleIdentifier" : {
                            bundleIdentifier = readValue(reader);
                            break;
                        }
                        case "LSApplicationCategoryType" : {
                            categoryType = readValue(reader);
                            break;
                        }
                    }
                }
            }
        }
        boolean passed = true;
        if (!TEST_BUNDLE_NAME.equals(bundleName)) {
            passed = false;
            System.err.println("Wrong bundle name [" + bundleName +
                    "] expected [" + TEST_BUNDLE_NAME + "]" );
        }
        if (!TEST_BUNDLE_IDENTIFIER.equals(bundleIdentifier)) {
            passed = false;
            System.err.println("Wrong bundle identifier [" +
                    bundleIdentifier + "] expected [" + TEST_BUNDLE_IDENTIFIER
                    + "]" );
        }
        if (!TEST_CATECORY.equals(categoryType)) {
            passed = false;
            System.err.println("Wrong appstore category [" + categoryType +
                    "] expected [" + TEST_CATECORY + "]" );
        }

        if (!passed) {
            throw new AssertionError("Test failed");
        }
    }

    static private String readValue(XMLStreamReader reader) throws Exception {
        while (reader.hasNext() && reader.next() != XMLStreamConstants.START_ELEMENT);
        return reader.hasNext() ? reader.getElementText() : null;
    }

    private static void verifyInstall() throws Exception {
        String app = JPackagePath.getOSXInstalledApp("jpackage", TEST_NAME);
        JPackageInstallerHelper.validateApp(app);
    }

    private static void verifyUnInstall() throws Exception {
        // Not needed on OS X, since we just deleting installed application
        // without using generated installer. We keeping this for consistnency
        // between platforms.
    }

    private static void init(String name, String ext) {
        TEST_NAME = name;
        EXT = ext;
        OUTPUT = "output" + File.separator + TEST_NAME + "-1.0." + EXT;
        CMD = new String[] {
            "--package-type", EXT,
            "--input", "input",
            "--output", "output",
            "--name", TEST_NAME,
            "--main-jar", "hello.jar",
            "--main-class", "Hello",
            "--mac-bundle-name", TEST_BUNDLE_NAME,
            "--mac-bundle-identifier", TEST_BUNDLE_IDENTIFIER,
            "--mac-app-store-category", TEST_CATECORY
        };
    }

    public static void run(String name, String ext) throws Exception {
        init(name, ext);

        if (JPackageInstallerHelper.isVerifyInstall()) {
            verifyInstall();
        } else if (JPackageInstallerHelper.isVerifyUnInstall()) {
            verifyUnInstall();
        } else {
            JPackageHelper.createHelloInstallerJar();
            testCreateInstaller();
        }
    }
}
