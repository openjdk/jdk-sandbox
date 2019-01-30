/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8195160
 * @summary Checks the RDMA selector provider API methods
 * @requires (os.family == "linux")
 * @library /test/lib
 * @build RsocketTest
 * @run testng/othervm RdmaSelectorProvider
 */

import java.io.IOException;
import java.net.ProtocolFamily;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import org.testng.SkipException;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;
import static jdk.net.RdmaSockets.*;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertThrows;

public class RdmaSelectorProvider {

    @BeforeTest
    public void setup() throws Exception {
        if (!RsocketTest.isRsocketAvailable())
            throw new SkipException("rsocket is not available");
    }

    @DataProvider(name = "families")
    public Object[][] families() {
        return new Object[][] { { INET }, { INET6 } };
    }

    static final Class<UnsupportedOperationException> UOE = UnsupportedOperationException.class;

    @Test(dataProvider = "families")
    public void testSocketChannel(ProtocolFamily family)
        throws IOException
    {
        try (SocketChannel sc = openSocketChannel(family)) {
            assertThrows(UOE, () -> sc.provider().openDatagramChannel());
            assertThrows(UOE, () -> sc.provider().openDatagramChannel(INET));
            assertThrows(UOE, () -> sc.provider().openDatagramChannel(INET6));
            assertThrows(UOE, () -> sc.provider().openPipe());

            assertNotEquals(sc.provider().openSelector(), null);
            assertNotEquals(sc.provider().openSocketChannel(), null);
            assertNotEquals(sc.provider().openServerSocketChannel(), null);
        }
    }

    @Test(dataProvider = "families")
    public void testServerSocketChannel(ProtocolFamily family)
        throws IOException
    {
        try (ServerSocketChannel ssc = openServerSocketChannel(family)) {
            assertThrows(UOE, () -> ssc.provider().openDatagramChannel());
            assertThrows(UOE, () -> ssc.provider().openDatagramChannel(INET));
            assertThrows(UOE, () -> ssc.provider().openDatagramChannel(INET6));
            assertThrows(UOE, () -> ssc.provider().openPipe());

            assertNotEquals(ssc.provider().openSelector(), null);
            assertNotEquals(ssc.provider().openSocketChannel(), null);
            assertNotEquals(ssc.provider().openServerSocketChannel(), null);
        }
    }

    @Test
    public void testSelector() throws IOException {
        try (Selector selector = openSelector()) {
            assertThrows(UOE, () -> selector.provider().openDatagramChannel());
            assertThrows(UOE, () -> selector.provider().openDatagramChannel(INET));
            assertThrows(UOE, () -> selector.provider().openDatagramChannel(INET6));
            assertThrows(UOE, () -> selector.provider().openPipe());

            assertNotEquals(selector.provider().openSelector(), null);
            assertNotEquals(selector.provider().openSocketChannel(), null);
            assertNotEquals(selector.provider().openServerSocketChannel(), null);
        }
    }
}
