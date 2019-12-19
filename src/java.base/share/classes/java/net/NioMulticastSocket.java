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
import java.nio.channels.MembershipKey;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

/**
 * This class is the new implementation of {@link MulticastSocket} based
 * on {@link DatagramChannel}.
 */
final class NioMulticastSocket extends MulticastSocket {
    // used to coordinate changing TTL with the deprecated send method
    private final ReentrantLock sendLock = new ReentrantLock();

    // cached outgoing interface (for use by setInterface/getInterface)
    private final Object outgoingInterfaceLock = new Object();
    private NetworkInterface outgoingNetworkInterface;
    private InetAddress outgoingInetAddress;

    // membership keys for the groups that the socket has joined
    private final Set<MembershipKey> keys = new HashSet<>();

    static  NioMulticastSocket create(SocketAddress bindaddr) throws SocketException {
        return new NioMulticastSocket(DatagramSocket.createChannel(bindaddr, true));
    }

    private NioMulticastSocket(DatagramChannel channel) throws SocketException {
        super(channel.socket());
    }

    @Deprecated
    @Override
    public final void setTTL(byte ttl) throws IOException {
        setTimeToLive(Byte.toUnsignedInt(ttl));
    }

    @Override
    public final void setTimeToLive(int ttl) throws IOException {
        sendLock.lock();
        try {
            socket().setOption(StandardSocketOptions.IP_MULTICAST_TTL, ttl);
        } finally {
            sendLock.unlock();
        }
    }

    @Override
    @Deprecated
    public final byte getTTL() throws IOException {
        return (byte) getTimeToLive();
    }

    @Override
    public final int getTimeToLive() throws IOException {
        sendLock.lock();
        try {
            return socket().getOption(StandardSocketOptions.IP_MULTICAST_TTL);
        } finally {
            sendLock.unlock();
        }
    }

    @Override
    @Deprecated
    public final void joinGroup(InetAddress mcastaddr) throws IOException {
        InetAddress group = Objects.requireNonNull(mcastaddr);
        joinGroup(new InetSocketAddress(group, 0), defaultNetworkInterface());
    }

    @Override
    @Deprecated
    public final void leaveGroup(InetAddress mcastaddr) throws IOException {
        InetAddress group = Objects.requireNonNull(mcastaddr);
        leaveGroup(new InetSocketAddress(group, 0), defaultNetworkInterface());
    }

    @Override
    public final void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf)
            throws IOException {
        if (mcastaddr == null || !(mcastaddr instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type");
        InetAddress group = ((InetSocketAddress) mcastaddr).getAddress();
        if (group == null)
            throw new IllegalArgumentException("Unresolved address");
        if (!group.isMulticastAddress())
            throw new SocketException("Not a multicast address");
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkMulticast(group);
        if (isClosed())
            throw new SocketException("Socket is closed");

        // join the group on the specified (or default) network interface
        NetworkInterface ni = (netIf != null) ? netIf : defaultNetworkInterface();
        // socket should be of type DatagramSocketAdaptor
        DatagramChannel channel = socket().getChannel();
        assert channel != null;
        synchronized (keys) {
            MembershipKey key = channel.join(group, ni);
            boolean added = keys.add(key);
            if (!added) {
                throw new SocketException("Already a member of group");
            }
        }
    }

    @Override
    public final void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf)
            throws IOException {
        if (mcastaddr == null || !(mcastaddr instanceof InetSocketAddress))
            throw new IllegalArgumentException("Unsupported address type");
        InetAddress group = ((InetSocketAddress) mcastaddr).getAddress();
        if (group == null)
            throw new IllegalArgumentException("Unresolved address");
        if (!group.isMulticastAddress())
            throw new SocketException("Not a multicast address");
        SecurityManager sm = System.getSecurityManager();
        if (sm != null)
            sm.checkMulticast(group);
        if (isClosed())
            throw new SocketException("Socket is closed");

        // drop member of the group on the specified (or default) network interface
        NetworkInterface ni = (netIf != null) ? netIf : defaultNetworkInterface();
        synchronized (keys) {
            MembershipKey key = keys.stream()
                    .filter(k -> k.group().equals(group) && k.networkInterface().equals(ni))
                    .findAny()
                    .orElseThrow(() -> new SocketException("Not a member of group"));
            key.drop();
            keys.remove(key);
        }
    }

    @Override
    @Deprecated
    public final void setInterface(InetAddress inf) throws SocketException {
        NetworkInterface ni = NetworkInterface.getByInetAddress(inf);
        if (ni == null)
            throw new SocketException(inf.getHostAddress() + " not found");
        synchronized (outgoingInterfaceLock) {
            // set interface and update cached values
            setNetworkInterface(ni);
            outgoingNetworkInterface = ni;
            outgoingInetAddress = inf;
        }
    }

    @Override
    @Deprecated
    public final InetAddress getInterface() throws SocketException {
        synchronized (outgoingInterfaceLock) {
            NetworkInterface ni = outgoingNetworkInterface();
            if (ni != null) {
                if (ni.equals(outgoingNetworkInterface)) {
                    return outgoingInetAddress;
                } else {
                    // network interface has changed so update cached values
                    PrivilegedAction<InetAddress> pa;
                    pa = () -> ni.inetAddresses().findFirst().orElse(null);
                    InetAddress ia = AccessController.doPrivileged(pa);
                    if (ia == null)
                        throw new SocketException("Network interface has no IP address");
                    outgoingNetworkInterface = ni;
                    outgoingInetAddress = ia;
                    return ia;
                }
            }
        }
        // return anyLocalAddress to match long standing behavior
        return InetAddress.anyLocalAddress();
    }

    @Override
    public final void setNetworkInterface(NetworkInterface netIf) throws SocketException {
        try {
            setOption(StandardSocketOptions.IP_MULTICAST_IF, netIf);
        } catch (IOException e) {
            rethrowAsSocketException(e);
        }
    }

    @Override
    public final NetworkInterface getNetworkInterface() throws SocketException {
        NetworkInterface ni = outgoingNetworkInterface();
        if (ni == null) {
            // return a dummy NetworkInterface to match long standing behavior
            ni = dummyNetworkInterface();
        }
        return ni;
    }

    @Override
    @Deprecated
    public final void setLoopbackMode(boolean disable) throws SocketException {
        try {
            boolean enable = !disable;
            setOption(StandardSocketOptions.IP_MULTICAST_LOOP, enable);
        } catch (IOException ioe) {
            rethrowAsSocketException(ioe);
        }
    }

    @Override
    @Deprecated
    public final boolean getLoopbackMode() throws SocketException {
        try {
            boolean enabled = getOption(StandardSocketOptions.IP_MULTICAST_LOOP);
            return !enabled;
        } catch (IOException ioe) {
            return rethrowAsSocketException(ioe);
        }
    }

    @Override
    @Deprecated
    public final void send(DatagramPacket p, byte ttl) throws IOException {
        sendLock.lock();
        try {
            int oldValue = getTimeToLive();
            try {
                setTTL(ttl);
                super.send(p);
            } finally {
                setTimeToLive(oldValue);
            }
        } finally {
            sendLock.unlock();
        }
    }

    @Override
    public final void send(DatagramPacket p) throws IOException {
        sendLock.lock();
        try {
            super.send(p);
        } finally {
            sendLock.unlock();
        }
    }

    /**
     * Returns the default NetworkInterface to use when joining or leaving a
     * multicast group and a network interface is not specified.
     * This method will return the outgoing NetworkInterface if set, otherwise
     * the result of NetworkInterface.getDefault() or a dummy interface.
     */
    private NetworkInterface defaultNetworkInterface() throws SocketException {
        NetworkInterface ni = outgoingNetworkInterface();
        if (ni == null)
            ni = NetworkInterface.getDefault();
        if (ni == null)
            ni = dummyNetworkInterface();
        return ni;
    }

    /**
     * Returns the outgoing NetworkInterface or null if not set.
     */
    private NetworkInterface outgoingNetworkInterface() throws SocketException {
        try {
            return getOption(StandardSocketOptions.IP_MULTICAST_IF);
        } catch (IOException ioe) {
            return rethrowAsSocketException(ioe);
        }
    }

    /**
     * Return a NetworkInterface with index == 0.
     */
    private NetworkInterface dummyNetworkInterface() {
        InetAddress[] addrs = new InetAddress[1];
        addrs[0] = InetAddress.anyLocalAddress();
        return new NetworkInterface(addrs[0].getHostName(), 0, addrs);
    }

    /**
     * Throw a SocketException with the given IOException as cause.
     */
    private <T> T rethrowAsSocketException(IOException ioe) throws SocketException {
        SocketException e = new SocketException(ioe.getMessage());
        e.initCause(ioe);
        throw e;
    }

    /**
     * {@inheritDoc}
     * Drops any group membership (best effort) before closing.
     */
    @Override
    public void close() {
        synchronized (keys) {
            try {
                keys.forEach(MembershipKey::drop);
            } finally {
                keys.clear();
                super.close();
            }
        }
    }
}
