/*
 * Copyright (c) 2006, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package jnlp.converter.parser;

import java.util.Locale;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;

/**
 * Handy class to add some utility methods for dealing with property matching
 * etc.
 */
public class GeneralUtil {

    public static boolean prefixMatchStringList(String[] prefixList, String target) {
        // No prefixes matches everything
        if (prefixList == null) {
            return true;
        }
        // No target, but a prefix list does not match anything
        if (target == null) {
            return false;
        }
        for (String prefix : prefixList) {
            if (target.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private static String getOSArch() {
        return System.getProperty("os.arch");
    }

    public static boolean prefixMatchArch(String[] prefixList) {
        // No prefixes matches everything
        if (prefixList == null) {
            return true;
        }

        // check for the current arch
        String arch = getOSArch();
        for (String prefix : prefixList) {
            if (arch.startsWith(prefix)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Converts a space delimited string to a list of strings
     */
    public static String[] getStringList(String str) {
        if (str == null) {
            return null;
        }
        ArrayList<String> list = new ArrayList<>();
        int i = 0;
        int length = str.length();
        StringBuffer sb = null;
        while (i < length) {
            char ch = str.charAt(i);
            switch (ch) {
                case ' ':
                    // A space was hit. Add string to list
                    if (sb != null) {
                        list.add(sb.toString());
                        sb = null;
                    }
                    break;
                case '\\':
                    // It is a delimiter. Add next character
                    if (i + 1 < length) {
                        ch = str.charAt(++i);
                        if (sb == null) {
                            sb = new StringBuffer();
                        }
                        sb.append(ch);
                    }
                    break;
                default:
                    if (sb == null) {
                        sb = new StringBuffer();
                    }   sb.append(ch);
                    break;
            }
            i++; // Next character
        }
        // Make sure to add the last part to the list too
        if (sb != null) {
            list.add(sb.toString());
        }
        if (list.isEmpty()) {
            return null;
        }
        String[] results = new String[list.size()];
        return list.toArray(results);
    }

    /**
     * Checks if string list matches default locale
     */
    public static boolean matchLocale(String[] localeList, Locale locale) {
        // No locale specified matches everything
        if (localeList == null) {
            return true;
        }
        for (String localeList1 : localeList) {
            if (matchLocale(localeList1, locale)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks if string matches default locale
     */
    public static boolean matchLocale(String localeStr, Locale locale) {
        if (localeStr == null || localeStr.length() == 0) {
            return true;
        }

        // Compare against default locale
        String language;
        String country;
        String variant;

        // The locale string is of the form language_country_variant
        StringTokenizer st = new StringTokenizer(localeStr, "_", false);
        if (st.hasMoreElements() && locale.getLanguage().length() > 0) {
            language = st.nextToken();
            if (!language.equalsIgnoreCase(locale.getLanguage())) {
                return false;
            }
        }
        if (st.hasMoreElements() && locale.getCountry().length() > 0) {
            country = st.nextToken();
            if (!country.equalsIgnoreCase(locale.getCountry())) {
                return false;
            }
        }
        if (st.hasMoreElements() && locale.getVariant().length() > 0) {
            variant = st.nextToken();
            if (!variant.equalsIgnoreCase(locale.getVariant())) {
                return false;
            }
        }

        return true;
    }

    public static long heapValToLong(String heapValue) {
        if (heapValue == null) {
            return -1;
        }
        long multiplier = 1;
        if (heapValue.toLowerCase().lastIndexOf('m') != -1) {
            // units are megabytes, 1 megabyte = 1024 * 1024 bytes
            multiplier = 1024 * 1024;
            heapValue = heapValue.substring(0, heapValue.length() - 1);
        } else if (heapValue.toLowerCase().lastIndexOf('k') != -1) {
            // units are kilobytes, 1 kilobyte = 1024 bytes
            multiplier = 1024;
            heapValue = heapValue.substring(0, heapValue.length() - 1);
        }
        long theValue;
        try {
            theValue = Long.parseLong(heapValue);
            theValue = theValue * multiplier;
        } catch (NumberFormatException e) {
            theValue = -1;
        }
        return theValue;
    }

    public static byte[] readBytes(InputStream is, long size) throws IOException {
        // Sanity on file size (restrict to 1M)
        if (size > 1024 * 1024) {
            throw new IOException("File too large");
        }

        BufferedInputStream bis;
        if (is instanceof BufferedInputStream) {
            bis = (BufferedInputStream) is;
        } else {
            bis = new BufferedInputStream(is);
        }

        if (size <= 0) {
            size = 10 * 1024; // Default to 10K
        }
        byte[] b = new byte[(int) size];
        int n;
        int bytesRead = 0;
        n = bis.read(b, bytesRead, b.length - bytesRead);
        while (n != -1) {
            bytesRead += n;
            // Still room in array
            if (b.length == bytesRead) {
                byte[] bb = new byte[b.length * 2];
                System.arraycopy(b, 0, bb, 0, b.length);
                b = bb;
            }
            // Read next line
            n = bis.read(b, bytesRead, b.length - bytesRead);
        }
        bis.close();
        is.close();

        if (bytesRead != b.length) {
            byte[] bb = new byte[bytesRead];
            System.arraycopy(b, 0, bb, 0, bytesRead);
            b = bb;
        }
        return b;
    }

    public static String getOSFullName() {
        return System.getProperty("os.name");
    }

    /**
     * Makes sure a URL is a path URL, i.e., ends with '/'
     */
    public static URL asPathURL(URL url) {
        if (url == null) {
            return null;
        }

        String path = url.getFile();
        if (path != null && !path.endsWith("/")) {
            try {
                return new URL(url.getProtocol(),
                        url.getHost(),
                        url.getPort(),
                        url.getFile() + "/");
            } catch (MalformedURLException mue) {
                // Should not happen
            }
        }
        // Just return same URl
        return url;
    }

    public static Locale getDefaultLocale() {
        return Locale.getDefault();
    }

    public static String toNormalizedString(URL u) {
        if (u == null) {
            return "";
        }

        try {
            if (u.getPort() == u.getDefaultPort()) {
                u = new URL(u.getProtocol().toLowerCase(),
                        u.getHost().toLowerCase(), -1, u.getFile());
            } else {
                u = new URL(u.getProtocol().toLowerCase(),
                        u.getHost().toLowerCase(), u.getPort(), u.getFile());
            }
        } catch (MalformedURLException ex) {
        }
        return u.toExternalForm();
    }

    public static boolean sameURLs(URL u1, URL u2) {
        if (u1 == null || u2 == null || (u1 == u2)) {
            return (u1 == u2);
        }
        //NB: do not use URL.sameFile() as it will do DNS lookup
        // Also, do quick check before slow string comparisons
        String f1 = u1.getFile();
        String f2 = u2.getFile();
        return (f1.length() == f2.length()) && sameBase(u1, u2)
                && f1.equalsIgnoreCase(f2);
    }

    public static boolean sameBase(URL u1, URL u2) {
        return u1 != null && u2 != null &&
                sameHost(u1, u2) && samePort(u1, u2) && sameProtocol(u1, u2);
    }

    private static boolean sameProtocol(URL u1, URL u2) {
        //protocols are known to be lowercase
        return u1.getProtocol().equals(u2.getProtocol());
    }

    private static boolean sameHost(URL u1, URL u2) {
        String host = u1.getHost();
        String otherHost = u2.getHost();
        if (host == null || otherHost == null) {
            return (host == null && otherHost == null);
        } else {
            //avoid slow comparison for strings of different length
            return ((host.length() == otherHost.length())
                       && host.equalsIgnoreCase(otherHost));
        }
    }

    private static boolean samePort(URL u1, URL u2) {
        return getPort(u1) == getPort(u2);
    }

    public static int getPort(URL u) {
        if (u.getPort() != -1) {
            return u.getPort();
        } else {
            return u.getDefaultPort();
        }
    }

    public static URL getBase(URL url) {
        if (url == null) return null;
        String file = url.getFile();
        if (file != null) {
            int idx = file.lastIndexOf('/');
            if (idx != -1 ) {
                file = file.substring(0, idx + 1);
            }
            try {
                return new URL(
                    url.getProtocol(),
                    url.getHost(),
                    url.getPort(),
                    file);
            } catch(MalformedURLException mue) {
                System.err.println(mue.getMessage());
            }
        }
        // Just return same URL
        return url;
    }

    private static String getEmbeddedVersionPath(String path, String version) {
        int index = path.lastIndexOf("/");
        String filename = path.substring(index + 1);
        path = path.substring(0, index + 1);

        String ext = null;
        index = filename.lastIndexOf(".");
        if (index != -1) {
            ext = filename.substring(index + 1);
            filename = filename.substring(0, index);
        }

        StringBuilder filenameSB = new StringBuilder(filename);
        if (version != null) {
            filenameSB.append("__V");
            filenameSB.append(version);
        }
        if (ext != null) {
            filenameSB.append(".");
            filenameSB.append(ext);
        }

        path += filenameSB.toString();
        return path;
    }

    public static URL getEmbeddedVersionURL(URL u, String version) throws Exception {
        if (u == null) {
            return null;
        }

        if (version == null || version.indexOf("*") != -1
                || version.indexOf("+") != -1) {
            // Do not support * or + in version string
            return u;
        }

        URL versionURL = null;

        String protocol = u.getProtocol();
        String host = u.getHost();
        int port = u.getPort();
        String path = u.getPath();

        path = getEmbeddedVersionPath(path, version);

        versionURL = new URL(protocol, host, port, path);

        return versionURL;
    }
}
