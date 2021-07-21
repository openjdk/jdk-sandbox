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

import jdk.test.nsp.proof.netty.IPAddressUtil;

import java.net.InetAddress;
import java.util.Arrays;

public class TestProvider {
    public static void main(String[] args) throws Exception {
        String hostToResolve = args.length > 0 ? args[0] : "github.com";
        String resultsArray =
                Arrays.deepToString(InetAddress.getAllByName(hostToResolve));
        System.out.printf("'%s' addresses:%n%s%n", hostToResolve, resultsArray);

        // Reverse IPv4 DNS lookup test
        byte[] addressBytes = new byte[]{(byte) 192, 0, 43, 7};
        System.out.printf("'%s' v4 address is resolved to '%s' host name %n",
                IPAddressUtil.bytesToString(addressBytes),
                InetAddress.getByAddress(addressBytes).getCanonicalHostName()
        );

        // IPv6 address
        byte[] ipv6addressBytes = InetAddress.getByName("[2a03:2880:f132:83:face:b00c:0:25de]").getAddress();

        System.out.printf("'%s' v6 address is resolved to '%s' host name %n",
                IPAddressUtil.bytesToString(ipv6addressBytes),
                InetAddress.getByAddress(ipv6addressBytes).getCanonicalHostName()
        );
    }
}
