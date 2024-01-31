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
/*
 * @test
 * @summary  Foo
 * @requires (os.family == "linux")
 * @run main/othervm SingleThreadTest
 */

import sun.nio.ch.iouring.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

/**
 * Single thread blocking comparison between NIO FileChannel and iouring
 *
 * Expect NIO to be faster as all work is done by the user mode thread
 * whereas IOURING delegates the write to a kernel thread.
 */
public class SingleThreadTest {
    public static void main(String[] args) throws Throwable {
        testgroup(false);
        testgroup(true);
    }

    static final int BUFSIZE = 16 * 1024;
    static final int FILE_SIZE = 1 * 1024 * 1024;
    static OpenOption[] no_nio_options = new OpenOption[0];
    static void testgroup(boolean withSync) throws Throwable {
        testgroup(withSync, FILE_SIZE);
    }

    static void testgroup(boolean withSync, int size) throws Throwable {
        System.out.println("SYNC = " + withSync);
        IOUring iouring = new IOUring(16, 16, 8 * 1024);
        BlockingRing ring = BlockingRing.getSingleThreadedRing(iouring);

        channeltest("/tmp/chan.txt", size, true, withSync ? new OpenOption[]{StandardOpenOption.SYNC} : no_nio_options);
        iouringtest("/tmp/ioring.txt", size, true, ring, withSync ? IOURingFile.O_SYNC : 0);
        channeltest("/tmp/chan.txt", size, true, withSync ? new OpenOption[]{StandardOpenOption.SYNC} : no_nio_options);
        iouringtest("/tmp/ioring.txt", size, true, ring, withSync ? IOURingFile.O_SYNC : 0);
    }

    static void fill(ByteBuffer b) {
        int sz = b.capacity();
        b.clear();
        for (int i=0; i<sz; i++) {
            b.put((byte)i);
        }
        b.flip();
    }
    public static void channeltest(String file, int size, boolean logTime, OpenOption... additionals) throws IOException {
        Set options = new HashSet(List.of(
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE));
        options.addAll(Arrays.asList(additionals));
        FileChannel ch = FileChannel.open(Path.of(file), options);
        ByteBuffer buf = ByteBuffer.allocateDirect(BUFSIZE);
        fill(buf);
        long l1 = System.currentTimeMillis();
        int calls = 0;
        while (size > 0) {
            int remaining = buf.remaining();
            if (remaining == 0) {
                buf.position(0);
                buf.limit(Math.min(buf.capacity(), size));
            } else if (size < remaining) {
                buf.limit(buf.position() + size);
            }
            int n = ch.write(buf);
            calls++;
            size -= n;
        }
        ch.close();
        long l2 = System.currentTimeMillis();
        if (logTime)
            System.out.println("channeltest: " + Long.toString(l2-l1) + " calls: " + calls);
    }

    public static void iouringtest(String path, int size, boolean logTime, BlockingRing ring, int... additional) throws Throwable {
        IOUring iouring = ring.ring();
        int options = IOURingFile.O_CREAT |
                      IOURingFile.O_WRONLY | IOURingFile.O_TRUNC;
        for (int opt : additional) {
            options |= opt;
        }
        IOURingFile file = IOURingFile.open(ring, path, options,0777);
        ByteBuffer buf;
        boolean registered = true;
        if ((buf = iouring.getRegisteredBuffer()) == null) {
            buf = getDirectBuffer();
            registered = false;
        }
        fill(buf);
        long l1 = System.currentTimeMillis();
        int calls = 0;
        while (size > 0) {
            int remaining = buf.remaining();
            if (remaining == 0) {
                buf.position(0);
                buf.limit(Math.min(buf.capacity(), size));
            } else if (size < remaining) {
                buf.limit(buf.position() + size);
            }
            int n = registered
                    ? file.writeFixed(buf)
                    : file.write(buf);
            size -= n;
            calls++;
        }
        file.close();
        long l2 = System.currentTimeMillis();
        if (logTime)
            System.out.println("iouring: " + Long.toString(l2-l1) + " calls: " + calls);
    }
    private static ByteBuffer getDirectBuffer() {
        return ByteBuffer.allocateDirect(BUFSIZE);
    }
}
