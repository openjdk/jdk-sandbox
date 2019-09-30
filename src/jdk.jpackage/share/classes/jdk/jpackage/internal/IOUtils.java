/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.internal;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * IOUtils
 *
 * A collection of static utility methods.
 */
public class IOUtils {

    public static void deleteRecursive(File path) throws IOException {
        if (!path.exists()) {
            return;
        }
        Path directory = path.toPath();
        Files.walkFileTree(directory, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file,
                            BasicFileAttributes attr) throws IOException {
                if (Platform.getPlatform() == Platform.WINDOWS) {
                    Files.setAttribute(file, "dos:readonly", false);
                }
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir,
                            BasicFileAttributes attr) throws IOException {
                if (Platform.getPlatform() == Platform.WINDOWS) {
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

    public static void copyRecursive(Path src, Path dest) throws IOException {
        copyRecursive(src, dest, List.of());
    }

    public static void copyRecursive(Path src, Path dest,
            final List<String> excludes) throws IOException {
        Files.walkFileTree(src, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(final Path dir,
                    final BasicFileAttributes attrs) throws IOException {
                if (excludes.contains(dir.toFile().getName())) {
                    return FileVisitResult.SKIP_SUBTREE;
                } else {
                    Files.createDirectories(dest.resolve(src.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }
            }

            @Override
            public FileVisitResult visitFile(final Path file,
                    final BasicFileAttributes attrs) throws IOException {
                if (!excludes.contains(file.toFile().getName())) {
                    Files.copy(file, dest.resolve(src.relativize(file)));
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    public static void copyFile(File sourceFile, File destFile)
            throws IOException {
        destFile.getParentFile().mkdirs();

        //recreate the file as existing copy may have weird permissions
        destFile.delete();
        destFile.createNewFile();

        try (FileChannel source = new FileInputStream(sourceFile).getChannel();
            FileChannel destination =
                    new FileOutputStream(destFile).getChannel()) {

            if (destination != null && source != null) {
                destination.transferFrom(source, 0, source.size());
            }
        }

        //preserve executable bit!
        if (sourceFile.canExecute()) {
            destFile.setExecutable(true, false);
        }
        if (!sourceFile.canWrite()) {
            destFile.setReadOnly();
        }
        destFile.setReadable(true, false);
    }

    // run "launcher paramfile" in the directory where paramfile is kept
    public static void run(String launcher, File paramFile)
            throws IOException {
        if (paramFile != null && paramFile.exists()) {
            ProcessBuilder pb =
                    new ProcessBuilder(launcher, paramFile.getName());
            pb = pb.directory(paramFile.getParentFile());
            exec(pb);
        }
    }

    public static void exec(ProcessBuilder pb)
            throws IOException {
        exec(pb, false, null);
    }

    static void exec(ProcessBuilder pb, boolean testForPresenseOnly,
            PrintStream consumer) throws IOException {
        List<String> output = new ArrayList<>();
        Executor exec = Executor.of(pb).setOutputConsumer(lines -> {
            lines.forEach(output::add);
            if (consumer != null) {
                output.forEach(consumer::println);
            }
        });

        if (testForPresenseOnly) {
            exec.execute();
        } else {
            exec.executeExpectSuccess();
        }
    }

    public static int getProcessOutput(List<String> result, String... args)
            throws IOException, InterruptedException {

        ProcessBuilder pb = new ProcessBuilder(args);

        final Process p = pb.start();

        List<String> list = new ArrayList<>();

        final BufferedReader in =
                new BufferedReader(new InputStreamReader(p.getInputStream()));
        final BufferedReader err =
                new BufferedReader(new InputStreamReader(p.getErrorStream()));

        Thread t = new Thread(() -> {
            try {
                String line;
                while ((line = in.readLine()) != null) {
                    list.add(line);
                }
            } catch (IOException ioe) {
                Log.verbose(ioe);
            }

            try {
                String line;
                while ((line = err.readLine()) != null) {
                    Log.error(line);
                }
            } catch (IOException ioe) {
                  Log.verbose(ioe);
            }
        });
        t.setDaemon(true);
        t.start();

        int ret = p.waitFor();

        result.clear();
        result.addAll(list);

        return ret;
    }

    static void writableOutputDir(Path outdir) throws PackagerException {
        File file = outdir.toFile();

        if (!file.isDirectory() && !file.mkdirs()) {
            throw new PackagerException("error.cannot-create-output-dir",
                    file.getAbsolutePath());
        }
        if (!file.canWrite()) {
            throw new PackagerException("error.cannot-write-to-output-dir",
                    file.getAbsolutePath());
        }
    }
}
