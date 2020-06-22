/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.nameservice;

import jdk.internal.nameservice.util.StreamUtils;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.UnknownHostException;
import java.net.spi.InetNameServiceProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Comparator;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DnsInetNameServiceProvider extends InetNameServiceProvider {

    public DnsInetNameServiceProvider() {
    }

    @Override
    public NameService get(NameService delegate) {
        return new DnsNameService(delegate);
    }

    @Override
    public String name() {
        return "java";
    }

    public static final class DnsNameService implements NameService {
        private final NameService defaultPlatformNS;

        DnsNameService(NameService defaultPlatformNS) {
            this.defaultPlatformNS = defaultPlatformNS;
        }

        @Override
        public Stream<InetAddress> lookupInetAddresses(String host, ProtocolFamily family) throws UnknownHostException {
            Stream<InetAddress> addresses = NetworkNamesResolver.open(defaultPlatformNS).lookupAllHostAddr(host, family);
            if (order != AddressOrder.DontCare || IPv4Only) {
                Predicate<InetAddress> filter = IPv4Only ? (ia) -> ia instanceof Inet4Address : (ia) -> true;
                addresses = addresses
                        .filter(filter)
                        .sorted(Comparator.comparing(
                                ia -> (ia instanceof Inet4Address && order == AddressOrder.IPv4First) ||
                                      (ia instanceof Inet6Address && order == AddressOrder.IPv6First),
                                Boolean::compareTo)
                                .reversed());
            }
            return StreamUtils.ensureStreamHasAnyElement(addresses, host);
        }

        @Override
        public String lookupHostName(byte[] addr) throws UnknownHostException {
            return NetworkNamesResolver.open(defaultPlatformNS).getHostByAddr(addr);
        }
    }

    enum AddressOrder {
        DontCare,
        IPv4First,
        IPv6First;

        private static AddressOrder fromPreferIPv6AddressesProp(String value) {
            //  Try to match static initialization block in InetAddress
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
                return DontCare;
            }
            return IPv4First;
        }
    }

    static final AddressOrder order;
    static final boolean IPv4Only;

    static {
        var ipv6action = (PrivilegedAction<String>) () -> System.getProperty("java.net.preferIPv6Addresses");
        var ipv4action = (PrivilegedAction<Boolean>) () -> Boolean.getBoolean("java.net.preferIPv4Stack");

        String spValue = System.getSecurityManager() == null ? ipv6action.run() : AccessController.doPrivileged(ipv6action);
        order = AddressOrder.fromPreferIPv6AddressesProp(spValue);
        IPv4Only = System.getSecurityManager() == null ? ipv4action.run() : AccessController.doPrivileged(ipv4action);
    }
}
