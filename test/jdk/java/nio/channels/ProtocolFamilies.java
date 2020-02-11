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
import java.nio.channels.*;

import static java.lang.System.out;
import static java.lang.System.getProperty;
import static java.lang.Boolean.parseBoolean;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;

/*
 * @test
 * @summary Test methods with various ProtocolFamily combinations
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 * @run testng/othervm ProtocolFamilies
 */

public class ProtocolFamilies {
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
    }

    // SocketChannel open   - Dualstack, INET, INET6
    // SocketChannel bind   - INET, INET6, null

    @DataProvider(name = "scOpenBind")
    public Object[][] scOpenBind() {
        return new Object[][]{
                {   INET,   INET,   true   },
                {   INET,   INET6,  false  },
                {   INET,   null,   true   },
                {   INET6,  INET,   false  },
                {   INET6,  INET6,  true   },
                {   INET6,  null,   true   },
                {   null,   INET,   true   },
                {   null,   INET6,  true   },
                {   null,   null,   true   }
        };
    }

    @Test(dataProvider = "scOpenBind")
    public void scOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) {
        out.println("\n");
        try (SocketChannel sc = openSC(ofam)) {
            SocketAddress addr = getSocketAddress(bfam);
            sc.bind(addr);
            if (!expectPass) {
                throw new RuntimeException("Expected to fail");
            }
        } catch (UnsupportedAddressTypeException uate) {
            if (expectPass) {
                throw new RuntimeException("Expected to pass", uate);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    //  SocketChannel open    - Dualstack, INET, INET6
    //  SocketChannel connect - Dualstack, INET, INET6

    @DataProvider(name = "scOpenConnect")
    public Object[][] scOpenConnect() {
        return new Object[][]{
                {   INET,   INET,   true   },
                {   INET,   INET6,  false  },
                {   INET,   null,   true   },
                {   INET6,  INET,   false  },
                {   INET6,  INET6,  false  },
                {   INET6,  null,   false  },
                {   null,   INET,   false  },
                {   null,   INET6,  false  },
                {   null,   null,   true   }
        };
    }

    @Test(dataProvider = "scOpenConnect")
    public void scOpenConnect(StandardProtocolFamily sfam,
                           StandardProtocolFamily cfam,
                           boolean expectPass) throws Exception {
        out.println("\n");
        try (ServerSocketChannel ssc = openSSC(sfam)) {
            ssc.bind(null);
            SocketAddress saddr = ssc.getLocalAddress();

            try (SocketChannel sc = openSC(cfam)) {
                sc.connect(saddr);
                if (!expectPass) {
                    throw new RuntimeException("Expected to fail");
                }
            } catch (UnsupportedAddressTypeException uate) {
                if (expectPass) {
                    throw new RuntimeException("Expected to pass", uate);
                }
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        }
    }

    //  ServerSocketChannel open    - Dualstack, INET, INET6
    //  ServerSocketChannel bind    - INET, INET6, null

    @DataProvider(name = "sscOpenBind")
    public Object[][] sscOpenBind() {
        return new Object[][]{
                {   INET,   INET,   true   },
                {   INET,   INET6,  false  },
                {   INET,   null,   true   },
                {   INET6,  INET,   false  },
                {   INET6,  INET6,  true   },
                {   INET6,  null,   true   },
                {   null,   INET,   true   },
                {   null,   INET6,  true   },
                {   null,   null,   true   }
        };
    }

    @Test(dataProvider = "sscOpenBind")
    public void sscOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) {
        out.println("\n");
        try (ServerSocketChannel ssc = openSSC(ofam)) {
            SocketAddress addr = getSocketAddress(bfam);
            ssc.bind(addr);
            if (!expectPass) {
                throw new RuntimeException("Expected to fail");
            }
        } catch (UnsupportedAddressTypeException uate) {
            if (expectPass) {
                throw new RuntimeException("Expected to pass", uate);
            }
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Helper methods

    static SocketChannel openSC(StandardProtocolFamily fam)
            throws IOException {
        return fam == null ? SocketChannel.open()
                : SocketChannel.open(fam);
    }

    static ServerSocketChannel openSSC(StandardProtocolFamily fam)
            throws IOException {
        return fam == null ? ServerSocketChannel.open()
                : ServerSocketChannel.open(fam);
    }

    public static SocketAddress getSocketAddress(StandardProtocolFamily fam) {
        return fam == null ? null : switch (fam) {
            case INET -> new InetSocketAddress(ia4, 0);
            case INET6 -> new InetSocketAddress(ia6, 0);
            default -> throw new RuntimeException("address couldn't be allocated");
        };
    }

    public static Inet4Address getFirstLinkLocalIPv4Address()
            throws Exception {
        return NetworkConfiguration.probe()
                .ip4Addresses()
                .filter(a -> !a.isLoopbackAddress())
                .findFirst()
                .orElse(null);
    }

    public static Inet6Address getFirstLinkLocalIPv6Address()
            throws Exception {
        return NetworkConfiguration.probe()
                .ip6Addresses()
                .filter(Inet6Address::isLinkLocalAddress)
                .findFirst()
                .orElse(null);
    }
}
