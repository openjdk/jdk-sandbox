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

/**
 * @test
 * @bug 8231358
 * @run main SendChannels
 * @requires (os.family != "windows")
 * @summary SendChannels
 */

import jdk.net.ExtendedSocketOptions;

import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.LinkedList;
import java.util.List;

/**
 * Tests:
 * 1. basic functionality
 * 2. create two processes (p = parent, c = child). Connect c to p thru
 *    unix socket. Create {inet,unix} x {socketchannel,server socket} send
 *    from p to c; then from c to p.
 * 3. try send datagram channel, file channel: should fail
 * 4. interaction of SNDCHAN and PEERCRED
 *
 *
 */
public class SendChannels {

    public static void main(String[] args) throws Exception {
        basic(args);
    }

    private static ServerSocketChannel getUnixServer() throws IOException {
        ServerSocketChannel server = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        server.bind(null);
        return server;
    }



    /**
     * Create two socket channels (IP and Unix domain)
     * through a Unix Domain SocketChannel and check that read/write still works.
     */
    public static void basic(String[] args) throws Exception {
        ServerSocketChannel server = getUnixServer();
        ServerSocketChannel server1 = ServerSocketChannel.open();
        server1.bind(null);
        SocketChannel a1 = SocketChannel.open(server1.getLocalAddress());
        SocketChannel a2 = server1.accept();
        SocketAddress saddr = server.getLocalAddress();
        SocketChannel c1 = SocketChannel.open(saddr);
        SocketChannel c2 = server.accept();
        c2.setOption(ExtendedSocketOptions.SO_RCVCHAN_ENABLE, true);

        UnixDomainSocketAddress usa = (UnixDomainSocketAddress)server.getLocalAddress();
        var buf = ByteBuffer.wrap("Hello wurdled".getBytes());
        SocketChannel c3 = SocketChannel.open(saddr);
        Files.deleteIfExists(usa.getPath());
        SocketChannel c4 = server.accept();
        c1.setOption(ExtendedSocketOptions.SO_SNDCHAN, c3);
        c1.setOption(ExtendedSocketOptions.SO_SNDCHAN, a1);
        c1.write(buf);
        c1.close();
        ByteBuffer rx = ByteBuffer.allocate(128);
        c2.read(rx);
        rx.flip();
        List<SocketChannel> l = new LinkedList<>();
        SocketChannel x1;
        while ((x1 = (SocketChannel)c2.getOption(ExtendedSocketOptions.SO_SNDCHAN)) != null) {
            l.add(x1);
            System.out.println("Received: " + x1);
        }
        if (l.size() != 2) {
            throw new RuntimeException("Expected two channels");
        }
        System.out.println(rx);
        String s = new String(rx.array(), rx.arrayOffset()+rx.position(), rx.limit());
        System.out.println(s);
    }
}

