/*
 * Copyright (c) 2022, 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.internal.net.http.quic;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;

import javax.crypto.SecretKey;
import javax.net.ssl.QuicTLSAdapter;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;

import jdk.internal.net.http.quic.tls.QuicTLSEngineImpl;

/**
 * Instances of this class act as a factory for creation
 * of {@link QuicTLSEngine QUIC TLS engine}.
 */
public final class QuicTLSContext {

    private final SSLContext sslCtx;

    /**
     * {@return {@code true} if the given {@code sslContext} supports QUIC TLS, {@code false} otherwise}
     * @param sslContext an {@link SSLContext}
     */
    public static boolean isQuicCompatible(final SSLContext sslContext) {
        try {
            sslContext.createSSLEngine(null, -1, new QuicTLSAdapter() {
                @Override
                public void deriveHandshakeReadSecrets(SecretKey secretKey) {
                }
                @Override
                public void deriveHandshakeWriteSecrets(SecretKey secretKey) {
                }
                @Override
                public void deriveApplicationReadSecrets(SecretKey secretKey) {
                }
                @Override
                public void deriveApplicationWriteSecrets(SecretKey secretKey) {
                }
                @Override
                public String[] getSupportedCipherSuites() {
                    return new String[0];
                }
                @Override
                public ByteBuffer localQuicTransportParameters() {
                    return null;
                }
                @Override
                public void remoteQuicTransportParameters(ByteBuffer parameters) throws IOException {
                }
                @Override
                public void cryptoError(byte code, Exception closeReason) {
                }
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * {@return {@code true} if protocols of the given {@code parameters} support QUIC TLS, {@code false} otherwise}
     */
    private static boolean isQuicCompatible(SSLParameters parameters) {
        String[] protocols = parameters.getProtocols();
        return protocols != null && Arrays.asList(protocols).contains("TLSv1.3");
    }

    /**
     * Constructs a QuicTLSContext for the given {@code sslContext}
     *
     * @param sslContext The SSLContext
     * @throws IllegalArgumentException If the passed {@code sslContext} isn't
     *                                  supported by the QuicTLSContext
     * @see #isQuicCompatible(SSLContext)
     */
    public QuicTLSContext(final SSLContext sslContext) {
        Objects.requireNonNull(sslContext);
        if (!isQuicCompatible(sslContext)) {
            throw new IllegalArgumentException(
                    "Cannot construct a QUIC TLS context with the given SSLContext");
        }
        this.sslCtx = sslContext;
    }

    /**
     * Creates a {@link QuicTLSEngine} using this context
     * <p>
     * This method does not provide hints for session caching.
     *
     * @return the newly created QuicTLSEngine
     */
    public QuicTLSEngine createEngine() {
        return createEngine(null, -1);
    }

    /**
     * Creates a {@link QuicTLSEngine} using this context using
     * advisory peer information.
     * <p>
     * The provided parameters will be used as hints for session caching.
     * The {@code peerHost} parameter will be used in the server_name extension,
     * unless overridden later.
     *
     * @param peerHost The peer hostname or IP address. Can be null.
     * @param peerPort The peer port, can be -1 if the port is unknown
     * @return the newly created QuicTLSEngine
     */
    public QuicTLSEngine createEngine(final String peerHost, final int peerPort) {
        return new QuicTLSEngineImpl(this.sslCtx, peerHost, peerPort);
    }
}

