/*
 * Copyright (c) 2026, Datadog, Inc. All rights reserved.
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
 * @test id=default
 * @summary Test that identity hash codes are stable across Shenandoah evacuation
 * @bug 8379910
 * @requires vm.gc.Shenandoah
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @library /test/lib
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseShenandoahGC
 *      -Xms64m -Xmx64m
 *      TestHashCodeEvacRace
 */

/*
 * @test id=generational
 * @summary Test that identity hash codes are stable across Shenandoah evacuation
 * @bug 8379910
 * @requires vm.gc.Shenandoah
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @library /test/lib
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseShenandoahGC
 *      -XX:ShenandoahGCMode=generational -Xms64m -Xmx64m
 *      TestHashCodeEvacRace
 */

/*
 * @test id=serial
 * @summary Test that identity hash codes are stable across Serial GC
 * @bug 8379910
 * @requires vm.gc.Serial
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @library /test/lib
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseSerialGC
 *      -Xms64m -Xmx64m
 *      TestHashCodeEvacRace
 */

/*
 * @test id=parallel
 * @summary Test that identity hash codes are stable across Parallel GC
 * @bug 8379910
 * @requires vm.gc.Parallel
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @library /test/lib
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseParallelGC
 *      -Xms64m -Xmx64m
 *      TestHashCodeEvacRace
 */

/*
 * @test id=g1
 * @summary Test that identity hash codes are stable across G1 GC
 * @bug 8379910
 * @requires vm.gc.G1
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @library /test/lib
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseG1GC
 *      -Xms64m -Xmx64m
 *      TestHashCodeEvacRace
 */

/*
 * @test id=zgc
 * @summary Test that identity hash codes are stable across ZGC
 * @bug 8379910
 * @requires vm.gc.Z
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @library /test/lib
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseZGC
 *      -Xms64m -Xmx64m
 *      TestHashCodeEvacRace
 */

/**
 * Regression test for a race between reading the identity hash code and
 * Shenandoah concurrent evacuation with compact object headers.
 *
 * With compact headers, objects whose layout has no internal gap for the
 * identity hash (e.g. a class with a single int field: 4-byte header +
 * 4-byte int = 8 bytes) require an extra word ("hash expansion") when
 * evacuated. The bug was that initialize_hash_if_necessary() was called
 * AFTER the forwarding CAS, leaving a window where the copy is already
 * visible to other threads (via the forwarding pointer) but the hash
 * value in the expansion word has not been written yet. A thread reading
 * the hash during that window would see an uninitialized value.
 *
 * The fix moves initialize_hash_if_necessary() before the forwarding CAS
 * so the copy is fully initialized when it becomes visible.
 *
 * This test hashes objects, records the expected values, then continuously
 * verifies them while GC evacuates the objects. Without the fix, a reader
 * thread can observe a wrong (uninitialized) hash during evacuation.
 */
public class TestHashCodeEvacRace {

    // With compact headers: 4-byte header + 4-byte int = 8 bytes.
    // No room for the 4-byte identity hash — requires expansion on evacuation.
    static class IntHolder {
        int value;
        IntHolder(int v) { value = v; }
    }

    static final int NUM_OBJECTS = 50_000;
    static final int NUM_READERS = 4;
    static final int DURATION_MS = 10_000;

    static final IntHolder[] objects = new IntHolder[NUM_OBJECTS];
    static final int[] expectedHash = new int[NUM_OBJECTS];

    static volatile boolean running = true;
    static volatile String failure = null;

    public static void main(String[] args) throws Exception {
        // Create objects and record their identity hash codes.
        // After this, each object has is_hashed_not_expanded state.
        for (int i = 0; i < NUM_OBJECTS; i++) {
            objects[i] = new IntHolder(i);
            expectedHash[i] = System.identityHashCode(objects[i]);
        }

        // Reader threads: continuously read identity hash codes and verify
        // they match the recorded values. During concurrent evacuation,
        // readers may follow forwarding pointers to to-space copies.
        // Without the fix, the copy may be visible before its hash is
        // initialized, causing a mismatch.
        Thread[] readers = new Thread[NUM_READERS];
        for (int t = 0; t < NUM_READERS; t++) {
            readers[t] = new Thread(() -> {
                while (running && failure == null) {
                    for (int i = 0; i < NUM_OBJECTS; i++) {
                        IntHolder obj = objects[i];
                        if (obj == null) continue;
                        int actual = System.identityHashCode(obj);
                        int expected = expectedHash[i];
                        if (actual != expected) {
                            failure = "Hash mismatch at index " + i
                                    + ": expected=" + expected
                                    + " actual=" + actual;
                            return;
                        }
                    }
                }
            });
            readers[t].setDaemon(true);
            readers[t].start();
        }

        // Main thread: allocate garbage to trigger GC and evacuation.
        // The objects[] array keeps the hashed objects alive so they get
        // evacuated rather than collected.
        long deadline = System.currentTimeMillis() + DURATION_MS;
        while (System.currentTimeMillis() < deadline && failure == null) {
            for (int i = 0; i < 100; i++) {
                byte[] garbage = new byte[4096];
            }
            Thread.yield();
        }

        running = false;
        for (Thread t : readers) t.join(5000);

        if (failure != null) {
            throw new RuntimeException(failure);
        }
        System.out.println("PASSED");
    }
}
