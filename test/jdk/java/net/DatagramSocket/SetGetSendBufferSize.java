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

/*
 * @test
 * @bug 8239355
 * @summary Check that setSendBufferSize and getSendBufferSize work as expected
 * @run testng/othervm SetGetSendBufferSize
 * @run testng/othervm -Djava.net.preferIPv4Stack=true SetGetSendBufferSize
 */

import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.net.DatagramSocket;
import java.net.MulticastSocket;
import java.net.SocketException;
import java.nio.channels.DatagramChannel;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;

public class SetGetSendBufferSize {
    DatagramSocket datagramSocket, multicastSocket, datagramSocketAdaptor;

    private static final Class<SocketException> SE = SocketException.class;
    private static final Class<IllegalArgumentException> IAE =
            IllegalArgumentException.class;

    @BeforeTest
    public void setUp() throws Exception {
        datagramSocket = new DatagramSocket();
        multicastSocket = new MulticastSocket();
        datagramSocketAdaptor = DatagramChannel.open().socket();
    }

    @DataProvider(name = "data")
    public Object[][] variants() {
        return new Object[][]{
                { datagramSocket        },
                { datagramSocketAdaptor },
                { multicastSocket       },
        };
    }

    @Test(dataProvider = "data")
    public void testSetNegArguments(DatagramSocket ds) {
        assertThrows(IAE, () -> ds.setSendBufferSize(-1));
    }

    @Test(dataProvider = "data")
    public void testGetSendBufferSize(DatagramSocket ds) throws Exception {
        ds.setSendBufferSize(2345);
        assertEquals(ds.getSendBufferSize(), 2345);
    }

    @AfterTest
    public void tearDown() {
        datagramSocket.close();
        multicastSocket.close();
        datagramSocketAdaptor.close();
    }

    @Test
    public void testSetGetAfterClose() throws Exception {
        var ds = new DatagramSocket();
        var ms = new MulticastSocket();
        var dsa = DatagramChannel.open().socket();

        ds.close();
        ms.close();
        dsa.close();
        assertThrows(SE, () -> ds.setSendBufferSize(2345));
        assertThrows(SE, () -> ds.getSoTimeout());
        assertThrows(SE, () -> ms.setSendBufferSize(2345));
        assertThrows(SE, () -> ms.getSoTimeout());
        assertThrows(SE, () -> dsa.setSendBufferSize(2345));
        assertThrows(SE, () -> dsa.getSoTimeout());
    }
}
