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

/**
 * An addresses lookup policy object is used to specify a type and order of addresses
 * supplied to {@link java.net.spi.InetNameServiceProvider.NameService#lookupByName(String, InetLookupPolicy)}
 * for performing a host name resolution requests.
 * <p>
 * The platform-wide lookup policy is constructed by consulting a
 * <a href="doc-files/net-properties.html#Ipv4IPv6">System Properties</a> which affects how IPv4 and IPv6
 * addresses are returned.
 */
public final class InetLookupPolicy {
    // Placeholder for address family value
    private final AddressFamily family;

    // Placeholder for addresses order value
    private final AddressesOrder addressesOrder;

    // Private constructor
    private InetLookupPolicy(AddressFamily family, AddressesOrder addressesOrder) {
        this.family = family;
        this.addressesOrder  = addressesOrder;
    }

    /**
     * This factory method creates {@link InetLookupPolicy} instance from the provided
     * {@link AddressFamily} and {@link AddressesOrder} values.
     * @param family an address family
     * @param addressesOrder an addresses order
     * @return instance of {@code InetLookupPolicy}
     */
    public static InetLookupPolicy of(AddressFamily family, AddressesOrder addressesOrder) {
        return new InetLookupPolicy(family, addressesOrder);
    }

    /**
     * Returns a type of address family that is used to designate a type of addresses
     * queried during resolution of host IP addresses.
     *
     * @return an address family type
     * @see java.net.spi.InetNameServiceProvider.NameService#lookupByName(String, InetLookupPolicy)
     */
    public final AddressFamily getAddressesFamily() {
        return family;
    }

    /**
     * Returns an order in which IP addresses are returned by
     * {@link java.net.spi.InetNameServiceProvider.NameService} during a host name
     * resolution requests.
     *
     * @return an addresses order
     * @see java.net.spi.InetNameServiceProvider.NameService#lookupByName(String, InetLookupPolicy)
     */
    public AddressesOrder getAddressesOrder() {
        return addressesOrder;
    }

    /**
     * Specifies type that is used to designate a family of network addresses queried during
     * resolution of host IP addresses.
     *
     * @see AddressesOrder
     * @see java.net.spi.InetNameServiceProvider.NameService
     */
    public enum AddressFamily {
        /**
         * Unspecified address family. Instructs {@link java.net.spi.InetNameServiceProvider.NameService}
         * to return network addresses for {@code IPv4} and {@code IPv6} address families.
         */
        ANY,

        /**
         * Query IPv4 addresses only
         */
        IPV4,

        /**
         * Query IPv6 addresses only
         */
        IPV6
    }

    /**
     * Specifies an order in which IP addresses are returned by
     * {@link java.net.spi.InetNameServiceProvider.NameService NameService}
     * implementations.
     *
     * @see java.net.spi.InetNameServiceProvider.NameService
     */
    public enum AddressesOrder {
        /**
         * The addresses are ordered in the same way as returned by the name service provider.
         */
        SYSTEM,

        /**
         * IPv4 addresses are preferred over IPv6 addresses and returned first.
         */
        IPV4_FIRST,

        /**
         * IPv6 addresses are preferred over IPv4 addresses and returned first.
         */
        IPV6_FIRST
    }
}
