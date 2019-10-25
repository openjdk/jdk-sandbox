/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.NoConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.SelectorProvider;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import sun.net.ConnectionResetException;
import sun.net.NetHooks;
import sun.net.ext.ExtendedSocketOptions;
import sun.net.util.SocketExceptions;

/**
 * An implementation of SocketChannels
 */

class InetSocketChannelImpl extends SocketChannelImpl
{
    // set true when exclusive binding is on and SO_REUSEADDR is emulated
    private boolean isReuseAddress;

    // Constructor for normal connecting sockets
    //
    InetSocketChannelImpl(SelectorProvider sp) throws IOException {
        super(sp);
    }

    InetSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd);
        if (bound) {
            synchronized (stateLock) {
                this.localAddress = Net.localAddress(fd);
            }
        }
    }

    // Constructor for sockets obtained from server sockets
    //
    InetSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, InetSocketAddress isa)
        throws IOException
    {
        super(sp, fd);
        synchronized (stateLock) {
            this.localAddress = Net.localAddress(fd);
            this.remoteAddress = isa;
            this.state = ST_CONNECTED;
        }
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            return Net.getRevealedLocalAddress((InetSocketAddress)localAddress);
        }
    }

    @Override
    public SocketAddress getRemoteAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            return remoteAddress;
        }
    }

    @Override
    public <T> SocketChannel setOption(SocketOption<T> name, T value)
        throws IOException
    {
        Objects.requireNonNull(name);
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");
        if (!name.type().isInstance(value))
            throw new IllegalArgumentException("Invalid value '" + value + "'");

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOptions.IP_TOS) {
                ProtocolFamily family = Net.isIPv6Available() ?
                    StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
                Net.setSocketOption(fd, family, name, value);
                return this;
            }

            if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
                // SO_REUSEADDR emulated when using exclusive bind
                isReuseAddress = (Boolean)value;
                return this;
            }

            // no options that require special handling
            Net.setSocketOption(fd, name, value);
            return this;
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getOption(SocketOption<T> name)
        throws IOException
    {
        Objects.requireNonNull(name);
        if (!supportedOptions().contains(name))
            throw new UnsupportedOperationException("'" + name + "' not supported");

        synchronized (stateLock) {
            ensureOpen();

            if (name == StandardSocketOptions.SO_REUSEADDR && Net.useExclusiveBind()) {
                // SO_REUSEADDR emulated when using exclusive bind
                return (T)Boolean.valueOf(isReuseAddress);
            }

            // special handling for IP_TOS: always return 0 when IPv6
            if (name == StandardSocketOptions.IP_TOS) {
                ProtocolFamily family = Net.isIPv6Available() ?
                    StandardProtocolFamily.INET6 : StandardProtocolFamily.INET;
                return (T) Net.getSocketOption(fd, family, name);
            }

            // no options that require special handling
            return (T) Net.getSocketOption(fd, name);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_SNDBUF);
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_KEEPALIVE);
            set.add(StandardSocketOptions.SO_REUSEADDR);
            if (Net.isReusePortAvailable()) {
                set.add(StandardSocketOptions.SO_REUSEPORT);
            }
            set.add(StandardSocketOptions.SO_LINGER);
            set.add(StandardSocketOptions.TCP_NODELAY);
            // additional options required by socket adaptor
            set.add(StandardSocketOptions.IP_TOS);
            set.add(ExtendedSocketOption.SO_OOBINLINE);
            set.addAll(ExtendedSocketOptions.clientSocketOptions());
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    @Override
    public Socket socket() {
        synchronized (stateLock) {
            if (socket == null)
                socket = SocketAdaptor.create(this);
            return socket;
        }
    }

    /**
     * Writes a byte of out of band data.
     */
    int sendOutOfBandData(byte b) throws IOException {
        writeLock.lock();
        try {
            boolean blocking = isBlocking();
            int n = 0;
            try {
                beginWrite(blocking);
                if (blocking) {
                    do {
                        n = Net.sendOOB(fd, b);
                    } while (n == IOStatus.INTERRUPTED && isOpen());
                } else {
                    n = Net.sendOOB(fd, b);
                }
            } finally {
                endWrite(blocking, n > 0);
                if (n <= 0 && isOutputClosed)
                    throw new AsynchronousCloseException();
            }
            return IOStatus.normalize(n);
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    public SocketChannel bind(SocketAddress local) throws IOException {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                synchronized (stateLock) {
                    ensureOpen();
                    if (state == ST_CONNECTIONPENDING)
                        throw new ConnectionPendingException();
                    if (localAddress != null)
                        throw new AlreadyBoundException();
                    InetSocketAddress isa = (local == null) ?
                        new InetSocketAddress(0) : Net.checkAddress(local);
                    SecurityManager sm = System.getSecurityManager();
                    if (sm != null) {
                        sm.checkListen(isa.getPort());
                    }
                    NetHooks.beforeTcpBind(fd, isa.getAddress(), isa.getPort());
                    Net.bind(fd, isa.getAddress(), isa.getPort());
                    localAddress = Net.localAddress(fd);
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
        return this;
    }


    /**
     * Checks the remote address to which this channel is to be connected.
     */
    @Override
    protected SocketAddress checkRemote(SocketAddress sa) throws IOException {
        InetSocketAddress isa = Net.checkAddress(sa);
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkConnect(isa.getAddress().getHostAddress(), isa.getPort());
        }
        if (isa.getAddress().isAnyLocalAddress()) {
            return new InetSocketAddress(InetAddress.getLocalHost(), isa.getPort());
        } else {
            return isa;
        }
    }

    @Override
    protected int connectImpl(FileDescriptor fd, SocketAddress sa) throws IOException {
        InetSocketAddress isa = (InetSocketAddress)sa;
        return Net.connect(fd, isa.getAddress(), isa.getPort());
    }

    @Override
    protected SocketAddress localAddressImpl(FileDescriptor fd) throws IOException {
        return Net.localAddress(fd);
    }

    @Override
    protected String getRevealedLocalAddressAsString(SocketAddress sa) {
        InetSocketAddress isa = (InetSocketAddress)sa;
        return Net.getRevealedLocalAddressAsString(isa);
    }
}
