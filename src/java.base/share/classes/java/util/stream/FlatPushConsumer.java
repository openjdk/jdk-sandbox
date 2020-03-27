/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.util.stream;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

final class FlatPushConsumer<T> implements Consumer<T>, AutoCloseable {

    SpinedBuffer<T> buffer;

    FlatPushConsumer(SpinedBuffer<T> buffer) {
        this.buffer = buffer;
    }

    @Override
    public void accept(T t) {
        buffer.accept(t);
    }

    // Dereference to ensure buffer is inaccessible after use
    @Override
    public void close() {
        buffer = null;
    }

    static class OfInt implements IntConsumer, AutoCloseable {
        SpinedBuffer.OfInt intBuffer;

        OfInt(SpinedBuffer.OfInt intBuffer) {
            this.intBuffer = intBuffer;
        }

        @Override
        public void accept(int i) {
            intBuffer.accept(i);
        }

        @Override
        public void close() {
            intBuffer = null;
        }
    }

    static class OfDouble implements DoubleConsumer, AutoCloseable {
        SpinedBuffer.OfDouble doubleBuffer;

        OfDouble(SpinedBuffer.OfDouble doubleBuffer) {
            this.doubleBuffer = doubleBuffer;
        }

        @Override
        public void accept(double d) {
            doubleBuffer.accept(d);
        }

        @Override
        public void close() {
            doubleBuffer = null;
        }
    }

    static class OfLong implements LongConsumer, AutoCloseable {
        SpinedBuffer.OfLong longBuffer;

        OfLong(SpinedBuffer.OfLong longBuffer) {
            this.longBuffer = longBuffer;
        }

        @Override
        public void accept(long l) {
            longBuffer.accept(l);
        }

        @Override
        public void close() {
            longBuffer = null;
        }
    }
}
