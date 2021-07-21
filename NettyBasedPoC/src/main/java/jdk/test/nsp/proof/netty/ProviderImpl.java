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

import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DefaultDnsPtrRecord;
import io.netty.handler.codec.dns.DefaultDnsQuestion;
import io.netty.handler.codec.dns.DnsRecord;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.resolver.ResolvedAddressTypes;
import io.netty.resolver.dns.DnsNameResolver;
import io.netty.resolver.dns.DnsNameResolverBuilder;
import io.netty.resolver.dns.DnsServerAddressStreamProviders;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetNameService;
import java.net.spi.InetNameServiceProvider;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static java.net.spi.InetNameService.LookupPolicy.IPV4;
import static java.net.spi.InetNameService.LookupPolicy.IPV4_FIRST;
import static java.net.spi.InetNameService.LookupPolicy.IPV6;
import static java.net.spi.InetNameService.LookupPolicy.IPV6_FIRST;

/**
 * This is a proof-of-concept name service provider implementation that exercises new
 * {@code java.net.spi.InetNameServiceProvider} API to implement an experimental
 * name service based on <a href="https://netty.io/">Netty library</a>.
 */
public class ProviderImpl extends InetNameServiceProvider {

    /**
     * Create instance of {@code NameServiceImpl}.
     *
     * @param configuration platform built-in address resolution configuration
     * @return name service instance
     */
    @Override
    public InetNameService get(Configuration configuration) {
        var ns = new NameServiceImpl(configuration);
        System.out.println("Returning instance of jdk.test.nsp.proof.netty.ProviderImpl NS:" + ns);
        return ns;
    }

    /**
     * Returns the name of this provider.
     *
     * @return the name service provider name
     */
    @Override
    public String name() {
        return "jdk.test.nsp.proof.netty.ProviderImpl";
    }

    /**
     * Netty library based experimental name service implementation.
     */
    public static class NameServiceImpl implements InetNameService {

        // Built-in name service provider configuration
        private final Configuration configuration;

        /**
         * Name service constructor
         *
         * @param configuration a built-in name service related configuration
         */
        public NameServiceImpl(Configuration configuration) {
            this.configuration = configuration;
            this.customDnsResolvers = newCustomDnsResolvers();
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
                System.out.printf("Using jdk.test.nsp.proof.netty.ProviderImpl to lookup addresses for '%s' hostname.%n",
                        host);
                // Map LookupPolicy value to a DNS resolver
                DnsNameResolver mappedResolver = mapLookupPolicyToResolver(lookupPolicy);
                return forwardLookup(host, mappedResolver);
            } catch (InterruptedException | ExecutionException | UnknownHostException e) {
                System.err.println("Custom DNS resolver failed to lookup addresses. Continuing with the built-in name service.");
                return configuration.builtinNameService().lookupAddresses(host, lookupPolicy);
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
            System.out.printf("Using jdk.test.nsp.proof.netty.ProviderImpl to reverse " +
                    "lookup hostname for '%s' address%n", IPAddressUtil.bytesToString(addr));
            try {
                return reverseLookup(IPAddressUtil.bytesToReverseLookupName(addr),
                        mapLookupPolicyToResolver(LookupPolicy.of(IPV4 | IPV6)));
            } catch (InterruptedException | ExecutionException | UnknownHostException e) {
                System.err.println("Custom DNS resolver failed to lookup a hostname for the provided address. " +
                        "Continuing with the built-in name service.");
                return configuration.builtinNameService().lookupHostName(addr);
            }
        }

        /*
         * Do a forward DNS lookup to resolve a host name to a stream of IP addresses
         */
        private Stream<InetAddress> forwardLookup(String host, DnsNameResolver resolver)
                throws ExecutionException, InterruptedException {
            return resolver.resolveAll(host)
                    .syncUninterruptibly()
                    .get()
                    .stream();
        }

        /*
         * Do a reverse DNS lookup to resolve IP address byte array to a host name
         */
        private String reverseLookup(String reverseLookupName, DnsNameResolver resolver) throws InterruptedException, ExecutionException, UnknownHostException {
            var q = new DefaultDnsQuestion(reverseLookupName, DnsRecordType.PTR, DnsRecord.CLASS_IN);
            List<DnsRecord> records = resolver.resolveAll(q).syncUninterruptibly().get();
            for (DnsRecord r : records) {
                if (r instanceof DefaultDnsPtrRecord record) {
                    String n = record.hostname();
                    return n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
                }
            }
            throw new UnknownHostException();
        }

        /**
         * Create DNS resolvers map. It maps address type and order related characteristics
         * to a {@code DnsNameResolver}.
         *
         * @return mapping with lookup characteristic to DNS name resolver.
         */
        private Map<Integer, DnsNameResolver> newCustomDnsResolvers() {
            EventLoopGroup eventGrp = new NioEventLoopGroup(1, new ThreadFactory() {
                public static final String PREFIX = "NettyProviderImpl-EventLoopGroup-";
                private final AtomicInteger nextId = new AtomicInteger();

                @Override
                public Thread newThread(Runnable r) {
                    String name = PREFIX + nextId.getAndIncrement();
                    Thread t = new Thread(null, r, name, 0, false);
                    t.setDaemon(true);
                    return t;
                }
            });
            DnsNameResolverBuilder systemResolverBuilder = new DnsNameResolverBuilder(eventGrp.next())
                    .channelType(NioDatagramChannel.class)
                    .nameServerProvider(DnsServerAddressStreamProviders.platformDefault())
                    .maxQueriesPerResolve(2)
                    .optResourceEnabled(false)
                    .ndots(1);

            // Resolver for case when preferIPv6Addresses value is system
            DnsNameResolver systemResolver = systemResolverBuilder.build();

            // Resolver for case when preferIPv6Addresses value is true
            DnsNameResolver ipv6firstResolver = systemResolverBuilder.copy()
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV6_PREFERRED)
                    .build();

            // Resolver for case when no values for preferIPv6Addresses
            // and preferIPv4Stack is set
            DnsNameResolver ipv4firstResolver = systemResolverBuilder.copy()
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_PREFERRED)
                    .build();

            // Resolver for case when ipv6 addresses are not supported by the Java platform
            DnsNameResolver ipv4onlyResolver = systemResolverBuilder.copy()
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV4_ONLY)
                    .build();

            // Resolver for case when ipv4 addresses are not supported by the Java platform
            DnsNameResolver ipv6onlyResolver = systemResolverBuilder.copy()
                    .resolvedAddressTypes(ResolvedAddressTypes.IPV6_ONLY)
                    .build();

            // Construct a map with characteristic to resolvers mapping
            return Map.of(IPV4, ipv4onlyResolver,
                    IPV4 | IPV6 | IPV4_FIRST, ipv4firstResolver,
                    IPV6, ipv6onlyResolver,
                    IPV4 | IPV6 | IPV6_FIRST, ipv6firstResolver,
                    IPV4 | IPV6, systemResolver);
        }

        /**
         * Returns a {@code "DnsNameResolver"} suitable for a provided lookup policy.
         *
         * @param lookupPolicy lookup policy
         * @return a {@code "DnsNameResolver"} resolver
         * @throws UnknownHostException if no resolver found for the provided lookup policy
         */
        private DnsNameResolver mapLookupPolicyToResolver(LookupPolicy lookupPolicy) throws UnknownHostException {
            int orderRelatedCharacteristics = lookupPolicy.characteristics() & TYPE_AND_ORDER_FLAGS_MASK;
            DnsNameResolver resolver = customDnsResolvers.get(orderRelatedCharacteristics);
            if (resolver == null) {
                throw new UnknownHostException("Can't find resolver for the provided lookup policy");
            }
            return resolver;
        }

        // Address type to DNS name resolvers map
        private final Map<Integer, DnsNameResolver> customDnsResolvers;

        // Mask to extract addresses type and order from characteristics value
        private static final int TYPE_AND_ORDER_FLAGS_MASK = IPV4 | IPV6 | IPV4_FIRST | IPV6_FIRST;
    }
}
