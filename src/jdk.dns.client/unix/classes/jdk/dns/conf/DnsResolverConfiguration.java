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

import jdk.dns.client.internal.util.ReloadTracker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedExceptionAction;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
 * An implementation of DnsResolverConfiguration for Solaris
 * and Linux.
 */

public class DnsResolverConfiguration {

    public static String getDefaultHostsFileLocation() {
        return "/etc/hosts";
    }

    // Lock held whilst loading configuration or checking
    private static final ReadWriteLock LOCK = new ReentrantReadWriteLock();

    //
    private static String RESOLV_CONF_LOCATION = java.security.AccessController.doPrivileged(
            (PrivilegedAction<String>) () -> System.getProperty("jdk.dns.client.resolv.conf", "/etc/resolv.conf"));
    private static final ReloadTracker RESOLVE_CONF_TRACKER;


    // Cache timeout (300 seconds) in nano seconds - should be converted into property
    // or configured as preference in the future.
    private static final long TIMEOUT = 300_000_000_000L;

    // Parse /etc/resolv.conf to get the values for a particular
    // keyword.
    //
    private List<String> resolvconf(String keyword,
                                    int maxperkeyword,
                                    int maxkeywords) {
        LinkedList<String> ll = new LinkedList<>();

        try {
            BufferedReader in =
                    new BufferedReader(new FileReader(RESOLV_CONF_LOCATION));
            String line;
            while ((line = in.readLine()) != null) {
                int maxvalues = maxperkeyword;
                if (line.isEmpty())
                    continue;
                if (line.charAt(0) == '#' || line.charAt(0) == ';')
                    continue;
                if (!line.startsWith(keyword))
                    continue;
                String value = line.substring(keyword.length());
                if (value.isEmpty())
                    continue;
                if (value.charAt(0) != ' ' && value.charAt(0) != '\t')
                    continue;
                StringTokenizer st = new StringTokenizer(value, " \t");
                while (st.hasMoreTokens()) {
                    String val = st.nextToken();
                    if (val.charAt(0) == '#' || val.charAt(0) == ';') {
                        break;
                    }
                    if ("nameserver".equals(keyword)) {
                        if (val.indexOf(':') >= 0 &&
                                val.indexOf('.') < 0 && // skip for IPv4 literals with port
                                val.indexOf('[') < 0 &&
                                val.indexOf(']') < 0) {
                            // IPv6 literal, in non-BSD-style.
                            val = "[" + val + "]";
                        }
                    }
                    ll.add(val);
                    if (--maxvalues == 0) {
                        break;
                    }
                }
                if (--maxkeywords == 0) {
                    break;
                }
            }
            in.close();
        } catch (IOException ioe) {
            // problem reading value
        }

        return Collections.unmodifiableList(ll);
    }

    private volatile List<String> searchlist = Collections.emptyList();
    private volatile List<String> nameservers = Collections.emptyList();
    private volatile String domain = "";


    // Load DNS configuration from OS
    private void loadConfig() {
        LOCK.readLock().lock();
        try {
            var rs = RESOLVE_CONF_TRACKER.getReloadStatus();
            if (!rs.isReloadNeeded() || !rs.isFileExists()) {
                return;
            }
        } finally {
            LOCK.readLock().unlock();
        }

        LOCK.writeLock().lock();
        try {
            var rs = RESOLVE_CONF_TRACKER.getReloadStatus();
            // Check if reload is needed again
            if (rs.isReloadNeeded()) {
                if (rs.isFileExists()) {
                    // get the name servers from /etc/resolv.conf
                    nameservers =
                            java.security.AccessController.doPrivileged(
                                    (PrivilegedAction<List<String>>) () -> {
                                        // typically MAXNS is 3 but we've picked 5 here
                                        // to allow for additional servers if required.
                                        return resolvconf("nameserver", 1, 5);
                                    });

                    // get the search list (or domain)
                    searchlist = getSearchList();

                    // update the timestamp on the configuration
                    RESOLVE_CONF_TRACKER.updateTimestamps(rs);
                } else {
                    nameservers = Collections.emptyList();
                    searchlist = Collections.emptyList();
                }
            }
        } finally {
            LOCK.writeLock().unlock();
        }
    }

    // obtain search list or local domain
    private List<String> getSearchList() {

        List<String> sl;

        // first try the search keyword in /etc/resolv.conf

        /* run */
        sl = java.security.AccessController.doPrivileged(
                (PrivilegedAction<List<String>>) () -> {
                    List<String> ll;

                    // first try search keyword (max 6 domains)
                    ll = resolvconf("search", 6, 1);
                    if (ll.size() > 0) {
                        domain = ll.get(0);
                        return ll;
                    }

                    return null;

                });
        if (sl != null) {
            return sl;
        }

        // No search keyword so use local domain


        // LOCALDOMAIN has absolute priority on Solaris

        String localDomain = localDomain0();
        if (localDomain != null && !localDomain.isEmpty()) {
            sl = new LinkedList<>();
            sl.add(localDomain);
            return Collections.unmodifiableList(sl);
        }

        // try domain keyword in /etc/resolv.conf

        /* run */
        sl = java.security.AccessController.doPrivileged(
                (PrivilegedAction<List<String>>) () -> {
                    List<String> ll;

                    ll = resolvconf("domain", 1, 1);
                    if (ll.size() > 0) {
                        domain = ll.get(0);
                        return ll;
                    }
                    return null;

                });
        if (sl != null) {
            // sl is already UnmodifiableList
            return sl;
        }

        // no local domain so try fallback (RPC) domain or
        // hostName

        sl = new LinkedList<>();
        String domain = fallbackDomain0();
        if (domain != null && !domain.isEmpty()) {
            sl.add(domain);
        }

        return Collections.unmodifiableList(sl);
    }


    // ----

    public DnsResolverConfiguration() {
    }

    @SuppressWarnings("unchecked")
    public List<String> searchlist() {
        loadConfig();
        // List is unmodifiable
        return searchlist;
    }

    @SuppressWarnings("unchecked")
    public List<String> nameservers() {
        loadConfig();
        // List is unmodifiable
        return nameservers;
    }

    public String domain() {
        loadConfig();
        return domain;
    }

    // --- Native methods --

    static native String localDomain0();

    static native String fallbackDomain0();

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
        var pea = (PrivilegedExceptionAction<ReloadTracker>) () ->
                ReloadTracker.newInstance(Paths.get(RESOLV_CONF_LOCATION), TIMEOUT);
        // TODO: Revisit
        try {
            RESOLVE_CONF_TRACKER = System.getSecurityManager() == null ? pea.run()
                    : AccessController.doPrivileged(pea);
        } catch (Exception e) {
            throw new RuntimeException("Can't instantiate resolver configuration file tracker", e);
        }
    }

}
