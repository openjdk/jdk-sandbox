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

import java.io.ObjectStreamException;
import java.io.Serializable;
import java.net.SocketAddress;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

/**
 * A <a href="package-summary.html#unixdomain">Unix domain</a> socket address.
 * A Unix domain socket address encapsulates a file path that Unix domain sockets
 * bind or connect to.
 *
 * <p> An <a id="unnamed"></a><i>unnamed</i> {@code UnixDomainSocketAddress} has
 * an empty path. The local address of a Unix domain socket that is automatically
 * bound will be unnamed.
 *
 * <p> {@link Path} objects used to create instances of this class must be obtained
 * from the {@linkplain FileSystems#getDefault system-default} file system.
 *
 * @since 16
 */
public final class UnixDomainSocketAddress extends SocketAddress {
    static final long serialVersionUID = 92902496589351288L;

    private final Path path;

    static class SerializationProxy implements Serializable {
        static final long serialVersionUID = 9829020419651288L;

        private String pathname;

        SerializationProxy(UnixDomainSocketAddress addr) {
            this.pathname = addr.path.toString();
        }

        private Object readResolve() {
            return UnixDomainSocketAddress.of(pathname);
        }
    }

    private Object writeReplace() throws ObjectStreamException {
        return new SerializationProxy(this);
    }

    private UnixDomainSocketAddress(Path path) {
        FileSystem fs = path.getFileSystem();
        if (fs != FileSystems.getDefault()) {
            throw new IllegalArgumentException(); // fix message
        }
        if (fs.getClass().getModule() != Object.class.getModule()) {
            throw new IllegalArgumentException();  // fix message
        }
        this.path = path;
    }

    /**
     * Create a named UnixDomainSocketAddress from the given path string.
     *
     * @param  pathname
     *         The path string
     *
     * @return A UnixDomainSocketAddress
     *
     * @throws InvalidPathException
     *         If the path cannot be converted to a Path
     */
    public static UnixDomainSocketAddress of(String pathname) {
        return of(Path.of(pathname));
    }

    /**
     * Create a named UnixDomainSocketAddress for the given path.
     *
     * @param  path
     *         The path to the socket
     *
     * @return A UnixDomainSocketAddress
     *
     * @throws IllegalArgumentException
     *         If the path is not associated with the default file system
     */
    public static UnixDomainSocketAddress of(Path path) {
        return new UnixDomainSocketAddress(path);
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
     * Returns a hash code computed from this object's path string.
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
        return path.toString();
    }
}
