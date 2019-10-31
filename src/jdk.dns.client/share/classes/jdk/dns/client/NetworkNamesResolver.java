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
import jdk.dns.client.internal.DnsName;
import jdk.dns.client.internal.HostsFileResolver;
import jdk.dns.client.internal.Resolver;
import jdk.dns.client.internal.ResourceClassType;
import jdk.dns.client.internal.ResourceRecord;
import jdk.dns.client.internal.ResourceRecords;
import jdk.dns.conf.DnsResolverConfiguration;
import sun.net.util.IPAddressUtil;

import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.UnknownHostException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class NetworkNamesResolver {

    private final AddressFamily addressFamily;

    public static NetworkNamesResolver open() throws UnknownHostException {
        return new NetworkNamesResolver(AddressFamily.ANY);
    }

    public static NetworkNamesResolver open(ProtocolFamily protocolFamily) throws UnknownHostException {
        return new NetworkNamesResolver(AddressFamily.fromProtocolFamily(protocolFamily));
    }

    private NetworkNamesResolver(AddressFamily addressFamily) throws UnknownHostException {
        this.addressFamily = addressFamily;
        this.dnsResolver = new Resolver(dnsResolverConfiguration.nameservers(), 1000, 4);
    }


    /**
     * Lookup the IP address of a host.
     * The family of required address needed can be specified with {@code addressFamily} parameter.
     *
     * @param hostname the specified host name
     * @return first IP address that matches requested {@code addressFamily}
     * @throws UnknownHostException  if no IP address found for the specified host name and the specified address family
     */
    public InetAddress lookupHostAddr(String hostname) throws UnknownHostException {

        // First try hosts file
        // TODO: Add nsswitch.conf to select proper order
        try {
            return hostsFileResolver.getHostAddress(hostname, addressFamily);
        } catch (UnknownHostException uhe) {
            if (DEBUG) {
                System.err.printf("Hosts file doesn't know '%s' host with '%s' address family%n",
                        hostname, addressFamily.toString());
            }
        }

        // If no luck - try to ask name servers
        try {
            var results = lookup(hostname, addressFamily, true, true, 0);
            if (results.isEmpty()) {
                throw new UnknownHostException(hostname);
            }
            return results.get(0);
        } catch (DnsResolverException e) {
            UnknownHostException uhe = new UnknownHostException(hostname);
            uhe.initCause(e);
            throw uhe;
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
            return List.of(hostsFileResolver.getHostAddress(hostname));
        } catch (UnknownHostException uhe) {
            if (DEBUG) {
                System.err.printf("Resolver API: Hosts file doesn't know '%s' host%n", hostname);
            }
        }
        try {
            var results = lookup(hostname, AddressFamily.ANY, false, true, 0);
            if (results.isEmpty()) {
                throw new UnknownHostException(hostname + " unknown host name");
            }
            return results;
        } catch (DnsResolverException e) {
            UnknownHostException uhe = new UnknownHostException(hostname);
            uhe.initCause(e);
            throw uhe;
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
        try {
            var literalIP = addressToLiteralIP(address);
            var family = AddressFamily.fromInetAddress(address);
            var results = rlookup(literalIP, family);
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

    private Set<String> prepareDomainsSearchList() {
        var domainsToSearch = new LinkedHashSet<String>();
        var domain = dnsResolverConfiguration.domain();
        // First will try the domain
        if (!domain.isBlank()) {
            domainsToSearch.add(domain);
        }
        // Then will iterate over search list
        domainsToSearch.addAll(dnsResolverConfiguration.searchlist());
        if (DEBUG) {
            System.out.printf("Domains search list:%s%n", domainsToSearch);
        }
        return domainsToSearch;
    }

    private List<InetAddress> lookup(String host, AddressFamily addressFamily, boolean oneIsEnough, boolean checkDomains, int depth) throws UnknownHostException, DnsResolverException {

        if (DEBUG) {
            System.out.printf("Resolver API: internal lookup call - %s%n", host);
        }

        // Will remain the same during DNS queries execution
        ResourceClassType ct = ResourceClassType.fromAddressFamily(addressFamily);
        ResourceRecords rrs = null;

        // First try to get the resource records with the requested DNS host name if it is FQDN
        if (host.contains(".")) {
            rrs = execAddrResolutionQuery(new DnsName(host), ct);
        }
        // Non-FQDN or not known host address and domains still can be checked
        if ((rrs == null || rrs.answer.isEmpty()) && checkDomains) {
            for (String domain : prepareDomainsSearchList()) {
                String hostWithSuffix = host + "." + domain;
                if (DEBUG) {
                    System.err.printf("Resolver API: Trying to lookup:'%s'%n", hostWithSuffix);
                }
                rrs = execAddrResolutionQuery(new DnsName(hostWithSuffix), ct);
                if (rrs != null && !rrs.answer.isEmpty()) {
                    if (DEBUG) {
                        System.err.printf("Resolver API: Found host in '%s' domain%n", domain);
                    }
                    break;
                }
            }
        }

        // If no answers then host is not known in registered domain
        if (rrs == null || rrs.answer.isEmpty()) {
            throw new UnknownHostException(host);
        }

        // Parse answers
        List<InetAddress> results = new ArrayList<>();
        for (int i = 0; i < rrs.answer.size(); i++) {
            ResourceRecord rr = rrs.answer.elementAt(i);
            int answerType = rr.getType();
            String recordData = rr.getRdata().toString();
            if (DEBUG) {
                System.err.printf("Resolver API: Got %s type: %s%n", ResourceRecord.getTypeName(rr.getType()), rr.getRdata());
            }
            if (answerType == ResourceRecord.TYPE_CNAME) {
                // We've got CNAME entry - issue another request to resolve the canonical host name
                if (depth > MAX_CNAME_RESOLUTION_DEPTH) {
                    throw new UnknownHostException(host);
                }
                List<InetAddress> cnameResults = lookup(recordData, addressFamily, oneIsEnough, false, depth + 1);
                if (oneIsEnough) {
                    return cnameResults;
                } else {
                    results.addAll(cnameResults);
                }
            } else {
                byte[] addrBytes;
                if (answerType == ResourceRecord.TYPE_A && (addressFamily == AddressFamily.IPv4 || addressFamily == AddressFamily.ANY)) {
                    addrBytes = IPAddressUtil.textToNumericFormatV4(recordData);
                    if (addrBytes == null) {
                        if (DEBUG) {
                            System.err.println("Incorrect A resource record answer");
                        }
                        return Collections.emptyList();
                    }
                } else if (answerType == ResourceRecord.TYPE_AAAA && (addressFamily == AddressFamily.IPv6 || addressFamily == AddressFamily.ANY)) {
                    addrBytes = IPAddressUtil.textToNumericFormatV6(recordData);
                    if (addrBytes == null) {
                        if (DEBUG) {
                            System.err.println("Incorrect AAAA resource record answer");
                        }
                        return Collections.emptyList();
                    }
                } else {
                    continue;
                }
                // IPAddressUtil.textToNumeric can return null
                if (addrBytes == null) {
                    throw new RuntimeException("Internal error");
                }
                InetAddress address = InetAddress.getByAddress(host, addrBytes);
                results.add(address);
                if (oneIsEnough) {
                    break;
                }
            }
        }

        return results;
    }

    // Can return null if query failed
    private ResourceRecords execAddrResolutionQuery(DnsName name, ResourceClassType ct) {
        // Not supporting authoritative requests [for now?]
        try {
            ResourceRecords rrs = dnsResolver.query(name, ct.getRecordClass(), ct.getRecordType(),
                    true, false);
            return rrs.hasAddressOrAlias() ? rrs : null;
        } catch (DnsResolverException e) {
            if (DEBUG) {
                System.err.println("Query failed:" + e.getMessage());
                e.printStackTrace();
            }
            return null;
        }
    }

    private List<String> rlookup(String literalIP, AddressFamily family) throws DnsResolverException {
        switch (family) {
            case IPv4:
                return ipv4rlookup(literalIP);
            case IPv6:
                return ipv6rlookup(literalIP);
            default:
                throw new RuntimeException("Only IPv4 and IPv6 addresses are supported");
        }
    }

    private List<String> ipv4rlookup(String literalIP) throws DnsResolverException {
        var request = literalIP + "IN-ADDR.ARPA.";
        var hostNames = new ArrayList<String>();
        var name = new DnsName(request);
        var rrs = dnsResolver.query(name, ResourceRecord.CLASS_INTERNET,
                ResourceRecord.TYPE_PTR, true, false);
        for (int i = 0; i < rrs.answer.size(); i++) {
            ResourceRecord rr = rrs.answer.elementAt(i);
            hostNames.add(rr.getRdata().toString());
        }
        return hostNames;
    }

    private List<String> ipv6rlookup(String literalIP) throws DnsResolverException {
        List<String> hostNames = new ArrayList<>();
        DnsName name = new DnsName(literalIP + "IP6.ARPA.");
        ResourceRecords rrs = dnsResolver.query(name, ResourceRecord.CLASS_INTERNET,
                ResourceRecord.TYPE_PTR, true, false);
        /**
         * Because RFC 3152 changed the root domain name for reverse
         * lookups from IP6.INT. to IP6.ARPA., we need to check
         * both. I.E. first the new one, IP6.ARPA, then if it fails
         * the older one, IP6.INT
         */
        if (rrs.answer.isEmpty()) {
            name = new DnsName(literalIP + "IP6.INT.");
            rrs = dnsResolver.query(name, ResourceRecord.CLASS_INTERNET,
                    ResourceRecord.TYPE_PTR, true, false);
        }
        for (int i = 0; i < rrs.answer.size(); i++) {
            ResourceRecord rr = rrs.answer.elementAt(i);
            hostNames.add(rr.getRdata().toString());
        }
        return hostNames;
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

    private static DnsResolverConfiguration dnsResolverConfiguration = new DnsResolverConfiguration();
    private static HostsFileResolver hostsFileResolver = new HostsFileResolver();

    // TODO: Add system property with the sequence of resolution OR nsswitch.conf location to get the order:
    //     file, name
    private final Resolver dnsResolver;
    private static final boolean DEBUG = java.security.AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> Boolean.getBoolean("jdk.dns.client.debug"));
    private static final int MAX_CNAME_RESOLUTION_DEPTH = 4;
}
