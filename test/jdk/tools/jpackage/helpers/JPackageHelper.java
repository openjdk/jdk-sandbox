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

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.FileVisitResult;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import java.util.spi.ToolProvider;

public class JPackageHelper {

    private static final boolean VERBOSE = false;
    private static final String OS = System.getProperty("os.name").toLowerCase();
    private static final String JAVA_HOME = System.getProperty("java.home");
    public static final String TEST_SRC_ROOT;
    public static final String TEST_SRC;
    private static final Path BIN_DIR = Path.of(JAVA_HOME, "bin");
    private static final Path JPACKAGE;
    private static final Path JAVAC;
    private static final Path JAR;
    private static final Path JLINK;

    static {
        if (OS.startsWith("win")) {
            JPACKAGE = BIN_DIR.resolve("jpackage.exe");
            JAVAC = BIN_DIR.resolve("javac.exe");
            JAR = BIN_DIR.resolve("jar.exe");
            JLINK = BIN_DIR.resolve("jlink.exe");
        } else {
            JPACKAGE = BIN_DIR.resolve("jpackage");
            JAVAC = BIN_DIR.resolve("javac");
            JAR = BIN_DIR.resolve("jar");
            JLINK = BIN_DIR.resolve("jlink");
        }

        // Figure out test src based on where we called
        File testSrc = new File(System.getProperty("test.src") + File.separator + ".."
                + File.separator + "apps");
        if (testSrc.exists()) {
            TEST_SRC_ROOT = System.getProperty("test.src") + File.separator + "..";
        } else {
            testSrc = new File(System.getProperty("test.src") + File.separator
                    + ".." + File.separator + ".." + File.separator + "apps");
            if (testSrc.exists()) {
                TEST_SRC_ROOT = System.getProperty("test.src") + File.separator + ".."
                        + File.separator + "..";
            } else {
                testSrc = new File(System.getProperty("test.src") + File.separator
                        + ".." + File.separator + ".."  + File.separator + ".."
                        + File.separator + "apps");
                if (testSrc.exists()) {
                    TEST_SRC_ROOT = System.getProperty("test.src") + File.separator + ".."
                            + File.separator + ".." + File.separator + "..";
                } else {
                    TEST_SRC_ROOT = System.getProperty("test.src");
                }
            }
        }

        TEST_SRC = System.getProperty("test.src");
    }

    static final ToolProvider JPACKAGE_TOOL =
            ToolProvider.findFirst("jpackage").orElseThrow(
            () -> new RuntimeException("jpackage tool not found"));

    public static int execute(File out, String... command) throws Exception {
        if (VERBOSE) {
            System.out.print("Execute command: ");
            for (String c : command) {
                System.out.print(c);
                System.out.print(" ");
            }
            System.out.println();
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        if (out != null) {
            builder.redirectErrorStream(true);
            builder.redirectOutput(out);
        }

        Process process = builder.start();
        return process.waitFor();
    }

    public static Process executeNoWait(File out, String... command) throws Exception {
        if (VERBOSE) {
            System.out.print("Execute command: ");
            for (String c : command) {
                System.out.print(c);
                System.out.print(" ");
            }
            System.out.println();
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        if (out != null) {
            builder.redirectErrorStream(true);
            builder.redirectOutput(out);
        }

        return builder.start();
    }

    private static String[] getCommand(String... args) {
        String[] command;
        if (args == null) {
            command = new String[1];
        } else {
            command = new String[args.length + 1];
        }

        int index = 0;
        command[index] = JPACKAGE.toString();

        if (args != null) {
            for (String arg : args) {
                index++;
                command[index] = arg;
            }
        }

        return command;
    }

    public static void deleteRecursive(File path) throws IOException {
        if (!path.exists()) {
            return;
        }

        Path directory = path.toPath();
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                    BasicFileAttributes attr) throws IOException {
                if (OS.startsWith("win")) {
                    Files.setAttribute(file, "dos:readonly", false);
                }
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                    BasicFileAttributes attr) throws IOException {
                if (OS.startsWith("win")) {
                    Files.setAttribute(dir, "dos:readonly", false);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException e)
                    throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void deleteOutputFolder(String output) throws IOException {
        File outputFolder = new File(output);
        System.out.println("AMDEBUG output: " + outputFolder.getAbsolutePath());
        try {
            deleteRecursive(outputFolder);
        } catch (IOException ioe) {
            System.out.println("IOException: " + ioe);
            ioe.printStackTrace();
            deleteRecursive(outputFolder);
        }
    }

    public static String executeCLI(boolean retValZero, String... args) throws Exception {
        int retVal;
        File outfile = new File("output.log");
        try {
            String[] command = getCommand(args);
            retVal = execute(outfile, command);
        } catch (Exception ex) {
            if (outfile.exists()) {
                System.err.println(Files.readString(outfile.toPath()));
            }
            throw ex;
        }

        String output = Files.readString(outfile.toPath());
        if (retValZero) {
            if (retVal != 0) {
                System.err.println(output);
                throw new AssertionError("jpackage exited with error: " + retVal);
            }
        } else {
            if (retVal == 0) {
                System.err.println(output);
                throw new AssertionError("jpackage exited without error: " + retVal);
            }
        }

        if (VERBOSE) {
            System.out.println("output =");
            System.out.println(output);
        }

        return output;
    }

    public static String executeToolProvider(boolean retValZero, String... args) throws Exception {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        int retVal = JPACKAGE_TOOL.run(pw, pw, args);
        String output = writer.toString();

        if (retValZero) {
            if (retVal != 0) {
                System.err.println(output);
                throw new AssertionError("jpackage exited with error: " + retVal);
            }
        } else {
            if (retVal == 0) {
                System.err.println(output);
                throw new AssertionError("jpackage exited without error");
            }
        }

        if (VERBOSE) {
            System.out.println("output =");
            System.out.println(output);
        }

        return output;
    }

    public static boolean isWindows() {
        return (OS.contains("win"));
    }

    public static boolean isOSX() {
        return (OS.contains("mac"));
    }

    public static boolean isLinux() {
        return ((OS.contains("nix") || OS.contains("nux")));
    }

    public static void createHelloImageJar() throws Exception {
        createJar(false, "Hello", "image");
    }

    public static void createHelloImageJarWithMainClass() throws Exception {
        createJar(true, "Hello", "image");
    }

    public static void createHelloInstallerJar() throws Exception {
        createJar(false, "Hello", "installer");
    }

    public static void createHelloInstallerJarWithMainClass() throws Exception {
        createJar(true, "Hello", "installer");
    }

    private static void createJar(boolean mainClassAttribute, String name,
                                  String testType) throws Exception {
        int retVal;

        File input = new File("input");
        if (!input.exists()) {
            input.mkdir();
        }

        Files.copy(Path.of(TEST_SRC_ROOT + File.separator + "apps" + File.separator
                + testType + File.separator + name + ".java"), Path.of(name + ".java"));

        File javacLog = new File("javac.log");
        try {
            retVal = execute(javacLog, JAVAC.toString(), name + ".java");
        } catch (Exception ex) {
            if (javacLog.exists()) {
                System.err.println(Files.readString(javacLog.toPath()));
            }
            throw ex;
        }

        if (retVal != 0) {
            if (javacLog.exists()) {
                System.err.println(Files.readString(javacLog.toPath()));
            }
            throw new AssertionError("javac exited with error: " + retVal);
        }

        File jarLog = new File("jar.log");
        try {
            List<String> args = new ArrayList<>();
            args.add(JAR.toString());
            args.add("-c");
            args.add("-v");
            args.add("-f");
            args.add("input" + File.separator + name.toLowerCase() + ".jar");
            if (mainClassAttribute) {
                args.add("-e");
                args.add(name);
            }
            args.add(name + ".class");
            retVal = execute(jarLog, args.stream().toArray(String[]::new));
        } catch (Exception ex) {
            if (jarLog.exists()) {
                System.err.println(Files.readString(jarLog.toPath()));
            }
            throw ex;
        }

        if (retVal != 0) {
            if (jarLog.exists()) {
                System.err.println(Files.readString(jarLog.toPath()));
            }
            throw new AssertionError("jar exited with error: " + retVal);
        }
    }

    public static void createHelloModule() throws Exception {
        createModule("Hello.java", "input", "hello");
    }

    public static void createOtherModule() throws Exception {
        createModule("Other.java", "input-other", "other");
    }

    private static void createModule(String javaFile, String inputDir,
            String aName) throws Exception {
        int retVal;

        File input = new File(inputDir);
        if (!input.exists()) {
            input.mkdir();
        }

        File module = new File("module" + File.separator + "com." + aName);
        if (!module.exists()) {
            module.mkdirs();
        }

        File javacLog = new File("javac.log");
        try {
            List<String> args = new ArrayList<>();
            args.add(JAVAC.toString());
            args.add("-d");
            args.add("module" + File.separator + "com." + aName);
            args.add(TEST_SRC_ROOT + File.separator + "apps" + File.separator
                    + "com." + aName + File.separator + "module-info.java");
            args.add(TEST_SRC_ROOT + File.separator + "apps"
                    + File.separator + "com." + aName + File.separator + "com"
                    + File.separator + aName + File.separator + javaFile);
            retVal = execute(javacLog, args.stream().toArray(String[]::new));
        } catch (Exception ex) {
            if (javacLog.exists()) {
                System.err.println(Files.readString(javacLog.toPath()));
            }
            throw ex;
        }

        if (retVal != 0) {
            if (javacLog.exists()) {
                System.err.println(Files.readString(javacLog.toPath()));
            }
            throw new AssertionError("javac exited with error: " + retVal);
        }

        File jarLog = new File("jar.log");
        try {
            List<String> args = new ArrayList<>();
            args.add(JAR.toString());
            args.add("--create");
            args.add("--file");
            args.add(inputDir + File.separator + "com." + aName + ".jar");
            args.add("-C");
            args.add("module" + File.separator + "com." + aName);
            args.add(".");

            retVal = execute(jarLog, args.stream().toArray(String[]::new));
        } catch (Exception ex) {
            if (jarLog.exists()) {
                System.err.println(Files.readString(jarLog.toPath()));
            }
            throw ex;
        }

        if (retVal != 0) {
            if (jarLog.exists()) {
                System.err.println(Files.readString(jarLog.toPath()));
            }
            throw new AssertionError("jar exited with error: " + retVal);
        }
    }

    public static void createRuntime() throws Exception {
        int retVal;

        File jlinkLog = new File("jlink.log");
        try {
            List<String> args = new ArrayList<>();
            args.add(JLINK.toString());
            args.add("--output");
            args.add("runtime");
            args.add("--add-modules");
            args.add("java.base");
            retVal = execute(jlinkLog, args.stream().toArray(String[]::new));
        } catch (Exception ex) {
            if (jlinkLog.exists()) {
                System.err.println(Files.readString(jlinkLog.toPath()));
            }
            throw ex;
        }

        if (retVal != 0) {
            if (jlinkLog.exists()) {
                System.err.println(Files.readString(jlinkLog.toPath()));
            }
            throw new AssertionError("jlink exited with error: " + retVal);
        }
    }

    public static String listToArgumentsMap(List<String> arguments, boolean toolProvider) {
        if (arguments.isEmpty()) {
            return "";
        }

        String argsStr = "";
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            argsStr += quote(arg, toolProvider);
            if ((i + 1) != arguments.size()) {
                argsStr += " ";
            }
        }

        if (!toolProvider && isWindows()) {
            if (argsStr.contains(" ")) {
                if (argsStr.contains("\"")) {
                    argsStr = escapeQuote(argsStr, toolProvider);
                }
                argsStr = "\"" + argsStr + "\"";
            }
        }
        return argsStr;
    }

    private static String quote(String in, boolean toolProvider) {
        if (in == null) {
            return null;
        }

        if (in.isEmpty()) {
            return "";
        }

        if (!in.contains("=")) {
            // Not a property
            if (in.contains(" ")) {
                in = escapeQuote(in, toolProvider);
                return "\"" + in + "\"";
            }
            return in;
        }

        if (!in.contains(" ")) {
            return in; // No need to quote
        }

        int paramIndex = in.indexOf("=");
        if (paramIndex <= 0) {
            return in; // Something wrong, just skip quoting
        }

        String param = in.substring(0, paramIndex);
        String value = in.substring(paramIndex + 1);

        if (value.length() == 0) {
            return in; // No need to quote
        }

        value = escapeQuote(value, toolProvider);

        return param + "=" + "\"" + value + "\"";
    }

    private static String escapeQuote(String in, boolean toolProvider) {
        if (in == null) {
            return null;
        }

        if (in.isEmpty()) {
            return "";
        }

        if (in.contains("\"")) {
            // Use code points to preserve non-ASCII chars
            StringBuilder sb = new StringBuilder();
            int codeLen = in.codePointCount(0, in.length());
            for (int i = 0; i < codeLen; i++) {
                int code = in.codePointAt(i);
                // Note: No need to escape '\' on Linux or OS X
                // jpackage expects us to pass arguments and properties with
                // quotes and spaces as a map
                // with quotes being escaped with additional \ for
                // internal quotes.
                // So if we want two properties below:
                // -Djnlp.Prop1=Some "Value" 1
                // -Djnlp.Prop2=Some Value 2
                // jpackage will need:
                // "-Djnlp.Prop1=\"Some \\"Value\\" 1\" -Djnlp.Prop2=\"Some Value 2\""
                // but since we using ProcessBuilder to run jpackage we will need to escape
                // our escape symbols as well, so we will need to pass string below to ProcessBuilder:
                // "-Djnlp.Prop1=\\\"Some \\\\\\\"Value\\\\\\\" 1\\\" -Djnlp.Prop2=\\\"Some Value 2\\\""
                switch (code) {
                    case '"':
                        // " -> \" -> \\\"
                        if (i == 0 || in.codePointAt(i - 1) != '\\') {
                            sb.appendCodePoint('\\');
                            sb.appendCodePoint(code);
                        }
                        break;
                    case '\\':
                        // We need to escape already escaped symbols as well
                        if ((i + 1) < codeLen) {
                            int nextCode = in.codePointAt(i + 1);
                            if (nextCode == '"') {
                                // \" -> \\\"
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint(nextCode);
                            } else {
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint(code);
                            }
                        } else {
                            sb.appendCodePoint(code);
                        }
                        break;
                    default:
                        sb.appendCodePoint(code);
                        break;
                }
            }
            return sb.toString();
        }

        return in;
    }

}
