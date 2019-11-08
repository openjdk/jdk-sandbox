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

package jdk.dns.client;

import jdk.dns.client.ex.DnsResolverException;
import jdk.dns.client.internal.AddressResolutionQueue;
import jdk.dns.client.internal.DnsResolver;
import jdk.dns.client.internal.HostsFileResolver;

import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.UnknownHostException;
import java.security.PrivilegedAction;
import java.util.List;
import java.util.stream.Collectors;

public class NetworkNamesResolver {

    private final ProtocolFamily protocolFamily;

    public static NetworkNamesResolver open() throws UnknownHostException {
        // null for any (IPv4+IPv6) addresses family
        return new NetworkNamesResolver(null);
    }

    public static NetworkNamesResolver open(ProtocolFamily protocolFamily) throws UnknownHostException {
        return new NetworkNamesResolver(protocolFamily);
    }

    private NetworkNamesResolver(ProtocolFamily protocolFamily) throws UnknownHostException {
        this.protocolFamily = protocolFamily;
    }

    /**
     * Lookup the IP address of a host.
     * The family of required address needed can be specified with {@code addressFamily} parameter.
     *
     * @param hostname the specified host name
     * @return first IP address that matches requested {@code addressFamily}
     * @throws UnknownHostException if no IP address found for the specified host name and the specified address family
     */
    public InetAddress lookupHostAddr(String hostname) throws UnknownHostException {
        // First try hosts file
        // TODO: Add nsswitch.conf to select proper order
        try {
            return hostsFileResolver.getHostAddress(hostname, protocolFamily);
        } catch (UnknownHostException uhe) {
            if (DEBUG) {
                System.err.printf("Hosts file doesn't know '%s' host with '%s' address family%n",
                        hostname, protocolFamily == null ? "ANY" : protocolFamily.toString());
            }
        }

        // If no luck - try to ask name servers
        try (DnsResolver dnsResolver = new DnsResolver()) {
            var results = lookup(dnsResolver, hostname, protocolFamily, false);
            return results.get(0);
        }
    }

    /**
     * <p>Lookup a host mapping by name. Retrieve the IP addresses
     * associated with a host.
     *
     * @param hostname the specified hostname
     * @return array of IP addresses for the requested host
     * @throws UnknownHostException if no IP address for the {@code hostname} could be found
     */
    public List<InetAddress> lookupAllHostAddr(String hostname) throws UnknownHostException {
        // First try hosts file
        // TODO: Add nsswitch.conf ReloadTracker and parser to select proper order
        try {
            return List.of(hostsFileResolver.getHostAddress(hostname, protocolFamily));
        } catch (UnknownHostException uhe) {
            if (DEBUG) {
                System.err.printf("Resolver API: Hosts file doesn't know '%s' host%n", hostname);
            }
        }

        try (var dnsResolver = new DnsResolver()) {
            var results = lookup(dnsResolver, hostname, null, true);
            if (results.isEmpty()) {
                throw new UnknownHostException(hostname + " unknown host name");
            }
            return results;
        }
    }

    /**
     * Lookup the host name  corresponding to the IP address provided.
     *
     * @param address the specified IP address
     * @return {@code String} representing the host name
     * @throws UnknownHostException if no host found for the specified IP address
     */
    public String getHostByAddr(InetAddress address) throws UnknownHostException {
        var results = getAllHostsByAddr(address);
        return results.get(0);
    }

    /**
     * Lookup all known host names which correspond to the IP address provided
     *
     * @param address the specified IP address
     * @return array of {@code String} representing the host names
     * @throws UnknownHostException if no host found for the specified IP address
     */
    public List<String> getAllHostsByAddr(InetAddress address) throws UnknownHostException {
        // First try hosts file
        // TODO: Add nsswitch.conf to select proper order
        try {
            return List.of(hostsFileResolver.getByAddress(address));
        } catch (UnknownHostException uhe) {
            if (DEBUG) {
                System.err.printf("Resolver API: No host in hosts file with %s address%n", address);
            }
        }
        try (DnsResolver dnsResolver = new DnsResolver()) {
            var literalIP = addressToLiteralIP(address);
            var results = dnsResolver.rlookup(literalIP, address);
            if (results.isEmpty()) {
                throw new UnknownHostException();
            }
            // remove trailing dot
            return results.stream()
                    .map(host -> host.endsWith(".") ? host.substring(0, host.length() - 1) : host)
                    .collect(Collectors.toList());
        } catch (DnsResolverException dre) {
            UnknownHostException uhe = new UnknownHostException();
            uhe.initCause(dre);
            throw uhe;
        }
    }

    private static String addressToLiteralIP(InetAddress address) {
        byte[] bytes = address.getAddress();
        StringBuilder addressBuff = new StringBuilder();
        // IPv4 address
        if (bytes.length == 4) {
            for (int i = bytes.length - 1; i >= 0; i--) {
                addressBuff.append(bytes[i] & 0xff);
                addressBuff.append(".");
            }
            // IPv6 address
        } else if (bytes.length == 16) {
            for (int i = bytes.length - 1; i >= 0; i--) {
                addressBuff.append(Integer.toHexString((bytes[i] & 0x0f)));
                addressBuff.append(".");
                addressBuff.append(Integer.toHexString((bytes[i] & 0xf0) >> 4));
                addressBuff.append(".");
            }
        } else {
            return null;
        }
        return addressBuff.toString();
    }

    private List<InetAddress> lookup(DnsResolver dnsResolver, String host, ProtocolFamily protocolFamily, boolean needAllAddresses) throws UnknownHostException {
        if (DEBUG) {
            System.out.printf("Resolver API: internal lookup call - %s%n", host);
        }
        return AddressResolutionQueue.resolve(dnsResolver, host, protocolFamily, needAllAddresses);
    }

    private static HostsFileResolver hostsFileResolver = new HostsFileResolver();
    private static final boolean DEBUG = java.security.AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> Boolean.getBoolean("jdk.dns.client.debug"));
}
