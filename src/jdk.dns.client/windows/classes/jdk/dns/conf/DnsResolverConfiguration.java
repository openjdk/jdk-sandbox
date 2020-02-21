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

package jdk.dns.conf;

import jdk.dns.client.internal.util.AddressArray;
import jdk.dns.client.internal.util.IPUtils;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class DnsResolverConfiguration {
    // Lock held whilst loading configuration or checking
    private static ReentrantLock lock = new ReentrantLock();

    // Addresses have changed
    private static boolean changed = false;

    // Time of last refresh
    private static long lastRefresh = -1;

    // Cache timeout (120 seconds in nanoseconds) - should be converted into property
    // or configured as preference in the future.
    private static final long TIMEOUT_NANOS = TimeUnit.SECONDS.toNanos(120);

    // DNS suffix list, name servers and default hostname populated by native method
    private static String os_searchlist;
    private static String os_nameservers;

    private static String os_hostname;
    private static volatile Set<AddressArray> knownLocalHostAddresses = Collections.emptySet();

    // Cached lists
    private static ArrayList<String> searchlist;
    private static ArrayList<String> nameservers;
    private volatile String domain = "";

    // Parse string that consists of token delimited by comma
    // and return ArrayList. Refer to ResolverConfigurationImpl.c and
    // strappend to see how the string is created.
    private ArrayList<String> stringToList(String str) {
        // String is delimited by comma.
        String[] tokens = str.split(" ");
        ArrayList<String> l = new ArrayList<>(tokens.length);
        for (String s : tokens) {
            if (!s.isEmpty() && !l.contains(s)) {
                l.add(s);
            }
        }
        l.trimToSize();
        return l;
    }

    // Parse string that consists of token delimited by comma
    // and return ArrayList.  Refer to ResolverConfigurationImpl.c and
    // strappend to see how the string is created.
    // In addition to splitting the string, converts IPv6 addresses to
    // BSD-style.
    private ArrayList<String> addressesToList(String str) {
        // String is delimited by comma
        String[] tokens = str.split(" ");
        ArrayList<String> l = new ArrayList<>(tokens.length);

        for (String s : tokens) {
            if (!s.isEmpty()) {
                if (s.indexOf(':') >= 0 && s.charAt(0) != '[') {
                    // Not BSD style
                    s = '[' + s + ']';
                }
                if (!s.isEmpty() && !l.contains(s)) {
                    l.add(s);
                }
            }
        }
        l.trimToSize();
        return l;
    }

    public static String getDefaultHostsFileLocation() {
        return Paths.get(System.getenv("SystemRoot"))
                .resolve("System32")
                .resolve("drivers")
                .resolve("etc")
                .resolve("hosts")
                .toString();
    }

    public List<byte[]> nativeLookup0(String hostName) {
        List<byte[]> resList = null;
        if (hostName != null && (hostName.equals(os_hostname) || hostName.equals("localhost"))) {
            String result = nativeLocalhostResolve0(hostName);
            resList = new ArrayList<>();
            for (var addressString : result.split(" ")) {
                if (!addressString.isBlank()) {
                    resList.add(
                            IPUtils.stringToAddressBytes(addressString)
                    );
                }
            }
        }
        return resList;
    }

    public String nativeReverseLookup0(byte[] address) {
        if (address == null) {
            return null;
        }

        if (knownLocalHostAddresses.isEmpty()) {
            nativeLocalhostResolve0(os_hostname);
        }
        if (knownLocalHostAddresses.contains(AddressArray.newAddressArray(address))) {
            return os_hostname;
        }
        return null;
    }

    // Perform caching only on Windows Platform - other platforms NO-OP
    public void cacheLocalHostAddresses(List<byte[]> addressesList) {
        knownLocalHostAddresses = addressesList.stream().map(AddressArray::newAddressArray)
                .collect(Collectors.toUnmodifiableSet());
    }

    // Load DNS configuration from OS

    private void loadConfig() {
        assert lock.isHeldByCurrentThread();

        // A change in the network address of the machine usually indicates
        // a change in DNS configuration too so we always refresh the config
        // after such a change.
        if (changed) {
            changed = false;
        } else {
            // Otherwise we refresh if TIMEOUT_NANOS has passed since last
            // load.
            long currTime = System.nanoTime();
            // lastRefresh will always have been set once because we start with
            // changed = true.
            if ((currTime - lastRefresh) < TIMEOUT_NANOS) {
                return;
            }
        }


        // Native code that uses Windows API to find out the DNS server
        // addresses and search suffixes. It builds a comma-delimited string
        // of nameservers and domain suffixes and sets them to the static
        // os_nameservers and os_searchlist. We then split these into Java
        // Lists here.
        loadDNSconfig0();

        // Record the time of update and refresh the lists of addresses /
        // domain suffixes.
        lastRefresh = System.nanoTime();
        searchlist = stringToList(os_searchlist);
        if (searchlist.size() > 0) {
            domain = searchlist.get(0);
        } else {
            domain = "";
        }
        nameservers = addressesToList(os_nameservers);
        os_searchlist = null;                       // can be GC'ed
        os_nameservers = null;
    }

    public DnsResolverConfiguration() {
    }

    @SuppressWarnings("unchecked") // clone()
    public List<String> searchlist() {
        lock.lock();
        try {
            loadConfig();

            // List is mutable so return a shallow copy
            return (List<String>) searchlist.clone();
        } finally {
            lock.unlock();
        }
    }

    @SuppressWarnings("unchecked") // clone()
    public List<String> nameservers() {
        lock.lock();
        try {
            loadConfig();

            // List is mutable so return a shallow copy
            return (List<String>) nameservers.clone();
        } finally {
            lock.unlock();
        }
    }

    public String domain() {
        lock.lock();
        try {
            loadConfig();
            return domain;
        } finally {
            lock.unlock();
        }
    }

    // --- Address Change Listener

    static class AddressChangeListener extends Thread {
        public void run() {
            for (; ; ) {
                // wait for configuration to change
                if (notifyAddrChange0() != 0)
                    return;
                lock.lock();
                try {
                    changed = true;
                } finally {
                    lock.unlock();
                }
            }
        }
    }


    // --- Native methods --

    static native void init0();

    static native void loadDNSconfig0();

    static native int notifyAddrChange0();

    static native String nativeLocalhostResolve0(String localHostName);

    static {
        System.loadLibrary("resolver");
        init0();

        // start the address listener thread
        AddressChangeListener thr = new AddressChangeListener();
        thr.setDaemon(true);
        thr.start();
    }
}
