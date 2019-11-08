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
import sun.net.util.IPAddressUtil;

import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.UnknownHostException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.stream.Collectors;

// This class handles DNS requests to perform host name to IP addresses resolutions.
// Reverse-DNS requests are handled by DnsResolver itself
public class AddressResolutionQueue {
    /**
     * Resolve hostname with provided @{code DnsResolver}
     *
     * @param dnsResolver      resolver that will be used to issue DNS requests
     * @param hostName         host name to resolve
     * @param family           address family to search
     * @param needAllAddresses search for all available addresses
     * @return list of found addresses
     * @throws UnknownHostException host name cannot be resolved to an address
     */
    public static List<InetAddress> resolve(DnsResolver dnsResolver,
                                            String hostName,
                                            ProtocolFamily family,
                                            boolean needAllAddresses) throws UnknownHostException {
        AddressResolutionQueue rq = new AddressResolutionQueue(dnsResolver,
                hostName,
                AddressFamily.fromProtocolFamily(family),
                needAllAddresses);
        var rqResults = rq.resolve();
        if (rqResults.isEmpty()) {
            throw new UnknownHostException(hostName);
        }

        // Parse answers
        var results = new ArrayList<InetAddress>();
        for (var result : rqResults) {
            byte[] addrBytes = null;
            switch (result.getType()) {
                case IPv4:
                    addrBytes = IPAddressUtil.textToNumericFormatV4(result.getAddressString());
                    break;
                case IPv6:
                    addrBytes = IPAddressUtil.textToNumericFormatV6(result.getAddressString());
                    break;
                default:
                    break;
            }

            if (addrBytes == null) {
                if (DEBUG) {
                    System.err.println("Bad address format: " + result.getAddressString());
                }
                continue;
            }
            InetAddress address = InetAddress.getByAddress(hostName, addrBytes);
            results.add(address);
            if (!needAllAddresses) {
                break;
            }
        }
        return results;
    }

    private AddressResolutionQueue(DnsResolver dnsResolver, String nameToResolve,
                                   AddressFamily requestedAddressType, boolean needAllAddresses) {
        this.dnsResolver = dnsResolver;
        this.nameToResolve = nameToResolve;
        this.requestedAddressType = requestedAddressType;
        this.needAllAddresses = needAllAddresses;
    }

    private void fillQueue() {
        if (nameToResolve.contains("."))
            addQueriesForHostname(nameToResolve);
        for (var domainName : dnsResolver.getDomainsSearchList()) {
            addQueriesForHostname(nameToResolve + "." + domainName);
        }
    }

    private List<ResolvedAddress> resolve() {
        // Used in non-ANY mode and stores the host name that yielded some result (A or AAAA)
        String gotResultsFor = "";
        // Init queue with ANY requests first
        isAnyMode = USE_ANY_SP_VALUE;
        fillQueue();
        List<ResolvedAddress> addresses = new ArrayList<>();
        while (true) {
            if (queue.isEmpty()) {
                if (isAnyMode) {
                    // If previous request was for ANY address and no A/AAAA/CNAME replies were received
                    // then populate queue with separate A/AAAA/CNAME requests
                    isAnyMode = false;
                    cnameCount = 0;
                    fillQueue();
                } else {
                    // Tried everything - return what we have
                    return Collections.unmodifiableList(addresses);
                }
            }
            // Output queue for debugging purposes
            if (DEBUG) {
                System.out.println("================DNS Resolution queue==================");
                System.out.println("Address resolution queue:" + queue);
                System.out.println("======================================================");
            }
            // Execute request
            var resRequest = queue.poll();
            // If not in ANY mode and already found something - continue searches only for the same
            // name and only for the left addresses and no need to process CNAME requests
            if (gotResultsFor.isEmpty() || (resRequest.hostName.equals(gotResultsFor)
                    && resRequest.resourceId != ResourceRecord.TYPE_CNAME)) {
                var result = executeRequest(resRequest, !gotResultsFor.isEmpty());
                addresses.addAll(result);
            }

            // If found something and is in ANY mode - return the addresses
            // If in separate A/AAAA requests - update host name with found address to filter out
            // other requests
            if (!addresses.isEmpty()) {
                if (isAnyMode || !needAllAddresses) {
                    return Collections.unmodifiableList(addresses);
                } else if (gotResultsFor.isEmpty()) {
                    gotResultsFor = resRequest.hostName;
                }
            }
        }
    }

    private void addQueriesForHostname(String hostname) {
        if (isAnyMode) {
            queue.add(ResolutionRequest.of(hostname, ResourceRecord.TYPE_ANY));
        } else {
            if (requestedAddressType == AddressFamily.ANY || requestedAddressType == AddressFamily.IPv4) {
                queue.add(ResolutionRequest.of(hostname, ResourceRecord.TYPE_A));
            }
            if (requestedAddressType == AddressFamily.ANY || requestedAddressType == AddressFamily.IPv6) {
                queue.add(ResolutionRequest.of(hostname, ResourceRecord.TYPE_AAAA));
            }
            queue.add(ResolutionRequest.of(hostname, ResourceRecord.TYPE_CNAME));
        }
    }

    private List<ResolvedAddress> executeRequest(ResolutionRequest request, boolean alreadyHaveResults) {

        try {
            var rrs = dnsResolver.query(request.hostName, request.resourceId);
            if (DEBUG) {
                System.err.println("================DNS Resolution queue==================");
                System.err.printf("Got answers for '%s':%n", request);
                System.err.println(rrs.answer);
                System.err.println("======================================================");
            }
            // If no addresses or aliases - return
            if (!rrs.hasAddressesOrAlias()) {
                return Collections.emptyList();
            }
            // Has addresses
            if (rrs.hasAddressOfFamily(AddressFamily.ANY)) {
                // if no address of requested type but has other addresses
                // clear the queue and return empty list
                // Clear queue if address is found or doesn't match the
                // requested address type - only for ANY requests
                if (request.resourceId == ResourceRecord.TYPE_ANY) {
                    queue.clear();
                }
                if (!rrs.hasAddressOfFamily(requestedAddressType)) {
                    return Collections.emptyList();
                }
                // Parse addresses
                return rrs.answer.stream()
                        .filter(requestedAddressType::matchesResourceRecord)
                        .map(ResolvedAddress::of)
                        .collect(Collectors.toList());
            }
            // If has alias - clear the queue and add new requests with
            // the alias name. Do it only for cases when no addresses is found
            if (!alreadyHaveResults) {
                Optional<String> aliasO = rrs.answer.stream()
                        .filter(rr -> rr.rrtype == ResourceRecord.TYPE_CNAME)
                        .map(ResourceRecord::getRdata)
                        .map(Object::toString)
                        .findFirst();
                if (aliasO.isPresent()) {
                    queue.clear();
                    String cname = aliasO.get();
                    if (DEBUG) {
                        System.err.println("Found alias: " + cname);
                    }
                    cnameCount++;
                    if (cnameCount > MAX_CNAME_RESOLUTION_DEPTH) {
                        throw new RuntimeException("CNAME loop detected");
                    }
                    addQueriesForHostname(cname);
                }
            }
            // Filter the results depending on typeOfAddress
            return Collections.emptyList();
        } catch (DnsResolverException e) {
            return Collections.emptyList();
        }
    }

    static class ResolvedAddress {
        final AddressFamily type;
        final String addressString;

        private ResolvedAddress(AddressFamily type, String addressString) {
            this.type = type;
            this.addressString = addressString;
        }

        public AddressFamily getType() {
            return type;
        }

        public String getAddressString() {
            return addressString;
        }

        public static ResolvedAddress of(ResourceRecord resourceRecord) {
            AddressFamily type = AddressFamily.fromResourceRecord(resourceRecord);
            String addressString = resourceRecord.getRdata().toString();
            return new ResolvedAddress(type, addressString);
        }

        @Override
        public String toString() {
            return "[" + type.name() + "]:" + addressString;
        }
    }

    static class ResolutionRequest {
        final int resourceId;
        final String hostName;

        private ResolutionRequest(String hostName, int resourceId) {
            this.resourceId = resourceId;
            this.hostName = hostName;
        }

        public boolean isAny() {
            return resourceId == ResourceRecord.TYPE_ANY;
        }

        public static ResolutionRequest of(String hostName, int resourceId) {
            assert hostName != null && !hostName.isBlank();
            return new ResolutionRequest(hostName, resourceId);
        }

        @Override
        public String toString() {
            return hostName + ":" + resourceId;
        }
    }

    // DNS resolver instance to issue DNS queries
    private final DnsResolver dnsResolver;
    // Queue with address resolution requests (ANY/A/AAAA or CNAME)
    private final Queue<ResolutionRequest> queue = new LinkedList<>();

    // Internal state variables to control queue processing
    // Type of requested address
    private final AddressFamily requestedAddressType;
    // Requested name to resolve
    private final String nameToResolve;
    // Is it getAllByName [true] or getByName [false]
    private final boolean needAllAddresses;
    // CNAME lookups counter needed to avoid alias loops
    private int cnameCount = 0;
    // Private resolution queue has two modes - ANY and single requests.
    // The mode in use is tracked with this boolean flag
    private boolean isAnyMode;
    // Use any mode property value
    private static final boolean USE_ANY_SP_VALUE = java.security.AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> Boolean.getBoolean("jdk.dns.client.use.any"));
    // Maximum number of sequential CNAME requests
    private static final int MAX_CNAME_RESOLUTION_DEPTH = 4;
    // Enable debug output
    private static final boolean DEBUG = java.security.AccessController.doPrivileged(
            (PrivilegedAction<Boolean>) () -> Boolean.getBoolean("jdk.dns.client.debug"));


}
