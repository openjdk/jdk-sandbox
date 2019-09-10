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
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

public class Test {

    public static final Path TEST_SRC_ROOT = new Supplier<Path>() {
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

    public static Path workDir() {
        return Path.of(".");
    }

    static Path defaultInputDir() {
        return workDir().resolve("input");
    }

    static Path defaultOutputDir() {
        return workDir().resolve("output");
    }

    static boolean isWindows() {
        return (OS.contains("win"));
    }

    static boolean isOSX() {
        return (OS.contains("mac"));
    }

    static boolean isLinux() {
        return ((OS.contains("nix") || OS.contains("nux")));
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

    public static Path createTempDirectory() throws IOException {
        return Files.createTempDirectory("jpackage_");
    }

    public static Path createTempFile(String suffix) throws IOException {
        return File.createTempFile("jpackage_", suffix).toPath();
    }

    public static void waitForFileCreated(Path fileToWaitFor,
            long timeoutSeconds) throws IOException {

        trace(String.format("Wait for file [%s] to be available", fileToWaitFor));

        WatchService ws = FileSystems.getDefault().newWatchService();

        Path watchDirectory = fileToWaitFor.toAbsolutePath().getParent();
        watchDirectory.register(ws, ENTRY_CREATE, ENTRY_MODIFY);

        long waitUntil = System.currentTimeMillis() + timeoutSeconds * 1000;
        for (;;) {
            long timeout = waitUntil - System.currentTimeMillis();
            assertTrue(timeout > 0, String.format(
                    "Check timeout value %d is positive", timeout));

            WatchKey key = null;
            try {
                key = ws.poll(timeout, TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                throw new RuntimeException(ex);
            }

            if (key == null) {
                if (fileToWaitFor.toFile().exists()) {
                    trace(String.format(
                            "File [%s] is available after poll timeout expired",
                            fileToWaitFor));
                    return;
                }
                assertUnexpected(String.format("Timeout expired", timeout));
            }

            for (WatchEvent<?> event : key.pollEvents()) {
                if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                    continue;
                }
                Path contextPath = (Path) event.context();
                if (Files.isSameFile(watchDirectory.resolve(contextPath),
                        fileToWaitFor)) {
                    trace(String.format("File [%s] is available", fileToWaitFor));
                    return;
                }
            }

            if (!key.reset()) {
                assertUnexpected("Watch key invalidated");
            }
        }
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

    public static void assertNull(Object value, String msg) {
        if (value != null) {
            error(concatMessages(String.format("Unexpected not null value [%s]",
                    value), msg));
        }

        traceAssert(String.format("assertNull(): %s", msg));
    }

    public static void assertNotNull(Object value, String msg) {
        if (value == null) {
            error(concatMessages("Unexpected null value", msg));
        }

        traceAssert(String.format("assertNotNull(%s): %s", value, msg));
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

    public static void assertPathExists(Path path, boolean exists) {
        if (exists) {
            assertTrue(path.toFile().exists(), String.format(
                    "Check [%s] path exists", path));
        } else {
            assertFalse(path.toFile().exists(), String.format(
                    "Check [%s] path doesn't exist", path));
        }
    }

    public static void assertDirectoryExists(Path path, boolean exists) {
        assertPathExists(path, exists);
        if (exists) {
            assertTrue(path.toFile().isDirectory(), String.format(
                    "Check [%s] is a directory", path));
        }
    }

    public static void assertFileExists(Path path, boolean exists) {
        assertPathExists(path, exists);
        if (exists) {
            assertTrue(path.toFile().isFile(), String.format(
                    "Check [%s] is a file", path));
        }
    }

    public static void assertExecutableFileExists(Path path, boolean exists) {
        assertFileExists(path, exists);
        if (exists) {
            assertTrue(path.toFile().canExecute(), String.format(
                    "Check [%s] file is executable", path));
        }
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

    private static final String OS = System.getProperty("os.name").toLowerCase();
}
