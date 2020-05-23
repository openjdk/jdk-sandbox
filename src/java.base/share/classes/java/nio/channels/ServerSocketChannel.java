/*
 * Copyright (c) 2000, 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.nio.channels;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.NetPermission;
import java.net.ProtocolFamily;
import java.net.ServerSocket;
import java.net.SocketOption;
import java.net.SocketAddress;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.nio.channels.spi.SelectorProvider;
import static java.util.Objects.requireNonNull;

/**
 * A selectable channel for stream-oriented listening sockets that are either
 * <i>Internet protocol</i> or <i>Unix domain</i> server-sockets.  Internet protocol server
 * sockets accept network connections addressed by IP address and TCP port number.
 * They use {@link InetSocketAddress} for local and remote addresses.
 * <a href="package-summary.html#unixdomain">Unix domain</a> server-sockets are used for
 * inter-process communication to other processes on the same host and use
 * {@link UnixDomainSocketAddress} for their local and remote addresses.
 *
 * <p> A server-socket channel is created by invoking one of the open methods of this class.
 * It is not possible to create a channel for an arbitrary,
 * pre-existing {@link ServerSocket}. A newly-created server-socket channel is
 * open but not yet bound.  An attempt to invoke the {@link #accept() accept}
 * method of an unbound server-socket channel will cause a {@link NotYetBoundException}
 * to be thrown. A server-socket channel can be bound by invoking one of the
 * {@link #bind(java.net.SocketAddress,int) bind} methods defined by this class.
 *
 * <p>Internet protocol channels are created using {@link #open()}, or {@link #open(ProtocolFamily)}
 * with the family parameter set to {@link StandardProtocolFamily#INET INET} or
 * {@link StandardProtocolFamily#INET6 INET6}. <i>Internet protocol</i> channels use {@link
 * InetSocketAddress} addresses and support both IPv4 and IPv6 TCP/IP.
 *
 * <p><i>Unix Domain</i> channels are created using {@link #open(ProtocolFamily)}
 * with the family parameter set to {@link StandardProtocolFamily#UNIX UNIX}.
 *
 * <p>Aside from the different address types used, the behavior of both channel types is
 * otherwise the same except where specified differently below.
 * The two main additional differences are: <i>Unix domain</i> channels do not support the
 * {@link #socket()} method and they also only support a subset of the socket options
 * supported by <i>IP</i> channels.
 *
 * <p> Socket options are configured using the {@link #setOption(SocketOption,Object)
 * setOption} method. <i>Internet protocol</i> server-socket channels support the following options:
 * <blockquote>
 * <table class="striped">
 * <caption style="display:none">Socket options</caption>
 * <thead>
 *   <tr>
 *     <th scope="col">Option Name</th>
 *     <th scope="col">Description</th>
 *   </tr>
 * </thead>
 * <tbody>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_RCVBUF SO_RCVBUF} </th>
 *     <td> The size of the socket receive buffer </td>
 *   </tr>
 *   <tr>
 *     <th scope="row"> {@link java.net.StandardSocketOptions#SO_REUSEADDR SO_REUSEADDR} </th>
 *     <td> Re-use address </td>
 *   </tr>
 * </tbody>
 * </table>
 * </blockquote>
 *
 * Additional (implementation specific) options may also be supported.
 *
 * <p> Server-socket channels are safe for use by multiple concurrent threads.
 * </p>
 *
 * @author Mark Reinhold
 * @author JSR-51 Expert Group
 * @since 1.4
 */

public abstract class ServerSocketChannel
    extends AbstractSelectableChannel
    implements NetworkChannel
{

    /**
     * Initializes a new instance of this class.
     *
     * @param  provider
     *         The provider that created this channel
     */
    protected ServerSocketChannel(SelectorProvider provider) {
        super(provider);
    }

    /**
     * Opens an <i>Internet protocol</i> server-socket channel.
     *
     * <p> The new channel is created by invoking the {@link
     * java.nio.channels.spi.SelectorProvider#openServerSocketChannel
     * openServerSocketChannel} method of the system-wide default {@link
     * java.nio.channels.spi.SelectorProvider} object.
     *
     * <p> The new channel's socket is initially unbound; it must be bound to a
     * specific address via one of its socket's {@link
     * java.net.ServerSocket#bind(SocketAddress) bind} methods before
     * connections can be accepted.  </p>
     *
     * @return  A new socket channel
     *
     * @throws  IOException
     *          If an I/O error occurs
     */
    public static ServerSocketChannel open() throws IOException {
        return SelectorProvider.provider().openServerSocketChannel();
    }

    /**
     * Opens a server-socket channel.The {@code family} parameter specifies the
     * {@link ProtocolFamily protocol family} of the channel's socket.
     *
     * <p> The new channel is created by invoking the {@link
     * java.nio.channels.spi.SelectorProvider#openServerSocketChannel(ProtocolFamily)
     * openServerSocketChannel(ProtocolFamily)} method of the system-wide default {@link
     * java.nio.channels.spi.SelectorProvider} object. </p>
     *
     * @param   family
     *          The protocol family
     *
     * @return  A new socket channel
     *
     * @throws  UnsupportedOperationException
     *          If the specified protocol family is not supported. For example,
     *          suppose the parameter is specified as {@link
     *          java.net.StandardProtocolFamily#INET6 StandardProtocolFamily.INET6}
     *          but IPv6 is not enabled on the platform.
     * @throws  IOException
     *          If an I/O error occurs
     *
     * @since 15
     */
    public static ServerSocketChannel open(ProtocolFamily family) throws IOException {
        return SelectorProvider.provider().openServerSocketChannel(requireNonNull(family));
    }

    /**
     * Returns an operation set identifying this channel's supported
     * operations.
     *
     * <p> Server-socket channels only support the accepting of new
     * connections, so this method returns {@link SelectionKey#OP_ACCEPT}.
     * </p>
     *
     * @return  The valid-operation set
     */
    public final int validOps() {
        return SelectionKey.OP_ACCEPT;
    }


    // -- ServerSocket-specific operations --

    /**
     * Binds the channel's socket to a local address and configures the socket
     * to listen for connections.
     *
     * <p> An invocation of this method is equivalent to the following:
     * <blockquote><pre>
     * bind(local, 0);
     * </pre></blockquote>
     *
     * @param   local
     *          The local address to bind the socket, or {@code null} to bind
     *          to an automatically assigned socket address
     *
     * @return  This channel
     *
     * @throws  AlreadyBoundException               {@inheritDoc}
     * @throws  UnsupportedAddressTypeException     {@inheritDoc}
     * @throws  ClosedChannelException              {@inheritDoc}
     * @throws  IOException                         {@inheritDoc}
     * @throws  SecurityException
     *          If a security manager has been installed and its
     *          {@link SecurityManager#checkListen checkListen} method denies
     *          the operation for <i>Internet protocol</i> channels; or for <i>Unix Domain</i>
     *          channels, if the security manager denies the "read,write" actions for
     *          {@link java.io.FilePermission} for the {@code local} parameter's path
     *          or {@link java.net.NetPermission NetPermission}{@code ("unixChannels.server")}.
     *          Note, if {@code local} is null for a <i>Unix Domain</i> channel then
     *          the FilePermission check will use an empty path.
     *
     * @since 1.7
     */
    public final ServerSocketChannel bind(SocketAddress local)
        throws IOException
    {
        return bind(local, 0);
    }

    /**
     * Binds the channel's socket to a local address and configures the socket to
     * listen for connections.
     *
     * <p> This method is used to establish an association between the socket and
     * a local address. Once an association is established then the socket remains
     * bound until the channel is closed.
     *
     * <p> The {@code backlog} parameter is the maximum number of pending
     * connections on the socket. Its exact semantics are implementation specific.
     * In particular, an implementation may impose a maximum length or may choose
     * to ignore the parameter altogther. If the {@code backlog} parameter has
     * the value {@code 0}, or a negative value, then an implementation specific
     * default is used.
     *
     * <p> Note, for <i>Unix Domain</i> channels, a file is created in the file-system
     * with the same path name as this channel's bound {@link UnixDomainSocketAddress}.
     * This file persists after the channel is closed, and must be removed before
     * another channel can bind to the same name. <i>Unix Domain</i>
     * {@code ServerSocketChannels} bound to automatically assigned addresses will be
     * assigned a unique pathname in some system temporary directory. The associated socket
     * file also persists after the channel is closed. Its name can be obtained by
     * calling {@link #getLocalAddress()}.
     *
     * @param   local
     *          The address to bind the socket, or {@code null} to bind to
     *          an automatically assigned socket address
     * @param   backlog
     *          The maximum number of pending connections
     *
     * @return  This channel
     *
     * @throws  AlreadyBoundException
     *          If the socket is already bound
     * @throws  UnsupportedAddressTypeException
     *          If the type of the given address is not supported
     * @throws  ClosedChannelException
     *          If this channel is closed
     * @throws  IOException
     *          If some other I/O error occurs
     * @throws  SecurityException
     *          If a security manager has been installed and its
     *          {@link SecurityManager#checkListen checkListen} method denies
     *          the operation for <i>Internet protocol</i> channels; or in the case of <i>Unix Domain</i>
     *          channels, if the security manager denies the "read,write" actions for
     *          {@link java.io.FilePermission} for the {@code local} parameter's path
     *          or {@link java.net.NetPermission NetPermission}{@code ("unixChannels.server")}.
     *          Note, if {@code local} is null for a <i>Unix Domain</i> channel then
     *          the FilePermission check will use an empty path.
     *
     * @since 1.7
     */
    public abstract ServerSocketChannel bind(SocketAddress local, int backlog)
        throws IOException;

    /**
     * @throws  UnsupportedOperationException           {@inheritDoc}
     * @throws  IllegalArgumentException                {@inheritDoc}
     * @throws  ClosedChannelException                  {@inheritDoc}
     * @throws  IOException                             {@inheritDoc}
     *
     * @since 1.7
     */
    public abstract <T> ServerSocketChannel setOption(SocketOption<T> name, T value)
        throws IOException;

    /**
     * Retrieves a server socket associated with this channel if it is an <i>Internet protocol</i>
     * channel. The operation is not supported for <i>Unix Domain</i> channels.
     *
     * <p> The returned object will not declare any public methods that are not
     * declared in the {@link java.net.ServerSocket} class.  </p>
     *
     * @return  A server socket associated with this channel
     *
     * @throws UnsupportedOperationException if this is a Unix Domain channel
     */
    public abstract ServerSocket socket();

    /**
     * Accepts a connection made to this channel's socket.
     *
     * <p> If this channel is in non-blocking mode then this method will
     * immediately return {@code null} if there are no pending connections.
     * Otherwise it will block indefinitely until a new connection is available
     * or an I/O error occurs.
     *
     * <p> The socket channel returned by this method, if any, will be in
     * blocking mode regardless of the blocking mode of this channel.
     *
     * <p> For <i>Internet protocol</i> channels, this method performs exactly the same security checks
     * as the {@link java.net.ServerSocket#accept accept} method of the {@link
     * java.net.ServerSocket} class.  That is, if a security manager has been
     * installed then for each new connection this method verifies that the
     * address and port number of the connection's remote endpoint are
     * permitted by the security manager's {@link
     * java.lang.SecurityManager#checkAccept checkAccept} method.  </p>
     *
     * <p> For <i>Unix Domain</i> channels, this method checks two permissions
     * with {@link SecurityManager#checkPermission(Permission)}:
     * {@link java.io.FilePermission} constructed with the path from the
     * remote address and {@code "read, write"} as the actions and
     * {@link java.net.NetPermission NetPermission}{@code ("unixChannels.server")}.
     * Note, in the case where the remote socket is bound to the unnamed address,
     * then the path of the FilePermission will be the empty string.
     *
     * @return  The socket channel for the new connection,
     *          or {@code null} if this channel is in non-blocking mode
     *          and no connection is available to be accepted
     *
     * @throws  ClosedChannelException
     *          If this channel is closed
     *
     * @throws  AsynchronousCloseException
     *          If another thread closes this channel
     *          while the accept operation is in progress
     *
     * @throws  ClosedByInterruptException
     *          If another thread interrupts the current thread
     *          while the accept operation is in progress, thereby
     *          closing the channel and setting the current thread's
     *          interrupt status
     *
     * @throws  NotYetBoundException
     *          If this channel's socket has not yet been bound
     *
     * @throws  SecurityException
     *          If a security manager has been installed
     *          and it does not permit access to the remote endpoint
     *          of the new connection
     *
     * @throws  IOException
     *          If some other I/O error occurs
     */
    public abstract SocketChannel accept() throws IOException;

    /**
     * {@inheritDoc}
     * Where the channel is bound to a <i>Unix Domain</i> address, the return
     * value from this method is of type  {@link UnixDomainSocketAddress}.
     * <p>
     * If there is a security manager set and this is an <i>Internet protocol</i> channel,
     * {@code checkConnect} method is
     * called with the local address and {@code -1} as its arguments to see
     * if the operation is allowed. If the operation is not allowed,
     * a {@code SocketAddress} representing the
     * {@link java.net.InetAddress#getLoopbackAddress loopback} address and the
     * local port of the channel's socket is returned.
     * <p>
     * If there is a security manager set and this is a <i>Unix Domain</i> channel,
     * then {@link SecurityManager#checkPermission(Permission)} is called using
     * a {@link java.io.FilePermission} constructed with the path from the
     * local address and "read" as the action. If this check fails
     * then an unnamed {@link UnixDomainSocketAddress} (with empty pathname)
     * is returned.
     *
     * @return  The {@code SocketAddress} that the socket is bound to, or the
     *          {@code SocketAddress} representing the loopback address or empty
     *          pathname if denied by the security manager, or {@code null} if the
     *          channel's socket is not bound
     *
     * @throws  ClosedChannelException     {@inheritDoc}
     * @throws  IOException                {@inheritDoc}
     */
    @Override
    public abstract SocketAddress getLocalAddress() throws IOException;

}
