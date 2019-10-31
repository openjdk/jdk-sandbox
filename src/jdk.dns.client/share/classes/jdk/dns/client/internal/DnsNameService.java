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

package jdk.dns.client.internal;

import jdk.dns.client.NetworkNamesResolver;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Comparator;

public class DnsNameService implements InetAddress.NameService {
    private static final InetAddress[] NONE = new InetAddress[0];

    enum AddressOrder {
        DontCare,
        IPv4First,
        IPv6First;

        private static AddressOrder fromString(String value) {
            if (value == null) {
                return IPv4First;
            }
            if ("true".equals(value)) {
                return IPv6First;
            }
            if ("false".equals(value)) {
                return IPv4First;
            }
            if ("system".equals(value)) {
                return DontCare; // TODO: Decide if it is compatible way with default InetAddress resolver
            }
            return IPv4First;
        }
    }

    static final AddressOrder order;

    static {
        var action = (PrivilegedAction<String>) () -> System.getProperty("java.net.preferIPv6Addresses");

        String spValue = System.getSecurityManager() == null ? action.run() : AccessController.doPrivileged(action);
        order = AddressOrder.fromString(spValue);
    }

    public DnsNameService() {
    }

    @Override
    public InetAddress[] lookupAllHostAddr(String host) throws UnknownHostException {
        if (order == AddressOrder.DontCare) {
            return NetworkNamesResolver.open().lookupAllHostAddr(host).toArray(NONE);
        } else {
            return NetworkNamesResolver.open()
                    .lookupAllHostAddr(host)
                    .stream()
                    .sorted(
                            Comparator.comparing(
                                    ia -> (ia instanceof Inet4Address && order == AddressOrder.IPv4First)
                                       || (ia instanceof Inet6Address && order == AddressOrder.IPv6First),
                                    Boolean::compareTo)
                                    .reversed()
                    ).toArray(InetAddress[]::new);
        }
    }

    @Override
    public String getHostByAddr(byte[] addr) throws UnknownHostException {
        return NetworkNamesResolver.open().getHostByAddr(InetAddress.getByAddress(addr));
    }
}
