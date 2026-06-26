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

package gc.stress.ihash;

/*
 * @test id=G1
 * @bug 8387335
 * @summary Stress test: does humongous object compaction corrupt hash-code or other objects?
 * @requires vm.gc.G1
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseG1GC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+VerifyDuringGC
 *      -XX:-ExplicitGCInvokesConcurrent
 *      -Xmx512m -XX:G1HeapRegionSize=1M
 *      gc.stress.ihash.TestHumongousHash
 */

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TestHumongousHash {
    public static void main(String[] args) {
        // For G1, 50% of region size. For Shenandoah, 100%. We want to stress objects over the
        // threshold, but also particularly objects near a region boundary (hash-code expansion)
        // could see an object expand from below threshold to above threshold, or from fitting in
        // N regions to requiring N+1 regions.
        int humongousThreshold = 512 * 1024;

        for (int i = 1; i < 10; i++) {
            test(humongousThreshold * i);
        }
    }

    static void test(int objectSize) {
        List<byte[]> largeObjects = new ArrayList<>();
        List<Integer> hashes = new ArrayList<>();

        int numObjects = 0;
        try {
            while (true) {
                // Stress various sizes near the threshold, below and above.
                byte[] largeObject = new byte[objectSize + numObjects - 32];
                Arrays.fill(largeObject, (byte) numObjects);
                largeObjects.add(largeObject);
                hashes.add(System.identityHashCode(largeObject));
                numObjects++;
            }
        } catch (OutOfMemoryError e) {
            // That's enough.
        }

        // Fragment so that GC can compact.
        for (int i = 0; i < numObjects; i++) {
            if (i % 2 == 0) {
                largeObjects.set(i, null);
            }
        }

        // Trigger compaction. System.gc() doesn't trigger it in all cases (e.g. G1), so we fill up
        // the heap to trigger OOM, too.
        System.gc();
        ArrayList<byte[]> boom = new ArrayList<>();
        try {
            while (true) {
                boom.add(new byte[100_000_000]);
            }
        } catch (OutOfMemoryError e) {
            boom = null;
        }

        // Check for corruption in hash-code, length or data.
        boolean fail = false;
        for (int i = 0; i < numObjects; i++) {
            if (i % 2 == 0) continue;
            byte[] largeObject = largeObjects.get(i);
            if (System.identityHashCode(largeObject) != hashes.get(i)) {
                System.out.println("hash mismatch at " + i);
                fail = true;
            }
            if (largeObject.length != objectSize - 32 + i) {
                System.out.println("length corruption at " + i);
                fail = true;
            }
            for (int x = 0; x < largeObject.length; x++) {
                if (largeObject[x] != (byte) i) {
                    System.out.println("corruption at [" + i + "][" + x + "]");
                    fail = true;
                }
            }
        }
        if (fail) {
           throw new RuntimeException("Identity hash-code, length or data corruption detected.");
        }
    }
}
