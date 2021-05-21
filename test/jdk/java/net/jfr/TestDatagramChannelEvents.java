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
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import jdk.jfr.ValueDescriptor;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingStream;

class TestDatagramChannelEvents {

    interface ThrowingRunnable {
        void run() throws Exception;
    }

    public static void main(String[] args) throws Exception {
        enableStreamingAndRun(TestSocketChannelEvents::runTest);
    }

    static void enableStreamingAndRun(ThrowingRunnable task) throws Exception {
        CountDownLatch latch = new CountDownLatch(6);
        try (var rs = new RecordingStream()) {
            rs.enable("jdk.DatagramSend").withThreshold(Duration.ofMillis(0)).withStackTrace();
            rs.enable("jdk.DatagramReceive").withThreshold(Duration.ofMillis(0)).withStackTrace();
            rs.onEvent("jdk.DatagramReceive", event -> {
                System.out.println("---\nRECEIVED: " + eventToString(event));
                latch.countDown();
            });
            rs.onEvent("jdk.DatagramSend", event -> {
                System.out.println("---\nSENT: " + eventToString(event));
                latch.countDown();
            });
            rs.startAsync();
            task.run();
            latch.await();
        }
    }

    static void runTest() throws Exception {
        try (var sender = new DatagramSender();
             var receiver = new DatagramReceiver()) {
            Thread t1 = new Thread(sender, "Server-Thread");
            Thread t2 = new Thread(receiver, "Client-Thread");
            t1.start();
            t2.start();
            t1.join();
            t2.join();
        }
    }

    public static class DatagramSender implements Runnable, AutoCloseable {

        DatagramSocket sender;

        DatagramSender() throws IOException {
            sender = new DatagramSocket();
        }

        @Override
        public void run() {
            try {
                var buffer = ByteBuffer.wrap(new byte[] { (byte)0xFF });
                DatagramPacket dp = new DatagramPacket(buffer.array(), 0);
                sender.send(dp);
                buffer.clear();
                // TBD: Cleanup and send packet to proper receiver
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void close() throws IOException {
            // TBD: Implement here
        }
    }

    public static class DatagramReceiver implements Runnable, AutoCloseable {

        // TBD: Fully Implement Datagram Receiver

        DatagramReceiver() throws Exception {

        }

        @Override
        public void run() {
            var buffer = ByteBuffer.wrap(new byte[1]);
        }

        @Override
        public void close() throws IOException {

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