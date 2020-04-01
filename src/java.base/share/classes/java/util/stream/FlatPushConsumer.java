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

abstract class FlatPushConsumer<T extends AbstractSpinedBuffer>
        implements AutoCloseable {
    T buffer;

    FlatPushConsumer(T buffer) {
        this.buffer = buffer;
    }

    // Dereference to ensure buffer is inaccessible after use
    @Override
    public void close() {
        buffer = null;
    }

    static class OfRef<U> extends FlatPushConsumer<SpinedBuffer<U>>
            implements Consumer<U> {

        OfRef(SpinedBuffer<U> buffer) {
            super(buffer);
        }

        @Override
        public void accept(U u) {
            buffer.accept(u);
        }
    }

    static class OfDouble extends FlatPushConsumer<SpinedBuffer.OfDouble>
            implements DoubleConsumer {

        OfDouble(SpinedBuffer.OfDouble buffer) {
            super(buffer);
        }

        @Override
        public void accept(double d) {
            buffer.accept(d);
        }
    }

    static class OfInt extends FlatPushConsumer<SpinedBuffer.OfInt>
            implements IntConsumer {

        OfInt(SpinedBuffer.OfInt buffer) {
            super(buffer);
        }

        @Override
        public void accept(int i) {
            buffer.accept(i);
        }
    }

    static class OfLong extends FlatPushConsumer<SpinedBuffer.OfLong>
            implements LongConsumer {

        OfLong(SpinedBuffer.OfLong buffer) {
            super(buffer);
        }

        @Override
        public void accept(long l) {
            buffer.accept(l);
        }
    }
}
