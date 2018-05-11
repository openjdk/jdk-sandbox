/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
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

package sun.security.ssl;

import java.io.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

/*
 * RandomCookie ... SSL hands standard format random cookies (nonces)
 * around.  These know how to encode/decode themselves on SSL streams,
 * and can be created and printed.
 *
 * @author David Brownell
 */
final class RandomCookie {
    final byte[] randomBytes = new byte[32];   // exactly 32 bytes

    private static final byte[] hrrRandomBytes = new byte[] {
            (byte)0xCF, (byte)0x21, (byte)0xAD, (byte)0x74,
            (byte)0xE5, (byte)0x9A, (byte)0x61, (byte)0x11,
            (byte)0xBE, (byte)0x1D, (byte)0x8C, (byte)0x02,
            (byte)0x1E, (byte)0x65, (byte)0xB8, (byte)0x91,
            (byte)0xC2, (byte)0xA2, (byte)0x11, (byte)0x16,
            (byte)0x7A, (byte)0xBB, (byte)0x8C, (byte)0x5E,
            (byte)0x07, (byte)0x9E, (byte)0x09, (byte)0xE2,
            (byte)0xC8, (byte)0xA8, (byte)0x33, (byte)0x9C
        };

    private static final byte[] t12Protection = new byte[] {
            (byte)0x44, (byte)0x4F, (byte)0x57, (byte)0x4E,
            (byte)0x47, (byte)0x52, (byte)0x44, (byte)0x01
        };

    private static final byte[] t11Protection = new byte[] {
            (byte)0x44, (byte)0x4F, (byte)0x57, (byte)0x4E,
            (byte)0x47, (byte)0x52, (byte)0x44, (byte)0x01
        };

    static final RandomCookie hrrRandom = new RandomCookie(hrrRandomBytes);

    RandomCookie(SecureRandom generator) {
        generator.nextBytes(randomBytes);
    }

    RandomCookie(ByteBuffer m) throws IOException {
        m.get(randomBytes);
    }

    private RandomCookie(byte[] randomBytes) {
        System.arraycopy(randomBytes, 0, this.randomBytes, 0, 32);
    }

    @Override
    public String toString() {
        return "random_bytes = {" + Utilities.toHexString(randomBytes) + "}";
    }

    boolean isHelloRetryRequest() {
        return Arrays.equals(hrrRandomBytes, randomBytes);
    }

    boolean isT12Downgrade() {
        return Arrays.equals(hrrRandomBytes, 24, 31, t12Protection, 0, 7);
    }

    boolean isT11Downgrade() {
        return Arrays.equals(hrrRandomBytes, 24, 31, t11Protection, 0, 7);
    }
}
