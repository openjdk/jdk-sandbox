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
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.*;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;

import static java.lang.System.out;
import static java.lang.System.getProperty;
import static java.lang.Boolean.parseBoolean;
import static java.net.StandardProtocolFamily.UNIX;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static java.nio.channels.UnixDomainSocketAddress.MAXNAMELENGTH;

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
    static UnixDomainSocketAddress cudsa;
    static final boolean preferIPv4 =
            parseBoolean(getProperty("java.net.preferIPv4Stack", "false"));
    static final boolean preferIPv6 =
            parseBoolean(getProperty("java.net.preferIPv6Addresses", "false"));

    @BeforeTest()
    public void setup() throws Exception {
        if (MAXNAMELENGTH == -1) {
            throw new SkipException("Unix domain channels not supported");
        }
        ia4 = getFirstLinkLocalIPv4Address();
        ia6 = getFirstLinkLocalIPv6Address();
        cudsa = new UnixDomainSocketAddress(Path.of("client.sock"));

        out.println("preferIPv4: " + preferIPv4);
        out.println("preferIPv6: " + preferIPv6);
        out.println("IPv6 supported: " + IPSupport.hasIPv6());
    }

    // SocketChannel open   - Dualstack, INET, INET6, UNIX
    // SocketChannel bind   - INET, INET6, UNIX, null

    @DataProvider(name = "scOpenBind")
    public Object[][] scOpenBind() {
        return new Object[][]{
                {  INET, INET,  true   }, {  INET6, INET,  false  },
                {  INET, INET6, false  }, {  INET6, INET6, true   },
                {  INET, UNIX,  false  }, {  INET6, UNIX,  false  },
                {  INET, null,  true   }, {  INET6, null,  true   },
                {  UNIX, INET,  false  }, {  null,  INET,  true   },
                {  UNIX, INET6, false  }, {  null,  INET6, true   },
                {  UNIX, UNIX,  true   }, {  null,  UNIX,  false  },
                {  UNIX, null,  true   }, {  null,  null,  true   }
        };
    }

    @Test(dataProvider = "scOpenBind")
    public void scOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) throws Exception {
        out.println("\n");
        try (SocketChannel sc = openSC(ofam)) {
            SocketAddress addr = bfam == null ? null : switch (bfam) {
                case INET -> new InetSocketAddress(ia4, 0);
                case INET6 -> new InetSocketAddress(ia6, 0);
                case UNIX -> cudsa;
            };
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
        deleteFile(cudsa);
    }

    //  SocketChannel open    - Dualstack, INET, INET6, UNIX
    //  SocketChannel connect - INET, INET6, UNIX, Dualstack

    @DataProvider(name = "scOpenConnect")
    public Object[][] scOpenConnect() {
        return new Object[][]{
                {  INET, INET,  true   }, {  INET6, INET,  false  },
                {  INET, INET6, false  }, {  INET6, INET6, false  },
                {  INET, UNIX,  false  }, {  INET6, UNIX,  false  },
                {  INET, null,  false  }, {  INET6, null,  false  },
                {  UNIX, INET,  false  }, {  null,  INET,  true   },
                {  UNIX, INET6, false  }, {  null,  INET6, false  },
                {  UNIX, UNIX,  true   }, {  null,  UNIX,  false  },
                {  UNIX, null,  false  }, {  null,  null,  true   }
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
        deleteFile(cudsa);
    }

    //  ServerSocketChannel open    - Dualstack, INET, INET6, UNIX
    //  ServerSocketChannel bind    - INET, INET6, UNIX, null

    @DataProvider(name = "sscOpenBind")
    public Object[][] sscOpenBind() {
        return new Object[][]{
                {  INET, INET,  true   }, {  INET6, INET,  false  },
                {  INET, INET6, false  }, {  INET6, INET6, true   },
                {  INET, UNIX,  false  }, {  INET6, UNIX,  false  },
                {  INET, null,  true   }, {  INET6, null,  true   },
                {  UNIX, INET,  false  }, {  null,  INET,  true   },
                {  UNIX, INET6, false  }, {  null,  INET6, true   },
                {  UNIX, UNIX,  true   }, {  null,  UNIX,  false  },
                {  UNIX, null,  true   }, {  null,  null,  true   }
        };
    }

    @Test(dataProvider = "sscOpenBind")
    public void sscOpenBind(StandardProtocolFamily ofam,
                           StandardProtocolFamily bfam,
                           boolean expectPass) throws Exception {
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
        deleteFile(cudsa);
    }

    // Helper methods

    static SocketChannel openSC(ProtocolFamily family)
            throws IOException {
        return family == null ? SocketChannel.open()
                : SocketChannel.open(family);
    }

    static ServerSocketChannel openSSC(ProtocolFamily family)
            throws IOException {
        return family == null ? ServerSocketChannel.open()
                : ServerSocketChannel.open(family);
    }

    public static void deleteFile(SocketAddress addr) throws Exception {
        if (addr instanceof UnixDomainSocketAddress) {
            Files.deleteIfExists(((UnixDomainSocketAddress) addr).getPath());
        }
    }

    public static SocketAddress getSocketAddress(StandardProtocolFamily fam) {
        return fam == null ? null : switch (fam) {
            case INET -> new InetSocketAddress(ia4, 0);
            case INET6 -> new InetSocketAddress(ia6, 0);
            case UNIX -> cudsa;
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
