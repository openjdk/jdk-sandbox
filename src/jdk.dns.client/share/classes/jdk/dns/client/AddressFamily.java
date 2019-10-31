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

package jdk.dns.client;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;

public enum AddressFamily {
    IPv4,
    IPv6,
    ANY;

    public static AddressFamily fromProtocolFamily(ProtocolFamily protocolFamily) {
        if (protocolFamily == StandardProtocolFamily.INET) {
            return IPv4;
        } else if (protocolFamily == StandardProtocolFamily.INET6) {
            return IPv6;
        } else {
            return ANY;
        }

    }

    public boolean sameFamily(InetAddress inetAddress) {
        switch (this) {
            case IPv4:
                return inetAddress instanceof Inet4Address;
            case IPv6:
                return inetAddress instanceof Inet6Address;
            case ANY:
                return true;
            default:
                return false;
        }
    }

    public static AddressFamily fromInetAddress(InetAddress addr) {
        return addr instanceof Inet4Address ? IPv4 : addr instanceof Inet6Address ? IPv6 : ANY;
    }
}
