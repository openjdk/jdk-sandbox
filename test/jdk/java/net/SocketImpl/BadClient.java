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
 * @modules java.base/java.net:+open java.base/sun.nio.ch:+open
 * @run testng/othervm BadClient
 * @summary Test the SocketImpl for scenarios that do not arise with Socket or
 *          ServerSocket
 */

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOption;
import java.net.StandardSocketOptions;
import java.util.Set;

import org.testng.annotations.Test;
import static org.testng.Assert.*;

@Test
public class BadClient {

    /**
     * Test create when already created
     */
    public void testCreate1() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            expectThrows(IOException.class, () -> impl.create(true));
        }
    }

    /**
     * Test create when closed
     */
    public void testCreate2() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        expectThrows(IOException.class, () -> impl.create(true));
    }

    /**
     * Test connect when not created
     */
    public void testConnect1() throws IOException {
        try (var ss = new ServerSocket(0)) {
            var impl = new PlatformSocketImpl(false);
            var remote = ss.getLocalSocketAddress();
            expectThrows(IOException.class, () -> impl.connect(remote, 0));
        }
    }

    /**
     * Test connect with unsupported address type
     */
    public void testConnect2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var remote = new SocketAddress() { };
            expectThrows(IOException.class, () -> impl.connect(remote, 0));
        }
    }

    /**
     * Test connect with an unresolved address
     */
    public void testConnect3() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var remote = new InetSocketAddress("blah-blah.blah-blah", 80);
            expectThrows(IOException.class, () -> impl.connect(remote, 0));
        }
    }

    /**
     * Test connect when already connected
     */
    public void testConnect4() throws IOException {
        try (var ss = new ServerSocket(0);
             var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            impl.connect(ss.getLocalSocketAddress(), 0);
            var remote = ss.getLocalSocketAddress();
            expectThrows(IOException.class, () -> impl.connect(remote, 0));
        }
    }

    /**
     * Test connect when impl is closed
     */
    public void testConnect5() throws IOException {
        try (var ss = new ServerSocket(0)) {
            var impl = new PlatformSocketImpl(false);
            impl.close();
            var remote = ss.getLocalSocketAddress();
            expectThrows(IOException.class, () -> impl.connect(remote, 0));
        }
    }

    /**
     * Test connect(String host, int port)
     */
    public void testConnect6() throws IOException {
        try (var ss = new ServerSocket(0);
             var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            impl.connect(ss.getInetAddress().getHostAddress(), ss.getLocalPort());
        }
    }

    /**
     * Test connect(InetAddress address, int port)
     */
    public void testConnect7() throws IOException {
        try (var ss = new ServerSocket(0);
             var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            impl.connect(ss.getInetAddress(), ss.getLocalPort());
        }
    }

    /**
     * Test bind when not created
     */
    public void testBind1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        var loopback = InetAddress.getLoopbackAddress();
        expectThrows(IOException.class, () -> impl.bind(loopback, 0));
    }

    /**
     * Test bind when already bound
     */
    public void testBind2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var loopback = InetAddress.getLoopbackAddress();
            impl.bind(loopback, 0);
            expectThrows(IOException.class, () -> impl.bind(loopback, 0));
        }
    }

    /**
     * Test bind when connected
     */
    public void testBind3() throws IOException {
        try (var ss = new ServerSocket(0);
             var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            impl.connect(ss.getLocalSocketAddress(), 0);
            var loopback = InetAddress.getLoopbackAddress();
            expectThrows(IOException.class, () -> impl.bind(loopback, 0));
        }
    }

    /**
     * Test bind when closed
     */
    public void testBind4() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        var loopback = InetAddress.getLoopbackAddress();
        expectThrows(IOException.class, () -> impl.bind(loopback, 0));
    }


    /**
     * Test listen when not created
     */
    public void testListen1() {
        var impl = new PlatformSocketImpl(false);
        expectThrows(IOException.class, () -> impl.listen(16));
    }

    /**
     * Test listen when not bound
     */
    public void testListen2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            expectThrows(IOException.class, () -> impl.listen(16));
        }
    }

    /**
     * Test listen when closed
     */
    public void testListen3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        expectThrows(IOException.class, () -> impl.listen(16));
    }

    /**
     * Test accept when not created
     */
    public void testAccept1() throws IOException {
        var impl = new PlatformSocketImpl(true);
        var si = new PlatformSocketImpl(false);
        expectThrows(IOException.class, () -> impl.accept(si));
    }

    /**
     * Test accept when not bound
     */
    public void testAccept2() throws IOException {
        try (var impl = new PlatformSocketImpl(true)) {
            impl.create(true);
            var si = new PlatformSocketImpl(false);
            expectThrows(IOException.class, () -> impl.accept(si));
        }
    }

    /**
     * Test accept when not a stream socket
     */
    public void testAccept3() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(false);
            impl.bind(InetAddress.getLoopbackAddress(), 0);
            var si = new PlatformSocketImpl(false);
            expectThrows(IOException.class, () -> impl.accept(si));
        }
    }

    /**
     * Test accept when closed
     */
    public void testAccept4() throws IOException {
        var impl = new PlatformSocketImpl(true);
        impl.close();
        var si = new PlatformSocketImpl(false);
        expectThrows(IOException.class, () -> impl.accept(si));
    }

    /**
     * Test accept with SocketImpl that is already created
     */
    public void testAccept5() throws IOException {
        try (var impl = new PlatformSocketImpl(true);
             var si = new PlatformSocketImpl(false)) {
            impl.create(true);
            impl.bind(InetAddress.getLoopbackAddress(), 0);
            si.create(true);
            expectThrows(IOException.class, () -> impl.accept(si));
        }
    }

    /**
     * Test accept with SocketImpl that is closed
     */
    public void testAccept6() throws IOException {
        try (var impl = new PlatformSocketImpl(true);
             var si = new PlatformSocketImpl(false)) {
            impl.create(true);
            impl.bind(InetAddress.getLoopbackAddress(), 0);
            si.create(true);
            si.close();
            expectThrows(IOException.class, () -> impl.accept(si));
        }
    }

    /**
     * Test available when not created
     */
    public void testAvailable1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        expectThrows(IOException.class, () -> impl.available());
    }

    /**
     * Test available when created but not connected
     */
    public void testAvailable2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            expectThrows(IOException.class, () -> impl.available());
        }
    }

    /**
     * Test available when closed
     */
    public void testAvailable3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        expectThrows(IOException.class, () -> impl.available());
    }


    /**
     * Test setOption with unsupported option
     */
    public void testSetOption1() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var opt = new SocketOption<String>() {
                @Override public String name() { return "birthday"; }
                @Override public Class<String> type() { return String.class; }
            };
            expectThrows(UnsupportedOperationException.class, () -> impl.setOption(opt, "today"));
        }
    }

    /**
     * Test setOption when not created
     */
    public void testSetOption2() throws IOException {
        var impl = new PlatformSocketImpl(false);
        var opt = StandardSocketOptions.SO_REUSEADDR;
        expectThrows(IOException.class, () -> impl.setOption(opt, true));
    }

    /**
     * Test setOption when closed
     */
    public void testSetOption3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        var opt = StandardSocketOptions.SO_REUSEADDR;
        expectThrows(IOException.class, () -> impl.setOption(opt, true));
    }

    /**
     * Test getOption with unsupported option
     */
    public void testGetOption1() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            var opt = new SocketOption<String>() {
                @Override public String name() { return "birthday"; }
                @Override public Class<String> type() { return String.class; }
            };
            expectThrows(UnsupportedOperationException.class, () -> impl.getOption(opt));
        }
    }

    /**
     * Test getOption when not created
     */
    public void testGetOption2() throws IOException {
        var impl = new PlatformSocketImpl(false);
        var opt = StandardSocketOptions.SO_REUSEADDR;
        expectThrows(IOException.class, () -> impl.getOption(opt));
    }

    /**
     * Test getOption when closed
     */
    public void testGetOption3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        var opt = StandardSocketOptions.SO_REUSEADDR;
        expectThrows(IOException.class, () -> impl.getOption(opt));
    }

    /**
     * Test shutdownInput when not created
     */
    public void testShutdownInput1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        expectThrows(IOException.class, () -> impl.shutdownInput());
    }

    /**
     * Test shutdownInput when not connected
     */
    public void testShutdownInput2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            expectThrows(IOException.class, () -> impl.shutdownInput());
        }
    }

    /**
     * Test shutdownInput when closed
     */
    public void testShutdownInput3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        expectThrows(IOException.class, () -> impl.shutdownInput());
    }

    /**
     * Test shutdownOutput when not created
     */
    public void testShutdownOutput1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        expectThrows(IOException.class, () -> impl.shutdownOutput());
    }

    /**
     * Test shutdownOutput when not connected
     */
    public void testShutdownOutput2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            expectThrows(IOException.class, () -> impl.shutdownOutput());
        }
    }

    /**
     * Test shutdownOutput when closed
     */
    public void testShutdownOutput3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        expectThrows(IOException.class, () -> impl.shutdownOutput());
    }

    /**
     * Test sendUrgentData when not created
     */
    public void testSendUrgentData1() throws IOException {
        var impl = new PlatformSocketImpl(false);
        expectThrows(IOException.class, () -> impl.sendUrgentData(0));
    }

    /**
     * Test sendUrgentData when not connected
     */
    public void testSendUrgentData2() throws IOException {
        try (var impl = new PlatformSocketImpl(false)) {
            impl.create(true);
            expectThrows(IOException.class, () -> impl.sendUrgentData(0));
        }
    }

    /**
     * Test sendUrgentData when closed
     */
    public void testSendUrgentData3() throws IOException {
        var impl = new PlatformSocketImpl(false);
        impl.close();
        expectThrows(IOException.class, () -> impl.sendUrgentData(0));
    }


    /**
     * A SocketImpl that delegates all operations to a NioSocketImpl.
     */
    static class PlatformSocketImpl extends SocketImpl implements AutoCloseable {
        private final SocketImpl impl;

        PlatformSocketImpl(boolean server) {
            try {
                Class<?> clazz = Class.forName("sun.nio.ch.NioSocketImpl");
                Constructor<?> ctor = clazz.getConstructor(boolean.class);
                this.impl = (SocketImpl) ctor.newInstance(server);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void close() throws IOException {
            find("close").invoke(impl);
        }

        @Override
        protected void create(boolean stream) throws IOException {
            find("create", boolean.class).invoke(impl, stream);
        }

        @Override
        protected void connect(SocketAddress remote, int millis) throws IOException {
            find("connect", SocketAddress.class, int.class).invoke(impl, remote, millis);
        }

        @Override
        protected void connect(String host, int port) throws IOException {
            find("connect", String.class, int.class).invoke(impl, host, port);
        }

        @Override
        protected void connect(InetAddress address, int port) throws IOException {
            find("connect", InetAddress.class, int.class).invoke(impl, address, port);
        }

        @Override
        protected void bind(InetAddress address, int port) throws IOException {
            find("bind", InetAddress.class, int.class).invoke(impl, address, port);
        }

        @Override
        protected void listen(int backlog) throws IOException {
            find("listen", int.class).invoke(impl, backlog);
        }

        @Override
        protected void accept(SocketImpl si) throws IOException {
            find("accept", SocketImpl.class).invoke(this.impl, ((PlatformSocketImpl) si).impl);
        }

        @Override
        protected InputStream getInputStream() throws IOException {
            return (InputStream) find("getInputStream").invoke(impl);
        }

        @Override
        protected OutputStream getOutputStream() throws IOException {
            return (OutputStream) find("getOutputStream").invoke(impl);
        }

        @Override
        protected int available() throws IOException {
            return (int) find("available").invoke(impl);
        }

        @Override
        protected Set<SocketOption<?>> supportedOptions() {
            try {
                return (Set<SocketOption<?>>) find("supportedOptions").invoke(impl);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        protected <T> void setOption(SocketOption<T> opt, T value) throws IOException {
            find("setOption", SocketOption.class, Object.class).invoke(impl, opt, value);
        }

        @Override
        protected <T> T getOption(SocketOption<T> opt) throws IOException {
            return (T) find("getOption", SocketOption.class).invoke(impl, opt);
        }

        @Override
        public void setOption(int opt, Object value) throws SocketException {
            try {
                find("setOption", int.class, Object.class).invoke(impl, opt, value);
            } catch (SocketException e) {
                throw e;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        public Object getOption(int opt) throws SocketException {
            try {
                return find("getOption", int.class).invoke(impl, opt);
            } catch (SocketException e) {
                throw e;
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        protected void shutdownInput() throws IOException {
            find("shutdownInput").invoke(impl);
        }

        @Override
        protected void shutdownOutput() throws IOException {
            find("shutdownOutput").invoke(impl);
        }

        @Override
        protected boolean supportsUrgentData() {
            try {
                return (boolean) find("supportsUrgentData").invoke(impl);
            } catch (IOException ioe) {
                throw new RuntimeException(ioe);
            }
        }

        @Override
        protected void sendUrgentData(int data) throws IOException {
            find("sendUrgentData", int.class).invoke(impl, data);
        }

        private MethodInvoker find(String name, Class<?>... paramTypes) {
            try {
                Method m = SocketImpl.class.getDeclaredMethod(name, paramTypes);
                m.setAccessible(true);
                return new MethodInvoker(m);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        static class MethodInvoker {
            private final Method method;

            MethodInvoker(Method method) {
                this.method = method;
            }

            Object invoke(Object target, Object... args) throws IOException {
                try {
                    return method.invoke(target, args);
                } catch (InvocationTargetException e) {
                    Throwable cause = e.getCause();
                    if (cause instanceof IOException) {
                        throw (IOException) cause;
                    } else if (cause instanceof RuntimeException) {
                        throw (RuntimeException) cause;
                    } else if (cause instanceof Error) {
                        throw (Error) cause;
                    } else {
                        throw new RuntimeException(e);
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}
