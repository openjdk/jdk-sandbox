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
    protected String revivalDataPath;

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
            revivalDataPath = (String) env.get("revivalDataPath");
        } else {
            libDirs = null;
            revivalDataPath = null;
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

    /**
     * Execute the given command in the target VM.
     */
    @SuppressWarnings("deprecation")
    InputStream execute(String cmd, Object ... args) throws IOException {
        checkNulls(args);

        // Only 'jcmd' command is implemented.
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
            throw new IOException("Revival helper '" + helper + "' not found");
        }
        List<String> pargs = new ArrayList<String>();
        pargs.add(helper);

        // Pass library directory as -L/path
        if (libDirs != null) {
            for (String s : libDirs) {
                pargs.add("-L" + s);
            }
        }

        // Revival data location
        if (revivalDataPath != null) {
            pargs.add("-R" + revivalDataPath);
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

        boolean verbose = Boolean.getBoolean("jdk.attach.core.verbose");
        if (verbose) {
            newEnv.put("REVIVAL_VERBOSE", "1"); // any non-null value will be recognised
        }
        if (Boolean.getBoolean("jdk.attach.core.skipversioncheck")) {
            newEnv.put("REVIVAL_SKIPVERSIONCHECK", "1");
        }
        // Linux-specific:
        if (System.getProperty("os.name").startsWith("Linux")) {
            newEnv.put("LD_USE_LOAD_BIAS", "1");
            newEnv.put("LD_PRELOAD", jdkLibDir + File.separator + "librevival_support.so");
        }
        // Windows-specific:
        String editbin = System.getProperty("jdk.attach.core.editbin");
        if (editbin != null) {
            newEnv.put("EDITBIN", editbin);
        }
        // Run the helper.
        // Be prepared for it to fail, e.g. if Address Space Layout Randomization is unkind and causes a clash.
        // Recognise the process return value and retry with some limit.
        //
        // This method returns an InputStream, although until recently there was no streaming from jcmd,
        // the full output was written to a buffer and then printed.
        // For core files, for now, we can read the whole output, and only return it if it is a successful run.
        int TRIES = 10;
        int tries = Integer.getInteger("jdk.attach.core.tries", TRIES);
        String out = null;

        for (int i = 0; i < tries; i++) {
            Process p = pb.start();
            BufferedReader outReader = p.inputReader();
            BufferedReader errReader = p.errorReader();
            out = drain(p, outReader);
            try {
                int e = p.waitFor();
                if (e == 1) {
                    // Actual error from JCmd, e.g. Exception thrown by command implementation.
                    System.out.println(out);
                    String err = drain(p, errReader);
                    System.err.println(err);
                    throw new IOException("jcmd returned an error");  // JCmd caller will call System.exit(1);
                } else if (e == 7) {
                    // Hint to retry due to address space clash.
                    if (verbose) {
                        System.err.println(out);
                        String err = drain(p, errReader);
                        System.err.println(err);
                        System.out.println("(Retrying process revival)");
                    }
                    continue; // ...and retry.
                } else if (e != 0) {
                    // Other errors
                    System.out.println(out);
                    System.out.println("ERROR (" + e + ")");
                    String err = drain(p, errReader);
                    System.err.println(err);
                } else {
                    // Success
                }
            } catch (InterruptedException ie) {
                System.err.println("VirtualMachineCoreDumpImpl.execute: " + ie);
            }
            break; // No retry except for explicit continue above.
        }
        return new StringBufferInputStream(out);
    }

    public static String drain(Process p, BufferedReader r) throws IOException {
        StringBuilder s = new StringBuilder();
        do {
            while (r.ready()) {
                String line = r.readLine();
                if (line == null) {
                    break;
                } else {
                    s.append(line).append("\n");
                }
            }
        } while (p.isAlive());

        return s.toString();
    }

    public static long drainUTF8(InputStream is, PrintStream ps) throws IOException {
        long result = 0;

        try (BufferedInputStream bis = new BufferedInputStream(is);
             InputStreamReader isr = new InputStreamReader(bis, UTF_8)) {
            char c[] = new char[256];
            int n;

            do {
                n = isr.read(c);

                if (n > 0) {
                    result += n;
                    ps.print(n == c.length ? c : Arrays.copyOf(c, n));
                }
            } while (n > 0);
        }

        return result;
    }
}
