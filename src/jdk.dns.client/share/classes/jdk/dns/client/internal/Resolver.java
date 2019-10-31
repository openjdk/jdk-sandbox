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

import jdk.dns.client.ex.DnsResolverException;

import java.net.UnknownHostException;
import java.util.List;

// TODO: Remove this one
// TODO: All with close() implements AutoCloseable
// TODO: Analyse exceptions and remove all different types if not needed to maintain code-flow
public class Resolver implements AutoCloseable {
    private DnsClient dnsClient;

    /*
     * Constructs a new Resolver given package oracle.dns.client.standalone;its servers and timeout parameters.
     * Each server is of the form "server[:port]".
     * IPv6 literal host names include delimiting brackets.
     * There must be at least one server.
     * "timeout" is the initial timeout interval (in ms) for UDP queries,
     * and "retries" gives the number of retries per server.
     */
    public Resolver(List<String> servers, int timeout, int retries)
            throws UnknownHostException {
        dnsClient = new DnsClient(servers, timeout, retries);
    }

    public void close() {
        dnsClient.close();
        dnsClient = null;
    }

    /*
     * Queries resource records of a particular class and type for a
     * given domain name.
     * Useful values of rrclass are ResourceRecord.[Q]CLASS_xxx.
     * Useful values of rrtype are ResourceRecord.[Q]TYPE_xxx.
     * If recursion is true, recursion is requested on the query.
     * If auth is true, only authoritative responses are accepted.
     */
    public ResourceRecords query(DnsName fqdn, int rrclass, int rrtype,
                                 boolean recursion, boolean auth)
            throws DnsResolverException {
        return dnsClient.query(fqdn, rrclass, rrtype, recursion, auth);
    }
}
