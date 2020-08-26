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
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetPermission;
import java.net.ProtocolFamily;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.net.UnixDomainSocketAddress;
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
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.access.SharedSecrets;

import sun.net.ConnectionResetException;
import sun.net.NetHooks;
import sun.net.ext.ExtendedSocketOptions;
import sun.net.util.SocketExceptions;

/**
 * An implementation of SocketChannels
 */

public class UnixDomainSocketChannelImpl extends SocketChannelImpl
{
    private static final JavaIOFileDescriptorAccess fdAccess =
            SharedSecrets.getJavaIOFileDescriptorAccess();

    // snd channels waiting to be sent
    private final Set<SendableChannel> sendQueue = new HashSet<>();

    // received channels waiting to be accepted through get SO_SNDCHAN option
    private final LinkedList<SendableChannel> receiveQueue = new LinkedList<>();

    private boolean soSndChanEnable;

    UnixDomainSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, boolean bound)
        throws IOException
    {
        super(sp, fd, bound);
    }

    // Constructor for sockets obtained from server sockets
    //
    UnixDomainSocketChannelImpl(SelectorProvider sp, FileDescriptor fd, SocketAddress isa)
        throws IOException
    {
        super(sp, fd, isa);
    }

    @Override
    SocketAddress implLocalAddress(FileDescriptor fd) throws IOException {
        return UnixDomainNet.localAddress(fd);
    }

    @Override
    SocketAddress getRevealedLocalAddress(SocketAddress address) {
        UnixDomainSocketAddress uaddr = (UnixDomainSocketAddress)address;
        return UnixDomainNet.getRevealedLocalAddress(uaddr);
    }

    @SuppressWarnings("unchecked")
    @Override
    <T> T implGetOption(SocketOption<T> name) throws IOException {
        return (T) Net.getSocketOption(getFD(), name);
    }

    @Override
    <T> void implSetOption(SocketOption<T> name, T value) throws IOException {
        Net.setSocketOption(getFD(), name, value);
    }

    private static class DefaultOptionsHolder {
        static final Set<SocketOption<?>> defaultOptions = defaultOptions();

        private static Set<SocketOption<?>> defaultOptions() {
            HashSet<SocketOption<?>> set = new HashSet<>();
            set.add(StandardSocketOptions.SO_SNDBUF);
            set.add(StandardSocketOptions.SO_RCVBUF);
            set.add(StandardSocketOptions.SO_LINGER);
            set.addAll(ExtendedSocketOptions.unixSocketOptions());
            return Collections.unmodifiableSet(set);
        }
    }

    @Override
    public final Set<SocketOption<?>> supportedOptions() {
        return DefaultOptionsHolder.defaultOptions;
    }

    /**
     * If special handling of a socket option is required, override this in subclass
     * and return the option value.
     *
     * @param name
     * @param <T>
     * @return
     * @throws IOException
     */
    <T> T getOptionSpecial(SocketOption<T> name) throws IOException {
        return null;
    }

    @Override
    SocketAddress implBind(SocketAddress local) throws IOException {
        UnixDomainNet.checkCapability();
        UnixDomainSocketAddress usa = UnixDomainNet.checkAddress(local);
        Path path = usa == null ? null : usa.getPath();
        UnixDomainNet.bind(getFD(), path);
        if (usa == null || path.toString().equals("")) {
            return UnixDomainNet.UNNAMED;
        } else {
            return UnixDomainNet.localAddress(getFD());
        }
    }

    @Override
    public Socket socket() {
        throw new UnsupportedOperationException("socket not supported");
    }

    /**
     * Checks the permissions required for connect
     */
    @Override
    SocketAddress checkRemote(SocketAddress sa) throws IOException {
        Objects.requireNonNull(sa);
        UnixDomainNet.checkCapability();
        UnixDomainSocketAddress usa = UnixDomainNet.checkAddress(sa);
        return usa;
    }

    @Override
    int implConnect(FileDescriptor fd, SocketAddress sa) throws IOException {
        UnixDomainSocketAddress usa = (UnixDomainSocketAddress)sa;
        return UnixDomainNet.connect(fd, usa.getPath());
    }

    String getRevealedLocalAddressAsString(SocketAddress sa) {
        UnixDomainSocketAddress usa = (UnixDomainSocketAddress)sa;
        return UnixDomainNet.getRevealedLocalAddressAsString(usa);
    }

    /**
     * Read/write need to be overridden for JFR
     */
    @Override
    public int read(ByteBuffer buf) throws IOException {
        return super.read(buf);
    }

    private static final int[] nullArray = new int[0];

    @Override
    int readImpl(FileDescriptor fd, ByteBuffer bb, long unused, NativeDispatcher nd)
        throws IOException
    {
        int[] newfds;
        if (soSndChanEnable) {
            newfds = new int[MAX_SEND_FDS];
            for (int i=0; i<newfds.length; i++)
                newfds[i] = -1;
        } else {
            newfds = nullArray;
        }
        int nbytes = IOUtil.recvmsg(fd, bb, (SocketDispatcher)nd, newfds);

        int fd1 = newfds.length == 0 ? -1 : newfds[0];

        for (int i=0; fd1 != -1; fd1 = newfds[++i]) {
            FileDescriptor newfd = new FileDescriptor();
            fdAccess.set(newfd, newfds[i]);
            SocketAddress laddr = Net.localAddress(newfd);
            SocketAddress raddr = Net.remoteAddress(newfd);
            SendableChannel chan;
            if (laddr instanceof UnixDomainSocketAddress) {
                if (raddr == null) {
                    chan = new UnixDomainServerSocketChannelImpl(provider(), newfd, true);
                } else {
                    chan = new UnixDomainSocketChannelImpl(provider(), newfd, raddr);
                }
                addToChannelList(receiveQueue, chan);
            } else if (laddr instanceof InetSocketAddress) {
                if (raddr == null) {
                    chan = new InetServerSocketChannelImpl(provider(), newfd, true);
                } else {
                    InetSocketAddress isa = (InetSocketAddress) raddr;
                    ProtocolFamily family = isa.getAddress() instanceof Inet4Address ?
                        StandardProtocolFamily.INET : StandardProtocolFamily.INET6;

                    chan = new InetSocketChannelImpl(provider(), family, newfd, isa);
                }
                addToChannelList(receiveQueue, chan);
            }
        }
        return nbytes;
    }

    private static void addToChannelList(LinkedList<SendableChannel> list, SendableChannel c)
        throws IOException
    {
        addToChannelList(list, c, Integer.MAX_VALUE);
    }

    private static void addToChannelList(LinkedList<SendableChannel> list, SendableChannel c, int maxlistlen)
        throws IOException
    {
        synchronized (list) {
            if (list.size() >= maxlistlen) {
                throw new IOException("Too many entries in queue");
            }
            list.add(c);
        }
    }

    private static SendableChannel pollChannelList(LinkedList<SendableChannel> list) {
        synchronized (list) {
            return list.poll();
        }
    }

    @Override
    public int write(ByteBuffer buf) throws IOException {
        return super.write(buf);
    }

    @Override
    public int writeImpl(FileDescriptor fd, ByteBuffer src, long unused,
                         NativeDispatcher nd)
        throws IOException
    {
        FileDescriptor[] sendfds = null;
        SendableChannel[] chans = {};
        synchronized (sendQueue) {
            if (!sendQueue.isEmpty()) {
                int l = sendQueue.size();
                sendfds = new FileDescriptor[l];
                chans = new SendableChannel[l];
                int i=0;
                for (SendableChannel sendee : sendQueue) {
                    if (!sendee.isOpen()) {
                        throw new IOException("Target channel for send is closed");
                    }
                    if (sendee.isRegistered()) {
                        throw new IOException("Target channel for send is registered with selector");
                    }
                    sendfds[i] = sendee.getFD();
                    chans[i] = sendee;
                    i++;
                };
                sendQueue.clear();
            }
        }
        int nbytes = IOUtil.sendmsg(fd, src, (SocketDispatcher)nd, sendfds);
        for (SendableChannel chan : chans) {
            try {chan.close(); } catch (IOException e) {}
        }
        return nbytes;
    }

    @Override
    public long write(ByteBuffer[] srcs, int offset, int length)
        throws IOException
    {
        return super.write(srcs, offset, length);
    }

    @Override
    public long read(ByteBuffer[] dsts, int offset, int length)
        throws IOException
    {
        return super.read(dsts, offset, length);
    }

    SendableChannel receivedChannel() {
        return pollChannelList(receiveQueue);
    }

    private static final int MAX_SEND_FDS = SocketDispatcher.maxsendfds();

    void sendChannel(SendableChannel target) throws IOException {
        ReentrantLock writeLock = writeLock();
        writeLock.lock();
        try {
            synchronized (sendQueue) {
                if (sendQueue.contains(target)) {
                    throw new IOException("channel already on send queue");
                }
                if (sendQueue.size() >= MAX_SEND_FDS) {
                    throw new IOException("send queue is full");
                }
                sendQueue.add(target);
            }
        } finally {
            writeLock.unlock();
        }
    }

    @Override
    protected void implCloseSelectableChannel() throws IOException {
        for (SendableChannel c : sendQueue) {
            try {c.close(); } catch (IOException e) {}
        }
        for (SendableChannel c : receiveQueue) {
            try {c.close(); } catch (IOException e) {}
        }
        sendQueue.clear();
        receiveQueue.clear();
        super.implCloseSelectableChannel();
    }

    synchronized boolean getSoSndChanEnable() {
        return soSndChanEnable;
    }

    synchronized void setSoSndChanEnable(boolean enable) {
        soSndChanEnable = enable;
    }
}
