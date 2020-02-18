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
import jdk.test.lib.Platform;
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
import static jdk.test.lib.net.IPSupport.preferIPv4Stack;
import static jdk.test.lib.net.IPSupport.hasIPv6;

/*
 * @test
 * @summary Test SocketChannel, ServerSocketChannel and DatagramChannel
 *          with various ProtocolFamily combinations
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 * @run testng/othervm ProtocolFamilies
 */

public class ProtocolFamilies {
    static final boolean isWindows = Platform.isWindows();
    static final boolean isIPv6available = hasIPv6();
    static final boolean preferIPv4 = preferIPv4Stack();
    static final boolean preferIPv6 =
            parseBoolean(getProperty("java.net.preferIPv6Addresses", "false"));
    static Inet4Address ia4;
    static Inet6Address ia6;

    @BeforeTest()
    public void setup() throws Exception {
            NetworkConfiguration.printSystemConfiguration(out);
            IPSupport.printPlatformSupport(out);
        out.println("preferIPv6Addresses: " + preferIPv6 + "\n");

        ia4 = getFirstLinkLocalIPv4Address();
        ia6 = getFirstLinkLocalIPv6Address();
        out.println("ia4: " + ia4);
        out.println("ia6: " + ia6 + "\n");
    }

    @DataProvider(name = "openBind")
    public Object[][] openBind() {
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

    // SocketChannel open   - INET, INET6, default
    // SocketChannel bind   - INET, INET6, null

    @Test(dataProvider = "openBind")
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

    //  ServerSocketChannel open    - INET, INET6, default
    //  ServerSocketChannel bind    - INET, INET6, null

    @Test(dataProvider = "openBind")
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

    //  DatagramChannel open    - INET, INET6, default
    //  DatagramChannel bind    - INET, INET6, null

    @Test(dataProvider = "openBind")
    public void dcOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) throws Exception {
        out.println("\n");
        try (DatagramChannel dc = openDC(ofam)) {
            SocketAddress addr = getSocketAddress(bfam);
            dc.bind(addr);
            if (!expectPass) {
                throw new RuntimeException("Expected to fail");
            }
        } catch (UnsupportedAddressTypeException uate) {
            if (expectPass) {
                throw new RuntimeException("Expected to pass", uate);
            }
        }
    }

    //  SocketChannel open      - INET, INET6, default
    //  SocketChannel connect   - INET, INET6, default

    @DataProvider(name = "openConnect")
    public Object[][] openConnect() {
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

    @Test(dataProvider = "openConnect")
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

    //  DatagramChannel open        - INET, INET6, default
    //  DatagramChannel connect     - INET, INET6, default

    @Test(dataProvider = "openConnect")
    public void dcOpenConnect(StandardProtocolFamily sfam,
                              StandardProtocolFamily cfam,
                              boolean expectPass) throws Exception {
        out.println("\n");
        try (DatagramChannel sdc = openDC(sfam)) {
            sdc.bind(null);
            SocketAddress saddr = sdc.getLocalAddress();
            try (DatagramChannel dc = openDC(cfam)) {
                // Cannot connect DatagramChannel to any local address on Windows
                // use loopback address in this case
                if (isWindows) {
                    dc.connect(getLoopback(sfam));
                } else {
                    dc.connect(saddr);
                }
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

    private static SocketChannel openSC(StandardProtocolFamily fam)
            throws IOException {
        return fam == null ? SocketChannel.open()
                : SocketChannel.open(fam);
    }

    private static ServerSocketChannel openSSC(StandardProtocolFamily fam)
            throws IOException {
        return fam == null ? ServerSocketChannel.open()
                : ServerSocketChannel.open(fam);
    }

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

    private static SocketAddress getLoopback(StandardProtocolFamily fam)
            throws UnknownHostException {
        if ((fam == null || fam == INET6) && isIPv6available) {
            return new InetSocketAddress(InetAddress.getByName("::1"), 0);
        }
        if (fam == INET) {
            return new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0);
        }
        throw new RuntimeException("address couldn't be allocated");
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
