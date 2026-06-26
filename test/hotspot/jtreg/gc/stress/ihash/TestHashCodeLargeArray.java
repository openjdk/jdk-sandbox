/*
 * Copyright (c) 2026, Amazon.com, Inc. or its affiliates. All rights reserved.
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
 * @bug 8387311
 * @test id=parallel
 * @summary Hashing a >2GB array and relocating it must not overflow the hash
 *          offset and corrupt the heap (compact object headers).
 * @library /test/lib
 * @library /
 * @requires vm.gc.Parallel
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @requires os.maxMemory >= 8G
 * @requires sun.arch.data.model == "64"
 * @key stress
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseParallelGC -Xmx6g
 *      TestHashCodeLargeArray
 */

/*
 * @bug 8387311
 * @test id=serial
 * @summary Hashing a >2GB array and relocating it must not overflow the hash
 *          offset and corrupt the heap (compact object headers).
 * @library /test/lib
 * @library /
 * @requires vm.gc.Serial
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @requires os.maxMemory >= 8G
 * @requires sun.arch.data.model == "64"
 * @key stress
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseSerialGC -Xmx6g
 *      TestHashCodeLargeArray
 */

/*
 * @bug 8387311
 * @test id=g1
 * @summary Hashing a >2GB array and relocating it must not overflow the hash
 *          offset and corrupt the heap (compact object headers).
 * @library /test/lib
 * @library /
 * @requires vm.gc.G1
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @requires os.maxMemory >= 8G
 * @requires sun.arch.data.model == "64"
 * @key stress
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseG1GC -Xmx6g
 *      TestHashCodeLargeArray
 */

/*
 * @bug 8387311
 * @test id=shen
 * @summary Hashing a >2GB array and relocating it must not overflow the hash
 *          offset and corrupt the heap (compact object headers).
 * @library /test/lib
 * @library /
 * @requires vm.gc.Shenandoah
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @requires os.maxMemory >= 8G
 * @requires sun.arch.data.model == "64"
 * @key stress
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseShenandoahGC -Xmx6g
 *      TestHashCodeLargeArray
 */

/*
 * @bug 8387311
 * @test id=zgc
 * @summary Hashing a >2GB array and relocating it must not overflow the hash
 *          offset and corrupt the heap (compact object headers).
 * @library /test/lib
 * @library /
 * @requires vm.gc.Z
 * @requires vm.opt.UseCompactObjectHeaders == null | vm.opt.UseCompactObjectHeaders == true
 * @requires os.maxMemory >= 8G
 * @requires sun.arch.data.model == "64"
 * @key stress
 * @run main/othervm -XX:+UseCompactObjectHeaders -XX:+UseZGC -Xmx6g
 *      TestHashCodeLargeArray
 */

import jtreg.SkippedException;

/**
 * Regression test for an integer-overflow in ArrayKlass::hash_offset_in_bytes
 * under -XX:+UseCompactObjectHeaders.
 *
 * With compact headers an array's identity hash is stored in a hidden word that
 * the GC appends right after the elements when it "expands" the (hashed but not
 * yet expanded) object. The offset of that word was computed as
 * {@code base_offset + (length << log2_element_size)} in 32-bit int. For a
 * multi-byte element array of ~2GB or more this overflows to a negative value;
 * the GC then writes the 4-byte hash at a sign-extended negative displacement
 * from the object base, outside the array -- a SIGSEGV or, worse, silent
 * corruption of a neighboring heap object.
 *
 * The test allocates an int[] just past the 2GB element-byte boundary
 * (length 2^29 + 16, which makes the would-be hash offset exceed INT_MAX),
 * installs its identity hash, forces several relocating GC cycles, and verifies
 * the array contents and identity hash survive. On the buggy VM this crashes
 * during the first full GC that relocates the array.
 */
public class TestHashCodeLargeArray {

    // 2^29 + 16 ints. payload = (2^29 + 16) * 4 bytes > INT_MAX, so the hash
    // word offset (base + payload) overflows a 32-bit int in the buggy code.
    static final int LEN = 536870912 + 16;

    static volatile Object sink;

    public static void main(String[] args) {
        int[] a;
        try {
            a = new int[LEN];
        } catch (OutOfMemoryError e) {
            System.out.println("Not enough heap to allocate the array; skipping.");
            return;
        }

        // Touch first/last element so corruption of the tail is detectable.
        a[0] = 0x11111111;
        a[LEN - 1] = 0x22222222;

        // Install identity hash -> mark word becomes "hashed, not expanded".
        int h0 = System.identityHashCode(a);

        // Force relocation: the compacting GC expands the object and writes the
        // hash word at hash_offset_in_bytes(). This is where the overflow bit.
        for (int round = 0; round < 5; round++) {
            // Short-lived garbage to provoke promotion / compaction.
            for (int i = 0; i < 64; i++) {
                sink = new byte[1 << 20];
            }
            System.gc();

            int h1 = System.identityHashCode(a);
            if (h1 != h0) {
                throw new RuntimeException("identity hash changed across GC: h0=" + h0 + " h1=" + h1);
            }
            if (a.length != LEN || a[0] != 0x11111111 || a[LEN - 1] != 0x22222222) {
                throw new RuntimeException("array contents corrupted after GC round " + round
                        + ": length=" + a.length + " first=" + Integer.toHexString(a[0])
                        + " last=" + Integer.toHexString(a[LEN - 1]));
            }
        }

        System.out.println("PASSED: large-array identity hash stable across relocation");
    }
}
