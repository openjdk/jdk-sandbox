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
import java.net.InetLookupPolicy;
import java.net.UnknownHostException;
import java.util.stream.Stream;

/**
 * An {@code InetNameServiceProvider} can be used to provide a system-wide alternative name
 * service resolution mechanism used for {@link InetAddress} host name and IP address resolution.
 */
public abstract class InetNameServiceProvider {

    /**
     * NameService provides host and address lookup service
     */
    public interface NameService {

        /**
         * Given the name of a host, returns a stream of IP addresses of the requested
         * address family associated with a provided hostname.
         * <p>
         * {@code host} should be a machine name, such as "{@code www.example.com}",
         * not a textual representation of its IP address. No validation is performed on
         * the given {@code host} name: if a textual representation is supplied, the name
         * resolution is likely to fail and {@link UnknownHostException} may be thrown.
         * <p>
         * If the specified {@code lookupPolicy.getAddressesFamily()} is {@link InetLookupPolicy.AddressFamily#ANY ANY} this
         * method returns addresses of both {@link InetLookupPolicy.AddressFamily#INET} and
         * {@link InetLookupPolicy.AddressFamily#INET6} families.
         * @implSpec
         * In the case when addresses of both types are returned, there is a
         * <a href="doc-files/net-properties.html#Ipv4IPv6">couple of System Properties</a>
         * affecting how IPv4 and IPv6 addresses are returned.
         * If the value of the {@code "java.net.preferIPv6Addresses"} property is {@code true} then IPv6 addresses
         * should be returned first. If the value is {@code system} addresses should be returned in the order they
         * are returned by the system. Otherwise, IPv4 addresses should be returned first.
         * If the value of the {@code "java.net.preferIPv4Stack"} property is {@code true} then only
         * IPv4 addresses should be returned.
         *
         * @param host the specified hostname
         * @param lookupPolicy the address lookup policy
         * @return stream of IP addresses for the requested host
         * @throws NullPointerException if {@code host} is {@code null}
         * @throws UnknownHostException if no IP address for the {@code host} could be found
         *
         */
        Stream<InetAddress> lookupByName(String host, InetLookupPolicy lookupPolicy) throws UnknownHostException;

        /**
         * Lookup the host name corresponding to the raw IP address provided.
         * This method performs reverse name service lookup.
         *
         * <p>{@code addr} argument is in network byte order: the highest order byte of the address
         * is in {@code addr[0]}.
         *
         * <p> IPv4 address byte array must be 4 bytes long and IPv6 byte array
         * must be 16 bytes long.
         *
         * @param addr byte array representing a raw IP address
         * @return {@code String} representing the host name mapping
         * @throws UnknownHostException if no host found for the specified IP address
         * @throws IllegalArgumentException if IP address is of illegal length
         */
        String lookupAddress(byte[] addr) throws UnknownHostException;
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
}
