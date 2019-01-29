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

import jdk.jpackage.internal.Arguments;
import jdk.jpackage.internal.DeployParams;
import jdk.jpackage.internal.PackagerException;
import java.io.File;

/*
 * @test
 * @bug 8211285
 * @summary DeployParamsTest
 * @modules jdk.jpackage
 * @modules jdk.jpackage/jdk.jpackage.internal
 * @run main/othervm -Xmx512m DeployParamsTest
 */
public class DeployParamsTest {

    private static File testRoot = null;

    private static void setUp() {
        testRoot = new File("deployParamsTest");
        System.out.println("DeployParamsTest: " + testRoot.getAbsolutePath());
        testRoot.mkdir();
    }

    private static void tearDown() {
        if (testRoot != null) {
            testRoot.delete();
        }
    }

    private static void testValidateAppName1() throws Exception {
        DeployParams params = getParamsAppName();

        setAppName(params, "Test");
        params.validate();

        setAppName(params, "Test Name");
        params.validate();

        setAppName(params, "Test - Name !!!");
        params.validate();
    }

    private static void testValidateAppName2() throws Exception {
        DeployParams params = getParamsAppName();

        setAppName(params, "Test\nName");
        appName2TestHelper(params);

        setAppName(params, "Test\rName");
        appName2TestHelper(params);

        setAppName(params, "TestName\\");
        appName2TestHelper(params);

        setAppName(params, "Test \" Name");
        appName2TestHelper(params);
    }

    private static void appName2TestHelper(DeployParams params) throws Exception {
        try {
            params.validate();
        } catch (PackagerException pe) {
            if (!pe.getMessage().startsWith("Error: Invalid Application name")) {
                throw new Exception("Unexpected PackagerException received: " + pe);
            }

            return; // Done
        }

        throw new Exception("Expecting PackagerException");
    }

    // Returns deploy params initialized to pass all validation, except for
    // app name
    private static DeployParams getParamsAppName() {
        DeployParams params = new DeployParams();

        params.setOutput(testRoot);
        params.addResource(testRoot, new File(testRoot, "test.jar"));
        params.addBundleArgument(Arguments.CLIOptions.APPCLASS.getId(), "TestClass");
        params.addBundleArgument(Arguments.CLIOptions.MAIN_JAR.getId(), "test.jar");

        return params;
    }

    private static void setAppName(DeployParams params, String appName) {
        params.addBundleArgument(Arguments.CLIOptions.NAME.getId(), appName);
    }

    public static void main(String[] args) throws Exception {
        setUp();

        try {
            testValidateAppName1();
            testValidateAppName2();
        } finally {
            tearDown();
        }
    }

}
