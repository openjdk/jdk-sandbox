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

import java.lang.annotation.Native;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.stream.Stream;

/**
 * An interface to provide host name and IP address lookup operations.
 */
public interface InetNameService {

    /**
     * Given the name of a host, returns a stream of IP addresses of the requested
     * address family associated with a provided hostname.
     * <p>
     * {@code host} should be a machine name, such as "{@code www.example.com}",
     * not a textual representation of its IP address. No validation is performed on
     * the given {@code host} name: if a textual representation is supplied, the name
     * resolution is likely to fail and {@link UnknownHostException} may be thrown.
     * <p>
     * The address family type and addresses order are specified by the {@code LookupPolicy} instance.
     * Lookup operation characteristics could be acquired with {@link LookupPolicy#characteristics()}. If
     * {@link InetNameService.LookupPolicy#IPV4} and {@link InetNameService.LookupPolicy#IPV6}
     * characteristics provided then this method returns addresses of both IPV4 and IPV6 families.
     *
     * @param host         the specified hostname
     * @param lookupPolicy the address lookup policy
     * @return a stream of IP addresses for the requested host
     * @throws NullPointerException if {@code host} is {@code null}
     * @throws UnknownHostException if no IP address for the {@code host} could be found
     * @see LookupPolicy
     */
    Stream<InetAddress> lookupAddresses(String host, LookupPolicy lookupPolicy) throws UnknownHostException;

    /**
     * Lookup the host name corresponding to the raw IP address provided.
     *
     * <p>{@code addr} argument is in network byte order: the highest order byte of the address
     * is in {@code addr[0]}.
     *
     * <p> IPv4 address byte array must be 4 bytes long and IPv6 byte array
     * must be 16 bytes long.
     *
     * @param addr byte array representing a raw IP address
     * @return {@code String} representing the host name mapping
     * @throws UnknownHostException     if no host found for the specified IP address
     * @throws IllegalArgumentException if IP address is of illegal length
     */
    String lookupHostName(byte[] addr) throws UnknownHostException; // lookupByAddress, reverseLookup

    /**
     * An addresses lookup policy object to specify a type and an order of addresses
     * supplied to {@link InetNameService#lookupAddresses(String, LookupPolicy)}
     * for filtering and ordering a host name resolution results.
     * <p>
     * The platform-wide lookup policy is constructed by consulting a
     * <a href="doc-files/net-properties.html#Ipv4IPv6">System Properties</a> which affects
     * how IPv4 and IPv6 addresses are returned.
     */
    final class LookupPolicy {

        /**
         * Specifies if IPv4 addresses need to be queried during lookup.
         */
        @Native
        public static final int IPV4 = 1 << 0;

        /**
         * Specifies if IPv6 addresses need to be queried during lookup.
         */
        @Native
        public static final int IPV6 = 1 << 1;

        /**
         * Specifies if IPv4 addresses should be returned first by {@code InetNameService}.
         */
        @Native
        public static final int IPV4_FIRST = 1 << 2;

        /**
         * Specifies if IPv6 addresses should be returned first by {@code InetNameService}.
         */
        @Native
        public static final int IPV6_FIRST = 1 << 3;

        private final int characteristics;

        private LookupPolicy(int characteristics) {
            this.characteristics = characteristics;
        }

        /**
         * This factory method creates {@link LookupPolicy LookupPolicy} with the provided
         * characteristics value.
         *
         * @param characteristics value which represents the set of lookup characteristics
         * @return instance of {@code InetNameServiceProvider.LookupPolicy}
         * @throws IllegalArgumentException if incompatible characteristics are provided
         */
        public static final LookupPolicy of(int characteristics) {
            // At least one type of addresses should be requested
            if ((characteristics & IPV4) == 0 && (characteristics & IPV6) == 0) {
                throw new IllegalArgumentException("No address type specified");
            }

            // Requested order of addresses couldn't be determined
            if ((characteristics & IPV4_FIRST) != 0 && (characteristics & IPV6_FIRST) != 0) {
                throw new IllegalArgumentException("Addresses order cannot be determined");
            }

            // If IPv4 addresses requested to be returned first then they should be requested too
            if ((characteristics & IPV4_FIRST) != 0 && (characteristics & IPV4) == 0) {
                throw new IllegalArgumentException("Addresses order and type do not match");
            }

            // If IPv6 addresses requested to be returned first then they should be requested too
            if ((characteristics & IPV6_FIRST) != 0 && (characteristics & IPV6) == 0) {
                throw new IllegalArgumentException("Addresses order and type do not match");
            }
            return new LookupPolicy(characteristics);
        }

        /**
         * Returns an integer value which specifies lookup operation characteristics.
         * Type and order of address families queried during resolution of host IP addresses.
         *
         * @return a characteristics value
         * @see InetNameService#lookupAddresses(String, LookupPolicy)
         */
        public final int characteristics() {
            return characteristics;
        }
    }
}
