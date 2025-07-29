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

import java.io.IOException;
import java.time.Duration;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_TIMEOUT;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_POLL_ADD;
import static sun.nio.ch.iouring.foreign.iouring_h_1.IORING_OP_POLL_REMOVE;

/**
 */
public class IoUring {

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
     * flags used in user_data field
     * Flags set in upper 32 bits. Lower 32 bits used for fd
     */
    public static final long ADD_OP   = 0x100000000L;
    public static final long DEL_OP   = 0x200000000L;
    public static final long TIMER_OP = 0x400000000L;

    private static final int SQ_ENTRIES = 5;
 
    private final int event;
    private final IOUringImpl impl;
    // Counter incremented for each operation and reset after poll() returns
    private int additions, deletions, timers;

    private IoUring(int event) throws IOException, InterruptedException {
	this.event = event;
	this.impl = new IOUringImpl(SQ_ENTRIES);
	this.additions = 0;
	this.deletions = 0;
	this.timers = 0;
    }

    public static IoUring create(int event) throws IOException, InterruptedException {
	return new IoUring(event);
    }

    public void poll_add(int sock) throws IOException, InterruptedException {
	Sqe sqe = new Sqe()
		.opcode(IORING_OP_POLL_ADD())
		.fd(sock)
		.user_data(sock | ADD_OP)
		.poll_events(event);
	impl.submit(sqe);
	additions++;
    }
	

    public void cancel_poll(int sock) throws IOException, InterruptedException {
	Sqe sqe = new Sqe()
		.opcode(IORING_OP_POLL_REMOVE())
		.fd(sock)
		.user_data(sock | DEL_OP);
	impl.submit(sqe);
	deletions++;
    }

    public int poll() throws IOException, InterruptedException {
	return poll(null);
    }

    public int poll(Duration duration) throws IOException, InterruptedException {
	Sqe timeout = null;
        // we want results for all deletions and at least one poll op
	int mineventswait = deletions + 1;

	if (duration != null) {
	    timers = 1;
	    timeout = impl.getTimeoutSqe(duration,
			                 IORING_OP_TIMEOUT(), mineventswait);
	    impl.submit(timeout);
	}
	int submissions = additions + deletions + timers;

        int n = impl.enter(submissions, mineventswait, 0);
	System.out.printf("Enter returns %d\n", n);
	assert n == mineventswait;
	additions = deletions = timers = 0;
	// For now, only check the result of the poll op itself
	// and the timer, if it fires
	Cqe cqe;
	int result = -1;
	for (int i=0; i<n; i++) {
	    cqe = impl.pollCompletion();
	    assert cqe != null;
	    int res = cqe.res();
	    long udata = cqe.user_data();
	    if ((udata & ADD_OP) != 0) {
		System.out.printf("POLLER: res = %d\n", res);
	        result = 1;
	    } else if ((udata & TIMER_OP) != 0) {
		System.out.printf("TIMER: res = %d\n", res);
		(new Exception("Stack Trace")).printStackTrace(System.out);
	        result = 0;
	    } else {
		throw new InternalError();
	    }
	}
	return result;
    }
}


