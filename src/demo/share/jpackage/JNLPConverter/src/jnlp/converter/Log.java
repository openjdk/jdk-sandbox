/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package jnlp.converter;

public class Log {
    private static boolean verbose = false;

    public static void setVerbose(boolean verbose) {
        Log.verbose = verbose;
    }

    public static boolean isVerbose() {
        return verbose;
    }

    public static void verbose(String msg) {
        if (verbose) {
            System.out.println(msg);
        }
    }

    public static void info(String msg) {
        System.out.println("Info: " + msg);
    }

    public static void warning(String msg) {
        System.err.println("Warning: " + msg);
    }

    public static void error(String msg) {
        System.err.println("Error: " + msg);
        System.exit(1);
    }
}
