/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package sun.tools.attach;

import com.sun.tools.attach.AttachNotSupportedException;
import com.sun.tools.attach.spi.AttachProvider;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringBufferInputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.concurrent.*;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * Core dump implementation of HotSpotVirtualMachine
 */
@SuppressWarnings("restricted")
public class VirtualMachineCoreDumpImpl extends HotSpotVirtualMachine {

    protected boolean attached;
    protected String filename;
    protected List<String> libDirs;
    protected String revivalCachePath;

    /**
     * Attaches to a core file or minidump.
     */
    VirtualMachineCoreDumpImpl(AttachProvider provider, String vmid, Map<String, ?> env)
            throws AttachNotSupportedException, IOException {

        // Superclass HotSpotVirtualMachine modified to accept String that is not a PID.
        super(provider, vmid);

        filename = vmid;
        if (env != null) {
            if (env.containsKey("libDirs")) {
                String x = (String) env.get("libDirs");
                libDirs = List.of(x.split(File.pathSeparator));
            }  else {
                libDirs = null;
            }
            revivalCachePath = (String) env.get("revivalCachePath");
        } else {
            libDirs = null;
            revivalCachePath = null;
        }
        attach();
    }

    protected void attach() throws IOException {
        // Process revival from a core or minidump to enable executing diagnostic commands.
        // This happens in a new process.
        attached = true;
    }

    /**
     * Detach from the target VM
     */
    public void detach() throws IOException {
        attached = false;
    }

    private void checkAttached() throws IOException {
        if (!attached) {
            throw new IOException("Not attached");
        }
    }

    private static final int HELPER_TRIES = 10; // Default attempts to run helper
    private static final int HELPER_RETRY = 7;  // revivalhelper exit value hint to retry due to e.g. address space clash

    /**
     * Execute the given command in the target VM.
     */
    @SuppressWarnings("deprecation")
    InputStream execute(String cmd, Object ... args) throws IOException {
        checkNulls(args);

        // Only 'jcmd' the command operation is implemented on a core/minidump.
        if (!cmd.equals("jcmd")) {
            throw new IOException("command '" + cmd + "' not implemented");
        }
        // An IOException thrown above for any command other than jcmd would be correct
        // behavior when not attached.  The "not implemented" message is more informative.
        checkAttached();

        // Invoke "JDK/lib/revivalhelper corefilename jcmd command..."
        // (Putting revivalhelper in JDK/lib works as jlink is updated also,
        // see DefaultImageBuilder.java and its jspawnhelper/jexec special case).
        String jdkLibDir = System.getProperty("java.home") + File.separator + "lib";
        String helper = jdkLibDir + File.separator + "revivalhelper"
                        + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "");
        if (!(new File(helper).exists())) {
            throw new IOException("jcmd helper '" + helper + "' not found");
        }
        List<String> pargs = new ArrayList<String>();
        pargs.add(helper);

        if (libDirs != null) {
            for (String s : libDirs) {
                pargs.add("-L" + s); // Pass library directory as -L/path
            }
        }
        if (revivalCachePath != null) {
            pargs.add("-R" + revivalCachePath);
        }

        pargs.add(filename);
        pargs.add(cmd);
        for (Object o : args) {
            pargs.add((String) o);
        }

        ProcessBuilder pb = new ProcessBuilder(pargs);
        pb.redirectErrorStream(true); // merge error with output

        // Some System Properties are passed on to the native revival helper tool in the environment:
        Map<String, String> newEnv = pb.environment();

        String path = System.getenv("PATH");
        newEnv.put("PATH", System.getProperty("java.home") + File.separator + "bin" + File.pathSeparator + path);

        // "verbose" is a log level, VERBOSE or DEBUG are recognised by the native helper, but do not want to
        // confuse with a Java Logger.
        String logString = System.getProperty("jdk.attach.core.log");
        boolean verbose = false; // Used in this method as well as native helper
        if (logString != null) {
            // Pass on 
            logString = logString.toLowerCase();
            newEnv.put("REVIVAL_LOG", logString);
            verbose = logString.equals("verbose") || logString.equals("debug");
        }

        if (Boolean.getBoolean("jdk.attach.core.skipVersionCheck")) {
            newEnv.put("REVIVAL_SKIPVERSIONCHECK", "1");
        }
        // Linux-specific:
        if (System.getProperty("os.name").startsWith("Linux")) {
            newEnv.put("LD_USE_LOAD_BIAS", "1"); // Required by OS to respect shared object load addresses
            newEnv.put("LD_PRELOAD", jdkLibDir + File.separator + "librevival_support.so");
        }
        // Windows-specific:
        String editbin = System.getProperty("jdk.attach.core.editbin");
        if (editbin != null) {
            newEnv.put("EDITBIN", editbin);
        }
        // Run the helper.
        // Be prepared for it to fail, e.g. if Address Space Layout Randomization is unkind and causes
        // a clash, recognise from the process return value and retry.
        //
        // This method returns an InputStream, although until recently there was no streaming from jcmd,
        // the full output was written to a buffer and then printed.
        // For core files, for now, we read the whole output, and only return it if it is a successful run.
        int tries = Integer.getInteger("jdk.attach.core.tries", HELPER_TRIES);
        String out = null;

        for (int i = 0; i < tries; i++) {
            if (verbose) System.err.println("revivalhelper: (run " + i + ")");
            Process p = pb.start();
            long pid = p.pid();
            if (verbose) System.err.println("revivalhelper: pid = " + pid);

            try {
                ExecutorService executor = Executors.newFixedThreadPool(2);
                Future<String> stdout = executor.submit(() -> drain(p.getInputStream()));
                // Future<String> stderr = executor.submit(() -> drain(p.getErrorStream()));
                int e = p.waitFor();
                out = stdout.get(5, TimeUnit.SECONDS);
                if (e == 1) {
                    // Actual error from JCmd, e.g. Exception thrown by command implementation.
                    System.out.print(out);
                    throw new IOException("jcmd returned an error");  // JCmd caller will call System.exit(1);
                } else if (e == HELPER_RETRY) {
                    if (verbose) {
                        System.err.print(out);
                        System.out.println("(Retrying process revival)");
                    }
                    continue; // ...and retry.
                } else if (e != 0) {
                    // Other errors
                    System.out.print(out);
                    System.out.println("ERROR (" + e + ")");
                }
                // Success.

            } catch (InterruptedException | ExecutionException | TimeoutException ex) {
                System.err.println("VirtualMachineCoreDumpImpl.execute: " + ex);
                if (verbose) {
                    ex.printStackTrace();
                }
            }
            break; // No retry except for explicit continue above.
        }
        return new StringBufferInputStream(out);
    }

    private static String drain(InputStream is) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line).append(System.lineSeparator());
            }
        }
        return sb.toString().trim();
    }
}
