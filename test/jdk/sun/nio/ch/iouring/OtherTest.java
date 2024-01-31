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
 * no run tag yet
 */

import sun.nio.ch.iouring.*;
import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;

import static sun.nio.ch.iouring.IOUring.strerror;
import static sun.nio.ch.iouring.foreign.iouring_h.AF_INET;
import static sun.nio.ch.iouring.foreign.iouring_h.IORING_ENTER_SQ_WAIT;
import static sun.nio.ch.iouring.foreign.iouring_h_1.*;
import static sun.nio.ch.iouring.foreign.iouring_h_1.SOCK_STREAM;

public class OtherTest {
    static void test4() throws Throwable {
        int QSIZE = 2;
        int REQUESTS = 4;
        var r = new IOUring(QSIZE);
        System.out.println(r.cqsize());
        System.out.println(r.sqsize());
        BlockingRing ring = BlockingRing.getMultiThreadedRing(r);
        IOURingFile file = IOURingFile.open(ring,
                "/tmp/foo.txt",
                IOURingFile.O_WRONLY | IOURingFile.O_APPEND |
                        IOURingFile.O_CREAT,
                00700);
        // Close the blocking ring so its thread doesn't interfere with rest of test
        ring.close(true);

        System.out.println("XX 1");
        // Attempt multiple writes in parallel to see if we can
        // fill the Submission Q
        var data = "Hello world";
        HashSet<Long> requests = new HashSet<>();
        int submitted = 0;
        for (int i=0; i<REQUESTS; i++) {
            var buf = ByteBuffer.allocateDirect(data.length());
            buf.put(data.getBytes(StandardCharsets.UTF_8));
            buf.flip();
            while (submitted >= QSIZE) {
                int ret = r.enter(submitted, 0, IORING_ENTER_SQ_WAIT());
                System.out.println("enter(IORING_ENTER_SQ_WAIT) returns " + ret);
                submitted -= ret;
            }
            long id = file.asyncWrite(buf);
            submitted++;
            if (id < 0)
                throw new IOException(Long.toString(id));
            if (!requests.add(id))
                throw new IOException("repeat request id");
            System.out.printf("Request %X\n", id);
        }
        for (int i=0; i<REQUESTS; i++) {
            int ret = r.enter(submitted, 1, 0);
            System.out.println("enter (0) returns " + ret);
            submitted -= ret;
            System.out.println("enter returns " + ret);
            Cqe cqe = r.pollCompletion();
            System.out.println(cqe);
        }
        System.out.println("DONE");
    }
    static void socktest1() throws IOException, InterruptedException {
        System.out.println("socktest1");
        var r = new IOUring(8, 8, 16 * 1024);
        BlockingRing ring = BlockingRing.getSingleThreadedRing(r);

        IOURingSocket server = IOURingSocket.create(ring, AF_INET(), SOCK_STREAM(), 0);
        server.setsockopt(SOL_SOCKET(), SO_REUSEADDR(), 1);
        int port = 5678;
        server.bind(new InetSocketAddress(InetAddress.getByName("10.0.2.15"), port));
        server.listen(10);
        server.timeout(Duration.ofSeconds(100));
        System.out.println("Server listening at: " + server.getsockname());
        while (true) {
            IOURingSocket sock = server.accept();
            System.out.println("Accepted connection from " + sock.getpeername());
            Thread t = new Thread(new Connection(sock));
            t.start();
            //Thread.startVirtualThread(new Connection(sock));
        }
    }

    static class Connection implements Runnable {
        final IOURingSocket socket;
        final IOUring ring;
        final BlockingRing br;
        Connection(IOURingSocket socket) {
            this.socket = socket;
            this.br = socket.ring();
            this.ring = br.ring();
        }

        boolean readMessage(ByteBuffer buf) throws IOException, InterruptedException {
            boolean done = false;
            int bytesRead = 0;
            while (bytesRead < 2) {
                int n = socket.readFixed(buf);
                if (n == 0)
                    return true; // EOF
                bytesRead += n;
            }
            int toRead = ((buf.get(0) & 0xff) << 8) + (buf.get(1) & 0xff);
            bytesRead -= 2;
            while (bytesRead < toRead) {
                int n = socket.readFixed(buf);
                if (n == 0)
                    throw new IOException("Unexpected EOF");
                bytesRead += n;
            }
            if (bytesRead != toRead) {
                System.out.printf("toRead: %d bytesRead: %d\n", toRead, bytesRead);
                throw new IOException("Too many bytes");
            }
            return false; // ! EOF
        }
        @Override
        public void run() {
            ByteBuffer buffer = null;
            try {
                System.out.println("Connection from " + socket.getpeername());
                while (true) {
                    buffer = ring.getRegisteredBuffer();
                    if (buffer == null) {
                        throw new InternalError();
                    }
                    buffer.clear();
                    if (readMessage(buffer)) {
                        System.out.println("EOF ");
                        ring.returnRegisteredBuffer(buffer);
                        return;
                    }
                    int size = buffer.remaining();
                    byte[] ba = new byte[size];
                    buffer.get(ba);
                    // add 1 to each byte in buffer and send it back
                    for (int i=0; i<size; i++) {
                        ba[i] = (byte)(ba[i] + 1);
                    }
                    buffer.clear();
                    buffer.put(ba);
                    try {
                        socket.writeFixed(buffer);
                    } finally {
                        ring.returnRegisteredBuffer(buffer);
                    }
                }
            } catch (IOException|InterruptedException e) {
                e.printStackTrace();
            } finally {

            }
        }
    }
    static void socktest() throws IOException, InterruptedException {
        System.out.println("socktest");
        var r = new IOUring(8, 8, 16 * 1024);
        BlockingRing ring = BlockingRing.getMultiThreadedRing(r);

        IOURingSocket server = IOURingSocket.create(ring, AF_INET(), SOCK_STREAM(), 0);
        server.bind(new InetSocketAddress(5678));
        server.listen(5);
        int port = server.getsockname().getPort();
        IOURingSocket sock1 = IOURingSocket.create(ring, AF_INET(), SOCK_STREAM(), 0);
        sock1.connect(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port));
        server.timeout(Duration.ofSeconds(5));
        IOURingSocket sock2 = server.accept();
        System.out.printf("sock1 local addr = %s, remote addrs = %s\n", sock1.getsockname(), sock1.getpeername());
        System.out.printf("sock2 local addr = %s, remote addrs = %s\n", sock2.getsockname(), sock2.getpeername());

        ByteBuffer buffer = r.getRegisteredBuffer();
        buffer.put("Hello world".getBytes(StandardCharsets.UTF_8));
        sock1.writeFixed(buffer);
        r.returnRegisteredBuffer(buffer);

        // Now read the data off the other socket

        buffer = r.getRegisteredBuffer();
        int n = sock2.readFixed(buffer);
        byte bb[] = new byte[buffer.remaining()];
        buffer.get(bb);
        r.returnRegisteredBuffer(buffer);
        String s = new String(bb, StandardCharsets.UTF_8);
        System.out.println("Received " + s);
        sock1.close();;
        sock2.close();
        server.close();
        ring.close(true);
    }
    public static void xxxTest() throws IOException, InterruptedException {
        int open_flags = IOURingFile.O_RDWR |
                IOURingFile.O_CREAT |
                IOURingFile.O_APPEND;
        MemorySegment name = Arena.ofAuto().allocateFrom("/tmp/out1.txt");
        IOUring iouring = new IOUring(8);
        BlockingRing br = BlockingRing.getSingleThreadedRing(iouring);
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_OPENAT())
                .fd(-100) // use normal open(2) semantics
                .xxx_flags(open_flags)
                .addr(name)
                .len(0777);
        Cqe response = br.blockingSubmit(sqe);
        System.out.println("Response: " + response);
        if (response.res() < 0) {
            System.out.println("Error: " + strerror(-response.res()));
            return;
        }
        int fd = response.res();
        System.out.println("fd = " + fd);

        MemorySegment dataseg = Arena.ofAuto().allocateFrom("Goodbye wworld");
        sqe = new Sqe()
                .opcode(IORING_OP_WRITE())
                .fd(fd)
                .addr(dataseg)
                .off(-1)

                .len((int)dataseg.byteSize());

        response = br.blockingSubmit(sqe);
        System.out.println("Response: " + response);
        if (response.res() < 0) {
            System.out.println("Error: " + strerror(-response.res()));
            return;
        }
    }
    public static void yyyTest() throws IOException, InterruptedException {
        int open_flags = IOURingFile.O_RDONLY;
        MemorySegment name = Arena.ofAuto().allocateFrom("/etc/passwd");
        IOUring iouring = new IOUring(8);
        BlockingRing br = BlockingRing.getSingleThreadedRing(iouring);
        Sqe sqe = new Sqe()
                .opcode(IORING_OP_OPENAT())
                .fd(-100) // use normal open(2) semantics
                .xxx_flags(open_flags)
                .addr(name)
                .len(0);
        Cqe response = br.blockingSubmit(sqe);
        System.out.println("Response: " + response);
        if (response.res() < 0) {
            System.out.println("Error: " + strerror(-response.res()));
            return;
        }
        int fd = response.res();
        System.out.println("fd = " + fd);

        MemorySegment dataseg = Arena.ofAuto().allocate(1024, 8);

        sqe = new Sqe()
                .opcode(IORING_OP_READ())
                .fd(fd)
                .addr(dataseg)
                .len((int)dataseg.byteSize());

        response = br.blockingSubmit(sqe);
        System.out.println("Response: " + response);
        if (response.res() < 0) {
            System.out.println("Error: " + strerror(-response.res()));
            return;
        }
    }
}
