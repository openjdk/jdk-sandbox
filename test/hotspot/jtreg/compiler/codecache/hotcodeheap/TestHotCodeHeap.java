/*
 * Copyright Amazon.com Inc. or its affiliates. All rights reserved.
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

/**
 * @test TestHotCodeHeap
 * @summary Check placement of compiled Java methods into the HotCodeHeap segment.
 * @library /test/lib
 *
 * @requires vm.flagless
 *
 * @run driver compiler.codecache.hotcodeheap.TestHotCodeHeap CompileCommandFile
 * @run driver compiler.codecache.hotcodeheap.TestHotCodeHeap CompileCommand
 */

package compiler.codecache.hotcodeheap;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHotCodeHeap {
    public static void main(String[] args) throws Exception {
        ArrayList<String> command = new ArrayList<String>();
        command.add("-Xbatch");
        command.add("-Xcomp");
        command.add("-XX:-TieredCompilation");
        command.add("-XX:-Inline");
        if (args[0].equals("CompileCommandFile")) {
          command.add("-XX:CompileCommandFile=" + new File(System.getProperty("test.src", "."), "command.txt"));
        } else if (args[0].equals("CompileCommand")) {
          command.add("-XX:CompileCommand=HotCodeHeap,compiler/codecache/hotcodeheap/TestHotCodeHeap$Launcher.test(I)I");
        } else {
          throw new RuntimeException("Unknown option: " + args[0]);
        }
        command.add("-XX:+PrintCompilation");
        command.add(Launcher.class.getName());

        ProcessBuilder pb = ProcessTools.createJavaProcessBuilder(command);
        OutputAnalyzer analyzer = new OutputAnalyzer(pb.start());
        analyzer.shouldHaveExitValue(0);
        analyzer.shouldContain("### HotCodeHeap  static  compiler.codecache.hotcodeheap.TestHotCodeHeap$Launcher::test");
    }

    static class Launcher {
        static int sink;

        public static void main(final String[] args) throws Exception {
            int end = 20_000;

            int v = 0;
            for (int i = 0; i < end; i++) {
                v += test(i);
            }
            sink = v;
        }

        // This method is tagged for HotCodeHeap by a compile command.
        // We should see this in the PrintCompilation output.
        static int test(int i) {
            return i;
        }
    }
}
