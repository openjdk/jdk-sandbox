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
 * @library /test/lib
 * @build jdk.test.lib.Utils
 * @run testng Timeouts
 * @run testng/othervm -Djdk.net.usePlainSocketImpl Timeouts
 * @summary Test Socket timeouts
 */

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;
import static org.testng.Assert.*;
import jdk.test.lib.Utils;

@Test
public class Timeouts {

    /**
     * Test timed connect where connection is established
     */
    public void testTimedConnect1() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            try (Socket s = new Socket()) {
                s.connect(ss.getLocalSocketAddress(), 2000);
            }
        }
    }

    /**
     * Test timed connect where connection is refused
     */
    public void testTimedConnect2() throws IOException {
        try (Socket s = new Socket()) {
            SocketAddress remote = Utils.refusingEndpoint();
            try {
                s.connect(remote, 2000);
            } catch (ConnectException expected) { }
        }
    }

    /**
     * Test timed read where the read succeeds immediately
     */
    public void testTimedRead1() throws IOException {
        withConnection((s1, s2) -> {
            s1.getOutputStream().write(99);
            s2.setSoTimeout(30*1000);
            int b = s2.getInputStream().read();
            assertTrue(b == 99);
        });
    }

    /**
     * Test timed read where the read succeeds after a delay
     */
    public void testTimedRead2() throws IOException {
        withConnection((s1, s2) -> {
            scheduleWrite(s1.getOutputStream(), 99, 2000);
            s2.setSoTimeout(30*1000);
            int b = s2.getInputStream().read();
            assertTrue(b == 99);
        });
    }

    /**
     * Test timed read where the read times out
     */
    public void testTimedRead3() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            try {
                s2.getInputStream().read();
                assertTrue(false);
            } catch (SocketTimeoutException expected) { }
        });
    }

    /**
     * Test timed read that succeeds after a previous read has timed out
     */
    public void testTimedRead4() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            try {
                s2.getInputStream().read();
                assertTrue(false);
            } catch (SocketTimeoutException e) { }
            s1.getOutputStream().write(99);
            int b = s2.getInputStream().read();
            assertTrue(b == 99);
        });
    }

    /**
     * Test timed read that succeeds after a previous read has timed out and
     * after a short delay
     */
    public void testTimedRead5() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            try {
                s2.getInputStream().read();
                assertTrue(false);
            } catch (SocketTimeoutException e) { }
            s2.setSoTimeout(30*3000);
            scheduleWrite(s1.getOutputStream(), 99, 2000);
            int b = s2.getInputStream().read();
            assertTrue(b == 99);
        });
    }

    /**
     * Test untimed read that succeeds after a previous read has timed out
     */
    public void testTimedRead6() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            try {
                s2.getInputStream().read();
                assertTrue(false);
            } catch (SocketTimeoutException e) { }
            s1.getOutputStream().write(99);
            s2.setSoTimeout(0);
            int b = s2.getInputStream().read();
            assertTrue(b == 99);
        });
    }

    /**
     * Test untimed read that succeeds after a previous read has timed out and
     * after a short delay
     */
    public void testTimedRead7() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(2000);
            try {
                s2.getInputStream().read();
                assertTrue(false);
            } catch (SocketTimeoutException e) { }
            scheduleWrite(s1.getOutputStream(), 99, 2000);
            s2.setSoTimeout(0);
            int b = s2.getInputStream().read();
            assertTrue(b == 99);
        });
    }

    /**
     * Test async close of timed read
     */
    public void testTimedRead8() throws IOException {
        withConnection((s1, s2) -> {
            s2.setSoTimeout(30*1000);
            scheduleClose(s2, 2000);
            try {
                s2.getInputStream().read();
                assertTrue(false);
            } catch (SocketException expected) { }
        });
    }

    /**
     * Test writing after a timed read. The timed read changes the underlying
     * socket to non-blocking.
     */
    public void testTimedWrite1() throws IOException {
        withConnection((s1, s2) -> {
            s1.getOutputStream().write(99);
            s2.setSoTimeout(3000);
            int b = s2.getInputStream().read();
            assertTrue(b == 99);

            // schedule thread to read s1 to EOF
            scheduleReadToEOF(s1.getInputStream(), 3000);

            // write a lot so that write blocks
            byte[] data = new byte[128*1024];
            for (int i = 0; i < 100; i++) {
                s2.getOutputStream().write(data);
            }
        });
    }

    /**
     * Test async close of writer (after a timed read). The timed read changes
     * the underlying socket to non-blocking.
     */
    public void testTimedWrite2() throws IOException {
        withConnection((s1, s2) -> {
            s1.getOutputStream().write(99);
            s2.setSoTimeout(3000);
            int b = s2.getInputStream().read();
            assertTrue(b == 99);

            // schedule s2 to be be closed
            scheduleClose(s2, 3000);

            // write a lot so that write blocks
            byte[] data = new byte[128*1024];
            try {
                while (true) {
                    s2.getOutputStream().write(data);
                }
            } catch (SocketException expected) { }
        });
    }

    /**
     * Test timed accept where a connection is established immediately
     */
    public void testTimedAccept1() throws IOException {
        Socket s1 = null;
        Socket s2 = null;
        try (ServerSocket ss = new ServerSocket(0)) {
            s1 = new Socket();
            s1.connect(ss.getLocalSocketAddress());
            ss.setSoTimeout(30*1000);
            s2 = ss.accept();
        } finally {
            if (s1 != null) s1.close();
            if (s2 != null) s2.close();
        }
    }

    /**
     * Test timed accept where a connection is established after a short delay
     */
    public void testTimedAccept2() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setSoTimeout(30*1000);
            scheduleConnect(ss.getLocalSocketAddress(), 2000);
            Socket s = ss.accept();
            s.close();
        }
    }
    
    /**
     * Test timed accept where the accept times out
     */
    public void testTimedAccept3() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setSoTimeout(2000);
            try {
                Socket s = ss.accept();
                s.close();
                assertTrue(false);
            } catch (SocketTimeoutException expected) { }
        }
    }

    /**
     * Test timed accept where a connection is established immediately after a
     * previous accept timed out.
     */
    public void testTimedAccept4() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setSoTimeout(2000);
            try {
                Socket s = ss.accept();
                s.close();
                assertTrue(false);
            } catch (SocketTimeoutException expected) { }
            try (Socket s1 = new Socket()) {
                s1.connect(ss.getLocalSocketAddress());
                Socket s2 = ss.accept();
                s2.close();
            }
        }
    }

    /**
     * Test untimed accept where a connection is established after a previous
     * accept timed out
     */
    public void testTimedAccept5() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setSoTimeout(2000);
            try {
                Socket s = ss.accept();
                s.close();
                assertTrue(false);
            } catch (SocketTimeoutException expected) { }
            ss.setSoTimeout(0);
            try (Socket s1 = new Socket()) {
                s1.connect(ss.getLocalSocketAddress());
                Socket s2 = ss.accept();
                s2.close();
            }
        }
    }

    /**
     * Test untimed accept where a connection is established after a previous
     * accept timed out and after a short delay
     */
    public void testTimedAccept6() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setSoTimeout(2000);
            try {
                Socket s = ss.accept();
                s.close();
                assertTrue(false);
            } catch (SocketTimeoutException expected) { }
            ss.setSoTimeout(0);
            scheduleConnect(ss.getLocalSocketAddress(), 2000);
            Socket s = ss.accept();
            s.close();
        }
    }

    /**
     * Test async close of a timed accept
     */
    public void testTimedAccept7() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            ss.setSoTimeout(30*1000);
            scheduleClose(ss, 2000);
            try {
                ss.accept().close();
                assertTrue(false);
            } catch (SocketException expected) { }
        }
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testBadTimeout1() throws IOException {
        try (Socket s = new Socket()) {
            s.setSoTimeout(-1);
        }
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testBadTimeout2() throws IOException {
        try (ServerSocket ss = new ServerSocket(0)) {
            try (Socket s = new Socket()) {
                s.connect(ss.getLocalSocketAddress(), -1);
            }
        }
    }

    @Test(expectedExceptions = { IllegalArgumentException.class })
    public void testBadTimeout3() throws IOException {
        try (ServerSocket ss = new ServerSocket()) {
            ss.setSoTimeout(-1);
        }
    }

    interface ThrowingBiConsumer<T, U> {
        void accept(T t, U u) throws IOException;
    }

    /**
     * Invokes the consumer with a connected pair of sockets
     */
    static void withConnection(ThrowingBiConsumer<Socket, Socket> consumer)
        throws IOException
    {
        Socket s1 = null;
        Socket s2 = null;
        try (ServerSocket ss = new ServerSocket(0)) {
            s1 = new Socket();
            s1.connect(ss.getLocalSocketAddress());
            s2 = ss.accept();
            consumer.accept(s1, s2);
        } finally {
            if (s1 != null) s1.close();
            if (s2 != null) s2.close();
        }
    }

    /**
     * Schedule c to be closed after a delay
     */
    static void scheduleClose(Closeable c, long delay) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Runnable task = () -> { try { c.close(); } catch (IOException ioe) { } };
            executor.schedule(task, delay, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Schedule a thread to connect to the given end point after a delay
     */
    static void scheduleConnect(SocketAddress remote, long delay) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Runnable task = () -> {
                try (Socket s = new Socket()) {
                    s.connect(remote);
                } catch (IOException ioe) { }
            };
            executor.schedule(task, delay, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Schedule a thread to read to EOF after a delay
     */
    static void scheduleReadToEOF(InputStream in, long delay) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Runnable task = () -> {
                byte[] bytes = new byte[8192];
                try {
                    while (in.read(bytes) != -1) { }
                } catch (IOException ioe) { }
            };
            executor.schedule(task, delay, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdown();
        }
    }

    /**
     * Schedule a thread to write after a delay
     */
    static void scheduleWrite(OutputStream out, byte[] data, long delay) {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        try {
            Runnable task = () -> {
                try {
                    out.write(data);
                } catch (IOException ioe) { }
            };
            executor.schedule(task, delay, TimeUnit.MILLISECONDS);
        } finally {
            executor.shutdown();
        }
    }
    static void scheduleWrite(OutputStream out, int b, long delay) {
        scheduleWrite(out, new byte[] { (byte)b }, delay);
    }
}
