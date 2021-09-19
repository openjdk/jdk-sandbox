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

package jdk.test.nsp.proof.jndi;

import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.spi.NamingManager;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetAddressResolver;
import java.net.spi.InetAddressResolverProvider;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV4_FIRST;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6;
import static java.net.spi.InetAddressResolver.LookupPolicy.IPV6_FIRST;

/**
 * This is a proof-of-concept resolver provider implementation that exercises new
 * {@code java.net.spi.InetAddressResolverProvider} API to implement an experimental
 * resolver based on
 * <a href="https://download.java.net/java/early_access/jdk17/docs/api/java.naming/javax/naming/directory/package-summary.html">
 * javax.naming DNS directory service</a>.
 */
public class ProviderImpl extends InetAddressResolverProvider {

    /**
     * Create instance of {@code ResolverImpl}.
     *
     * @param configuration platform built-in address resolution configuration
     * @return resolver instance
     */
    @Override
    public InetAddressResolver get(Configuration configuration) {
        InetAddressResolver ns = new ResolverImpl(configuration);
        System.err.println("Returning instance of jdk.test.nsp.proof.jndi.ProviderImpl NS:" + ns);
        return ns;
    }

    /**
     * Returns the name of this provider.
     *
     * @return the resolver provider name
     */
    @Override
    public String name() {
        return "jdk.test.nsp.proof.jndi.ProviderImpl";
    }

    /**
     * JNDI/DNS based experimental resolver implementation.
     */
    public class ResolverImpl implements InetAddressResolver {

        // Built-in resolver provider configuration
        private final Configuration configuration;

        /**
         * Resolver constructor
         *
         * @param configuration a built-in resolver related configuration
         */
        public ResolverImpl(Configuration configuration) {
            this.configuration = configuration;
        }

        /**
         * Given the name of a host, returns a stream of IP addresses of the requested
         * address family associated with a provided hostname.
         *
         * @param host         the specified hostname
         * @param lookupPolicy the address lookup policy
         * @return a stream of IP addresses for the requested host
         * @throws UnknownHostException if no IP address for the host could be found
         */
        @Override
        public Stream<InetAddress> lookupAddresses(String host, LookupPolicy lookupPolicy) throws UnknownHostException {
            try {
                System.out.printf("Using jdk.test.nsp.proof.jndi.ProviderImpl to lookup addresses for '%s' hostname.%n",
                        host);
                Stream<InetAddress> result = forwardLookup(host, lookupPolicy);
                return result;
            } catch (NamingException | InterruptedException | ExecutionException e) {
                System.err.println("Failed to use JNDI to lookup address (built-in NS will be used):" + e);
                return configuration.builtinResolver().lookupAddresses(host, lookupPolicy);
            }
        }

        /**
         * Lookup the host name corresponding to the raw IP address provided.
         *
         * @param addr byte array representing a raw IP address
         * @return String representing the host name mapping
         * @throws UnknownHostException if no host found for the specified IP address
         */
        @Override
        public String lookupHostName(byte[] addr) throws UnknownHostException {
            System.out.printf("Using jdk.test.nsp.proof.jndi.ProviderImpl to reverse " +
                    "lookup hostname for '%s' address%n", IPAddressUtil.bytesToString(addr));
            try {
                return reverseLookup(addr);
            } catch (ExecutionException | InterruptedException | UnknownHostException e) {
                System.err.println("Failed to use JNDI to lookup host name (built-in NS will be used):" + e);
                return configuration.builtinResolver().lookupHostName(addr);
            }
        }

        /*
         * Do a forward DNS lookup to resolve a host name to a stream of IP addresses
         */
        private Stream<InetAddress> forwardLookup(String hostName, LookupPolicy policy)
                throws NamingException, UnknownHostException, InterruptedException, ExecutionException {
            List<String> all = new ArrayList<>();
            CompletableFuture<List<String>> ipv4cf = CompletableFuture.completedFuture(List.of());
            CompletableFuture<List<String>> ipv6cf = CompletableFuture.completedFuture(List.of());

            if ((policy.characteristics() & IPV4) != 0) {
                ipv4cf = CompletableFuture.supplyAsync(() -> resolveOneTypeWithDirContext(hostName, "A"), EXECUTOR_SERVICE);
            }
            if ((policy.characteristics() & IPV6) != 0) {
                ipv6cf = CompletableFuture.supplyAsync(() -> resolveOneTypeWithDirContext(hostName, "AAAA"), EXECUTOR_SERVICE);
            }
            List<String> ipv4 = ipv4cf.get();
            List<String> ipv6 = ipv6cf.get();

            if ((policy.characteristics() & IPV4_FIRST) != 0) {
                all.addAll(ipv4);
                all.addAll(ipv6);
            } else if ((policy.characteristics() & IPV6_FIRST) != 0) {
                all.addAll(ipv6);
                all.addAll(ipv4);
            } else {
                all.addAll(ipv4);
                all.addAll(ipv6);
            }
            if (all.size() > 0) {
                return all.stream()
                        .map((sa) -> IPAddressUtil.toInetAddress(sa, hostName))
                        .filter(Objects::nonNull);
            } else {
                throw new UnknownHostException(hostName);
            }
        }

        /*
         * Do a reverse DNS lookup to resolve IP address byte array to a host name
         */
        private String reverseLookup(byte[] addr) throws UnknownHostException, ExecutionException, InterruptedException {
            CompletableFuture<List<String>> cfReverse;
            String reverseLookupName = IPAddressUtil.bytesToReverseLookupName(addr);
            cfReverse = CompletableFuture.supplyAsync(() -> resolveOneTypeWithDirContext(reverseLookupName, "PTR"), EXECUTOR_SERVICE);
            List<String> result = cfReverse.get();
            if (result.isEmpty()) {
                System.err.println("Failed to use JNDI to lookup hostname (built-in NS will be used):");
                throw new UnknownHostException("JNDI failed to lookup address");
            }
            String hostName = result.get(0);
            return hostName.endsWith(".") ? hostName.substring(0, hostName.length() - 1) : hostName;
        }

        // Do a DNS lookup for a provided address type - "A", "AAAA" or "PTR"
        private List<String> resolveOneTypeWithDirContext(String hostName, String addressType) {
            ArrayList<String> results = new ArrayList<>();
            Attributes attrs;
            try {
                DirContext dirContext = constructDirContext();
                attrs = dirContext.getAttributes(hostName, new String[]{addressType});
                NamingEnumeration<? extends Attribute> ne = attrs.getAll();
                while (ne.hasMoreElements()) {
                    Attribute attr = ne.next();
                    String attrId = attr.getID();
                    if (!addressType.equals(attrId)) {
                        continue;
                    }
                    for (NamingEnumeration<?> e = attr.getAll(); e.hasMoreElements(); ) {
                        results.add((String) e.next());
                    }
                }
            } catch (NamingException ne) {
                System.err.println("Error during resolution of \"" + hostName + "\" and type \"" + addressType + "\"");
                return List.of();
            }
            return results;
        }

        // Construct dir context for issuing DNS lookup requests
        private static DirContext constructDirContext() throws NamingException {
            Hashtable<String, Object> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("java.naming.provider.url", PROVIDERS_URL_STRING);
            return (DirContext) NamingManager.getInitialContext(env);
        }
    }

    // URL string with comma separated DNS provider addresses
    private static String systemPropertyValueToNameServersUrlString(String nsString) {
        Objects.requireNonNull(nsString, NAMESERVERS_SP_NAME + " system property is required");
        var nonBlank = Predicate.not(String::isBlank);
        String res = Arrays.stream(nsString.split(","))
                .filter(nonBlank)
                .map(ProviderImpl::mapToProviderUrlStrings)
                .filter(nonBlank)
                .collect(Collectors.joining(" "));
        if (res.isBlank()) {
            throw new RuntimeException("Invalid " + NAMESERVERS_SP_NAME + " SP value");
        }
        return res;
    }

    private static String mapToProviderUrlStrings(String s) {
        int colonPos = s.indexOf(':');
        int port = 53;
        String addressString = s;

        if (colonPos != -1) {
            int lastColonPos = s.indexOf(':');
            if (lastColonPos != colonPos) {
                throw new RuntimeException("Wrong '" + NAMESERVERS_SP_NAME + "' property value");
            }
            String[] addrParts = s.split(":");
            port = Integer.parseInt(addrParts[1]);
            addressString = addrParts[0].strip();
        }
        if (IPAddressUtil.isIPv4LiteralAddress(addressString)) {
            return "dns://" + addressString + ":" + port;
        } else if (IPAddressUtil.isIPv6LiteralAddress(addressString)) {
            return "dns://[" + addressString + ":" + port + "]";
        }
        return "";
    }

    // System property name with DNS server addresses
    private static final String NAMESERVERS_SP_NAME = "jdk.test.nsp.proof.nameservers";
    // Executor resolver threads name prefix
    private static final String THREAD_NAME_PREFIX = "JNDI-Based-Provider-Worker-";
    private static final AtomicLong NEXT_ID = new AtomicLong();
    // URL string with DNS provider addresses
    private static final String PROVIDERS_URL_STRING =
            systemPropertyValueToNameServersUrlString(System.getProperty(NAMESERVERS_SP_NAME));

    // Executor for IPv4/IPv6 addresses resolution requests
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(null, r, THREAD_NAME_PREFIX + NEXT_ID.getAndIncrement(),
                0, false);
        t.setDaemon(true);
        return t;
    });
}
