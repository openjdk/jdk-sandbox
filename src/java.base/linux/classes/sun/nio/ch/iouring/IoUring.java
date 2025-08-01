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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_TIMEOUT;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_POLL_ADD;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_POLL_REMOVE;

/**
 * Submission operations are synchronized (but non-blocking) so
 * they can be called from multiple threads
 */
public class IoUring implements Closeable {

    /**
     * poll event: There is data to read.
     */
    public static final int POLLIN = 0x0001;
    /**
     * poll event: There is some exceptional condition on the file descriptor.
     */
    public static final int POLLPRI = 0x0002;
    /**
     * poll event: Writing is now possible,
     */
    public static final int POLLOUT = 0x0004;
    /**
     * poll event: Error condition
     */
    public static final int POLLERR = 0x0008;
    /**
     * poll event: Hang up 
     */
    public static final int POLLHUP = 0x0010;
    /**
     * poll event: Invalid request
     */
    public static final int POLLNVAL = 0x0020;
    
    /*
     * fd is encoded in upper 32 bits
     * Lower 32 bits used for requestID
     * Special fd value which denotes a timer
     */
    private static final int TIMER_FD = -1;

    private static final int SQ_ENTRIES = 5;
 
    // Linux errno values
    private static final int ETIME = 62;
    private static final int ECANCELED = 125;

    private final int event;
    private final IOUringImpl impl;
    private final ConcurrentHashMap<Long,CompletableFuture<Integer>> map;
    private final AtomicInteger requestID = new AtomicInteger(1);

    private IoUring(int event) throws IOException, InterruptedException {
        this.event = event;
        this.impl = new IOUringImpl(SQ_ENTRIES);
        this.map = new ConcurrentHashMap<>();
    }

    private long getUserData(int fd) {
        long v = (long)fd << 32;
        v |= requestID.getAndIncrement();
        return v;
    }
        
    private int getFdFromUserData(long udata) {
        long v = udata >> 32;
        return (int)v;
    }

    @Override
    public void close() {
        try {
            impl.close();
        } catch (IOException e) {}
    }

    public static IoUring create(int event) 
                    throws IOException, InterruptedException {
        return new IoUring(event);
    }

    /**
     * Adds the given file descriptor to the iouring poller.
     * Submission errors may be reported immediately.
     *
     * @return a CompletableFuture<Integer> for the operation result
     *         The returned integer is the file descriptor of the
     *         polled socket
     */
    public synchronized CompletableFuture<Integer> poll_add(int sock) {
        var cf = new CompletableFuture<Integer>();
        long udata = getUserData(sock);
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_POLL_ADD())
                .fd(sock)
                .user_data(udata)
                .poll_events(event);
        try {
            impl.submit(sqe);
            int ret = impl.enter(1, 0, 0);
            if (ret < 1) {
                cf.completeExceptionally(
                   new IOException(
                      String.format("poll_add error %d", ret)));
            }
        } catch (IOException e) {
            cf.completeExceptionally(e);
        }
        map.put(udata,cf);
        return cf;
    }
        

    /**
     * Removes the given file descriptor to the iouring poller.
     * Submission errors may be reported immediately.
     *
     * @return a CompletableFuture<Integer> for the operation result
     *         The returned integer is the file descriptor of the
     *         cancelled poll
     */
    public synchronized CompletableFuture<Integer> poll_cancel(int sock) throws 
                     IOException, InterruptedException 
    {
        var cf = new CompletableFuture<Integer>();
        long udata = getUserData(sock);
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_POLL_REMOVE())
                .fd(sock)
                .user_data(udata);
        impl.submit(sqe);
        int ret = impl.enter(1, 0, 0);
        if (ret < 1) {
            cf.completeExceptionally(
                new IOException(
                   String.format("poll_cancel failed: on submission %d", ret)));
        }
        map.put(udata,cf);
        return cf;
    }

    /**
     * Blocks until some file descriptor is ready or an error
     * has occurred.
     *
     * @return the number of added CompletableFutures that were completed
     *         normally or exceptionally
     */
    public int poll() throws IOException, InterruptedException {
        return poll(null);
    }

    /**
     * Blocks until some file descriptor is ready or an error
     * has occurred or the given timeout has expired.
     * For any file descriptors that are ready, the CompletableFuture
     * is completed normally or exceptionally depending on the
     * Cqe result.
     * <p>
     * If this operation timesout, currently registered operations
     * remain active and are not cancelled.
     *
     * @return the number of added CompletableFutures that were completed
     *         normally or exceptionally or {@code -1} on timeout.
     */
    public int poll(Duration duration) throws IOException {
        Sqe timeout = null;
        int ret = 0;

        if (duration != null) {
            // must synchronize with other submitters (non-blocking)
            synchronized (this) {
                // timeout will complete if at least one fd is ready
                // or if the timer timesout. Difference seen in Cqe result
                timeout = impl.getTimeoutSqe(duration, IORING_OP_TIMEOUT(), 1);
                timeout.user_data(getUserData(TIMER_FD));
                impl.submit(timeout);
                ret = impl.enter(1, 0, 0); // submit, no-wait
                throwIOExceptionOnErr(ret);
            }
        }
        ret = impl.enter(0, 1, 0);
        throwIOExceptionOnErr(ret);
        Cqe cqe;
        int result = 0;
        int i=0;
        while(!impl.cqempty()) {
            cqe = impl.pollCompletion();
            i++;
            assert cqe != null;
            int res = cqe.res();
            long udata = cqe.user_data();
            int fd = getFdFromUserData(udata);
            
            if (fd == TIMER_FD) {
                if (res == 0) {
                    // Normal event completion
                    continue;
                } else if (res == -ETIME) {
                    result = -1;
                } else if (res == -ECANCELED) {
                    // TODO: what do we do?
                }
            } else {
                // Timeout should not occur if other completions available
                assert result != -1; 
                CompletableFuture<Integer> cf = map.remove(udata);
                assert cf != null;
                if (res < 0) {
                    var err = String.format("operation failed: %d", -res);
                    cf.completeExceptionally(new IOException(err));
                    result++;
                } else {
                    cf.complete(fd);
                    result++;
                }
            }
        }
        assert result != 0;
        return result;
    }

    private static void throwIOExceptionOnErr(int ret) throws IOException {
        if (ret < 0)
            throw new IOException("Error: " + ret);
    }
}


