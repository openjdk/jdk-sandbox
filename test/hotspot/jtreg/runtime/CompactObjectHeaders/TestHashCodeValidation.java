/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
 * @test
 * @summary -XX:+UseCompactObjectHeaders must reject a non-idempotent explicit
 *          -XX:hashCode generator. UCOH recomputes the identity hash for objects
 *          in the "hashed but not expanded" state via get_next_hash(nullptr, obj),
 *          which only works for the idempotent address-based generator hashCode=6.
 *          An explicit hashCode in {0,1,3,5} either crashes (5: dereferences the
 *          null Thread) or silently returns a different hash on each recompute
 *          (0/3/1), violating the Object.hashCode() lifetime contract. The VM must
 *          fail fast at startup instead of running with the broken generator.
 * @requires vm.bits == "64"
 * @library /test/lib
 * @run driver TestHashCodeValidation
 */

import jdk.test.lib.process.OutputAnalyzer;
import jdk.test.lib.process.ProcessTools;

public class TestHashCodeValidation {

    static final String EXPECTED_MSG =
        "-XX:hashCode must be 6 when UseCompactObjectHeaders is enabled";

    // Non-idempotent generators that must be rejected under UCOH.
    static final int[] REJECTED = { 0, 1, 3, 5 };

    public static void main(String[] args) throws Exception {
        // 1. Every non-idempotent explicit hashCode must be rejected at startup
        //    with a clear message and a non-zero exit code. On an unfixed VM this
        //    fails: hashCode=5 SIGSEGVs (or starts) and 0/1/3 start cleanly (exit 0)
        //    with no diagnostic, silently running the broken generator.
        for (int mode : REJECTED) {
            OutputAnalyzer out = run("-XX:+UnlockExperimentalVMOptions",
                                     "-XX:+UseCompactObjectHeaders",
                                     "-XX:hashCode=" + mode,
                                     "-version");
            out.shouldContain(EXPECTED_MSG);
            out.shouldHaveExitValue(1);
        }

        // 2. An explicit -XX:hashCode=6 is the idempotent generator and must be
        //    accepted (the fix must not over-reject the correct value).
        run("-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseCompactObjectHeaders",
            "-XX:hashCode=6",
            "-version")
            .shouldHaveExitValue(0);

        // 3. With no explicit hashCode, UCOH must ergonomically force hashCode=6
        //    and start normally.
        run("-XX:+UnlockExperimentalVMOptions",
            "-XX:+UseCompactObjectHeaders",
            "-XX:+PrintFlagsFinal",
            "-version")
            .shouldHaveExitValue(0)
            .shouldMatch("intx\\s+hashCode\\s+=\\s+6\\b");

        // 4. Sanity: without UCOH, a non-idempotent hashCode is still allowed
        //    (the restriction is specific to compact headers).
        run("-XX:+UnlockExperimentalVMOptions",
            "-XX:-UseCompactObjectHeaders",
            "-XX:hashCode=5",
            "-version")
            .shouldHaveExitValue(0);

        System.out.println("TestHashCodeValidation passed.");
    }

    private static OutputAnalyzer run(String... vmArgs) throws Exception {
        ProcessBuilder pb = ProcessTools.createLimitedTestJavaProcessBuilder(vmArgs);
        return new OutputAnalyzer(pb.start());
    }
}
