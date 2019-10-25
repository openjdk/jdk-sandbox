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
import java.io.UncheckedIOException;
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
import java.nio.channels.UnixDomainSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
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

public class UnixDomainSocketChannelImpl extends SocketChannelImpl
{
    public UnixDomainSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd);
        if (bound) {
            synchronized (stateLock) {
                this.localAddress = Net.localUnixAddress(fd);
            }
        }
    }

/*
    public static AbstractSocketChannelImpl create(SelectorProvider sp, FileDescriptor fd) {
        try {
            return new UnixDomainSocketChannelImpl(sp, fd, false);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
*/

    // Constructor for sockets obtained from server sockets
    //
    UnixDomainSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, SocketAddress isa)
        throws IOException
    {
        super(sp, fd);
        synchronized (stateLock) {
            this.localAddress = Net.localUnixAddress(fd);
            this.remoteAddress = isa;
            this.state = ST_CONNECTED;
        }
    }

    @Override
    public SocketAddress getLocalAddress() throws IOException {
        synchronized (stateLock) {
            ensureOpen();
            return localAddress; // TODO: revealed local address?
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
            // no options that require special handling
            return (T) Net.getSocketOption(fd, name); // AF_UNIX
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_SNDBUF);
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_REUSEADDR); // TODO: DELETE ME
            set.add(StandardSocketOptions.SO_KEEPALIVE);
            set.add(StandardSocketOptions.SO_LINGER);
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
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
                    if (local == null)
                        throw new NullPointerException(); // TODO: ??
                    UnixDomainSocketAddress usa = Net.checkUnixAddress(local);
                    Net.unixDomainBind(fd, usa);
                    localAddress = Net.localUnixAddress(fd);
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
        return this;
    }

    public Socket socket() {
        throw new UnsupportedOperationException("socket not supported");
    }

    /**
     * Checks the remote address to which this channel is to be connected.
     */
    @Override
    SocketAddress checkRemote(SocketAddress sa) throws IOException {
        SocketAddress isa = Net.checkUnixAddress(sa);
        return isa; // TODO
    }

    @Override
    int connectImpl(FileDescriptor fd, SocketAddress sa) throws IOException {
        UnixDomainSocketAddress isa = (UnixDomainSocketAddress)sa;
        return Net.unixDomainConnect(fd, isa);
    }

    @Override
    SocketAddress localAddressImpl(FileDescriptor fd) throws IOException {
        return Net.localUnixAddress(fd);
    }

    String getRevealedLocalAddressAsString(SocketAddress sa) {
        return sa.toString(); // TODO
    }

}
