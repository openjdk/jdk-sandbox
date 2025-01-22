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
 */

package gc.parallel;

import java.util.Random;

/*
 * @test TestHashedObjectCopy
 * @bug 8379910
 * @summary Parallel GC scavenge could read past the end of a hashed object
 *          when copying it to survivor/old space with compact object headers.
 *          If a hashed-but-not-expanded object sits at the very end of eden,
 *          the extra word read by copy_unmarked_to_survivor_space crosses into
 *          unmapped memory, causing a crash.
 * @requires vm.gc.Parallel
 * @run main/othervm -XX:+UnlockExperimentalVMOptions -XX:+UseCompactObjectHeaders
 *      -XX:+UseParallelGC -Xmx128m -Xms128m -Xmn1m
 *      gc.parallel.TestHashedObjectCopy
 */
public class TestHashedObjectCopy {

    // Stress a variety of layouts.
    static class S1 { byte a; }
    static class S2 { byte a, b; }
    static class S3 { byte a, b, c; }
    static class S4 { byte a, b, c, d; }
    static class S5 { byte a, b, c, d, e; }
    static class S6 { byte a, b, c, d, e, f; }
    static class S7 { byte a, b, c, d, e, f, g; }
    static class S8 { byte a, b, c, d, e, f, g, h; }

    static volatile int sink;

    public static void main(String[] args) {
        // Churn through different-sized objects. Keep some alive,
        // because we're testing copy-to-survivor behaviour.
        Random rng = new Random(42);
        Object[] retained = new Object[10_000];
        for (int cycle = 0; cycle < 1_000; cycle++) {
            for (int i = 0; i < retained.length; i++) {
                Object obj = switch (rng.nextInt(8)) {
                    case 0 -> new S1();
                    case 1 -> new S2();
                    case 2 -> new S3();
                    case 3 -> new S4();
                    case 4 -> new S5();
                    case 5 -> new S6();
                    case 6 -> new S7();
                    default -> new S8();
                };
                // Trigger expansion on copy.
                sink = System.identityHashCode(obj);
                retained[i] = obj;
            }
        }
    }
}
