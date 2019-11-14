/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/**
 * @test
 * @bug 8231358
 * @run main/othervm Basic 32000 32000 nagle-off
 * @run main/othervm Basic default default nagle-off
 * @summary Basic test for Unix Domain and Inet socket and server socket channels
 */

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;

public class Basic {
    static int sockRxBufsize, sockTxBufsize;
    static boolean nagle;
    static String tempDir;

    static {
        try {
            Path parent = Paths.get(".");
            Path child = Files.createTempDirectory(parent, null);
            tempDir = child.toString();
        } catch (IOException e) {
            tempDir = null;
        }
    }

    public static void main(String args[]) throws Exception {
        if (args.length != 3)
            usage();

        if (!supported()) {
            System.out.println("Unix domain channels not supported");
            return;
        }
        sockRxBufsize = getInt(args[0]);
        sockTxBufsize = getInt(args[1]);
        if (args[2].equals("nagle-on"))
            nagle = true;
        else if (args[2].equals("nagle-off"))
            nagle = false;

        warmup();
        test(128, 1000);
        test(8 * 1024, 10000);
        test(16 * 1024, 10000);
        test(32 * 1024, 10000);
    }

    static boolean supported() {
        try {
            SocketChannel.open(StandardProtocolFamily.UNIX);
        } catch (UnsupportedAddressTypeException e) {
            return false;
        } catch (Exception e) {
            return true; // continue test to see what problem is
        }
        return true;
    }

    static int getInt(String s) {
        if (s.equalsIgnoreCase("default"))
            return -1;
        else
            return Integer.parseInt(s);
    }

    static void usage() {
        System.out.println("usage: java Basic " +
            "<kernel sock read buf size> <kernel sock send buf size> {nagle-on|nagle-off}");
        System.out.println("nagle setting only affects TCP sockets");
        System.exit(-1);
    }

    static void warmup() throws Exception {
        Server server = new Server(StandardProtocolFamily.UNIX, 1024);
        Client client = new Client(server, 128, 100);
        server.start();
        client.run();

        server = new Server(StandardProtocolFamily.INET, 1024);
        client = new Client(server, 128, 100);
        server.start();
        client.run();
    }

    static void test(int bufsize, int nbufs) throws Exception {
        long unix = testUnix(bufsize, nbufs);
        long inet = testInet(bufsize, nbufs);
        // expect unix to be faster (express as percentage of inet)
        long percent = (unix * 100) / inet;
        System.out.printf ("Unix elapsed time is %d%% of the INET time\n\n", percent);
    }

    static long testUnix(int bufsize, int nbufs) throws Exception {
        Server server = new Server(StandardProtocolFamily.UNIX, bufsize);
        Client client = new Client(server, bufsize, nbufs);
        System.out.printf("Test (family=unix bufsize=%d, nbufs=%d) ", bufsize, nbufs);
        server.start();
        client.run();
        long unix = client.elapsed();
        int sbuf = client.sendSocketBufsize();
        int rbuf = client.receiveSocketBufsize();
        System.out.printf("completed in %d ns (sbuf=%d, rbuf=%d)\n", unix, sbuf, rbuf);
        System.gc();
        return unix;
    }

    static long testInet(int bufsize, int nbufs) throws Exception {
        Server server = new Server(StandardProtocolFamily.INET, bufsize);
        Client client = new Client(server, bufsize, nbufs);
        System.out.printf("Test (family=inet bufsize=%d, nbufs=%d) ", bufsize, nbufs);
        server.start();
        client.run();
        long inet = client.elapsed();
        System.gc();
        int sbuf = client.sendSocketBufsize();
        int rbuf = client.receiveSocketBufsize();
        System.out.printf("completed in %d ns (sbuf=%d, rbuf=%d)\n", inet, sbuf, rbuf);
        return inet;
    }

    static void setNagle(SocketChannel chan, boolean value) throws IOException {
        if (chan.getRemoteAddress() instanceof InetSocketAddress) {
            chan.setOption(StandardSocketOptions.TCP_NODELAY, value);
        }
    }

    static void setSendBufferSize(SocketChannel chan, int bufsize) throws IOException {
        if (bufsize < 0)
            return;
        chan.setOption(StandardSocketOptions.SO_SNDBUF, bufsize);
    }

    static void setRecvBufferSize(SocketChannel chan, int bufsize) throws IOException {
        if (bufsize < 0)
            return;
        chan.setOption(StandardSocketOptions.SO_RCVBUF, bufsize);
    }

    static class Server extends Thread {

        ServerSocketChannel server;
        SocketAddress address;
        SocketChannel connection;
        SelectionKey ckey;
        List<ByteBuffer> toSend = new LinkedList<>();
        final int bufsize;
        Path sockfile;

        Server(ProtocolFamily family, int bufsize) throws IOException {
            //setDaemon(true);
            SocketAddress addr;
            this.bufsize = bufsize;
            if (family == StandardProtocolFamily.UNIX) {
                server = ServerSocketChannel.open(family);
                sockfile = Path.of(tempDir, "server.sock");
                Files.deleteIfExists(sockfile);
                addr = new UnixDomainSocketAddress(sockfile);
                System.out.println("ADDR = " + addr);
            } else {
                server = ServerSocketChannel.open();
                addr = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
            }
            server.bind(addr);
            address = server.getLocalAddress();
        }

        SocketAddress getAddress() {
            return address;
        }

        public void  run() {
            try {
                server.configureBlocking(false);
                Selector sel = Selector.open();
                server.register(sel, SelectionKey.OP_ACCEPT, server);

                while (true) {
                    int n = sel.select();
                    var keys = sel.selectedKeys();
                    for (SelectionKey key : keys) {
                        if (key.isAcceptable()) {
                            if (connection != null)
                                throw new RuntimeException("One connection per server");
                            ServerSocketChannel server = (ServerSocketChannel)key.attachment();
                            connection = server.accept();
                            connection.configureBlocking(false);
                            setNagle(connection, nagle);
                            setSendBufferSize(connection, sockTxBufsize);
                            setRecvBufferSize(connection, sockRxBufsize);
                            ckey = connection.register(sel, SelectionKey.OP_READ, connection);
                        }
                        if (key.isReadable()) {
                            ByteBuffer buf = ByteBuffer.allocate(bufsize);
                            SocketChannel channel = (SocketChannel)key.attachment();
                            int m = channel.read(buf);
                            if (m == -1) {
                                channel.close();
                                return;
                            } else {
                                buf.flip();
                                // ECHO
                                toSend.add(buf);
                                ckey.interestOpsOr(SelectionKey.OP_WRITE);
                            }
                        }
                        if (key.isWritable()) {
                            if (toSend.isEmpty()) {
                                ckey.interestOpsAnd(~SelectionKey.OP_WRITE);
                            } else {
                                ByteBuffer b = toSend.get(0);
                                connection.write(b);
                                if (b.remaining() == 0)
                                    toSend.remove(0);
                            }
                        }
                    }
                    keys.clear();
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    if (server.isOpen()) server.close();
                    if (sockfile != null)
                        Files.deleteIfExists(sockfile);
                } catch (IOException ee) {}
            }
        }
    }

    static void fill(ByteBuffer buf) {
        int n = buf.remaining();
        for (int i=0; i<n; i++) {
            buf.put((byte)(i % 127));
        }
    }

    static void check(ByteBuffer buf, int expected, int iteration) throws Exception {
        if (buf.remaining() != expected)
            throw new RuntimeException();
        for (int i=0; i<expected; i++) {
            int b = buf.get();
            if (b != (i % 127)) {
                System.out.printf("b = %d , i = %d\n", b, i);
                throw new RuntimeException("Iteration " + Integer.toString(iteration));
            }
        }
    }

// read until buf is full

    static void readFully(SocketChannel chan, ByteBuffer buf) throws Exception {
        int n = buf.remaining();
        while (n > 0) {
            int c = chan.read(buf);
            if (c == -1)
                throw new RuntimeException("EOF");
            n -= c;
        }
    }

    static class Client {
        Server server;
        int bufsize, nbufs, sbuf, rbuf;
        long elapsed;

        Client(Server server, int bufsize, int nbufs) {
            this.server = server;
            this.bufsize = bufsize;
            this.nbufs = nbufs;
        }

        public void run() throws Exception {
            SocketAddress remote = server.getAddress();
            long start = System.nanoTime();
            SocketChannel c = null;
            String fam;
            if (remote instanceof UnixDomainSocketAddress) {
                c = SocketChannel.open(StandardProtocolFamily.UNIX);
                fam = "unix";
            } else {
                c = SocketChannel.open();
                fam = "inet";
            }
            setNagle(c, nagle);
            c.connect(remote);
            setSendBufferSize(c, sockTxBufsize);
            setRecvBufferSize(c, sockRxBufsize);
            ByteBuffer tx = ByteBuffer.allocate(bufsize);
            ByteBuffer rx = ByteBuffer.allocate(bufsize);
            fill(tx);
            for (int i=0; i<nbufs; i++) {
                tx.rewind();
                c.write(tx);
                rx.clear();
                readFully(c, rx);
                rx.flip();
                check(rx, bufsize, i);
            }
            long end = System.nanoTime();
            elapsed = end - start;
            sbuf = c.getOption(StandardSocketOptions.SO_SNDBUF);
            rbuf = c.getOption(StandardSocketOptions.SO_SNDBUF);
            c.close();
        }

        long elapsed() {
            return elapsed;
        }

        int receiveSocketBufsize() {return rbuf;}
        int sendSocketBufsize() {return sbuf;}
    }

}
