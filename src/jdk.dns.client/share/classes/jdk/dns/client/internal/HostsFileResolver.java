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

import jdk.dns.client.internal.util.ReloadTracker;
import jdk.dns.conf.DnsResolverConfiguration;
import sun.net.util.IPAddressUtil;

import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class HostsFileResolver {
    private static final String HOSTS_FILE_LOCATION_PROPERTY_VALUE =
            AccessController.doPrivileged((PrivilegedAction<String>)
                    () -> System.getProperty("jdk.dns.client.hosts.file",
                            DnsResolverConfiguration.getDefaultHostsFileLocation())
            );
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    // 300 seconds, similar to DnsResolverConfiguration in millis since Epoch
    private static final long REFRESH_TIMEOUT_NANOS = 300_000_000_000L;
    private static final ReloadTracker HOSTS_FILE_TRACKER;
    private static volatile Map<String, HostFileEntry> HOST_ADDRESSES = Collections.emptyMap();

    void loadHostsAddresses() {
        LOCK.readLock().lock();
        var rsf = HOSTS_FILE_TRACKER.getReloadStatus();
        try {
            if (!rsf.isReloadNeeded()) {
                return;
            }
        } finally {
            LOCK.readLock().unlock();
        }

        LOCK.writeLock().lock();
        try {
            var rs = HOSTS_FILE_TRACKER.getReloadStatus();
            // Check if reload is still needed
            if (rs.isReloadNeeded()) {
                if (rs.isFileExists()) {
                    HOST_ADDRESSES = parseHostsFile();
                    HOSTS_FILE_TRACKER.updateTimestamps(rs);
                } else {
                    HOST_ADDRESSES = Collections.emptyMap();
                }
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    private static String removeComments(String hostsEntry) {
        String filteredEntry = hostsEntry;
        int hashIndex;

        if ((hashIndex = hostsEntry.indexOf("#")) != -1) {
            filteredEntry = hostsEntry.substring(0, hashIndex);
        }
        return filteredEntry;
    }

    private static class HostFileEntry {
        final List<String> names; // Might need to split into aliases and name
        final InetAddress address;
        final boolean isValid;
        final boolean isHostname;

        HostFileEntry(String[] data) {
            assert data.length > 1;
            List<String> ln = List.of(Arrays.copyOfRange(data, 1, data.length));
            String addressString = data[0];
            names = ln;
            address = parseAddress(ln.isEmpty() ? null : ln.get(0), addressString);
            isValid = address != null;
            isHostname = false;
        }

        @Override
        public String toString() {
            return names + "/" + address;
        }

        private HostFileEntry(String name, InetAddress address, boolean isHostname) {
            this.names = List.of(name);
            this.address = address;
            this.isValid = address != null;
            this.isHostname = isHostname;
        }

        boolean isValid() {
            return isValid;
        }

        boolean isHostname() {
            return isHostname;
        }

        String getHostName() {
            return names.get(0);
        }

        Stream<HostFileEntry> oneNameStream() {
            HostFileEntry hostName = new HostFileEntry(names.get(0), address, true);
            Stream<HostFileEntry> aliases = names.stream()
                    .skip(1)
                    .map(n -> new HostFileEntry(n, address, false));
            return Stream.concat(Stream.of(hostName), aliases);
        }

        private InetAddress parseAddress(String hostName, String addressString) {
            // TODO: Revisit
            Objects.requireNonNull(hostName);

            // IPAddressUtil is from
            var pa = (PrivilegedAction<byte[]>) () -> {
                if (IPAddressUtil.isIPv4LiteralAddress(addressString)) {
                    return IPAddressUtil.textToNumericFormatV4(addressString);
                } else if (IPAddressUtil.isIPv6LiteralAddress(addressString)) {
                    return IPAddressUtil.textToNumericFormatV6(addressString);
                }
                return null;
            };
            byte[] addr = System.getSecurityManager() == null ?
                    pa.run() : AccessController.doPrivileged(pa);

            if (addr != null) {
                try {
                    // if (hostName == null) hostName = addressString
                    return InetAddress.getByAddress(hostName, addr);
                } catch (UnknownHostException e) {
                }
            }
            return null;
        }
    }

    private Map<String, HostFileEntry> parseHostsFile() {
        Path hf = Paths.get(HOSTS_FILE_LOCATION_PROPERTY_VALUE);
        try {
            // TODO: Revisit
            var pea = (PrivilegedExceptionAction<Boolean>) () -> Files.isRegularFile(hf);
            boolean isRegularFile = System.getSecurityManager() == null ? pea.run()
                    : AccessController.doPrivileged(pea);

            if (isRegularFile) {
                var result = new HashMap<String, HostFileEntry>();
                var pea2 = (PrivilegedExceptionAction<List<String>>) () -> Files.readAllLines(hf, StandardCharsets.UTF_8);
                var lines = System.getSecurityManager() == null ? pea2.run()
                        : AccessController.doPrivileged(pea2);

                lines.stream()
                        .map(HostsFileResolver::removeComments)
                        .filter(Predicate.not(String::isBlank))
                        .map(s -> s.split("\\s+"))
                        .filter(a -> a.length > 1)
                        .map(HostFileEntry::new)
                        .filter(HostFileEntry::isValid)
                        .flatMap(HostFileEntry::oneNameStream)
                        .forEachOrdered(
                                // If the same host name is listed multiple times then
                                // use the first encountered line
                                hfe -> result.putIfAbsent(hfe.names.get(0), hfe)
                        );
                return Map.copyOf(result);
            }
        } catch (PrivilegedActionException pae) {
            throw new RuntimeException("Can't read hosts file", pae.getCause());
        } catch (Exception e) {
            throw new RuntimeException("Can't read hosts file", e);
        }
        return Collections.emptyMap();
    }

    public InetAddress getHostAddress(String hostName) throws UnknownHostException {
        return getHostAddress(hostName, null);
    }

    public InetAddress getHostAddress(String hostName, ProtocolFamily family) throws UnknownHostException {
        var af = AddressFamily.fromProtocolFamily(family);
        loadHostsAddresses();
        var map = HOST_ADDRESSES;
        var he = map.get(hostName);
        if (he == null) {
            throw new UnknownHostException(hostName);
        }
        var addr = he.address;
        if (!af.sameFamily(addr)) {
            throw new UnknownHostException(hostName);
        }
        return addr;
    }

    public String getByAddress(final InetAddress ha) throws UnknownHostException {
        loadHostsAddresses();
        var map = HOST_ADDRESSES;
        var entry = map.values().stream()
                .filter(HostFileEntry::isHostname)
                .filter(e -> isAddressBytesTheSame(ha.getAddress(), e.address.getAddress()))
                .findFirst();
        if (entry.isEmpty()) {
            throw new UnknownHostException(ha.toString());
        }
        return entry.get().getHostName();
    }

    private static boolean isAddressBytesTheSame(byte[] addr1, byte[] addr2) {
        if (addr1 == null || addr2 == null) {
            return false;
        }
        if (addr1.length != addr2.length) {
            return false;
        }
        for (int i = 0; i < addr1.length; i++) {
            if (addr1[i] != addr2[i])
                return false;
        }
        return true;
    }

    static {
        // TODO: Revisit
        try {
            var pea = (PrivilegedExceptionAction<ReloadTracker>) () ->
                    ReloadTracker.newInstance(Paths.get(HOSTS_FILE_LOCATION_PROPERTY_VALUE), REFRESH_TIMEOUT_NANOS);
            HOSTS_FILE_TRACKER = System.getSecurityManager() == null ? pea.run() :
                    AccessController.doPrivileged(pea);
        } catch (PrivilegedActionException pae) {
            throw new RuntimeException("Error registering hosts file watch service", pae.getCause());
        } catch (Exception e) {
            throw new RuntimeException("Error registering hosts file watch service", e);
        }
    }
}
