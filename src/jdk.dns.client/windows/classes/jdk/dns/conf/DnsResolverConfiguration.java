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

package jdk.dns.conf;

import java.nio.file.Paths;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.locks.ReentrantLock;

public class DnsResolverConfiguration {
    // Lock held whilst loading configuration or checking
    private static ReentrantLock lock = new ReentrantLock();

    // Addresses have changed
    private static boolean changed = false;

    // Time of last refresh.
    private static long lastRefresh = -1;

    // Cache timeout (120 seconds in nanoseconds) - should be converted into property
    // or configured as preference in the future.
    private static final long TIMEOUT = 120_000_000_000L;

    // DNS suffix list and name servers populated by native method
    private static String os_searchlist;
    private static String os_nameservers;

    // Cached lists
    private static LinkedList<String> searchlist;
    private static LinkedList<String> nameservers;
    private volatile String domain = "";

    // Parse string that consists of token delimited by space or commas
    // and return LinkedHashMap
    private LinkedList<String> stringToList(String str) {
        LinkedList<String> ll = new LinkedList<>();

        // comma and space are valid delimiters
        StringTokenizer st = new StringTokenizer(str, ", ");
        while (st.hasMoreTokens()) {
            String s = st.nextToken();
            if (!ll.contains(s)) {
                ll.add(s);
            }
        }
        return ll;
    }

    public static String getDefaultHostsFileLocation() {
        return Paths.get(System.getenv("SystemRoot"))
                .resolve("System32")
                .resolve("drivers")
                .resolve("etc")
                .resolve("hosts")
                .toString();
    }

    // Load DNS configuration from OS

    private void loadConfig() {
        assert lock.isHeldByCurrentThread();

        // if address have changed then DNS probably changed as well;
        // otherwise check if cached settings have expired.
        //
        if (changed) {
            changed = false;
        } else {
            if (lastRefresh >= 0) {
                long currTime = System.nanoTime();
                if ((currTime - lastRefresh) < TIMEOUT) {
                    return;
                }
            }
        }

        // load DNS configuration, update timestamp, create
        // new HashMaps from the loaded configuration
        //
        loadDNSconfig0();

        lastRefresh = System.nanoTime();
        searchlist = stringToList(os_searchlist);
        if (searchlist.size() > 0) {
            domain = searchlist.get(0);
        } else {
            domain = "";
        }
        nameservers = stringToList(os_nameservers);
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

    static {
        System.loadLibrary("resolver");
        init0();

        // start the address listener thread
        AddressChangeListener thr = new AddressChangeListener();
        thr.setDaemon(true);
        thr.start();
    }
}
