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

/**
 * An INET name service provider class to provide a system-wide alternative name
 * service resolution mechanism used for {@linkplain InetAddress} host names and IP addresses
 * resolution.
 * <p>
 * An INET name service provider is a concrete subclass of this class that has a zero-argument
 * constructor and implements the abstract methods specified below.
 * <p>
 * INET name service providers are located using the ServiceLoader facility, as specified by
 * {@linkplain InetAddress}.
 */
public abstract class InetNameServiceProvider {

    /**
     * Initialise and return the {@link InetNameService} provided by
     * this provider.
     * Any unchecked exception thrown by this method is considered as
     * a failure of {@code InetNameService} instantiation and will be propagated to
     * the calling thread.
     * @param configuration a {@link Configuration} instance containing platform address resolution
     *               configuration which could be used to bootstrap a provider.
     * @return the name service provided by this provider
     */
    public abstract InetNameService get(Configuration configuration);

    /**
     * Returns the name of this provider
     *
     * @return the name service provider name
     */
    public abstract String name();

    /**
     * The {@code RuntimePermission("inetNameServiceProvider")} is
     * necessary to subclass and instantiate the {@code InetNameServiceProvider} class,
     * as well as to obtain name service from an instance of that class,
     * and it is also required to obtain the operating system name resolution configurations.
     */
    private static final RuntimePermission NAMESERVICE_PERMISSION =
            new RuntimePermission("inetNameServiceProvider");

    /**
     * Creates a new instance of {@code InetNameServiceProvider}.
     *
     * @throws SecurityException if a security manager is present and its
     *                           {@code checkPermission} method doesn't allow the
     *                           {@code RuntimePermission("inetNameServiceProvider")}.
     * @implNote It is recommended that an {@code InetNameServiceProvider} service
     * implementation does not perform any heavy initialization in its
     * constructor, in order to avoid possible risks of deadlock or class
     * loading cycles during the instantiation of the service provider.
     */
    protected InetNameServiceProvider() {
        this(checkPermission());
    }

    private InetNameServiceProvider(Void unused) {
    }

    private static Void checkPermission() {
        final SecurityManager sm = System.getSecurityManager();
        if (sm != null) {
            sm.checkPermission(NAMESERVICE_PERMISSION);
        }
        return null;
    }

    /**
     * A {@code Configuration} is supplied to {@link InetNameServiceProvider#get(Configuration)} method to
     * bootstrap a provider by supplying a configuration parameters related to an IP address resolution.
     */
    public interface Configuration {
        /**
         * Returns platform default {@link InetNameService InetNameService} which is
         * used to bootstrap a custom provider.
         *
         * @return the platform default provider.
         */
        InetNameService builtinNameService();

        /**
         * Returns the localhost name which is used to bootstrap a custom
         *  {@code InetNameService} provider.
         *
         * @return the localhost name.
         */
        String lookupLocalHostName();
    }
}
