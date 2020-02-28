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
import java.lang.reflect.*;
import java.net.*;
import java.nio.channels.*;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static java.lang.Boolean.parseBoolean;
import static java.lang.System.getProperty;
import static java.lang.System.out;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.test.lib.net.IPSupport.*;

/*
 * @test
 * @summary Test SocketChannel, ServerSocketChannel
 *          and DatagramChannel open and connect
 * @library /test/lib
 * @build jdk.test.lib.NetworkConfiguration
 * @run testng/othervm/timeout=30 OpenConnect
 */

// * @run testng/othervm -Djava.net.preferIPv6Addresses=true OpenConnect
// * @run testng/othervm -Djava.net.preferIPv4Stack=true OpenConnect

public class OpenConnect {
    static final boolean isWindows = Platform.isWindows();
    static final boolean hasIPv6 = hasIPv6();
    static final boolean preferIPv4 = preferIPv4Stack();
    static final boolean preferIPv6 =
            parseBoolean(getProperty("java.net.preferIPv6Addresses", "false"));

    static Inet4Address IA4LOCAL;
    static Inet6Address IA6LOCAL;
    static final Inet4Address IA4ANYLOCAL;
    static final Inet6Address IA6ANYLOCAL;
    static final Inet4Address IA4LOOPBACK;
    static final Inet6Address IA6LOOPBACK;
    static final Predicate<InetAddress> linkLocalAddr = a -> (a instanceof Inet6Address) && a.isLinkLocalAddress();

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
        out.println("preferIPv6Addresses: " + preferIPv6);
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
    static final int M = 4; // Macos
    static final int S = 8; // Solaris

    static final int ALL = L|M|W|S;
    static final int MWS = M|W|S; // ALL except Linux
    static final int MLS = M|L|S; // ALL except Windows
    static final int MS = M|S; // Mac OS & Solaris
    static final int LS = L|S; // Linux & Solaris

    static final boolean passOnThisPlatform(int mask) {
        if (Platform.isLinux())
            return (mask & L) != 0;
        if (Platform.isWindows())
            return (mask & W) != 0;
        if (Platform.isSolaris())
            return (mask & S) != 0;
        if (Platform.isOSX())
            return (mask & M) != 0;
        return false;
    }

    @DataProvider(name = "openConnect")
    public Object[][] openConnect() {
        return new Object[][]{
            //                                                                       Should pass
            //  {   sfam,   saddr,         cfam,    caddr,         ipv4,    ipv6,    DG    SC     }

                {   INET,   null,          INET,    null,          false,   false,   MLS,  ALL    },
                {   INET,   IA4ANYLOCAL,   INET,    IA4ANYLOCAL,   false,   false,   MLS,  ALL    },
                {   INET,   IA4LOOPBACK,   INET,    IA4LOOPBACK,   false,   false,   ALL,  ALL    },
                {   INET,   IA4LOCAL,      INET,    IA4LOCAL,      false,   false,   ALL,  ALL    },
                {   INET,   null,          null,    null,          false,   false,   MLS,  ALL    },
                {   INET,   IA4ANYLOCAL,   null,    IA4ANYLOCAL,   false,   false,   MLS,  ALL    },
                {   INET,   IA4LOOPBACK,   null,    IA4LOOPBACK,   false,   false,   ALL,  ALL    },
                {   INET,   IA4LOCAL,      null,    IA4LOCAL,      false,   false,   ALL,  ALL    },

                {   INET,   null,          INET,    null,          true,    false,   MLS,  ALL    },
                {   INET,   IA4ANYLOCAL,   INET,    IA4ANYLOCAL,   true,    false,   MLS,  ALL    },
                {   INET,   IA4LOOPBACK,   INET,    IA4LOOPBACK,   true,    false,   ALL,  ALL    },
                {   INET,   IA4LOCAL,      INET,    IA4LOCAL,      true,    false,   ALL,  ALL    },
                {   INET,   null,          null,    null,          true,    false,   MLS,  ALL    },
                {   INET,   IA4ANYLOCAL,   null,    IA4ANYLOCAL,   true,    false,   MLS,  ALL    },
                {   INET,   IA4LOOPBACK,   null,    IA4LOOPBACK,   true,    false,   ALL,  ALL    },
                {   INET,   IA4LOCAL,      null,    IA4LOCAL,      true,    false,   ALL,  ALL    },

                {   INET,   null,          INET,    null,          false,   true,    MLS,  MLS    },
                {   INET,   IA4ANYLOCAL,   INET,    IA4ANYLOCAL,   false,   true,    MLS,  MLS    },
                {   INET,   IA4LOOPBACK,   INET,    IA4LOOPBACK,   false,   true,    ALL,  ALL    },
                {   INET,   IA4LOCAL,      INET,    IA4LOCAL,      false,   true,    ALL,  ALL    },
                {   INET,   null,          null,    null,          false,   true,    MLS,  MLS    },
                {   INET,   IA4ANYLOCAL,   null,    IA4ANYLOCAL,   false,   true,    ALL,  ALL    },
                {   INET,   IA4LOOPBACK,   null,    IA4LOOPBACK,   false,   true,    ALL,  ALL    },
                {   INET,   IA4LOCAL,      null,    IA4LOCAL,      false,   true,    ALL,  ALL    },


                {   INET6,   null,          INET6,   null,          false,   false,  MLS,  ALL    },
                {   INET6,   IA6ANYLOCAL,   INET6,   IA6ANYLOCAL,   false,   false,  MLS,  ALL    },
                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   false,   false,  ALL,  ALL    },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL,      false,   false,  ALL,  ALL    },
                {   INET6,   null,          null,    null,          false,   false,  MLS,  ALL    },
                {   INET6,   IA6ANYLOCAL,   null,    IA6ANYLOCAL,   false,   false,  MLS,  ALL    },
                {   INET6,   IA4LOOPBACK,   null,    IA6LOOPBACK,   false,   false,  MS ,  ~ALL   },
                {   INET6,   IA6LOCAL,      null,    IA6LOCAL,      false,   false,  ALL,  ALL    },

                {   INET6,   null,          INET6,   null,          true,    false,  ~ALL, ~ALL   },
                {   INET6,   IA6ANYLOCAL,   INET6,   IA6ANYLOCAL,   true,    false,  ~ALL, ~ALL   },
                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   true,    false,  ~ALL, ~ALL   },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL,      true,    false,  ~ALL, ~ALL   },
                {   INET6,   null,          null,    null,          true,    false,  ~ALL, ~ALL   },
                {   INET6,   IA6ANYLOCAL,   null,    IA6ANYLOCAL,   true,    false,  ~ALL, ~ALL   },
                {   INET6,   IA6LOOPBACK,   null,    IA6LOOPBACK,   true,    false,  ~ALL, ~ALL   },
                {   INET6,   IA6LOCAL,      null,    IA6LOCAL,      true,    false,  ~ALL, ~ALL   },

                {   INET6,   null,          INET6,   null,          false,   true,   MLS,  ALL    },
                {   INET6,   IA6ANYLOCAL,   INET6,   IA6ANYLOCAL,   false,   true,   MLS,  ALL    },
                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   false,   true,   ALL,  ALL    },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL,      false,   true,   ALL,  ALL    },
                {   INET6,   null,          null,    null,          false,   true,   MLS,  MLS    },
                {   INET6,   IA6ANYLOCAL,   null,    IA6ANYLOCAL,   false,   true,   MLS,  MLS    },
                {   INET6,   IA6LOOPBACK,   null,    IA6LOOPBACK,   false,   true,   MLS,  MLS    },
                {   INET6,   IA6LOCAL,      null,    IA6LOCAL,      false,   true,   ALL,  ALL    },


                // despite binding to IA4ANYLOCAL, the local address is actually ::0
                {   null,   IA4ANYLOCAL,   INET,    IA4ANYLOCAL,    false,   false,  ~ALL, ~ALL   },

                {   null,   IA4LOOPBACK,   INET,    IA4ANYLOCAL,    false,   false,  ALL,  ALL    },
                {   null,   IA4LOCAL,      INET,    IA4ANYLOCAL,    false,   false,  ALL,  ALL    },
                {   null,   IA6ANYLOCAL,   INET,    IA4ANYLOCAL,    false,   false,  ~ALL, ~ALL    },
                {   null,   IA6LOOPBACK,   INET,    IA4ANYLOCAL,    false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOCAL,      INET,    IA4ANYLOCAL,    false,   false,  ~ALL, ~ALL   },
                {   null,   null,          INET,    IA4ANYLOCAL,    false,   false,  ~ALL, ~ALL   },

                {   null,   IA4ANYLOCAL,   INET,    IA4LOOPBACK,    false,   false,  ~ALL, ~ALL   },
                {   null,   IA4LOOPBACK,   INET,    IA4LOOPBACK,    false,   false,  ALL,  ALL    },
                {   null,   IA4LOCAL,      INET,    IA4LOOPBACK,    false,   false,  MLS,  MLS    },
                {   null,   IA6ANYLOCAL,   INET,    IA4LOOPBACK,    false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOOPBACK,   INET,    IA4LOOPBACK,    false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOCAL,      INET,    IA4LOOPBACK,    false,   false,  ~ALL, ~ALL   },
                {   null,   null,          INET,    IA4LOOPBACK,    false,   false,  ~ALL, ~ALL   },

                {   null,   IA4ANYLOCAL,   INET,    IA4LOCAL,       false,   false,  ~ALL, ~ALL   },

                // true on Macos(??)
                {   null,   IA4LOOPBACK,   INET,    IA4LOCAL,       false,   false,  MLS,  MLS    },
                {   null,   IA4LOCAL,      INET,    IA4LOCAL,       false,   false,  ALL,  ALL    },
                {   null,   IA6ANYLOCAL,   INET,    IA4LOCAL,       false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOOPBACK,   INET,    IA4LOCAL,       false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOCAL,      INET,    IA4LOCAL,       false,   false,  ~ALL, ~ALL   },
                {   null,   null,          INET,    IA4LOCAL,       false,   false,  ~ALL, ~ALL   },

                {   null,   IA4ANYLOCAL,   INET,    null,           false,   false,  ~ALL, ~ALL   },
                {   null,   IA4LOOPBACK,   INET,    null,           false,   false,  ALL,  ALL    },
                {   null,   IA4LOCAL,      INET,    null,           false,   false,  ALL,  ALL    },
                {   null,   IA6ANYLOCAL,   INET,    null,           false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOOPBACK,   INET,    null,           false,   false,  ~ALL, ~ALL   },
                {   null,   IA6LOCAL,      INET,    null,           false,   false,  ~ALL, ~ALL   },
                {   null,   null,          INET,    null,           false,   false,  ~ALL, ~ALL   },


                {   null,   IA4ANYLOCAL,   INET6,   IA6ANYLOCAL,   false,   false,   MLS,  ALL    },
                {   null,   IA4LOOPBACK,   INET6,   IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA4LOCAL,      INET6,   IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA6ANYLOCAL,   INET6,   IA6ANYLOCAL,   false,   false,   MLS,  ALL    },
                {   null,   IA6LOOPBACK,   INET6,   IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      INET6,   IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   null,          INET6,   IA6ANYLOCAL,   false,   false,   MLS,  ALL    },

                {   null,   IA4ANYLOCAL,   INET6,   IA6LOOPBACK,   false,   false,   MLS,  ALL    },
                {   null,   IA4LOOPBACK,   INET6,   IA6LOOPBACK,   false,   false,   MS ,  ~ALL   },
                {   null,   IA4LOCAL,      INET6,   IA6LOOPBACK,   false,   false,   MS ,  ~ALL   },
                {   null,   IA6ANYLOCAL,   INET6,   IA6LOOPBACK,   false,   false,   MLS,  ALL    },
                {   null,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      INET6,   IA6LOOPBACK,   false,   false,   MLS,  MS     },
                {   null,   null,          INET6,   IA6LOOPBACK,   false,   false,   MLS,  ALL    },

                {   null,   IA4ANYLOCAL,   INET6,   IA6LOCAL,      false,   false,   MS ,  MS     },
                {   null,   IA4LOOPBACK,   INET6,   IA6LOCAL,      false,   false,   MS ,  ~ALL  }, // *
                {   null,   IA4LOCAL,      INET6,   IA6LOCAL,      false,   false,   MS ,  ~ALL  }, // *
                {   null,   IA6ANYLOCAL,   INET6,   IA6LOCAL,      false,   false,   MS ,  MS     },
                {   null,   IA6LOOPBACK,   INET6,   IA6LOCAL,      false,   false,   MS ,  MS     },
                {   null,   IA6LOCAL,      INET6,   IA6LOCAL,      false,   false,   ALL,  ALL    },
                {   null,   null,          INET6,   IA6LOCAL,      false,   false,   MS ,  MS     },

                {   null,   IA4ANYLOCAL,   INET6,   null,          false,   false,   MLS,  ALL    },
                {   null,   IA4LOOPBACK,   INET6,   null,          false,   false,   ALL,  ALL    },
                {   null,   IA4LOCAL,      INET6,   null,          false,   false,   ALL,  ALL    },
                {   null,   IA6ANYLOCAL,   INET6,   null,          false,   false,   MLS,  ALL    },
                {   null,   IA6LOOPBACK,   INET6,   null,          false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      INET6,   null,          false,   false,   ALL,  ALL    },
                {   null,   null,          INET6,   null,          false,   false,   MLS,  ALL    },

                {   null,   IA4ANYLOCAL,   null,    IA6ANYLOCAL,   false,   false,   MLS,  ALL    },
                {   null,   IA4LOOPBACK,   null,    IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA4LOCAL,      null,    IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA6ANYLOCAL,   null,    IA6ANYLOCAL,   false,   false,   MLS,  ALL    },
                {   null,   IA6LOOPBACK,   null,    IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      null,    IA6ANYLOCAL,   false,   false,   ALL,  ALL    },
                {   null,   null,          null,    IA6ANYLOCAL,   false,   false,   MLS,  ALL    },

                {   null,   IA4ANYLOCAL,   null,    IA6LOOPBACK,   false,   false,   MLS,  ALL    },
                {   null,   IA4LOOPBACK,   null,    IA6LOOPBACK,   false,   false,   MS ,  ~ALL   }, //*
                {   null,   IA4LOCAL,      null,    IA6LOOPBACK,   false,   false,   MS ,  ~ALL   }, //*
                {   null,   IA6ANYLOCAL,   null,    IA6LOOPBACK,   false,   false,   MLS,  ALL    },
                {   null,   IA6LOOPBACK,   null,    IA6LOOPBACK,   false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      null,    IA6LOOPBACK,   false,   false,   MLS,  MS     }, // hangs L
                {   null,   null,          null,    IA6LOOPBACK,   false,   false,   MLS,  ALL    },

                {   null,   IA4ANYLOCAL,   null,    IA6LOCAL,      false,   false,   MS ,  MS     },
                {   null,   IA4LOOPBACK,   null,    IA6LOCAL,      false,   false,   MS ,  ~ALL   }, //*
                {   null,   IA4LOCAL,      null,    IA6LOCAL,      false,   false,   MS ,  ~ALL   }, //*
                {   null,   IA6ANYLOCAL,   null,    IA6LOCAL,      false,   false,   MS ,  MS     },
                {   null,   IA6LOOPBACK,   null,    IA6LOCAL,      false,   false,   MS ,  MS     },
                {   null,   IA6LOCAL,      null,    IA6LOCAL,      false,   false,   ALL,  ALL    },
                {   null,   null,          null,    IA6LOCAL,      false,   false,   MS ,  MS     },

                {   null,   IA4ANYLOCAL,   null,    null,          false,   false,   MLS,  ALL    },
                {   null,   IA4LOOPBACK,   null,    null,          false,   false,   ALL,  ALL    },
                {   null,   IA4LOCAL,      null,    null,          false,   false,   ALL,  ALL    },
                {   null,   IA6ANYLOCAL,   null,    null,          false,   false,   MLS,  ALL    },
                {   null,   IA6LOOPBACK,   null,    null,          false,   false,   ALL,  ALL    },
                {   null,   IA6LOCAL,      null,    null,          false,   false,   ALL,  ALL    },
                {   null,   null,          null,    null,          false,   false,   MLS,  ALL    },
        };
    }

    @Test(dataProvider = "openConnect")
    public void scOpenConnect(StandardProtocolFamily sfam,
                              InetAddress saddr,
                              StandardProtocolFamily cfam,
                              InetAddress caddr,
                              boolean ipv4,
                              boolean ipv6,
                              int dgMask, int scMask) throws Exception {
        if (ipv4 != preferIPv4 || ipv6 != preferIPv6) {
            return;
        }
        boolean scPass = passOnThisPlatform(scMask);
        System.out.printf("scOpenConnect: server bind: %s client bind: %s\n", saddr, caddr);
        try (ServerSocketChannel ssc = openSSC(sfam)) {
            if (ssc == null) return; // not supported
            ssc.bind(getSocketAddress(saddr));
            InetSocketAddress ssa = (InetSocketAddress)ssc.getLocalAddress();
            System.out.println(ssa);
            try (SocketChannel csc = openSC(cfam)) {
                if (csc == null) return; // not supported
                InetSocketAddress csa = (InetSocketAddress)getSocketAddress(caddr);
                System.out.printf("Connecting to:  %s/port: %d\n", ssa.getAddress(), ssa.getPort());
                csc.bind(csa);
                connectNonBlocking(csc, ssa, Duration.ofSeconds(3));
                throwIf(!scPass);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException re) {
                throwIf(scPass, re);
            }
        } catch (UnsupportedOperationException re) {
            throwIf(scPass, re);
        } catch (IOException ioe) {
            throwIf(scPass, ioe);
        }
    }

    static void connectNonBlocking(SocketChannel chan, SocketAddress dest, Duration duration) throws IOException {
        Selector sel = Selector.open();
        chan.configureBlocking(false);
        chan.register(sel, SelectionKey.OP_CONNECT);
        chan.connect(dest);
        long timeout = duration.toMillis();
        int res = sel.select(timeout);
        if (!chan.finishConnect())
            throw new IOException("connection not made");
    }

    @Test(dataProvider = "openConnect")
    public void dcOpenConnect(StandardProtocolFamily sfam,
                              InetAddress saddr,
                              StandardProtocolFamily cfam,
                              InetAddress caddr,
                              boolean ipv4,
                              boolean ipv6,
                              int dgMask, int scMask) throws Exception {
        if (ipv4 != preferIPv4 || ipv6 != preferIPv6) {
            return;
        }
        boolean dgPass = passOnThisPlatform(dgMask);
        try (DatagramChannel sdc = openDC(sfam)) {
            sdc.bind(getSocketAddress(saddr));
            SocketAddress ssa = sdc.getLocalAddress();
            System.out.println(ssa);
            try (DatagramChannel dc = openDC(cfam)) {
                SocketAddress csa = getSocketAddress(caddr);
                dc.bind(csa);
                dc.connect(ssa);
                throwIf(!dgPass);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException re) {
                throwIf(dgPass, re);
            }
        } catch (UnsupportedOperationException re) {
            throwIf(dgPass, re);
        } catch (IOException ioe) {
            throwIf(dgPass, ioe);
        }
    }

    // Helper methods

    static Method cfactory;
    static Method sfactory;

    static {
        try {
            Class<?> clazz = SocketChannel.class;
            cfactory = clazz.getDeclaredMethod("open", java.net.ProtocolFamily.class);
            clazz = ServerSocketChannel.class;
            sfactory = clazz.getDeclaredMethod("open", java.net.ProtocolFamily.class);
        } catch (Exception e) {
            cfactory = null;
            sfactory = null;
        }
    }

    private static SocketChannel openSC(StandardProtocolFamily fam)
        throws IOException {

        if (fam == null) {
            return SocketChannel.open();
        }
        if (cfactory == null) {
            return null;
        }
        try {
            return (SocketChannel) cfactory.invoke(null, fam);
        } catch (Exception e) {
            // should not happen
            throw new InternalError(e);
        }
    }


    private static ServerSocketChannel openSSC(StandardProtocolFamily fam)
        throws IOException {

        if (fam == null) {
            return ServerSocketChannel.open();
        }
        if (sfactory == null) {
            return null;
        }
        try {
            return (ServerSocketChannel) sfactory.invoke(null, fam);
        } catch (Exception e) {
            // should not happen
            throw new InternalError(e);
        }

    }

    private static DatagramChannel openDC(StandardProtocolFamily fam)
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

        IA6LOCAL = (Inet6Address)iface.inetAddresses()
                .filter(linkLocalAddr)
                .findFirst()
                .orElse(null);

        IA4LOCAL = (Inet4Address) iface.inetAddresses()
                .filter(a -> a instanceof Inet4Address)
                .findFirst()
                .orElse(null);
    }

    static boolean isUp(NetworkInterface nif) {
        try {
            return nif.isUp();
        } catch (SocketException e) {
            throw new RuntimeException(e);
        }
    }

    static boolean hasV4andV6Addrs(NetworkInterface nif) {
        if (!nif.inetAddresses().anyMatch(linkLocalAddr))
            return false;
        if (!nif.inetAddresses().anyMatch(a -> a instanceof Inet4Address))
            return false;
        return true;
    }

    private static void throwIf(boolean condition, Exception... re) {
        if (condition && re.length > 0) {
            throw new RuntimeException("Expected to pass", re[0]);
        }
        if (condition) {
            throw new RuntimeException("Expected to fail");
        }
    }
}
