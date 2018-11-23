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

package jnlp.converter;

import java.io.File;

public class Main {

    private static void showHelp() {
        Options.showHelp();
    }

    private static void showVersion() {
        System.out.println("Version: 1.0");
    }

    private static void createBundle(Options options) {
        Log.verbose("Creating bundle for JNLP: " + options.getJNLP());
        Log.verbose("Output folder: " + options.getOutput());

        JNLPConverter converter = new JNLPConverter(options);
        converter.convert();
    }

    private static void validateJDK() {
        String jpackagePath = JNLPConverter.getJPackagePath();
        File file = new File(jpackagePath);
        if (!file.exists()) {
            Log.error("Cannot find " + jpackagePath + ". Make sure you running JNLPConverter with supported JDK version.");
        }
    }

    public static void main(String[] args) {
        Options options = Options.parseArgs(args); // Only valid options will be returned

        Log.setVerbose(options.verbose());

        validateJDK();

        if (options.help()) {
            showHelp();
        } else if (options.version()) {
            showVersion();
        } else {
            createBundle(options);
        }

        System.exit(0);
    }
}
