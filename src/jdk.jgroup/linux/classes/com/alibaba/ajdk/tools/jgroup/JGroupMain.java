/*
 * Copyright (c) 2025, Alibaba Group Holding Limited. All rights reserved.
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

package com.alibaba.ajdk.tools.jgroup;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * Launcher of the jgroup configuration tools
 * usually invoked during JDK installation.
 * <p>
 * NOTE: Privileged user permission (e.g. sudo, su) is needed!
 */
public class JGroupMain {

    /*
     * name of JGroup's initializer resource stored in jdk/tools.jar.
     * The content of the resource file will be loaded from jdk/tools.jar and writen out to a temp shell script file,
     * finally the temp shell script will be executed.
     */
    private static final String INITIALIZER_NAME = "/com/alibaba/ajdk/tools/jgroup/JGroupInitializer.sh";

    /*
     * Wait {@code TIMEOUT_MILLIS} for child process to end
     */
    private static final long TIMEOUT_MILLIS = 60_000;

    private static final String SCRIPT_PREFIX = "jgroupInit_";

    public static void main(String[] args) {
        try {
            JGroupMain jgroup = new JGroupMain();
            if (args.length == 0
                    || Stream.of(args).anyMatch(arg -> "-h".equals(arg))) {
                jgroup.showHelp();
                System.exit(0);
            }
            // do initialization
            jgroup.doInitilization(args);
        } catch (Exception e) {
            throw (e instanceof RuntimeException ? (RuntimeException)e : new RuntimeException(e));
        }
    }

    // Check if current process has permission to init jgroup
    private void checkPermission() {
        // check platform
        String osName = System.getProperty("os.name");
        if (!"Linux".equalsIgnoreCase(osName)) {
            throw new RuntimeException("Only supports Linux platform");
        }

        // check permission of writing system files
        File file = new File("/etc");
        if (!file.canWrite()) {
            throw new SecurityException("Permission denied! need WRITE permission on system files");
        }
    }

    // do initialization work!
    private void doInitilization(String[] args)
            throws TimeoutException, IOException {
        // pre-check
        checkPermission();

        // create a temporary shell script to do primary initialization work
        String scriptAbsPath = generateInitScript();

        // execute the script file to initialize jgroup in a child process
        List<String> arguments = new ArrayList<>(1 + (args == null ? 0 : args.length));
        arguments.add(scriptAbsPath);
        for (String arg : args) {
            arguments.add(arg);
        }
        ProcessBuilder pb = new ProcessBuilder(arguments);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process process = pb.start();
        if (process == null) {
            throw new RuntimeException("Failed to launch initializer process!");
        }

        // wait with timeout for child process to terminate
        try {
            process.waitFor();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if (process.isAlive()) {
            process.destroyForcibly();
            throw new TimeoutException("ERROR: Initializer process does not finish in " + TIMEOUT_MILLIS + "ms!");
        }

        int retValue = process.exitValue();
        if (retValue != 0) {
            throw new IllegalStateException("Bad exit value from initialization process: " + retValue);
        }
    }

    private String generateInitScript() throws IOException {
        // load script content
        InputStream stream = this.getClass().getResourceAsStream(INITIALIZER_NAME);

        if (stream == null) {
            throw new IOException("Cannot load [1]" + INITIALIZER_NAME);
        }

        File script = File.createTempFile(SCRIPT_PREFIX, ".sh");
        script.setExecutable(true);
        script.setWritable(true);
        script.deleteOnExit();

        // copy content of initializer resource to a temp shell script file
        OutputStream fos = new FileOutputStream(script);
        int buf = 0;
        while ((buf = stream.read()) != -1) {
            fos.write(buf);
        }
        fos.close();

        return script.getAbsolutePath();
    }

    private void showHelp() {
        System.out.println("Usage: jgroup <options>\n" +
                "(Please NOTE: jgroup command requires WRITE permission on cgroup file systems)\n" +
                "where possible options include:\n" +
                "  -h                       Show this help message\n" +
                "  -u <user>                User name for whom cgroup will be initialized\n" +
                "  -g <group>               Group name for whom cgroup will be initialized, must include <user>" +
                "\nexample:\n jdk/bin/jgroup -u admin -g admin\n"
        );
    }
}
