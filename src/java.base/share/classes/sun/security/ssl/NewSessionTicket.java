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
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.GeneralSecurityException;
import java.security.ProviderException;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Optional;
import javax.crypto.SecretKey;
import javax.net.ssl.SSLHandshakeException;

import sun.security.ssl.SSLHandshake.HandshakeMessage;

/**
 * Pack of the NewSessionTicket handshake message.
 */
final class NewSessionTicket {

    private static final int SEVEN_DAYS_IN_SECONDS = 604800;

    static final SSLConsumer handshakeConsumer =
        new NewSessionTicketConsumer();
    static final SSLProducer kickstartProducer =
        new NewSessionTicketKickstartProducer();
    static final HandshakeProducer handshakeProducer =
        new NewSessionTicketProducer();

    /**
     * The NewSessionTicketMessage handshake message.
     */
    static final class NewSessionTicketMessage extends HandshakeMessage {
        final int ticketLifetime;
        final int ticketAgeAdd;
        final byte[] ticketNonce;
        final byte[] ticket;
        final SSLExtensions extensions;

        NewSessionTicketMessage(HandshakeContext context,
                int ticketLifetime, SecureRandom generator,
                byte[] ticketNonce, byte[] ticket) {
            super(context);

            this.ticketLifetime = ticketLifetime;
            this.ticketAgeAdd = generator.nextInt();
            this.ticketNonce = ticketNonce;
            this.ticket = ticket;
            this.extensions = new SSLExtensions(this);
        }

        NewSessionTicketMessage(HandshakeContext context,
                ByteBuffer m) throws IOException {
            super(context);

            this.ticketLifetime = Record.getInt32(m);
            this.ticketAgeAdd = Record.getInt32(m);
            this.ticketNonce = Record.getBytes8(m);
            this.ticket = Record.getBytes16(m);
            if (this.ticket.length == 0) {
                context.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                "Ticket has length 0");
            }

            SSLExtension[] supportedExtensions =
                context.sslConfig.getEnabledExtensions(
                SSLHandshake.NEW_SESSION_TICKET);

            if (m.hasRemaining()) {
                this.extensions =
                    new SSLExtensions(this, m, supportedExtensions);
            } else {
                this.extensions = new SSLExtensions(this);
            }
        }

        @Override
        public SSLHandshake handshakeType() {
            return SSLHandshake.NEW_SESSION_TICKET;
        }

        @Override
        public int messageLength() {
            return 8 + ticketNonce.length + 1 + ticket.length
                + 2 + extensions.length();
        }

        @Override
        public void send(HandshakeOutStream hos) throws IOException {
            hos.putInt32(ticketLifetime);
            hos.putInt32(ticketAgeAdd);
            hos.putBytes8(ticketNonce);
            hos.putBytes16(ticket);
            extensions.send(hos);
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"NewSessionTicket\": '{'\n" +
                "  \"ticket_lifetime\"      : \"{0}\",\n" +
                "  \"ticket_age_add\"       : \"{1}\",\n" +
                "  \"ticket_nonce\"         : \"{2}\",\n" +
                "  \"ticket\"               : \"{3}\",\n" +
                "  \"extensions\"           : [\n" +
                "{5}\n" +
                "  ]\n" +
                "'}'",
                Locale.ENGLISH);

            Object[] messageFields = {
                ticketLifetime,
                "omitted", //ticketAgeAdd should not be logged
                Utilities.toHexString(ticketNonce),
                Utilities.toHexString(ticket),
                Utilities.indent(extensions.toString(), "    ")
            };

            return messageFormat.format(messageFields);
        }
    }

    private static SecretKey derivePreSharedKey(CipherSuite.HashAlg hashAlg,
                                                SecretKey resumptionMasterSecret,
                                                byte[] nonce) throws IOException {

        try {
            HKDF hkdf = new HKDF(hashAlg.name);
            byte[] hkdfInfo = SSLSecretDerivation.createHkdfInfo(
                "tls13 resumption".getBytes(), nonce, hashAlg.hashLength);
            return hkdf.expand(resumptionMasterSecret, hkdfInfo,
                hashAlg.hashLength, "TlsPreSharedKey");

        } catch  (GeneralSecurityException gse) {
            throw (SSLHandshakeException) new SSLHandshakeException(
                "Could not derive PSK").initCause(gse);
        }
    }

    private static final
    class NewSessionTicketKickstartProducer implements SSLProducer {

        @Override
        public byte[] produce(ConnectionContext context) throws IOException {
            // The producing happens in server side only.
            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            if (shc.pskKeyExchangeModes.isEmpty()) {
                // client doesn't support PSK
                return null;
            }
            if (!shc.handshakeSession.isRejoinable()) {
                return null;
            }

            // get a new session ID
            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                shc.sslContext.engineGetServerSessionContext();
            SessionId newId = new SessionId(true,
                shc.sslContext.getSecureRandom());

            Optional<SecretKey> resumptionMasterSecret =
                shc.handshakeSession.getResumptionMasterSecret();
            if (!resumptionMasterSecret.isPresent()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "Session has no resumption secret. No ticket sent.");
                }
                return null;
            }

            // construct the PSK and handshake message
            BigInteger nonce = shc.handshakeSession.incrTicketNonceCounter();
            byte[] nonceArr = nonce.toByteArray();
            SecretKey psk = derivePreSharedKey(shc.negotiatedCipherSuite.hashAlg,
                resumptionMasterSecret.get(), nonceArr);

            int sessionTimeoutSeconds = sessionCache.getSessionTimeout();
            if (sessionTimeoutSeconds > SEVEN_DAYS_IN_SECONDS) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "Session timeout is too long. No NewSessionTicket sent.");
                }
                return null;
            }
            NewSessionTicketMessage nstm = new NewSessionTicketMessage(shc,
                sessionTimeoutSeconds, shc.sslContext.getSecureRandom(),
                nonceArr, newId.getId());
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                        "Produced NewSessionTicket handshake message", nstm);
            }

            // create and cache the new session
            // The new session must be a child of the existing session so
            // they will be invalidated together, etc.
            SSLSessionImpl sessionCopy = new SSLSessionImpl(shc,
                shc.handshakeSession.getSuite(), newId,
                shc.handshakeSession.getCreationTime());
            shc.handshakeSession.addChild(sessionCopy);
            sessionCopy.setPreSharedKey(psk);
            sessionCopy.setPskIdentity(newId.getId());
            sessionCopy.setTicketAgeAdd(nstm.ticketAgeAdd);
            sessionCache.put(sessionCopy);

            // Output the handshake message.
            nstm.write(shc.handshakeOutput);
            shc.handshakeOutput.flush();

            // The message has been delivered.
            return null;
        }
    }

    /**
     * The "NewSessionTicket" handshake message producer.
     */
    private static final class NewSessionTicketProducer
            implements HandshakeProducer {

        // Prevent instantiation of this class.
        private NewSessionTicketProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {

            // NSTM may be sent in response to handshake messages.
            // For example: key update

            throw new ProviderException(
                "NewSessionTicket handshake producer not implemented");
        }
    }



    private static final
            class NewSessionTicketConsumer implements SSLConsumer {
        // Prevent instantiation of this class.
        private NewSessionTicketConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                            ByteBuffer message) throws IOException {
            // The consuming happens in client side only.
            PostHandshakeContext hc = (PostHandshakeContext) context;
            NewSessionTicketMessage nstm = new NewSessionTicketMessage(hc, message);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Consuming NewSessionTicket message", nstm);
            }

            // discard tickets with timeout 0
            if (nstm.ticketLifetime <= 0 ||
                nstm.ticketLifetime > SEVEN_DAYS_IN_SECONDS) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "Discarding NewSessionTicket with lifetime "
                        + nstm.ticketLifetime, nstm);
                }
                return;
            }

            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                hc.sslContext.engineGetClientSessionContext();

            if (sessionCache.getSessionTimeout() > SEVEN_DAYS_IN_SECONDS) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "Session cache lifetime is too long. Discarding ticket.");
                }
                return;
            }

            SSLSessionImpl sessionToSave = hc.conContext.conSession;

            Optional<SecretKey> resumptionMasterSecret =
                sessionToSave.getResumptionMasterSecret();
            if (!resumptionMasterSecret.isPresent()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "Session has no resumption master secret. Ignoring ticket.");
                }
                return;
            }

            // derive the PSK
            SecretKey psk = derivePreSharedKey(
                sessionToSave.getSuite().hashAlg, resumptionMasterSecret.get(),
                nstm.ticketNonce);

            // create and cache the new session
            // The new session must be a child of the existing session so
            // they will be invalidated together, etc.
            SessionId newId =
                new SessionId(true, hc.sslContext.getSecureRandom());
            SSLSessionImpl sessionCopy =
                new SSLSessionImpl(hc, sessionToSave.getSuite(), newId,
                sessionToSave.getCreationTime());
            sessionToSave.addChild(sessionCopy);
            sessionCopy.setPreSharedKey(psk);
            sessionCopy.setTicketAgeAdd(nstm.ticketAgeAdd);
            sessionCopy.setPskIdentity(nstm.ticket);
            sessionCache.put(sessionCopy);

            // The handshakeContext is no longer needed
            hc.free();
        }
    }

}

