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
package javax.net.ssl;

import javax.crypto.SecretKey;
import java.nio.ByteBuffer;

/**
 * TODO add description
 *
 * @since 27
 */
public interface QuicTransport {
    /**
     * Represents the encryption level associated with a packet.
     */
    enum KeySpace {
        /**
         * The initial key space
         */
        INITIAL,
        /**
         * The handshake key space
         */
        HANDSHAKE,
        /**
         * The 0-RTT key space
         */
        ZERO_RTT,
        /**
         * The 1-RTT key space
         */
        ONE_RTT
    }

    /**
     * Called when the read secrets associated
     * with the Handshake key space are available.
     * <p>
     * The provided secret should be used with the key derivation function
     * defined for the active cipher suite. The cipher suite can be retrieved from the
     * {@link SSLEngine#getHandshakeSession() handshake session}
     * using {@link SSLSession#getCipherSuite()} method.
     * <p>
     * After this method is called, subsequent calls to {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)}
     * expect to receive CRYPTO data associated with the Handshake space.
     * If additional CRYPTO data is received in the Initial space,
     * the connection must be closed with PROTOCOL_VIOLATION error.
     *
     * @param secretKey the base secret used for deriving the handshake read keys
     */
    // TODO merge with deriveHandshakeWriteSecrets, since both are derived at the same time
    void deriveHandshakeReadSecrets(SecretKey secretKey);

    /**
     * Called when the write secrets associated
     * with the Handshake key space are available.
     * <p>
     * The provided secret should be used with the key derivation function
     * defined for the active cipher suite. The cipher suite can be retrieved from the
     * {@link SSLEngine#getHandshakeSession() handshake session}
     * using {@link SSLSession#getCipherSuite()} method.
     * <p>
     * If this method is called from {@code wrap},
     * the data produced by that call is associated with the Initial key space.
     * Subsequent calls to {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)}
     * produce data for the Handshake key space.
     *
     * @param secretKey the base secret used for deriving the handshake write keys
     */
    void deriveHandshakeWriteSecrets(SecretKey secretKey);

    /**
     * Called when the read secrets associated
     * with the 1-RTT key space are available.
     * <p>
     * The provided secret should be used with the key derivation function
     * defined for the active cipher suite. This is the same cipher suite
     * that was used with {@link #deriveHandshakeReadSecrets(SecretKey)} earlier.
     * Note that {@link SSLEngine#getHandshakeSession()} might no longer be available at this point.
     * Applications should cache the cipher suite value retrieved earlier, or fall back to
     * {@link SSLEngine#getSession()} if handshake session is {@code null}.
     * <p>
     * After this method is called, subsequent calls to {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)}
     * expect to receive CRYPTO data associated with the 1-RTT space.
     * If additional CRYPTO data is received in the Handshake space,
     * the connection must be closed with PROTOCOL_VIOLATION error.
     * <p>
     * The derived secrets must not be used
     * before the application write secrets are available.
     *
     * @param secretKey the base secret used for deriving the 1-RTT read keys
     */
    void deriveApplicationReadSecrets(SecretKey secretKey);

    /**
     * Called when the write secrets associated
     * with the 1-RTT key space are available.
     * <p>
     * The provided secret should be used with the key derivation function
     * defined for the active cipher suite. This is the same cipher suite
     * that was used with {@link #deriveHandshakeReadSecrets(SecretKey)} earlier.
     * Note that {@link SSLEngine#getHandshakeSession()} might no longer be available at this point.
     * Applications should cache the cipher suite value retrieved earlier, or fall back to
     * {@link SSLEngine#getSession()} if handshake session is {@code null}.
     * <p>
     * If this method is called from {@code wrap},
     * the data produced by that call is associated with the Handshake key space.
     * Subsequent calls to {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)}
     * produce data for the 1-RTT key space.
     *
     * @param secretKey the base secret used for deriving the 1-RTT write keys
     */
    void deriveApplicationWriteSecrets(SecretKey secretKey);
}
