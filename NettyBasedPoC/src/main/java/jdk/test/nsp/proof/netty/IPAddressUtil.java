/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package jdk.test.nsp.proof.netty;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;

/**
 * Utils for a provider to handle IP address related data
 */
public class IPAddressUtil {

    /**
     * Convert IP address bytes to a reverse lookup name suitable
     * for issuing reverse DNS lookups.
     *
     * @param addr array with IP address bytes
     * @return reverse lookup name
     * @throws UnknownHostException IP address array size is of illegal length
     */
    static String bytesToReverseLookupName(byte[] addr) throws UnknownHostException {
        return switch (addr.length) {
            case INADDR16SZ -> ipv6ByteToName(addr);
            case INADDR4SZ -> ipv4BytesToName(addr);
            default -> throw new UnknownHostException("addr is of illegal length");
        };
    }

    /**
     * Convert IPv6 byte array into String representation
     * suitable for constructing name for DNS PTR (reverse)
     * lookup request.
     *
     * @param addr IPv6 address bytes array
     * @return address String representation
     */
    private static String ipv6ByteToName(byte[] addr) {
        StringBuilder addressBuff = new StringBuilder();
        for (int i = addr.length - 1; i >= 0; i--) {
            addressBuff.append(Integer.toHexString((addr[i] & 0x0f)));
            addressBuff.append(".");
            addressBuff.append(Integer.toHexString((addr[i] & 0xf0) >> 4));
            addressBuff.append(".");
        }
        // This NSP is just an implementation example.
        // Therefore, we only use new "IP6.ARPA." root domain name here
        // (see RFC 3512 for details).
        addressBuff.append("IP6.ARPA.");
        return addressBuff.toString();
    }

    /**
     * Convert IPv4 byte array into String representation
     * suitable for constructing name for DNS PTR (reverse)
     * lookup request.
     *
     * @param addr IPv4 address bytes array
     * @return address String representation
     */
    private static String ipv4BytesToName(byte[] addr) {
        StringBuilder addressBuff = new StringBuilder();
        // IPv4 address
        for (int i = addr.length - 1; i >= 0; i--) {
            addressBuff.append(addr[i] & 0xff);
            addressBuff.append(".");
        }
        addressBuff.append("IN-ADDR.ARPA.");
        return addressBuff.toString();
    }

    /**
     * Convert IP address bytes array to a String.
     *
     * @param addr IP address byte array
     * @return String representation of an IP address bytes
     */
    public static String bytesToString(byte[] addr) {
        try {
            InetAddress unresolvedAddress = InetAddress.getByAddress(addr);
            return unresolvedAddress.getHostAddress();
        } catch (UnknownHostException e) {
            return "Wrong sized address array: " + Arrays.toString(addr);
        }
    }

    // IPv4 address size in bytes
    static final int INADDR4SZ = 4;

    // IPv6 address size in bytes
    static final int INADDR16SZ = 16;
}
