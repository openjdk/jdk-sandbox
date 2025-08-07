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
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_MSG_RING;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_POLL_ADD;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_POLL_REMOVE;

/**
 * Provides an API to io_ring. Callers should use synchronzation when submitting
 * or polling from more than one thread.
 */
public class IoUring implements Closeable {

    private static final int SQ_ENTRIES = 5;

    private final IOUringImpl impl;

    private IoUring() throws IOException {
        this.impl = new IOUringImpl(SQ_ENTRIES);
    }

    /* SQE/CQE user_data
     * fd is encoded in lower 32 bits
     * Some higher bits used for flags
     *
     * Flags signifying the operation type */
    private static final long OP_CLOSE = 0x4L << 32;
    /**
     * Closes this IoUring. If a thread is blocked in
     * poll() it will be unblocked and poll() will
     * return -1.
     */
    @Override
    public void close() {
        // Send a message to wakeup Poller
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_MSG_RING())
                .fd(impl.ringFd()) // send msg to self
                .user_data(0L)
                .off(OP_CLOSE)     // delivered as Cqe user_data
                .len(-1);          // delivered as Cqe res
        try {
            impl.submit(sqe);
            enterNSubmissions(1);
            impl.close();
        } catch (IOException e) {}
    }

    // Event values from Linux headers
    public static final int POLLIN = 1;
    public static final int POLLOUT = 4;

    public static IoUring create() throws IOException {
        return new IoUring();
    }

    /**
     * Adds the given file descriptor to the iouring poller.
     * Submission errors may be reported immediately.
     */
    public void poll_add(int sock, int events, long data) throws IOException {
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_POLL_ADD())
                .fd(sock)
                .flags(IOSQE_IO_LINK())
                .user_data(data)
                .poll_events(events);
        impl.submit(sqe);
        enterNSubmissions(1);
    }

    private void enterNSubmissions(int n) throws IOException {
        int ret = impl.enter(n, 0, 0);
        if (ret < 1) {
            throw new IOException(
                String.format("enterNSubmissions error %d", ret));
        }
    }

    /**
     * Removes the given file descriptor to the iouring poller.
     * Submission errors may be reported immediately.
     */
    public void poll_remove(int sock, long data) throws IOException {
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_POLL_REMOVE())
                .flags(IOSQE_IO_LINK())
                .fd(sock)
                .user_data(data);
        impl.submit(sqe);
        enterNSubmissions(1);
    }

    /**
     * Blocks until some file descriptor is ready or an error
     * has occurred.
     *
     * @param polled callback informed with the user data
     *               and the error (if any) relating to it
     *
     * @return the number of events signalled. Will be {@code 1}
     *         on success. {@code -1} signifies that the ring has
     *         been closed.
     *
     * @throws IOException Non fd specific errors are signaled
     *         via exception
     */
    public int poll(BiConsumer<Long, Integer> polled) throws IOException {
        return poll(polled, true);
    }

    /**
     * Blocks until some file descriptor is ready or an error
     * has occurred
     *
     * @param polled callback informed with the user data
     *               and the error if one occurred
     *
     * @param block true if call should block waiting for event
     *              {@code false} if call should not block
     *
     * @return the number of events signalled. Will be either
     *         {@code 1} or {@code 0} if block is {@code false}.
     *         {@code -1} signifies that the ring has
     *         been closed.
     *
     * @throws IOException Non fd specific errors are signaled
     *         via exception
     */
    public int poll(BiConsumer<Long, Integer> polled, boolean block) throws IOException {
        int r = pollCompletionQueue(polled);
        if (r == 1)
            return 1;
        if (!block)
            return 0;
        do {
            // the blocking op may have to be repeated in case
            // where a deregister result is returned which we ignore
            impl.enter(0, 1, 0);
        } while ((r = pollCompletionQueue(polled)) == 0);
        return r;
    }

    /**
     * Poll the Completion Queue and returning 0 or 1
     * if an event found and the completion consumer was called
     */
    private int pollCompletionQueue(BiConsumer<Long, Integer> polled) {
        while (!impl.cqempty()) {
            Cqe cqe = impl.pollCompletion();
            assert cqe != null;
            int res = cqe.res();
            long data = cqe.user_data();
            if (res == -1 && ((udata & OP_CLOSE) > 0)) {
                return -1;
            } else {
                int fd = getFdFromUserData(udata);
                assert fd >= 0;
                polled.accept(fd, res);
                return 1;
            }
        }
        return 0;
    }
}
