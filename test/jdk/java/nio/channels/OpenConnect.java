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
import java.nio.channels.DatagramChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnsupportedAddressTypeException;

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
 * @run testng/othervm OpenConnect
 */

// * @run testng/othervm -Djava.net.preferIPv6Addresses=true OpenConnect
// * @run testng/othervm -Djava.net.preferIPv4Stack=true OpenConnect

public class OpenConnect {
    static final boolean isWindows = Platform.isWindows();
    static final boolean hasIPv6 = hasIPv6();
    static final boolean preferIPv4 = preferIPv4Stack();
    static final boolean preferIPv6 =
            parseBoolean(getProperty("java.net.preferIPv6Addresses", "false"));

    static final Inet4Address IA4LOCAL;
    static final Inet6Address IA6LOCAL;
    static final Inet4Address IA4ANYLOCAL;
    static final Inet6Address IA6ANYLOCAL;
    static final Inet4Address IA4LOOPBACK;
    static final Inet6Address IA6LOOPBACK;

    static {
        try {
            IA4LOCAL = getFirstLinkLocalIPv4Address();
            IA6LOCAL = getFirstLinkLocalIPv6Address();
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

    @DataProvider(name = "openConnect")
    public Object[][] openConnect() {
        return new Object[][]{

            //  {   sfam,   saddr,         cfam,    caddr,         ipv4,    ipv6,    expectPass }

                {   INET,   null,          INET,    null,          false,   false,   true   },
                {   INET,   IA4ANYLOCAL,   INET,    IA4ANYLOCAL,   false,   false,   true   },
                {   INET,   IA4LOOPBACK,   INET,    IA4LOOPBACK,   false,   false,   true   },
                {   INET,   IA4LOCAL,      INET,    IA4LOCAL,      false,   false,   true   },
                {   INET,   null,          null,    null,          false,   false,   true   },
                {   INET,   IA4ANYLOCAL,   null,    IA4ANYLOCAL,   false,   false,   true   },
                {   INET,   IA4LOOPBACK,   null,    IA4LOOPBACK,   false,   false,   true   },
                {   INET,   IA4LOCAL,      null,    IA4LOCAL,      false,   false,   true   },

                {   INET,   null,          INET,    null,          true,    false,   true   },
                {   INET,   IA4ANYLOCAL,   INET,    IA4ANYLOCAL,   true,    false,   true   },
                {   INET,   IA4LOOPBACK,   INET,    IA4LOOPBACK,   true,    false,   true   },
                {   INET,   IA4LOCAL,      INET,    IA4LOCAL,      true,    false,   true   },
                {   INET,   null,          null,    null,          true,    false,   true   },
                {   INET,   IA4ANYLOCAL,   null,    IA4ANYLOCAL,   true,    false,   true   },
                {   INET,   IA4LOOPBACK,   null,    IA4LOOPBACK,   true,    false,   true   },
                {   INET,   IA4LOCAL,      null,    IA4LOCAL,      true,    false,   true   },

                {   INET,   null,          INET,    null,          false,   true,    true   },
                {   INET,   IA4ANYLOCAL,   INET,    IA4ANYLOCAL,   false,   true,    true   },
                {   INET,   IA4LOOPBACK,   INET,    IA4LOOPBACK,   false,   true,    true   },
                {   INET,   IA4LOCAL,      INET,    IA4LOCAL,      false,   true,    true   },
                {   INET,   null,          null,    null,          false,   true,    true   },
                {   INET,   IA4ANYLOCAL,   null,    IA4ANYLOCAL,   false,   true,    true   },
                {   INET,   IA4LOOPBACK,   null,    IA4LOOPBACK,   false,   true,    true   },
                {   INET,   IA4LOCAL,      null,    IA4LOCAL,      false,   true,    true   },


                {   INET6,   null,          INET6,   null,          false,   false,  true   },
                {   INET6,   IA6ANYLOCAL,   INET6,   IA6ANYLOCAL,   false,   false,  true   },
                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   false,   false,  true   },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL,      false,   false,  true   },
                {   INET6,   null,          null,    null,          false,   false,  true   },
                {   INET6,   IA6ANYLOCAL,   null,    IA6ANYLOCAL,   false,   false,  true   },
                {   INET6,   IA6LOOPBACK,   null,    IA6LOOPBACK,   false,   false,  true   },
                {   INET6,   IA6LOCAL,      null,    IA6LOCAL,      false,   false,  true   },

                {   INET6,   null,          INET6,   null,          true,    false,  false  },
                {   INET6,   IA6ANYLOCAL,   INET6,   IA6ANYLOCAL,   true,    false,  false  },
                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   true,    false,  false  },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL,      true,    false,  false  },
                {   INET6,   null,          null,    null,          true,    false,  false  },
                {   INET6,   IA6ANYLOCAL,   null,    IA6ANYLOCAL,   true,    false,  false  },
                {   INET6,   IA6LOOPBACK,   null,    IA6LOOPBACK,   true,    false,  false  },
                {   INET6,   IA6LOCAL,      null,    IA6LOCAL,      true,    false,  false  },

                {   INET6,   null,          INET6,   null,          false,   true,   true   },
                {   INET6,   IA6ANYLOCAL,   INET6,   IA6ANYLOCAL,   false,   true,   true   },
                {   INET6,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   false,   true,   true   },
                {   INET6,   IA6LOCAL,      INET6,   IA6LOCAL,      false,   true,   true   },
                {   INET6,   null,          null,    null,          false,   true,   true   },
                {   INET6,   IA6ANYLOCAL,   null,    IA6ANYLOCAL,   false,   true,   true   },
                {   INET6,   IA6LOOPBACK,   null,    IA6LOOPBACK,   false,   true,   true   },
                {   INET6,   IA6LOCAL,      null,    IA6LOCAL,      false,   true,   true   },


                {   null,   IA4ANYLOCAL,   INET,    IA4ANYLOCAL,    false,   false,  true   },
                {   null,   IA4LOOPBACK,   INET,    IA4ANYLOCAL,    false,   false,  true   },
                {   null,   IA4LOCAL,      INET,    IA4ANYLOCAL,    false,   false,  true   },
                {   null,   IA6ANYLOCAL,   INET,    IA4ANYLOCAL,    false,   false,  true   },
                {   null,   IA6LOOPBACK,   INET,    IA4ANYLOCAL,    false,   false,  true   },
                {   null,   IA6LOCAL,      INET,    IA4ANYLOCAL,    false,   false,  true   },
                {   null,   null,          INET,    IA4ANYLOCAL,    false,   false,  true   },

                {   null,   IA4ANYLOCAL,   INET,    IA4LOOPBACK,    false,   false,  true   },
                {   null,   IA4LOOPBACK,   INET,    IA4LOOPBACK,    false,   false,  true   },
                {   null,   IA4LOCAL,      INET,    IA4LOOPBACK,    false,   false,  true   },
                {   null,   IA6ANYLOCAL,   INET,    IA4LOOPBACK,    false,   false,  true   },
                {   null,   IA6LOOPBACK,   INET,    IA4LOOPBACK,    false,   false,  true   },
                {   null,   IA6LOCAL,      INET,    IA4LOOPBACK,    false,   false,  true   },
                {   null,   null,          INET,    IA4LOOPBACK,    false,   false,  true   },

                {   null,   IA4ANYLOCAL,   INET,    IA4LOCAL,       false,   false,  true   },
                {   null,   IA4LOOPBACK,   INET,    IA4LOCAL,       false,   false,  true   },
                {   null,   IA4LOCAL,      INET,    IA4LOCAL,       false,   false,  true   },
                {   null,   IA6ANYLOCAL,   INET,    IA4LOCAL,       false,   false,  true   },
                {   null,   IA6LOOPBACK,   INET,    IA4LOCAL,       false,   false,  true   },
                {   null,   IA6LOCAL,      INET,    IA4LOCAL,       false,   false,  true   },
                {   null,   null,          INET,    IA4LOCAL,       false,   false,  true   },

                {   null,   IA4ANYLOCAL,   INET,    null,           false,   false,  true   },
                {   null,   IA4LOOPBACK,   INET,    null,           false,   false,  true   },
                {   null,   IA4LOCAL,      INET,    null,           false,   false,  true   },
                {   null,   IA6ANYLOCAL,   INET,    null,           false,   false,  true   },
                {   null,   IA6LOOPBACK,   INET,    null,           false,   false,  true   },
                {   null,   IA6LOCAL,      INET,    null,           false,   false,  true   },
                {   null,   null,          INET,    null,           false,   false,  true   },


                {   null,   IA4ANYLOCAL,   INET6,   IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA4LOOPBACK,   INET6,   IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA4LOCAL,      INET6,   IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA6ANYLOCAL,   INET6,   IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA6LOOPBACK,   INET6,   IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA6LOCAL,      INET6,   IA6ANYLOCAL,   false,   false,   true   },
                {   null,   null,          INET6,   IA6ANYLOCAL,   false,   false,   true   },

                {   null,   IA4ANYLOCAL,   INET6,   IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA4LOOPBACK,   INET6,   IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA4LOCAL,      INET6,   IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA6ANYLOCAL,   INET6,   IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA6LOOPBACK,   INET6,   IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA6LOCAL,      INET6,   IA6LOOPBACK,   false,   false,   true   },
                {   null,   null,          INET6,   IA6LOOPBACK,   false,   false,   true   },

                {   null,   IA4ANYLOCAL,   INET6,   IA6LOCAL,      false,   false,   true   },
                {   null,   IA4LOOPBACK,   INET6,   IA6LOCAL,      false,   false,   true   },
                {   null,   IA4LOCAL,      INET6,   IA6LOCAL,      false,   false,   true   },
                {   null,   IA6ANYLOCAL,   INET6,   IA6LOCAL,      false,   false,   true   },
                {   null,   IA6LOOPBACK,   INET6,   IA6LOCAL,      false,   false,   true   },
                {   null,   IA6LOCAL,      INET6,   IA6LOCAL,      false,   false,   true   },
                {   null,   null,          INET6,   IA6LOCAL,      false,   false,   true   },

                {   null,   IA4ANYLOCAL,   INET6,   null,          false,   false,   true   },
                {   null,   IA4LOOPBACK,   INET6,   null,          false,   false,   true   },
                {   null,   IA4LOCAL,      INET6,   null,          false,   false,   true   },
                {   null,   IA6ANYLOCAL,   INET6,   null,          false,   false,   true   },
                {   null,   IA6LOOPBACK,   INET6,   null,          false,   false,   true   },
                {   null,   IA6LOCAL,      INET6,   null,          false,   false,   true   },
                {   null,   null,          INET6,   null,          false,   false,   true   },


                {   null,   IA4ANYLOCAL,   null,    IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA4LOOPBACK,   null,    IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA4LOCAL,      null,    IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA6ANYLOCAL,   null,    IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA6LOOPBACK,   null,    IA6ANYLOCAL,   false,   false,   true   },
                {   null,   IA6LOCAL,      null,    IA6ANYLOCAL,   false,   false,   true   },
                {   null,   null,          null,    IA6ANYLOCAL,   false,   false,   true   },

                {   null,   IA4ANYLOCAL,   null,    IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA4LOOPBACK,   null,    IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA4LOCAL,      null,    IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA6ANYLOCAL,   null,    IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA6LOOPBACK,   null,    IA6LOOPBACK,   false,   false,   true   },
                {   null,   IA6LOCAL,      null,    IA6LOOPBACK,   false,   false,   true   },
                {   null,   null,          null,    IA6LOOPBACK,   false,   false,   true   },

                {   null,   IA4ANYLOCAL,   null,    IA6LOCAL,      false,   false,   true   },
                {   null,   IA4LOOPBACK,   null,    IA6LOCAL,      false,   false,   true   },
                {   null,   IA4LOCAL,      null,    IA6LOCAL,      false,   false,   true   },
                {   null,   IA6ANYLOCAL,   null,    IA6LOCAL,      false,   false,   true   },
                {   null,   IA6LOOPBACK,   null,    IA6LOCAL,      false,   false,   true   },
                {   null,   IA6LOCAL,      null,    IA6LOCAL,      false,   false,   true   },
                {   null,   null,          null,    IA6LOCAL,      false,   false,   true   },

                {   null,   IA4ANYLOCAL,   null,    null,          false,   false,   true   },
                {   null,   IA4LOOPBACK,   null,    null,          false,   false,   true   },
                {   null,   IA4LOCAL,      null,    null,          false,   false,   true   },
                {   null,   IA6ANYLOCAL,   null,    null,          false,   false,   true   },
                {   null,   IA6LOOPBACK,   null,    null,          false,   false,   true   },
                {   null,   IA6LOCAL,      null,    null,          false,   false,   true   },
                {   null,   null,          null,    null,          false,   false,   true   },
        };
    }

    @Test(dataProvider = "openConnect")
    public void scOpenConnect(StandardProtocolFamily sfam,
                              InetAddress saddr,
                              StandardProtocolFamily cfam,
                              InetAddress caddr,
                              boolean ipv4,
                              boolean ipv6,
                              boolean expectPass) throws Exception {
        if (ipv4 != preferIPv4 || ipv6 != preferIPv6) {
            return;
        }
        try (ServerSocketChannel ssc = openSSC(sfam)) {
            ssc.bind(getSocketAddress(saddr));
            SocketAddress ssa = ssc.getLocalAddress();
            try (SocketChannel csc = openSC(cfam)) {
                SocketAddress csa = getSocketAddress(caddr);
                csc.bind(csa);
                csc.connect(ssa);
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

    @Test(dataProvider = "openConnect")
    public void dcOpenConnect(StandardProtocolFamily sfam,
                              InetAddress saddr,
                              StandardProtocolFamily cfam,
                              InetAddress caddr,
                              boolean ipv4,
                              boolean ipv6,
                              boolean expectPass) throws Exception {
        if (ipv4 != preferIPv4 || ipv6 != preferIPv6) {
            return;
        }
        try (DatagramChannel sdc = openDC(sfam)) {
            sdc.bind(getSocketAddress(saddr));
            SocketAddress ssa = sdc.getLocalAddress();
            try (DatagramChannel dc = openDC(cfam)) {
                SocketAddress csa = getSocketAddress(caddr);
                dc.bind(csa);
                dc.connect(ssa);
                throwIf(!expectPass);
            } catch (UnsupportedAddressTypeException
                    | UnsupportedOperationException re) {
                throwIf(expectPass, re);
            }
        } catch (UnsupportedOperationException re) {
            throwIf(expectPass, re);
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

    private static SocketAddress getSocketAddress(InetAddress ia) {
        return ia == null ? null : new InetSocketAddress(ia, 0);
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
                .orElse(null);
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
