/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.micro.hotspot.gc.g1;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.util.HashMap;
import java.util.List;
import java.util.LinkedList;
import java.util.concurrent.TimeUnit;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;

/**
 * Several different tests cases of reference writes that might require write
 * barrier depending on the GC used. Multiple sub-classes available with
 * specific command line options set to test G1, Parallel GC and CMS.
 *
 * @author staffan.friberg@oracle.com (sfriberg)
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5)
@Measurement(iterations = 5)
@Fork(jvmArgsAppend = {"-XX:+UseG1GC", "-Xmx256m", "-Xms256m", "-Xmn64m"}, value = 5)
public class WriteBarrier {

    // Datastructures that enables writes between different parts and regions on the heap
    private Object oldReferee_region1;
    private Object oldReferee_region2;
    private Object youngReferee_region3;
    private Object youngReferee_region4;
    private Object nullReferee = null;

    private static final int OLD_REFERENCES_LENGTH = 131072;
    private final Holder[] oldReferences = new Holder[OLD_REFERENCES_LENGTH];
    private Holder oldReference_region1;
    private Holder youngReference_region3;

    // Keep alive to avoid them being garbage collected but not used in benchmarks
    private final LinkedList<Holder> padding = new LinkedList<>();
    private final LinkedList<Holder> liveData = new LinkedList<>();
    private final HashMap<String, Long> gcCount = new HashMap<>();

    /**
     * Setup method for the benchmarks
     *
     * Allocate objects in a certain order to make sure the end up on the heap
     * in the right way to later use them in tests.
     */
    @Setup
    public void setup() {
        // Allocate together and System.gc to move them to Old Space and
        // keep in the same region by doing a fast promotion
        oldReferee_region1 = new Object();
        oldReference_region1 = new Holder(oldReferee_region1);
        System.gc();

        // Fill up old space to 80%
        List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
        for (MemoryPoolMXBean pool : pools) {
            if (pool.getName().contains("Old Gen")) {
                pool.setUsageThreshold((pool.getUsage().getMax() / 5) * 4);

                while (!pool.isUsageThresholdExceeded()) {
                    // Allocate new referee and and then increase live data count
                    // and force promotion until heap is full enough. The last
                    // oldReferee will most likely be located in a different region
                    // compared to the the initially allocated objects.
                    oldReferee_region2 = new Object();
                    for (int i = 0; i < 10000; i++) {
                        liveData.add(new Holder(new byte[512], new Object()));
                    }
                }
                break;
            }
        }
        int index = 0;
        for (Holder holder : liveData) {
            if (index < oldReferences.length) {
                oldReferences[index++] = holder;
            }
        }

        // Allocate reference and referee together to keep them in same region
        // Allocate Object first so they are located in the same memory order
        // as objects in old space
        youngReferee_region3 = new Object();
        youngReference_region3 = new Holder(youngReferee_region3);

        // Allocate padding and a new referee to make sure the reference and
        // referee are in different regions
        for (int i = 0; i < 2000; i++) {
            Holder tempHolder = new Holder(new byte[500], new Object());
            padding.add(tempHolder);
            youngReferee_region4 = tempHolder.getReference();
        }

        /*
         * Get GC numbers after all allocation but before any benchmark execution
         * starts to verify that no GCs happen during the benchmarking it self as
         * object will then move.
         */
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            gcCount.put(gcBean.getName(), gcBean.getCollectionCount());
        }
    }

    /**
     * Invalidate any benchmark result if a GC occurs during execution of
     * benchmark as moving objects will destroy the assumptions of the tests
     */
    @TearDown(Level.Iteration)
    public void checkGCCount() {
        List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
        for (GarbageCollectorMXBean gcBean : gcBeans) {
            if (gcBean.getCollectionCount() != gcCount.get(gcBean.getName())) {
                throw new RuntimeException("A GC has happened during iteration and the microbenchmark result is invalid.");
            }
        }
    }

    /**
     * Write a reference in an object located in the old space and where the
     * written pointer is to a old object in the same region
     */
    @Benchmark
    public void oldPointingToOldInSameRegion() {
        oldReference_region1.setReference(oldReferee_region1);
    }

    /**
     * Write a reference in an object located in the old space and where the
     * written pointer is to a old object in a different region
     */
    @Benchmark
    public void oldPointingToOldInDifferentRegion() {
        oldReference_region1.setReference(oldReferee_region2);
    }

    /**
     * Write a reference in an object located in the old space and where the
     * written pointer is to an object in the young space
     */
    @Benchmark
    public void oldPointingToYoungInDifferentRegion() {
        oldReference_region1.setReference(youngReferee_region3);
    }

    /**
     * Write a reference in an object located in the young space and where the
     * written pointer is to an object in the old space
     */
    @Benchmark
    public void youngPointingToOldInDifferentRegion() {
        youngReference_region3.setReference(oldReferee_region2);
    }

    /**
     * Write a reference in an object located in the young space and where the
     * written pointer is to a young object in the same region
     */
    @Benchmark
    public void youngPointingToYoungInSameRegion() {
        youngReference_region3.setReference(youngReferee_region3);
    }

    /**
     * Write a reference in an object located in the young space and where the
     * written pointer is to a young object in a different region
     */
    @Benchmark
    public void youngPointingToYoungInDifferentRegion() {
        youngReference_region3.setReference(youngReferee_region4);
    }

    /**
     * Write by compiler provable null to an object located in old space
     */
    @Benchmark
    public void oldPointingToExplicitNull() {
        oldReference_region1.setReference(null);
    }

    /**
     * Write by compiler unprovable null to an object located in old space
     */
    @Benchmark
    public void oldPointingToImplicitNull() {
        oldReference_region1.setReference(nullReferee);
    }

    /**
     * Write by compiler provable null to an object located in young space
     */
    @Benchmark
    public void youngPointingToExplicitNull() {
        youngReference_region3.setReference(null);
    }

    /**
     * Write by compiler unprovable null to an object located in young space
     */
    @Benchmark
    public void youngPointingToImplicitNull() {
        youngReference_region3.setReference(nullReferee);
    }

    /**
     * Iterate and update over many old references to point to a young object.
     * Since they are in different regions we will need to check the card, and
     * since we will update many different reference in different memory
     * locations/cards the card will need to be queued as no filtering will
     * catch it.
     */
    @Benchmark
    @OperationsPerInvocation(value = OLD_REFERENCES_LENGTH)
    public void manyOldPointingToYoung() {
        for (Holder oldReference : oldReferences) {
            oldReference.setReference(youngReferee_region3);
        }
    }

    /**
     * Iterate and update over many old references to point to explicit null.
     */
    @Benchmark
    @OperationsPerInvocation(value = OLD_REFERENCES_LENGTH)
    public void manyOldPointingToExplicitNull() {
        for (Holder oldReference : oldReferences) {
            oldReference.setReference(null);
        }
    }

    /**
     * Iterate and update over many old references to point to implicit null.
     */
    @Benchmark
    @OperationsPerInvocation(value = OLD_REFERENCES_LENGTH)
    public void manyOldPointingToImplicitNull() {
        for (Holder oldReference : oldReferences) {
            oldReference.setReference(nullReferee);
        }
    }

    /*
     * Holder object for reference and padding
     */
    static class Holder {

        private Object reference;
        private final byte[] padding;

        public Holder(Object reference) {
            this(null, reference);
        }

        public Holder(byte[] padding, Object reference) {
            this.padding = padding;
            this.reference = reference;
        }

        public void setReference(Object reference) {
            this.reference = reference;
        }

        public Object getReference() {
            return this.reference;
        }
    }
}
