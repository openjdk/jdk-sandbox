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
import java.util.Collections;
import java.util.Enumeration;
import java.util.Set;
/**
 * This class is the legacy implementation of {@link MulticastSocket} based
 * on {@link DatagramSocketImpl}.
 */
final class NetMulticastSocket extends MulticastSocket {

    /**
     * Used on some platforms to record if an outgoing interface
     * has been set for this socket.
     */
    private boolean interfaceSet;

    /**
     * The lock on the socket's TTL. This is for set/getTTL and
     * send(packet,ttl).
     */
    private final Object ttlLock = new Object();

    /**
     * The lock on the socket's interface - used by setInterface
     * and getInterface
     */
    private final Object infLock = new Object();

    /**
     * The "last" interface set by setInterface on this MulticastSocket
     */
    private InetAddress infAddress = null;

    static NetMulticastSocket create(SocketAddress bindaddr) throws SocketException {
        return new NetMulticastSocket(bindaddr);
    }

    private NetMulticastSocket(SocketAddress bindaddr) throws SocketException {
        // pass null so that super will not bind...
        super(new NetDatagramSocket(null, true));

        // Enable SO_REUSEADDR before binding
        setReuseAddress(true);

        if (bindaddr != null) {
            try {
                bind(bindaddr);
            } finally {
                if (!isBound()) {
                    close();
                }
            }
        }
    }

    final DatagramSocketImpl getImpl() throws SocketException {
        return ((NetDatagramSocket)socket()).getImpl();
    }

    final boolean oldImpl() throws SocketException {
        return ((NetDatagramSocket)socket()).oldImpl;
    }

    final void checkAddress(InetAddress addr, String op) {
        NetDatagramSocket.checkAddress(addr, op);
    }

    final int connectState() {
        return ((NetDatagramSocket)socket()).connectState;
    }

    final InetAddress connectedAddress() {
        return ((NetDatagramSocket)socket()).connectedAddress;
    }

    final int connectedPort() {
        return ((NetDatagramSocket)socket()).connectedPort;
    }

    @Deprecated
    @Override
    public final void setTTL(byte ttl) throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setTTL(ttl);
    }

    @Override
    public final void setTimeToLive(int ttl) throws IOException {
        if (ttl < 0 || ttl > 255) {
            throw new IllegalArgumentException("ttl out of range");
        }
        if (isClosed())
            throw new SocketException("Socket is closed");
        getImpl().setTimeToLive(ttl);
    }

    @Deprecated
    @Override
    public final byte getTTL() throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return getImpl().getTTL();
    }

    @Override
    public final int getTimeToLive() throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");
        return getImpl().getTimeToLive();
    }

    @Override
    @Deprecated
    public final void joinGroup(InetAddress mcastaddr) throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }

        checkAddress(mcastaddr, "joinGroup");
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkMulticast(mcastaddr);
        }

        if (!mcastaddr.isMulticastAddress()) {
            throw new SocketException("Not a multicast address");
        }

        /**
         * required for some platforms where it's not possible to join
         * a group without setting the interface first.
         */
        NetworkInterface defaultInterface = NetworkInterface.getDefault();

        if (!interfaceSet && defaultInterface != null) {
            setNetworkInterface(defaultInterface);
        }

        getImpl().join(mcastaddr);
    }

    @Override
    @Deprecated
    public final void leaveGroup(InetAddress mcastaddr) throws IOException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }

        checkAddress(mcastaddr, "leaveGroup");
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkMulticast(mcastaddr);
        }

        if (!mcastaddr.isMulticastAddress()) {
            throw new SocketException("Not a multicast address");
        }

        getImpl().leave(mcastaddr);
    }

    @Override
    public final void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf)
        throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");

        if (mcastaddr == null || !(mcastaddr instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type");

        if (oldImpl())
            throw new UnsupportedOperationException();

        checkAddress(((InetSocketAddress)mcastaddr).getAddress(), "joinGroup");
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkMulticast(((InetSocketAddress)mcastaddr).getAddress());
        }

        if (!((InetSocketAddress)mcastaddr).getAddress().isMulticastAddress()) {
            throw new SocketException("Not a multicast address");
        }

        getImpl().joinGroup(mcastaddr, netIf);
    }

    @Override
    public final void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf)
        throws IOException {
        if (isClosed())
            throw new SocketException("Socket is closed");

        if (mcastaddr == null || !(mcastaddr instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type");

        if (oldImpl())
            throw new UnsupportedOperationException();

        checkAddress(((InetSocketAddress)mcastaddr).getAddress(), "leaveGroup");
        SecurityManager security = System.getSecurityManager();
        if (security != null) {
            security.checkMulticast(((InetSocketAddress)mcastaddr).getAddress());
        }

        if (!((InetSocketAddress)mcastaddr).getAddress().isMulticastAddress()) {
            throw new SocketException("Not a multicast address");
        }

        getImpl().leaveGroup(mcastaddr, netIf);
     }

    @Override
    @Deprecated
    public final void setInterface(InetAddress inf) throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        checkAddress(inf, "setInterface");
        synchronized (infLock) {
            getImpl().setOption(SocketOptions.IP_MULTICAST_IF, inf);
            infAddress = inf;
            interfaceSet = true;
        }
    }

    @Override
    @Deprecated
    public final InetAddress getInterface() throws SocketException {
        if (isClosed()) {
            throw new SocketException("Socket is closed");
        }
        synchronized (infLock) {
            InetAddress ia =
                (InetAddress)getImpl().getOption(SocketOptions.IP_MULTICAST_IF);

            /**
             * No previous setInterface or interface can be
             * set using setNetworkInterface
             */
            if (infAddress == null) {
                return ia;
            }

            /**
             * Same interface set with setInterface?
             */
            if (ia.equals(infAddress)) {
                return ia;
            }

            /**
             * Different InetAddress from what we set with setInterface
             * so enumerate the current interface to see if the
             * address set by setInterface is bound to this interface.
             */
            try {
                NetworkInterface ni = NetworkInterface.getByInetAddress(ia);
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    if (addr.equals(infAddress)) {
                        return infAddress;
                    }
                }

                /**
                 * No match so reset infAddress to indicate that the
                 * interface has changed via means
                 */
                infAddress = null;
                return ia;
            } catch (Exception e) {
                return ia;
            }
        }
    }

    @Override
    public final void setNetworkInterface(NetworkInterface netIf)
        throws SocketException {

        synchronized (infLock) {
            getImpl().setOption(SocketOptions.IP_MULTICAST_IF2, netIf);
            infAddress = null;
            interfaceSet = true;
        }
    }

    @Override
    public final NetworkInterface getNetworkInterface() throws SocketException {
        NetworkInterface ni
            = (NetworkInterface)getImpl().getOption(SocketOptions.IP_MULTICAST_IF2);
        if (ni == null) {
            InetAddress[] addrs = new InetAddress[1];
            addrs[0] = InetAddress.anyLocalAddress();
            return new NetworkInterface(addrs[0].getHostName(), 0, addrs);
        } else {
            return ni;
        }
    }

    @Override
    @Deprecated
    public final void setLoopbackMode(boolean disable) throws SocketException {
        getImpl().setOption(SocketOptions.IP_MULTICAST_LOOP, Boolean.valueOf(disable));
    }

    @Override
    @Deprecated
    public final boolean getLoopbackMode() throws SocketException {
        return ((Boolean)getImpl().getOption(SocketOptions.IP_MULTICAST_LOOP)).booleanValue();
    }

    @Deprecated
    @Override
    public final void send(DatagramPacket p, byte ttl)
        throws IOException {
            if (isClosed())
                throw new SocketException("Socket is closed");
            synchronized(ttlLock) {
                synchronized(p) {
                    InetAddress packetAddress = p.getAddress();
                    checkAddress(packetAddress, "send");
                    if (connectState() == NetDatagramSocket.ST_NOT_CONNECTED) {
                        if (packetAddress == null) {
                            throw new IllegalArgumentException("Address not set");
                        }
                        // Security manager makes sure that the multicast address
                        // is allowed one and that the ttl used is less
                        // than the allowed maxttl.
                        SecurityManager security = System.getSecurityManager();
                        if (security != null) {
                            if (packetAddress.isMulticastAddress()) {
                                security.checkMulticast(packetAddress, ttl);
                            } else {
                                security.checkConnect(packetAddress.getHostAddress(),
                                                      p.getPort());
                            }
                        }
                    } else {
                        // we're connected
                        if (packetAddress == null) {
                            p.setAddress(connectedAddress());
                            p.setPort(connectedPort());
                        } else if ((!packetAddress.equals(connectedAddress())) ||
                                   p.getPort() != connectedPort()) {
                            throw new IllegalArgumentException("connected address and packet address" +
                                                        " differ");
                        }
                    }
                    byte dttl = getTTL();
                    try {
                        if (ttl != dttl) {
                            // set the ttl
                            getImpl().setTTL(ttl);
                        }
                        // call the datagram method to send
                        getImpl().send(p);
                    } finally {
                        // set it back to default
                        if (ttl != dttl) {
                            getImpl().setTTL(dttl);
                        }
                    }
                } // synch p
            }  //synch ttl
    } //method

    private static Set<SocketOption<?>> options;
    private static boolean optionsSet = false;

    @Override
    public Set<SocketOption<?>> supportedOptions() {
        synchronized (NetMulticastSocket.class) {
            if (optionsSet) {
                return options;
            }
            try {
                DatagramSocketImpl impl = getImpl();
                options = Collections.unmodifiableSet(impl.supportedOptions());
            } catch (SocketException ex) {
                options = Collections.emptySet();
            }
            optionsSet = true;
            return options;
        }
    }
}
