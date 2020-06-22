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

package jdk.internal.nameservice.util;

import jdk.internal.nameservice.dns.ResourceRecord;

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
        if (protocolFamily == null) {
            return ANY;
        } else if (protocolFamily == StandardProtocolFamily.INET) {
            return IPv4;
        } else if (protocolFamily == StandardProtocolFamily.INET6) {
            return IPv6;
        } else {
            return ANY;
        }
    }

    public boolean sameFamily(InetAddress inetAddress) {
        return switch (this) {
            case IPv4 -> inetAddress instanceof Inet4Address;
            case IPv6 -> inetAddress instanceof Inet6Address;
            case ANY -> true;
        };
    }

    public boolean matchesResourceRecord(ResourceRecord rr) {
        int type = rr.getType();
        return switch (this) {
            case IPv4 -> type == ResourceRecord.TYPE_A;
            case IPv6 -> type == ResourceRecord.TYPE_AAAA;
            case ANY -> type == ResourceRecord.TYPE_A ||
                    type == ResourceRecord.TYPE_AAAA;
        };
    }

    public static AddressFamily fromResourceRecord(ResourceRecord rr) {
        return rr.getType() == ResourceRecord.TYPE_AAAA ? IPv6 : IPv4;
    }

    public static AddressFamily fromByteArray(byte[] bytes) {
        return switch (bytes.length) {
            case 16 -> IPv6;
            case 4 -> IPv4;
            default -> ANY;
        };
    }
}
