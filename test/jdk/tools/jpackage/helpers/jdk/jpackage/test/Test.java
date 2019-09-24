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
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardWatchEventKinds;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import jdk.jpackage.test.Functional.ThrowingConsumer;
import jdk.jpackage.test.Functional.ThrowingRunnable;
import jdk.jpackage.test.Functional.ThrowingSupplier;

final public class Test {

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

    private static class Instance implements AutoCloseable {
        Instance(String args[]) {
            assertCount = 0;

            name = enclosingMainMethodClass().getSimpleName();
            extraLogStream = openLogStream();

            currentTest = this;

            log(String.format("[ RUN      ] %s", name));
        }

        @Override
        public void close() {
            log(String.format("%s %s; checks=%d",
                    success ? "[       OK ]" : "[  FAILED  ]", name, assertCount));

            if (extraLogStream != null) {
                extraLogStream.close();
            }
        }

        void notifyAssert() {
            assertCount++;
        }

        void notifySuccess() {
            success = true;
        }

        private int assertCount;
        private boolean success;
        private final String name;
        private final PrintStream extraLogStream;
    }

    public static void run(String args[], TestBody action) {
        if (currentTest != null) {
            throw new IllegalStateException(
                    "Unexpeced nested or concurrent Test.run() call");
        }

        try (Instance instance = new Instance(args)) {
            action.run();
            instance.notifySuccess();
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        } finally {
            currentTest = null;
        }
    }

    public static interface TestBody {
        public void run() throws Exception;
    }

    public static Path workDir() {
        return Path.of(".");
    }

    static Path defaultInputDir() {
        return workDir().resolve("input");
    }

    static Path defaultOutputDir() {
        return workDir().resolve("output");
    }

    static Class enclosingMainMethodClass() {
        StackTraceElement st[] = Thread.currentThread().getStackTrace();
        for (StackTraceElement ste : st) {
            if ("main".equals(ste.getMethodName())) {
                try {
                    return Class.forName(ste.getClassName());
                } catch (ClassNotFoundException ex) {
                    throw new RuntimeException(ex);
                }
            }
        }
        return null;
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
        System.out.println(v);
        if (currentTest != null && currentTest.extraLogStream != null) {
            currentTest.extraLogStream.println(v);
        }
    }

    public static Class getTestClass () {
        return enclosingMainMethodClass();
    }

    public static void createPropertiesFile(Path propsFilename,
            Collection<Map.Entry<String, String>> props) {
        trace(String.format("Create [%s] properties file...",
                propsFilename.toAbsolutePath().normalize()));
        try {
            Files.write(propsFilename, props.stream().peek(e -> trace(
                    String.format("%s=%s", e.getKey(), e.getValue()))).map(
                    e -> String.format("%s=%s", e.getKey(), e.getValue())).collect(
                            Collectors.toList()));
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        trace("Done");
    }

    public static void createPropertiesFile(Path propsFilename,
            Map.Entry<String, String>... props) {
        createPropertiesFile(propsFilename, List.of(props));
    }

    public static void createPropertiesFile(Path propsFilename,
            Map<String, String> props) {
        createPropertiesFile(propsFilename, props.entrySet());
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

    private static final String TEMP_FILE_PREFIX = null;

    public static Path createTempDirectory() throws IOException {
        return Files.createTempDirectory(workDir(), TEMP_FILE_PREFIX);
    }

    public static Path createTempFile(String suffix) throws IOException {
        return Files.createTempFile(workDir(), TEMP_FILE_PREFIX, suffix);
    }

    public static void withTempFile(String suffix, ThrowingConsumer<Path> action) {
        final Path tempFile = ThrowingSupplier.toSupplier(() -> createTempFile(
                suffix)).get();
        boolean keepIt = true;
        try {
            ThrowingConsumer.toConsumer(action).accept(tempFile);
            keepIt = false;
        } finally {
            if (tempFile != null && !keepIt) {
                ThrowingRunnable.toRunnable(() -> Files.deleteIfExists(tempFile)).run();
            }
        }
    }

    public static void withTempDirectory(ThrowingConsumer<Path> action) {
        final Path tempDir = ThrowingSupplier.toSupplier(
                () -> createTempDirectory()).get();
        boolean keepIt = true;
        try {
            ThrowingConsumer.toConsumer(action).accept(tempDir);
            keepIt = false;
        } finally {
            if (tempDir != null && tempDir.toFile().isDirectory() && !keepIt) {
                deleteDirectoryRecursive(tempDir);
            }
        }
    }

    static void deleteDirectoryRecursive(Path path) {
        ThrowingRunnable.toRunnable(() -> Files.walk(path).sorted(
                Comparator.reverseOrder()).map(Path::toFile).forEach(
                File::delete)).run();
    }

    static void waitForFileCreated(Path fileToWaitFor,
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

    public static void assertEquals(long expected, long actual, String msg) {
        currentTest.notifyAssert();
        if (expected != actual) {
            error(concatMessages(String.format(
                    "Expected [%d]. Actual [%d]", expected, actual),
                    msg));
        }

        traceAssert(String.format("assertEquals(%d): %s", expected, msg));
    }

    public static void assertNotEquals(long expected, long actual, String msg) {
        currentTest.notifyAssert();
        if (expected == actual) {
            error(concatMessages(String.format("Unexpected [%d] value", actual),
                    msg));
        }

        traceAssert(String.format("assertNotEquals(%d, %d): %s", expected,
                actual, msg));
    }

    public static void assertEquals(String expected, String actual, String msg) {
        currentTest.notifyAssert();
        if ((actual != null && !actual.equals(expected))
                || (expected != null && !expected.equals(actual))) {
            error(concatMessages(String.format(
                    "Expected [%s]. Actual [%s]", expected, actual),
                    msg));
        }

        traceAssert(String.format("assertEquals(%s): %s", expected, msg));
    }

    public static void assertNotEquals(String expected, String actual, String msg) {
        currentTest.notifyAssert();
        if ((actual != null && !actual.equals(expected))
                || (expected != null && !expected.equals(actual))) {

            traceAssert(String.format("assertNotEquals(%s, %s): %s", expected,
                actual, msg));
            return;
        }

        error(concatMessages(String.format("Unexpected [%s] value", actual), msg));
    }

    public static void assertNull(Object value, String msg) {
        currentTest.notifyAssert();
        if (value != null) {
            error(concatMessages(String.format("Unexpected not null value [%s]",
                    value), msg));
        }

        traceAssert(String.format("assertNull(): %s", msg));
    }

    public static void assertNotNull(Object value, String msg) {
        currentTest.notifyAssert();
        if (value == null) {
            error(concatMessages("Unexpected null value", msg));
        }

        traceAssert(String.format("assertNotNull(%s): %s", value, msg));
    }

    public static void assertTrue(boolean actual, String msg) {
        currentTest.notifyAssert();
        if (!actual) {
            error(concatMessages("Unexpected FALSE", msg));
        }

        traceAssert(String.format("assertTrue(): %s", msg));
    }

    public static void assertFalse(boolean actual, String msg) {
        currentTest.notifyAssert();
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

    public static void assertReadableFileExists(Path path) {
        assertFileExists(path, true);
        assertTrue(path.toFile().canRead(), String.format(
                "Check [%s] file is readable", path));
     }

    public static void assertUnexpected(String msg) {
        currentTest.notifyAssert();
        error(concatMessages("Unexpected", msg));
    }

    public static void assertStringListEquals(List<String> expected,
            List<String> actual, String msg) {
        currentTest.notifyAssert();

        if (expected.size() < actual.size()) {
            // Actual string list is longer than expected
            error(concatMessages(String.format(
                    "Actual list is longer than expected by %d elements",
                    actual.size() - expected.size()), msg));
        }

        if (actual.size() < expected.size()) {
            // Actual string list is shorter than expected
            error(concatMessages(String.format(
                    "Actual list is longer than expected by %d elements",
                    expected.size() - actual.size()), msg));
        }

        traceAssert(String.format("assertStringListEquals(): %s", msg));

        String idxFieldFormat = Functional.identity(() -> {
            int listSize = expected.size();
            int width = 0;
            while (listSize != 0) {
                listSize = listSize / 10;
                width++;
            }
            return "%" + width + "d";
        }).get();

        AtomicInteger counter = new AtomicInteger(0);
        Iterator<String> actualIt = actual.iterator();
        expected.stream().sequential().filter(expectedStr -> actualIt.hasNext()).forEach(expectedStr -> {
            int idx = counter.incrementAndGet();
            String actualStr = actualIt.next();

            if ((actualStr != null && !actualStr.equals(expectedStr))
                    || (expectedStr != null && !expectedStr.equals(actualStr))) {
                error(concatMessages(String.format(
                        "(" + idxFieldFormat + ") Expected [%s]. Actual [%s]",
                        idx, expectedStr, actualStr), msg));
            }

            traceAssert(String.format(
                    "assertStringListEquals(" + idxFieldFormat + ", %s)", idx,
                    expectedStr));
        });
    }

    private static PrintStream openLogStream() {
        if (LOG_FILE == null) {
            return null;
        }

        try {
            return new PrintStream(new FileOutputStream(LOG_FILE.toFile(), true));
        } catch (FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }
    }

    private static Instance currentTest;

    private static final boolean TRACE;
    private static final boolean TRACE_ASSERTS;

    static final boolean VERBOSE_JPACKAGE;

    static String getConfigProperty(String propertyName) {
        return System.getProperty(getConfigPropertyName(propertyName));
    }

    static String getConfigPropertyName(String propertyName) {
        return "jpackage.test." + propertyName;
    }

    static final Path LOG_FILE = Functional.identity(() -> {
        String val = getConfigProperty("logfile");
        if (val == null) {
            return null;
        }
        return Path.of(val);
    }).get();

    static {
        String val = getConfigProperty("suppress-logging");
        if (val == null) {
            TRACE = true;
            TRACE_ASSERTS = true;
            VERBOSE_JPACKAGE = true;
        } else if ("all".equals(val.toLowerCase())) {
            TRACE = false;
            TRACE_ASSERTS = false;
            VERBOSE_JPACKAGE = false;
        } else {
            Set<String> logOptions = Set.of(val.toLowerCase().split(","));
            TRACE = !(logOptions.contains("trace") || logOptions.contains("t"));
            TRACE_ASSERTS = !(logOptions.contains("assert") || logOptions.contains(
                    "a"));
            VERBOSE_JPACKAGE = !(logOptions.contains("jpackage") || logOptions.contains(
                    "jp"));
        }
    }

    private static final String OS = System.getProperty("os.name").toLowerCase();
}