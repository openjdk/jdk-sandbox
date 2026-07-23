/*
 * Copyright (c) 2023, 2025, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.IntFunction;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.KDF;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.HKDFParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLHandshakeException;

import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.quic.QuicKeyUnavailableException;
import jdk.internal.net.http.quic.QuicOneRttContext;
import jdk.internal.net.http.quic.QuicTLSEngine;
import jdk.internal.net.http.quic.QuicTransportErrors;
import jdk.internal.net.http.quic.QuicTransportException;
import jdk.internal.net.http.quic.QuicVersion;
import jdk.internal.net.http.quic.tls.QuicCipher.QuicReadCipher;
import jdk.internal.net.http.quic.tls.QuicCipher.QuicWriteCipher;

import static jdk.internal.net.http.quic.QuicTLSEngine.KeySpace.HANDSHAKE;
import static jdk.internal.net.http.quic.QuicTLSEngine.KeySpace.INITIAL;
import static jdk.internal.net.http.quic.QuicTLSEngine.KeySpace.ONE_RTT;
import static jdk.internal.net.http.quic.QuicTransportErrors.AEAD_LIMIT_REACHED;
import static jdk.internal.net.http.quic.QuicTransportErrors.KEY_UPDATE_ERROR;

sealed abstract class QuicKeyManager
        permits QuicKeyManager.HandshakeKeyManager,
        QuicKeyManager.InitialKeyManager, QuicKeyManager.OneRttKeyManager {

    protected QuicWriteCipher deriveWriteCipher(QuicVersion quicVersion, SecretKey secret, QuicCipherSuite cipherSuite) throws GeneralSecurityException {
        final QuicKeys keys = deriveQuicKeys(quicVersion,
                cipherSuite,
                secret);

        return QuicCipher.createWriteCipher(cipherSuite,
                secret,
                keys.key, keys.iv,
                keys.hp, 0);
    }

    protected QuicReadCipher deriveReadCipher(QuicVersion quicVersion, SecretKey secret, QuicCipherSuite cipherSuite) throws GeneralSecurityException {
        final QuicKeys keys = deriveQuicKeys(quicVersion,
                cipherSuite,
                secret);

        return QuicCipher.createReadCipher(cipherSuite,
                secret,
                keys.key, keys.iv,
                keys.hp, 0);
    }

    private record QuicKeys(SecretKey key, byte[] iv, SecretKey hp) {
    }

    private record CipherPair(QuicReadCipher readCipher,
                              QuicWriteCipher writeCipher) {
        void discard(boolean destroyHP) {
            if (writeCipher != null) {
                writeCipher.discard(destroyHP);
            }
            if (readCipher != null) {
                readCipher.discard(destroyHP);
            }
        }

        /**
         * {@return true if the keys represented by this {@code CipherPair}
         * were used by both this endpoint and the peer, thus implying these
         * keys are available to both of them}
         */
        boolean usedByBothEndpoints() {
            return readCipher != null &&
                    writeCipher != null &&
                    this.readCipher.hasDecryptedAny() &&
                    this.writeCipher.hasEncryptedAny();
        }
    }

    final QuicTLSEngine.KeySpace keySpace;
    // counter towards the integrity limit
    final AtomicLong invalidPackets = new AtomicLong();
    volatile boolean keysDiscarded;

    private QuicKeyManager(final QuicTLSEngine.KeySpace keySpace) {
        this.keySpace = keySpace;
    }

    protected abstract boolean keysAvailable();

    protected abstract QuicReadCipher getReadCipher()
            throws QuicKeyUnavailableException;

    protected abstract QuicWriteCipher getWriteCipher()
            throws QuicKeyUnavailableException;

    abstract void discardKeys();

    void decryptPacket(final long packetNumber, final int keyPhase,
            final ByteBuffer packet,final int headerLength,
            final ByteBuffer output) throws QuicKeyUnavailableException,
            IllegalArgumentException, AEADBadTagException,
            QuicTransportException, ShortBufferException {
        // keyPhase is only applicable for 1-RTT packets; the decryptPacket
        // method is overridden by OneRttKeyManager, so this check is for
        // other packet types
        if (keyPhase != -1) {
            throw new IllegalArgumentException(
                    "Unexpected key phase value: " + keyPhase);
        }
        // use current keys to decrypt
        QuicReadCipher readCipher = getReadCipher();
        try {
            readCipher.decryptPacket(packetNumber, packet, headerLength, output);
        } catch (AEADBadTagException e) {
            if (invalidPackets.incrementAndGet() >=
                    readCipher.integrityLimit()) {
                throw new QuicTransportException("Integrity limit reached",
                        keySpace, 0, AEAD_LIMIT_REACHED);
            }
            throw e;
        }
    }

    void encryptPacket(final long packetNumber,
                       final IntFunction<ByteBuffer> headerGenerator,
                       final ByteBuffer packetPayload,
                       final ByteBuffer output)
            throws QuicKeyUnavailableException, QuicTransportException, ShortBufferException {
        // generate the packet header passing the generator the key phase
        final ByteBuffer header = headerGenerator.apply(0); // key phase is always 0 for non-ONE_RTT
        getWriteCipher().encryptPacket(packetNumber, header, packetPayload, output);
    }

    private static QuicKeys deriveQuicKeys(final QuicVersion quicVersion,
                                           final QuicCipherSuite cs, final SecretKey traffic_secret)
            throws GeneralSecurityException {
        final QuicTLSKeyDerivation kd = new QuicTLSKeyDerivation(cs,
                traffic_secret);
        final QuicTLSData tlsData = getQuicData(quicVersion);
        final SecretKey quic_key = kd.deriveKey(tlsData.getTlsKeyLabel());
        final byte[] quic_iv = kd.deriveData(tlsData.getTlsIvLabel());
        final SecretKey quic_hp = kd.deriveKey(tlsData.getTlsHpLabel());
        return new QuicKeys(quic_key, quic_iv, quic_hp);
    }

    // Used in 1RTT when advancing the keyphase.  quic_hp is not advanced.
    private static QuicKeys deriveQuicKeys(final QuicVersion quicVersion,
            final QuicCipherSuite cs, final SecretKey traffic_secret,
            final SecretKey quic_hp) throws GeneralSecurityException {
        final QuicTLSKeyDerivation kd = new QuicTLSKeyDerivation(cs,
                traffic_secret);
        final QuicTLSData tlsData = getQuicData(quicVersion);
        final SecretKey quic_key = kd.deriveKey(tlsData.getTlsKeyLabel());
        final byte[] quic_iv = kd.deriveData(tlsData.getTlsIvLabel());
        return new QuicKeys(quic_key, quic_iv, quic_hp);
    }

    private static QuicTLSData getQuicData(final QuicVersion quicVersion) {
        return switch (quicVersion) {
            case QUIC_V1 -> QuicTLSData.V1;
            case QUIC_V2 -> QuicTLSData.V2;
        };
    }

    private static byte[] createHkdfInfo(final String label, final int length) {
        final byte[] tls13Label =
                ("tls13 " + label).getBytes(StandardCharsets.ISO_8859_1);
        return createHkdfInfo(tls13Label, length);
    }

    private static byte[] createHkdfInfo(final byte[] tls13Label,
            final int length) {
        final byte[] info = new byte[4 + tls13Label.length];
        final ByteBuffer m = ByteBuffer.wrap(info);
        m.putShort((short) length);
        assert tls13Label.length < 256;
        m.put((byte)tls13Label.length);
        m.put(tls13Label);
        m.put((byte)0); // zero-length context
        assert !m.hasRemaining();
        return info;
    }

    static final class InitialKeyManager extends QuicKeyManager {

        private volatile CipherPair cipherPair;

        InitialKeyManager() {
            super(INITIAL);
        }

        @Override
        protected boolean keysAvailable() {
            return this.cipherPair != null && !this.keysDiscarded;
        }

        @Override
        protected QuicReadCipher getReadCipher()
                throws QuicKeyUnavailableException {
            final CipherPair pair = this.cipherPair;
            if (pair == null) {
                final String msg = this.keysDiscarded
                        ? "Keys have been discarded"
                        : "Keys not available";
                throw new QuicKeyUnavailableException(msg, this.keySpace);
            }
            return pair.readCipher;
        }

        @Override
        protected QuicWriteCipher getWriteCipher()
                throws QuicKeyUnavailableException {
            final CipherPair pair = this.cipherPair;
            if (pair == null) {
                final String msg = this.keysDiscarded
                        ? "Keys have been discarded"
                        : "Keys not available";
                throw new QuicKeyUnavailableException(msg, this.keySpace);
            }
            return pair.writeCipher;
        }

        @Override
        void discardKeys() {
            final CipherPair toDiscard = this.cipherPair;
            this.keysDiscarded = true;
            this.cipherPair = null; // no longer needed
            if (toDiscard == null) {
                return;
            }
            if (Log.ssl()) {
                Log.logSSL("discarding keys (keyphase="
                        + toDiscard.writeCipher.getKeyPhase()
                        + ") of " + this.keySpace + " key space");
            }
            toDiscard.discard(true);
        }

        void deriveKeys(final QuicVersion quicVersion,
                final byte[] connectionId,
                final boolean clientMode) throws IOException{
            Objects.requireNonNull(quicVersion);
            final QuicCipherSuite cs = QuicCipherSuite.TLS_AES_128_GCM_SHA256;

            KDF hkdf;
            try {
                hkdf = KDF.getInstance(cs.hkdfAlgorithm);
            } catch (NoSuchAlgorithmException e) {
                throw new SSLHandshakeException("Could not generate secret", e);
            }
            final QuicTLSData tlsData = QuicKeyManager.getQuicData(quicVersion);
            SecretKey initial_secret = null;
            SecretKey server_initial_secret = null;
            SecretKey client_initial_secret = null;
            try {
                initial_secret = hkdf.deriveKey("TlsInitialSecret",
                        HKDFParameterSpec.ofExtract()
                                .addSalt(tlsData.getInitialSalt())
                                .addIKM(connectionId).extractOnly());

                byte[] clientInfo = createHkdfInfo("client in",
                        cs.hashLength);
                client_initial_secret =
                        hkdf.deriveKey("TlsClientInitialTrafficSecret",
                                HKDFParameterSpec.expandOnly(
                                        initial_secret,
                                        clientInfo,
                                        cs.hashLength));

                byte[] serverInfo = createHkdfInfo("server in",
                        cs.hashLength);
                server_initial_secret =
                        hkdf.deriveKey("TlsServerInitialTrafficSecret",
                                HKDFParameterSpec.expandOnly(
                                        initial_secret,
                                        serverInfo,
                                        cs.hashLength));

                final QuicReadCipher readCipher;
                final QuicWriteCipher writeCipher;
                if (clientMode) {
                    readCipher = deriveReadCipher(quicVersion,
                            server_initial_secret,
                            cs);
                    writeCipher = deriveWriteCipher(quicVersion,
                            client_initial_secret,
                            cs);
                } else {
                    readCipher = deriveReadCipher(quicVersion,
                            client_initial_secret,
                            cs);
                    writeCipher = deriveWriteCipher(quicVersion,
                            server_initial_secret,
                            cs);
                }
                final CipherPair old = this.cipherPair;
                // we don't check if keys are already available, since it's a
                // valid case where the INITIAL keys are regenerated due to a
                // RETRY packet from the peer or even for the case where a
                // different quic version was negotiated by the server
                this.cipherPair = new CipherPair(readCipher, writeCipher);
                if (old != null) {
                    old.discard(true);
                }
            } catch (GeneralSecurityException e) {
                throw new SSLException("Missing cipher algorithm", e);
            } finally {
                QuicCipher.safeDiscard(initial_secret);
                QuicCipher.safeDiscard(client_initial_secret);
                QuicCipher.safeDiscard(server_initial_secret);
            }
        }

        static Cipher getRetryCipher(final QuicVersion quicVersion,
                final boolean incoming) throws QuicTransportException {
            final QuicTLSData tlsData = QuicKeyManager.getQuicData(quicVersion);
            return tlsData.getRetryCipher(incoming);
        }
    }

    static final class HandshakeKeyManager extends QuicKeyManager {
        private volatile CipherPair cipherPair;

        HandshakeKeyManager() {
            super(HANDSHAKE);
        }

        @Override
        protected boolean keysAvailable() {
            return this.cipherPair != null && !this.keysDiscarded;
        }

        @Override
        protected QuicReadCipher getReadCipher()
                throws QuicKeyUnavailableException {
            final CipherPair pair = this.cipherPair;
            if (pair == null || pair.readCipher == null) {
                final String msg = this.keysDiscarded
                        ? "Keys have been discarded"
                        : "Keys not available";
                throw new QuicKeyUnavailableException(msg, this.keySpace);
            }
            return pair.readCipher;
        }

        @Override
        protected QuicWriteCipher getWriteCipher()
                throws QuicKeyUnavailableException {
            final CipherPair pair = this.cipherPair;
            if (pair == null || pair.writeCipher == null) {
                final String msg = this.keysDiscarded
                        ? "Keys have been discarded"
                        : "Keys not available";
                throw new QuicKeyUnavailableException(msg, this.keySpace);
            }
            return pair.writeCipher;
        }

        @Override
        void discardKeys() {
            final CipherPair toDiscard = this.cipherPair;
            this.cipherPair = null; // no longer needed
            this.keysDiscarded = true;
            if (toDiscard == null) {
                return;
            }
            if (Log.ssl()) {
                Log.logSSL("discarding keys (keyphase="
                        + toDiscard.writeCipher.getKeyPhase()
                        + ") of " + this.keySpace + " key space");
            }
            toDiscard.discard(true);
        }

        public void deriveWriteKeys(QuicVersion quicVersion, SecretKey secret, QuicCipherSuite cipherSuite)
                throws GeneralSecurityException {
            final QuicWriteCipher writeCipher = deriveWriteCipher(quicVersion, secret, cipherSuite);
            CipherPair cp = cipherPair;
            if (cp == null) {
                cipherPair = new CipherPair(null, writeCipher);
            } else if (cp.writeCipher == null) {
                cipherPair = new CipherPair(cp.readCipher, writeCipher);
            } else {
                throw new IllegalStateException("Write cipher already derived");
            }
        }

        public void deriveReadKeys(QuicVersion quicVersion, SecretKey secret, QuicCipherSuite cipherSuite)
                throws GeneralSecurityException {
            final QuicReadCipher readCipher = deriveReadCipher(quicVersion, secret, cipherSuite);
            CipherPair cp = cipherPair;
            if (cp == null) {
                cipherPair = new CipherPair(readCipher, null);
            } else if (cp.readCipher == null) {
                cipherPair = new CipherPair(readCipher, cp.writeCipher);
            } else {
                throw new IllegalStateException("Read cipher already derived");
            }
        }
    }

    static final class OneRttKeyManager extends QuicKeyManager {
        // a series of keys that the 1-RTT key manager uses
        private record KeySeries(QuicReadCipher old, CipherPair current,
                                 CipherPair next) {
            private KeySeries {
                Objects.requireNonNull(current);
                if (old != null) {
                    if (old.getKeyPhase() ==
                            current.writeCipher.getKeyPhase()) {
                        throw new IllegalArgumentException("Both old keys and" +
                                " current keys have the same key phase: " +
                                current.writeCipher.getKeyPhase());
                    }
                }
                if (next != null) {
                    if (next.writeCipher.getKeyPhase() ==
                            current.writeCipher.getKeyPhase()) {
                        throw new IllegalArgumentException("Both next keys " +
                                "and current keys have the same key phase: " +
                                current.writeCipher.getKeyPhase());
                    }
                }
            }

            /**
             * {@return true if this {@code KeySeries} has an old decryption key
             * and the {@code pktNum} is lower than the least packet number the
             * current decryption key has decrypted so far}
             *
             * @param pktNum the packet number for which the old key
             *         might be needed
             */
            boolean canUseOldDecryptKey(final long pktNum) {
                assert pktNum >= 0 : "unexpected packet number: " + pktNum;
                if (this.old == null) {
                    return false;
                }
                final QuicReadCipher currentKey = this.current.readCipher;
                final long lowestDecrypted = currentKey.lowestDecryptedPktNum();
                // if the incoming packet number is lesser than the lowest
                // decrypted packet number by the current key, then it
                // implies that this might be a delayed packet and thus is
                // allowed to use the old key (if available) from
                // the previous key phase.
                // see RFC-9001, section 6.5
                if (lowestDecrypted == -1) {
                    return true;
                }
                return pktNum < lowestDecrypted;
            }
        }

        // will be set when the keys are derived
        private volatile QuicVersion negotiatedVersion;

        private final Lock keySeriesLock = new ReentrantLock();
        // will be set when keys are derived and will
        // be updated whenever keys are updated.
        // Must be updated/written only
        // when holding the keySeriesLock lock
        private volatile KeySeries keySeries;

        private volatile QuicOneRttContext oneRttContext;

        OneRttKeyManager() {
            super(ONE_RTT);
        }

        @Override
        protected boolean keysAvailable() {
            KeySeries ks = this.keySeries;
            // the read cipher may be available before the write cipher,
            // but should not be used until both are available
            return ks != null &&
                    ks.current.writeCipher != null &&
                    ks.current.readCipher != null &&
                    !this.keysDiscarded;
        }

        @Override
        protected QuicReadCipher getReadCipher()
                throws QuicKeyUnavailableException {
            final KeySeries series = requireKeySeries();
            return series.current.readCipher;
        }

        @Override
        protected QuicWriteCipher getWriteCipher()
                throws QuicKeyUnavailableException {
            final KeySeries series = requireKeySeries();
            return series.current.writeCipher;
        }

        @Override
        void discardKeys() {
            this.keysDiscarded = true;
            final KeySeries series;
            this.keySeriesLock.lock();
            try {
                series = this.keySeries;
                this.keySeries = null; // no longer available
            } finally {
                this.keySeriesLock.unlock();
            }
            if (series == null) {
                return;
            }
            if (Log.ssl()) {
                Log.logSSL("discarding key (series) of " +
                        this.keySpace + " key space");
            }
            if (series.old != null) {
                series.old.discard(false);
            }
            discardKeys(series.current);
            discardKeys(series.next);
        }

        @Override
        void decryptPacket(final long packetNumber, final int keyPhase,
                final ByteBuffer packet, final int headerLength,
                final ByteBuffer output) throws QuicKeyUnavailableException,
                QuicTransportException, AEADBadTagException, ShortBufferException {
            if (keyPhase != 0 && keyPhase != 1) {
                throw new IllegalArgumentException("Unexpected key phase " +
                        "value: " + keyPhase);
            }
            final KeySeries series = requireKeySeries();
            final CipherPair current = series.current;
            // Use the write cipher's key phase to detect a key update as noted
            // in RFC-9001, section 6.2:
            // An endpoint detects a key update when processing a packet with
            // a key phase that differs from the value used to protect the
            // last packet it sent.
            final int currentKeyPhase = current.writeCipher.getKeyPhase();
            if (keyPhase == currentKeyPhase) {
                current.readCipher.decryptPacket(packetNumber, packet,
                        headerLength, output);
                return;
            }
            // incoming packet is using a key phase which doesn't match the
            // current key phase. this implies that either a key update
            // is being initiated or a key update initiated by the current
            // endpoint is in progress and some older packet with the
            // previous key phase has arrived.
            if (series.canUseOldDecryptKey(packetNumber)) {
                final QuicReadCipher oldReadCipher = series.old;
                assert oldReadCipher != null : "old key is unexpectedly null";
                if (Log.ssl()) {
                    Log.logSSL("using old read key to decrypt packet: " +
                            packetNumber + ", with incoming key phase: " +
                            keyPhase + ", current key phase: " +
                            currentKeyPhase);
                }
                oldReadCipher.decryptPacket(
                        packetNumber, packet, headerLength, output);
                // we were able to decrypt using an old key. now verify
                // that it was OK to use this old key for this packet.
                if (!series.current.usedByBothEndpoints()
                        && series.current.writeCipher.hasEncryptedAny()
                        && oneRttContext.getLargestPeerAckedPN()
                            >= series.current.writeCipher.lowestEncryptedPktNum()) {
                    // RFC-9001, section 6.2:
                    // An endpoint that receives an acknowledgment that is
                    // carried in a packet protected with old keys where any
                    // acknowledged packet was protected with newer keys MAY
                    // treat that as a connection error of type
                    // KEY_UPDATE_ERROR. This indicates that a peer has
                    // received and acknowledged a packet that initiates a key
                    // update, but has not updated keys in response.
                    if (Log.ssl()) {
                        Log.logSSL("peer used incorrect key, was" +
                                " expected to use updated key of" +
                                " key phase: " + currentKeyPhase +
                                ", incoming key phase: " + keyPhase +
                                ", packet number: " + packetNumber);
                    }
                    throw new QuicTransportException("peer used incorrect" +
                            " key, was expected to use updated key",
                            this.keySpace, 0, KEY_UPDATE_ERROR);
                }
                return;
            }
            if (Log.ssl()) {
                Log.logSSL("detected ONE_RTT key update, current key " +
                        "phase: " + currentKeyPhase
                        + ", incoming key phase: " + keyPhase
                        + ", packet number: " + packetNumber);
            }
            decryptUsingNextKeys(
                    series, packetNumber, packet, headerLength, output);
        }

        @Override
        void encryptPacket(final long packetNumber,
                           final IntFunction<ByteBuffer> headerGenerator,
                           final ByteBuffer packetPayload,
                           final ByteBuffer output)
                throws QuicKeyUnavailableException, QuicTransportException, ShortBufferException {
            KeySeries currentSeries = requireKeySeries();
            if (currentSeries.next == null) {
                // next keys haven't yet been generated,
                // generate them now
                try {
                    currentSeries = generateNextKeys(
                            this.negotiatedVersion, currentSeries);
                } catch (GeneralSecurityException e) {
                    throw new QuicTransportException("Failed to update keys",
                            ONE_RTT, 0, QuicTransportErrors.INTERNAL_ERROR.code(), e);
                }
            }
            maybeInitiateKeyUpdate(currentSeries, packetNumber);
            // call getWriteCipher() afresh so that it can use
            // the new keyseries if at all the key update was
            // initiated
            final QuicWriteCipher writeCipher = getWriteCipher();
            final int keyPhase = writeCipher.getKeyPhase();
            // generate the packet header passing the generator the key phase
            final ByteBuffer header = headerGenerator.apply(keyPhase);
            writeCipher.encryptPacket(packetNumber, header, packetPayload, output);
        }

        void setOneRttContext(final QuicOneRttContext ctx) {
            Objects.requireNonNull(ctx);
            this.oneRttContext = ctx;
        }

        private KeySeries requireKeySeries()
                throws QuicKeyUnavailableException {
            final KeySeries series = this.keySeries;
            if (series != null) {
                return series;
            }
            final String msg = this.keysDiscarded
                    ? "Keys have been discarded"
                    : "Keys not available";
            throw new QuicKeyUnavailableException(msg, this.keySpace);
        }

        // based on certain internal criteria, this method may trigger a key
        // update.
        // returns true if it does trigger the key update. false otherwise.
        private boolean maybeInitiateKeyUpdate(final KeySeries currentSeries,
                final long packetNumber) {
            final QuicWriteCipher cipher = currentSeries.current.writeCipher;
            // when we notice that we have reached 80% (which is arbitrary)
            // of the confidentiality limit, we trigger a key update instead
            // of waiting to hit the limit
            final long confidentialityLimit = cipher.confidentialityLimit();
            if (confidentialityLimit < 0) {
                return false;
            }
            final long numEncrypted = cipher.getNumEncrypted();
            if (numEncrypted >= 0.8 * confidentialityLimit) {
                if (Log.ssl()) {
                    Log.logSSL("about to reach confidentiality limit, " +
                            "attempting to initiate a 1-RTT key update," +
                            " packet number: " +
                            packetNumber + ", current key phase: " +
                            cipher.getKeyPhase());
                }
                final boolean initiated = initiateKeyUpdate(currentSeries);
                if (initiated) {
                    final int newKeyPhase =
                            this.keySeries.current.writeCipher.getKeyPhase();
                    assert cipher.getKeyPhase() != newKeyPhase
                            : "key phase of updated key unexpectedly matches " +
                            "the key phase "
                            + cipher.getKeyPhase() + " of current keys";
                    if (Log.ssl()) {
                        Log.logSSL(
                                "1-RTT key update initiated, new key phase: "
                                        + newKeyPhase);
                    }
                }
                return initiated;
            }
            return false;
        }

        private boolean initiateKeyUpdate(final KeySeries series) {
            // we only initiate a key update if this current endpoint and the
            // peer have both been using this current key
            if (!series.current.usedByBothEndpoints()) {
                // RFC-9001, section 6.1:
                // An endpoint MUST NOT initiate a subsequent key update
                // unless it has received
                // an acknowledgment for a packet that was sent protected
                // with keys from the
                // current key phase. This ensures that keys are
                // available to both peers before
                // another key update can be initiated.
                if (Log.ssl()) {
                    Log.logSSL(
                            "skipping key update initiation because peer " +
                            "hasn't yet sent us a packet encrypted with " +
                            "current key of key phase: " +
                            series.current.readCipher.getKeyPhase());
                }
                return false;
            }
            // OK to initiate a key update.
            // An endpoint initiates a key update by updating its packet
            // protection write secret
            // and using that to protect new packets.
            rolloverKeys(this.negotiatedVersion, series);
            return true;
        }

        private static void discardKeys(final CipherPair cipherPair) {
            if (cipherPair == null) {
                return;
            }
            cipherPair.discard(true);
        }

        /**
         * uses "next" keys to try and decrypt the incoming packet. if that
         * succeeded then it implies that the key update was indeed initiated by
         * the peer and this method then rolls over the keys to start using
         * these "next" keys. this method then returns true in such cases. if
         * the packet decryption using the "next" key fails, then this method
         * just returns back false (and doesn't roll over the keys)
         */
        private void decryptUsingNextKeys(
                final KeySeries currentKeySeries,
                final long packetNumber,
                final ByteBuffer packet,
                final int headerLength,
                final ByteBuffer output)
                throws QuicKeyUnavailableException, AEADBadTagException,
                ShortBufferException, QuicTransportException {
            if (currentKeySeries.next == null) {
                // this can happen if the peer initiated another
                // key update before we could generate the next
                // keys during our encryption flow. in such
                // cases we reject the key update for the packet
                // (we avoid timing attacks by not generating
                // keys during decryption, our key generation
                // only happens during encryption)
                if (Log.ssl()) {
                    Log.logSSL("next keys unavailable," +
                            " won't decrypt a packet which appears to be" +
                            " a key update");
                }
                throw new QuicKeyUnavailableException(
                        "next keys unavailable to handle key update",
                        this.keySpace);
            }
            // use the next keys to attempt decrypting
            currentKeySeries.next.readCipher.decryptPacket(packetNumber, packet,
                    headerLength, output);
            if (Log.ssl()) {
                Log.logSSL(
                        "decrypted using next keys for peer-initiated" +
                        " key update; will now switch to new key phase: " +
                        currentKeySeries.next.readCipher.getKeyPhase());
            }
            // we have successfully decrypted the packet using the new/next
            // read key. So we now update even the write key as noted in
            // RFC-9001, section 6.2:
            // If a packet is successfully processed using the next key and
            // IV, then the peer has initiated a key update. The endpoint
            // MUST update its send keys to the corresponding
            // key phase in response, as described in Section 6.1. Sending
            // keys MUST be updated before sending an acknowledgment for the
            // packet that was received with updated keys. rollover the
            // keys == old gets discarded and is replaced by
            // current, current is replaced by next and next is set to null
            // (a new set of next keys will be generated separately on
            // a schedule)
            rolloverKeys(this.negotiatedVersion, currentKeySeries);
        }

        void deriveReadKeys(final QuicVersion version,
                            final SecretKey secret,
                            QuicCipherSuite cipherSuite) throws GeneralSecurityException {
            this.negotiatedVersion = version;
            final QuicReadCipher readCipher = deriveReadCipher(version,
                    secret,
                    cipherSuite);
            this.keySeriesLock.lock();
            try {
                if (this.keySeries != null && keySeries.current.readCipher != null) {
                    // don't allow deriving the first set of 1-RTT keys more
                    // than once
                    throw new IllegalStateException("Read keys already available " +
                            "for keyspace: "
                            + this.keySpace);
                }
                if (this.keySeries != null) {
                    QuicWriteCipher writeCipher = keySeries.current.writeCipher;
                    // generate the next set of keys beforehand to prevent any timing
                    // attacks during key update
                    final QuicReadCipher nPlus1ReadCipher =
                            generateNextReadCipher(version, readCipher);
                    final QuicWriteCipher nPlus1WriteCipher =
                            generateNextWriteCipher(version, writeCipher);
                    this.keySeries = new KeySeries(null,
                            new CipherPair(readCipher, writeCipher),
                            new CipherPair(nPlus1ReadCipher, nPlus1WriteCipher));
                } else {
                    this.keySeries = new KeySeries(null,
                            new CipherPair(readCipher, null),
                            null);
                }
            } finally {
                this.keySeriesLock.unlock();
            }
        }

        void deriveWriteKeys(final QuicVersion version,
                             final SecretKey secret,
                             QuicCipherSuite cipherSuite) throws GeneralSecurityException {
            this.negotiatedVersion = version;
            final QuicWriteCipher writeCipher = deriveWriteCipher(version,
                    secret,
                    cipherSuite);
            this.keySeriesLock.lock();
            try {
                if (this.keySeries != null &&  keySeries.current.writeCipher != null) {
                    // don't allow deriving the first set of 1-RTT keys more
                    // than once
                    throw new IllegalStateException("Write keys already available " +
                            "for keyspace: "
                            + this.keySpace);
                }
                if (this.keySeries != null) {
                    QuicReadCipher readCipher = keySeries.current.readCipher;
                    // generate the next set of keys beforehand to prevent any timing
                    // attacks during key update
                    final QuicReadCipher nPlus1ReadCipher =
                            generateNextReadCipher(version, readCipher);
                    final QuicWriteCipher nPlus1WriteCipher =
                            generateNextWriteCipher(version, writeCipher);
                    this.keySeries = new KeySeries(null,
                            new CipherPair(readCipher, writeCipher),
                            new CipherPair(nPlus1ReadCipher, nPlus1WriteCipher));
                } else {
                    this.keySeries = new KeySeries(null,
                            new CipherPair(null, writeCipher),
                            null);
                }
            } finally {
                this.keySeriesLock.unlock();
            }
        }

        private static QuicWriteCipher generateNextWriteCipher(
                final QuicVersion quicVersion, final QuicWriteCipher current)
                throws GeneralSecurityException {
            final QuicTLSKeyDerivation kd =
                    new QuicTLSKeyDerivation(current.getCipherSuite(),
                            current.getBaseSecret());
            final QuicTLSData tlsData = QuicKeyManager.getQuicData(quicVersion);
            final SecretKey nplus1Secret =
                    kd.deriveKey(tlsData.getTlsKeyUpdateLabel());
            final QuicKeys quicKeys =
                    QuicKeyManager.deriveQuicKeys(quicVersion,
                            current.getCipherSuite(),
                            nplus1Secret, current.getHeaderProtectionKey());
            final int nextKeyPhase = current.getKeyPhase() == 0 ? 1 : 0;
            // toggle the 1 bit keyphase
            final QuicWriteCipher next =
                    QuicCipher.createWriteCipher(current.getCipherSuite(),
                            nplus1Secret, quicKeys.key, quicKeys.iv,
                            quicKeys.hp, nextKeyPhase);
            return next;
        }

        private static QuicReadCipher generateNextReadCipher(
                final QuicVersion quicVersion, final QuicReadCipher current)
                throws GeneralSecurityException {
            final QuicTLSKeyDerivation kd =
                    new QuicTLSKeyDerivation(current.getCipherSuite(),
                            current.getBaseSecret());
            final QuicTLSData tlsData = QuicKeyManager.getQuicData(quicVersion);
            final SecretKey nPlus1Secret =
                    kd.deriveKey(tlsData.getTlsKeyUpdateLabel());
            final QuicKeys quicKeys =
                    QuicKeyManager.deriveQuicKeys(quicVersion,
                            current.getCipherSuite(),
                            nPlus1Secret, current.getHeaderProtectionKey());
            final int nextKeyPhase = current.getKeyPhase() == 0 ? 1 : 0;
            // toggle the 1 bit keyphase
            final QuicReadCipher next =
                    QuicCipher.createReadCipher(current.getCipherSuite(),
                            nPlus1Secret, quicKeys.key,
                            quicKeys.iv, quicKeys.hp, nextKeyPhase);
            return next;
        }

        private KeySeries generateNextKeys(final QuicVersion version,
                final KeySeries currentSeries)
                throws GeneralSecurityException {
            this.keySeriesLock.lock();
            try {
                // nothing to do if some other thread
                // already changed the keySeries
                if (this.keySeries != currentSeries) {
                    return this.keySeries;
                }
                final QuicReadCipher nPlus1ReadCipher =
                        generateNextReadCipher(version,
                                currentSeries.current.readCipher);
                final QuicWriteCipher nPlus1WriteCipher =
                        generateNextWriteCipher(version,
                                currentSeries.current.writeCipher);
                // only the next keys will differ in the new series
                // as compared to the current series
                final KeySeries newSeries = new KeySeries(currentSeries.old,
                        currentSeries.current,
                        new CipherPair(nPlus1ReadCipher, nPlus1WriteCipher));
                this.keySeries = newSeries;
                return newSeries;
            } finally {
                this.keySeriesLock.unlock();
            }
        }

        /**
         * Updates the key series by "left shifting" the series of keys.
         * i.e. old keys (if any) are discarded, current keys
         * are moved to old keys and next keys are moved to current keys.
         * Note that no new keys will be generated by this method.
         * @return the key series that will be in use going forward
         */
        private KeySeries rolloverKeys(final QuicVersion version,
                final KeySeries currentSeries) {
            this.keySeriesLock.lock();
            try {
                // nothing to do if some other thread
                // already changed the keySeries
                if (this.keySeries != currentSeries) {
                    return this.keySeries;
                }
                assert currentSeries.next != null : "Key series missing next" +
                        " keys";
                // discard the old read cipher which will no longer be used
                final QuicReadCipher oldReadCipher = currentSeries.old;
                // once we move current key to old, we won't be using the
                // write cipher of that
                // moved pair
                final QuicWriteCipher writeCipherToDiscard =
                        currentSeries.current.writeCipher;
                final KeySeries newSeries = new KeySeries(
                        currentSeries.current.readCipher, currentSeries.next,
                        null);
                // update the key series
                this.keySeries = newSeries;
                if (oldReadCipher != null) {
                    if (Log.ssl()) {
                        Log.logSSL(
                                "discarding old read key of key phase: " +
                                oldReadCipher.getKeyPhase());
                    }
                    oldReadCipher.discard(false);
                }
                if (Log.ssl()) {
                    Log.logSSL("discarding write key of key phase: " +
                            writeCipherToDiscard.getKeyPhase());
                }
                writeCipherToDiscard.discard(false);
                return newSeries;
            } finally {
                this.keySeriesLock.unlock();
            }
        }
    }

    private static final class QuicTLSKeyDerivation {

        private enum HkdfLabel {
            // RFC 9001: quic version 1
            quickey("quic key"),
            quiciv("quic iv"),
            quichp("quic hp"),
            quicku("quic ku"),

            // RFC 9369: quic version 2
            quicv2key("quicv2 key"),
            quicv2iv("quicv2 iv"),
            quicv2hp("quicv2 hp"),
            quicv2ku("quicv2 ku");

            private final String label;
            private final byte[] tls13LabelBytes;

            HkdfLabel(final String label) {
                Objects.requireNonNull(label);
                this.label = label;
                this.tls13LabelBytes =
                        ("tls13 " + label).getBytes(StandardCharsets.ISO_8859_1);
            }

            private static HkdfLabel fromLabel(final String label) {
                Objects.requireNonNull(label);
                for (final HkdfLabel hkdfLabel : HkdfLabel.values()) {
                    if (hkdfLabel.label.equals(label)) {
                        return hkdfLabel;
                    }
                }
                throw new IllegalArgumentException(
                        "unrecognized label: " + label);
            }
        }

        private final QuicCipherSuite cs;
        private final SecretKey secret;

        private QuicTLSKeyDerivation(final QuicCipherSuite cs,
                final SecretKey secret) {
            this.cs = Objects.requireNonNull(cs);
            this.secret = Objects.requireNonNull(secret);
        }

        public SecretKey deriveKey(final String algorithm) throws GeneralSecurityException {
            final HkdfLabel hkdfLabel = HkdfLabel.fromLabel(algorithm);
            final KDF hkdf = KDF.getInstance(this.cs.hkdfAlgorithm);
            final int keyLength = getKeyLength(hkdfLabel);
            final byte[] hkdfInfo =
                    createHkdfInfo(hkdfLabel.tls13LabelBytes, keyLength);
            final String keyAlgo = getKeyAlgorithm(hkdfLabel);
            return hkdf.deriveKey(keyAlgo,
                    HKDFParameterSpec.expandOnly(
                            secret, hkdfInfo, keyLength));
        }

        public byte[] deriveData(final String algorithm) throws GeneralSecurityException {
            final HkdfLabel hkdfLabel = HkdfLabel.fromLabel(algorithm);
            final KDF hkdf = KDF.getInstance(this.cs.hkdfAlgorithm);
            final int keyLength = getKeyLength(hkdfLabel);
            final byte[] hkdfInfo =
                    createHkdfInfo(hkdfLabel.tls13LabelBytes, keyLength);
            return hkdf.deriveData(HKDFParameterSpec.expandOnly(
                    secret, hkdfInfo, keyLength));
        }

        private int getKeyLength(final HkdfLabel hkdfLabel) {
            return switch (hkdfLabel) {
                case quicku, quicv2ku -> {
                    // RFC-9001, section 6.1:
                    // secret_<n+1> = HKDF-Expand-Label(secret_<n>, "quic
                    // ku", "", Hash.length)
                    yield this.cs.hashLength;
                }
                case quiciv, quicv2iv -> this.cs.ivSize;
                default -> this.cs.keySize;
            };
        }

        private String getKeyAlgorithm(final HkdfLabel hkdfLabel) {
            return switch (hkdfLabel) {
                case quicku, quicv2ku -> "TlsUpdateNplus1";
                case quiciv, quicv2iv ->
                        throw new IllegalArgumentException("IV not expected");
                default -> this.cs.keyAlgorithm;
            };
        }
    }

    private enum QuicTLSData {
        V1("38762cf7f55934b34d179ae6a4c80cadccbb7f0a",
                "be0c690b9f66575a1d766b54e368c84e",
                "461599d35d632bf2239825bb",
                "quic key", "quic iv", "quic hp", "quic ku"),
        V2("0dede3def700a6db819381be6e269dcbf9bd2ed9",
                "8fb4b01b56ac48e260fbcbcead7ccc92",
                "d86969bc2d7c6d9990efb04a",
                "quicv2 key", "quicv2 iv", "quicv2 hp", "quicv2 ku");

        private final byte[] initialSalt;
        private final SecretKey retryKey;
        private final GCMParameterSpec retryIvSpec;
        private final String keyLabel;
        private final String ivLabel;
        private final String hpLabel;
        private final String kuLabel;

        QuicTLSData(String initialSalt, String retryKey, String retryIv,
                     String keyLabel, String ivLabel, String hpLabel,
                     String kuLabel) {
            this.initialSalt = HexFormat.of()
                    .parseHex(initialSalt);
            this.retryKey = new SecretKeySpec(HexFormat.of()
                    .parseHex(retryKey), "AES");
            retryIvSpec = new GCMParameterSpec(128,
                    HexFormat.of().parseHex(retryIv));
            this.keyLabel = keyLabel;
            this.ivLabel = ivLabel;
            this.hpLabel = hpLabel;
            this.kuLabel = kuLabel;
        }

        public byte[] getInitialSalt() {
            return initialSalt;
        }

        public Cipher getRetryCipher(boolean incoming) throws QuicTransportException {
            Cipher retryCipher = null;
            try {
                retryCipher = Cipher.getInstance("AES/GCM/NoPadding");
                retryCipher.init(incoming ? Cipher.DECRYPT_MODE :
                                Cipher.ENCRYPT_MODE,
                        retryKey, retryIvSpec);
            } catch (Exception e) {
                throw new QuicTransportException("Cipher not available",
                        null, 0, QuicTransportErrors.INTERNAL_ERROR.code(), e);
            }
            return retryCipher;
        }

        public String getTlsKeyLabel() {
            return keyLabel;
        }

        public String getTlsIvLabel() {
            return ivLabel;
        }

        public String getTlsHpLabel() {
            return hpLabel;
        }

        public String getTlsKeyUpdateLabel() {
            return kuLabel;
        }
    }
}
