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

import static sun.nio.ch.iouring.foreign.iouring_h.IOSQE_IO_LINK;

class SimpleRingImpl extends BlockingRing {
    SimpleRingImpl(IOUring ring) {
        super(ring);
    }

    @Override
    public void close(boolean orderly) throws IOException {
        ring().close();
    }

    @Override
    public Cqe blockingSubmit(Sqe request) throws InterruptedException, IOException {
        long requestID = ring.submit(request); // Assuming there is space
        if (requestID < 0) {
            throw new IOException("blockingSubmit failed: ");
        }
        Cqe response = enter(1);
        if (response.user_data() != requestID)
            throw new InternalError();
        return response;
    }

    private Cqe enter(int nsubmissions) throws IOException {
        var ret = ring.enter(nsubmissions, nsubmissions, 0);
        if (ret < 0)
            throw new IOException("io_uring_enter failed");
        return ring.pollCompletion();
    }
    @Override
    public Cqe blockingSubmit(Sqe request, Optional<Duration> duration) throws InterruptedException, IOException {
        if (ring.sqsize() < 2) {
            throw new IllegalStateException("Ring must have at least 2 entries to support timed operations");
        }
        long requestID;
        long timerID = -1;
        int n;
        if (duration.isPresent()) {
            // link this request to the following timeout request
            request.flags(request.flags() | IOSQE_IO_LINK());
            Sqe timeout = ring.getTimeoutSqe(duration.get());
            requestID = ring.submit(request);
            timerID = ring.submit(timeout);
            n = 2;
        } else {
            n = 1;
            requestID = ring.submit(request);
        }
        Cqe cqe = enter(n);
        long respID = cqe.user_data();
        if (respID == timerID) {
            throw new IOException("Operation timed out");
        }
        if (respID != requestID) {
            throw new InternalError(); // shouldn't happen
        }
        return cqe;
    }
}
