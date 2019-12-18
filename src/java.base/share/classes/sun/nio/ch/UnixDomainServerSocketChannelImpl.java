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
import java.net.BindException;
import java.net.ServerSocket;
import java.net.SocketAddress;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.channels.AlreadyBoundException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.IllegalBlockingModeException;
import java.nio.channels.NotYetBoundException;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.UnixDomainSocketAddress;
import java.nio.channels.spi.SelectorProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import sun.net.NetHooks;
import sun.net.ext.ExtendedSocketOptions;

/**
 * An implementation of ServerSocketChannels
 */

// TODO: Security checks

public class UnixDomainServerSocketChannelImpl
    extends ServerSocketChannelImpl
{
    static {
        // register with InheritedChannel mechanism so it can create instances
        // not yet sun.nio.ch.InheritedChannel.register(UnixDomainServerSocketChannelImpl::create);
    }

    public UnixDomainServerSocketChannelImpl(SelectorProvider sp) throws IOException {
        super(sp, Net.unixDomainSocket());
    }

    public UnixDomainServerSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd);
        if (bound) {
            synchronized (stateLock) {
                localAddress = Net.localUnixAddress(fd);
            }
        }
    }

    @Override
    public ServerSocket socket() {
        throw new UnsupportedOperationException("socket not supported");
    }

    @Override
    public <T> ServerSocketChannel setOption(SocketOption<T> name, T value)
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
            Net.setSocketOption(fd, Net.UNSPEC, name, value);
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
            return (T) Net.getSocketOption(fd, Net.UNSPEC, name);
        }
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_RCVBUF);
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    @Override
    public ServerSocketChannel bind(SocketAddress local, int backlog) throws IOException {

        synchronized (stateLock) {
            for (int i=0; i<100; i++) { // bail out after 100
                if (localAddress != null)
                    throw new AlreadyBoundException();
                UnixDomainSocketAddress usa = null;
                if (local == null) {
                    usa = getTempName();
                } else {
                    usa = Net.checkUnixAddress(local);
                }
                try {
                    Net.unixDomainBind(fd, usa);
                    break;
                } catch (BindException e) {
                    if (local != null) {
                        throw e;
                    }
                    if (i == 99)
                        throw new IOException("could not bind to temporary name", e);
                }
            }
            Net.listen(fd, backlog < 1 ? 50 : backlog);
            localAddress = Net.localUnixAddress(fd);
        }
        return this;
    }

    private static final AtomicInteger nameCounter = new AtomicInteger(1);

    private static String getTempDir() {
        return AccessController.doPrivileged(
            (PrivilegedAction<String>) () -> {
		String s = System.getProperty("java.io.tmpdir");
		String sep = System.getProperty("file.separator");
		if (!s.endsWith(sep))
		    s = s + sep;
		return s;
	    }
        );
    }

    private static final String tempDir = getTempDir();

    /**
     * Return a possible temporary name to bind to, which is different for each call
     */
    private static UnixDomainSocketAddress getTempName() throws IOException {
        long pid = ProcessHandle.current().pid();
        int counter = nameCounter.getAndIncrement();
        StringBuilder sb = new StringBuilder();
        sb.append(tempDir).append("socket_").append(pid).append('_').append(counter);
        return new UnixDomainSocketAddress(sb.toString());
    }

    @Override
    int implAccept(FileDescriptor fd, FileDescriptor newfd, SocketAddress[] addrs)
        throws IOException
    {

        return Net.unixDomainAccept(this.fd, newfd, addrs);
    }

    @Override
    String getRevealedLocalAddressAsString(SocketAddress addr) {
        // TODO
        return addr.toString();
    }

    @Override
    SocketAddress getRevealedLocalAddress(SocketAddress addr) {
        return addr; // TODO
    }

    SocketChannel finishAccept(FileDescriptor newfd, SocketAddress isa)
        throws IOException
    {
        try {
            // newly accepted socket is initially in blocking mode
            IOUtil.configureBlocking(newfd, true);
            return new UnixDomainSocketChannelImpl(provider(), newfd, isa);
        } catch (Exception e) {
            nd.close(newfd);
            throw e;
        }
    }

    /**
     * Returns the local address, or null if not bound
     */
    SocketAddress localAddress() {
        synchronized (stateLock) {
            return localAddress;
        }
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName());
        sb.append('[');
        if (!isOpen()) {
            sb.append("closed");
        } else {
            synchronized (stateLock) {
                UnixDomainSocketAddress addr = (UnixDomainSocketAddress)localAddress;
                if (addr == null) {
                    sb.append("unbound");
                } else {
                    sb.append(getRevealedLocalAddressAsString(addr));
                }
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
