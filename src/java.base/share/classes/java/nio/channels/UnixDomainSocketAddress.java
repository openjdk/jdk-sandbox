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
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;
import sun.nio.ch.Net;

/**
 * A <a href="package-summary.html#unixdomain">Unix domain</a> socket address for {@link SocketChannel} or
 * {@link ServerSocketChannel}. This encapsulates a path which represents a filesystem pathname
 * that channels can bind or connect to.
 *
 * <p>{@link Path} objects used to create instances of this class must be obtained from the
 * {@linkplain FileSystems#getDefault system-default} filesystem.
 * All other {@code Path}s are considered invalid.
 *
 * @since 15
 */
public final class UnixDomainSocketAddress extends SocketAddress {
    static final long serialVersionUID = 92902496589351288L;

    private final Path path;
    private final String pathname; // used in native code

    static class SerializationProxy implements Serializable {

        static final long serialVersionUID = 9829020419651288L;

        private String pathname;

        public SerializationProxy(UnixDomainSocketAddress addr) {
            this.pathname = addr.pathname;
        }

        private Object readResolve() {
            return UnixDomainSocketAddress.of(pathname);
        }
    }

    private Object writeReplace() throws ObjectStreamException {
        return new SerializationProxy(this);
    }

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
     * @return a UnixDomainSocketAddress
     *
     * @throws InvalidPathException if pathname cannot be converted to a valid Path
     */
    public static  UnixDomainSocketAddress of(String pathname) {
        return new UnixDomainSocketAddress(pathname);
    }

    private UnixDomainSocketAddress(String pathname) throws InvalidPathException {
        Objects.requireNonNull(pathname);
        this.path = Paths.get(pathname);
        checkPath(path);
        this.pathname = pathname;
    }

    /**
     * Create a named UnixDomainSocketAddress for the given path.
     *
     * @param path the path to the socket.
     * @return a UnixDomainSocketAddress
     *
     * @throws InvalidPathException if path is not from the system-default filesystem
     * @throws NullPointerException if path is null
     */
    public static UnixDomainSocketAddress of(Path path) {
        return new UnixDomainSocketAddress(path);
    }

    private UnixDomainSocketAddress(Path path) throws InvalidPathException {
        checkPath(path);
        this.pathname = path.toString();
        this.path = path;
    }

    private static void checkPath(Path path) throws InvalidPathException {
        FileSystem fs = path.getFileSystem();
        if (!fs.equals(FileSystems.getDefault())) {
            throw new InvalidPathException(path.toString(),
                        "path is not from the default file-system");
        }
        if (!fs.getClass().getModule().equals(Object.class.getModule())) {
            throw new InvalidPathException(path.toString(),
                        "default file-system is not the system-default");
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
        return path.toString();
    }
}
