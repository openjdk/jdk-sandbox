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
package jdk.jpackage.test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {

    static final Path TEST_SRC_ROOT = new Supplier<Path>() {
        @Override
        public Path get() {
            Path root = Path.of(System.getProperty("test.src"));

            for (int i = 0; i != 10; ++i) {
                if (root.resolve("apps").toFile().isDirectory()) {
                    return root.toAbsolutePath();
                }
                root = root.resolve("..");
            }

            throw new RuntimeException("Failed to locate apps directory");
        }
    }.get();

    static Path workDir() {
        return Path.of(".");
    }

    static Path defaultInputDir() {
        return workDir().resolve("input");
    }

    static Path defaultOutputDir() {
        return workDir().resolve("output");
    }

    static private void log(String v) {
        System.err.println(v);
    }

    public static void trace(String v) {
        if (TRACE) {
            log("TRACE: " + v);
        }
    }

    private static void traceAssert(String v) {
        if (TRACE_ASSERTS) {
            log("TRACE: " + v);
        }
    }

    public static void error(String v) {
        log("ERROR: " + v);
        throw new AssertionError(v);
    }

    static Path createTempDirectory() throws IOException {
        return Files.createTempDirectory("jpackage_");
    }

    static Path createTempFile(String suffix) throws IOException {
        return File.createTempFile("jpackage_", suffix).toPath();
    }

    private static String concatMessages(String msg, String msg2) {
        if (msg2 != null && !msg2.isBlank()) {
            return msg + ": " + msg2;
        }
        return msg;
    }

    public static void assertEquals(int expected, int actual, String msg) {
        if (expected != actual) {
            error(concatMessages(String.format(
                    "Expected [%d]. Actual [%d]", expected, actual),
                    msg));
        }

        traceAssert(String.format("assertEquals(%d): %s", expected, msg));
    }

    public static void assertEquals(String expected, String actual, String msg) {
        if (expected == null && actual == null) {
            return;
        }

        if (actual == null || !expected.equals(actual)) {
            error(concatMessages(String.format(
                    "Expected [%s]. Actual [%s]", expected, actual),
                    msg));
        }

        traceAssert(String.format("assertEquals(%s): %s", expected, msg));
    }

    public static void assertNotEquals(int expected, int actual, String msg) {
        if (expected == actual) {
            error(concatMessages(String.format("Unexpected [%d] value", actual),
                    msg));
        }

        traceAssert(String.format("assertNotEquals(%d, %d): %s", expected,
                actual, msg));
    }

    public static void assertTrue(boolean actual, String msg) {
        if (!actual) {
            error(concatMessages("Unexpected FALSE", msg));
        }

        traceAssert(String.format("assertTrue(): %s", msg));
    }

    public static void assertFalse(boolean actual, String msg) {
        if (actual) {
            error(concatMessages("Unexpected TRUE", msg));
        }

        traceAssert(String.format("assertFalse(): %s", msg));
    }

    public static void assertUnexpected(String msg) {
        error(concatMessages("Unexpected", msg));
    }

    private static final boolean TRACE;
    private static final boolean TRACE_ASSERTS;

    static {
        String val = System.getProperty("jpackage.test.suppress-logging");
        if (val == null) {
            TRACE = true;
            TRACE_ASSERTS = true;
        } else {
            Set<String> logOptions = Set.of(val.toLowerCase().split(","));
            TRACE = !(logOptions.contains("trace") || logOptions.contains("t"));
            TRACE_ASSERTS = !(logOptions.contains("assert") || logOptions.contains(
                    "a"));
        }
    }
}
