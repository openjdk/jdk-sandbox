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
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

import static sun.nio.ch.iouring.Util.strerror;
import static sun.nio.ch.iouring.foreign.iouring_h.IORING_OP_NOP;
import static sun.nio.ch.iouring.foreign.iouring_h.IORING_ENTER_SQ_WAIT;
import static sun.nio.ch.iouring.foreign.iouring_h.IOSQE_IO_LINK;

/**
 * A simple multi threaded blocking interface over IOUring which supports
 * cancellation (by the kernel) of requests after a pre-defined timeout.
 */
public class BlockingRingImpl extends BlockingRing {
    static record Submission(
            Sqe sqe,            // The user sqe or timer
            CompletableFuture<Cqe> result) // the CF which will wakeup the caller (not used for timer)
    {}

    // Requests submitted pending acknowledgment of acceptance by kernel
    // Note, completion comes later (on the completion queue)
    // Could be LinkedTransferQueue but prefer bounded Queue
    private final LinkedBlockingQueue<Submission> submissionQ;
    private final int sqsize;
    private int sqfree;
    private int submissions;
    private final ConcurrentHashMap<Long,Submission> operations
            = new ConcurrentHashMap<>();
    private final Thread submitter;
    private final Thread completer;

    // Strictly not necessary. When a timed blocking operation
    // completes normally, a cancellation event is delivered
    // for the corresponding time. Keeping track of these
    // timers means we can filter them out and highlight
    // other unexpected events.
    //private final HashSet<Long> timerOperations
            //= new HashSet<>();

    BlockingRingImpl(IOUring ring) throws IOException {
        super(ring);
        // set limit to 5, which means calling threads will block
        // if there are 5 requests submitted simultaneously
        this.submissionQ = new LinkedBlockingQueue<>(5);
        this.sqsize = ring.sqsize();
        this.sqfree = this.sqsize;
        //this.submitter = Thread.startVirtualThread(this::submissionLoop);
        this.submitter = new Thread(this::submissionLoop, "Ring Submission Queue");
        this.submitter.setDaemon(true);
        this.submitter.start();
        this.completer = new Thread(this::completionLoop, "Ring Completion Queue");
        this.completer.setDaemon(true);
        this.completer.start();
        //this.completer = Thread.startVirtualThread(this::completionLoop);
    }
    public CompletableFuture<Cqe> submit(Sqe request) throws InterruptedException {
        return submit(request, Optional.empty());
    }
    public CompletableFuture<Cqe> submit(Sqe request, Optional<Duration> duration) throws InterruptedException {
        Sqe timeout = null;
        Submission timerSub = null;
        long deadline = duration.isPresent() ? duration.get().toMillis() + System.currentTimeMillis() : -1;
        Submission submission = new Submission(request, new CompletableFuture<>());
        if (deadline != -1) {
            // link this request to the following timeout request
            request.flags(request.flags() | IOSQE_IO_LINK());
            timeout = ring.getTimeoutSqe(duration.get());
            timerSub = new Submission(timeout, null);
        }
        submissionQ.put(submission);
        if (timerSub != null) {
            // Also submit a timer request. The ring thread aggregates all timer requests down to one.
            submissionQ.put(timerSub);
        }
        return submission.result();
    }

    @Override
    public Cqe blockingSubmit(Sqe request) throws InterruptedException, IOException {
        return blockingSubmit(request, Optional.empty());
    }

    @Override
    public Cqe blockingSubmit(Sqe request, Optional<Duration> duration) throws InterruptedException, IOException {
        CompletableFuture<Cqe> cf = submit(request, duration);
        try {
            return cf.get();
        } catch(ExecutionException e) {
            Throwable t = e.getCause();
            if (t instanceof IOException ioe)
                throw ioe;
            else if (t instanceof InterruptedException ie)
                throw ie;
            else
                throw new RuntimeException(t);
        }
    }

    /**
     *  Close this blocking ring. In particular, stop the submitter and completer threads.
     *  if orderly true then the underlying ring stays open. If false, it is closed
     * @param orderly
     * @throws IOException
     */
    public void close(boolean orderly) throws IOException {
        if (!orderly) {
            // Stop the submitter and then just close the ring
            // which will make the completer exit as well
            submitter.interrupt();
            ring.close();
        } else {
            // notify submitter which will exit quickly
            // and the OP_NOP will notify the completer
            // which waits until all operations complete
            // A timeout could be added here as well..
            Sqe sqe = new Sqe().opcode(IORING_OP_NOP());
            try {
                Cqe cqe = blockingSubmit(sqe);
                if (cqe.res() < 0) {
                    throw new IOException(strerror(-cqe.res()));
                }
            } catch (InterruptedException e) {
                throw new IOException(e);
            }
        }
    }

    /**
     * We know there is at least one free slot on ring so
     * take one element from SubmissionQ (blocking if necessary)
     * and submit it.
     *
     * @return true if the submitter must shutdown on return
     * @throws InterruptedException
     * @throws IOException
     */
    private boolean takeAndSubmit() throws InterruptedException, IOException {
        Submission sub = submissionQ.take();
        Sqe sqe = sub.sqe();
        sqfree--;
        submissions++;
        long id = ring.submit(sub.sqe());
        operations.put(id, sub);
        if (sqe.opcode() == IORING_OP_NOP()) {
            // signifies a shutdown
            return true;
        }
        return  false;
    }
    private void submissionLoop() {
        boolean closing = false;
        try {
            while (true) {
                // Block if necessary before submitting one entry
                closing = takeAndSubmit();

                // submit as many entries as are in Q while slots available
                while (sqfree > 0 && !submissionQ.isEmpty() && !closing) {
                    closing = takeAndSubmit();
                }
                // Notify kernel of submissions
                int ret = ring.enter(submissions, 0, 0);
                if (ret < 0)
                    throw new IOException("ioring enter error " + ret);
                if (closing)
                    return;
                sqfree += ret;
                submissions -= ret;
                while (sqfree == 0) {
                    // block until at least 1 SQ entry available
                    System.out.printf("ring.enter(2) submissions=%d returned=%d\n", submissions, ret);
                    ret = ring.enter(0, 0, IORING_ENTER_SQ_WAIT());
                    sqfree += ret;
                }
            }
        } catch (InterruptedException e) {
            // terminate;
        } catch (IOException e) {
            e.printStackTrace();
            // TODO ? - probably just terminate
        }
    }

    private void completionLoop() {
        try {
            boolean closing = false;
            CompletableFuture<Cqe> closer = null;

            while (true) {
                if (ring.cqempty()) {
                    int ret = ring.enter(0, 1, 0);
                    //System.out.printf("ring.enter(1) submissions=%d returned=%d\n", submissions, ret);
                    if (ret < 0)
                        throw new IOException("ioring enter error " + strerror(-ret));
                }
                Cqe cqe = ring.pollCompletion();
                if (cqe != null) {
                    long id = cqe.user_data();
                    //System.out.printf("Cqe received : %s\n", cqe);
                    Submission sub = operations.remove(id);
                    Sqe sqe = sub.sqe();
                    CompletableFuture<Cqe> cf = sub.result();
                    if (sqe.opcode() == IORING_OP_NOP()) {
                        closing = true;
                        closer = cf;
                    } else {
                        if (cf != null)
                            cf.complete(cqe);
                        else {
                            // Should be a timer cancellation
                            //if (cqe.res() < 0) {
                                //System.out.println("Error received: " + Util.strerror(-cqe.res()));
                                //System.out.println(cqe);
                            //}
                        }
                    }
                }
                if (closing && operations.size() == 0) {
                    closer.complete(new Cqe(0, 0, 0));
                    return;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            // TODO ? - probably just terminate
        }
    }

    public IOUring iouring() {
        return ring;
    }
}
