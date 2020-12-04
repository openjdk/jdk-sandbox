/*
 * Copyright (c) 2000, 2021, Oracle and/or its affiliates. All rights reserved.
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

package java.nio;

import java.io.FileDescriptor;
import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.misc.ScopedMemoryAccess;
import jdk.internal.misc.VM;
import jdk.internal.ref.Cleaner;
import sun.nio.ch.DirectBuffer;

final class DirectByteBuffer extends MappedByteBuffer implements DirectBuffer {

    private final Cleaner cleaner;

    private DirectByteBuffer(Deallocator deallocator,
                             long addr,
                             int mark, int pos, int lim, int cap,
                             FileDescriptor fd,
                             boolean isSync,
                             boolean readOnly,
                             boolean bigEndian,
                             Object attachment,
                             MemorySegmentProxy segment) {
        super(addr, mark, pos, lim, cap, fd, isSync, readOnly, bigEndian, attachment, segment);
        if (deallocator != null) {
            this.cleaner = Cleaner.create(this, deallocator);
        } else {
            this.cleaner = null;
        }
    }

    // Invoked only by JNI: NewDirectByteBuffer(void*, long)
    //
    private DirectByteBuffer(long addr, int cap) {
        this(null, addr, -1, 0, cap, cap, null, false, false, NORD_IS_BIG, null, null);
    }

    // For memory-mapped buffers -- invoked by FileChannelImpl via reflection
    //
    DirectByteBuffer(int cap,
                     long addr,
                     FileDescriptor fd,
                     Runnable unmapper,  // TODO: why this?
                     boolean isSync,
                     boolean readOnly,
                     boolean bigEndian,
                     MemorySegmentProxy segment) {
        this(null, addr, -1, 0, cap, cap, fd, isSync, readOnly, bigEndian, null, segment);
    }

    // Invoked to construct a direct ByteBuffer referring to the block of
    // memory. A given arbitrary object may also be attached to the buffer.
    //
    DirectByteBuffer(long addr, int cap, Object attachment, FileDescriptor fd, boolean sync, boolean readOnly, boolean bigEndian, MemorySegmentProxy segment) {
        this(null, addr, -1, 0, cap, cap, fd, sync, readOnly, bigEndian, attachment, segment);
    }

    @Override
    Object base() {
        return null;
    }

    @Override
    public boolean isDirect() {
        return true;
    }

    @Override
    public long address() {
        ScopedMemoryAccess.Scope scope = scope();
        if (scope != null) {
            if (scope.ownerThread() == null) {
                throw new UnsupportedOperationException("ByteBuffer derived from shared segments not supported");
            }
            try {
                scope.checkValidState();
            } catch (ScopedMemoryAccess.Scope.ScopedAccessError e) {
                throw new IllegalStateException("This segment is already closed");
            }
        }
        return address;
    }

    @Override
    public Cleaner cleaner() {
        return cleaner;
    }

    @Override
    public Object attachment() {
        return attachment;
    }

    @Override
    ByteBuffer dup(int offset, int mark, int pos, int lim, int cap, boolean readOnly) {
        return new DirectByteBuffer(null,  /* deallocator */
                                    address + offset,
                                    mark, pos, lim, cap,
                                    fd, isSync,
                                    readOnly,
                                    true, /*big-endian*/
                                    attachmentValueOrThis(),
                                    segment);
    }

    private static class Deallocator implements Runnable {
        private final long size;
        private final int capacity;
        private long address;   // TODO: this should be final, remove paranoia?

        private Deallocator(long address, long size, int capacity) {
            assert (address != 0);
            this.address = address;
            this.size = size;
            this.capacity = capacity;
        }

        public void run() {
            if (address == 0) {
                // Paranoia
                return;
            }
            UNSAFE.freeMemory(address);
            address = 0;
            Bits.unreserveMemory(size, capacity);
        }

    }

    static MappedByteBuffer createDirectBuffer(int cap) {
        boolean pa = VM.isDirectMemoryPageAligned();
        int ps = Bits.pageSize();
        long size = Math.max(1L, (long)cap + (pa ? ps : 0));
        Bits.reserveMemory(size, cap);

        long base;
        try {
            base = UNSAFE.allocateMemory(size);
        } catch (OutOfMemoryError x) {
            Bits.unreserveMemory(size, cap);
            throw x;
        }
        UNSAFE.setMemory(base, size, (byte) 0);
        final long address;
        if (pa && (base % ps != 0)) {
            // Round up to page boundary
            address = base + ps - (base & (ps - 1));
        } else {
            address = base;
        }
        return new DirectByteBuffer(new Deallocator(base, size, cap),
                                    address,
                                    -1, 0, cap, cap,
                                    null,  // fd
                                    false, // isSync
                                    false, // read-only
                                    true,  // big-endian
                                    null,  // attachment
                                    null); // segment
    }
}