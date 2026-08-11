/*
 * Copyright Amazon.com Inc. or its affiliates. All Rights Reserved.
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
 *
 */

/*
 * @test
 * @summary Test that Shenandoah works reliably with different save stack slot configs
 * @requires vm.gc.Shenandoah
 * @library /test/lib
 * @modules java.base/jdk.internal.misc
 *          java.management
 * @run driver TestFastSaveSlots
 */

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;

public class TestFastSaveSlots {
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            payload();
            return;
        }

        for (int slots = 0; slots < 32; slots++) {
            testWith(slots);
        }
    }
   
    private static void testWith(int slots) throws Exception {
        OutputAnalyzer output = ProcessTools.executeLimitedTestJava(
            "-Xmx128m",
            "-XX:+UnlockDiagnosticVMOptions",
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseShenandoahGC",
            "-XX:ShenandoahGCHeuristics=aggressive",
            "-XX:ShenandoahFastSaveSlots=" + slots,
            TestFastSaveSlots.class.getName(),
            "test"
        );
        output.shouldHaveExitValue(0);
    }

    static final int COUNT = 10_000_000;
    static final int WINDOW = 10_000;

    static final String[] reachable = new String[WINDOW];

    public static void payload() {
        int rIdx = 0;
        for (int c = 0; c < COUNT; c++) {
            reachable[rIdx] = ("LargeString" + c);
            rIdx++;
            if (rIdx >= WINDOW) {
                rIdx = 0;
            }
        }
    }

}
