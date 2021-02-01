/*
 * Copyright (c) 2020, 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.ref.Reference;
import java.lang.reflect.Array;
import java.util.Objects;
import jdk.internal.access.foreign.MemorySegmentProxy;
import jdk.internal.vm.annotation.ForceInline;

/**
 * An abstract Buffer implementation.
 *
 * <p> This class contains most of the logic associated with the various buffer
 * subclasses. The methods in this class access the buffer's memory in an
 * abstract fashion, using the ScopedMemoryAccess API. Subclasses should
 * implement the abstract methods in this class so that memory access can occur
 * correctly and in the most efficient fashion.
 *
 * @param <B> the buffer class type, e.g. IntBuffer
 * @param <A> the primitive array type associated with the buffer, e.g. int[]
 */
abstract class AbstractBufferImpl<B extends AbstractBufferImpl<B,A>, A> extends Buffer {

    final Object attachment;

    AbstractBufferImpl(long addr, Object hb,
                       int mark, int pos, int lim, int cap,
                       boolean readOnly,
                       Object attachment,
                       MemorySegmentProxy segment) {
        super(addr, hb, mark, pos, lim, cap, readOnly, segment);
        this.attachment = attachment;
    }

    /**
     * True if the buffer is big-endian. Otherwise, false for little-endian.
     */
    abstract boolean bigEndian();

    /**
     * A scale factor, expressed as a number of shift positions associated with
     * the element size, which is used to turn a logical buffer index into a
     * concrete address (usable from unsafe access). That is, for an int buffer
     * given that each buffer index covers 4 bytes, the buffer index needs to be
     * shifted by 2 in order to obtain the corresponding address offset. For
     * performance reasons, it is best to override this method in each subclass,
     * so that the scale factor becomes effectively a constant.
     */
    abstract int scaleFactor();

    /**
     * The base object for the unsafe access. Must be overridden by concrete
     * subclasses so that (i) cases where base == null are explicit in the code
     * and (ii) cases where base != null also feature a cast to the correct
     * array type. Unsafe access intrinsics can work optimally only if both
     * conditions are met.
     */
    abstract Object base();

    /**
     * The array carrier associated with this buffer; e.g. a ShortBuffer will
     * have a short[] carrier.
     */
    abstract Class<A> carrier();

    /**
     * The offset of the first element in the storage allocation of the carrier
     * type.
     */
    abstract long arrayBaseOffset();

    /**
     * Creates a new buffer of the same type as this buffer, with the given
     * properties. This method is used to implement various methods featuring
     * covariant override.
     */
    abstract B dup(int offset, int mark, int pos, int lim, int cap, boolean readOnly);

    @Override
    public B slice() {
        final int pos = this.position();
        final int lim = this.limit();
        final int rem = (pos <= lim ? lim - pos : 0);
        final int off = (pos << scaleFactor());
        return dup(off, -1, 0, rem, rem, readOnly);
    }

    @Override
    public B slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        final int off = (index << scaleFactor());
        return dup(off, -1, 0, length, length, readOnly);
    }

    @Override
    public B duplicate() {
        return dup(0, markValue(), position(), limit(), capacity(), readOnly);
    }

    public B asReadOnlyBuffer() {
        return dup(0, markValue(), position(), limit(), capacity(), true);
    }

    // access primitives

    /** Returns the address/offset for the given buffer position. */
    @ForceInline
    final long ix(int pos) {
        return address + ((long)pos << scaleFactor());
    }

    @ForceInline
    final byte getByteImpl(int pos) {
        try {
            return SCOPED_MEMORY_ACCESS.getByte(scope(), base(), ix(pos));
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putByteImpl(int pos, byte value) {
        try {
            SCOPED_MEMORY_ACCESS.putByte(scope(), base(), ix(pos), value);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final char getCharImpl(int pos) {
        try {
            return SCOPED_MEMORY_ACCESS.getCharUnaligned(scope(), base(), ix(pos), bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putCharImpl(int pos, char value) {
        try {
            SCOPED_MEMORY_ACCESS.putCharUnaligned(scope(), base(), ix(pos), value, bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final short getShortImpl(int pos) {
        try {
            return SCOPED_MEMORY_ACCESS.getShortUnaligned(scope(), base(), ix(pos), bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putShortImpl(int pos, short value) {
        try {
            SCOPED_MEMORY_ACCESS.putShortUnaligned(scope(), base(), ix(pos), value, bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final int getIntImpl(int pos) {
        try {
            return SCOPED_MEMORY_ACCESS.getIntUnaligned(scope(), base(), ix(pos), bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putIntImpl(int pos, int value) {
        try {
            SCOPED_MEMORY_ACCESS.putIntUnaligned(scope(), base(), ix(pos), value, bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final long getLongImpl(int pos) {
        try {
            return SCOPED_MEMORY_ACCESS.getLongUnaligned(scope(), base(), ix(pos), bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putLongImpl(int pos, long value) {
        try {
            SCOPED_MEMORY_ACCESS.putLongUnaligned(scope(), base(), ix(pos), value, bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final float getFloatImpl(int pos) {
        try {
            int x = SCOPED_MEMORY_ACCESS.getIntUnaligned(scope(), base(), ix(pos), bigEndian());
            return Float.intBitsToFloat(x);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putFloatImpl(int pos, float value) {
        try {
            int x = Float.floatToRawIntBits(value);
            SCOPED_MEMORY_ACCESS.putIntUnaligned(scope(), base(), ix(pos), x, bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final double getDoubleImpl(int pos) {
        try {
            long x = SCOPED_MEMORY_ACCESS.getLongUnaligned(scope(), base(), ix(pos), bigEndian());
            return Double.longBitsToDouble(x);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putDoubleImpl(int pos, double value) {
        try {
            long x = Double.doubleToRawLongBits(value);
            SCOPED_MEMORY_ACCESS.putLongUnaligned(scope(), base(), ix(pos), x, bigEndian());
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    // bulk access primitives

    @ForceInline
    final void getBulkImpl(A dst, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, Array.getLength(dst));
        final int pos = position();
        final int lim = limit();
        assert (pos <= lim);
        final int rem = (pos <= lim ? lim - pos : 0);
        if (length > rem)
            throw new BufferUnderflowException();
        getBulkInternal(pos, dst, offset, length);
        position(pos + length);
    }

    @ForceInline
    final void getBulkImpl(int index, A dst, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, Array.getLength(dst));
        getBulkInternal(index, dst, offset, length);
    }

    @ForceInline
    private final void getBulkInternal(int index, A dst, int offset, int length) {
        int scaleFactor = scaleFactor();
        long dstOffset = arrayBaseOffset() + ((long)offset << scaleFactor);
        try {
            if (scaleFactor > 0 && bigEndian() != NORD_IS_BIG)
                SCOPED_MEMORY_ACCESS.copySwapMemory(scope(), null,
                        base(), ix(index),
                        dst, dstOffset,
                        (long)length << scaleFactor,
                        (long)1 << scaleFactor);
            else
                SCOPED_MEMORY_ACCESS.copyMemory(scope(), null,
                        base(), ix(index),
                        dst, dstOffset,
                        (long)length << scaleFactor);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putBulkImpl(A src, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, Array.getLength(src));
        if (readOnly)
            throw new ReadOnlyBufferException();
        final int pos = position();
        final int lim = limit();
        assert (pos <= lim);
        final int rem = (pos <= lim ? lim - pos : 0);
        if (length > rem)
            throw new BufferOverflowException();
        putBulkInternal(pos, src, offset, length);
        position(pos + length);
    }

    @ForceInline
    final void putBulkImpl(int index, A src, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, Array.getLength(src));
        if (readOnly)
            throw new ReadOnlyBufferException();
        putBulkInternal(index, src, offset, length);
    }

    @ForceInline
    private final void putBulkInternal(int index, A src, int offset, int length) {
        int scaleFactor = scaleFactor();
        long srcOffset = arrayBaseOffset() + ((long)offset << scaleFactor);
        try {
            if (scaleFactor > 0 && bigEndian() != NORD_IS_BIG)
                SCOPED_MEMORY_ACCESS.copySwapMemory(null, scope(),
                        src, srcOffset,
                        base(), ix(index),
                        (long)length << scaleFactor,
                        (long)1 << scaleFactor);
            else
                SCOPED_MEMORY_ACCESS.copyMemory(null, scope(),
                        src, srcOffset,
                        base(), ix(index),
                        (long)length << scaleFactor);
        } finally {
            Reference.reachabilityFence(this);
        }
    }

    @ForceInline
    final void putBulkImpl(B src) {
        if (src == this)
            throw createSameBufferException();
        if (readOnly)
            throw new ReadOnlyBufferException();
        final int srcPos = src.position();
        final int srcLim= src.limit();
        final int dstPos = position();
        final int dstLim = limit();
        final int srcLength    = (srcPos <= srcLim ? srcLim - srcPos : 0);
        final int dstRemaining = (dstPos <= dstLim ? dstLim - dstPos : 0);
        if (srcLength > dstRemaining)
            throw new BufferOverflowException();
        putBulkInternal(dstPos, src, srcPos, srcLength);
        position(dstPos + srcLength);
        src.position(srcPos + srcLength);
    }

    @ForceInline
    final void putBulkImpl(int index, B src, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, src.limit());
        if (readOnly)
            throw new ReadOnlyBufferException();
        putBulkInternal(index, src, offset, length);
    }

    @ForceInline
    private final void putBulkInternal(int index, B src, int srcPos, int length) {
        int scaleFactor = scaleFactor();
        try {
            if (scaleFactor > 0 && bigEndian() != src.bigEndian())
                SCOPED_MEMORY_ACCESS.copySwapMemory(src.scope(), scope(),
                        src.base(), src.ix(srcPos),
                        base(), ix(index),
                        (long) length << scaleFactor,
                        (long) 1 << scaleFactor);
            else
                SCOPED_MEMORY_ACCESS.copyMemory(src.scope(), scope(),
                        src.base(), src.ix(srcPos),
                        base(), ix(index),
                        (long) length << scaleFactor);
        } finally {
            Reference.reachabilityFence(src);
            Reference.reachabilityFence(this);
        }
    }

    // ---

    @ForceInline
    public boolean hasArray() {
        return hb != null && hb.getClass() == carrier() && !readOnly;
    }

    @ForceInline
    public A array() {
        if (hb == null || hb.getClass() != carrier())
            throw new UnsupportedOperationException();
        if (readOnly)
            throw new ReadOnlyBufferException();
        @SuppressWarnings("unchecked") A a = (A)hb;
        return a;
    }

    @ForceInline
    public int arrayOffset() {
        if (hb == null || hb.getClass() != carrier())
            throw new UnsupportedOperationException();
        if (readOnly)
            throw new ReadOnlyBufferException();
        assert (address & 0xFFFFFFFF00000000L) == 0;
        return ((int)address - UNSAFE.arrayBaseOffset(carrier())) >> scaleFactor();
    }

    @ForceInline
    public B compact() {
        if (readOnly)
            throw new ReadOnlyBufferException();
        int pos = position();
        int lim = limit();
        int rem = (pos <= lim ? lim - pos : 0);
        try {
            SCOPED_MEMORY_ACCESS.copyMemory(scope(), scope(), base(), ix(pos), base(), ix(0), (long)rem << scaleFactor());
        } finally {
            Reference.reachabilityFence(this);
        }
        position(rem);
        limit(capacity());
        discardMark();
        @SuppressWarnings("unchecked") B b = (B)this;
        return b;
    }

    @Override
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getClass().getName());
        sb.append("[pos=");
        sb.append(position());
        sb.append(" lim=");
        sb.append(limit());
        sb.append(" cap=");
        sb.append(capacity());
        sb.append("]");
        return sb.toString();
    }

    /**
     * A functional interface that features a method to be used to compute the
     * hash of a buffer element at a given position.
     */
    interface BufferHashOp<B extends AbstractBufferImpl<B, A>, A> {
        int hash(B b, int position);
    }

    @SuppressWarnings("unchecked")
    final int hashCodeImpl(BufferHashOp<B, A> bufferHashOp) {
        int h = 1;
        int p = position();
        for (int i = limit() - 1; i >= p; i--)
            h = 31 * h + bufferHashOp.hash((B)this, i);
        return h;
    }

    /**
     * A functional interface that features a method to be used to compute the
     * mismatch between two buffer regions, starting at given positions and
     * with given length.
     */
    interface BufferMismatchOp<B extends AbstractBufferImpl<B, A>, A> {
        int mismatch(B aBuf, int aPos, B bBuf, int bPos, int length);
    }

    @SuppressWarnings("unchecked")
    final boolean equalsImpl(Object ob, BufferMismatchOp<B, A> abBufferMismatchOp) {
        if (this == ob)
            return true;
        if (!(ob instanceof AbstractBufferImpl that) || that.carrier() != carrier())
            return false;
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        if (thisRem < 0 || thisRem != thatRem)
            return false;
        return abBufferMismatchOp.mismatch((B)this, thisPos, (B)that, thatPos, thisRem) < 0;
    }

    @SuppressWarnings("unchecked")
    final int mismatchImpl(B that, BufferMismatchOp<B, A> abBufferMismatchOp) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int r = abBufferMismatchOp.mismatch((B)this, thisPos, that, thatPos, length);
        return (r == -1 && thisRem != thatRem) ? length : r;
    }

    /**
     * A functional interface that features a method to be used to compare two
     * buffer elements at given positions.
     */
    interface BufferComparatorOp<B extends AbstractBufferImpl<B, A>, A> {
        int compare(B aBuf, int aPos, B bBuf, int bPos);
    }

    @SuppressWarnings("unchecked")
    final int compareToImpl(B that,
                            BufferMismatchOp<B, A> abBufferMismatchOp,
                            BufferComparatorOp<B, A> abBufferCompareOp) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int i = abBufferMismatchOp.mismatch((B)this, thisPos, that, thatPos, length);
        if (i >= 0) {
            return abBufferCompareOp.compare((B)this, thisPos + i, that, thatPos + i);
        }
        return thisRem - thatRem;
    }

    // direct buffer utils

    final Object attachmentValueOrThis() {
        return attachment != null ? attachment : this;
    }
}
