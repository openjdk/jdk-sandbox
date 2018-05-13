/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;

import sun.security.ssl.SSLHandshake.HandshakeMessage;
import sun.security.ssl.SSLCipher.SSLReadCipher;
import sun.security.ssl.SSLCipher.SSLWriteCipher;

import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;

/**
 * Pack of the KeyUpdate handshake message.
 */
final class KeyUpdate {
    static final SSLProducer kickstartProducer =
        new KeyUpdateKickstartProducer();

    static final SSLConsumer handshakeConsumer =
        new KeyUpdateConsumer();
    static final HandshakeProducer handshakeProducer =
        new KeyUpdateProducer();

    /**
     * The KeyUpdate handshake message.
     *
     * The KeyUpdate handshake message is used to indicate that the sender is
     * updating its sending cryptographic keys.
     *
     *       enum {
     *           update_not_requested(0), update_requested(1), (255)
     *       } KeyUpdateRequest;
     *
     *       struct {
     *           KeyUpdateRequest request_update;
     *       } KeyUpdate;
     */
    static final class KeyUpdateMessage extends HandshakeMessage {
        static final byte NOTREQUSTED = 0;
        static final byte REQUSTED = 1;
        private byte status;

        KeyUpdateMessage(PostHandshakeContext context, byte status) {
            super(context);
            this.status = status;
            if (status > 1) {
                new IOException("KeyUpdate message value invalid: " + status);
            }
        }
        KeyUpdateMessage(PostHandshakeContext context, ByteBuffer m)
                throws IOException{
            super(context);

            if (m.remaining() != 1) {
                throw new IOException("KeyUpdate has an unexpected length of "+
                        m.remaining());
            }

            status = m.get();
            if (status > 1) {
                new IOException("KeyUpdate message value invalid: " + status);
            }
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.KEY_UPDATE;
        }

        @Override
        public int messageLength() {
            // one byte enum
            return 1;
        }

        @Override
        public void send(HandshakeOutStream s) throws IOException {
            s.write(status);
        }

        @Override
        public String toString() {
            throw new UnsupportedOperationException("Not supported yet.");
        }

        byte getStatus() {
            return status;
        }
    }

    private static final
            class KeyUpdateKickstartProducer implements SSLProducer {
        // Prevent instantiation of this class.
        private KeyUpdateKickstartProducer() {
            // blank
        }

        // Produce kickstart handshake message.
        @Override
        public byte[] produce(ConnectionContext context) throws IOException {
            PostHandshakeContext hc = (PostHandshakeContext)context;
            handshakeProducer.produce(hc,
                    new KeyUpdateMessage(hc, KeyUpdateMessage.REQUSTED));
            return null;
        }
    }

    /**
     * The "KeyUpdate" handshake message consumer.
     */
    private static final class KeyUpdateConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private KeyUpdateConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                ByteBuffer message) throws IOException {
            // The consuming happens in client side only.
            PostHandshakeContext hc = (PostHandshakeContext)context;
            KeyUpdateMessage km = new KeyUpdateMessage(hc, message);

            if (km.getStatus() == KeyUpdateMessage.NOTREQUSTED) {
                return;
            }

            SSLTrafficKeyDerivation kdg =
                    SSLTrafficKeyDerivation.valueOf(hc.conContext.protocolVersion);
            if (kdg == null) {
                // unlikely
                hc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key derivation: " +
                                hc.conContext.protocolVersion);
                return;
            }

            SSLKeyDerivation skd = kdg.createKeyDerivation(hc,
                    hc.conContext.inputRecord.readCipher.baseSecret);
            SecretKey nplus1 = skd.deriveKey("TlsUpdateNplus1", null);
            if (skd == null) {
                // unlikely
                hc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "no key derivation");
                return;
            }

            SSLKeyDerivation kd = kdg.createKeyDerivation(hc, nplus1);
            SecretKey key = kd.deriveKey("TlsKey", null);
            IvParameterSpec ivSpec =
                    new IvParameterSpec(kd.deriveKey("TlsIv", null)
                            .getEncoded());
            try {
                SSLReadCipher rc =
                        hc.negotiatedCipherSuite.bulkCipher.createReadCipher(
                                Authenticator.valueOf(hc.conContext.protocolVersion),
                                hc.conContext.protocolVersion, key, ivSpec,
                                hc.sslContext.getSecureRandom());
                hc.conContext.inputRecord.changeReadCiphers(rc);
                hc.conContext.inputRecord.readCipher.baseSecret = nplus1;
                if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                    SSLLogger.fine("KeyUpdate: read key updated");
                }

            } catch (GeneralSecurityException e) {
                throw new IOException(e);
            }

            // Send Reply
            handshakeProducer.produce(hc,
                    new KeyUpdateMessage(hc, KeyUpdateMessage.NOTREQUSTED));
        }
    }

    /**
     * The "KeyUpdate" handshake message producer.
     */
    private static final class KeyUpdateProducer implements HandshakeProducer {
        // Prevent instantiation of this class.
        private KeyUpdateProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {
            // The producing happens in server side only.
            PostHandshakeContext hc = (PostHandshakeContext)context;
            KeyUpdateMessage km = (KeyUpdateMessage)message;
            SecretKey secret;

            if (km.getStatus() == KeyUpdateMessage.REQUSTED) {
                secret = hc.conContext.outputRecord.writeCipher.baseSecret;
            } else {
                km.write(hc.handshakeOutput);
                hc.handshakeOutput.flush();
                return null;
            }

            SSLTrafficKeyDerivation kdg =
                    SSLTrafficKeyDerivation.valueOf(hc.conContext.protocolVersion);
            if (kdg == null) {
                // unlikely
                hc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Not supported key derivation: " +
                                hc.conContext.protocolVersion);
                return null;
            }
            // update the application traffic read keys.
            SSLKeyDerivation skd = kdg.createKeyDerivation(hc, secret);
            if (skd == null) {
                // unlikely
                hc.conContext.fatal(Alert.INTERNAL_ERROR,"no key derivation");
                return null;
            }
            SecretKey nplus1 = skd.deriveKey("TlsUpdateNplus1", null);
            SSLKeyDerivation kd =
                    kdg.createKeyDerivation(hc, nplus1);
            SecretKey key = kd.deriveKey("TlsKey", null);
            IvParameterSpec ivSpec =
                    new IvParameterSpec(kd.deriveKey("TlsIv", null)
                            .getEncoded());

            SSLWriteCipher wc;
            try {
                wc = hc.negotiatedCipherSuite.bulkCipher.createWriteCipher(
                        Authenticator.valueOf(hc.conContext.protocolVersion),
                        hc.conContext.protocolVersion, key, ivSpec,
                        hc.sslContext.getSecureRandom());

            } catch (GeneralSecurityException gse){
                hc.conContext.fatal(Alert.INTERNAL_ERROR,
                        "Failure to derive application secrets", gse);
                return null;
            }

            km.write(hc.handshakeOutput);
            hc.handshakeOutput.flush();
            hc.conContext.outputRecord.changeWriteCiphers(wc, false);
            hc.conContext.outputRecord.writeCipher.baseSecret = nplus1;
            if (SSLLogger.isOn && SSLLogger.isOn("ssl")) {
                SSLLogger.fine("KeyUpdate: write key updated");
            }
            hc.free();
            return null;
        }
    }
}
