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

import com.sun.tools.attach.AgentLoadException;
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
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;

/*
 * Core dump implementation of HotSpotVirtualMachine
 */
@SuppressWarnings("restricted")
public class VirtualMachineCoreDumpImpl extends HotSpotVirtualMachine {

    protected String filename;
    protected List<String> libDirs;

    /**
     * Attaches to a core file or minidump.
     */
    VirtualMachineCoreDumpImpl(AttachProvider provider, String vmid, List<String> libDirs) throws AttachNotSupportedException, IOException {
        // super HotSpotVirtualMachine modified to accept String that is not a PID.
        super(provider, vmid);
        filename = vmid;
        this.libDirs = libDirs;
        attach();
    }

    protected void attach() throws IOException {
        // Process revival from a core or minidump to enable executing diagnostic commands.
        // This happens in a new process.
    }

    /**
     * Detach from the target VM
     */
    public void detach() throws IOException {
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
        // Invoke "JDK/lib/revivalhelper corefilename jcmd command..."
        // (Putting revivalhelper in JDK/lib works if jlink is updated also,
        // see DefaultImageBuilder.java and its jspawnhelper/jexec special case).
        String helper = System.getProperty("java.home") + File.separator + "lib" + File.separator + "revivalhelper"
                        + (System.getProperty("os.name").startsWith("Windows") ? ".exe" : "");
        if (!(new File(helper).exists())) {
            throw new IOException("Revival helper '" + helper + "' not found");
        }
        List<String> pargs = new ArrayList<String>();
        pargs.add(helper);
        // Pass library directories as -L/path
        // Do we need > 1 ?
        if (libDirs != null) {
            for (String s : libDirs) {
                pargs.add("-L" + s);
            }
        }
        pargs.add(filename);
        pargs.add(cmd);
        for (Object o : args) {
            pargs.add((String) o);
        }

        ProcessBuilder pb = new ProcessBuilder(pargs);
        pb.redirectErrorStream(true);

        Map<String, String> newEnv = pb.environment();
        if (Boolean.getBoolean("jdk.attach.core.verbose")) {
            newEnv.put("REVIVAL_VERBOSE", "1");
        }
        if (System.getProperty("os.name").startsWith("Linux")) {
            newEnv.put("LD_USE_LOAD_BIAS", "1");
        }

        String path = System.getenv("PATH");
        newEnv.put("PATH", System.getProperty("java.home") + File.separator + "bin" + File.pathSeparator + path);

        // Run the helper.
        // Be prepared for it to fail, if Address Space Layout Randomization is unkind, and causes a clash.
        // Recognise the process return value and retry with some limit.
        //
        // This method returns an InputStream, although until recently there was no streaming from jcmd implementations,
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
                if (e == 7) {
                    // Possibly show errors if verbose:
                    if (Boolean.getBoolean("jdk.attach.core.verbose")) {
                        String err = drain(p, errReader);
                        System.err.println(err);
                    }
                    continue; // ...and retry.
                } else {
                    if (e != 0) {
                        System.out.println("ERROR (" + e + ")");
                        String err = drain(p, errReader);
                        System.err.println(err);
                    }
                    // System.out.println("DONE (" + e + ") " + out);
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
                s.append(r.readLine()).append("\n");
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

//    static native void write(int fd, byte buf[], int off, int bufLen) throws IOException;
/*    static {
        System.loadLibrary("attach");
    } */
}
