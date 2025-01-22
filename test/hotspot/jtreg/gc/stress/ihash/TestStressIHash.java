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
 */

package gc.stress.ihash;

/*
 * @test id=Serial
 * @bug 8372151
 * @summary Stress test: cloning identity-hashed objects must not copy mark-word hash-control bits
 * @requires vm.gc.Serial
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseSerialGC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+VerifyDuringGC
 *      -Xmx256m
 *      gc.stress.ihash.TestStressIHash
 */

/*
 * @test id=Parallel
 * @bug 8372151
 * @summary Stress test: cloning identity-hashed objects must not copy mark-word hash-control bits
 * @requires vm.gc.Parallel
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseParallelGC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+VerifyDuringGC
 *      -Xmx256m
 *      gc.stress.ihash.TestStressIHash
 */

/*
 * @test id=G1
 * @bug 8372151
 * @summary Stress test: cloning identity-hashed objects must not copy mark-word hash-control bits
 * @requires vm.gc.G1
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseG1GC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+VerifyDuringGC
 *      -Xmx256m
 *      gc.stress.ihash.TestStressIHash
 */

/*
 * @test id=Shenandoah
 * @bug 8372151
 * @summary Stress test: cloning identity-hashed objects must not copy mark-word hash-control bits
 * @requires vm.gc.Shenandoah
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseShenandoahGC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+VerifyDuringGC
 *      -Xmx256m
 *      gc.stress.ihash.TestStressIHash
 */

/*
 * @test id=Z
 * @bug 8372151
 * @summary Stress test: cloning identity-hashed objects must not copy mark-word hash-control bits
 * @requires vm.gc.Z
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseZGC
 *      -XX:+UnlockDiagnosticVMOptions -XX:+VerifyDuringGC
 *      -Xmx256m
 *      gc.stress.ihash.TestStressIHash
 */

/*
 * @test id=C2-Serial
 * @bug 8372151
 * @summary C2 clone of objects with narrowOop at offset 4 must use mismatched access
 * @requires vm.gc.Serial
 * @requires vm.opt.TieredCompilation != true
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseSerialGC
 *      -XX:-TieredCompilation
 *      -Xmx256m
 *      gc.stress.ihash.TestStressIHash clone-ref
 */

/*
 * @test id=C2-Parallel
 * @bug 8372151
 * @summary C2 clone of objects with narrowOop at offset 4 must use mismatched access
 * @requires vm.gc.Parallel
 * @requires vm.opt.TieredCompilation != true
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseParallelGC
 *      -XX:-TieredCompilation
 *      -Xmx256m
 *      gc.stress.ihash.TestStressIHash clone-ref
 */

/*
 * @test id=C2-G1
 * @bug 8372151
 * @summary C2 clone of objects with narrowOop at offset 4 must use mismatched access
 * @requires vm.gc.G1
 * @requires vm.opt.TieredCompilation != true
 * @key stress
 * @run main/othervm/timeout=300
 *      -XX:+UseCompactObjectHeaders -XX:+UseG1GC
 *      -XX:-TieredCompilation
 *      -Xmx256m
 *      gc.stress.ihash.TestStressIHash clone-ref
 */

import java.util.Random;

/**
 * Regression test for JDK-8372151.
 *
 * With compact object headers (4-byte mark-word), the identity hash state is
 * tracked via two "hashctrl" bits in the mark-word. When an object is hashed
 * and later relocated by GC, the GC may "expand" it by one HeapWord to store
 * the hash value, setting the hashctrl bits to "hashed-expanded" (11).
 *
 * The bug: Object.clone() copied the mark-word from source to destination,
 * including the hashctrl bits. A clone of an expanded object would therefore
 * appear expanded (hashctrl=11) without actually having the extra HeapWord
 * allocated. This causes two problems:
 *  1. The clone's identity hash is the same as the source's (semantic bug).
 *  2. JVM_Clone allocates the clone at expanded size but then resets the
 *     mark-word to prototype (not-hashed, not-expanded), creating a size
 *     mismatch that crashes GCs using linear heap iteration (e.g., Serial).
 *
 * This test creates objects whose layout guarantees hash-expansion (a single
 * int field: header(4) + int(4) = 8 bytes, no room for the 4-byte hash),
 * hashes them, forces a GC to expand them, then clones them. It verifies
 * both that clones get unique identity hashes (semantic correctness) and that
 * the heap remains intact (-XX:+VerifyDuringGC detects size mismatches).
 */
public class TestStressIHash {

    // With compact headers: header(4) + int(4) = 8 bytes (1 HeapWord).
    // The identity hash needs 4 bytes but there is no gap in the layout,
    // so GC must expand the object by one word when preserving the hash.
    static class Payload implements Cloneable {
        int field;

        Payload(int v) { field = v; }

        @Override
        public Payload clone() {
            try {
                return (Payload) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // With compact headers: header(4) + narrowOop(4) + narrowOop(4) + ...
    // All fields are object references so HotSpot's field layout places a
    // narrowOop at offset 4 (the first slot after the 4-byte compact header).
    // The C2 clone intrinsic (BarrierSetC2::clone) pre-copies the 4 bytes at
    // offset 4 as a raw T_INT. When C2 later expands the ArrayCopyNode via
    // try_clone_instance, it creates typed stores for ALL fields, including a
    // StoreN (narrowOop store) at offset 4. Without the mismatched-access flag
    // on the pre-copy StoreI, IGVN's StoreNode::Ideal walks the memory chain
    // and asserts that chained stores have matching opcodes (StoreN vs StoreI).
    //
    // Important: NO int/long/float/double fields — HotSpot's field sorter
    // would place those at offset 4 to fill the gap, pushing the narrowOop
    // to a higher (8-byte-aligned) offset where no mismatch occurs.
    static class RefPayload implements Cloneable {
        Object ref;
        Object ref2;
        Object ref3;
        Object ref4;

        RefPayload(Object r) {
            ref = r;
            ref2 = r;
            ref3 = r;
            ref4 = r;
        }

        @Override
        public RefPayload clone() {
            try {
                return (RefPayload) super.clone();
            } catch (CloneNotSupportedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    static final int BATCH_SIZE = 100_000;
    static final int MAX_SURVIVORS = 1_000_000;

    // Small helper methods that C2 will compile and inline the clone
    // intrinsic into. Keeping them small encourages C2 to intrinsify
    // Object.clone() rather than emitting a call.
    static Payload clonePayload(Payload p) {
        return p.clone();
    }

    // Allocate, clone, and return the clone's ref field. When C2 compiles
    // this, it sees the source allocation inline and can constant-fold the
    // LoadN from the clone expansion (try_clone_instance), leaving the
    // pre-copy StoreI at offset 4 with outcnt==1. IGVN then walks the
    // memory chain from the typed StoreN and hits the StoreI, triggering
    // the assertion for mismatched store opcodes.
    static Object cloneRefPayload(Object r) {
        RefPayload p = new RefPayload(r);
        RefPayload c = p.clone();
        return c.ref;
    }

    public static void main(String[] args) {
        boolean cloneRef = args.length > 0 && "clone-ref".equals(args[0]);

        if (cloneRef) {
            testCloneRefPayload();
        } else {
            testClonePayload();
        }
    }

    // C2 regression test: exercises the clone pre-copy StoreI/StoreN
    // mismatch at offset 4. Runs cloneRefPayload in a tight loop so C2
    // compiles it and hits the assertion during IGVN.
    static void testCloneRefPayload() {
        Object anchor = new Object();
        for (int i = 0; i < 100_000; i++) {
            Object result = cloneRefPayload(anchor);
            if (result != anchor) {
                throw new RuntimeException("FAIL: RefPayload clone has wrong ref field");
            }
        }
    }

    // GC stress test: clones identity-hashed Payload objects across GC
    // cycles and verifies hash uniqueness and heap integrity.
    static void testClonePayload() {
        Random rng = new Random(12345);
        Object[] survivors = new Object[MAX_SURVIVORS];
        int survivorCount = 0;
        int totalCreated = 0;
        int sharedHashes = 0;
        int totalCloned = 0;

        while (survivorCount < MAX_SURVIVORS) {
            // Phase 1: Create a batch of objects and hash every one of them.
            Payload[] batch = new Payload[BATCH_SIZE];
            int[] srcHashes = new int[BATCH_SIZE];
            for (int i = 0; i < BATCH_SIZE; i++) {
                batch[i] = new Payload(totalCreated++);
                srcHashes[i] = System.identityHashCode(batch[i]);
            }

            // Phase 2: Force a GC cycle. Surviving hashed objects that need
            // expansion get relocated with an extra HeapWord and their
            // hashctrl bits set to "hashed-expanded" (11).
            System.gc();

            // Phase 3: Clone the (now expanded) batch objects and verify that
            // each clone gets a unique identity hash. With the bug, clones
            // inherit the source's hash because the hashctrl bits and stored
            // hash value are copied from the source mark-word.
            for (int i = 0; i < BATCH_SIZE; i++) {
                Payload clone = clonePayload(batch[i]);
                totalCloned++;
                int cloneHash = System.identityHashCode(clone);
                if (cloneHash == srcHashes[i]) {
                    sharedHashes++;
                }
                if (rng.nextInt(10) == 0 && survivorCount < MAX_SURVIVORS) {
                    survivors[survivorCount++] = clone;
                }
            }

            // Let the originals die.
            batch = null;
        }

        // Final GC: with VerifyDuringGC, this walks the heap and will detect
        // corrupt clones if the allocation-size vs mark-word bug is present.
        System.gc();

        // Verify that clones got independent identity hashes. Random hash
        // collisions are possible but extremely rare (~1 in 2^32 per pair).
        // The threshold of 10 accounts for any statistical noise.
        if (sharedHashes > 10) {
            throw new RuntimeException("FAIL: " + sharedHashes + " / " + totalCloned
                + " clones share identity hash with source object");
        }
    }
}
