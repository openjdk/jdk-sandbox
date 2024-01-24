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
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;

import static sun.nio.ch.iouring.Util.strerror;
import static sun.nio.ch.iouring.foreign.iouring_h.*;

public class IOURingFile extends IOURingReaderWriter {

    public final static int O_RDONLY        = 0000000;
    public final static int O_WRONLY        = 0000001;
    public final static int O_RDWR          = 0000002;
    public final static int O_CREAT         = 0000100;
    public final static int O_EXCL          = 0000200;
    public final static int O_NOCTTY        = 0000400;
    public final static int O_TRUNC         = 0001000;
    public final static int O_APPEND        = 0002000;
    public final static int O_DSYNC		    = 00010000;
    public final static int O_SYNC          = (04000000 | O_DSYNC);

    private final static int AT_FDCWD       = -100;

    private IOURingFile(BlockingRing ring, FDImpl fd) {
        super(ring, fd);
    }

    public static IOURingFile open(BlockingRing ring, String name, int open_flags, int access_mode) throws Throwable {
        MemorySegment nameSeg = Arena.ofAuto().allocateFrom(name);
        Sqe request = new Sqe()
                .opcode(IORING_OP_OPENAT())
                .fd(AT_FDCWD) // use normal open(2) semantics
                .xxx_flags(open_flags)
                .addr(nameSeg)
                .len(access_mode);
        Cqe response = ring.blockingSubmit(request);
        int result = response.res();
        if (result < 0) {
            throw new IOException("Open failed: " + strerror(-result));
        }
        return new IOURingFile(ring, new FDImpl(result));
    }

    // fsync ?
    // file locking

    public void close() throws Throwable {
        Sqe request = new Sqe()
                .opcode(IORING_OP_CLOSE())
                .fd(fd.fd())
                .addr(MemorySegment.NULL);

        Cqe response = ring.blockingSubmit(request);
        int result = response.res();
        if (result < 0) {
            throw new IOException("Close failed: " + strerror(-result));
        }
    }
}
