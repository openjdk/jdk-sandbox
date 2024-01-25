/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch.iouring;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.nio.ByteBuffer;

import static sun.nio.ch.iouring.Util.strerror;
import static sun.nio.ch.iouring.foreign.iouring_h.*;

/**
 * An abstract base class which implements blocking read and write
 * operations through an IOURing.
 *
 * Traditional single buffer read and write calls are provided.
 * Also, there is the possibility to register ByteBuffers with the
 * kernel (fixed buffers) whose contents are mapped in both user
 * and kernel space. The readFixed and writeFixed calls are
 * used with registered buffers.
 */
abstract class IOURingReaderWriter {
    protected final BlockingRing ring;

    protected final FDImpl fd;
    protected final SystemCallContext ctx;
    protected final Arena autoArena = Arena.ofAuto();

    protected final IOUring iouring;

    IOURingReaderWriter(BlockingRing ring, FDImpl fd) {
        this.ring = ring;
        this.iouring = ring.ring();
        this.fd = fd;
        this.ctx = new SystemCallContext();
    }
    public int write(ByteBuffer buffer) throws IOException, InterruptedException {
        buffer = getDirectBuffer(buffer);
        int len = buffer.remaining();
        MemorySegment data = MemorySegment.ofBuffer(buffer);
        Sqe request = new Sqe()
                .opcode(IORING_OP_WRITE())
                .fd(fd.fd())
                .addr(data)
                .off(-1)
                .len(len);
        Cqe response = ring.blockingSubmit(request);
        int result = response.res();
        if (result < 0) {
            throw new IOException("Write failed: " + strerror(-result));
        }
        return result;
    }

    public long asyncWrite(ByteBuffer buffer) throws IOException, InterruptedException {
        buffer = getDirectBuffer(buffer);
        int len = buffer.remaining();
        MemorySegment data = MemorySegment.ofBuffer(buffer);
        Sqe request = new Sqe()
                .opcode(IORING_OP_WRITE())
                .fd(fd.fd())
                .addr(data)
                .off(-1L)
                .len(len);
        long id = iouring.submit(request);
        return id;
    }

    /**
     * Writes the contents of the supplied (registered) buffer. When this call
     * returns the buffer has already been returned to the IOUring registered
     * buffer list and should not be accessed again.
     *
     * @param buffer
     * @throws IOException
     * @throws IllegalArgumentException if buffer is not registered
     */
    public int writeFixed(ByteBuffer buffer) throws IOException, InterruptedException {
        int index = iouring.checkAndGetIndexFor(buffer);
        int len = buffer.remaining();
        MemorySegment data = MemorySegment.ofBuffer(buffer);
        Sqe request = new Sqe()
                .opcode(IORING_OP_WRITE_FIXED())
                .fd(fd.fd())
                .buf_index(index)
                .off(-1)
                .addr(data)
                .len(len);
        Cqe response = ring.blockingSubmit(request);
        //iouring().returnRegisteredBuffer(buffer);
        int result = response.res();
        if (result < 0) {
            System.out.println("errno " + result);
            throw new IOException("Write failed: " + strerror(-result));
        }
        buffer.position(buffer.position()+result);
        return result;
    }

    private MemorySegment getSegmentFor(ByteBuffer buf) {
        return null;
    }
    public int read(ByteBuffer buffer1) throws IOException, InterruptedException {
        ByteBuffer buffer = getDirectBuffer(buffer1);
        int len = buffer.remaining();
        MemorySegment data = MemorySegment.ofBuffer(buffer);
        Sqe request = new Sqe()
                .opcode(IORING_OP_READ())
                .fd(fd.fd())
                .addr(data)
                .len(len);
        Cqe response = ring.blockingSubmit(request);
        int result = response.res();
        if (result < 0) {
            throw new IOException("Read failed: " + strerror(-result));
        }
        return result;
    }

    /**
     * Reads into the supplied (registered) buffer. When this call returns
     * and subsequently when the caller is finished with registered
     * buffer, it should be returned to the IOURing with
     * {@link IOUring#returnRegisteredBuffer(ByteBuffer)}
     *
     * @param buffer
     * @throws IOException
     * @throws IllegalArgumentException if buffer is not registered
     */
    public int readFixed(ByteBuffer buffer) throws IOException, InterruptedException {
        int index = iouring.checkAndGetIndexFor(buffer);
        if (index == -1)
            throw new IllegalArgumentException("Unknown buffer");
        int len = buffer.remaining();
        MemorySegment data = MemorySegment.ofBuffer(buffer);
        Sqe request = new Sqe()
                .opcode(IORING_OP_READ_FIXED())
                .fd(fd.fd())
                .buf_index(index)
                .addr(data)
                .len(len);
        Cqe response = ring.blockingSubmit(request);
        int result = response.res();
        if (result < 0) {
            throw new IOException("Read fixed failed: " + strerror(-result));
        }
        buffer.position(0);
        buffer.limit(result);
        return result;
    }

    public BlockingRing ring() {
        return ring;
    }

    protected ByteBuffer getDirectBuffer(ByteBuffer buffer) {
        if (buffer.isDirect())
            return buffer;
        ByteBuffer db = ByteBuffer.allocateDirect(buffer.remaining());
        db.put(buffer);
        db.flip();
        return db;
    }
}
