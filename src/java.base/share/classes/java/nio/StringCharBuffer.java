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

import java.util.Objects;

// ## If the sequence is a string, use reflection to share its array

final class StringCharBuffer                                  // package-private
    extends CharBuffer
{
    private final CharSequence str;
    private final int offset;

    StringCharBuffer(CharSequence s, int start, int end) { // package-private
        super(0L,
              null,
              -1, start, end, s.length(),
              true,        // read-only
              NORD_IS_BIG, // true if big-endian
              null,        // attachment
              null);       // segment
        int n = s.length();
        Objects.checkFromToIndex(start, end, n);
        str = s;
        offset = 0;
    }

    private StringCharBuffer(CharSequence s,
                             int mark,
                             int pos,
                             int limit,
                             int cap,
                             int offset) {
        super(0L, null,
              mark, pos, limit, cap,
              true,        // read-only
              NORD_IS_BIG, // true if big-endian
              null,        // attachment
              null);       // segment
        str = s;
        this.offset = offset;
    }

    @Override
    public CharBuffer slice() {
        int pos = this.position();
        int lim = this.limit();
        int rem = (pos <= lim ? lim - pos : 0);
        return new StringCharBuffer(str,
                                    -1,
                                    0,
                                    rem,
                                    rem,
                                    offset + pos);
    }

    @Override
    public CharBuffer slice(int index, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        return new StringCharBuffer(str,
                                    -1,
                                    0,
                                    length,
                                    length,
                                    offset + index);
    }

    @Override
    public CharBuffer subSequence(int start, int end) {
        try {
            int pos = position();
            return new StringCharBuffer(str,
                                        -1,
                                        pos + checkGetIndex(start, pos),
                                        pos + checkGetIndex(end, pos),
                                        capacity(),
                                        offset);
        } catch (IllegalArgumentException x) {
            throw new IndexOutOfBoundsException();
        }
    }

    @Override
    public CharBuffer duplicate() {
        return new StringCharBuffer(str, markValue(),
                                    position(), limit(), capacity(), offset);
    }

    @Override
    public CharBuffer asReadOnlyBuffer() {
        return duplicate();
    }

    @Override
    public char get() {
        return str.charAt(nextGetIndex() + offset);
    }

    @Override
    public char get(int index) {
        return str.charAt(checkGetIndex(index) + offset);
    }

    char getUnchecked(int index) {
        return str.charAt(index + offset);
    }

    @Override
    public CharBuffer get(char[] dst, int offset, int length) {
        Objects.checkFromIndexSize(offset, length, dst.length);
        final int pos = position();
        final int lim = limit();
        final int rem = (pos <= lim ? lim - pos : 0);
        if (length > rem)
            throw new BufferUnderflowException();
        getBulkInternal(pos, dst, offset, length);
        return this;
    }

    @Override
    public CharBuffer get(int index, char[] dst, int offset, int length) {
        Objects.checkFromIndexSize(index, length, limit());
        Objects.checkFromIndexSize(offset, length, dst.length);
        getBulkInternal(index, dst, offset, length);
        return this;
    }

    private void getBulkInternal(int index, char[] dst, int offset, int length) {
        for (int i = 0; i < length; i++)
            dst[offset+i] = getUnchecked(index+i);
    }

    @Override
    public CharBuffer compact() {
        throw new ReadOnlyBufferException();
    }

    @Override
    public String toString() {
        final int pos = position();
        final int lim = limit();
        return str.subSequence(pos + offset, lim + offset).toString();
    }

    @Override
    public boolean hasArray() {
        return false;
    }

    @Override
    public char[] array() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int arrayOffset() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isDirect() {
        return false;
    }

    @Override
    ByteOrder charRegionOrder() {
        return null;
    }

    @Override
    public boolean equals(Object ob) {
        if (this == ob)
            return true;
        if (!(ob instanceof CharBuffer))
            return false;
        CharBuffer that = (CharBuffer)ob;
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        if (thisRem < 0 || thisRem != thatRem)
            return false;
        return BufferMismatch.mismatch(this, thisPos,
                                       that, thatPos,
                                       thisRem) < 0;
    }

    @Override
    public int compareTo(CharBuffer that) {
        int thisPos = this.position();
        int thisRem = this.limit() - thisPos;
        int thatPos = that.position();
        int thatRem = that.limit() - thatPos;
        int length = Math.min(thisRem, thatRem);
        if (length < 0)
            return -1;
        int i = BufferMismatch.mismatch(this, thisPos,
                                        that, thatPos,
                                        length);
        if (i >= 0) {
            return Character.compare(this.get(thisPos + i), that.get(thatPos + i));
        }
        return thisRem - thatRem;
    }
}
