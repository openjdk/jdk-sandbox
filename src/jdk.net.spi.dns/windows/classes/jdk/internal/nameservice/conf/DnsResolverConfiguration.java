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

package jdk.internal.nameservice.conf;

import jdk.internal.nameservice.util.AddressArray;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetNameServiceProvider.NameService;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class DnsResolverConfiguration {

    private static volatile Set<AddressArray> knownLocalHostAddresses = Collections.emptySet();

    public static String getDefaultHostsFileLocation() {
        return Paths.get(System.getenv("SystemRoot"))
                .resolve("System32")
                .resolve("drivers")
                .resolve("etc")
                .resolve("hosts")
                .toString();
    }

    public Stream<InetAddress> nativeLookup0(String hostName, NameService defaultPlatformNS) {
        if (hostName != null && (LOCAL_HOSTNAME.equals(hostName) || hostName.equals("localhost"))) {
            try {
                var addresses = defaultPlatformNS.lookupInetAddresses(hostName, null)
                        .collect(Collectors.toList());
                cacheLocalHostAddresses(addresses);
                return addresses.stream();
            } catch (UnknownHostException e) {
                return null;
            }
        }
        return null;
    }

    public String nativeReverseLookup0(byte[] address, NameService defaultPlatformNS) {
        if (address == null || address.length < 4) {
            return null;
        }
        // If local host name was never resolved before - do that
        if (knownLocalHostAddresses.isEmpty()) {
            nativeLookup0(LOCAL_HOSTNAME, defaultPlatformNS);
        }
        if (knownLocalHostAddresses.contains(AddressArray.newAddressArray(address))) {
            return LOCAL_HOSTNAME;
        }
        return null;
    }

    // Perform caching only on Windows Platform - other platforms NO-OP
    private void cacheLocalHostAddresses(List<InetAddress> addressesList) {
        knownLocalHostAddresses = addressesList.stream()
                .map(InetAddress::getAddress)
                .map(AddressArray::newAddressArray)
                .collect(Collectors.toUnmodifiableSet());
    }

    public DnsResolverConfiguration() {
    }

    // Alternative is to call hostname cmd tool:
    // ProcessBuilder pb = new ProcessBuilder("cmd.exe", "/c", "hostname");
    private static native String getLocalHostNameNative();

    static {
        var pa = (PrivilegedAction<Void>) () -> {
            System.loadLibrary("resolver");
            return null;
        };
        if (System.getSecurityManager() == null) {
            pa.run();
        } else {
            AccessController.doPrivileged(pa);
        }
    }

    // Caching of local host name is needed to resolve the following inconsistency in Windows:
    // %COMPUTERNAME% environment variable is in uppercase,
    // but hostname can be in any case.
    // Therefore getCanonicalName results will differ from original local hostname
    private static final String LOCAL_HOSTNAME = getLocalHostNameNative();
}
