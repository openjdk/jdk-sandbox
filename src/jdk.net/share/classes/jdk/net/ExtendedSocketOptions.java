/*
 * Copyright (c) 2014, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.net;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.SocketException;
import java.net.SocketOption;
import java.nio.ByteBuffer;
import java.nio.channels.Channel;
import java.nio.channels.NetworkChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.access.SharedSecrets;

/**
 * Defines extended socket options, beyond those defined in
 * {@link java.net.StandardSocketOptions}. These options may be platform
 * specific.
 *
 * @since 1.8
 */
public final class ExtendedSocketOptions {

    private static class ExtSocketOption<T> implements SocketOption<T> {
        private final String name;
        private final Class<T> type;
        ExtSocketOption(String name, Class<T> type) {
            this.name = name;
            this.type = type;
        }
        @Override public String name() { return name; }
        @Override public Class<T> type() { return type; }
        @Override public String toString() { return name; }
    }

    private ExtendedSocketOptions() { }

    /**
     * Disable Delayed Acknowledgements.
     *
     * <p>
     * This socket option can be used to reduce or disable delayed
     * acknowledgments (ACKs). When {@code TCP_QUICKACK} is enabled, ACKs are
     * sent immediately, rather than delayed if needed in accordance to normal
     * TCP operation. This option is not permanent, it only enables a switch to
     * or from {@code TCP_QUICKACK} mode. Subsequent operations of the TCP
     * protocol will once again disable/enable {@code TCP_QUICKACK} mode
     * depending on internal protocol processing and factors such as delayed ACK
     * timeouts occurring and data transfer, therefore this option needs to be
     * set with {@code setOption} after each operation of TCP on a given socket.
     *
     * <p>
     * The value of this socket option is a {@code Boolean} that represents
     * whether the option is enabled or disabled. The socket option is specific
     * to stream-oriented sockets using the TCP/IP protocol. The exact semantics
     * of this socket option are socket type and system dependent.
     *
     * @since 10
     */
    public static final SocketOption<Boolean> TCP_QUICKACK =
            new ExtSocketOption<Boolean>("TCP_QUICKACK", Boolean.class);

    /**
     * Keep-Alive idle time.
     *
     * <p>
     * The value of this socket option is an {@code Integer} that is the number
     * of seconds of idle time before keep-alive initiates a probe. The socket
     * option is specific to stream-oriented sockets using the TCP/IP protocol.
     * The exact semantics of this socket option are system dependent.
     *
     * <p>
     * When the {@link java.net.StandardSocketOptions#SO_KEEPALIVE
     * SO_KEEPALIVE} option is enabled, TCP probes a connection that has been
     * idle for some amount of time. The default value for this idle period is
     * system dependent, but is typically 2 hours. The {@code TCP_KEEPIDLE}
     * option can be used to affect this value for a given socket.
     *
     * @since 11
     */
    public static final SocketOption<Integer> TCP_KEEPIDLE
            = new ExtSocketOption<Integer>("TCP_KEEPIDLE", Integer.class);

    /**
     * Keep-Alive retransmission interval time.
     *
     * <p>
     * The value of this socket option is an {@code Integer} that is the number
     * of seconds to wait before retransmitting a keep-alive probe. The socket
     * option is specific to stream-oriented sockets using the TCP/IP protocol.
     * The exact semantics of this socket option are system dependent.
     *
     * <p>
     * When the {@link java.net.StandardSocketOptions#SO_KEEPALIVE
     * SO_KEEPALIVE} option is enabled, TCP probes a connection that has been
     * idle for some amount of time. If the remote system does not respond to a
     * keep-alive probe, TCP retransmits the probe after some amount of time.
     * The default value for this retransmission interval is system dependent,
     * but is typically 75 seconds. The {@code TCP_KEEPINTERVAL} option can be
     * used to affect this value for a given socket.
     *
     * @since 11
     */
    public static final SocketOption<Integer> TCP_KEEPINTERVAL
            = new ExtSocketOption<Integer>("TCP_KEEPINTERVAL", Integer.class);

    /**
     * Keep-Alive retransmission maximum limit.
     *
     * <p>
     * The value of this socket option is an {@code Integer} that is the maximum
     * number of keep-alive probes to be sent. The socket option is specific to
     * stream-oriented sockets using the TCP/IP protocol. The exact semantics of
     * this socket option are system dependent.
     *
     * <p>
     * When the {@link java.net.StandardSocketOptions#SO_KEEPALIVE
     * SO_KEEPALIVE} option is enabled, TCP probes a connection that has been
     * idle for some amount of time. If the remote system does not respond to a
     * keep-alive probe, TCP retransmits the probe a certain number of times
     * before a connection is considered to be broken. The default value for
     * this keep-alive probe retransmit limit is system dependent, but is
     * typically 8. The {@code TCP_KEEPCOUNT} option can be used to affect this
     * value for a given socket.
     *
     * @since 11
     */
    public static final SocketOption<Integer> TCP_KEEPCOUNT
            = new ExtSocketOption<Integer>("TCP_KEEPCOUNT", Integer.class);

    /**
     * Identifies the receive queue that the last incoming packet for the socket
     * was received on.
     *
     * <p> The value of this socket option is a positive {@code Integer} that
     * identifies a receive queue that the application can use to split the
     * incoming flows among threads based on the queue identifier. The value is
     * {@code 0} when the socket is not bound, a packet has not been received,
     * or more generally, when there is no receive queue to identify.
     * The socket option is supported by both stream-oriented and datagram-oriented
     * sockets.
     *
     * <p> The socket option is read-only and an attempt to set the socket option
     * will throw {@code SocketException}.
     *
     * @apiNote
     * Network devices may have multiple queues or channels to transmit and receive
     * network packets. The {@code SO_INCOMING_NAPI_ID} socket option provides a hint
     * to the application to indicate the receive queue on which an incoming socket
     * connection or packets for that connection are directed to. An application may
     * take advantage of this by handling all socket connections assigned to a
     * specific queue on one thread.
     *
     * @since 15
     */
    public static final SocketOption<Integer> SO_INCOMING_NAPI_ID
            = new ExtSocketOption<Integer>("SO_INCOMING_NAPI_ID", Integer.class);

    /**
     * Unix domain {@link SocketChannel} peer credentials.
     * <p>
     * This is a read-only socket option which returns a {@link UnixDomainPrincipal} for the
     * peer socket that the channel is connected to. The credentials returned are those 
     * that applied at the time the channel was first connected, or accepted from a 
     * {@code ServerSocketChannel}. Attempting to set this option or to get
     * it from an unconnected socket will throw a {@link SocketException}.
     */
    public static final SocketOption<UnixDomainPrincipal> SO_PEERCRED
            = new ExtSocketOption<UnixDomainPrincipal>
                ("SO_PEERCRED", UnixDomainPrincipal.class);


    /**
     * Send a {@link SocketChannel} or {@link ServerSocketChannel} through a <i>Unix Domain</i>
     * {@link SocketChannel} or obtain such a channel received through a <i>Unix Domain</i>
     * {@code SocketChannel}.
     * <p>
     * This socket option allows channels to be sent and received between
     * processes on the same system alongside regular data. Both <i>Internet Protocol</I>
     * and <i>Unix domain</i> channels can be sent in this way.
     * Other {@link Channel} types are not supported at this time.
     *
     * <p> Setting the option causes the supplied channel to be added to a send queue
     * associated with this <i>Unix Domain</i> {@code SocketChannel} such that when the next write occurs
     * on this channel, an attempt is made to send all channels in the queue, in an ancillary
     * control message together with the data. Setting this option synchronizes with
     * channel writes to make ordering predictable. No change in state of the given
     * channel occurs until the write happens, at which time the channel is closed.
     * The channel's socket remains open and usable on the receiving side.
     * The send queue has an implementation specific maximum length and
     * it is an error to register more than this number of channels for sending before calling
     * write. It is inadvisable to perform any I/O on a channel
     * after registering it to be sent with this option. In particular, the
     * send will fail if a channel is already closed at the time it is to be sent.
     *
     * <p> Receiving channels involves a similar procedure in reverse. As data is read
     * through the {@link SocketChannel} read methods, if any associated ancillary control messages
     * contain a channel, they are added to a receive queue associated with this channel.
     * Getting this socket option then removes the first channel from the receive queue
     * and returns it. If the receive queue is empty when getOption is called, {@code null}
     * is returned.
     *
     * <p> Note, there is no way to detect if a particular read has received
     * a channel. However, once the data that was written when sending a channel has been
     * read on the receiving side, then the associated channel is guaranteed to be
     * available to the receiver. The sender and receiver can co-ordinate by using this fact, or
     * alternatively the receiver can poll for a received channel after each read
     * or notification of OP_READ, if non-blocking.
     *
     * <p> Closing a channel while channels are still in its send or receive queues causes all
     * channels in both queues to be closed. If a <i>Unix Domain</i> channel is sent
     * using this mechanism, and has channels in its own send or receive queues, these
     * channels are closed before the channel is sent.
     */
    public static final SocketOption<Channel> SO_SNDCHAN
            = new ExtSocketOption<Channel>
                ("SO_SNDCHAN", Channel.class);

    private static final PlatformSocketOptions platformSocketOptions =
            PlatformSocketOptions.get();

    private static final boolean quickAckSupported =
            platformSocketOptions.quickAckSupported();
    private static final boolean keepAliveOptSupported =
            platformSocketOptions.keepAliveOptionsSupported();
    private static final boolean unixDomainExtOptionsSupported =
            platformSocketOptions.unixDomainExtOptionsSupported();
    private static final boolean incomingNapiIdOptSupported  =
            platformSocketOptions.incomingNapiIdSupported();

    private static final Set<SocketOption<?>> extendedOptions = options();

    static Set<SocketOption<?>> options() {
        Set<SocketOption<?>> options = new HashSet<>();
        if (quickAckSupported) {
            options.add(TCP_QUICKACK);
        }
        if (incomingNapiIdOptSupported) {
            options.add(SO_INCOMING_NAPI_ID);
        }
        if (keepAliveOptSupported) {
            options.addAll(Set.of(TCP_KEEPCOUNT, TCP_KEEPIDLE, TCP_KEEPINTERVAL));
        }
        if (unixDomainExtOptionsSupported) {
            options.add(SO_PEERCRED);
            options.add(SO_SNDCHAN);
        }
        return Collections.unmodifiableSet(options);
    }

    static {
        // Registers the extended socket options with the base module.
        sun.net.ext.ExtendedSocketOptions.register(
                new sun.net.ext.ExtendedSocketOptions(extendedOptions) {

            @Override
            @SuppressWarnings("removal")
            public void setOption(FileDescriptor fd,
                                  SocketOption<?> option,
                                  Object value)
                throws SocketException
            {
                if (fd == null || !fd.valid())
                    throw new SocketException("socket closed");

                if (option == TCP_QUICKACK) {
                    setQuickAckOption(fd, (boolean) value);
                } else if (option == TCP_KEEPCOUNT) {
                    setTcpkeepAliveProbes(fd, (Integer) value);
                } else if (option == TCP_KEEPIDLE) {
                    setTcpKeepAliveTime(fd, (Integer) value);
                } else if (option == TCP_KEEPINTERVAL) {
                    setTcpKeepAliveIntvl(fd, (Integer) value);
                } else if (option == SO_INCOMING_NAPI_ID) {
                    if (!incomingNapiIdOptSupported)
                        throw new UnsupportedOperationException("Attempt to set unsupported option " + option);
                    else
                        throw new SocketException("Attempt to set read only option " + option);
                } else if (option == SO_PEERCRED) {
                    throw new SocketException("SO_PEERCRED cannot be set ");
                } else {
                    throw new InternalError("Unexpected option " + option);
                }
            }

            @Override
            @SuppressWarnings("removal")
            public Object getOption(FileDescriptor fd,
                                    SocketOption<?> option)
                throws SocketException
            {
                if (fd == null || !fd.valid())
                    throw new SocketException("socket closed");

                if (option == TCP_QUICKACK) {
                    return getQuickAckOption(fd);
                } else if (option == TCP_KEEPCOUNT) {
                    return getTcpkeepAliveProbes(fd);
                } else if (option == TCP_KEEPIDLE) {
                    return getTcpKeepAliveTime(fd);
                } else if (option == TCP_KEEPINTERVAL) {
                    return getTcpKeepAliveIntvl(fd);
                } else if (option == SO_PEERCRED) {
                    return getSoPeerCred(fd);
                } else if (option == SO_INCOMING_NAPI_ID) {
                    return getIncomingNapiId(fd);
                } else {
                    throw new InternalError("Unexpected option " + option);
                }
            }

            @Override
            @SuppressWarnings("removal")
            public void setOptionByChannel(NetworkChannel chan, SocketOption<?> option, Object value)
                throws IOException
            {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null)
                    sm.checkPermission(new NetworkPermission("setOption." + option.name()));

                if (option == SO_SNDCHAN) {
                    setSoSndChan(chan, (Channel)value);
                } else {
                    throw new UnsupportedOperationException("Unexpected option " + option);
                }
            }

            @Override
            @SuppressWarnings("removal")
            public Object getOptionByChannel(NetworkChannel chan, SocketOption<?> option)
                throws IOException
            {
                SecurityManager sm = System.getSecurityManager();
                if (sm != null)
                    sm.checkPermission(new NetworkPermission("getOption." + option.name()));
                if (option == SO_SNDCHAN) {
                    return receivedChannelFor(chan);
                } else {
                    throw new UnsupportedOperationException("Unexpected option " + option);
                }
            }
        });
    }

    private static final JavaIOFileDescriptorAccess fdAccess =
            SharedSecrets.getJavaIOFileDescriptorAccess();

    private static void setQuickAckOption(FileDescriptor fd, boolean enable)
            throws SocketException {
        platformSocketOptions.setQuickAck(fdAccess.get(fd), enable);
    }

    private static Object getSoPeerCred(FileDescriptor fd)
            throws SocketException {
        return platformSocketOptions.getSoPeerCred(fdAccess.get(fd));
    }

    private static Object getQuickAckOption(FileDescriptor fd)
            throws SocketException {
        return platformSocketOptions.getQuickAck(fdAccess.get(fd));
    }

    private static void setTcpkeepAliveProbes(FileDescriptor fd, int value)
            throws SocketException {
        platformSocketOptions.setTcpkeepAliveProbes(fdAccess.get(fd), value);
    }

    private static void setTcpKeepAliveTime(FileDescriptor fd, int value)
            throws SocketException {
        platformSocketOptions.setTcpKeepAliveTime(fdAccess.get(fd), value);
    }

    private static void setTcpKeepAliveIntvl(FileDescriptor fd, int value)
            throws SocketException {
        platformSocketOptions.setTcpKeepAliveIntvl(fdAccess.get(fd), value);
    }

    private static int getTcpkeepAliveProbes(FileDescriptor fd) throws SocketException {
        return platformSocketOptions.getTcpkeepAliveProbes(fdAccess.get(fd));
    }

    private static int getTcpKeepAliveTime(FileDescriptor fd) throws SocketException {
        return platformSocketOptions.getTcpKeepAliveTime(fdAccess.get(fd));
    }

    private static int getTcpKeepAliveIntvl(FileDescriptor fd) throws SocketException {
        return platformSocketOptions.getTcpKeepAliveIntvl(fdAccess.get(fd));
    }

    private static int getIncomingNapiId(FileDescriptor fd) throws SocketException {
        return platformSocketOptions.getIncomingNapiId(fdAccess.get(fd));
    }

    static class PlatformSocketOptions {

        protected PlatformSocketOptions() {}

        @SuppressWarnings("unchecked")
        private static PlatformSocketOptions newInstance(String cn) {
            Class<PlatformSocketOptions> c;
            try {
                c = (Class<PlatformSocketOptions>)Class.forName(cn);
                return c.getConstructor(new Class<?>[] { }).newInstance();
            } catch (ReflectiveOperationException x) {
                throw new AssertionError(x);
            }
        }

        private static PlatformSocketOptions create() {
            String osname = AccessController.doPrivileged(
                    new PrivilegedAction<String>() {
                        public String run() {
                            return System.getProperty("os.name");
                        }
                    });
            if ("Linux".equals(osname)) {
                return newInstance("jdk.net.LinuxSocketOptions");
            } else if (osname.startsWith("Mac")) {
                return newInstance("jdk.net.MacOSXSocketOptions");
            } else {
                return new PlatformSocketOptions();
            }
        }

        private static final PlatformSocketOptions instance = create();

        static PlatformSocketOptions get() {
            return instance;
        }

        boolean unixDomainExtOptionsSupported() {
            return false;
        }

        void setQuickAck(int fd, boolean on) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_QUICKACK option");
        }

        boolean getQuickAck(int fd) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_QUICKACK option");
        }

        boolean quickAckSupported() {
            return false;
        }

        boolean keepAliveOptionsSupported() {
            return false;
        }

        void setTcpkeepAliveProbes(int fd, final int value) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_KEEPCNT option");
        }

        void setTcpKeepAliveTime(int fd, final int value) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_KEEPIDLE option");
        }

        UnixDomainPrincipal getSoPeerCred(int fd) throws SocketException {
            throw new UnsupportedOperationException("unsupported SO_PEERCRED option");
        }

        void setTcpKeepAliveIntvl(int fd, final int value) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_KEEPINTVL option");
        }

        int getTcpkeepAliveProbes(int fd) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_KEEPCNT option");
        }

        int getTcpKeepAliveTime(int fd) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_KEEPIDLE option");
        }

        int getTcpKeepAliveIntvl(int fd) throws SocketException {
            throw new UnsupportedOperationException("unsupported TCP_KEEPINTVL option");
        }

        boolean incomingNapiIdSupported() {
            return false;
        }

        int getIncomingNapiId(int fd) throws SocketException {
            throw new UnsupportedOperationException("unsupported SO_INCOMING_NAPI_ID socket option");
        }
    }
}
