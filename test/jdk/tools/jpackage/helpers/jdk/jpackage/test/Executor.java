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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class Executor extends CommandArguments<Executor> {

    public Executor() {
        saveOutputType = SaveOutputType.NONE;
    }

    public Executor setExecutable(String v) {
        executable = v;
        return this;
    }

    public Executor setDirectory(Path v) {
        directory = v;
        return this;
    }

    public Executor setExecutable(JavaTool v) {
        return setExecutable(v.getPath().getAbsolutePath());
    }

    public Executor saveOutput() {
        saveOutputType = SaveOutputType.FULL;
        return this;
    }

    public Executor saveFirstLineOfOutput() {
        saveOutputType = SaveOutputType.FIRST_LINE;
        return this;
    }

    public class Result {

        Result(int exitCode) {
            this.exitCode = exitCode;
        }

        public String getFirstLineOfOutput() {
            return output.get(0).trim();
        }

        public List<String> getOutput() {
            return output;
        }

        public String getPrintableCommandLine() {
            return Executor.this.getPrintableCommandLine();
        }

        public Result assertExitCodeIs(int expectedExitCode) {
            Test.assertEquals(expectedExitCode, exitCode, String.format(
                    "Check command %s exited with %d code",
                    getPrintableCommandLine(), expectedExitCode));
            return this;
        }

        public Result assertExitCodeIsZero() {
            return assertExitCodeIs(0);
        }

        int exitCode;
        List<String> output;
    }

    public Result execute() {
        try {
            return executeImpl();
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Result executeImpl() throws Exception {
        List<String> command = new ArrayList<>();
        command.add(executable);
        command.addAll(args);
        Path outputFile = null;
        ProcessBuilder builder = new ProcessBuilder(command);
        StringBuilder sb = new StringBuilder(getPrintableCommandLine());
        if (saveOutputType != SaveOutputType.NONE) {
            outputFile = Test.createTempFile(".out");
            builder.redirectErrorStream(true);
            builder.redirectOutput(outputFile.toFile());
            sb.append(String.format("; redirect output to [%s]", outputFile));
        }
        if (directory != null) {
            builder.directory(directory.toFile());
            sb.append(String.format("; in directory [%s]", directory));
        }

        try {
            Test.trace("Execute " + sb.toString() + "...");
            Process process = builder.start();
            Result reply = new Result(process.waitFor());
            Test.trace("Done. Exit code: " + reply.exitCode);
            if (saveOutputType == SaveOutputType.FIRST_LINE) {
                reply.output = Arrays.asList(
                        Files.readAllLines(outputFile).stream().findFirst().get());
            } else if (saveOutputType == SaveOutputType.FULL) {
                reply.output = Collections.unmodifiableList(Files.readAllLines(
                        outputFile));
            }
            return reply;
        } finally {
            if (outputFile != null) {
                Files.deleteIfExists(outputFile);
            }
        }
    }

    public String getPrintableCommandLine() {
        return "[" + executable + "]; args(" + args.size() + ")=" + Arrays.toString(
                args.toArray());
    }

    private String executable;
    private SaveOutputType saveOutputType;
    private Path directory;

    private static enum SaveOutputType {
        NONE, FULL, FIRST_LINE
    };
}
