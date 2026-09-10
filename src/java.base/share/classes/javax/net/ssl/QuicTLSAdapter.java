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

import jdk.internal.javac.PreviewFeature;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.util.Set;

/**
 * This interface is implemented by QUIC transport implementations
 * and is used by an {@link SSLEngine} instance
 * {@linkplain SSLContext#createSSLEngine(QuicTLSAdapter)
 * created for QUIC transport} to communicate with
 * the QUIC transport that uses this instance.
 * <P>
 * The set of cipher suites supported by the {@code SSLEngine} may be different
 * from the set supported by the QUIC transport. The {@code SSLEngine} calls
 * {@link #getSupportedCipherSuites()}, and then only negotiates cipher suites
 * that are both {@link SSLEngine#getEnabledCipherSuites() enabled}
 * and supported by QUIC.
 * <P>
 * Each QUIC endpoint offers QUIC transport parameters to the peer.
 * The {@code SSLEngine} calls {@link #localQuicTransportParameters()}
 * to retrieve the local transport parameters, and calls
 * {@link #remoteQuicTransportParameters(ByteBuffer)} to deliver the transport
 * parameters offered by the peer.
 * <P>
 * The {@code SSLEngine} delivers the traffic secrets by calling
 * {@link #deriveHandshakeReadSecrets(SecretKey, String)},
 * {@link #deriveHandshakeWriteSecrets(SecretKey, String)},
 * {@link #deriveApplicationReadSecrets(SecretKey, String)}, and
 * {@link #deriveApplicationWriteSecrets(SecretKey, String)} callbacks.
 * When application secrets are available in both directions,
 * the handshake is complete.
 * <P>
 * The {@code SSLEngine} produces the first application secret only.
 * Key updates are expected to be handled by the QUIC transport.
 * <P>
 * The {@code SSLEngine} reports handshake failures using the
 * {@link #cryptoError(int, Exception)} callback.
 * Failures are not recoverable.
 * <P>
 * The methods on this interface may be called by {@code SSLEngine wrap()},
 * {@code unwrap()}, {@code beginHandshake()}, or by delegated tasks.
 * The implementations must not call {@code wrap()}, {@code unwrap()},
 * or run any delegated tasks.
 *
 * @spec https://www.rfc-editor.org/info/rfc9001
 *      RFC 9001: Using TLS to Secure QUIC
 * @see SSLEngine
 * @since TBD
 */
@PreviewFeature(feature = PreviewFeature.Feature.QUIC_API)
public interface QuicTLSAdapter {
    /**
     * Called by the {@link SSLEngine} when the read secrets associated
     * with the Handshake key space are available.
     * <p>
     * The implementation should use the provided secret and cipher suite
     * to derive the packet decryption keys for the Handshake key space.
     * <p>
     * After the {@code SSLEngine} calls this method, subsequent calls to
     * {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)}
     * expect to receive CRYPTO data associated with the Handshake space.
     * If the QUIC transport implementation detects CRYPTO data
     * in the Initial space extending beyond what was already consumed
     * by {@code unwrap()}, it must close the associated QUIC connection
     * with a PROTOCOL_VIOLATION error.
     *
     * @param secretKey   the base secret used for deriving the handshake read keys
     * @param cipherSuite the selected cipher suite
     * @throws NullPointerException if any of the parameters is {@code null}
     * @throws IllegalArgumentException if the {@code cipherSuite}
     *          is not {@linkplain #getSupportedCipherSuites() supported}
     * @throws IllegalStateException if handshake or application read secrets
     *          have already been derived
     * @throws GeneralSecurityException if secret derivation fails
     */
    void deriveHandshakeReadSecrets(SecretKey secretKey, String cipherSuite)
            throws GeneralSecurityException;

    /**
     * Called by the {@link SSLEngine} when the write secrets associated
     * with the Handshake key space are available.
     * <p>
     * The implementation should use the provided secret and cipher suite
     * to derive the packet encryption keys for the Handshake key space.
     * <p>
     * This method may be called from {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)},
     * in which case the data produced by that call is the last chunk
     * to be delivered in the Initial key space.
     * Bytes produced by subsequent calls to {@code wrap} must be delivered
     * in the Handshake key space.
     *
     * @param secretKey   the base secret used for deriving the handshake write keys
     * @param cipherSuite the selected cipher suite
     * @throws NullPointerException if any of the parameters is {@code null}
     * @throws IllegalArgumentException if the {@code cipherSuite}
     *          is not {@linkplain #getSupportedCipherSuites() supported}
     * @throws IllegalStateException if handshake or application write secrets
     *          have already been derived
     * @throws GeneralSecurityException if secret derivation fails
     */
    void deriveHandshakeWriteSecrets(SecretKey secretKey, String cipherSuite)
            throws GeneralSecurityException;

    /**
     * Called by the {@link SSLEngine} when the read secrets associated
     * with the 1-RTT key space are available.
     * <p>
     * The implementation should use the provided secret and cipher suite
     * to derive the packet decryption keys for the 1-RTT key space.
     * <p>
     * After the {@code SSLEngine} calls this method, subsequent calls to
     * {@link SSLEngine#unwrap(ByteBuffer, ByteBuffer)}
     * expect to receive CRYPTO data associated with the 1-RTT space.
     * If the QUIC transport implementation detects CRYPTO data
     * in the Handshake space extending beyond what was already consumed
     * by {@code unwrap()}, it must close the associated QUIC connection
     * with a PROTOCOL_VIOLATION error.
     * <p>
     * The derived secrets must not be used
     * before the application write secrets are available.
     *
     * @param secretKey   the base secret used for deriving the 1-RTT read keys
     * @param cipherSuite the selected cipher suite
     * @throws NullPointerException if any of the parameters is {@code null}
     * @throws IllegalArgumentException if the {@code cipherSuite}
     *          is not {@linkplain #getSupportedCipherSuites() supported}
     * @throws IllegalStateException if application read secrets
     *          have already been derived
     * @throws GeneralSecurityException if secret derivation fails
     */
    void deriveApplicationReadSecrets(SecretKey secretKey, String cipherSuite)
            throws GeneralSecurityException;

    /**
     * Called by the {@link SSLEngine} when the write secrets associated
     * with the 1-RTT key space are available.
     * <p>
     * The implementation should use the provided secret and cipher suite
     * to derive the packet encryption keys for the 1-RTT key space.
     * <p>
     * This method may be called from {@link SSLEngine#wrap(ByteBuffer, ByteBuffer)},
     * in which case the data produced by that call is the last chunk
     * to be delivered in the Handshake key space.
     * Bytes produced by subsequent calls to {@code wrap} must be delivered
     * in the 1-RTT key space.
     *
     * @param secretKey   the base secret used for deriving the 1-RTT write keys
     * @param cipherSuite the selected cipher suite
     * @throws NullPointerException if any of the parameters is {@code null}
     * @throws IllegalArgumentException if the {@code cipherSuite}
     *          is not {@linkplain #getSupportedCipherSuites() supported}
     * @throws IllegalStateException if application write secrets
     *          have already been derived, or if remote transport parameters
     *          have not been {@linkplain #remoteQuicTransportParameters(ByteBuffer) received}
     * @throws GeneralSecurityException if secret derivation fails
     */
    void deriveApplicationWriteSecrets(SecretKey secretKey, String cipherSuite)
            throws GeneralSecurityException;

    /**
     * Called by the {@link SSLEngine} to retrieve the names
     * of the cipher suites supported by this QUIC transport.
     * <p>
     * Only the cipher suites supported by the QUIC transport
     * and enabled by the {@link SSLEngine#getEnabledCipherSuites() SSLEngine}
     * will be offered.
     * @return the names of the cipher suites supported by this QUIC transport
     */
    Set<String> getSupportedCipherSuites();

    /**
     * Called by the {@link SSLEngine} to retrieve the contents of the QUIC
     * transport parameters extension.
     * <P>
     * The returned buffer must not be modified until the calling method returns.
     * @return the QUIC transport parameters offered by this transport. Must not be null.
     */
    ByteBuffer localQuicTransportParameters();

    /**
     * Called by the {@link SSLEngine} when the remote QUIC
     * transport parameters extension is received.
     * <p>
     * If the parameters are invalid, the QUIC transport must close
     * the connection with the {@code TRANSPORT_PARAMETER_ERROR} error code.
     * After closing the connection, this method may throw an exception
     * to abort the handshake.
     * @param parameters a buffer containing the remote QUIC transport
     *                   parameters. This buffer is only valid for the duration
     *                   of this callback.
     * @throws IOException when parameters are invalid
     * @throws NullPointerException if {@code parameters} is null
     * @throws IllegalStateException if remote transport parameters
     *          have been received before
     */
    void remoteQuicTransportParameters(ByteBuffer parameters) throws IOException;

    /**
     * Called by the {@link SSLEngine} when the handshake fails
     * with a fatal alert.
     * <P>
     * Unless the connection has already been closed for other reasons,
     * the QUIC transport must convert the alert to a QUIC error code
     * by adding {@code 0x100} and send it in a {@code CONNECTION_CLOSE} frame
     * of type {@code 0x1c}.
     * @param code        the TLS alert code
     * @param closeReason the exception related to the alert, may be {@code null}
     * @throws IllegalArgumentException if code is not between 0 and 255 inclusive
     */
    void cryptoError(int code, Exception closeReason);
}
