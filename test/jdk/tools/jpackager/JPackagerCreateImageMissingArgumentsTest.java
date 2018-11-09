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

 /*
 * @test
 * @summary jpackager create image missing arguments test
 * @build JPackagerHelper
 * @modules jdk.jpackager
 * @run main/othervm -Xmx512m JPackagerCreateImageMissingArgumentsTest
 */

public class JPackagerCreateImageMissingArgumentsTest {
    private static final String [] RESULT_1 = {"--output" };
    private static final String [] CMD_1 = {
        "create-image",
        "--input", "input",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--force",
        "--files", "hello.jar"};

    private static final String [] RESULT_2 = {"--input"};
    private static final String [] CMD_2 = {
        "create-image",
        "--output", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--force",
        "--files", "hello.jar"};

    private static final String [] RESULT_3 = {"--input", "--app-image"};
    private static final String [] CMD_3 = {
        "create-installer",
        "--output", "output",
        "--name", "test",
        "--main-jar", "hello.jar",
        "--class", "Hello",
        "--force",
        "--files", "hello.jar"};

    private static void validate(String output, String [] expected)
           throws Exception {
        String[] result = output.split("\n");
        if (result.length != 1) {
            System.err.println(output);
            throw new AssertionError("Invalid number of lines in output: "
                    + result.length);
        }

        for (String s : expected) {
            if (!result[0].contains(s)) {
                System.err.println("Expected to contain: " + s);
                System.err.println("Actual: " + result[0]);
                throw new AssertionError("Unexpected error message");
            }
        }
    }

    private static void testMissingArg() throws Exception {
        String output = JPackagerHelper.executeCLI(false, CMD_1);
        validate(output, RESULT_1);

        output = JPackagerHelper.executeCLI(false, CMD_2);
        validate(output, RESULT_2);

        output = JPackagerHelper.executeCLI(false, CMD_3);
        validate(output, RESULT_3);
    }

    private static void testMissingArgToolProvider() throws Exception {
        String output = JPackagerHelper.executeToolProvider(false, CMD_1);
        validate(output, RESULT_1);

        output = JPackagerHelper.executeToolProvider(false, CMD_2);
        validate(output, RESULT_2);

        output = JPackagerHelper.executeToolProvider(false, CMD_3);
        validate(output, RESULT_3);
    }

    public static void main(String[] args) throws Exception {
        JPackagerHelper.createHelloJar();
        testMissingArg();
        testMissingArgToolProvider();
    }

}
