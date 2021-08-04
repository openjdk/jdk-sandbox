/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

import java.io.IOException;
import java.net.*;

/**
 * Snippets used in DatagramSocketSnippets.
 */ 

final class DatagramSocketSnippets {

private static void snippet3() throws IOException {
// @start region=snippet3 :
    DatagramSocket sender = new DatagramSocket(new InetSocketAddress(0));
    NetworkInterface outgoingIf = NetworkInterface.getByName("en0");
    sender.setOption(StandardSocketOptions.IP_MULTICAST_IF, outgoingIf);

    // optionally configure multicast TTL; the TTL defines the scope of a
    // multicast datagram, for example, confining it to host local (0) or
    // link local (1) etc...
    int ttl = 2; // a number betwen 0 and 255 //@replace regex="2" replacement="..."
    sender.setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);

    // send a packet to a multicast group
    byte[] msgBytes = null; //@replace regex="null" replacement="..."
    InetAddress mcastaddr = InetAddress.getByName("228.5.6.7");
    int port = 6789;
    InetSocketAddress dest = new InetSocketAddress(mcastaddr, port);
    DatagramPacket hi = new DatagramPacket(msgBytes, msgBytes.length, dest);
    sender.send(hi);
// @end snippet3
}

private static void snippet4() throws IOException {
// @start region=snippet4 :
    DatagramSocket socket = new DatagramSocket(null); // unbound
    socket.setReuseAddress(true); // set reuse address before binding
    socket.bind(new InetSocketAddress(6789)); // bind

    // joinGroup 228.5.6.7
    InetAddress mcastaddr = InetAddress.getByName("228.5.6.7");
    InetSocketAddress group = new InetSocketAddress(mcastaddr, 0);
    NetworkInterface netIf = NetworkInterface.getByName("en0");
    socket.joinGroup(group, netIf);
    byte[] msgBytes = new byte[1024]; // up to 1024 bytes
    DatagramPacket packet = new DatagramPacket(msgBytes, msgBytes.length);
    socket.receive(packet);
    //... //@replace regex="//" replacement=""
    // eventually leave group
    socket.leaveGroup(group, netIf);
// @end snippet4
}

}
