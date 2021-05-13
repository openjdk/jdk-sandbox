/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

class TestSocketChannelEvents {

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        enableStreamingAndRun(TestSocketChannelEvents::runTest);
    }

    static void enableStreamingAndRun(ThrowingRunnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(6);
        try (var rs = new RecordingStream()) {
            rs.enable("jdk.SocketAccept").withThreshold(Duration.ofMillis(0)).withStackTrace();
            rs.enable("jdk.SocketConnect").withThreshold(Duration.ofMillis(0)).withStackTrace();
            rs.enable("jdk.SocketRead").withThreshold(Duration.ofMillis(0));
            rs.enable("jdk.SocketWrite").withThreshold(Duration.ofMillis(0));
            rs.onEvent("jdk.SocketAccept", event -> {
                System.out.println("---\nRECEIVED: " + eventToString(event));
                latch.countDown();
            });
            rs.onEvent("jdk.SocketConnect", event -> {
                System.out.println("---\nRECEIVED: " + eventToString(event));
                latch.countDown();
            });
            rs.onEvent("jdk.SocketRead", event -> {
                System.out.println("---\nRECEIVED: " + eventToString(event));
                latch.countDown();
            });
            rs.onEvent("jdk.SocketWrite", event -> {
                System.out.println("---\nRECEIVED: " + eventToString(event));
                latch.countDown();
            });
            rs.startAsync();

            task.run();
            latch.await();
        }
    }

    static void runTest() throws Exception {
        try (var server = new Server();
             var client = new Client(InetAddress.getLocalHost(), server.port)) {
            Thread t1 = new Thread(server, "Server-Thread");
            Thread t2 = new Thread(client, "Client-Thread");
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        }
    }

    public static class Client implements Runnable, AutoCloseable {
        private final SocketChannel sc;

        Client(InetAddress remote, int port) throws IOException {
            sc = SocketChannel.open(new InetSocketAddress(remote, port));
        }

        @Override
        public void run() {
            try {
            var buffer = ByteBuffer.wrap(new byte[] { (byte)0xFF });
            sc.write(buffer);
            buffer.clear();
            int r = sc.read(buffer);
            if (r != 1)
                throw new AssertionError("Expected 1, got:" + r);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            sc.close();
        }
    }

    public static class Server implements Runnable, AutoCloseable {

        private final ServerSocketChannel ssc;
        private final InetAddress ia;
        private final int port;

        Server() throws Exception {
            ssc = ServerSocketChannel.open().bind(new InetSocketAddress(0));
            var addr = (InetSocketAddress)ssc.getLocalAddress();
            ia = addr.getAddress();
            port = addr.getPort();
        }

        @Override
        public void run() {
            try (var sc = ssc.accept()) {
                var buffer = ByteBuffer.wrap(new byte[1]);
                sc.read(buffer);
                buffer.flip();
                int n = sc.write(buffer);
                if (n != 1)
                    throw new AssertionError("Expected 1, got:" + n);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            ssc.close();
        }
    }

    static String eventToString(RecordedEvent event) {
        var fields = event.getFields();
        StringBuilder sb = new StringBuilder(event + " [");
        for (ValueDescriptor vd : fields) {
            var name = vd.getName();
            var value = event.getValue(vd.getName());
            sb.append(name).append("=").append(value).append("\n");
        }
        return sb.append("]").toString();
    }
}