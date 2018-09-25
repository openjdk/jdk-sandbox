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
package org.openjdk.micro.jdk.java.util.zip;

import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.Checksum;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 *
 * Base class for benchmarking JDK supported Checksums with unaligned memory
 * accesses.
 *
 * To use the base class extend it and use a setup method configure the checksum
 * field.
 *
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public abstract class ChecksumUnalignedBenchmarks {

    private final byte[] unalignedByteArray_1k = new byte[1034];
    private final ByteBuffer unalignedWrappedByteBuffer_1k = ByteBuffer.wrap(unalignedByteArray_1k);
    private final ByteBuffer unalignedDirectByteBuffer_1k = ByteBuffer.allocateDirect(unalignedByteArray_1k.length);

    @Setup
    final public void setup() {
        Random r = new Random(123456789L);
        r.nextBytes(unalignedByteArray_1k);
        unalignedDirectByteBuffer_1k.put(unalignedByteArray_1k);
    }

    protected Checksum checksum;

    @Param({"0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "10"})
    private int offset;

    @Benchmark
    @OperationsPerInvocation(1024)
    public void byteArray_1K() {
        checksum.update(unalignedByteArray_1k, offset, 1024);
    }

    @Benchmark
    @OperationsPerInvocation(1024)
    public void wrappedByteBuffer_1K() {
        unalignedWrappedByteBuffer_1k.position(offset);
        unalignedWrappedByteBuffer_1k.limit(offset + 1024);
        checksum.update(unalignedWrappedByteBuffer_1k);
    }

    @Benchmark
    @OperationsPerInvocation(1024)
    public void directByteBuffer_1K() {
        unalignedDirectByteBuffer_1k.position(offset);
        unalignedWrappedByteBuffer_1k.limit(offset + 1024);
        checksum.update(unalignedDirectByteBuffer_1k);
    }
}
