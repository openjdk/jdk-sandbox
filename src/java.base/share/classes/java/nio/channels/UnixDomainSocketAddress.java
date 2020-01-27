/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.net.SocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import sun.nio.ch.Net;

/**
 * An address for a Unix domain {@link SocketChannel} or {@link ServerSocketChannel}.
 * This contains a path which, when the address is bound to a channel,
 * has an associated socket file in the file-system with the same name as the path. Instances
 * are created with either a {@link String} path name or a {@link Path}. Paths
 * can be either absolute or relative with respect to the current working directory.
 * <p>
 * If a Unix domain {@link SocketChannel} is automatically bound by connecting it
 * without calling {@link SocketChannel#bind(SocketAddress) bind} first, then its address
 * is unnamed; it has an empty path field, and therefore has no associated file
 * in the file-system. {@link SocketChannel#getLocalAddress() getLocalAddress} will return
 * the constant value {@link #UNNAMED} in this case.
 * <p>
 * If a Unix domain {@link ServerSocketChannel} is automatically bound by passing a {@code null}
 * address to one of the {@link ServerSocketChannel#bind(SocketAddress) bind} methods, the channel
 * is bound to a unique name in some temporary directory. The name can be obtained by calling
 * {@link ServerSocketChannel#getLocalAddress() getLocalAddress} after bind returns.
 * <p>
 * @apiNote A channel can be bound to a name if and only if, no file exists
 * in the file-system with the same name, and the calling process has the required
 * operating system permissions to create a file of that name.
 * The socket file created when a channel binds to a name is not removed when
 * the channel is closed. User code must arrange for the deletion of this file if
 * another channel needs to bind to the same name. Note also, that it may be possible
 * to delete the socket file, even before the channel is closed, thus allowing another
 * channel to bind to the same name. The original channel is not notified of any error
 * in this situation. Operating system permissions can be used to control who is allowed
 * to create and delete these socket files.
 * <p>
 * @implNote
 * The constant value {@link #MAXNAMELENGTH} specifies the (platform specific) maximum length
 * of a UnixDomainSocketAddress's path. For most platforms, this is typically close to but
 * not less than {@code 100}.
 *
 * @since 15
 */
public class UnixDomainSocketAddress extends SocketAddress {

    static final long serialVersionUID = 9829020419651288L;

    static {
        Net.init();
        init();
    }

    private final Path path;

    private final String pathname;

    /**
     * An unnamed UnixDomainSocketAddress. If a {@link SocketChannel} is automatically
     * bound then its local address will be this instance.
     */
    public static final UnixDomainSocketAddress UNNAMED =
        new UnixDomainSocketAddress("");

    /**
     * The maximum number of characters allowed in a UnixDomainSocketAddress name
     * This may vary between platforms, but is fixed on each platform.
     * If Unix domain channels are not supported, then the value will be {@code -1}.
     */
    public static final int MAXNAMELENGTH = Net.unixDomainMaxNameLen();

    /**
     * Create a named UnixDomainSocketAddress for the given pathname string.
     *
     * @param pathname the pathname to the socket.
     *
     * @throws NullPointerException if pathname is null
     * @throws InvalidArgumentException if pathname is too long
     * @throws InvalidPathException if pathname cannot be converted to a Path
     * @throws UnsupportedOperationException if Unix domain channels not supported
     *         on this platform.
     */
    public UnixDomainSocketAddress(String pathname) {
        Objects.requireNonNull(pathname);
        // TBD: The implication of check below is that instances cannot
        // be created on platforms where Unix domain sockets are not supported.
        // Is this ok?
        if (pathname.length() > MAXNAMELENGTH) {
            if (MAXNAMELENGTH == -1)
                throw new UnsupportedOperationException("Unix domain channels not supported"
                        + " on this platform");
            throw new IllegalArgumentException("pathname too long");
        }
        this.pathname = pathname;
        this.path = Paths.get(pathname);
    }

    /**
     * Create a named UnixDomainSocketAddress for the given path.
     *
     * @param path the path to the socket.
     *
     * @throws InvalidArgumentException if path name is too long
     * @throws UnsupportedOperationException if Unix domain channels not supported
     *         on this platform.
     * @throws NullPointerException if path is null
     */
    public UnixDomainSocketAddress(Path path) {
        Objects.requireNonNull(path);
        this.path = path;
        this.pathname = path.toString();
        if (pathname.length() > MAXNAMELENGTH) {
            if (MAXNAMELENGTH == -1)
                throw new UnsupportedOperationException("Unix domain channels not supported"
                        + " on this platform");
            throw new IllegalArgumentException("pathname too long");
        }
    }

    /**
     * Return this address's path.
     *
     * @return this address's path
     */
    public Path getPath() {
        return path;
    }

    /**
     * Return this address's path as a String.
     *
     * @return this address's path
     */
    public String getPathName() {
        return pathname;
    }

    static native void init();

    /**
     * Returns a hashcode computed from this object's path string.
     */
    @Override
    public int hashCode() {
        return path.hashCode();
    }

    /**
     * Compares this address with another object.
     *
     * @return true if the path fields are equal
     */
    @Override
    public boolean equals(Object o) {
        if (! (o instanceof UnixDomainSocketAddress))
            return false;
        UnixDomainSocketAddress that = (UnixDomainSocketAddress)o;
        return this.path.equals(that.path);
    }

    /**
     * Returns a string representation of this {@code UnixDomainSocketAddress}.
     *
     * @return this address's path which may be empty for an unnamed address
     */
    @Override
    public String toString() {
        return pathname;
    }
}
