/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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

import jdk.internal.net.quic.QuicKeyUnavailableException;
import jdk.internal.net.quic.QuicOneRttContext;
import jdk.internal.net.quic.QuicTLSEngine;
import jdk.internal.net.quic.QuicTransportException;
import jdk.internal.net.quic.QuicTransportParametersConsumer;
import jdk.internal.net.quic.QuicVersion;
import sun.security.ssl.QuicKeyManager.HandshakeKeyManager;
import sun.security.ssl.QuicKeyManager.InitialKeyManager;
import sun.security.ssl.QuicKeyManager.OneRttKeyManager;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.net.ssl.QuicTransport;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.security.AlgorithmConstraints;
import java.security.GeneralSecurityException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.IntFunction;

import static javax.net.ssl.QuicTransport.KeySpace.*;

/**
 * One instance per QUIC connection. Configuration methods similar to
 * SSLEngine.
 * <p>
 * The implementation of this class uses the {@link QuicKeyManager} to maintain
 * all state relating to keys for each encryption levels.
 */
public final class QuicTLSEngineImpl implements QuicTLSEngine, SSLTransport, QuicTransport {

    static final long BASE_CRYPTO_ERROR = 256;

    private static final Set<QuicVersion> SUPPORTED_QUIC_VERSIONS =
            Set.of(QuicVersion.QUIC_V1, QuicVersion.QUIC_V2);

    // VarHandles are used to access compareAndSet semantics.
    private static final VarHandle HANDSHAKE_STATE_HANDLE;
    static {
        final MethodHandles.Lookup lookup = MethodHandles.lookup();
        try {
            final Class<?> quicTlsEngineImpl = QuicTLSEngineImpl.class;
            HANDSHAKE_STATE_HANDLE = lookup.findVarHandle(
                    quicTlsEngineImpl,
                    "handshakeStatus",
                    SSLEngineResult.HandshakeStatus.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final TransportContext conContext;
    private final String peerHost;
    private final int peerPort;
    private volatile SSLEngineResult.HandshakeStatus handshakeStatus;
    private volatile QuicTransport.KeySpace recvKeySpace;
    private volatile QuicTransport.KeySpace sendKeySpace;
    // next message to send or receive
    private volatile ByteBuffer localQuicTransportParameters;
    private volatile QuicTransportParametersConsumer
            remoteQuicTransportParametersConsumer;

    // keymanagers for individual keyspaces
    private final InitialKeyManager initialKeyManager = new InitialKeyManager();
    private final HandshakeKeyManager handshakeKeyManager =
            new HandshakeKeyManager();
    private final OneRttKeyManager oneRttKeyManager = new OneRttKeyManager();
    private String cipherSuite;

    private volatile QuicVersion negotiatedVersion;

    public QuicTLSEngineImpl(SSLContextImpl sslContextImpl) {
        this(sslContextImpl, null, -1);
    }

    public QuicTLSEngineImpl(SSLContextImpl sslContextImpl, final String peerHost, final int peerPort) {
        this.peerHost = peerHost;
        this.peerPort = peerPort;
        this.sendKeySpace = INITIAL;
        this.recvKeySpace = INITIAL;
        HandshakeHash handshakeHash = new HandshakeHash();
        this.conContext = new TransportContext(sslContextImpl, this,
                new QuicEngineInputRecord(handshakeHash, this),
                new QuicEngineOutputRecord(handshakeHash, this));
        conContext.sslConfig.enabledProtocols = List.of(ProtocolVersion.TLS13);
        if (peerHost != null) {
            conContext.sslConfig.serverNames =
                    Utilities.addToSNIServerNameList(
                            conContext.sslConfig.serverNames, peerHost);
        }
        conContext.setQuic(true);
    }

    @Override
    public void setUseClientMode(boolean mode) {
        conContext.setUseClientMode(mode);
        this.handshakeStatus = mode
                ? SSLEngineResult.HandshakeStatus.NEED_WRAP
                : SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
    }

    @Override
    public boolean getUseClientMode() {
        return conContext.sslConfig.isClientMode;
    }

    @Override
    public void setSSLParameters(final SSLParameters params) {
        Objects.requireNonNull(params);
        // section, 4.2 of RFC-9001
        // Clients MUST NOT offer TLS versions older than 1.3
        final String[] protos = params.getProtocols();
        if (protos == null || protos.length == 0) {
            throw new IllegalArgumentException("No TLS protocols set");
        }
        boolean tlsv13Present = false;
        Set<String> unsupported = new HashSet<>();
        for (String p : protos) {
            if ("TLSv1.3".equals(p)) {
                tlsv13Present = true;
            } else {
                unsupported.add(p);
            }
        }
        if (!tlsv13Present) {
            throw new IllegalArgumentException(
                    "required TLSv1.3 protocol version hasn't been set");
        }
        if (!unsupported.isEmpty()) {
            throw new IllegalArgumentException(
                    "Unsupported TLS protocol versions " + unsupported);
        }
        conContext.sslConfig.setSSLParameters(params);
    }

    @Override
    public SSLSession getSession() {
        return conContext.conSession;
    }

    @Override
    public SSLSession getHandshakeSession() {
        final HandshakeContext handshakeContext = conContext.handshakeContext;
        return handshakeContext == null
                ? null
                : handshakeContext.handshakeSession;
    }

    /**
     * {@return the {@link AlgorithmConstraints} that are applicable for this engine,
     * or null if none are applicable}
     */
    AlgorithmConstraints getAlgorithmConstraints() {
        final HandshakeContext handshakeContext = conContext.handshakeContext;
        // if we are handshaking then use the handshake context
        // to determine the constraints, else use the configured
        // SSLParameters
        return handshakeContext == null
                ? getSSLParameters().getAlgorithmConstraints()
                : handshakeContext.sslConfig.userSpecifiedAlgorithmConstraints;
    }

    @Override
    public SSLParameters getSSLParameters() {
        return conContext.sslConfig.getSSLParameters();
    }

    @Override
    public String getApplicationProtocol() {
        // TODO: review thread safety when dealing with conContext
        return conContext.applicationProtocol;
    }

    @Override
    public Set<QuicVersion> getSupportedQuicVersions() {
        return SUPPORTED_QUIC_VERSIONS;
    }

    @Override
    public void setOneRttContext(final QuicOneRttContext ctx) {
        this.oneRttKeyManager.setOneRttContext(ctx);
    }

    private QuicVersion getNegotiatedVersion() {
        final QuicVersion negotiated = this.negotiatedVersion;
        if (negotiated == null) {
            throw new IllegalStateException(
                    "Quic version hasn't been negotiated yet");
        }
        return negotiated;
    }

    private boolean isEnabled(final QuicVersion quicVersion) {
        final Set<QuicVersion> enabled = getSupportedQuicVersions();
        if (enabled == null) {
            return false;
        }
        return enabled.contains(quicVersion);
    }

    /**
     * Returns the current handshake state of the connection. Sometimes packets
     * that could be decrypted can be received before the handshake has
     * completed, but should not be decrypted until it is complete
     *
     * @return the HandshakeStatus
     */
    @Override
    public SSLEngineResult.HandshakeStatus getHandshakeStatus() {
        return handshakeStatus;
    }

    /**
     * Returns the current sending key space (encryption level)
     *
     * @return the current sending key space
     */
    @Override
    public QuicTransport.KeySpace getCurrentSendKeySpace() {
        return sendKeySpace;
    }

    @Override
    public boolean keysAvailable(QuicTransport.KeySpace keySpace) {
        return switch (keySpace) {
            case INITIAL -> this.initialKeyManager.keysAvailable();
            case HANDSHAKE -> this.handshakeKeyManager.keysAvailable();
            case ONE_RTT -> this.oneRttKeyManager.keysAvailable();
            case ZERO_RTT -> false;
            default -> throw new IllegalArgumentException(
                    keySpace + " not expected here");
        };
    }

    @Override
    public void discardKeys(QuicTransport.KeySpace keySpace) {
        switch (keySpace) {
            case INITIAL -> this.initialKeyManager.discardKeys();
            case HANDSHAKE -> this.handshakeKeyManager.discardKeys();
            case ONE_RTT -> this.oneRttKeyManager.discardKeys();
            default -> throw new IllegalArgumentException(
                    "key discarding not implemented for " + keySpace);
        }
    }

    @Override
    public int getHeaderProtectionSampleSize(QuicTransport.KeySpace keySpace) {
        return switch (keySpace) {
            case INITIAL, HANDSHAKE, ZERO_RTT, ONE_RTT -> 16;
            default -> throw new IllegalArgumentException(
                    "Type '" + keySpace + "' not expected here");
        };
    }

    @Override
    public ByteBuffer computeHeaderProtectionMask(QuicTransport.KeySpace keySpace,
                                                  boolean incoming, ByteBuffer sample)
            throws QuicKeyUnavailableException, QuicTransportException {
        final QuicKeyManager keyManager = keyManager(keySpace);
        if (incoming) {
            final QuicCipher.QuicReadCipher quicCipher =
                    keyManager.getReadCipher();
            return quicCipher.computeHeaderProtectionMask(sample);
        } else {
            final QuicCipher.QuicWriteCipher quicCipher =
                    keyManager.getWriteCipher();
            return quicCipher.computeHeaderProtectionMask(sample);
        }
    }

    @Override
    public int getAuthTagSize() {
        // RFC-9001, section 5.3
        // QUIC can use any of the cipher suites defined in [TLS13] with the
        // exception of TLS_AES_128_CCM_8_SHA256. ...
        // These cipher suites have a 16-byte authentication tag and produce
        // an output 16 bytes larger than their input.
        return 16;
    }

    @Override
    public void encryptPacket(final QuicTransport.KeySpace keySpace, final long packetNumber,
                              final IntFunction<ByteBuffer> headerGenerator,
                              final ByteBuffer packetPayload, final ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException, ShortBufferException {
        final QuicKeyManager keyManager = keyManager(keySpace);
        keyManager.encryptPacket(packetNumber, headerGenerator, packetPayload, output);
    }

    @Override
    public void decryptPacket(final QuicTransport.KeySpace keySpace,
            final long packetNumber, final int keyPhase,
            final ByteBuffer packet, final int headerLength,
            final ByteBuffer output)
            throws QuicKeyUnavailableException, AEADBadTagException,
            QuicTransportException, ShortBufferException {
        final QuicKeyManager keyManager = keyManager(keySpace);
        keyManager.decryptPacket(packetNumber, keyPhase, packet, headerLength,
                output);
    }

    @Override
    public void signRetryPacket(final QuicVersion quicVersion,
            final ByteBuffer originalConnectionId, final ByteBuffer packet,
            final ByteBuffer output)
            throws ShortBufferException, QuicTransportException {
        if (!isEnabled(quicVersion)) {
            throw new IllegalArgumentException(
                    "Quic version " + quicVersion + " isn't enabled");
        }
        int connIdLength = originalConnectionId.remaining();
        if (connIdLength >= 256 || connIdLength < 0) {
            throw new IllegalArgumentException("connection ID length");
        }
        final Cipher cipher = InitialKeyManager.getRetryCipher(
                quicVersion, false);
        cipher.updateAAD(new byte[]{(byte) connIdLength});
        cipher.updateAAD(originalConnectionId);
        cipher.updateAAD(packet);
        try {
            // No data to encrypt, just outputting the tag which will be
            // verified later.
            cipher.doFinal(ByteBuffer.allocate(0), output);
        } catch (ShortBufferException e) {
            throw e;
        } catch (Exception e) {
            throw new QuicTransportException("Failed to sign packet",
                    null, 0, BASE_CRYPTO_ERROR + Alert.INTERNAL_ERROR.id, e);
        }
    }

    @Override
    public void verifyRetryPacket(final QuicVersion quicVersion,
            final ByteBuffer originalConnectionId,
            final ByteBuffer packet)
            throws AEADBadTagException, QuicTransportException {
        if (!isEnabled(quicVersion)) {
            throw new IllegalArgumentException(
                    "Quic version " + quicVersion + " isn't enabled");
        }
        int connIdLength = originalConnectionId.remaining();
        if (connIdLength >= 256 || connIdLength < 0) {
            throw new IllegalArgumentException("connection ID length");
        }
        int originalLimit = packet.limit();
        packet.limit(originalLimit - 16);
        final Cipher cipher =
                InitialKeyManager.getRetryCipher(quicVersion, true);
        cipher.updateAAD(new byte[]{(byte) connIdLength});
        cipher.updateAAD(originalConnectionId);
        cipher.updateAAD(packet);
        packet.limit(originalLimit);
        try {
            assert packet.remaining() == 16;
            int outBufLength = cipher.getOutputSize(packet.remaining());
            // No data to decrypt, just checking the tag.
            ByteBuffer outBuffer = ByteBuffer.allocate(outBufLength);
            cipher.doFinal(packet, outBuffer);
            assert outBuffer.position() == 0;
        } catch (AEADBadTagException e) {
            throw e;
        } catch (Exception e) {
            throw new QuicTransportException("Failed to verify packet",
                    null, 0, BASE_CRYPTO_ERROR + Alert.INTERNAL_ERROR.id, e);
        }
    }

    private QuicKeyManager keyManager(final QuicTransport.KeySpace keySpace) {
        return switch (keySpace) {
            case INITIAL -> this.initialKeyManager;
            case HANDSHAKE -> this.handshakeKeyManager;
            case ONE_RTT -> this.oneRttKeyManager;
            default -> throw new IllegalArgumentException(
                    "No key manager available for key space: " + keySpace);
        };
    }

    @Override
    public ByteBuffer getHandshakeBytes(QuicTransport.KeySpace keySpace) throws IOException {
        if (keySpace != sendKeySpace) {
            throw new IllegalStateException("Unexpected key space: "
                    + keySpace + " (expected " + sendKeySpace + ")");
        }
        if (handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP ||
                !conContext.outputRecord.isEmpty()) { // session ticket
            var buf = ByteBuffer.allocate(4096);
            produceNextHandshakeMessage(buf);
            return buf;
        } else {
            return null;
        }
    }

    private void produceNextHandshakeMessage(ByteBuffer buf) throws IOException {
        if (!conContext.isNegotiated && !conContext.isBroken &&
                !conContext.isInboundClosed() &&
                !conContext.isOutboundClosed()) {
            conContext.kickstart();
        }
        var ct = conContext.outputRecord.encode(null, 0, 0,
                new ByteBuffer[]{buf}, 0, 1);
        buf.flip();
        if (handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP) {
            if (conContext.outputRecord.isEmpty()) {
                if (conContext.isNegotiated) {
                    // client, done
                    handshakeStatus = SSLEngineResult.HandshakeStatus.FINISHED;
                } else {
                    handshakeStatus = SSLEngineResult.HandshakeStatus.NEED_UNWRAP;
                }
            }
        } else {
            assert conContext.isNegotiated;
        }
    }

    @Override
    public void consumeHandshakeBytes(QuicTransport.KeySpace keySpace, ByteBuffer payload)
            throws QuicTransportException {
        if (!payload.hasRemaining()) {
            throw new IllegalArgumentException("Empty crypto buffer");
        }
        if (keySpace == QuicTransport.KeySpace.ZERO_RTT) {
            throw new IllegalArgumentException("Crypto in zero-rtt");
        }
        if (keySpace != recvKeySpace) {
            throw new IllegalStateException("Unexpected key space: "
                    + keySpace + " (expected " + recvKeySpace + ")");
        }
        try {
            if (!conContext.isNegotiated && !conContext.isBroken &&
                    !conContext.isInboundClosed() &&
                    !conContext.isOutboundClosed()) {
                conContext.kickstart();
            }
        } catch (IOException e) {
            throw new QuicTransportException(e.toString(), null, 0,
                    BASE_CRYPTO_ERROR + Alert.INTERNAL_ERROR.id, e);
        }
        try {
            SSLTransport.decode(conContext, new ByteBuffer[]{payload}, 0, 1, null, 0, 0);
        } catch (IOException e) {
            if (e.getCause() instanceof QuicTransportException qte) {
                // rethrow quic transport parameters validation exception
                throw qte;
            }
            Alert alert = ((QuicEngineOutputRecord)
                    conContext.outputRecord).getAlert();
            throw new QuicTransportException(e.getMessage(), keySpace, 0,
                    BASE_CRYPTO_ERROR + (alert != null ? alert.id : Alert.INTERNAL_ERROR.id), e);

        }
        if (handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
            if (conContext.outputRecord.isEmpty()) {
                if (conContext.isNegotiated) {
                    // dead code? done, server side, no session ticket
                    handshakeStatus = SSLEngineResult.HandshakeStatus.FINISHED;
                }
            } else {
                // our turn to send
                if (conContext.isNegotiated && !getUseClientMode()) {
                    // done, server side, wants to send session ticket
                    handshakeStatus = SSLEngineResult.HandshakeStatus.FINISHED;
                } else {
                    // more messages needed to finish handshake
                    handshakeStatus = SSLEngineResult.HandshakeStatus.NEED_WRAP;
                }
            }
        } else {
            assert conContext.isNegotiated;
        }
    }

    @Override
    public void deriveInitialKeys(final QuicVersion quicVersion,
            final ByteBuffer connectionId) throws IOException {
        if (!isEnabled(quicVersion)) {
            throw new IllegalArgumentException("Quic version " + quicVersion +
                    " isn't enabled");
        }
        final byte[] connectionIdBytes = new byte[connectionId.remaining()];
        connectionId.get(connectionIdBytes);
        this.initialKeyManager.deriveKeys(quicVersion, connectionIdBytes,
                getUseClientMode());
    }

    @Override
    public void versionNegotiated(final QuicVersion quicVersion) {
        Objects.requireNonNull(quicVersion);
        if (!isEnabled(quicVersion)) {
            throw new IllegalArgumentException("Quic version " + quicVersion +
                    " is not enabled");
        }
        synchronized (this) {
            final QuicVersion prevNegotiated = this.negotiatedVersion;
            if (prevNegotiated != null) {
                throw new IllegalStateException("A Quic version has already " +
                        "been negotiated previously");
            }
            this.negotiatedVersion = quicVersion;
        }
    }

    private String populateCipherSuite() {
        if (cipherSuite != null)
            return cipherSuite;
        cipherSuite = conContext.handshakeContext.handshakeSession.getCipherSuite();
        assert cipherSuite != null;
        return cipherSuite;
    }

    @Override
    public void deriveReadSecrets(SecretKey secretKey, KeySpace keySpace) {
        recvKeySpace = keySpace;
        String cs = populateCipherSuite();
        final QuicVersion quicVersion = getNegotiatedVersion();
        try {
            switch (keySpace) {
                case HANDSHAKE -> this.handshakeKeyManager.deriveReadKeys(quicVersion,
                        secretKey, cs);
                case ONE_RTT -> this.oneRttKeyManager.deriveReadKeys(quicVersion,
                        secretKey, cs);
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void deriveWriteSecrets(SecretKey secretKey, KeySpace keySpace) {
        sendKeySpace = keySpace;
        String cs = populateCipherSuite();
        final QuicVersion quicVersion = getNegotiatedVersion();
        try {
            switch (keySpace) {
                case HANDSHAKE -> this.handshakeKeyManager.deriveWriteKeys(quicVersion,
                        secretKey, cs);
                case ONE_RTT -> this.oneRttKeyManager.deriveWriteKeys(quicVersion,
                        secretKey, cs);
            }
        } catch (IOException | GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Runnable getDelegatedTask() {
        // TODO: actually delegate tasks
        return null;
    }

    @Override
    public String getPeerHost() {
        return peerHost;
    }

    @Override
    public int getPeerPort() {
        return peerPort;
    }

    @Override
    public boolean useDelegatedTask() {
        return false;
    }

    public byte[] getLocalQuicTransportParameters() {
        ByteBuffer ltp = localQuicTransportParameters;
        if (ltp == null) {
            return null;
        }
        byte[] result = new byte[ltp.remaining()];
        ltp.get(0, result);
        return result;
    }

    @Override
    public void setLocalQuicTransportParameters(ByteBuffer params) {
        this.localQuicTransportParameters = params;
    }

    @Override
    public void restartHandshake() throws IOException {
        if (negotiatedVersion != null) {
            throw new IllegalStateException("Version already negotiated");
        }
        if (sendKeySpace != INITIAL || handshakeStatus != SSLEngineResult.HandshakeStatus.NEED_UNWRAP) {
            throw new IllegalStateException("Unexpected handshake state");
        }
        HandshakeContext context = conContext.handshakeContext;
        ClientHandshakeContext chc = (ClientHandshakeContext)context;

        // Refresh handshake hash
        chc.handshakeHash.finish();     // reset the handshake hash

        // Update the initial ClientHello handshake message.
        chc.initialClientHelloMsg.extensions.reproduce(chc,
                new SSLExtension[] {
                        SSLExtension.CH_QUIC_TRANSPORT_PARAMETERS,
                        SSLExtension.CH_PRE_SHARED_KEY
                });

        // produce handshake message
        chc.initialClientHelloMsg.write(chc.handshakeOutput);
        handshakeStatus = SSLEngineResult.HandshakeStatus.NEED_WRAP;
    }

    @Override
    public void setRemoteQuicTransportParametersConsumer(
            QuicTransportParametersConsumer consumer) {
        this.remoteQuicTransportParametersConsumer = consumer;
    }

    void processRemoteQuicTransportParameters(ByteBuffer buffer)
            throws QuicTransportException{
        remoteQuicTransportParametersConsumer.accept(buffer);
    }

    @Override
    public boolean tryMarkHandshakeDone() {
        if (getUseClientMode()) {
            // not expected to be called on client
            throw new IllegalStateException(
                    "Not expected to be called in client mode");
        }
        final boolean confirmed = HANDSHAKE_STATE_HANDLE.compareAndSet(this,
                SSLEngineResult.HandshakeStatus.FINISHED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
        if (confirmed) {
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.fine("QuicTLSEngine (server) marked handshake " +
                        "state as NOT_HANDSHAKING");
            }
        }
        return confirmed;
    }

    @Override
    public boolean tryReceiveHandshakeDone() {
        final boolean isClient = getUseClientMode();
        if (!isClient) {
            throw new IllegalStateException(
                    "Not expected to receive HANDSHAKE_DONE in server mode");
        }
        final boolean confirmed = HANDSHAKE_STATE_HANDLE.compareAndSet(this,
                SSLEngineResult.HandshakeStatus.FINISHED, SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING);
        if (confirmed) {
            if (SSLLogger.isOn() && SSLLogger.isOn(SSLLogger.Opt.SSL)) {
                SSLLogger.fine(
                        "QuicTLSEngine (client) received HANDSHAKE_DONE," +
                        " marking state as NOT_HANDSHAKING");
            }
        }
        return confirmed;
    }

    @Override
    public boolean isTLSHandshakeComplete() {
        final boolean isClient = getUseClientMode();
        final SSLEngineResult.HandshakeStatus hsState = this.handshakeStatus;
        if (isClient) {
            // the client has received TLS Finished message from server and
            // has sent its own TLS Finished message and is waiting for the server
            // to send QUIC HANDSHAKE_DONE frame.
            // OR
            // the client has received TLS Finished message from server and
            // has sent its own TLS Finished message and has even received the
            // QUIC HANDSHAKE_DONE frame.
            // Either of these implies the TLS handshake is complete for the client
            return hsState == SSLEngineResult.HandshakeStatus.FINISHED ||
                    hsState == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
        }
        // on the server side the TLS handshake is complete only when the server has
        // sent a TLS Finished message and received the client's Finished message.
        return hsState == SSLEngineResult.HandshakeStatus.NOT_HANDSHAKING;
    }

    /**
     * {@return the key phase being used when decrypting incoming 1-RTT
     * packets}
     */
    // this is only used in tests
    public int getOneRttKeyPhase() throws QuicKeyUnavailableException {
        return this.oneRttKeyManager.getReadCipher().getKeyPhase();
    }
}
