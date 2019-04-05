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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class JPackageCreateAppImageAddLauncherBase {
    private static final String app = JPackagePath.getApp();
    private static final String app2 = JPackagePath.getAppSL();
    private static final String appOutput = JPackagePath.getAppOutputFile();
    private static final String appWorkingDir = JPackagePath.getAppWorkingDir();

    // Note: quotes in argument for add launcher is not support by test
    private static final String ARGUMENT1 = "argument 1";
    private static final String ARGUMENT2 = "argument 2";
    private static final String ARGUMENT3 = "argument 3";

    private static final List<String> arguments = new ArrayList<>();

    private static final String PARAM1 = "-Dparam1=Some Param 1";
    private static final String PARAM2 = "-Dparam2=Some Param 2";
    private static final String PARAM3 = "-Dparam3=Some Param 3";

    private static final List<String> vmArguments = new ArrayList<>();

    private static void validateResult(List<String> args, List<String> vmArgs)
            throws Exception {
        File outfile = new File(appWorkingDir + File.separator + appOutput);
        if (!outfile.exists()) {
            throw new AssertionError(appOutput + " was not created");
        }

        String output = Files.readString(outfile.toPath());
        String[] result = output.split("\n");

        if (result.length != (args.size() + vmArgs.size() + 2)) {
            throw new AssertionError("Unexpected number of lines: "
                    + result.length);
        }

        if (!result[0].trim().equals("jpackage test application")) {
            throw new AssertionError("Unexpected result[0]: " + result[0]);
        }

        if (!result[1].trim().equals("args.length: " + args.size())) {
            throw new AssertionError("Unexpected result[1]: " + result[1]);
        }

        int index = 2;
        for (String arg : args) {
            if (!result[index].trim().equals(arg)) {
                throw new AssertionError("Unexpected result[" + index + "]: "
                    + result[index]);
            }
            index++;
        }

        for (String vmArg : vmArgs) {
            if (!result[index].trim().equals(vmArg)) {
                throw new AssertionError("Unexpected result[" + index + "]: "
                    + result[index]);
            }
            index++;
        }
    }

    private static void validate() throws Exception {
        int retVal = JPackageHelper.execute(null, app);
        if (retVal != 0) {
            throw new AssertionError("Test application exited with error: "
                    + retVal);
        }
        validateResult(new ArrayList<>(), new ArrayList<>());

        retVal = JPackageHelper.execute(null, app2);
        if (retVal != 0) {
            throw new AssertionError("Test application exited with error: "
                    + retVal);
        }
        validateResult(arguments, vmArguments);
    }

    public static void testCreateAppImage(String [] cmd) throws Exception {
        JPackageHelper.executeCLI(true, cmd);
        validate();
    }

    public static void testCreateAppImageToolProvider(String [] cmd) throws Exception {
        JPackageHelper.executeToolProvider(true, cmd);
        validate();
    }

    public static void createSLProperties() throws Exception {
        arguments.add(ARGUMENT1);
        arguments.add(ARGUMENT2);
        arguments.add(ARGUMENT3);

        String argumentsMap =
                JPackageHelper.listToArgumentsMap(arguments, true);

        vmArguments.add(PARAM1);
        vmArguments.add(PARAM2);
        vmArguments.add(PARAM3);

        String vmArgumentsMap =
                JPackageHelper.listToArgumentsMap(vmArguments, true);

        try (PrintWriter out = new PrintWriter(new BufferedWriter(
                new FileWriter("sl.properties")))) {
            out.println("name=test2");
            out.println("arguments=" + argumentsMap);
            out.println("java-options=" + vmArgumentsMap);
        }
    }

}
