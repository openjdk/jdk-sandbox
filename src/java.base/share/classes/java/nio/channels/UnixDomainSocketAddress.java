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
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;

/**
 * An address for a Unix domain socket or server socket channel. These
 * addresses contain a String path name, which when bound to a channel,
 * have an associated file in the file-system with the same name.
 * <p>
 * If a channel is automatically bound to Unix domain address then its address
 * is unnamed, has an empty path field, and therefore has no associated
 * file in the file-system.
 * <p>
 * Note, not all channel types support Unix domain addresses.
 *
 * @since 14
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

    private final String path;

    /**
     * Create a named UnixDomainSocketAddress for the given path string.
     *
     * @param path the pathname to the socket.
     *
     * @throws NullPointerException if path is null
     */
    public UnixDomainSocketAddress(String path) {
        Objects.requireNonNull(path);
        this.path = path;
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
        this.path = path.toString();
    }

    /**
     * Return this address's path.
     *
     * @return this address's path
     */
    public String getPath() {
        return path;
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

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("af_unix:");
        if (path != null)
            sb.append(path);
        return sb.toString();
    }
}
