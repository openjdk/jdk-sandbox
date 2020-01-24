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

    static <T extends Exception> void call(Command r, boolean mustThrow) {
        boolean threw = false;
        try {
            r.run();
        } catch (Throwable t) {
            threw = true;
            if (!(t instanceof SecurityException)) {
                throw new RuntimeException("wrong exception type thrown " + t.toString());
            }
            if (!mustThrow)
                throw new RuntimeException("an exception was thrown but was not expected");
        }
        if (mustThrow && !threw) {
            // should have thrown
            throw new RuntimeException("SecurityException was expected");
        }
    }

    public static void main(String[] args) throws Exception {
        String policy = args[0];
        switch (policy) {
            case "policy1":
                initDir("sockets");
                initDir("sockets1");
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
                setSecurityManager(policy);
                testPolicy3();
                break;
            default:
        }

    }

    static void setSecurityManager(String policy) {
        String testSrc = System.getProperty("test.src");
        String policyURL = "file://" + testSrc + "/" + policy;
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

    public static void testPolicy1() throws Exception {
        // Permission exists to bind and connect to this
        Path servername = Path.of("sockets", "sock1");
        final UnixDomainSocketAddress saddr = new UnixDomainSocketAddress(servername);
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);
        call(() -> {
            server.bind(saddr);
        }, false);
        call(() -> {
            client.connect(saddr);
        }, false);
        call(() -> {
            SocketChannel s1 = server.accept();
            s1.close();
        }, false);
        close(server, client);

        // Permission to bind but not to connect to sock2
        Path servername1 = Path.of("sockets", "sock2");
        final UnixDomainSocketAddress saddr1 = new UnixDomainSocketAddress(servername1);
        final ServerSocketChannel server1 = ServerSocketChannel.open(UNIX);
        final SocketChannel client1 = SocketChannel.open(UNIX);
        call(() -> {
            server1.bind(saddr1);
        }, false);
        call(() -> {
            client1.connect(saddr1);
        }, true);
        close(server1, client1);
    }

    public static void testPolicy2() throws Exception {
        Path servername = Path.of("server", "sock");
        final UnixDomainSocketAddress saddr = new UnixDomainSocketAddress(servername);
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);
        call(() -> {
            server.bind(saddr);
        }, false);
        call(() -> {
            client.connect(saddr);
        }, false);

        // accept should fail because the client is unnamed
        call(() -> {
            SocketChannel s1 = server.accept();
            s1.close();
        }, true);

        final SocketChannel client1 = SocketChannel.open(UNIX);
        Path clientname = Path.of("client1", "csock");
        final UnixDomainSocketAddress caddr = new UnixDomainSocketAddress(clientname);

        call(() -> {
            client1.bind(caddr);
        }, false);
        call(() -> {
            client1.connect(saddr);
        }, false);

        // accept should succeed because client has expected name "client1/csock"
        call(() -> {
            SocketChannel s1 = server.accept();
            s1.close();
        }, false);

        // Before we create permission to bind to ${java.io.tmpdir} in next test
        // check that we can't bind to a null address here first, connect to it either

        final ServerSocketChannel server1 = ServerSocketChannel.open(UNIX);
        call(() -> {
            server1.bind(null);
        }, true);

        final SocketChannel client2 = SocketChannel.open(UNIX);
        final UnixDomainSocketAddress caddr2 = new UnixDomainSocketAddress(System.getProperty("java.io.tmpdir") + "test");
        call(() -> {
            client2.connect(caddr2);
        }, true);
    }

    public static void testPolicy3() throws Exception {
        final ServerSocketChannel server = ServerSocketChannel.open(UNIX);
        final SocketChannel client = SocketChannel.open(UNIX);

        call(() -> {
            server.bind(null);
        }, false);

        SocketAddress saddr = server.getLocalAddress();

        call(() -> {
            client.connect(saddr);
        }, false);
    }
}
