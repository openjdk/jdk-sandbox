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
    static final boolean PREFERIPV4 = preferIPv4Stack();
    static final boolean PREFERIPV6 =
            parseBoolean(getProperty("java.net.preferIPv6Addresses", "false"));
    static final Inet4Address IA4ANYLOCAL;
    static final Inet6Address IA6ANYLOCAL;
    static final Inet4Address IA4LOOPBACK;
    static final Inet6Address IA6LOOPBACK;
    static final Predicate<InetAddress> IPV6LINKLOCALADDR = a ->
            (a instanceof Inet6Address) && a.isLinkLocalAddress();
    static Inet4Address IA4LOCAL;
    static Inet6Address IA6LOCAL;

    static {
        try {
            initAddrs();
            IA4ANYLOCAL = (Inet4Address) InetAddress.getByName("0.0.0.0");
            IA6ANYLOCAL = (Inet6Address) InetAddress.getByName("::0");
            IA4LOOPBACK = (Inet4Address) InetAddress.getByName("127.0.0.1");
            IA6LOOPBACK = (Inet6Address) InetAddress.getByName("::1");
        } catch (Exception e) {
            throw new RuntimeException("Could not initialize addresses", e);
        }
    }

    @BeforeTest()
    public void setup() {
        NetworkConfiguration.printSystemConfiguration(out);
        IPSupport.printPlatformSupport(out);
        out.println("preferIPv6Addresses: " + PREFERIPV6);
        throwSkippedExceptionIfNonOperational();

        out.println("IA4LOCAL:    " + IA4LOCAL);
        out.println("IA6LOCAL:    " + IA6LOCAL);
        out.println("IA4ANYLOCAL: " + IA4ANYLOCAL);
        out.println("IA6ANYLOCAL: " + IA6ANYLOCAL);
        out.println("IA4LOOPBACK: " + IA4LOOPBACK);
        out.println("IA6LOOPBACK: " + IA6LOOPBACK);
    }

    // Platform bits: run test on platforms specified only
    static final int L = 1; // Linux
    static final int W = 2; // Windows
    static final int M = 4; // Mac OS

    static final int ALL = L|M|W;
    static final int ML = M|L; // Mac OS & Linux

    static final boolean passOnThisPlatform(int mask) {
        if (Platform.isLinux())
            return (mask & L) != 0;
        if (Platform.isWindows())
            return (mask & W) != 0;
        if (Platform.isOSX())
            return (mask & M) != 0;
        return false;
    }

    @DataProvider(name = "openConnect")
    public Object[][] openConnect() {
        return new Object[][]{
            //                                                        Run if set      Should
            //                                                     ipv4     ipv6      pass on
            //  {   sfam,   saddr,         cfam,    caddr,         only,    addrs,   DG    SC     }

                {   INET,   IA4LOOPBACK,   INET,    IA4LOOPBACK,   false,   false,   ALL,  ALL    },
                {   INET,   IA4LOCAL,      INET,    IA4LOCAL,      false,   false,   ALL,  ALL    },
                {   INET,   IA4LOOPBACK,   null,    IA4LOOPBACK,   false,   false,   ALL,  ALL    },
                {   INET,   IA4LOCAL,      null,    IA4LOCAL,      false,   false,   ALL,  ALL    },

                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   false,   false,  ALL,  ALL    },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL,      false,   false,  ALL,  ALL    },
                {   INET6,   IA6LOCAL,      null,    IA6LOCAL,      false,   false,  ALL,  ALL    },

                {   null,   IA4LOOPBACK,   INET,    IA4ANYLOCAL,    false,   false,  ALL,  ALL    },
                {   null,   IA4LOCAL,      INET,    IA4ANYLOCAL,    false,   false,  ALL,  ALL    },
                {   null,   IA6LOOPBACK,   INET,    IA4ANYLOCAL,    false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOCAL,      INET,    IA4ANYLOCAL,    false,   false,  ~ALL, ~ALL   },

                {   null,   IA4LOOPBACK,   INET,    IA4LOOPBACK,    false,   false,  ALL,  ALL    },
                {   null,   IA6LOOPBACK,   INET,    IA4LOOPBACK,    false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOCAL,      INET,    IA4LOOPBACK,    false,   false,  ~ALL, ~ALL   },

                {   null,   IA4LOCAL,      INET,    IA4LOCAL,       false,   false,  ALL,  ALL    },
                {   null,   IA6LOOPBACK,   INET,    IA4LOCAL,       false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOCAL,      INET,    IA4LOCAL,       false,   false,  ~ALL, ~ALL   },

                {   null,   IA4LOOPBACK,   INET,    null,           false,   false,  ALL,  ALL    },
                {   null,   IA4LOCAL,      INET,    null,           false,   false,  ALL,  ALL    },
                {   null,   IA6LOOPBACK,   INET,    null,           false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOCAL,      INET,    null,           false,   false,  ~ALL, ~ALL   },


                {   null,   IA4LOOPBACK,   INET6,   IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA4LOCAL,      INET6,   IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA6LOOPBACK,   INET6,   IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      INET6,   IA6ANYLOCAL,   false,   false,   ALL,  ALL    },

                {   null,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   false,   false,   ALL,  ALL    },

                {   null,   IA6LOCAL,      INET6,   IA6LOCAL,      false,   false,   ALL,  ALL    },

                {   null,   IA4LOOPBACK,   INET6,   null,          false,   false,   ALL,  ALL    },
                {   null,   IA4LOCAL,      INET6,   null,          false,   false,   ALL,  ALL    },
                {   null,   IA6LOOPBACK,   INET6,   null,          false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      INET6,   null,          false,   false,   ALL,  ALL    },

                {   null,   IA4LOOPBACK,   null,    IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA4LOCAL,      null,    IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA6LOOPBACK,   null,    IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      null,    IA6ANYLOCAL,   false,   false,   ALL,  ALL    },

                {   null,   IA6LOOPBACK,   null,    IA6LOOPBACK,   false,   false,   ALL,  ALL    },

                {   null,   IA6LOCAL,      null,    IA6LOCAL,      false,   false,   ALL,  ALL    },

                {   null,   IA4LOOPBACK,   null,    null,          false,   false,   ALL,  ALL    },
                {   null,   IA4LOCAL,      null,    null,          false,   false,   ALL,  ALL    },
                {   null,   IA6LOOPBACK,   null,    null,          false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      null,    null,          false,   false,   ALL,  ALL    },
        };
    }

    @Test(dataProvider = "openConnect")
    public void scOpenAndConnect(ProtocolFamily sfam,
                                 InetAddress saddr,
                                 ProtocolFamily cfam,
                                 InetAddress caddr,
                                 boolean ipv4,
                                 boolean ipv6,
                                 int dgMask, int scMask) {
        if (ipv4 != PREFERIPV4 || ipv6 != PREFERIPV6) {
            return;
        }

        if (IA4LOCAL == null || IA6LOCAL == null)
            // mark test as skipped
            throw new SkipException("can't run due to configuration");

        boolean scPass = passOnThisPlatform(scMask);
        out.printf("scOpenAndConnect: server bind: %s client bind: %s\n", saddr, caddr);
        try (ServerSocketChannel ssc = openSSC(sfam)) {
            ssc.bind(getSocketAddress(saddr));
            InetSocketAddress ssa = (InetSocketAddress)ssc.getLocalAddress();
            out.println(ssa);
            try (SocketChannel csc = openSC(cfam)) {
                InetSocketAddress csa = (InetSocketAddress)getSocketAddress(caddr);
                out.printf("Connecting to:  %s/port: %d\n", ssa.getAddress(), ssa.getPort());
                csc.bind(csa);
                connectNonBlocking(csc, ssa, Duration.ofSeconds(3));
                throwIf(!scPass);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException e) {
                throwIf(scPass, e);
            }
        } catch (UnsupportedOperationException | IOException e) {
            throwIf(scPass, e);
        }
    }

    static void connectNonBlocking(SocketChannel chan,
                                   SocketAddress dest,
                                   Duration duration) throws IOException {
        Selector sel = Selector.open();
        chan.configureBlocking(false);
        if (!chan.connect(dest)) {  // connect operation still in progress
            chan.register(sel, SelectionKey.OP_CONNECT);
            sel.select(duration.toMillis());
        }
        if (!chan.finishConnect())
            throw new IOException("connection not made");
    }

    @Test(dataProvider = "openConnect")
    public void dcOpenAndConnect(ProtocolFamily sfam,
                                 InetAddress saddr,
                                 ProtocolFamily cfam,
                                 InetAddress caddr,
                                 boolean ipv4,
                                 boolean ipv6,
                                 int dgMask, int scMask) {
        if (ipv4 != PREFERIPV4 || ipv6 != PREFERIPV6) {
            return;
        }

        if (IA4LOCAL == null || IA6LOCAL == null)
            // mark test as skipped
            throw new SkipException("can't run due to configuration");

        boolean dgPass = passOnThisPlatform(dgMask);
        try (DatagramChannel sdc = openDC(sfam)) {
            sdc.bind(getSocketAddress(saddr));
            SocketAddress ssa = sdc.getLocalAddress();
            out.println(ssa);
            try (DatagramChannel dc = openDC(cfam)) {
                SocketAddress csa = getSocketAddress(caddr);
                dc.bind(csa);
                dc.connect(ssa);
                throwIf(!dgPass);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException e) {
                throwIf(dgPass, e);
            }
        } catch (UnsupportedOperationException | IOException e) {
            throwIf(dgPass, e);
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

    private static void initAddrs() throws IOException {
        NetworkInterface iface = NetworkConfiguration.probe()
                .interfaces()
                .filter(nif -> isUp(nif))
                .filter(nif -> hasV4andV6Addrs(nif))
                .findFirst()
                .orElse(null);

        if (iface == null) {
            IA6LOCAL = null;
            IA4LOCAL = null;
            return;
        }

        IA6LOCAL = (Inet6Address)iface.inetAddresses()
                .filter(IPV6LINKLOCALADDR)
                .findFirst()
                .orElse(null);

        IA4LOCAL = (Inet4Address) iface.inetAddresses()
                .filter(a -> a instanceof Inet4Address)
                //.filter(a -> !s.isLoopbackAddress())
                .findFirst()
                .orElse(null);
    }

    static boolean isUp(NetworkInterface nif) {
        try {
            return nif.isUp();
        } catch (SocketException se) {
            throw new RuntimeException(se);
        }
    }

    static boolean hasV4andV6Addrs(NetworkInterface nif) {
        if (nif.inetAddresses().noneMatch(IPV6LINKLOCALADDR))
            return false;
        if (nif.inetAddresses().noneMatch(a -> a instanceof Inet4Address))
            return false;
        return true;
    }

    private static void throwIf(boolean condition, Exception... e) {
        if (condition && e.length > 0) {
            throw new RuntimeException("Expected to pass", e[0]);
        }
        if (condition) {
            throw new RuntimeException("Expected to fail");
        }
    }
}
