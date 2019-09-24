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

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.io.IOException;

public class Hello {

    private static final String MSG = "jpackage test application";
    private static final int EXPECTED_NUM_OF_PARAMS = 3; // Starts at 1

    public static void main(String[] args) throws IOException {
        printArgs(args, System.out);

        try (PrintStream out = new PrintStream(new BufferedOutputStream(
                new FileOutputStream("appOutput.txt")))) {
            printArgs(args, out);
        }
    }

    private static void printArgs(String[] args, PrintStream out) {
        out.println(MSG);

        out.println("args.length: " + args.length);

        for (String arg : args) {
            out.println(arg);
        }

        for (int index = 1; index <= EXPECTED_NUM_OF_PARAMS; index++) {
            String value = System.getProperty("param" + index);
            if (value != null) {
                out.println("-Dparam" + index + "=" + value);
            }
        }
    }
}
