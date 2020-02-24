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
 * @summary Test no-arg open method
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 * @run testng/othervm OpenDefault
 * @run testng/othervm -Djava.net.preferIPv4Stack=true OpenDefault
 * @run testng/othervm -Djava.net.preferIPv6Addresses=true OpenDefault
 */

import jdk.test.lib.NetworkConfiguration;
import jdk.test.lib.net.IPSupport;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;
import static java.lang.System.out;
import static jdk.test.lib.Asserts.assertEquals;
import static jdk.test.lib.Asserts.assertTrue;
import static jdk.test.lib.net.IPSupport.*;

public class OpenDefault {
    static final boolean preferIPv6 =
            parseBoolean(getProperty("java.net.preferIPv6Addresses", "false"));

    @BeforeTest()
    public void setup() {
        IPSupport.printPlatformSupport(out);
        out.printf("preferIPv6Addresses: %b\n", preferIPv6);
        throwSkippedExceptionIfNonOperational();
    }

    @DataProvider(name = "addresses")
    public static Iterator<Object[]> addresses() throws Exception {
        return Stream.concat(
                Stream.of(
                        new Object[]{new InetSocketAddress(InetAddress.getLoopbackAddress(), 0)},
                        new Object[]{new InetSocketAddress(InetAddress.getLocalHost(), 0)}),
                Stream.concat(
                        NetworkConfiguration.probe()
                                .ip4Addresses()
                                .map(a -> new Object[]{new InetSocketAddress(a, 0)}),
                        NetworkConfiguration.probe()
                                .ip6Addresses()
                                .map(a -> new Object[]{new InetSocketAddress(a, 0)})))
                .collect(Collectors.toUnmodifiableList())
                .iterator();
    }

    @Test(dataProvider = "addresses")
    public static void openSC(InetSocketAddress addr)
            throws Exception {
        try (var c = SocketChannel.open()) {
            Class<? extends InetAddress> cls = addr.getAddress().getClass();
            InetAddress ia = c.bind(addr).socket().getLocalAddress();
            assertEquals(ia.getClass(), cls);
        }
    }

    @Test(dataProvider = "addresses")
    public static void openSSC(InetSocketAddress addr)
            throws Exception {
        try (var c = ServerSocketChannel.open()) {
            Class<? extends InetAddress> cls = addr.getAddress().getClass();
            InetAddress ia = c.bind(addr).socket().getInetAddress();
            assertEquals(ia.getClass(), cls);
        }
    }

    @Test(dataProvider = "addresses")
    public static void openDC(InetSocketAddress addr)
            throws Exception {
        try (var c = DatagramChannel.open()) {
            Class<? extends InetAddress> cls = addr.getAddress().getClass();
            InetAddress ia = c.bind(addr).socket().getLocalAddress();
            assertEquals(ia.getClass(), cls);
        }
    }
}
