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
 * @summary Combinations of bad SocketImpls
 * @run testng/othervm BadSocketImpls
 * @run testng/othervm -Djdk.net.usePlainSocketImpl BadSocketImpls
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketImpl;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import static java.lang.String.format;
import static java.lang.System.out;
import static org.testng.Assert.fail;

public class BadSocketImpls {

    Class<IOException> IOE = IOException.class;

    /**
     * Tests that a server-side custom socket impl, whose accept is a no-op,
     * does not have any adverse negative effects. Default accept impl.
     */
    @Test
    public void testNoOpAccept() throws IOException {
        ServerSocket ss = new ServerSocket(new NoOpSocketImpl()) { };
        try (ss) {
            ss.bind(new InetSocketAddress(0));
            assertThrows(IOE, ss::accept);
        }
    }

    /**
     * Tests that a server-side custom socket impl, whose accept is a no-op,
     * does not have any adverse negative effects. Custom accept, client has
     * an explicit null impl.
     */
    @Test
    public void testNoOpAcceptWithNullClientImpl() throws IOException {
        ServerSocket ss = new ServerSocket(new NoOpSocketImpl()) {
            @Override
            public Socket accept() throws IOException {
                Socket s = new Socket((SocketImpl)null) { };
                implAccept(s);
                return s;
            }
        };
        try (ss) {
            ss.bind(new InetSocketAddress(0));
            assertThrows(IOE, ss::accept);
        }
    }

    /**
     * Tests that a server-side custom socket impl, whose accept is a no-op,
     * does not have any adverse negative effects. Custom accept, client has
     * a platform impl.
     */
    @Test
    public void testNoOpAcceptWithPlatformClientImpl() throws IOException {
        ServerSocket ss = new ServerSocket(new NoOpSocketImpl()) {
            @Override
            public Socket accept() throws IOException {
                Socket s = new Socket();
                implAccept(s);
                return s;
            }
        };
        try (ss) {
            ss.bind(new InetSocketAddress(0));
            assertThrows(IOE, ss::accept);
        }
    }

    static class NoOpSocketImpl extends SocketImpl {
        @Override protected void create(boolean b) { }
        @Override protected void connect(String s, int i) { }
        @Override protected void connect(InetAddress inetAddress, int i) { }
        @Override protected void connect(SocketAddress socketAddress, int i) { }
        @Override protected void bind(InetAddress inetAddress, int i) { }
        @Override protected void listen(int i) { }
        @Override protected void accept(SocketImpl socket) { }
        @Override protected InputStream getInputStream() { return null; }
        @Override protected OutputStream getOutputStream() { return null; }
        @Override protected int available() { return 0; }
        @Override protected void close() { }
        @Override protected void sendUrgentData(int i) { }
        @Override public void setOption(int i, Object o) { }
        @Override public Object getOption(int i) { return null; }
    }

    static <T extends Throwable> void assertThrows(Class<T> throwableClass,
                                                   ThrowingRunnable runnable) {
        try {
            runnable.run();
            fail(format("Expected %s to be thrown, but nothing was thrown",
                        throwableClass.getSimpleName()));
        } catch (Throwable t) {
            if (!throwableClass.isInstance(t)) {
                fail(format("Expected %s to be thrown, but %s was thrown",
                            throwableClass.getSimpleName(),
                            t.getClass().getSimpleName()),
                     t);
            }
            out.println("caught expected exception: " + t);
        }
    }

    interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @BeforeMethod
    public void breakBetweenTests() {
        System.out.println("\n-------\n");
    }
}
