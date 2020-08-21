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

package java.net;

import sun.security.action.GetPropertyAction;

/**
 * An addresses lookup policy object is used to specify a type and order of addresses
 * supplied to {@link java.net.spi.InetNameServiceProvider.NameService#lookupByName(String, InetLookupPolicy)}
 * for performing a host name resolution requests.
 *
 * The platform-wide lookup policy is constructed by consulting a
 * <a href="doc-files/net-properties.html#Ipv4IPv6">System Properties</a> which affects how IPv4 and IPv6
 * addresses are returned.
 */
public abstract class InetLookupPolicy {
    /**
     * Returns a type of address family that is used to designate a type of addresses
     * queried during resolution of host IP addresses.
     *
     * @return an address family type
     * @see java.net.spi.InetNameServiceProvider.NameService#lookupByName(String, InetLookupPolicy)
     */
    public abstract AddressFamily getAddressesFamily();

    /**
     * Returns an order in which IP addresses are returned by
     * {@link java.net.spi.InetNameServiceProvider.NameService} during a host name
     * resolution requests.
     *
     * @return an addresses order
     * @see java.net.spi.InetNameServiceProvider.NameService#lookupByName(String, InetLookupPolicy)
     */
    public abstract AddressesOrder getAddressesOrder();

    /**
     * Specifies type that is used to designate a family of network addresses queried during
     * resolution of host IP addresses.
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


    /**
     * A system-wide {@code InetLookupPolicy}.
     **/
    static final InetLookupPolicy PLATFORM = new PlatformLookupPolicy();

    /**
     * An address lookup policy initialized from {@code "java.net.preferIPv4Stack"},
     * {@code "java.net.preferIPv6Addresses"} system property values, and O/S configuration.
     */
    private final static class PlatformLookupPolicy extends InetLookupPolicy {

        @Override
        public AddressFamily getAddressesFamily() {
            return family;
        }

        @Override
        public AddressesOrder getAddressesOrder() {
            return order;
        }

        private PlatformLookupPolicy() {
            family = initializeFamily();
            order = initializeOrder();
        }

        // Initialize the addresses family field. The following information is used
        // to make a decision about the supported address families:
        //     a) java.net.preferIPv4Stack system property value
        //     b) IPV4 availability is checked by ipv4_available call
        //     c) Type of InetAddress.impl instance
        private static AddressFamily initializeFamily() {
            boolean ipv4Available = isIPv4Available();
            if ("true".equals(PREFER_IPV4_VALUE) && ipv4Available) {
                return AddressFamily.IPV4;
            }
            // Check if IPv6 is not supported
            if (InetAddress.impl instanceof Inet4AddressImpl) {
                return AddressFamily.IPV4;
            }
            // Check if system supports IPv4, if not return IPv6
            if (!ipv4Available) {
                return AddressFamily.IPV6;
            }
            return AddressFamily.ANY;
        }

        // Initialize the order of addresses
        private AddressesOrder initializeOrder() {
            return switch (family) {
                case IPV4, IPV6 -> AddressesOrder.SYSTEM;
                case ANY -> getOrderForAnyAddressFamily();
            };
        }

        // Initializes the order of addresses for case when ANY address family is selected.
        private static AddressesOrder getOrderForAnyAddressFamily() {
            // Logic here is identical to the initialization in InetAddress static initializer
            if (PREFER_IPV6_VALUE == null) {
                return AddressesOrder.IPV4_FIRST;
            } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("true")) {
                return AddressesOrder.IPV6_FIRST;
            } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("false")) {
                return AddressesOrder.IPV4_FIRST;
            } else if (PREFER_IPV6_VALUE.equalsIgnoreCase("system")) {
                return AddressesOrder.SYSTEM;
            } else {
                return AddressesOrder.IPV4_FIRST;
            }
        }

        // Platform's addresses order
        private final AddressesOrder order;

        // Platform's addresses family
        private final AddressFamily family;

        // "java.net.preferIPv4Stack" system property value
        private static final String PREFER_IPV4_VALUE;

        // "java.net.preferIPv6Addresses" system property value
        private static final String PREFER_IPV6_VALUE;

        static {
            PREFER_IPV4_VALUE =
                    GetPropertyAction.privilegedGetProperty("java.net.preferIPv4Stack");
            PREFER_IPV6_VALUE =
                    GetPropertyAction.privilegedGetProperty("java.net.preferIPv6Addresses");
        }
    }

    private static native boolean isIPv4Available();
}
