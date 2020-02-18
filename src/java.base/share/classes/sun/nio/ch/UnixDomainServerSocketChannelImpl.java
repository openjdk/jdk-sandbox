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

import java.io.File;
import java.io.FileDescriptor;
import java.io.FilePermission;
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
import java.nio.file.Path;
import java.security.AccessController;
import java.security.NoSuchAlgorithmException;
import java.security.PrivilegedAction;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Random;
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
    public UnixDomainServerSocketChannelImpl(SelectorProvider sp) throws IOException {
        super(sp, Net.unixDomainSocket(), false);
    }

    public UnixDomainServerSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd, bound);
    }

    SocketAddress localAddressImpl(FileDescriptor fd) throws IOException {
        return Net.localUnixAddress(fd);
    }

    @Override
    public ServerSocket socket() {
        throw new UnsupportedOperationException("socket not supported");
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
    public SocketAddress bindImpl(SocketAddress local, int backlog) throws IOException {
        boolean found = false;
        SecurityManager sm = System.getSecurityManager();

        if (local == null && sm != null) {
            // only needs to be done once
            checkTempDirPermission(sm);
        }

        // Attempt up to 10 times to find an unused name in temp directory
        // Unlikely to fail
        for (int i = 0; i < 10; i++) {
            UnixDomainSocketAddress usa = null;
            if (local == null) {
                usa = getTempName();
            } else {
                usa = Net.checkUnixAddress(local);
                if (sm != null) {
                    Path parent = privilegedGetParent(usa.getPath());
                    FilePermission p = new FilePermission(parent.toString(), "write");
                    sm.checkPermission(p);
                }
            }
            try {
                Net.unixDomainBind(getFD(), usa);
                found = true;
                break;
            } catch (BindException e) {
                if (local != null) {
                    throw e;
                }
            }
        }
        if (!found)
            throw new IOException("could not bind to temporary name");
        Net.listen(getFD(), backlog < 1 ? 50 : backlog);
        return Net.localUnixAddress(getFD());
    }

    private static Path privilegedGetParent(Path path) {
        return AccessController.doPrivileged(
            (PrivilegedAction<Path>) () -> {
                return path
                    .normalize()
                    .toAbsolutePath()
                    .getParent();
            }
        );
    }

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

    private static Random getRandom() {
        return AccessController.doPrivileged(
            (PrivilegedAction<Random>) () -> {
                try {
                    return SecureRandom.getInstance("NativePRNGNonBlocking");
                } catch (NoSuchAlgorithmException e) {
                    return new SecureRandom(); // This should not fail
                }
            }
        );
    }

    private static final String tempDir = getTempDir();
    private static final FilePermission tempDirPermission = new FilePermission(tempDir, "write");
    private static final Random random = getRandom();;
    private static final long pid = AccessController.doPrivileged(
        (PrivilegedAction<Long>)UnixDomainServerSocketChannelImpl::getPid);

    private static long getPid() {
        return ProcessHandle.current().pid();
    }

    private static void checkTempDirPermission(SecurityManager sm) {
        if (sm != null) {
            sm.checkPermission(tempDirPermission);
        }
    }

    /**
     * Return a possible temporary name to bind to, which is different for each call
     * Name is of the form <temp dir>/niosocket_<pid>_<random>
     */
    private static UnixDomainSocketAddress getTempName() throws IOException {
        int rnd = random.nextInt(Integer.MAX_VALUE);
        StringBuilder sb = new StringBuilder();
        sb.append(tempDir).append("niosocket_").append(pid).append('_').append(rnd);
        return new UnixDomainSocketAddress(sb.toString());
    }

    @Override
    protected int acceptImpl(FileDescriptor fd, FileDescriptor newfd, SocketAddress[] addrs)
        throws IOException
    {
        return Net.unixDomainAccept(fd, newfd, addrs);
    }

    @Override
    String getRevealedLocalAddressAsString(SocketAddress addr) {
        return Net.getRevealedLocalAddressAsString((UnixDomainSocketAddress)addr);
    }

    @Override
    SocketAddress getRevealedLocalAddress(SocketAddress addr) {
        return Net.getRevealedLocalAddress((UnixDomainSocketAddress)addr);
    }

    SocketChannel finishAcceptImpl(FileDescriptor newfd, SocketAddress sa)
        throws IOException
    {
        UnixDomainSocketAddress usa = (UnixDomainSocketAddress)sa;
        SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            String path = usa.getPathName();
            FilePermission p = new FilePermission(path, "read,write");
            sm.checkPermission(p);
        }
        return new UnixDomainSocketChannelImpl(provider(), newfd, usa);
    }

    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getName());
        sb.append('[');
        if (!isOpen()) {
            sb.append("closed");
        } else {
            UnixDomainSocketAddress addr = (UnixDomainSocketAddress) localAddress();
            if (addr == null) { // TODO: ???
                sb.append("unbound");
            } else {
                sb.append(getRevealedLocalAddressAsString(addr));
            }
        }
        sb.append(']');
        return sb.toString();
    }
}
