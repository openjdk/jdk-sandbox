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
import java.time.Duration;
import java.util.Optional;

/**
 * An IOUring with blocking semantics. Two implementations currently provided.
 *
 * A single threaded ring performs all work in the calling thread. Therefore, only
 * one operation can be in flight at a time
 *
 * A multi threaded ring allows multiple threads to use one ring. This currently
 * uses two background threads to manage the Submission queue and completion queue
 * respectively.
 *
 * These implementations are mainly provided for testing/experimental purposes.
 * It's likely that real IOUring use cases will be based on the lower level
 * asynchronous API
 *
 * {@link IOURingSocket} and {@link IOURingFile} are example APIs built on
 * the blocking API.
 */
public abstract class BlockingRing {

    protected final IOUring ring;

    public BlockingRing(IOUring ring) {
        this.ring = ring;
    }
    public static BlockingRing getMultiThreadedRing(IOUring r) throws IOException {
        return new BlockingRingImpl(r);
    }

    public static BlockingRing getSingleThreadedRing(IOUring r) throws IOException {
        return new SimpleRingImpl(r);
    }

    public IOUring ring() {
        return ring;
    }

    public abstract void close(boolean orderly) throws IOException;
    public abstract Cqe blockingSubmit(Sqe request) throws InterruptedException, IOException;

    public abstract Cqe blockingSubmit(Sqe request, Optional<Duration> duration) throws InterruptedException, IOException;
}
