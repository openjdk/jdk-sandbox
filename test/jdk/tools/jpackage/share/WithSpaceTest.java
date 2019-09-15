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

 /*
 * @test
 * @summary jpackage create image test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @build Base
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m WithSpaceTest
 */
public class WithSpaceTest {
    private static final String OUTPUT = "output";

    private static final String [] CMD1 = {
        "--package-type", "app-image",
        "--input", "input dir",
        "--dest", OUTPUT,
        "--name", "test",
        "--main-jar", "hello.jar",
        "--main-class", "Hello",
    };

    private static final String [] CMD2 = {
        "--package-type", "app-image",
        "--input", "input dir2",
        "--dest", OUTPUT,
        "--name", "test",
        "--main-jar", "sub dir/hello.jar",
        "--main-class", "Hello",
    };

    public static void main(String[] args) throws Exception {

        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.createHelloImageJar("input dir");
        Base.testCreateAppImage(CMD1);

        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageHelper.createHelloImageJar(
                "input dir2" + File.separator + "sub dir");

        Base.testCreateAppImageToolProvider(CMD2);
        JPackageHelper.deleteOutputFolder(OUTPUT);
    }
}
