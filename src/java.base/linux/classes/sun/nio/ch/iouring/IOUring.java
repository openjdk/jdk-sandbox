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

import sun.nio.ch.iouring.foreign.*;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.time.Duration;

import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static sun.nio.ch.iouring.Util.strerror;
import static sun.nio.ch.iouring.Util.locateHandleFromLib;
import static sun.nio.ch.iouring.Util.locateStdHandle;
import static sun.nio.ch.iouring.foreign.iouring_h.*;

/**
 * Low level interface to a Linux io_uring. Each IOUring needs to be owned
 * and used by one thread. It provides an asynchronous interface.
 * Requests are submitted through the {@link #submit(Sqe)} method. Completion
 * events can be awaited by calling {@link #enter(int, int, int)}. Completions
 * represented by {@link Cqe} are then obtained by calling {@link #pollCompletion()}.
 * Completions are linked to submissions by the {@link Cqe#user_data()} field of
 * the {@code Cqe} which contains the same 64-bit (long) value that was returned
 * from {@link #submit(Sqe)}.
 * <p>
 * A higher level interface {@link BlockingRing} provides a traditional
 * blocking API (for experiments). There are two implementations of that interface:
 * <ol>
 * <li>a simple single threaded impl</li>
 * <li>a multi thread impl where requests are submitted through a blocking queue and
 *    the ring itself is managed by two background threads.</li>
 * </ol>
 * <p>
 * Some IOUring operations work with kernel registered direct ByteBuffers. When creating
 * an IOUring instance, a number of these buffers can be created in a pool.
 * Registered buffers are not used with regular IOUring read/write operations.
 */
public class IOUring {
    private static Arena arena = Arena.ofAuto();

    private static boolean TRACE = System
            .getProperty("sun.nio.ch.iouring.trace", "false")
            .equalsIgnoreCase("true");
    private final SubmissionQueue sq;
    private final CompletionQueue cq;
    private final int fd;               // The ringfd
    private int epollfd = -1;           // The epoll(7) if set
    private static final int INT_SIZE = (int)ValueLayout.JAVA_INT.byteSize();

    private static long lowestRequestID = 0x1000000;
    private long nextRequestID = lowestRequestID;
    private final Arena autoArena = Arena.ofAuto();

    private final KMappedBuffers mappedBuffers;

    /**
     * Creates an IOURing and initializes the ring structures. {@code entries} (or the next higher power of 2)
     * is the size of the Submission Queue. Currently, the completion queue returned
     * will be double the size of the Submission queue.
     * <p>
     * This constructor invokes {@code IOURing(entries, 0, -1)}
     * </p>
     */
    public IOUring(int entries) throws IOException {
        this(entries, 0, -1);
    }

    /**
     * Creates an IOURing initializes the ring structures and allocates a number of
     * direct {@link ByteBuffer}s which are additionally mapped into the kernel
     * address space.
     *
     * @param entries requested size of submission queue
     * @param nmappedBuffers number of mapped direct ByteBuffers to create
     * @param mappedBufsize
     * @throws IOException
     */
    public IOUring(int entries, int nmappedBuffers, int mappedBufsize) throws IOException {
        MemorySegment params_seg = getSegmentFor(io_uring_params.$LAYOUT());
        // call setup
        fd = io_uring_setup(entries, params_seg);
        if (fd < 0) {
            throw new IOException(errorString(fd));
        }
        mappedBuffers = new KMappedBuffers(nmappedBuffers, mappedBufsize);
        if (nmappedBuffers > 0) {
            mappedBuffers.register(fd);
        }
        // Offsets segments
        MemorySegment cq_off_seg = io_uring_params.cq_off(params_seg);
        MemorySegment sq_off_seg = io_uring_params.sq_off(params_seg);

        // Offsets to cqe array and the sqe index array
        int cq_off_cqes = io_cqring_offsets.cqes(cq_off_seg);
        int sq_off_array = io_sqring_offsets.array(sq_off_seg);

        // Number of entries in each Q
        int sq_entries = io_uring_params.sq_entries(params_seg);
        int cq_entries = io_uring_params.cq_entries(params_seg);

        int sq_size = sq_off_array + sq_entries * INT_SIZE;
        int cq_size = cq_off_cqes + cq_entries * (int)io_uring_cqe.sizeof();

        boolean singleMmap = (io_uring_params.features(params_seg)
                & IORING_FEAT_SINGLE_MMAP()) != 0;

        if (singleMmap) {
            if (cq_size > sq_size)
                sq_size = cq_size;
            cq_size = sq_size;
        }
        var sqe_seg = mmap(sq_size, fd, IORING_OFF_SQ_RING());

        MemorySegment cqes_seg;
        if (singleMmap) {
            cqes_seg = sqe_seg;
        } else {
            cqes_seg = mmap(cq_size, fd, IORING_OFF_CQ_RING());
        }

        // Masks
        int sq_mask = sqe_seg.get(ValueLayout.JAVA_INT, io_sqring_offsets.ring_mask(sq_off_seg));
        int cq_mask = cqes_seg.get(ValueLayout.JAVA_INT, io_cqring_offsets.ring_mask(cq_off_seg));

        var sqes = mmap(sq_entries * io_uring_sqe.sizeof(), fd, IORING_OFF_SQES());

        cq = new CompletionQueue(cqes_seg.asSlice(cq_off_cqes),
                cqes_seg.asSlice(io_cqring_offsets.head(cq_off_seg)),
                cqes_seg.asSlice(io_cqring_offsets.tail(cq_off_seg)),
                cq_mask);

        sq = new SubmissionQueue(sqe_seg.asSlice(sq_off_array),
                cqes_seg.asSlice(io_cqring_offsets.head(sq_off_seg)),
                cqes_seg.asSlice(io_cqring_offsets.tail(sq_off_seg)),
                sq_mask,
                sqes);
    }


    public void close() throws IOException {
        int ret;
        SystemCallContext ctx = new SystemCallContext();
        try {
            ret = (int)close_fn.invokeExact(ctx.errnoCaptureSegment(), ringFd());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        ctx.throwIOExceptionOnError(ret);

    }

    private int initEpoll() throws IOException {
        int ret;
        SystemCallContext ctx = new SystemCallContext();
        try {
            ret = (int)epoll_create_fn.invokeExact(ctx.errnoCaptureSegment(), ringFd(), 1);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        ctx.throwIOExceptionOnError(ret);
        return ret;
    }
    /**
     * Asynchronously submits an Sqe to this IOUring. Can be called multiple times
     * before enter().
     * <p>
     * If a timed wait is required then set the {@code IOSQE_IO_LINK()} flag
     * on this request and immediately submit an Sqe returned from
     * {@link #getTimeoutSqe(Duration)} after this one, before calling enter();
     * </p>
     *
     * @param sqe
     * @return user_data of submitted request (request id)
     * @throws IOException if submission q full
     */
    public long submit(Sqe sqe) throws IOException {
        long id = sq.submit(sqe);
        if (TRACE)
            System.out.printf("submit: %s -> %X\n", sqe, id);
        return id;
    }

    private static final int EINTR = -4;

    /**
     * Notifies the kernel of entries on the Submission Q and waits for a number
     * of responses (completion events). If this returns normally with value {@code n > 0},
     * this means that n requests have been accepted by the kernel and that number
     * of free slots have been added to the Submission Q. A normal return
     * also means that the requested number of completion events have been received
     * {@link #pollCompletion()} can be called {@code nreceive} times to obtain
     * the results.
     *
     * @param nsubmit number of requests to submit
     * @param nreceive block until this number of events received
     *
     * @return if return value less than 0 means an error occurred. Otherwise, the number of
     *          Sqes successfully processed.
     */
    public int enter(int nsubmit, int nreceive, int flags) throws IOException {
        if (nreceive > 0) {
            flags |= IORING_ENTER_GETEVENTS();
        }
        int ret;
        while ((ret = io_uring_enter(this.fd, nsubmit, nreceive, flags)) == EINTR)
            ;
        return ret;
    }

    /**
     * Returns the allocated size of the Submission Q. If the requested size
     * was not a power of 2, then the allocated size will be the next highest
     * power of 2.
     *
     * @return
     */
    public int sqsize() {
        return sq.ringSize;
    }

    /**
     * Returns the number of free entries in the Submission Q
     */
    public int sqfree() {
        return sq.nUsed();
    }

    /**
     * Returns whether the completion Q is empty or not.
     *
     * @return
     */
    public boolean cqempty() {
        return cq.nEntries() == 0;
    }

    /**
     * Returns the allocated size of the Completion Q.
     * Currently, double the size of the Submission Q
     *
     * @return
     */
    public int cqsize() {
        return cq.ringSize;
    }

    /**
     * Polls the Completion Queue for results.
     *
     * @return a Cqe if available or {@code null}
     */
    public Cqe pollCompletion() {
        Cqe cqe = cq.pollHead();
        if (TRACE)
            System.out.printf("pollCompletion: -> %s\n", cqe);
        return cqe;
    }

    /**
     * Returns a String description of the given errno value
     *
     * @param errno
     * @return
     */
    public static String strerror(int errno) {
        return Util.strerror(errno);
    }
    private static int io_uring_setup(int entries, MemorySegment params) throws IOException {
        try {
            return (int) setup_fn.invokeExact(entries, params);
        } catch (Throwable t) {
            throw ioexception(t);
        }
    }

    private static int io_uring_enter(int fd, int to_submit, int min_complete,
                                      int flags) throws IOException {
        try {
            return (int) enter_fn.invokeExact(fd, to_submit,
                    min_complete, flags, MemorySegment.NULL);
        } catch (Throwable t) {
            throw ioexception(t);
        }
    }

    static IOException ioexception(Throwable t) {
        if (t instanceof IOException ioe) {
            return ioe;
        } else {
            return new IOException(t);
        }
    }

    int checkAndGetIndexFor(ByteBuffer buffer) {
        return mappedBuffers.checkAndGetIndexForBuffer(buffer);
    }

    /**
     * Returns a mapped direct ByteBuffer or {@code null} if none available. Mapped buffers must
     * be used with some IOUring operations such as {@code IORING_OP_WRITE_FIXED} and
     * {@code IORING_OP_READ_FIXED}. Buffers must be returned after use with
     * {@link #returnRegisteredBuffer(ByteBuffer)}.
     *
     * @return
     */
    public ByteBuffer getRegisteredBuffer() {
        return mappedBuffers.getRegisteredBuffer();
    }

    /**
     * Returns a previously allocated registered buffer.
     *
     * @param buffer
     */
    public void returnRegisteredBuffer(ByteBuffer buffer) {
        mappedBuffers.returnRegisteredBuffer(buffer);
    }

    /**
     * Common capabilities of SubmissionQueue and CompletionQueue
     */
    sealed class QueueImplBase permits SubmissionQueue, CompletionQueue {
        protected final MemorySegment ringSeg;
        private final MemorySegment head, tail;
        protected final int ringMask;
        protected final MemoryLayout ringLayout;
        protected final int ringLayoutSize;
        protected final int ringLayoutAlignment;
        protected final int ringSize;

        // For accessing head and tail as volatile
        protected final VarHandle addrH;

        /**
         *
         * @param ringSeg The mapped segment
         * @param head The head pointer
         * @param tail The tail pointer
         * @param ringMask
         * @param ringLayout
         */
        QueueImplBase(MemorySegment ringSeg, MemorySegment head, MemorySegment tail, int ringMask, MemoryLayout ringLayout) {
            this.ringSeg = ringSeg;
            this.head = head;
            this.tail = tail;
            this.ringMask = ringMask;
            this.ringSize = ringMask + 1;
            this.ringLayout = ringLayout;
            this.ringLayoutSize = (int)ringLayout.byteSize();
            this.ringLayoutAlignment = (int)ringLayout.byteAlignment();
            this.addrH = ValueLayout.JAVA_INT.varHandle();
        }

        int nEntries() {
            int n = Math.abs(getTail(false) - getHead(false));
            return n;
        }

        boolean ringFull() {
            return nEntries() == ringSize;
        }

        int nUsed() {
            return ringSize - nEntries();
        }
        protected int getHead(boolean withAcquire) {
            int val = (int)(withAcquire ? addrH.getAcquire(head, 0) : addrH.get(head, 0));
            return val;
        }

        protected int getTail(boolean withAcquire) {
            int val = (int)(withAcquire ? addrH.getAcquire(tail, 0L) : addrH.get(tail, 0L));
            return val;
        }
        
        // Used by CompletionQueue
        protected void setHead(int val) {
            addrH.setRelease(head, 0L, val);
        }

        // Used by SubmissionQueue
        protected void setTail(int val) {
            addrH.setRelease(tail, 0L, val);
        }
    }

    final class SubmissionQueue extends QueueImplBase {
        final MemorySegment sqes;
        final int n_sqes;
        static final int sqe_layout_size = (int)io_uring_sqe.$LAYOUT().byteSize();
        static final int sqe_alignment = (int)io_uring_sqe.$LAYOUT().byteAlignment();

        SubmissionQueue(MemorySegment ringSeg, MemorySegment head, MemorySegment tail, int mask, MemorySegment sqes) {
            super(ringSeg, head, tail, mask, ValueLayout.JAVA_INT);
            this.sqes = sqes;
            this.n_sqes = (int) (sqes.byteSize() / sqe_layout_size);
        }

        /**
         * Submits an Sqe to Submission Q.
         * @param sqe
         * @return Returns a +ve user_data if succeeds
         * @throws IOException if Q full
         */
        public long submit(Sqe sqe) throws IOException {
            if (ringFull()) {
                throw new IOException("Submission Queue full");
            }

            int tailVal = getTail(false);
            int tailIndex = tailVal & ringMask;

            MemorySegment slot = sqes.asSlice(
                    tailIndex * sqe_layout_size,
                    sqe_layout_size, sqe_alignment).fill((byte)0);
            if (slot == null)
                throw new IOException("Q full"); // shouldn't happen
            long requestID = nextRequestID++;
            // Populate the slot as an io_uring_sqe
            //
            io_uring_sqe.user_data(slot, requestID);
            io_uring_sqe.fd(slot, sqe.fd());
            io_uring_sqe.opcode(slot, (byte)sqe.opcode());
            io_uring_sqe.open_flags(slot, sqe.xxx_flags());
            io_uring_sqe.flags(slot, (byte)sqe.flags());
            io_uring_sqe.addr(slot, sqe.addr().orElse(MemorySegment.NULL).address());
            io_uring_sqe.addr2(slot, sqe.addr2().orElse(MemorySegment.NULL).address());
            io_uring_sqe.buf_index(slot, (short)sqe.buf_index().orElse(0));
            io_uring_sqe.off(slot, sqe.off().orElse(0L));
            io_uring_sqe.len(slot, sqe.len().orElse(0));
            // Populate the tail slot
            ringSeg.setAtIndex(ValueLayout.JAVA_INT, tailIndex, tailIndex);
            //Util.print(slot, "SQE");
            setTail(++tailVal);
            return requestID;
        }
    }

    final class CompletionQueue extends QueueImplBase {
        CompletionQueue(MemorySegment ringSeg, MemorySegment head, MemorySegment tail, int mask) {
            super(ringSeg, head, tail, mask, io_uring_cqe.$LAYOUT());
        }

        public Cqe pollHead() {
            int headVal = getHead(false);
            Cqe res = null;
            if (headVal != getTail(true)) {
                int index = headVal & ringMask;
                int offset = index * ringLayoutSize;
                MemorySegment seg = ringSeg.asSlice(offset,
                        ringLayoutSize, ringLayoutAlignment);
                res = new Cqe(
                        io_uring_cqe.user_data(seg),
                        io_uring_cqe.res(seg),
                        io_uring_cqe.flags(seg));
                headVal++;
            }
            setHead(headVal);
            return res;
        }
    };

    /**
     * Adds the given FD to this ring's epoll(7) instance
     * and creates the epoll instance if it hasn't already been created
     *
     * If using the EPOLLONESHOT mode (in flags) the opaque field
     * can be used to return the "id" of the specific operation that was
     * kicked off.
     *
     * @param fd target fd to manage
     * @param poll_events bit mask of events to activate
     * @param opaque a 64 bit value to return with event notifications
     * @return
     * @throws IOException
     * @throws InterruptedException
     */
    public long epoll_add(FD fd, int poll_events, long opaque) throws IOException, InterruptedException {
        return epoll_op(fd, poll_events, opaque, EPOLL_CTL_ADD());
    }

    public long epoll_del(FD fd, int poll_events) throws IOException, InterruptedException {
        return epoll_op(fd, poll_events, -1L, EPOLL_CTL_DEL());
    }

    public long epoll_mod(FD fd, int poll_events, long opaque) throws IOException, InterruptedException {
        return epoll_op(fd, poll_events, -1L, EPOLL_CTL_DEL());
    }
    private long epoll_op(FD fd, int poll_events, long opaque, int op) throws IOException, InterruptedException {
        if (this.epollfd == -1) {
            this.epollfd = initEpoll();
        }
        FDImpl fdi = (FDImpl)fd;

        MemorySegment targetfd = arena.allocateFrom(ValueLayout.OfInt.JAVA_INT, fdi.fd());

        Sqe request = new Sqe()
                .opcode(IORING_OP_EPOLL_CTL())
                .fd(epollfd)
                .addr(targetfd)
                .xxx_flags(poll_events)
                .len(op);

        if (opaque != -1L) {
            MemorySegment event = arena.allocate(epoll_event.$LAYOUT());
            epoll_event.events(event, poll_events);
            var dataSlice = epoll_event.data(event);
            epoll_data_t.u64(dataSlice, opaque);
            request = request.off(event.address());
        }

        long id = submit(request);
        return id;
    }

    static MemorySegment getSegmentFor(MemoryLayout layout) {
        return arena.allocate(layout.byteSize(), layout.byteAlignment())
                    .fill((byte)0);
    }

    static String errorString(int errno) {
        errno = -errno;
        return "Error: " + strerror(errno);
    }

    public Sqe getTimeoutSqe(Duration maxwait) {
        MemorySegment seg = arena.allocate(__kernel_timespec.$LAYOUT()).fill((byte)(0));
        __kernel_timespec.tv_sec(seg, maxwait.getSeconds());
        __kernel_timespec.tv_nsec(seg, maxwait.getNano());
        return new Sqe()
                .opcode(IORING_OP_LINK_TIMEOUT())
                .addr(seg)
                //.flags(IOSQE_IO_LINK())
                .len(1);
    }
    private final static ValueLayout POINTER = ValueLayout.ADDRESS.withTargetLayout(
            MemoryLayout.sequenceLayout(Long.MAX_VALUE, JAVA_BYTE)
    );

    private static final MethodHandle mmap_fn = locateStdHandle(
            "mmap", FunctionDescriptor.of(
                    POINTER,
                    //ValueLayout.JAVA_LONG, // returned address
                    ValueLayout.JAVA_LONG, // input address, usually zero
                    ValueLayout.JAVA_LONG, // size_t
                    ValueLayout.JAVA_INT, // int prot (PROT_READ | PROT_WRITE)
                    ValueLayout.JAVA_INT, // int flags (MAP_SHARED|MAP_POPULATE)
                    ValueLayout.JAVA_INT, // int fd
                    ValueLayout.JAVA_LONG // off_t (64bit?)
            )
    );

    private static final MethodHandle epoll_create_fn = locateStdHandle(
            "epoll_create", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, // returned fd
                    ValueLayout.JAVA_INT // int size (ignored)
            ), SystemCallContext.errnoLinkerOption()
    );
    private static final MethodHandle setup_fn = locateHandleFromLib(
            "liburing.so",
            "io_uring_setup",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS)
    );

    private static final MethodHandle close_fn = locateStdHandle(
            "close",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT),
            SystemCallContext.errnoLinkerOption()
    );
    private static final MethodHandle enter_fn = locateHandleFromLib(
            "liburing.so",
            "io_uring_enter",
            FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS) // sigset_t UNUSED for now
    );
    // mmap constants used internally
    private static final int PROT_READ = 1;
    private static final int PROT_WRITE = 2;
    private static final int MAP_SHARED = 1;
    private static final int MAP_POPULATE = 0x8000;

    /**
     * offset (when mapping IOURING segments) must be one of:
     *      sun.nio.ch.iouring.foreign.iouring_h.IORING_OFF_SQ_RING()
     *      sun.nio.ch.iouring.foreign.iouring_h.IORING_OFF_CQ_RING()
     *      sun.nio.ch.iouring.foreign.iouring_h.IORING_OFF_SQES()
     *
     * @param size
     * @param fd
     * @param offset
     * @return
     */
    private static MemorySegment mmap(long size, int fd, long offset) {
        MemorySegment seg = null;
        try {
            seg = (MemorySegment)mmap_fn
                    .invokeExact(0L, size,
                            PROT_READ | PROT_WRITE,
                            MAP_SHARED | MAP_POPULATE,
                            fd,
                            offset
                    );
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        long addr = seg.address();
        return seg.reinterpret(size);
    }

    int ringFd() {
        return fd;
    }
}


