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

/*
 * @test
 * @bug 8195160
 * @summary Checks the RDMA selector provider API methods
 * @requires (os.family == "linux")
 * @library /test/lib
 * @build RsocketTest
 * @run testng/othervm RdmaSelectorProvider
 */

import java.io.IOException;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.nio.channels.NetworkChannel;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_READ;
import static jdk.net.RdmaSockets.*;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.fail;

public class RdmaSelectorProvider {

    boolean ipv6Supported;  // false

    @BeforeTest
    public void setup() throws Exception {
        if (!RsocketTest.isRsocketAvailable())
            throw new SkipException("rsocket is not available");

        try {
            openSocketChannel(INET6);
            ipv6Supported = true;
        } catch (UnsupportedOperationException x) {  }

        System.out.println("INET6 rsockets supported: " + ipv6Supported);
    }

    @DataProvider(name = "families")
    public Object[][] families() {
        if (ipv6Supported)
            return new Object[][] { { INET }, { INET6 } };
        else
            return new Object[][] { { INET } };
    }

    static final Class<UnsupportedOperationException> UOE = UnsupportedOperationException.class;

    void assertProtocal(NetworkChannel channel) throws IOException {
        channel.bind(null);
        InetSocketAddress isa = (InetSocketAddress)(channel.getLocalAddress());
        if (ipv6Supported && !(isa.getAddress() instanceof Inet6Address))
            fail("Expected INET6 family for " + channel);
        else if (!ipv6Supported && !(isa.getAddress() instanceof Inet4Address))
            fail("Expected INET family for " + channel);

    }
    @Test(dataProvider = "families")
    public void testSocketChannel(ProtocolFamily family)
        throws IOException
    {
        try (SocketChannel sc = openSocketChannel(family)) {
            assertThrows(UOE, () -> sc.provider().openPipe());
            assertThrows(UOE, () -> sc.provider().openDatagramChannel());
            assertThrows(UOE, () -> sc.provider().openDatagramChannel(INET));
            if (ipv6Supported)
                assertThrows(UOE, () -> sc.provider().openDatagramChannel(INET6));

            try (var selector = sc.provider().openSelector()) {
                assertNotEquals(selector, null);
                sc.configureBlocking(false);
                sc.register(selector, OP_READ);  // ensures RDMA channel registration
            }

            try (var channel = sc.provider().openSocketChannel()) {
                assertNotEquals(channel, null);
                assertProtocal(channel);
            }

            try (var channel = sc.provider().openServerSocketChannel()) {
                assertNotEquals(channel, null);
                assertProtocal(channel);
            }
        }
    }

    @Test(dataProvider = "families")
    public void testServerSocketChannel(ProtocolFamily family)
        throws IOException
    {
        try (ServerSocketChannel ssc = openServerSocketChannel(family)) {
            assertThrows(UOE, () -> ssc.provider().openPipe());
            assertThrows(UOE, () -> ssc.provider().openDatagramChannel());
            assertThrows(UOE, () -> ssc.provider().openDatagramChannel(INET));
            if (ipv6Supported)
                assertThrows(UOE, () -> ssc.provider().openDatagramChannel(INET6));

            try (var selector = ssc.provider().openSelector()) {
                assertNotEquals(selector, null);
                ssc.configureBlocking(false);
                ssc.register(selector, OP_ACCEPT);  // ensures RDMA channel registration
            }

            try (var channel = ssc.provider().openSocketChannel()) {
                assertNotEquals(channel, null);
                assertProtocal(channel);
            }

            try (var channel = ssc.provider().openServerSocketChannel()) {
                assertNotEquals(channel, null);
                assertProtocal(channel);
            }
        }
    }

    @Test
    public void testSelector() throws IOException {
        try (Selector selector = openSelector()) {
            assertThrows(UOE, () -> selector.provider().openPipe());
            assertThrows(UOE, () -> selector.provider().openDatagramChannel());
            assertThrows(UOE, () -> selector.provider().openDatagramChannel(INET));
            if (ipv6Supported)
                assertThrows(UOE, () -> selector.provider().openDatagramChannel(INET6));

            try (var selector1 = selector.provider().openSelector()) {
                assertNotEquals(selector1, null);
                SocketChannel sc = openSocketChannel(INET);
                sc.configureBlocking(false);
                sc.register(selector, OP_READ);  // ensures RDMA channel registration
            }

            try (var channel = selector.provider().openSocketChannel()) {
                assertNotEquals(channel, null);
                assertProtocal(channel);
            }

            try (var channel = selector.provider().openServerSocketChannel()) {
                assertNotEquals(channel, null);
                assertProtocal(channel);
            }
        }
    }
}
