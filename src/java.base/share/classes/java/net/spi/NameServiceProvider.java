/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package java.net.spi;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A {@code NameServiceProvider} can be used to provide a system-wide alternative name
 * service resolution mechanism used for {@link InetAddress} host name and IP address resolution.
 */
public abstract class NameServiceProvider {
    /**
     * NameService provides host and address lookup service
     */
    public interface NameService {

        /**
         * Lookup a host mapping by name. Retrieve the IP addresses
         * associated with a host
         *
         * @param host the specified hostname
         * @return array of IP addresses for the requested host
         * @throws UnknownHostException if no IP address for the {@code host} could be found
         */
        InetAddress[] lookupAllHostAddr(String host) throws UnknownHostException;

        /**
         * Lookup the host corresponding to the IP address provided
         *
         * @param addr byte array representing an IP address
         * @return {@code String} representing the host name mapping
         * @throws UnknownHostException if no host found for the specified IP address
         */
        String getHostByAddr(byte[] addr) throws UnknownHostException;
    }

    /**
     * Initialise and return the {@link NameService} provided by
     * this provider.
     *
     * @param defaultNameService The platform default name service which can
     *                 be used to bootstrap this provider.
     * @return the name service provided by this provider
     */
    public abstract NameService get(NameService defaultNameService);

    /**
     * The {@code RuntimePermission("nameServiceProvider")} is
     * necessary to subclass and instantiate the {@code NameServiceProvider} class,
     * as well as to obtain name service from an instance of that class,
     * and it is also required to obtain the operating system name resolution configurations.
     */
    public static final RuntimePermission NAMESERVICE_PERMISSION =
            new RuntimePermission("nameServiceProvider");

    /**
     * Creates a new instance of {@code NameServiceProvider}.
     *
     * @throws SecurityException if a security manager is present and its
     *                           {@code checkPermission} method doesn't allow the
     *                           {@code RuntimePermission("nameServiceProvider")}.
     * @implNote It is recommended that a {@code NameServiceProvider} service
     * implementation does not perform any heavy initialization in its
     * constructor, in order to avoid possible risks of deadlock or class
     * loading cycles during the instantiation of the service provider.
     */
    protected NameServiceProvider() {
        this(checkPermission());
    }

    private NameServiceProvider(Void unused) {
    }

    private static Void checkPermission() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(NAMESERVICE_PERMISSION);
        }
        return null;
    }
}
