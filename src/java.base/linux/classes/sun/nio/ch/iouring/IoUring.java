/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.Closeable;
import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.concurrent.atomic.AtomicInteger;
import static sun.nio.ch.iouring.foreign.iouring_h.IOSQE_IO_LINK;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_TIMEOUT;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_POLL_ADD;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_POLL_REMOVE;

/**
 * Submission operations are synchronized (but non-blocking) so
 * they can be called from multiple threads
 */
public class IoUring implements Closeable {

    /* SQE/CQE user_data
     * fd is encoded in lower 32 bits
     * Some higher bits used for flags
     */
    private static final int TIMER_FD = -1;

    private static final int SQ_ENTRIES = 5;

    private final int event;
    private final IOUringImpl impl;

    private IoUring(int event) throws IOException {
        this.event = event;
        this.impl = new IOUringImpl(SQ_ENTRIES);
    }

    /* Flags signifying the operation type */
    private static final long OP_ADD = 0x1L << 32;
    private static final long OP_DEL = 0x2L << 32;

    private long getUserData(int fd) {
        return getUserData(fd, 0L);
    }

    private long getUserData(int fd, long op) {
        long v = op | (long)fd;
        return v;
    }

    private int getFdFromUserData(long udata) {
        return (int)udata;
    }

    @Override
    public void close() {
        try {
            impl.close();
        } catch (IOException e) {}
    }

    // Event values from Linux headers
    public static final int POLLIN = 1;
    public static final int POLLOUT = 4;

    public static IoUring create(int event)
                    throws IOException {
        return new IoUring(event);
    }

    /**
     * Adds the given file descriptor to the iouring poller.
     * Submission errors may be reported immediately.
     */
    public synchronized void poll_add(int sock) throws IOException {
        long udata = getUserData(sock, OP_ADD);
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_POLL_ADD())
                .fd(sock)
                .flags(IOSQE_IO_LINK())
                .user_data(udata)
                .poll_events(event);
        impl.submit(sqe);
        int ret = impl.enter(1, 0, 0);
        if (ret < 1) {
            throw new IOException(
                  String.format("poll_add error %d", ret));
        }
    }


    /**
     * Removes the given file descriptor to the iouring poller.
     * Submission errors may be reported immediately.
     */
    public synchronized void poll_cancel(int sock) 
        throws IOException
    {
        long udata = getUserData(sock, OP_DEL);
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_POLL_REMOVE())
                .flags(IOSQE_IO_LINK())
                .fd(sock)
                .user_data(udata);
        impl.submit(sqe);
        int ret = impl.enter(1, 0, 0);
        if (ret < 1) {
            throw new IOException(
                String.format("poll_cancel failed: on submission %d", ret));
        }
    }

    /**
     * Blocks until some file descriptor is ready or an error
     * has occurred.
     *
     * @param polled callback informed which fd is signalled
     *               and the error (if any) relating to it
     * @return the number of events signalled. Will be {@code 1} 
     *         on success. 
     * 
     * @throws IOException Non fd specific errors are signaled 
     *         via exception
     */
    public int poll(BiConsumer<Integer,Integer> polled) throws IOException {
        return poll(polled, true);
    }

    /**
     * Blocks until some file descriptor is ready or an error
     * has occurred
     *
     * @param polled callback informed which/if any fd is signalled
     *               and the error if one occurred on that fd
     *
     * @param block true if call should block waiting for event
     *              {@code false} if call should not block
     *
     * @return the number of events signalled. Will be either
     *         {@code 1} or {@code 0} if block is {@code false}
     *
     * @throws IOException Non fd specific errors are signaled 
     *         via exception
     */
    public int poll(BiConsumer<Integer,Integer> polled, boolean block) 
        throws IOException {

        int r = pollCompletionQueue(polled);
        if (r == 1)
            return 1;
        if (!block)
            return 0;

        do {
            // the blocking op may have to be repeated in case
            // where a deregister result is returned which we ignore
            r = impl.enter(0, 1, 0);
            if (r < 0) {
                String msg = String.format("poll: error %d", r);
                throw new IOException(msg);
            }
        } while ((r = pollCompletionQueue(polled)) == 0);
        return r;
    }

    /**
     * Poll the Completion Queue and returning 0 or 1
     * if an event found and the completion consumer was called
     */
    private int pollCompletionQueue(BiConsumer<Integer,Integer> polled) {
        while (!impl.cqempty()) {
            Cqe cqe = impl.pollCompletion();
            assert cqe != null;
            int res = cqe.res();
            long udata = cqe.user_data();
            if (res == 0 && ((udata & OP_DEL) > 0)) {
                // Don't report successful deregister
                // which implies a failed dereg does
                continue;
            }
            int fd = getFdFromUserData(udata);
            assert fd >= 0;
            polled.accept(fd, res);
            return 1;
        }
        return 0;
    }
}
