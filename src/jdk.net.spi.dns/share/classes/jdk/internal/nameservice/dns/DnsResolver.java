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

package jdk.internal.nameservice.dns;

import jdk.internal.nameservice.dns.ex.DnsResolverException;
import jdk.internal.nameservice.util.AddressFamily;
import jdk.internal.nameservice.conf.DnsResolverConfiguration;

import java.net.InetAddress;
import java.net.spi.InetNameServiceProvider;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

public class DnsResolver implements AutoCloseable {
    private DnsClient dnsClient;

    /*
     * Constructs a new Resolver given package oracle.dns.client.standalone;its servers and timeout parameters.
     * Each server is of the form "server[:port]".
     * IPv6 literal host names include delimiting brackets.
     * There must be at least one server.
     * "timeout" is the initial timeout interval (in ms) for UDP queries,
     * and "retries" gives the number of retries per server.
     */
    public DnsResolver() {
        PrivilegedAction<List<String>> pa = () -> sun.net.dns.ResolverConfigurationImpl.open().nameservers();
        List<String> nameservers = System.getSecurityManager() == null ? pa.run() : AccessController.doPrivileged(pa);
        dnsClient = new DnsClient(nameservers, 1000, 4);
    }

    @Override
    public void close() {
        dnsClient.close();
        dnsClient = null;
    }

    public List<String> getDomainsSearchList() {
        PrivilegedAction<List<String>> pa = () -> sun.net.dns.ResolverConfigurationImpl.open().searchlist();
        return System.getSecurityManager() == null ? pa.run() : AccessController.doPrivileged(pa);
    }

    public Stream<InetAddress> resolvePlatform(String hostName, InetNameServiceProvider.NameService defaultPlatformNS) {
        return dnsResolverConfiguration.nativeLookup0(hostName, defaultPlatformNS);
    }

    public String reverseResolvePlatform(byte[] address, InetNameServiceProvider.NameService defaultPlatformNS) {
        if (DEBUG) {
            System.err.printf("Calling reverse resolve platform for: %s%n", Arrays.toString(address));
        }
        if (address != null) {
            return dnsResolverConfiguration.nativeReverseLookup0(address, defaultPlatformNS);
        } else {
            return null;
        }
    }

    /*
     * Queries resource records of a particular class and type for a
     * given domain name.
     * Useful values of rrtype are ResourceRecord.[Q]TYPE_xxx.
     */
    public ResourceRecords query(String fqdn, int rrtype)
            throws DnsResolverException {
        return dnsClient.query(new DnsName(fqdn), ResourceRecord.CLASS_INTERNET, rrtype, true, false);
    }

    public List<String> rlookup(String literalIP, AddressFamily addressFamily) throws DnsResolverException {
        return switch (addressFamily) {
            case IPv4 -> ipv4rlookup(literalIP);
            case IPv6 -> ipv6rlookup(literalIP);
            default -> throw new RuntimeException("Only IPv4 and IPv6 addresses are supported");
        };
    }

    private List<String> ipv4rlookup(String literalIP) throws DnsResolverException {
        var request = literalIP + "IN-ADDR.ARPA.";
        var hostNames = new ArrayList<String>();
        var rrs = query(request, ResourceRecord.TYPE_PTR);
        for (int i = 0; i < rrs.answer.size(); i++) {
            ResourceRecord rr = rrs.answer.elementAt(i);
            hostNames.add(rr.getRdata().toString());
        }
        return hostNames;
    }

    private List<String> ipv6rlookup(String literalIP) throws DnsResolverException {
        List<String> hostNames = new ArrayList<>();
        ResourceRecords rrs = query(literalIP + "IP6.ARPA.", ResourceRecord.TYPE_PTR);
        /*
         * Because RFC 3152 changed the root domain name for reverse
         * lookups from IP6.INT. to IP6.ARPA., we need to check
         * both. I.E. first the new one, IP6.ARPA, then if it fails
         * the older one, IP6.INT
         */
        if (rrs.answer.isEmpty()) {
            rrs = query(literalIP + "IP6.INT.", ResourceRecord.TYPE_PTR);
        }
        for (int i = 0; i < rrs.answer.size(); i++) {
            ResourceRecord rr = rrs.answer.elementAt(i);
            hostNames.add(rr.getRdata().toString());
        }
        return hostNames;
    }


    // Configuration file tracker
    private static final DnsResolverConfiguration dnsResolverConfiguration = new DnsResolverConfiguration();
    private static final boolean DEBUG = java.security.AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> Boolean.getBoolean("jdk.dns.client.debug"));
}
