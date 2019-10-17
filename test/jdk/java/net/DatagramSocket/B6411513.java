/*
 * Copyright (c) 2006, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 6411513
 * @summary java.net.DatagramSocket.receive: packet isn't received
 */

import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.util.*;
import static java.lang.System.out;

public class B6411513 {

    public static void main( String[] args ) throws Exception {
        Enumeration<NetworkInterface> nics = NetworkInterface.getNetworkInterfaces();
        while (nics.hasMoreElements()) {
            NetworkInterface nic = nics.nextElement();
            if (nic.isUp() && !nic.isVirtual()) {
                Enumeration<InetAddress> addrs = nic.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();

                    // Currently, seems there's a bug on Linux that one is
                    // unable to get IPv6 datagrams to be received by an
                    // IPv6 socket bound to any address except ::1. So filter
                    // out IPv6 address here. The test should be revisited
                    // later when aforementioned bug gets fixed.
                    if (addr instanceof Inet4Address) {
                        out.printf("%s : %s\n", nic.getName(), addr);
                        testConnectedUDP(addr);
                    }
                }
            }
        }
    }


    /*
     * Connect a UDP socket, disconnect it, then send and recv on it.
     * It will fail on Linux if we don't silently bind(2) again at the
     * end of DatagramSocket.disconnect().
     */
    private static void testConnectedUDP(InetAddress addr) throws Exception {
        try {
            DatagramSocket s = new DatagramSocket(0, addr);
            DatagramSocket ss = new DatagramSocket(0, addr);
            out.println("localaddress: " + s.getLocalSocketAddress());
            out.println("\tconnect...");
            s.connect(ss.getLocalAddress(), ss.getLocalPort());
            out.println("localaddress: " + s.getLocalSocketAddress());
            out.println("disconnect...");
            s.disconnect();
            out.println("localaddress: " + s.getLocalSocketAddress());

            byte[] data = { 0, 1, 2 };
            DatagramPacket p = new DatagramPacket(data, data.length,
                    s.getLocalAddress(), s.getLocalPort());
            s.setSoTimeout( 10000 );
            out.print("send...");
            s.send( p );
            out.print("recv...");
            s.receive( p );
            out.println("OK");

            ss.close();
            s.close();
        } catch( Exception e ){
            e.printStackTrace();
            throw e;
        }
    }

    // Tests with DatagramChannel
//    private static void testConnectedUDPNIO(InetAddress addr) throws Exception {
//        DatagramChannel s = DatagramChannel.open();
//        s.bind(new InetSocketAddress(addr, 0));
//        DatagramChannel ss = DatagramChannel.open();
//        ss.bind(new InetSocketAddress(addr, 0));
//        out.println("localaddress: " + s.getLocalAddress());
//        out.println("\tconnect...");
//        s.connect(ss.getLocalAddress());
//        out.println("localaddress: " + s.getLocalAddress());
//        out.println("disconnect...");
//        s.disconnect();
//        out.println("localaddress: " + s.getLocalAddress());
//
//        byte[] data = {0, 1, 2};
//        out.print("send...");
//        s.send(ByteBuffer.wrap(data), s.getLocalAddress());
//        out.print("recv...");
//        s.receive(ByteBuffer.allocate(100));
//        out.println("OK");
//
//        ss.close();
//        s.close();
//    }
}
