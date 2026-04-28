/*
 * Copyright (c) 2026, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic.tls;

/**
 * This enum contains a copy of the CipherSuite fields relevant to QUIC
 */
enum QuicCipherSuite {
    TLS_AES_256_GCM_SHA384("AES", 32, 12, "HKDF-SHA384", 48),
    TLS_AES_128_GCM_SHA256("AES", 16, 12, "HKDF-SHA256", 32),
    TLS_CHACHA20_POLY1305_SHA256("ChaCha20-Poly1305", 32, 12,"HKDF-SHA256", 32),
    ;

    /**
     * copy of SSLCipher#algotirhm
     */
    final String keyAlgorithm;
    /**
     * copy of SSLCipher#keySize
     */
    final int keySize;
    /**
     * copy of SSLCipher#ivSize
     */
    final int ivSize;
    /**
     * copy of HashAlg#hkdfAlgorithm
     */
    final String hkdfAlgorithm;
    /**
     * copy of HashAlg#hashLength
     */
    final int hashLength;

    QuicCipherSuite(String keyAlgorithm,
                    int keySize,
                    int ivSize,
                    String hkdfAlgorithm,
                    int hashLength) {
        this.keyAlgorithm = keyAlgorithm;
        this.keySize = keySize;
        this.ivSize = ivSize;
        this.hkdfAlgorithm = hkdfAlgorithm;
        this.hashLength = hashLength;
    }
}
