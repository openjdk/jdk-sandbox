/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test id=Simple
 * @summary Simplified variant with just VM.version
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 * @requires vm.debug == true
 *
 * @run main/othervm JCmdRevival cicrash VM.version
 */

/**
 * @test id=JCmdRevivalOOM
 * @summary Test process revival for serviceability: jcmd on a core file (OOM crash).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm -Djdk.attach.core.verbose=true JCmdRevival oom VM.version help Thread.print GC.class_histogram GC.heap_dump Compiler.CodeHeap_Analytics Compiler.codecache Compiler.codelist Compiler.memory GC.heap_info VM.class_hierarchy VM.classes VM.classloader_stats VM.classloaders VM.command_line VM.dynlibs VM.events VM.flags VM.metaspace VM.stringtable VM.symboltable VM.systemdictionary VM.version_UNKNOWN VM.unknowncommand VM.flags_UNKNOWNARG
 */

/*
 * @test id=JCmdRevivalCICrash
 * @summary Test process revival for serviceability: jcmd on a core file (CI crash, debug VM).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 * @requires vm.debug == true
 *
 * @run main/othervm JCmdRevival cicrash VM.version help Thread.print GC.class_histogram GC.heap_dump Compiler.CodeHeap_Analytics Compiler.codecache Compiler.codelist Compiler.memory GC.heap_info VM.class_hierarchy VM.classes VM.classloader_stats VM.classloaders VM.command_line VM.dynlibs VM.events VM.flags VM.metaspace VM.stringtable VM.symboltable VM.systemdictionary VM.version_UNKNOWN VM.unknowncommand VM.flags_UNKNOWNARG
 *
 */

/*
 * @test id=JCmdRevivalAbortOnException
 * @summary Test process revival for serviceability: jcmd on a core file (AbortVMOnException).
 * @requires os.family == "linux" | os.family == "windows"
 * @library /test/lib
 *
 * @run main/othervm JCmdRevival abortvmonexception VM.version help Thread.print GC.class_histogram GC.heap_dump Compiler.CodeHeap_Analytics Compiler.codecache Compiler.codelist Compiler.memory GC.heap_info VM.class_hierarchy VM.classes VM.classloader_stats VM.classloaders VM.command_line VM.dynlibs VM.events VM.flags VM.metaspace VM.stringtable VM.symboltable VM.systemdictionary VM.version_UNKNOWN VM.unknowncommand VM.flags_UNKNOWNARG
 *
 */

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import jdk.test.lib.Asserts;
import jdk.test.lib.JDKToolFinder;
import jdk.test.lib.JDKToolLauncher;
import jdk.test.lib.Platform;
import jdk.test.lib.Utils;
import jdk.test.lib.classloader.GeneratingClassLoader;
import jdk.test.lib.hprof.HprofParser;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.util.CoreUtils;
import jtreg.SkippedException;

public class JCmdRevival {

    private static String MAJOR;

    public static void main(String[] args) throws Throwable {
        //  '27-...'
        MAJOR = System.getProperty("java.vm.version").substring(0, 2);

        if (args.length > 1) { 
            // We are the initial test invocation.
            // Will re-invoke ourself to cause crash and core, wait for that process,
            // then run "jcmd command" on the core.
            test(args);
        } else if (args.length == 1) {
            // One argument is a re-invocation to run, with a crash and core.
            testAndCrash(args);
        } else {
            throw new RuntimeException("Missing test arguments");
        }
    }

    public static void testAndCrash(String[] args) {
        // Type of run (cicrash, oom) is passed as arg.
        // Do the thing that will cause a crash, or if cicrash, then any activity will trigger crash.
        System.out.println("JCmdRevival in main for test type '" + args[0] + "'");
        if (args[0].equals("abortvmonexception")) {
            throw new NullPointerException("testing NPE");
        }
        Object[] oa = new Object[Integer.MAX_VALUE / 2];
        for(int i = 0; i < oa.length; i++) {
            oa[i] = new Object[Integer.MAX_VALUE / 2];
        }
        System.out.println("JCmdRevival: finishing, should not reach here.");
        System.exit(1); // Should not reach here.
    }

    public static ProcessBuilder getProcessBuilder(String type) {

        switch (type) {
            case ("cicrash"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-Xmx128m", "-XX:CICrashAt=1", "-XX:CompileThreshold=1",
                       JCmdRevival.class.getName(), type);
            }
            case ("oom"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-Xmx128m", "-XX:MaxMetaspaceSize=64m", "-XX:+CrashOnOutOfMemoryError",
                       JCmdRevival.class.getName(), type);
            }
            case ("abortvmonexception"): {
                return ProcessTools.createTestJavaProcessBuilder("-XX:+CreateCoredumpOnCrash",
                       "-XX:+UnlockDiagnosticVMOptions", "-XX:AbortVMOnException=java.lang.NullPointerException",
                       "-Xmx128m", "-XX:MaxMetaspaceSize=64m",
                       JCmdRevival.class.getName(), type);
            }
            default: {
                throw new RuntimeException("unknown test type");
            }
        }
    }

    /**
      * Test a type of crashing test process, and a list of jcmd commands.
      * e.g. oom VM.version Thread.print
      */
    static void test(String [] args) throws Throwable {
        String type = args[0];
        ProcessBuilder pb = getProcessBuilder(type);

        // For a core dump, apply "ulimit -c unlimited" if we can.
        pb = CoreUtils.addCoreUlimitCommand(pb);
        OutputAnalyzer output = ProcessTools.executeProcess(pb);
        System.out.println(output);
        // Find core, filename found is reported:
        String coreFileName = CoreUtils.getCoreFileLocation(output.getStdout(), output.pid());

        // Run jcmd(s) on core: collect failures.
        List<Object[]> failures = new ArrayList<>();
        int tested = 0;
        for (int i = 1; i < args.length; i++) {
            try {
                tested++;
                testJCmd(coreFileName, type, args[i]);
            } catch (Throwable thr) {
                failures.add(new Object[] { args[i], thr});
            }
        }
        if (!failures.isEmpty()) {
            System.err.println("FAILURE(s): ");
            for (Object [] f: failures) {
                System.err.println(f[0]);
                ((Throwable) f[1]).printStackTrace(System.err);
            }
            throw new RuntimeException("FAILED tests: " + failures.size() + " out of " + tested);
        }
        System.out.println("PASSED");
    }
        
    static void testJCmd(String coreFileName, String type, String command) throws Throwable {
		System.out.println("TEST: core: " + coreFileName + " Test type: " + type + " Command: " + command);

        String heapDumpName = null;
        JDKToolLauncher launcher = JDKToolLauncher.createUsingTestJDK("jcmd");
        // Run jcmd with -J-Dprop=value to pass any Sytem property we care about: the verbose setting.
        String verbose = System.getProperty("jdk.attach.core.verbose");
        if (verbose != null) {
            launcher.addVMArg("-Djdk.attach.core.verbose=" + verbose);
        }
        launcher.addVMArgs(Utils.getTestJavaOpts()); // People do not generally run jcmd itself with other options.
        launcher.addToolArg(coreFileName);

        // This method just takes a command name to test.  For some commands we will
        // add a further argument:
        if (command.equals("GC.heap_dump")) {
            launcher.addToolArg(command);
            heapDumpName = "heapdump.hprof";
            launcher.addToolArg(heapDumpName);
        } else if (command.equals("VM.version_UNKNOWN")) {
            // Test additional args when none are expected:
            launcher.addToolArg("VM.version");
            launcher.addToolArg("badarg1");
        } else if (command.equals("VM.flags_UNKNOWNARG")) {
            // Test additional unknown args:
            launcher.addToolArg("VM.flags");
            launcher.addToolArg("badarg1");
            launcher.addToolArg("badarg2");
        } else {
            // All other jcmds just need the command name:
            launcher.addToolArg(command);
        }

        ProcessBuilder jcmdpb = new ProcessBuilder();
        System.out.println("Will run command:" + Arrays.toString(launcher.getCommand()));
        jcmdpb.command(launcher.getCommand());
        // Process revival is currently assisted by some pre-work in a separate Java tool:
        Map<String, String> env = jcmdpb.environment();

        Process jcmd = jcmdpb.start();
        OutputAnalyzer out = new OutputAnalyzer(jcmd);
        int e = jcmd.waitFor();
        System.out.println("jcmd completed, return code " + e);
        System.out.println(out.getStdout());
        System.err.println(out.getStderr());

        // Generic checks:
        // out.shouldContain("Opening dump file "); // changed to "FILENAME:" to be same format as PIDs.

        int expectedExit = 0;

        // Verify specific jcmd output:
        try {
        switch (command) {
            case "Compiler.CodeHeap_Analytics": {
                // out.shouldContain("buffer blob         flush_icache_stub");
                out.shouldContain("__ CodeHeapStateAnalytics total duration ");
                break;
            }
            case "Compiler.codecache": {
                out.shouldContain("CodeHeap 'non-profiled nmethods': size=");
                break;
            }
            case "Compiler.codelist": {
                out.shouldContain("jdk.internal.misc.Unsafe.getReferenceVolatile(Ljava/lang/Object;J)Ljava/lang/Object; [0x");
                break;
            }
            case "Compiler.memory": {
                // Compiler memory stats not usually enabled.
                if (Platform.isDebugBuild()) {
                    out.shouldContain("ctyp  total     ra        node      comp      type      states    reglive   regsplit  regmask   superword cienv     ha        other     #nodes  result  limit     time    id    thread             method");
                }
                break;
            }
            case "GC.class_histogram": {
                out.shouldContain(" num     #instances         #bytes  class name (module)");
                out.shouldContain("java.lang.String (java.base@" + MAJOR);
                break;
            }
            case "GC.heap_dump": {
                File dumpFile = new File(heapDumpName);
                if (!dumpFile.exists() && dumpFile.isFile()) {
                    throw new RuntimeException("Could not find dump file '" + heapDumpName + "'");
                } else {
                    System.out.println("Reading dump file '" + heapDumpName + "'");
                    HprofParser.parse(dumpFile);
                }
                break;
            }
            case "GC.heap_info": {
                out.shouldMatch("total reserved ");
                // out.shouldMatch("Metaspace\\s+used "); // not any more...
                break;
            }
            case "Thread.print": {
                out.shouldContain("Full thread dump");
                out.shouldContain("_java_thread_list=0x");
                // OOM crash will contain the stack of the test app, but not likely for cicrash.
                if (!(type.equals("cicrash"))) {
                    out.shouldContain("at JCmdRevival.main(JCmdRevival.java:");
                }
                out.shouldContain("VM Thread");
                out.shouldContain("JNI global refs:");
                break;
            }
            case "VM.class_hierarchy": {
                out.shouldContain("java.lang.Object/null");
                out.shouldContain("|--java.lang.String/null");
                break;
            }
            case "VM.classes": {
                out.shouldContain("fully_initialized     WS       java.lang.String");
                break;
            }
            case "VM.classloader_stats": {
                out.shouldContain("<boot class loader>");
                break;
            }
            case "VM.classloaders": {
                out.shouldContain("jdk.internal.loader.ClassLoaders$AppClassLoader");
                break;
            }
            case "VM.command_line": {
                out.shouldContain("Launcher Type: SUN_STANDARD");
                break;
            }
            case "VM.dynlibs": {
                out.shouldContain("");
                break;
            }
            case "VM.events": {
                out.shouldContain("Events");
                break;
            }
            case "VM.flags": {
                out.shouldContain("-XX:+CreateCoredumpOnCrash");
                out.shouldContain("-XX:ReservedCodeCacheSize=");
                // Should find a flag with a value we can verify.
                // ConcGCThreads can be set, but is changed on some test machines, e.g. to 2.
                // out.shouldContain("-XX:ConcGCThreads=3"); // Recognise value set in test header
                break;
            }
            case "VM.log": {
                break;
            }
            case "VM.metaspace": {
                out.shouldContain("Narrow klass pointer bits ");
                break;
            }
            case "VM.stringtable": {
                out.shouldContain("Maximum bucket size     : ");
                break;
            }
            case "VM.symboltable": {
                out.shouldContain("Maximum bucket size     : ");
                break;
            }
            case "VM.systemdictionary": {
                // out.shouldContain("System Dictionary for 'bootstrap' class loader statistics:");
                out.shouldContain("Number of buckets       :");
                break;
            }
            case "VM.unknowncommand": {
                out.stdoutShouldContain("Unknown diagnostic command");
                expectedExit = 1;
                break;
            }
            case "VM.version": {
                // e.g.
                // Java HotSpot(TM) 64-Bit Server VM version 27-...
                // JDK 27.0.0
                out.shouldContain("VM version " + MAJOR + "-");
                out.shouldContain("JDK " + MAJOR + ".");
                break;
            }
            case "VM.version_UNKNOWN": {
                out.stdoutShouldContain("The argument list of this diagnostic command should be empty.");
                expectedExit = 1;
                break;
            }
            case "VM.flags_UNKNOWNARG": {
                out.stdoutShouldContain("Unknown argument 'badarg1' in diagnostic command.");
                expectedExit = 1;
                break;
            }
            case "help": {
                out.shouldContain("The following commands are available:");
                out.shouldContain("VM.version");
                out.shouldContain("help");
                out.shouldNotContain("GC.run");
                out.shouldNotContain("VM.set_flag");
                break;
            }
            default: {
                throw new RuntimeException("Unknown command being tested: '" + command + "'");
            }
            }
        } finally {
            // Show an unusual jcmd exit code in finally, in case a check above throws (and skips exit value assert below).
            if (e != expectedExit) {
                System.err.println("Test '" + command + "' exits with: " + e);
            }
        }
        Asserts.assertEquals(expectedExit, e, "Unexpected jcmd return code");
    }
}
