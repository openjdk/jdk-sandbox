/*
 * Copyright (c) 2011, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.jpackage.main;

import jdk.jpackage.internal.Arguments;
import jdk.jpackage.internal.Log;
import jdk.jpackage.internal.CLIHelp;
import java.io.PrintWriter;
import java.util.ResourceBundle;
import java.io.IOException;

public class Main {

    private static final ResourceBundle bundle = ResourceBundle.getBundle(
            "jdk.jpackage.internal.resources.MainResources");

    private static final String version = bundle.getString("MSG_Version")
            + " " + System.getProperty("java.version");

    /**
     * main(String... args)
     * This is the entry point for the jpackage tool.
     *
     * @param args command line arguments
     */
    public static void main(String... args) throws Exception {
        // Create logger with default system.out and system.err
        Log.Logger logger = new Log.Logger(false);
        Log.setLogger(logger);

        int status = new jdk.jpackage.main.Main().execute(args);
        System.exit(status);
    }

    /**
     * execute() - this is the entry point for the ToolProvider API.
     *
     * @param out output stream
     * @param err error output stream
     * @param args command line arguments
     * @return an exit code. 0 means success, non-zero means an error occurred.
     */
    public int execute(PrintWriter out, PrintWriter err, String... args) {
        // Create logger with provided streams
        Log.Logger logger = new Log.Logger(false);
        logger.setPrintWriter(out, err);
        Log.setLogger(logger);

        return execute(args);
    }

    private int execute(String... args) {
        try {
            String[] newArgs;
            try {
                newArgs = CommandLine.parse(args);
            } catch (IOException ioe) {
                Log.error(ioe.getMessage());
                return 1;
            }

            if (newArgs.length == 0) {
                CLIHelp.showHelp(true);
            } else if (hasHelp(newArgs)){
                if (hasVersion(newArgs)) {
                    Log.info(version + "\n");
                }
                CLIHelp.showHelp(false);
            } else if (hasVersion(newArgs)) {
                Log.info(version);
            } else {
                Arguments arguments = new Arguments(newArgs);
                if (!arguments.processArguments()) {
                    // processArguments() will log error message if failed.
                    return 1;
                }
            }
            return 0;
        } finally {
            Log.flush();
        }
    }

    private boolean hasHelp(String[] args) {
        for (String a : args) {
            if ("--help".equals(a) || "-h".equals(a)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasVersion(String[] args) {
        for (String a : args) {
            if ("--version".equals(a)) {
                return true;
            }
        }
        return false;
    }

}
