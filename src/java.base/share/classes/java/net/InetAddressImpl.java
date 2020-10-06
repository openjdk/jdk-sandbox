/*
 * Copyright (c) 2002, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.net.spi.InetNameServiceProvider.LookupPolicy;

/*
 * Package private interface to "implementation" used by
 * {@link InetAddress}.
 * <p>
 * See {@link java.net.Inet4AddressImp} and
 * {@link java.net.Inet6AddressImp}.
 *
 * @since 1.4
 */
interface InetAddressImpl {

    String getLocalHostName() throws UnknownHostException;
    InetAddress[]
        lookupAllHostAddr(String hostname, LookupPolicy lookupPolicy) throws UnknownHostException;
    String getHostByAddr(byte[] addr) throws UnknownHostException;

    InetAddress anyLocalAddress();
    InetAddress loopbackAddress();
    boolean isReachable(InetAddress addr, int timeout, NetworkInterface netif,
                        int ttl) throws IOException;

    /**
     * Encodes the lookup policy to an integer descriptor.
     * @param lookupPolicy addresses lookup policy
     * @return integer value that contains the encoded lookup policy
     */
    default int policyToNativeDescriptor(LookupPolicy lookupPolicy) {
        int value = 0;
        value |= switch (lookupPolicy.searchStrategy()) {
            case IPV4_ONLY -> IPV4_ADDRESS_FAMILY_VALUE;
            case IPV6_ONLY -> IPV6_ADDRESS_FAMILY_VALUE;
            default -> ANY_ADDRESS_FAMILY_VALUE;
        };

        value |= switch (lookupPolicy.searchStrategy()) {
            case IPV4_FIRST -> IPV4_FIRST_ADDRESSES_ORDER_VALUE;
            case IPV6_FIRST -> IPV6_FIRST_ADDRESSES_ORDER_VALUE;
            default -> SYSTEM_ADDRESSES_ORDER_VALUE;
        };

        return value & (ADDRESS_FAMILY_MASK | ADDRESSES_ORDER_MASK);
    }

    int ANY_ADDRESS_FAMILY_VALUE = 0x01;
    int IPV4_ADDRESS_FAMILY_VALUE = 0x02;
    int IPV6_ADDRESS_FAMILY_VALUE = 0x04;
    int ADDRESS_FAMILY_MASK = 0x0f;

    int SYSTEM_ADDRESSES_ORDER_VALUE = 0x10;
    int IPV4_FIRST_ADDRESSES_ORDER_VALUE = 0x20;
    int IPV6_FIRST_ADDRESSES_ORDER_VALUE = 0x40;
    int ADDRESSES_ORDER_MASK = 0xf0;
}
