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

import jdk.test.lib.NetworkConfiguration;
import jdk.test.lib.net.IPSupport;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.*;
import java.nio.channels.DatagramChannel;
import java.nio.channels.UnsupportedAddressTypeException;

import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;
import static java.lang.System.out;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;

/*
 * @test
 * @summary Test methods with various ProtocolFamily combinations
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 * @run testng/othervm Families
 */

public class Families {
    static Inet4Address ia4;
    static Inet6Address ia6;
    static final boolean preferIPv4 =
            parseBoolean(getProperty("java.net.preferIPv4Stack", "false"));
    static final boolean preferIPv6 =
            parseBoolean(getProperty("java.net.preferIPv6Addresses", "false"));

    @BeforeTest()
    public void setup() throws Exception {
        ia4 = getFirstLinkLocalIPv4Address();
        ia6 = getFirstLinkLocalIPv6Address();

        out.println("preferIPv4: " + preferIPv4);
        out.println("preferIPv6: " + preferIPv6);
        out.println("IPv6 supported: " + IPSupport.hasIPv6());
        out.println("ia4: " + ia4);
        out.println("ia6: " + ia6);
    }

    // DatagramChannel open   - default, INET, INET6
    // DatagramChannel bind   - INET, INET6, null

    @DataProvider(name = "dcOpenBind")
    public Object[][] dcOpenBind() {
        return new Object[][]{
                {   INET,   INET,   true   },
                {   INET,   INET6,  false  },
                {   INET,   null,   true   },
                {   INET6,  INET,   true   },
                {   INET6,  INET6,  true   },
                {   INET6,  null,   true   },
                {   null,   INET,   true   },
                {   null,   INET6,  true   },
                {   null,   null,   true   }
        };
    }

    @Test(dataProvider = "dcOpenBind")
    public void dcOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) throws Exception {
        out.println("\n");
        try (DatagramChannel sc = openDC(ofam)) {
            SocketAddress addr = getSocketAddress(bfam);
            sc.bind(addr);
            if (!expectPass) {
                throw new RuntimeException("Expected to fail");
            }
        } catch (UnsupportedAddressTypeException uate) {
            if (expectPass) {
                throw new RuntimeException("Expected to pass", uate);
            }
        }
    }

    //  DatagramChannel open    - default, INET, INET6
    //  DatagramChannel connect - default, INET, INET6

    @DataProvider(name = "dcOpenConnect")
    public Object[][] dcOpenConnect() {
        return new Object[][]{
                {   INET,   INET,   true   },
                {   INET,   INET6,  true   },
                {   INET,   null,   true   },
                {   INET6,  INET,   false  },
                {   INET6,  INET6,  true   },
                {   INET6,  null,   true   },
                {   null,   INET,   false  },
                {   null,   INET6,  true   },
                {   null,   null,   true   }
        };
    }

    @Test(dataProvider = "dcOpenConnect")
    public void dcOpenConnect(StandardProtocolFamily sfam,
                           StandardProtocolFamily cfam,
                           boolean expectPass) throws Exception {
        out.println("\n");
        try (DatagramChannel ssc = openDC(sfam)) {
            ssc.bind(null);
            SocketAddress saddr = ssc.getLocalAddress();
            try (DatagramChannel dc = openDC(cfam)) {
                dc.connect(saddr);
                if (!expectPass) {
                    throw new RuntimeException("Expected to fail");
                }
            } catch (UnsupportedAddressTypeException uate) {
                if (expectPass) {
                    throw new RuntimeException("Expected to pass", uate);
                }
            }
        }
    }

    // Helper methods

    private static DatagramChannel openDC(StandardProtocolFamily fam)
            throws IOException {
        return fam == null ? DatagramChannel.open()
                : DatagramChannel.open(fam);
    }

    private static SocketAddress getSocketAddress(StandardProtocolFamily fam) {
        return fam == null ? null : switch (fam) {
            case INET -> new InetSocketAddress(ia4, 0);
            case INET6 -> new InetSocketAddress(ia6, 0);
            default -> throw new RuntimeException("address couldn't be allocated");
        };
    }

    private static Inet4Address getFirstLinkLocalIPv4Address()
            throws Exception {
        return NetworkConfiguration.probe()
                .ip4Addresses()
                .filter(a -> !a.isLoopbackAddress())
                .findFirst()
                .orElse(null);
    }

    private static Inet6Address getFirstLinkLocalIPv6Address()
            throws Exception {
        return NetworkConfiguration.probe()
                .ip6Addresses()
                .filter(Inet6Address::isLinkLocalAddress)
                .findFirst()
                .orElse((Inet6Address) InetAddress.getByName("::0"));
    }
}
