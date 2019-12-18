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

/**
 * An address for a Unix domain {@link SocketChannel} or {@link ServerSocketChannel}.
 * These addresses contain a path, which when bound to a channel,
 * have an associated socket file in the file-system with the same name. Instances
 * are created with either a {@link String} path name or a {@link Path}.
 * <p>
 * If a Unix domain {@link SocketChannel} is automatically bound by connecting it
 * without calling {@link SocketChannel#bind(SocketAddress)} first, then its address
 * is unnamed; it has an empty path field, and therefore has no associated file
 * in the file-system. If a {@link ServerSocketChannel} is automatically bound by
 * passing a {@code null} address to one of the {@link ServerSocketChannel#bind(SocketAddress)
 * bind} methods, the channel is bound to a name in some temporary directory. The name can be
 * obtained by calling {@link ServerSocketChannel#getLocalAddress()} after bind returns.
 * <p>
 * @apiNote A channel can be bound to a name if and only if, no file exists
 * in the file-system with the same name, and the calling process has the required
 * operating system permissions to create a file of that name.
 * @apiNote The socket file created when a channel binds to a name is not removed when
 * the channel is closed. User code must arrange for the deletion of this file if
 * another channel needs to bind to the same name. Note also, that it may be possible
 * to delete the socket file, even before the channel is closed, thus allowing another
 * channel to bind to the same name. The original channel is not notified of any error
 * in this situation. Operating system permissions can be used to control who is allowed
 * to create and delete these socket files.
 *
 * @since 15
 */
public class UnixDomainSocketAddress extends SocketAddress {

    static final long serialVersionUID = 9829020419651288L;

    static {
        if (System.getSecurityManager() == null) {
            System.loadLibrary("nio");
        } else {
            AccessController.doPrivileged((PrivilegedAction<Void>) () -> {
                System.loadLibrary("nio");
                return null;
            });
        }
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
     * Create a named UnixDomainSocketAddress for the given pathname string.
     *
     * @param pathname the pathname to the socket.
     *
     * @throws NullPointerException if path is null
     *
     * @throws InvalidPathException if pathname cannot be converted to a Path
     */
    public UnixDomainSocketAddress(String pathname) {
        Objects.requireNonNull(pathname);
        this.pathname = pathname;
        this.path = Paths.get(pathname);
    }

    /**
     * Create a named UnixDomainSocketAddress for the given path.
     *
     * @param path the path to the socket.
     *
     * @throws NullPointerException if path is null
     */
    public UnixDomainSocketAddress(Path path) {
        Objects.requireNonNull(path);
        this.path = path;
        this.pathname = path.toString();
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
     * The format of the string is {@code "af_unix:<path>"} where {@code <path>}
     * is this address's path field, which will be empty in the case of an
     * unnamed socket address.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("af_unix:");
        if (path != null)
            sb.append(path);
        return sb.toString();
    }
}
