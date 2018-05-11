/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.security.*;
import java.text.MessageFormat;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import sun.security.ssl.SSLExtension.ExtensionConsumer;

import sun.security.ssl.SSLExtension.SSLExtensionSpec;
import sun.security.ssl.SSLHandshake.HandshakeMessage;

import javax.crypto.Mac;
import javax.crypto.SecretKey;

import static sun.security.ssl.SSLExtension.*;

/**
 * Pack of the "pre_shared_key" extension.
 */
final class PreSharedKeyExtension {
    static final HandshakeProducer chNetworkProducer =
            new CHPreSharedKeyProducer();
    static final ExtensionConsumer chOnLoadConsumer =
            new CHPreSharedKeyConsumer();
    static final HandshakeAbsence chOnLoadAbsence =
            new CHPreSharedKeyAbsence();
    static final HandshakeConsumer chOnTradeConsumer=
            new CHPreSharedKeyUpdate();

    static final HandshakeProducer shNetworkProducer =
            new SHPreSharedKeyProducer();
    static final ExtensionConsumer shOnLoadConsumer =
            new SHPreSharedKeyConsumer();
    static final HandshakeAbsence shOnLoadAbsence =
            new SHPreSharedKeyAbsence();

    static final class PskIdentity {
        final byte[] identity;
        final int obfuscatedAge;

        public PskIdentity(byte[] identity, int obfuscatedAge) {
            this.identity = identity;
            this.obfuscatedAge = obfuscatedAge;
        }

        public PskIdentity(ByteBuffer m)
            throws IllegalParameterException, IOException {

            identity = Record.getBytes16(m);
            if (identity.length == 0) {
                throw new IllegalParameterException("identity has length 0");
            }
            obfuscatedAge = Record.getInt32(m);
        }

        int getEncodedLength() {
            return 2 + identity.length + 4;
        }

        public void writeEncoded(ByteBuffer m) throws IOException {
            Record.putBytes16(m, identity);
            Record.putInt32(m, obfuscatedAge);
        }
        @Override
        public String toString() {
            return "{" + Utilities.toHexString(identity) + "," +
                obfuscatedAge + "}";
        }
    }

    static final class CHPreSharedKeySpec implements SSLExtensionSpec {
        final List<PskIdentity> identities;
        final List<byte[]> binders;

        CHPreSharedKeySpec(List<PskIdentity> identities, List<byte[]> binders) {
            this.identities = identities;
            this.binders = binders;
        }

        CHPreSharedKeySpec(ByteBuffer m)
            throws IllegalParameterException, IOException {

            identities = new ArrayList<>();
            int idEncodedLength = Record.getInt16(m);
            int idReadLength = 0;
            while (idReadLength < idEncodedLength) {
                PskIdentity id = new PskIdentity(m);
                identities.add(id);
                idReadLength += id.getEncodedLength();
            }

            binders = new ArrayList<>();
            int bindersEncodedLength = Record.getInt16(m);
            int bindersReadLength = 0;
            while (bindersReadLength < bindersEncodedLength) {
                byte[] binder = Record.getBytes8(m);
                if (binder.length < 32) {
                    throw new IllegalParameterException(
                        "binder has length < 32");
                }
                binders.add(binder);
                bindersReadLength += 1 + binder.length;
            }
        }

        int getIdsEncodedLength() {
            int idEncodedLength = 0;
            for(PskIdentity curId : identities) {
                idEncodedLength += curId.getEncodedLength();
            }
            return idEncodedLength;
        }

        int getBindersEncodedLength() {
            return getBindersEncodedLength(binders);
        }
        static int getBindersEncodedLength(Iterable<byte[]> binders) {
            int binderEncodedLength = 0;
            for (byte[] curBinder : binders) {
                binderEncodedLength += 1 + curBinder.length;
            }
            return binderEncodedLength;
        }

        byte[] getEncoded() throws IOException {

            int idsEncodedLength = getIdsEncodedLength();
            int bindersEncodedLength = getBindersEncodedLength();
            int encodedLength = 4 + idsEncodedLength + bindersEncodedLength;
            byte[] buffer = new byte[encodedLength];
            ByteBuffer m = ByteBuffer.wrap(buffer);
            Record.putInt16(m, idsEncodedLength);
            for(PskIdentity curId : identities) {
                curId.writeEncoded(m);
            }
            Record.putInt16(m, bindersEncodedLength);
            for (byte[] curBinder : binders) {
                Record.putBytes8(m, curBinder);
            }

            return buffer;
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"PreSharedKey\": '{'\n" +
                "  \"identities\"      : \"{0}\",\n" +
                "  \"binders\"       : \"{1}\",\n" +
                "'}'",
                Locale.ENGLISH);

            Object[] messageFields = {
                Utilities.indent(identitiesString()),
                Utilities.indent(bindersString())
            };

            return messageFormat.format(messageFields);
        }

        String identitiesString() {
            StringBuilder result = new StringBuilder();
            for(PskIdentity curId : identities) {
                result.append(curId.toString() + "\n");
            }

            return result.toString();
        }

        String bindersString() {
            StringBuilder result = new StringBuilder();
            for(byte[] curBinder : binders) {
                result.append("{" + Utilities.toHexString(curBinder) + "}\n");
            }

            return result.toString();
        }
    }

    static final class SHPreSharedKeySpec implements SSLExtensionSpec {
        final int selectedIdentity;

        SHPreSharedKeySpec(int selectedIdentity) {
            this.selectedIdentity = selectedIdentity;
        }

        SHPreSharedKeySpec(ByteBuffer m) throws IOException {
            this.selectedIdentity = Record.getInt16(m);
        }

        byte[] getEncoded() throws IOException {

            byte[] buffer = new byte[2];
            ByteBuffer m = ByteBuffer.wrap(buffer);
            Record.putInt16(m, selectedIdentity);

            return buffer;
        }

        @Override
        public String toString() {
            MessageFormat messageFormat = new MessageFormat(
                "\"PreSharedKey\": '{'\n" +
                "  \"selected_identity\"      : \"{0}\",\n" +
                "'}'",
                Locale.ENGLISH);

            Object[] messageFields = {
                selectedIdentity
            };

            return messageFormat.format(messageFields);
        }

    }


    private static class IllegalParameterException extends Exception {

        private static final long serialVersionUID = 0;

        private final String message;

        private IllegalParameterException(String message) {
            this.message = message;
        }
    }

    private static final class CHPreSharedKeyConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private CHPreSharedKeyConsumer() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                            HandshakeMessage message,
                            ByteBuffer buffer) throws IOException {

            ServerHandshakeContext shc = (ServerHandshakeContext) message.handshakeContext;
            // Is it a supported and enabled extension?
            if (!shc.sslConfig.isAvailable(SSLExtension.CH_PRE_SHARED_KEY)) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "Ignore unavailable pre_shared_key extension");
                }
                return;     // ignore the extension
            }

            CHPreSharedKeySpec pskSpec = null;
            try {
                pskSpec = new CHPreSharedKeySpec(buffer);
            } catch (IOException ioe) {
                shc.conContext.fatal(Alert.UNEXPECTED_MESSAGE, ioe);
                return;     // fatal() always throws, make the compiler happy.
            } catch (IllegalParameterException ex) {
                shc.conContext.fatal(Alert.ILLEGAL_PARAMETER, ex.message);
            }

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Received PSK extension: ", pskSpec);
            }

            if (shc.pskKeyExchangeModes.isEmpty()) {
                shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                "Client sent PSK but does not support PSK modes");
            }

            // error if id and binder lists are not the same length
            if (pskSpec.identities.size() != pskSpec.binders.size()) {
                shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                "PSK extension has incorrect number of binders");
            }

            shc.handshakeExtensions.put(SSLExtension.CH_PRE_SHARED_KEY, pskSpec);

            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
            message.handshakeContext.sslContext.engineGetServerSessionContext();

            // The session to resume will be decided below.
            // It could have been set by previous actions (e.g. PSK received
            // earlier), and it must be recalculated.
            shc.isResumption = false;
            shc.resumingSession = null;

            int idIndex = 0;
            for (PskIdentity requestedId : pskSpec.identities) {
                SSLSessionImpl s = sessionCache.get(requestedId.identity);
                if (s != null && s.getPreSharedKey().isPresent()) {
                    resumeSession(shc, s, idIndex);
                    break;
                }

                ++idIndex;
            }
        }
    }

    private static final class CHPreSharedKeyUpdate implements HandshakeConsumer {
        // Prevent instantiation of this class.
        private CHPreSharedKeyUpdate() {
            // blank
        }

        @Override
        public void consume(ConnectionContext context,
                            HandshakeMessage message) throws IOException {

            ServerHandshakeContext shc = (ServerHandshakeContext) message.handshakeContext;

            if (!shc.isResumption || shc.resumingSession == null) {
                // not resuming---nothing to do
                return;
            }

            CHPreSharedKeySpec chPsk = (CHPreSharedKeySpec)shc.handshakeExtensions.get(SSLExtension.CH_PRE_SHARED_KEY);
            SHPreSharedKeySpec shPsk = (SHPreSharedKeySpec)shc.handshakeExtensions.get(SSLExtension.SH_PRE_SHARED_KEY);

            if (chPsk == null || shPsk == null) {
                shc.conContext.fatal(Alert.INTERNAL_ERROR,
                "Required extensions are unavailable");
            }

            byte[] binder = chPsk.binders.get(shPsk.selectedIdentity);

            // set up PSK binder hash
            HandshakeHash pskBinderHash = shc.handshakeHash.copy();
            byte[] lastMessage = pskBinderHash.removeLastReceived();
            ByteBuffer messageBuf = ByteBuffer.wrap(lastMessage);
            // skip the type and length
            messageBuf.position(4);
            // read to find the beginning of the binders
            ClientHello.ClientHelloMessage.readPartial(shc.conContext, messageBuf);
            int length = messageBuf.position();
            messageBuf.position(0);
            pskBinderHash.receive(messageBuf, length);

            checkBinder(shc, shc.resumingSession, pskBinderHash, binder);

            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                message.handshakeContext.sslContext.engineGetServerSessionContext();
        }
    }

    private static void resumeSession(ServerHandshakeContext shc,
                                      SSLSessionImpl session,
                                      int index)
        throws IOException {

        if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
            SSLLogger.fine(
            "Resuming session: ", session);
        }

        // binder will be checked later

        shc.isResumption = true;
        shc.resumingSession = session;

        SHPreSharedKeySpec pskMsg = new SHPreSharedKeySpec(index);
        shc.handshakeExtensions.put(SH_PRE_SHARED_KEY, pskMsg);
    }

    private static void checkBinder(ServerHandshakeContext shc, SSLSessionImpl session,
                                    HandshakeHash pskBinderHash, byte[] binder) throws IOException {

        Optional<SecretKey> pskOpt = session.getPreSharedKey();
        if (!pskOpt.isPresent()) {
            shc.conContext.fatal(Alert.INTERNAL_ERROR,
            "Session has no PSK");
        }
        SecretKey psk = pskOpt.get();

        SecretKey binderKey = deriveBinderKey(psk, session);
        byte[] computedBinder = computeBinder(binderKey, session, pskBinderHash);
        if (!Arrays.equals(binder, computedBinder)) {
            shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
            "Incorect PSK binder value");
        }
    }

    // Class that produces partial messages used to compute binder hash
    static final class PartialClientHelloMessage extends HandshakeMessage {

        private final ClientHello.ClientHelloMessage msg;
        private final CHPreSharedKeySpec psk;

        PartialClientHelloMessage(HandshakeContext ctx,
                                  ClientHello.ClientHelloMessage msg,
                                  CHPreSharedKeySpec psk) {
            super(ctx);

            this.msg = msg;
            this.psk = psk;
        }

        @Override
        SSLHandshake handshakeType() {
            return msg.handshakeType();
        }

        private int pskTotalLength() {
            return psk.getIdsEncodedLength() +
                psk.getBindersEncodedLength() + 8;
        }

        @Override
        int messageLength() {

            if (msg.extensions.get(SSLExtension.CH_PRE_SHARED_KEY) != null) {
                return msg.messageLength();
            } else {
                return msg.messageLength() + pskTotalLength();
            }
        }

        @Override
        void send(HandshakeOutStream hos) throws IOException {
            msg.sendCore(hos);

            // complete extensions
            int extsLen = msg.extensions.length();
            if (msg.extensions.get(SSLExtension.CH_PRE_SHARED_KEY) == null) {
                extsLen += pskTotalLength();
            }
            hos.putInt16(extsLen - 2);
            // write the complete extensions
            for (SSLExtension ext : SSLExtension.values()) {
                byte[] extData = msg.extensions.get(ext);
                if (extData == null) {
                    continue;
                }
                // the PSK could be there from an earlier round
                if (ext == SSLExtension.CH_PRE_SHARED_KEY) {
                    continue;
                }
                System.err.println("partial CH extension: " + ext.name());
                int extID = ext.id;
                hos.putInt16(extID);
                hos.putBytes16(extData);
            }

            // partial PSK extension
            int extID = SSLExtension.CH_PRE_SHARED_KEY.id;
            hos.putInt16(extID);
            byte[] encodedPsk = psk.getEncoded();
            hos.putInt16(encodedPsk.length);
            hos.write(encodedPsk, 0, psk.getIdsEncodedLength() + 2);
        }
    }

    private static final class CHPreSharedKeyProducer implements HandshakeProducer {

        // Prevent instantiation of this class.
        private CHPreSharedKeyProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {

            // The producing happens in client side only.
            ClientHandshakeContext chc = (ClientHandshakeContext)context;
            if (!chc.isResumption || chc.resumingSession == null) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "No session to resume.");
                }
                return null;
            }

            Optional<SecretKey> pskOpt = chc.resumingSession.getPreSharedKey();
            if (!pskOpt.isPresent()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "Existing session has no PSK.");
                }
                return null;
            }
            SecretKey psk = pskOpt.get();
            Optional<byte[]> pskIdOpt = chc.resumingSession.getPskIdentity();
            if (!pskIdOpt.isPresent()) {
                if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                    SSLLogger.fine(
                    "PSK has no identity, or identity was already used");
                }
                return null;
            }
            byte[] pskId = pskIdOpt.get();

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Found resumable session. Preparing PSK message.");
            }

            List<PskIdentity> identities = new ArrayList<>();
            int ageMillis = (int)(System.currentTimeMillis() - chc.resumingSession.getTicketCreationTime());
            int obfuscatedAge = ageMillis + chc.resumingSession.getTicketAgeAdd();
            identities.add(new PskIdentity(pskId, obfuscatedAge));

            SecretKey binderKey = deriveBinderKey(psk, chc.resumingSession);
            ClientHello.ClientHelloMessage clientHello = (ClientHello.ClientHelloMessage) message;
            CHPreSharedKeySpec pskPrototype = createPskPrototype(chc.resumingSession.getSuite().hashAlg.hashLength, identities);
            HandshakeHash pskBinderHash = chc.handshakeHash.copy();

            byte[] binder = computeBinder(binderKey, pskBinderHash, chc.resumingSession, chc, clientHello, pskPrototype);

            List<byte[]> binders = new ArrayList<>();
            binders.add(binder);

            CHPreSharedKeySpec pskMessage = new CHPreSharedKeySpec(identities, binders);
            chc.handshakeExtensions.put(CH_PRE_SHARED_KEY, pskMessage);
            return pskMessage.getEncoded();
        }

        private CHPreSharedKeySpec createPskPrototype(int hashLength, List<PskIdentity> identities) {
            List<byte[]> binders = new ArrayList<>();
            byte[] binderProto = new byte[hashLength];
            for (PskIdentity curId : identities) {
                binders.add(binderProto);
            }

            return new CHPreSharedKeySpec(identities, binders);
        }
    }

    private static byte[] computeBinder(SecretKey binderKey, SSLSessionImpl session, HandshakeHash pskBinderHash) throws IOException {

        pskBinderHash.determine(session.getProtocolVersion(), session.getSuite());
        pskBinderHash.update();
        byte[] digest = pskBinderHash.digest();

        return computeBinder(binderKey, session, digest);
    }

    private static byte[] computeBinder(SecretKey binderKey, HandshakeHash hash, SSLSessionImpl session,
                                        HandshakeContext ctx, ClientHello.ClientHelloMessage hello,
                                        CHPreSharedKeySpec pskPrototype) throws IOException {

        PartialClientHelloMessage partialMsg = new PartialClientHelloMessage(ctx, hello, pskPrototype);

        SSLEngineOutputRecord record = new SSLEngineOutputRecord(hash);
        HandshakeOutStream hos = new HandshakeOutStream(record);
        partialMsg.write(hos);

        hash.determine(session.getProtocolVersion(), session.getSuite());
        hash.update();
        byte[] digest = hash.digest();

        return computeBinder(binderKey, session, digest);
    }

    private static byte[] computeBinder(SecretKey binderKey, SSLSessionImpl session,
                                        byte[] digest) throws IOException {

        try {
            CipherSuite.HashAlg hashAlg = session.getSuite().hashAlg;
            HKDF hkdf = new HKDF(hashAlg.name);
            byte[] label = ("tls13 finished").getBytes();
            byte[] hkdfInfo = SSLSecretDerivation.createHkdfInfo(label, new byte[0], hashAlg.hashLength);
            SecretKey finishedKey = hkdf.expand(binderKey, hkdfInfo, hashAlg.hashLength, "TlsBinderKey");

            String hmacAlg =
                "Hmac" + hashAlg.name.replace("-", "");
            try {
                Mac hmac = JsseJce.getMac(hmacAlg);
                hmac.init(finishedKey);
                return hmac.doFinal(digest);
            } catch (NoSuchAlgorithmException | InvalidKeyException ex) {
                throw new IOException(ex);
            }
        } catch(GeneralSecurityException ex) {
            throw new IOException(ex);
        }
    }

    private static SecretKey deriveBinderKey(SecretKey psk,
                                             SSLSessionImpl session)
        throws IOException {

        try {
            CipherSuite.HashAlg hashAlg = session.getSuite().hashAlg;
            HKDF hkdf = new HKDF(hashAlg.name);
            byte[] zeros = new byte[hashAlg.hashLength];
            SecretKey earlySecret = hkdf.extract(zeros, psk, "TlsEarlySecret");

            byte[] label = ("tls13 res binder").getBytes();
            MessageDigest md = MessageDigest.getInstance(hashAlg.toString());;
            byte[] hkdfInfo = SSLSecretDerivation.createHkdfInfo(
                label, md.digest(new byte[0]), hashAlg.hashLength);
            return hkdf.expand(earlySecret, hkdfInfo, hashAlg.hashLength,
                "TlsBinderKey");

        } catch (GeneralSecurityException ex) {
            throw new IOException(ex);
        }
    }

    private static final class CHPreSharedKeyAbsence implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                           HandshakeMessage message) throws IOException {

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Handling pre_shared_key absence.");
            }

            ServerHandshakeContext shc = (ServerHandshakeContext)context;

            // Resumption is only determined by PSK, when enabled
            shc.resumingSession = null;
            shc.isResumption = false;
        }
    }

    private static final class SHPreSharedKeyConsumer implements ExtensionConsumer {
        // Prevent instantiation of this class.
        private SHPreSharedKeyConsumer() {

        }

        @Override
        public void consume(ConnectionContext context,
                HandshakeMessage message, ByteBuffer buffer) throws IOException {

            ClientHandshakeContext chc = (ClientHandshakeContext) message.handshakeContext;

            SHPreSharedKeySpec shPsk = new SHPreSharedKeySpec(buffer);
            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Received pre_shared_key extension: ", shPsk);
            }

            if (!chc.handshakeExtensions.containsKey(SSLExtension.CH_PRE_SHARED_KEY)) {
                chc.conContext.fatal(Alert.UNEXPECTED_MESSAGE,
                "Server sent unexpected pre_shared_key extension");
            }

            // The PSK identity should not be reused, even if it is
            // not selected.
            chc.resumingSession.consumePskIdentity();

            if (shPsk.selectedIdentity != 0) {
                chc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                "Selected identity index is not in correct range.");
            }

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Resuming session: ", chc.resumingSession);
            }

            // remove the session from the cache
            SSLSessionContextImpl sessionCache = (SSLSessionContextImpl)
                chc.sslContext.engineGetClientSessionContext();
            sessionCache.remove(chc.resumingSession.getSessionId());
        }
    }

    private static final class SHPreSharedKeyAbsence implements HandshakeAbsence {
        @Override
        public void absent(ConnectionContext context,
                           HandshakeMessage message) throws IOException {

            if (SSLLogger.isOn && SSLLogger.isOn("ssl,handshake")) {
                SSLLogger.fine(
                "Handling pre_shared_key absence.");
            }

            ClientHandshakeContext chc = (ClientHandshakeContext)context;

            if (!chc.handshakeExtensions.containsKey(SSLExtension.CH_PRE_SHARED_KEY)) {
                // absence is expected---nothing to do
                return;
            }

            // The PSK identity should not be reused, even if it is
            // not selected.
            chc.resumingSession.consumePskIdentity();

            // If the client requested to resume, the server refused
            chc.resumingSession = null;
            chc.isResumption = false;
        }
    }

    private static final class SHPreSharedKeyProducer implements HandshakeProducer {

        // Prevent instantiation of this class.
        private SHPreSharedKeyProducer() {
            // blank
        }

        @Override
        public byte[] produce(ConnectionContext context,
                HandshakeMessage message) throws IOException {

            ServerHandshakeContext shc = (ServerHandshakeContext)
                message.handshakeContext;
            SHPreSharedKeySpec psk = (SHPreSharedKeySpec)
                shc.handshakeExtensions.get(SH_PRE_SHARED_KEY);
            if (psk == null) {
                return null;
            }

            return psk.getEncoded();
        }
    }
}
