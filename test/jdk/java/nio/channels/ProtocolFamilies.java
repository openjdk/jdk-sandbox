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
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;
import static java.lang.System.out;
import static java.lang.System.getProperty;
import static java.lang.Boolean.parseBoolean;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.test.lib.net.IPSupport.*;
import static org.testng.Assert.assertThrows;

/*
 * @test
 * @summary Test SocketChannel, ServerSocketChannel and DatagramChannel
 *          with various ProtocolFamily combinations
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 * @run testng/othervm ProtocolFamilies
 * @run testng/othervm -Djava.net.preferIPv4Stack=true ProtocolFamilies
 * @run testng/othervm -Djava.net.preferIPv6Addresses=true ProtocolFamilies
 */


public class ProtocolFamilies {
    static final boolean isWindows = Platform.isWindows();
    static final boolean hasIPv6 = hasIPv6();
    static final boolean preferIPv4 = preferIPv4Stack();
    static final boolean preferIPv6 =
            parseBoolean(getProperty("java.net.preferIPv6Addresses", "false"));
    static Inet4Address ia4;
    static Inet6Address ia6;

    @BeforeTest()
    public void setup() throws Exception {
        NetworkConfiguration.printSystemConfiguration(out);
        IPSupport.printPlatformSupport(out);
        out.println("preferIPv6Addresses: " + preferIPv6);
        throwSkippedExceptionIfNonOperational();

        ia4 = getFirstLinkLocalIPv4Address();
        ia6 = getFirstLinkLocalIPv6Address();
        out.println("ia4: " + ia4);
        out.println("ia6: " + ia6 + "\n");
    }

    @DataProvider(name = "openBind")
    public Object[][] openBind() {
        if (!preferIPv4 && hasIPv6) {
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
        } else {
            return new Object[][]{
                    {   INET,   INET,   true   },
                    {   INET,   INET6,  false  },
                    {   INET,   null,   true   },
                    {   INET6,  INET,   false  },
                    {   INET6,  INET6,  false  },
                    {   INET6,  null,   false  },
                    {   null,   INET,   true   },
                    {   null,   INET6,  false  },
                    {   null,   null,   true   }
            };
        }
    }

    // SocketChannel open - INET, INET6, default
    // SocketChannel bind - INET, INET6, null

    @Test(dataProvider = "openBind")
    public void scOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) {
        try (SocketChannel sc = openSC(ofam)) {
            SocketAddress addr = getSocketAddress(bfam);
            sc.bind(addr);
            throwIf(!expectPass);
        } catch (UnsupportedAddressTypeException
                | UnsupportedOperationException re) {
            throwIf(expectPass, re);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    //  ServerSocketChannel open - INET, INET6, default
    //  ServerSocketChannel bind - INET, INET6, null

    @Test(dataProvider = "openBind")
    public void sscOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) {
        try (ServerSocketChannel ssc = openSSC(ofam)) {
            SocketAddress addr = getSocketAddress(bfam);
            ssc.bind(addr);
            throwIf(!expectPass);
        } catch (UnsupportedAddressTypeException
                | UnsupportedOperationException re) {
            throwIf(expectPass, re);
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    //  DatagramChannel open - INET, INET6, default
    //  DatagramChannel bind - INET, INET6, null

    @Test(dataProvider = "openBind")
    public void dcOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) throws Exception {
        try (DatagramChannel dc = openDC(ofam)) {
            SocketAddress addr = getSocketAddress(bfam);
            dc.bind(addr);
            throwIf(!expectPass);
        } catch (UnsupportedAddressTypeException
                | UnsupportedOperationException re) {
            throwIf(expectPass, re);
        }
    }

    //  SocketChannel open    - INET, INET6, default
    //  SocketChannel connect - INET, INET6, default

    @DataProvider(name = "openConnect")
    public Object[][] openConnect() {
        if (!preferIPv4 && hasIPv6) {
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
        } else {
            return new Object[][]{
                    {   INET,   INET,   true   },
                    {   INET,   INET6,  false  },
                    {   INET,   null,   true   },
                    {   INET6,  INET,   false  },
                    {   INET6,  INET6,  false  },
                    {   INET6,  null,   false  },
                    {   null,   INET,   true   },
                    {   null,   INET6,  false  },
                    {   null,   null,   true   }
            };
        }
    }

    @Test(dataProvider = "openConnect")
    public void scOpenConnect(StandardProtocolFamily sfam,
                              StandardProtocolFamily cfam,
                              boolean expectPass) throws Exception {
        try (ServerSocketChannel ssc = openSSC(sfam)) {
            ssc.bind(null);
            SocketAddress saddr = ssc.getLocalAddress();
            try (SocketChannel sc = openSC(cfam)) {
                sc.connect(saddr);
                throwIf(!expectPass);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException re) {
                throwIf(expectPass, re);
            } catch (Throwable t) {
                throw new RuntimeException(t);
            }
        } catch (UnsupportedOperationException re) {
            throwIf(expectPass, re);
        }
    }

    //  DatagramChannel open    - INET, INET6, default
    //  DatagramChannel connect - INET, INET6, default

    @Test(dataProvider = "openConnect")
    public void dcOpenConnect(StandardProtocolFamily sfam,
                              StandardProtocolFamily cfam,
                              boolean expectPass) throws Exception {
        try (DatagramChannel sdc = openDC(sfam)) {
            sdc.bind(null);
            InetSocketAddress saddr = (InetSocketAddress)sdc.getLocalAddress();
            try (DatagramChannel dc = openDC(cfam)) {
                // Cannot connect to any local address on Windows
                // use loopback address in this case
                if (isWindows) {
                    dc.connect(getLoopback(sfam, saddr.getPort()));
                } else {
                    dc.connect(saddr);
                }
                throwIf(!expectPass);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException re) {
                throwIf(expectPass, re);
            }
        } catch (UnsupportedOperationException re) {
            throwIf(expectPass, re);
        }
    }

    static final Class<NullPointerException> NPE = NullPointerException.class;

    // Tests null handling
    @Test
    public void nulls() {
        assertThrows(NPE, () -> SocketChannel.open((ProtocolFamily)null));
        assertThrows(NPE, () -> ServerSocketChannel.open(null));
        assertThrows(NPE, () -> DatagramChannel.open(null));

        assertThrows(NPE, () -> SelectorProvider.provider().openSocketChannel(null));
        assertThrows(NPE, () -> SelectorProvider.provider().openServerSocketChannel(null));
        assertThrows(NPE, () -> SelectorProvider.provider().openDatagramChannel(null));
    }

    static final Class<UnsupportedOperationException> UOE = UnsupportedOperationException.class;
    static final ProtocolFamily STITCH = () -> "STITCH";

    // Tests UOE handling
    @Test
    public void uoe() {
        assertThrows(UOE, () -> SocketChannel.open(STITCH));
        assertThrows(UOE, () -> ServerSocketChannel.open(STITCH));
        assertThrows(UOE, () -> DatagramChannel.open(STITCH));

        assertThrows(UOE, () -> SelectorProvider.provider().openSocketChannel(STITCH));
        assertThrows(UOE, () -> SelectorProvider.provider().openServerSocketChannel(STITCH));
        assertThrows(UOE, () -> SelectorProvider.provider().openDatagramChannel(STITCH));
    }

    // A concrete subclass of SelectorProvider, in order to test implSpec
    static final SelectorProvider SELECTOR_PROVIDER = new SelectorProvider() {
        @Override public DatagramChannel openDatagramChannel() { return null; }
        @Override public DatagramChannel openDatagramChannel(ProtocolFamily family) { return null; }
        @Override public Pipe openPipe() { return null; }
        @Override public AbstractSelector openSelector() { return null; }
        @Override public ServerSocketChannel openServerSocketChannel() { return null; }
        @Override public SocketChannel openSocketChannel() { return null; }
    };

    // Tests the specified default implementation of SelectorProvider
    @Test
    public void defaultImpl() {
        assertThrows(NPE, () -> SELECTOR_PROVIDER.openSocketChannel(null));
        assertThrows(NPE, () -> SELECTOR_PROVIDER.openServerSocketChannel(null));

        assertThrows(UOE, () -> SELECTOR_PROVIDER.openSocketChannel(STITCH));
        assertThrows(UOE, () -> SELECTOR_PROVIDER.openServerSocketChannel(STITCH));
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
            default -> throw new RuntimeException("Unexpected protocol family");
        };
    }

    private static SocketAddress getLoopback(StandardProtocolFamily fam, int port)
            throws UnknownHostException {
        if ((fam == null || fam == INET6) && hasIPv6) {
            return new InetSocketAddress(InetAddress.getByName("::1"), port);
        } else {
            return new InetSocketAddress(InetAddress.getByName("127.0.0.1"), port);
        }
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

    private static void throwIf(boolean condition, RuntimeException... re) {
        if (condition && re.length > 0) {
            throw new RuntimeException("Expected to pass", re[0]);
        }
        if (condition) {
            throw new RuntimeException("Expected to fail");
        }
    }
}
