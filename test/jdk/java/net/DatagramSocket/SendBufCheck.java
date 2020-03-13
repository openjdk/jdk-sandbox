/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @bug 8239355
 * @summary Check that new SO_SNDBUF limit on macOS is adhered to
 * @library /test/lib
 * @build jdk.test.lib.net.IPSupport
 * @requires os.family == "mac"
 * @run testng/othervm SendBufCheck
 * @run testng/othervm -Djava.net.preferIPv4Stack=true SendBufCheck
 * @run testng/othervm -Djava.net.preferIPv6Addresses=true SendBufCheck
 * @run testng/othervm -Djdk.net.usePlainDatagramSocketImpl=true SendBufCheck
 * @run testng/othervm -Djdk.net.usePlainDatagramSocketImpl=true
 *      -Djava.net.preferIPv4Stack=true SendBufCheck
 * @run testng/othervm -Djdk.net.usePlainDatagramSocketImpl=true
 *      -Djava.net.preferIPv6Addresses=true SendBufCheck
 */

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import jdk.test.lib.net.IPSupport;

import static jdk.test.lib.net.IPSupport.hasIPv6;
import static jdk.test.lib.net.IPSupport.preferIPv6Addresses;
import static org.testng.Assert.expectThrows;

public class SendBufCheck {
    private int SOCKET_SNDBUF, CHANNEL_SNDBUF;
    private Sender datagramSocket, multicastSocket, datagramChannel,
            datagramSocketAdaptor;

    private final static int IPV4_SNDBUF = 65507;
    private final static int IPV6_SNDBUF = 65527;
    private final static Class<IOException> IOE = IOException.class;

    public boolean isPropertyEnabled(String property) {
        String impl = System.getProperty(property);
        if ((impl != null) && (impl.isEmpty()
                || impl.equalsIgnoreCase("true"))) {
            return true;
        }
        return false;
    }

    @BeforeTest
    public void setUp() throws IOException {
        IPSupport.throwSkippedExceptionIfNonOperational();

        datagramSocket = Sender.of(new DatagramSocket());
        multicastSocket = Sender.of(new MulticastSocket());
        datagramChannel = Sender.of(DatagramChannel.open());
        datagramSocketAdaptor = Sender.of(DatagramChannel.open().socket());

        int BUF_LIMIT = preferIPv6Addresses() && hasIPv6()
                ? IPV6_SNDBUF : IPV4_SNDBUF;

        SOCKET_SNDBUF = isPropertyEnabled("jdk.net.usePlainDatagramSocketImpl")
             ? IPV4_SNDBUF : BUF_LIMIT;

        CHANNEL_SNDBUF = BUF_LIMIT;
    }

    @DataProvider(name = "sendBufferLimits")
    public Object[][] providerIO() {
        return new Object[][]{
                { datagramSocket,        SOCKET_SNDBUF - 1,    null },
                { datagramSocket,        SOCKET_SNDBUF,        null },
                { datagramSocket,        SOCKET_SNDBUF + 1,    IOE  },
                { multicastSocket,       SOCKET_SNDBUF - 1,    null },
                { multicastSocket,       SOCKET_SNDBUF,        null },
                { multicastSocket,       SOCKET_SNDBUF + 1,    IOE  },
                { datagramChannel,       CHANNEL_SNDBUF - 1,   null },
                { datagramChannel,       CHANNEL_SNDBUF,       null },
                { datagramChannel,       CHANNEL_SNDBUF + 1,   IOE  },
                { datagramSocketAdaptor, CHANNEL_SNDBUF - 1,   null },
                { datagramSocketAdaptor, CHANNEL_SNDBUF,       null },
                { datagramSocketAdaptor, CHANNEL_SNDBUF + 1,   IOE  },
        };
    }

    @Test(dataProvider = "sendBufferLimits")
    public void sendBufferOptionCheck(Sender sender,
                                      int capacity,
                                      Class<? extends Throwable> exception)
            throws Exception {
        var receiver = new DatagramSocket(0,
                InetAddress.getLoopbackAddress());
        var pkt = new DatagramPacket(new byte[capacity], capacity,
                receiver.getLocalSocketAddress());

        if (exception != null) {
            expectThrows(exception, () -> sender.send(pkt));
        } else {
            sender.send(pkt);
        }
    }

    interface Sender<E extends Exception> extends AutoCloseable {
        void send(DatagramPacket p) throws E;

        void close() throws E;

        static Sender<IOException> of(DatagramSocket socket) {
            return new SenderImpl<>(socket, socket::send, socket::close);
        }

        static Sender<IOException> of(MulticastSocket socket, byte ttl) {
            SenderImpl.Send<IOException> send =
                    (pkt) -> socket.send(pkt, ttl);
            return new SenderImpl<>(socket, send, socket::close);
        }

        static Sender<IOException> of(DatagramChannel socket) {
            SenderImpl.Send<IOException> send =
                    (pkt) -> socket.send(ByteBuffer.allocate(pkt.getData().length),
                            pkt.getSocketAddress());
            return new SenderImpl<>(socket, send, socket::close);
        }
    }

    static final class SenderImpl<E extends Exception> implements Sender<E> {
        @FunctionalInterface
        interface Send<E extends Exception> {
            void send(DatagramPacket p) throws E;
        }

        @FunctionalInterface
        interface Closer<E extends Exception> {
            void close() throws E;
        }

        private final Send<E> send;
        private final Closer<E> closer;
        private final Object socket;

        public SenderImpl(Object socket, Send<E> send, Closer<E> closer) {
            this.socket = socket;
            this.send = send;
            this.closer = closer;
        }

        @Override
        public void send(DatagramPacket p) throws E {
            send.send(p);
        }

        @Override
        public void close() throws E {
            closer.close();
        }

        @Override
        public String toString() {
            return socket.getClass().getSimpleName();
        }

    }

    @AfterTest
    public void tearDown() throws Exception {
        datagramSocket.close();
        multicastSocket.close();
        datagramChannel.close();
        datagramSocketAdaptor.close();
    }
}
