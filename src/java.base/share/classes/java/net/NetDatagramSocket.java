/*
 * Copyright (c) 1995, 2019, Oracle and/or its affiliates. All rights reserved.
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

package java.net;

import java.io.IOException;
import java.nio.channels.DatagramChannel;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;

/**
 * This class is the legacy implementation of {@link DatagramSocket} based
 * on {@link DatagramSocketImpl}.
 */
class NetDatagramSocket extends DatagramSocket {
    /**
     * Various states of this socket.
     */
    private boolean created = false;
    private boolean bound = false;
    private boolean closed = false;
    private final Object closeLock = new Object();

    /*
     * The implementation of this DatagramSocket.
     */
    DatagramSocketImpl impl;

    /**
     * Are we using an older DatagramSocketImpl?
     */
    boolean oldImpl = false;

    /**
     * Set when a socket is ST_CONNECTED until we are certain
     * that any packets which might have been received prior
     * to calling connect() but not read by the application
     * have been read. During this time we check the source
     * address of all packets received to be sure they are from
     * the connected destination. Other packets are read but
     * silently dropped.
     */
    private boolean explicitFilter = false;
    private int bytesLeftToFilter;
    /*
     * Connection state:
     * ST_NOT_CONNECTED = socket not connected
     * ST_CONNECTED = socket connected
     * ST_CONNECTED_NO_IMPL = socket connected but not at impl level
     */
    static final int ST_NOT_CONNECTED = 0;
    static final int ST_CONNECTED = 1;
    static final int ST_CONNECTED_NO_IMPL = 2;

    int connectState = ST_NOT_CONNECTED;

    /*
     * Connected address & port
     */
    InetAddress connectedAddress = null;
    int connectedPort = -1;
    private final boolean isMulticast;


    static NetDatagramSocket create(SocketAddress bindaddr) throws SocketException {
        return new NetDatagramSocket(bindaddr, false);
    }

    // checks that the provided DatagramSocketImpl is non null, and
    // returns a null DatagramSocket for delegation
    static DatagramSocket nullDatagramSocket(DatagramSocketImpl impl) {
        Objects.requireNonNull(impl);
        return null;
    }

    /**
     * This constructor is used by {@link DatagramSocket#DatagramSocket(DatagramSocketImpl)}.
     * @param impl The impl used in this instance.
     */
    NetDatagramSocket(DatagramSocketImpl impl) {
        // Revisit:
        // The NetDatagramSocket class overrides all the methods
        // defined by DatagramSocket, and thus calls super(null).
        // Whether to pass a non-functional instance of DatagramSocket
        // instead should be considered.
        super(nullDatagramSocket(impl));
        this.isMulticast = false;
        this.impl = impl;
        checkOldImpl();
    }

    /**
     * Construct a new instance of NetDatagramSocket bound to
     * the specified address.
     * @param bindaddr an address to bind to, or null. If null,
     *                 the socket is not bound.
     * @throws SocketException
     */
    // also used by NetMulticastAddress
    NetDatagramSocket(SocketAddress bindaddr, boolean isMulticast) throws SocketException {
        super((DatagramSocket) null);
        this.isMulticast = isMulticast;
        // create a datagram socket.
        createImpl();
        if (bindaddr != null) {
            try {
                bind(bindaddr);
            } finally {
                if (!isBound())
                    close();
            }
        }
    }

    private synchronized void connectInternal(InetAddress address, int port) throws SocketException {
        if (port < 0 || port > 0xFFFF) {
            throw new IllegalArgumentException("connect: " + port);
        }
        if (address == null) {
            throw new IllegalArgumentException("connect: null address");
        }
        checkAddress(address, "connect");
        if (isClosed())
            return;
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            if (address.isMulticastAddress()) {
                security.checkMulticast(address);
            } else {
                security.checkConnect(address.getHostAddress(), port);
                security.checkAccept(address.getHostAddress(), port);
            }
        }

        if (!isBound())
            bind(new InetSocketAddress(0));

        // old impls do not support connect/disconnect
        if (oldImpl || (impl instanceof AbstractPlainDatagramSocketImpl &&
                ((AbstractPlainDatagramSocketImpl) impl).nativeConnectDisabled())) {
            connectState = ST_CONNECTED_NO_IMPL;
        } else {
            try {
                getImpl().connect(address, port);

                // socket is now connected by the impl
                connectState = ST_CONNECTED;
                // Do we need to filter some packets?
                int avail = getImpl().dataAvailable();
                if (avail == -1) {
                    throw new SocketException();
                }
                explicitFilter = avail > 0;
                if (explicitFilter) {
                    bytesLeftToFilter = getReceiveBufferSize();
                }
            } catch (SocketException se) {

                // connection will be emulated by DatagramSocket
                connectState = ST_CONNECTED_NO_IMPL;
            }
        }

        connectedAddress = address;
        connectedPort = port;
    }

    private void checkOldImpl() {
        if (impl == null)
            return;
        // DatagramSocketImpl.peekData() is a protected method, therefore we need to use
        // getDeclaredMethod, therefore we need permission to access the member
        try {
            AccessController.doPrivileged(
                    new PrivilegedExceptionAction<>() {
                        public Void run() throws NoSuchMethodException {
                            Class<?>[] cl = new Class<?>[1];
                            cl[0] = DatagramPacket.class;
                            impl.getClass().getDeclaredMethod("peekData", cl);
                            return null;
                        }
                    });
        } catch (java.security.PrivilegedActionException e) {
            oldImpl = true;
        }
    }

    static Class<?> implClass = null;

    void createImpl() throws SocketException {
        DatagramSocketImplFactory factory = DatagramSocket.factory;
        if (impl == null) {
            if (factory != null) {
                impl = factory.createDatagramSocketImpl();
                checkOldImpl();
            } else {
                impl = DefaultDatagramSocketImplFactory.createDatagramSocketImpl(isMulticast);

                checkOldImpl();
            }
        }
        // creates a udp socket
        impl.create();
        created = true;
    }

    /**
     * Get the {@code DatagramSocketImpl} attached to this socket,
     * creating it if necessary.
     *
     * @return the {@code DatagramSocketImpl} attached to that
     * DatagramSocket
     * @throws SocketException if creation fails.
     */
    // also used by NetMulticastSocket
    DatagramSocketImpl getImpl() throws SocketException {
        if (!created)
            createImpl();
        return impl;
    }

    @Override
    public final synchronized void bind(SocketAddress addr) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (isBound())
            throw new SocketException("already bound");
        if (addr == null)
            addr = new InetSocketAddress(0);
        if (!(addr instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type!");
        InetSocketAddress epoint = (InetSocketAddress) addr;
        if (epoint.isUnresolved())
            throw new SocketException("Unresolved address");
        InetAddress iaddr = epoint.getAddress();
        int port = epoint.getPort();
        checkAddress(iaddr, "bind");
        SecurityManager sec = System.getSecurityManager();
        if (sec != null) {
            sec.checkListen(port);
        }
        try {
            getImpl().bind(port, iaddr);
        } catch (SocketException e) {
            getImpl().close();
            throw e;
        }
        bound = true;
    }

    // also used by NetMulticastSocket
    static void checkAddress(InetAddress addr, String op) {
        if (addr == null) {
            return;
        }
        if (!(addr instanceof Inet4Address || addr instanceof Inet6Address)) {
            throw new IllegalArgumentException(op + ": invalid address type");
        }
    }

    @Override
    public final void connect(InetAddress address, int port) {
        try {
            connectInternal(address, port);
        } catch (SocketException se) {
            throw new Error("connect failed", se);
        }
    }

    @Override
    public final void connect(SocketAddress addr) throws SocketException {
        if (addr == null)
            throw new IllegalArgumentException("Address can't be null");
        if (!(addr instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type");
        InetSocketAddress epoint = (InetSocketAddress) addr;
        if (epoint.isUnresolved())
            throw new SocketException("Unresolved address");
        connectInternal(epoint.getAddress(), epoint.getPort());
    }

    @Override
    public final void disconnect() {
        synchronized (this) {
            if (isClosed())
                return;
            if (connectState == ST_CONNECTED) {
                impl.disconnect();
            }
            connectedAddress = null;
            connectedPort = -1;
            connectState = ST_NOT_CONNECTED;
            explicitFilter = false;
        }
    }

    @Override
    public final boolean isBound() {
        return bound;
    }

    @Override
    public final boolean isConnected() {
        return connectState != ST_NOT_CONNECTED;
    }

    @Override
    public final InetAddress getInetAddress() {
        return connectedAddress;
    }

    @Override
    public final int getPort() {
        return connectedPort;
    }

    @Override
    public final SocketAddress getRemoteSocketAddress() {
        if (!isConnected())
            return null;
        return new InetSocketAddress(getInetAddress(), getPort());
    }

    @Override
    public final SocketAddress getLocalSocketAddress() {
        if (isClosed())
            return null;
        if (!isBound())
            return null;
        return new InetSocketAddress(getLocalAddress(), getLocalPort());
    }

    @Override
    public final void send(DatagramPacket p) throws IOException {
        synchronized (p) {
            if (isClosed())
                throw new SocketException("Socket is closed");
            InetAddress packetAddress = p.getAddress();
            checkAddress(packetAddress, "send");
            if (connectState == ST_NOT_CONNECTED) {
                if (packetAddress == null) {
                    throw new IllegalArgumentException("Address not set");
                }
                // check the address is ok with the security manager on every send.
                SecurityManager security = System.getSecurityManager();

                // The reason you want to synchronize on datagram packet
                // is because you don't want an applet to change the address
                // while you are trying to send the packet for example
                // after the security check but before the send.
                if (security != null) {
                    if (packetAddress.isMulticastAddress()) {
                        security.checkMulticast(packetAddress);
                    } else {
                        security.checkConnect(packetAddress.getHostAddress(),
                                              p.getPort());
                    }
                }
            } else {
                // we're connected
                if (packetAddress == null) {
                    p.setAddress(connectedAddress);
                    p.setPort(connectedPort);
                } else if ((!packetAddress.equals(connectedAddress)) ||
                        p.getPort() != connectedPort) {
                    throw new IllegalArgumentException("connected address " +
                            "and packet address" +
                            " differ");
                }
            }
            // Check whether the socket is bound
            if (!isBound())
                bind(new InetSocketAddress(0));
            // call the  method to send
            getImpl().send(p);
        }
    }

    @Override
    public final synchronized void receive(DatagramPacket p) throws IOException {
        synchronized (p) {
            if (!isBound())
                bind(new InetSocketAddress(0));
            if (connectState == ST_NOT_CONNECTED) {
                // check the address is ok with the security manager before every recv.
                SecurityManager security = System.getSecurityManager();
                if (security != null) {
                    while (true) {
                        String peekAd = null;
                        int peekPort = 0;
                        // peek at the packet to see who it is from.
                        if (!oldImpl) {
                            // We can use the new peekData() API
                            DatagramPacket peekPacket = new DatagramPacket(new byte[1], 1);
                            peekPort = getImpl().peekData(peekPacket);
                            peekAd = peekPacket.getAddress().getHostAddress();
                        } else {
                            InetAddress adr = new InetAddress();
                            peekPort = getImpl().peek(adr);
                            peekAd = adr.getHostAddress();
                        }
                        try {
                            security.checkAccept(peekAd, peekPort);
                            // security check succeeded - so now break
                            // and recv the packet.
                            break;
                        } catch (SecurityException se) {
                            // Throw away the offending packet by consuming
                            // it in a tmp buffer.
                            DatagramPacket tmp = new DatagramPacket(new byte[1], 1);
                            getImpl().receive(tmp);

                            // silently discard the offending packet
                            // and continue: unknown/malicious
                            // entities on nets should not make
                            // runtime throw security exception and
                            // disrupt the applet by sending random
                            // datagram packets.
                            continue;
                        }
                    } // end of while
                }
            }
            DatagramPacket tmp = null;
            if ((connectState == ST_CONNECTED_NO_IMPL) || explicitFilter) {
                // We have to do the filtering the old fashioned way since
                // the native impl doesn't support connect or the connect
                // via the impl failed, or .. "explicitFilter" may be set when
                // a socket is connected via the impl, for a period of time
                // when packets from other sources might be queued on socket.
                boolean stop = false;
                while (!stop) {
                    InetAddress peekAddress = null;
                    int peekPort = -1;
                    // peek at the packet to see who it is from.
                    if (!oldImpl) {
                        // We can use the new peekData() API
                        DatagramPacket peekPacket = new DatagramPacket(new byte[1], 1);
                        peekPort = getImpl().peekData(peekPacket);
                        peekAddress = peekPacket.getAddress();
                    } else {
                        // this api only works for IPv4
                        peekAddress = new InetAddress();
                        peekPort = getImpl().peek(peekAddress);
                    }
                    if ((!connectedAddress.equals(peekAddress)) ||
                            (connectedPort != peekPort)) {
                        // throw the packet away and silently continue
                        tmp = new DatagramPacket(
                                new byte[1024], 1024);
                        getImpl().receive(tmp);
                        if (explicitFilter) {
                            if (checkFiltering(tmp)) {
                                stop = true;
                            }
                        }
                    } else {
                        stop = true;
                    }
                }
            }
            // If the security check succeeds, or the datagram is
            // connected then receive the packet
            getImpl().receive(p);
            if (explicitFilter && tmp == null) {
                // packet was not filtered, account for it here
                checkFiltering(p);
            }
        }
    }

    private boolean checkFiltering(DatagramPacket p) throws SocketException {
        bytesLeftToFilter -= p.getLength();
        if (bytesLeftToFilter <= 0 || getImpl().dataAvailable() <= 0) {
            explicitFilter = false;
            return true;
        }
        return false;
    }

    @Override
    public final InetAddress getLocalAddress() {
        if (isClosed())
            return null;
        InetAddress in = null;
        try {
            in = (InetAddress) getImpl().getOption(SocketOptions.SO_BINDADDR);
            if (in.isAnyLocalAddress()) {
                in = InetAddress.anyLocalAddress();
            }
            SecurityManager s = System.getSecurityManager();
            if (s != null) {
                s.checkConnect(in.getHostAddress(), -1);
            }
        } catch (Exception e) {
            in = InetAddress.anyLocalAddress(); // "0.0.0.0"
        }
        return in;
    }

    @Override
    public final int getLocalPort() {
        if (isClosed())
            return -1;
        try {
            return getImpl().getLocalPort();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    public final synchronized void setSoTimeout(int timeout) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (timeout < 0)
            throw new IllegalArgumentException("timeout < 0");
        getImpl().setOption(SocketOptions.SO_TIMEOUT, timeout);
    }

    @Override
    public final synchronized int getSoTimeout() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        if (getImpl() == null)
            return 0;
        Object o = getImpl().getOption(SocketOptions.SO_TIMEOUT);
        /* extra type safety */
        if (o instanceof Integer) {
            return ((Integer) o).intValue();
        } else {
            return 0;
        }
    }

    @Override
    public final synchronized void setSendBufferSize(int size)
            throws SocketException {
        if (!(size > 0)) {
            throw new IllegalArgumentException("negative send size");
        }
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_SNDBUF, size);
    }

    @Override
    public final synchronized int getSendBufferSize() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        int result = 0;
        Object o = getImpl().getOption(SocketOptions.SO_SNDBUF);
        if (o instanceof Integer) {
            result = ((Integer) o).intValue();
        }
        return result;
    }

    @Override
    public final synchronized void setReceiveBufferSize(int size)
            throws SocketException {
        if (size <= 0) {
            throw new IllegalArgumentException("invalid receive size");
        }
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_RCVBUF, size);
    }

    @Override
    public final synchronized int getReceiveBufferSize()
            throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        int result = 0;
        Object o = getImpl().getOption(SocketOptions.SO_RCVBUF);
        if (o instanceof Integer) {
            result = ((Integer) o).intValue();
        }
        return result;
    }

    @Override
    public final synchronized void setReuseAddress(boolean on) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        // Integer instead of Boolean for compatibility with older DatagramSocketImpl
        if (oldImpl)
            getImpl().setOption(SocketOptions.SO_REUSEADDR, on ? -1 : 0);
        else
            getImpl().setOption(SocketOptions.SO_REUSEADDR, Boolean.valueOf(on));
    }

    @Override
    public final synchronized boolean getReuseAddress() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        Object o = getImpl().getOption(SocketOptions.SO_REUSEADDR);
        return ((Boolean) o).booleanValue();
    }

    @Override
    public final synchronized void setBroadcast(boolean on) throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(SocketOptions.SO_BROADCAST, Boolean.valueOf(on));
    }

    @Override
    public final synchronized boolean getBroadcast() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return ((Boolean) (getImpl().getOption(SocketOptions.SO_BROADCAST))).booleanValue();
    }

    @Override
    public final synchronized void setTrafficClass(int tc) throws SocketException {
        if (tc < 0 || tc > 255)
            throw new IllegalArgumentException("tc is not in range 0 -- 255");

        if (isClosed())
            throw new SocketException("Socket is closed");
        try {
            getImpl().setOption(SocketOptions.IP_TOS, tc);
        } catch (SocketException se) {
            // not supported if socket already connected
            // Solaris returns error in such cases
            if (!isConnected())
                throw se;
        }
    }

    @Override
    public final synchronized int getTrafficClass() throws SocketException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return ((Integer) (getImpl().getOption(SocketOptions.IP_TOS))).intValue();
    }

    @Override
    public final void close() {
        synchronized (closeLock) {
            if (isClosed())
                return;
            impl.close();
            closed = true;
        }
    }

    @Override
    public final boolean isClosed() {
        synchronized (closeLock) {
            return closed;
        }
    }

    @Override
    public final DatagramChannel getChannel() {
        return null;
    }

    @Override
    public final  <T> DatagramSocket setOption(SocketOption<T> name, T value)
            throws IOException {
        Objects.requireNonNull(name);
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setOption(name, value);
        return this;
    }

    @Override
    public final <T> T getOption(SocketOption<T> name) throws IOException {
        Objects.requireNonNull(name);
        if (isClosed())
            throw new SocketException("Socket is closed");
        return getImpl().getOption(name);
    }

    private static Set<SocketOption<?>> options;
    private static boolean optionsSet = false;

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        synchronized (NetDatagramSocket.class) {
            if (optionsSet) {
                return options;
            }
            try {
                DatagramSocketImpl impl = getImpl();
                options = Collections.unmodifiableSet(impl.supportedOptions());
            } catch (IOException e) {
                options = Collections.emptySet();
            }
            optionsSet = true;
            return options;
        }
    }
}
