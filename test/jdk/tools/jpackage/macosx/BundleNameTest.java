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

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Files;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

/*
 * @test
 * @summary jpackage create image bundle name test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @modules jdk.jpackage
 * @requires (os.family == "mac")
 * @run main/othervm -Xmx512m BundleNameTest
 */
public class BundleNameTest {
    private static final String OUTPUT = "output";
    private static final String app = JPackagePath.getApp();
    private static final String appOutput = JPackagePath.getAppOutputFile();
    private static final String MAC_BUNDLE_NAME = "TestBundleName";
    private static final String APP_NAME = "test";

    private static final String [] CMD_1 = {
        "--input", "input",
        "--output", OUTPUT,
        "--name", APP_NAME,
        "--main-jar", "hello.jar",
        "--main-class", "Hello"
    };

    private static final String [] CMD_2 = {
        "--input", "input",
        "--output", OUTPUT,
        "--name", APP_NAME,
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
        "--mac-bundle-name", MAC_BUNDLE_NAME
    };

    private static void validateResult(String[] result) throws Exception {
        if (result.length != 2) {
            throw new AssertionError(
                   "Unexpected number of lines: " + result.length);
        }

        if (!result[0].trim().equals("jpackage test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: 0")) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }
    }

    private static void validate() throws Exception {
        int retVal = JPackageHelper.execute(null, app);
        if (retVal != 0) {
            throw new AssertionError(
                   "Test application exited with error: " + retVal);
        }

        File outfile = new File(appOutput);
        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");
        validateResult(result);
    }

    private static void validateBundleName(String bundleName) throws Exception {
        System.out.println("Validating bundleName: " + bundleName);

        File infoPList = new File(OUTPUT + File.separator + APP_NAME + ".app" +
                File.separator + "Contents" + File.separator + "Info.plist");

        DocumentBuilderFactory dbf = DocumentBuilderFactory.newDefaultInstance();
        dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        DocumentBuilder b = dbf.newDocumentBuilder();
        org.w3c.dom.Document doc = b.parse(new FileInputStream(
                                                  infoPList.getAbsolutePath()));

        XPath xPath = XPathFactory.newInstance().newXPath();
        // Query for the value of <string> element preceding <key> element
        // with value equal to CFBundleName
        String v = (String)xPath.evaluate(
                       "//string[preceding-sibling::key = \"CFBundleName\"][1]",
                       doc, XPathConstants.STRING);

        if (!v.equals(bundleName)) {
            throw new AssertionError("Unexpected value of CFBundleName key: ["
                                  + v + "]. Expected value: [" + bundleName + "]");
        }
    }

    private static void testCreateAppImage(String [] cmd,
                                         String bundleName,
                                         boolean validateApp) throws Exception {
        JPackageHelper.executeCLI(true, cmd);
        if (validateApp) {
            validate();
        }
        validateBundleName(bundleName);
    }

    private static void testCreateAppImageToolProvider(String [] cmd,
                                         String bundleName,
                                         boolean validateApp) throws Exception {
        JPackageHelper.executeToolProvider(true, cmd);
        if (validateApp) {
            validate();
        }
        validateBundleName(bundleName);
    }

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloImageJar();
        testCreateAppImage(CMD_1, APP_NAME, false);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        testCreateAppImageToolProvider(CMD_1, APP_NAME, false);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        testCreateAppImage(CMD_2, MAC_BUNDLE_NAME, true);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        testCreateAppImageToolProvider(CMD_2, MAC_BUNDLE_NAME, true);
    }
}
