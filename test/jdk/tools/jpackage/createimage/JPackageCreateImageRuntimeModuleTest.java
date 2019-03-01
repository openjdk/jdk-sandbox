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

 /*
 * @test
 * @summary jpackage create image runtime test
 * @library ../helpers
 * @build JPackageHelper
 * @build JPackagePath
 * @build JPackageCreateImageRuntimeBase
 * @modules jdk.jpackage
 * @run main/othervm -Xmx512m JPackageCreateImageRuntimeModuleTest
 */
public class JPackageCreateImageRuntimeModuleTest {
    private static final String OUTPUT = "output";
    private static final String [] CMD = {
        "create-image",
        "--runtime-image", "runtime",
        "--output", OUTPUT,
        "--name", "test",
        "--win-console",
        "--module", "com.hello/com.hello.Hello",
        "--module-path", "input"};

    public static void main(String[] args) throws Exception {
        JPackageHelper.createHelloModule();
        JPackageHelper.createRuntime();
        JPackageCreateImageRuntimeBase.testCreateImage(CMD);
        JPackageHelper.deleteOutputFolder(OUTPUT);
        JPackageCreateImageRuntimeBase.testCreateImageToolProvider(CMD);
    }

}
