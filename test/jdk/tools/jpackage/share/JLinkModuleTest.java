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

import java.util.ArrayList;

 /*
  * @test
  * @summary jpackage create image modular jar test
  * @library ../helpers
  * @build JPackageHelper
  * @build JPackagePath
  * @build Base
  * @modules jdk.jpackage
  * @run main/othervm -Xmx512m JLinkModuleTest
  */
public class JLinkModuleTest {
    private static final String OUTPUT = "output";
    private static final String RUNTIME = "runtime";

    private static final String [] CMD = {
        "--package-type", "app-image",
        "--dest", OUTPUT,
        "--name", "test",
        "--module", "com.other/com.other.Other",
        "--runtime-image", RUNTIME,
    };

    public static void main(String[] args) throws Exception {
        JPackageHelper.createOtherModule();

        ArrayList<String> jlargs = new ArrayList<>();
        jlargs.add("--add-modules");
        jlargs.add("com.other");
        jlargs.add("--module-path");
        jlargs.add("module");
        jlargs.add("--strip-debug");
        jlargs.add("--no-header-files");
        jlargs.add("--no-man-pages");
        jlargs.add("--strip-native-commands");
        JPackageHelper.createRuntime(jlargs);


        JPackageHelper.deleteOutputFolder(OUTPUT);
        Base.testCreateAppImage(CMD);

    }

}
