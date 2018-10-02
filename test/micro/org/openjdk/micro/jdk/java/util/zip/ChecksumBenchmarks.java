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
import java.nio.charset.StandardCharsets;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.zip.Checksum;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.OperationsPerInvocation;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 *
 * Base class for benchmarking JDK supported Checksums.
 *
 * To use the base class extend it and use a setup method configure the checksum
 * field.
 * 
 */
@State(Scope.Thread)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
public abstract class ChecksumBenchmarks {

    private final byte[] bytes_1to9 = "123456789".getBytes(StandardCharsets.US_ASCII);
    private final byte[] byteArray_1k = new byte[1024];
    private final byte[] byteArray_64k = new byte[65536];
    private final ByteBuffer wrappedByteBuffer_1k = ByteBuffer.wrap(byteArray_1k);
    private final ByteBuffer readonlyByteBuffer_1k = ByteBuffer.wrap(byteArray_1k).asReadOnlyBuffer();
    private final ByteBuffer directByteBuffer_1k = ByteBuffer.allocateDirect(byteArray_1k.length);
    private final ByteBuffer wrappedByteBuffer_64k = ByteBuffer.wrap(byteArray_64k);
    private final ByteBuffer readonlyByteBuffer_64k = ByteBuffer.wrap(byteArray_64k).asReadOnlyBuffer();
    private final ByteBuffer directByteBuffer_64k = ByteBuffer.allocateDirect(byteArray_64k.length);

    @Setup
    final public void setup() {
        Random r = new Random(123456789L);
        r.nextBytes(byteArray_1k);
        r.nextBytes(byteArray_64k);
        directByteBuffer_1k.put(byteArray_1k);
        directByteBuffer_64k.put(byteArray_64k);
    }

    protected Checksum checksum;

    @Benchmark
    @OperationsPerInvocation(9)
    public void byteArray_9() {
        checksum.update(bytes_1to9);
    }

    @Benchmark
    @OperationsPerInvocation(1024)
    public void byteArray_1K() {
        checksum.update(byteArray_1k);
    }

    @Benchmark
    @OperationsPerInvocation(1024)
    public void wrappedByteBuffer_1K() {
        wrappedByteBuffer_1k.position(0);
        checksum.update(wrappedByteBuffer_1k);
    }

    @Benchmark
    @OperationsPerInvocation(1024)
    public void readonlyByteBuffer_1K() {
        readonlyByteBuffer_1k.position(0);
        checksum.update(readonlyByteBuffer_1k);
    }

    @Benchmark
    @OperationsPerInvocation(1024)
    public void directByteBuffer_1K() {
        directByteBuffer_1k.position(0);
        checksum.update(directByteBuffer_1k);
    }

    @Benchmark
    @OperationsPerInvocation(65536)
    public void byteArray_64K() {
        checksum.update(byteArray_64k);
    }

    @Benchmark
    @OperationsPerInvocation(65536)
    public void wrappedByteBuffer_64K() {
        wrappedByteBuffer_64k.position(0);
        checksum.update(wrappedByteBuffer_64k);
    }

    @Benchmark
    @OperationsPerInvocation(65536)
    public void readonlyByteBuffer_64K() {
        readonlyByteBuffer_64k.position(0);
        checksum.update(readonlyByteBuffer_64k);
    }

    @Benchmark
    @OperationsPerInvocation(65536)
    public void directByteBuffer_64K() {
        directByteBuffer_64k.position(0);
        checksum.update(directByteBuffer_64k);
    }
}
