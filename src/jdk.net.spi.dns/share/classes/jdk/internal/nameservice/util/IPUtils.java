/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

import sun.net.util.IPAddressUtil;

public class IPUtils {

    public static String addressToLiteralIP(byte[] bytes) {
        StringBuilder addressBuff = new StringBuilder();
        // IPv4 address
        if (bytes.length == 4) {
            for (int i = bytes.length - 1; i >= 0; i--) {
                addressBuff.append(bytes[i] & 0xff);
                addressBuff.append(".");
            }
            // IPv6 address
        } else if (bytes.length == 16) {
            for (int i = bytes.length - 1; i >= 0; i--) {
                addressBuff.append(Integer.toHexString((bytes[i] & 0x0f)));
                addressBuff.append(".");
                addressBuff.append(Integer.toHexString((bytes[i] & 0xf0) >> 4));
                addressBuff.append(".");
            }
        } else {
            return null;
        }
        return addressBuff.toString();
    }

    public static byte[] stringToAddressBytes(String strAddress) {
        if (strAddress.contains(":")) {
            return IPAddressUtil.textToNumericFormatV6(strAddress);
        } else {
            return IPAddressUtil.textToNumericFormatV4(strAddress);
        }
    }
}
