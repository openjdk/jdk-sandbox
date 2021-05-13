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
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

class TestSocketEvents {

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        enableStreamingAndRun(TestSocketEvents::runTest);
    }

    static void enableStreamingAndRun(ThrowingRunnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(6);
        try (var rs = new RecordingStream()) {
            rs.enable("jdk.SocketAccept").withThreshold(Duration.ofMillis(0));
            rs.enable("jdk.SocketConnect").withThreshold(Duration.ofMillis(0));
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
        private final Socket s;

        Client(InetAddress remote, int port) throws IOException {
            s = new Socket(remote, port);
        }

        @Override
        public void run() {
            try (var is = s.getInputStream();
                 var os = s.getOutputStream()) {
                os.write((byte)0xFF);
                is.read();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            s.close();
        }
    }

    public static class Server implements Runnable, AutoCloseable {

        private final ServerSocket ss;
        private final InetAddress ia;
        private final int port;

        Server() throws Exception {
            ss = new ServerSocket(0);
            ia = ss.getInetAddress();
            port = ss.getLocalPort();
        }

        @Override
        public void run() {
            try (var s = ss.accept();
                 var is = s.getInputStream();
                 var os = s.getOutputStream()) {
                os.write(is.read());  // read one and echo
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            ss.close();
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