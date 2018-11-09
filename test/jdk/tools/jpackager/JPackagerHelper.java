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

import java.io.File;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import java.util.spi.ToolProvider;

public class JPackagerHelper {

    private static final boolean VERBOSE = false;
    private static final String OS =
            System.getProperty("os.name").toLowerCase();
    private static final String JAVA_HOME = System.getProperty("java.home");
    private static final String TEST_SRC = System.getProperty("test.src");
    private static final Path BIN_DIR = Path.of(JAVA_HOME, "bin");
    private static final Path JPACKAGER;
    private static final Path JAVAC;
    private static final Path JAR;

    static {
        if (OS.startsWith("win")) {
            JPACKAGER = BIN_DIR.resolve("jpackager.exe");
            JAVAC = BIN_DIR.resolve("javac.exe");
            JAR = BIN_DIR.resolve("jar.exe");
        } else {
            JPACKAGER = BIN_DIR.resolve("jpackager");
            JAVAC = BIN_DIR.resolve("javac");
            JAR = BIN_DIR.resolve("jar");
        }
    }

    static final ToolProvider JPACKAGER_TOOL =
            ToolProvider.findFirst("jpackager").orElseThrow(
            () -> new RuntimeException("jpackager tool not found"));

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

    private static String[] getCommand(String... args) {
        String[] command;
        if (args == null) {
            command = new String[1];
        } else {
            command = new String[args.length + 1];
        }

        int index = 0;
        command[index] = JPACKAGER.toString();

        if (args != null) {
            for (String arg : args) {
                index++;
                command[index] = arg;
            }
        }

        return command;
    }

    public static String executeCLI(boolean retValZero, String... args)
            throws Exception {
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
                throw new AssertionError("jpackager exited with error: "
                        + retVal);
            }
        } else {
            if (retVal == 0) {
                System.err.println(output);
                throw new AssertionError("jpackager exited without error: "
                        + retVal);
            }
        }

        if (VERBOSE) {
            System.out.println("output =");
            System.out.println(output);
        }

        return output;
    }

    public static String executeToolProvider(
           boolean retValZero, String... args) throws Exception {
        StringWriter writer = new StringWriter();
        PrintWriter pw = new PrintWriter(writer);
        int retVal = JPACKAGER_TOOL.run(pw, pw, args);
        String output = writer.toString();

        if (retValZero) {
            if (retVal != 0) {
                System.err.println(output);
                throw new AssertionError("jpackager exited with error: "
                        + retVal);
            }
        } else {
            if (retVal == 0) {
                System.err.println(output);
                throw new AssertionError("jpackager exited without error");
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

    public static void createHelloJar() throws Exception {
        int retVal;

        File input = new File("input");
        if (!input.exists()) {
            input.mkdir();
        }

        Files.copy(Path.of(TEST_SRC + File.separator + "Hello.java"),
                Path.of("Hello.java"));

        File javacLog = new File("javac.log");
        try {
            retVal = execute(javacLog, JAVAC.toString(), "Hello.java");
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
            retVal = execute(null, JAR.toString(), "cvf",
                    "input" + File.separator + "hello.jar", "Hello.class");
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

    public static String listToArgumentsMap(List<String> arguments,
            boolean toolProvider) {
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
                // jpackager expects us to pass arguments and properties with
                // quotes and spaces as a map
                // with quotes being escaped with additional \ for
                // internal quotes.
                // So if we want two properties below:
                // -Djnlp.Prop1=Some "Value" 1
                // -Djnlp.Prop2=Some Value 2
                // jpackager will need:
                // "-Djnlp.Prop1=\"Some \\"Value\\" 1\" -Djnlp.Prop2=\"Some Value 2\""
                // but since we using ProcessBuilder to run jpackager we will need to escape
                // our escape symbols as well, so we will need to pass string below to ProcessBuilder:
                // "-Djnlp.Prop1=\\\"Some \\\\\\\"Value\\\\\\\" 1\\\" -Djnlp.Prop2=\\\"Some Value 2\\\""
                switch (code) {
                    case '"':
                        // " -> \" -> \\\"
                        if (i == 0 || in.codePointAt(i - 1) != '\\') {
                            if (!toolProvider && isWindows()) {
                                sb.appendCodePoint('\\');
                                sb.appendCodePoint('\\');
                            }
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
                            if (isWindows()) {
                                sb.appendCodePoint('\\');
                            }
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
