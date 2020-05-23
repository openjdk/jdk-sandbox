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
 * @run main/othervm Security policy1
 * @run main/othervm Security policy2
 * @run main/othervm Security policy3
 * @run main/othervm Security policy4
 * @summary Security test for Unix Domain socket and server socket channels
 */

import java.io.File;
import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;

import static java.net.StandardProtocolFamily.UNIX;

/**
 * Tests required all with security manager
 */

public class Security {

    static interface Command {
        public void run() throws Exception;
    }

    static <T extends Exception> void call(Command r, Class<? extends Exception> expectedException) {
        boolean threw = false;
        try {
            r.run();
        } catch (Throwable t) {
            if (expectedException == null) {
                t.printStackTrace();
                throw new RuntimeException("an exception was thrown but was not expected");
            }
            threw = true;
            if (!(expectedException.isAssignableFrom(t.getClass()))) {
                throw new RuntimeException("wrong exception type thrown " + t.toString());
            }
        }
        if (expectedException != null && !threw) {
            // should have thrown
            throw new RuntimeException("SecurityException was expected");
        }
    }


    public static void main(String[] args) throws Exception {
        try {SocketChannel.open(); } catch (java.io.IOException e) {}

        int namelen = Integer.parseInt(System.getProperty("jdk.nio.channels.unixdomain.maxnamelength"));
        if (namelen == -1) {
            System.out.println("Unix domain not supported");
            return;
        }
        String policy = args[0];
        switch (policy) {
            case "policy1":
                initDir("sockets");
                initDir("sockets2");
                setSecurityManager(policy);
                testPolicy1();
                break;
            case "policy2":
                initDir("server");
                initDir("client1");
                setSecurityManager(policy);
                testPolicy2();
                break;
            case "policy3":
                initDir("sockets");
                setSecurityManager(policy);
                testPolicy3();
                break;
            case "policy4":
                initDir("sockets");
                setSecurityManager(policy);
                testPolicy4();
                break;
            default:
        }

    }

    static void setSecurityManager(String policy) {
        String testSrc = System.getProperty("test.src");
        // Three /// required for Windows below
        String policyURL = "file:///" + testSrc + File.separator + policy;
        System.out.println("POLICY: " + policyURL);
        System.setProperty("java.security.policy", policyURL);
        System.setSecurityManager(new SecurityManager());
    }

    // initialization prior to setting security manager: remove directory and recreate
    // it empty
    static void initDir(String directory) throws IOException {
        Path dirname = Path.of(directory);

        if (Files.exists(dirname)) {
            Files.walk(dirname)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        }
        Files.createDirectory(dirname);
    }

    static void close(NetworkChannel... channels) {

        for (NetworkChannel chan : channels) {
            try {
                chan.close();
            } catch (Exception e) {
            }
        }
    }

    private static final Class<SecurityException> SE = SecurityException.class;
    private static final Class<IOException> IOE = IOException.class;

    // can bind a server only

    public static void testPolicy1() throws Exception {
        // Permission exists to bind a ServerSocketChannel
        Path servername = Path.of("sockets", "sock1");
        Path servername2 = Path.of("sockets2", "sock1");
        final UnixDomainSocketAddress saddr = UnixDomainSocketAddress.of(servername);
        final UnixDomainSocketAddress saddr2 = UnixDomainSocketAddress.of(servername2);
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final ServerSocketChannel server2 = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);
        call(() -> {
            server.bind(saddr);
        }, null);
        call(() -> {
            server2.bind(saddr2);
        }, SE);
        call(() -> {
            client.connect(saddr);
        }, SE);
        close(server, client, server2);

        final ServerSocketChannel server3 = ServerSocketChannel.open(UNIX);
        call(() -> {
            server3.bind(null);
        }, SE);
        close(server3);
    }

    // can bind a client only

    public static void testPolicy2() throws Exception {
        Path servername = Path.of("sockets", "sock");
        final UnixDomainSocketAddress saddr = UnixDomainSocketAddress.of(servername);
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);
        call(() -> {
            server.bind(saddr);
        }, SE);
        call(() -> {
            client.connect(saddr);
        }, IOE);
        close(server, client);
    }

    // can bind both server and client to a specific diriectory only

    public static void testPolicy3() throws Exception {
        Path servername = Path.of("sockets", "sock1");
        Path servername2 = Path.of("sockets2", "sock1");
        final UnixDomainSocketAddress saddr = UnixDomainSocketAddress.of(servername);
        final UnixDomainSocketAddress saddr2 = UnixDomainSocketAddress.of(servername2);
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final ServerSocketChannel server2 = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);
        final SocketChannel client2 = SocketChannel.open(UNIX);

        call(() -> {
            server.bind(saddr);
        }, null);

        call(() -> {
            client.connect(saddr);
        }, null);

        call(() -> {
            server2.bind(saddr2);
        }, SE);

        call(() -> {
            client2.connect(saddr2);
        }, SE);
        close(server, client, server2, client2);
    }

    public static void testPolicy4() throws Exception {
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);
        call(() -> {
            server.bind(null);
        }, null);

        SocketAddress addr = server.getLocalAddress();
        System.out.println("testPolicy4: connecting to " + addr);

        call(() -> {
            client.connect(addr);
        }, null);

        close(server, client);
    }

}
