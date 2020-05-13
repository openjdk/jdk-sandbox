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
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.*;
import java.nio.channels.*;
import java.time.Duration;
import java.util.function.Predicate;

import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;
import static java.lang.System.out;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.test.lib.net.IPSupport.*;

/*
 * @test
 * @summary Test SocketChannel, ServerSocketChannel and DatagramChannel
 *          open() and connect(), taking into consideration combinations of
 *          protocol families (INET, INET6, default),
 *          addresses (Inet4Address, Inet6Address),
 *          platforms (Linux, Mac OS, Windows),
 *          and the system properties preferIPv4Stack and preferIPv6Addresses.
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 * @run testng/othervm OpenAndConnect
 */


public class OpenAndConnect {
    static final Inet4Address IA4ANYLOCAL;
    static final Inet6Address IA6ANYLOCAL;
    static final Inet4Address IA4LOOPBACK;
    static final Inet6Address IA6LOOPBACK;
    static Inet4Address IA4LOCAL;
    static Inet6Address IA6LOCAL;
    static Inet4Address NO_IA4LOCAL;
    static Inet6Address NO_IA6LOCAL;
    static InetAddress DONT_BIND;

    static {
        try {
            IA4ANYLOCAL = (Inet4Address) InetAddress.getByName("0.0.0.0");
            IA6ANYLOCAL = (Inet6Address) InetAddress.getByName("::0");
            IA4LOOPBACK = (Inet4Address) InetAddress.getByName("127.0.0.1");
            IA6LOOPBACK = (Inet6Address) InetAddress.getByName("::1");

            // Special values used as placeholders when local IPv4/IPv6 address
            // cannot be found (addresses are not used)
            NO_IA4LOCAL = (Inet4Address) InetAddress.getByName("127.0.0.2");
            NO_IA6LOCAL = (Inet6Address) InetAddress.getByName("ff00::");

            // Special value to tell test not to call bind (address is not used)
            DONT_BIND = (Inet4Address) InetAddress.getByName("127.0.0.3");

            initAddrs();
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize addresses", e);
        }
    }

    @BeforeTest()
    public void setup() {
        NetworkConfiguration.printSystemConfiguration(out);
        IPSupport.printPlatformSupport(out);
        throwSkippedExceptionIfNonOperational();

        out.println("IA4LOCAL:    " + (IA4LOCAL == NO_IA4LOCAL
                                       ? "NO_IA4LOCAL"
                                       : IA4LOCAL));
        out.println("IA6LOCAL:    " + (IA6LOCAL == NO_IA6LOCAL
                                       ? "NO_IA6LOCAL"
                                       : IA6LOCAL));
        out.println("IA4ANYLOCAL: " + IA4ANYLOCAL);
        out.println("IA6ANYLOCAL: " + IA6ANYLOCAL);
        out.println("IA4LOOPBACK: " + IA4LOOPBACK);
        out.println("IA6LOOPBACK: " + IA6LOOPBACK);
    }

    @DataProvider(name = "openConnect")
    public Object[][] openConnect() {
        return new Object[][]{
            //       +----- sfam is server/first socket family
            //       |
            //       |       +------ saddr is bind address for server/first socket
            //       |       |
            //       |       |              +---- cfam is family for client/second socket
            //       |       |              |
            //       |       |              |        +---- caddr is address client/second
            //       |       |              |        |     socket binds to
            //       |       |              |        |
            //       |       |              |        |            +--- if saddr is wildcard
            //       |       |              |        |            |    and test is datagram
            //       |       |              |        |            |    channel, then client
            //       |       |              |        |            |    connects to this.
            //       |       |              |        |            |    Otherwise, client
            //       |       |              |        |            |    connects to saddr.
            //       +       +              +        +            +
            //  {   sfam,   saddr,         cfam,    caddr,        alternate }

                {   INET,   IA4LOOPBACK,   INET,    IA4LOOPBACK  , null        },
                {   INET,   IA4LOCAL,      INET,    IA4LOCAL     , null        },
                {   INET,   IA4LOOPBACK,   null,    IA4LOOPBACK  , null        },
                {   INET,   IA4LOCAL,      null,    IA4LOCAL     , null        },
                {   INET,   IA4LOCAL,      null,    DONT_BIND    , null        },

                {   INET,   IA4ANYLOCAL,   null,    IA4LOCAL     , IA4LOCAL    },
                {   INET,   IA4ANYLOCAL,   null,    IA4LOOPBACK  , IA4LOOPBACK },
                {   INET,   IA4ANYLOCAL,   null,    IA4LOOPBACK  , IA4LOCAL    },
                {   INET,   IA4ANYLOCAL,   INET,    IA4LOCAL     , IA4LOOPBACK },
                {   INET,   IA4ANYLOCAL,   INET,    IA4LOOPBACK  , IA4LOCAL    },

                {   INET6,  IA6ANYLOCAL,   null,    IA6LOCAL     , IA6LOCAL    },
                {   INET6,  IA6ANYLOCAL,   null,    IA6LOOPBACK  , IA4LOOPBACK },
                {   INET6,  IA6ANYLOCAL,   null,    IA6LOOPBACK  , IA6LOCAL    },
                {   INET6,  IA6ANYLOCAL,   INET6,   IA6LOCAL     , IA6LOCAL    },
                {   INET6,  IA6ANYLOCAL,   INET6,   IA6LOOPBACK  , IA4LOOPBACK },

                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK , null        },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL    , null        },
                {   INET6,   IA6LOCAL,      null,    IA6LOCAL    , null        },
                {   INET6,   IA6LOCAL,      null,    DONT_BIND   , null        },

                {   null,   IA4LOOPBACK,   INET,    IA4ANYLOCAL  , null        },
                {   null,   IA4LOCAL,      INET,    IA4ANYLOCAL  , null        },

                {   null,   IA4LOOPBACK,   INET,    IA4LOOPBACK  , null        },

                {   null,   IA4LOCAL,      INET,    IA4LOCAL     , null        },

                {   null,   IA4LOOPBACK,   INET,    null         , null        },
                {   null,   IA4LOCAL,      INET,    null         , null        },


                {   null,   IA4LOOPBACK,   INET6,   IA6ANYLOCAL  , null        },
                {   null,   IA4LOCAL,      INET6,   IA6ANYLOCAL  , null        },
                {   null,   IA6LOOPBACK,   INET6,   IA6ANYLOCAL  , null        },
                {   null,   IA6LOCAL,      INET6,   IA6ANYLOCAL  , null        },

                {   null,   IA6LOOPBACK,   INET6,   IA6LOOPBACK  , null        },
                {   null,   IA6LOOPBACK,   INET6,   DONT_BIND    , null        },
                {   null,   IA4LOOPBACK,   INET6,   DONT_BIND    , null        },

                {   null,   IA6LOCAL,      INET6,   IA6LOCAL     , null        },

                {   null,   IA4LOOPBACK,   INET6,   null         , null        },
                {   null,   IA4LOCAL,      INET6,   null         , null        },
                {   null,   IA6LOOPBACK,   INET6,   null         , null        },
                {   null,   IA6LOCAL,      INET6,   null         , null        },

                {   null,   IA4LOOPBACK,   null,    IA6ANYLOCAL  , null        },
                {   null,   IA4LOCAL,      null,    IA6ANYLOCAL  , null        },
                {   null,   IA6LOOPBACK,   null,    IA6ANYLOCAL  , null        },
                {   null,   IA6LOCAL,      null,    IA6ANYLOCAL  , null        },

                {   null,   IA6LOOPBACK,   null,    IA6LOOPBACK  , null        },

                {   null,   IA6LOCAL,      null,    IA6LOCAL     , null        },

                {   null,   IA4LOOPBACK,   null,    null         , null        },
                {   null,   IA4LOCAL,      null,    null         , null        },
                {   null,   IA6LOOPBACK,   null,    null         , null        },
                {   null,   IA6LOCAL,      null,    null         , null        },

                {   null,   IA6ANYLOCAL,   null,    IA6LOCAL     , IA6LOOPBACK },
                {   null,   IA6ANYLOCAL,   null,    IA6LOOPBACK  , IA6LOOPBACK },
                {   null,   IA6ANYLOCAL,   null,    IA6LOOPBACK  , IA6LOCAL    },
                {   null,   IA6ANYLOCAL,   INET6,   IA6LOCAL     , IA6LOCAL    },
                {   null,   IA6ANYLOCAL,   INET6,   IA6LOOPBACK  , IA6LOCAL    },

                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK , null        },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL    , null     }
        };
    }

    static boolean ignoreTest(InetAddress addr1, InetAddress addr2) {
        return (addr1 == NO_IA4LOCAL || addr1 == NO_IA6LOCAL || addr2 == NO_IA4LOCAL
                                     || addr2 == NO_IA6LOCAL);
    }

    /**
     * If the destination address is the wildcard, it is replaced by the alternate
     * using the port number from destination. Otherwise destination is returned.
     * Only used by dcOpenAndConnect
     */
    static InetSocketAddress getDestinationAddress(SocketAddress destination, InetAddress alternate) {
        InetSocketAddress isa = (InetSocketAddress)destination;
        if (isa.getAddress().isAnyLocalAddress())
            return new InetSocketAddress(alternate, isa.getPort());
        else
            return isa;
    }

    @Test(dataProvider = "openConnect")
    public void scOpenAndConnect(ProtocolFamily sfam,
                                 InetAddress saddr,
                                 ProtocolFamily cfam,
                                 InetAddress caddr,
                                 InetAddress ignored)
    {
        if (ignoreTest(saddr, caddr))
            // mark test as skipped
            throw new SkipException("can't run due to configuration");

        out.printf("scOpenAndConnect: server bind: %s client bind: %s\n", saddr, caddr);
        try (ServerSocketChannel ssc = openSSC(sfam)) {
            ssc.bind(getSocketAddress(saddr));
            InetSocketAddress ssa = (InetSocketAddress)ssc.getLocalAddress();
            out.println(ssa);
            try (SocketChannel csc = openSC(cfam)) {
                InetSocketAddress csa = (InetSocketAddress)getSocketAddress(caddr);
                out.printf("Connecting to:  %s/port: %d\n", ssa.getAddress(), ssa.getPort());
                if (caddr != DONT_BIND) {
                    csc.bind(csa);
                }
                csc.connect(ssa);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException e) {
                error(e);
            }
        } catch (UnsupportedOperationException | IOException e) {
            error(e);
        }
    }

    @Test(dataProvider = "openConnect")
    public void dcOpenAndConnect(ProtocolFamily sfam,
                                 InetAddress saddr,
                                 ProtocolFamily cfam,
                                 InetAddress caddr,
                                 InetAddress alternate)
    {
        if (ignoreTest(saddr, caddr))
            // mark test as skipped
            throw new SkipException("can't run due to configuration");

        try (DatagramChannel sdc = openDC(sfam)) {
            sdc.bind(getSocketAddress(saddr));
            SocketAddress ssa = sdc.socket().getLocalSocketAddress();
            ssa = getDestinationAddress(ssa, alternate);
            out.println(ssa);
            try (DatagramChannel dc = openDC(cfam)) {
                SocketAddress csa = getSocketAddress(caddr);
                if (caddr != DONT_BIND) {
                    dc.bind(csa);
                }
                dc.connect(ssa);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException e) {
                error(e);
            }
        } catch (UnsupportedOperationException | IOException e) {
            error(e);
        }
    }

    // Helper methods

    private static SocketChannel openSC(ProtocolFamily fam) throws IOException {
        return fam == null ? SocketChannel.open() : SocketChannel.open(fam);
    }

    private static ServerSocketChannel openSSC(ProtocolFamily fam)
            throws IOException {
        return fam == null ? ServerSocketChannel.open()
                : ServerSocketChannel.open(fam);
    }

    private static DatagramChannel openDC(ProtocolFamily fam)
            throws IOException {
        return fam == null ? DatagramChannel.open()
                : DatagramChannel.open(fam);
    }

    private static SocketAddress getSocketAddress(InetAddress ia) {
        return ia == null ? null : new InetSocketAddress(ia, 0);
    }

    private static boolean isNotLoopback(NetworkInterface nif) {
        try {
            return !nif.isLoopback();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void initAddrs() throws IOException {

        NetworkConfiguration cfg = NetworkConfiguration.probe();

        NetworkInterface iface4 = cfg.ip4Interfaces()
                .filter(nif -> isNotLoopback(nif))
                .findFirst()
                .orElse(null);

        NetworkInterface iface6 = cfg.ip6Interfaces()
                .filter(nif -> isNotLoopback(nif))
                .findFirst()
                .orElse(null);

        if (iface6 != null) {
            IA6LOCAL = (Inet6Address)iface6.inetAddresses()
                .filter(a -> a instanceof Inet6Address)
                .filter(a -> !a.isLoopbackAddress())
                .filter(a -> !a.isLinkLocalAddress())
                .findFirst()
                .orElse(NO_IA6LOCAL);
        } else {
            IA6LOCAL = NO_IA6LOCAL;
        }

        if (iface4 != null) {
            IA4LOCAL = (Inet4Address)iface4.inetAddresses()
                .filter(a -> a instanceof Inet4Address)
                .filter(a -> !a.isLoopbackAddress())
                .findFirst()
                .orElse(NO_IA4LOCAL);
        } else {
            IA4LOCAL = NO_IA4LOCAL;
        }
    }

    static boolean isUp(NetworkInterface nif) {
        try {
            return nif.isUp();
        } catch (SocketException se) {
            throw new RuntimeException(se);
        }
    }

    private static void error(Exception e) {
        throw new RuntimeException("Expected to pass", e);
    }
}
