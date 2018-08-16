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
import java.security.AlgorithmConstraints;
import java.security.CryptoPrimitive;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.interfaces.XECPrivateKey;
import java.security.interfaces.XECPublicKey;
import java.security.spec.*;
import java.util.EnumSet;
import javax.crypto.KeyAgreement;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.net.ssl.SSLHandshakeException;
import sun.security.ssl.CipherSuite.HashAlg;
import sun.security.ssl.SupportedGroupsExtension.NamedGroup;
import sun.security.ssl.SupportedGroupsExtension.NamedGroupType;
import sun.security.ssl.SupportedGroupsExtension.SupportedGroups;
import sun.security.ssl.X509Authentication.X509Credentials;
import sun.security.ssl.X509Authentication.X509Possession;
import sun.security.util.ECUtil;

final class XDHKeyExchange {
    static final SSLPossessionGenerator poGenerator =
            new XDHEPossessionGenerator();
    static final SSLKeyAgreementGenerator xdheKAGenerator =
            new XDHEKAGenerator();
    static final SSLKeyAgreementGenerator xdhKAGenerator =
            new XDHKAGenerator();

    static final class XDHECredentials implements SSLKeyAgreementCredentials {
        final XECPublicKey popPublicKey;
        final NamedGroup namedGroup;

        XDHECredentials(XECPublicKey popPublicKey, NamedGroup namedGroup) {
            this.popPublicKey = popPublicKey;
            this.namedGroup = namedGroup;
        }

        @Override
        public PublicKey getPublicKey() {
            return popPublicKey;
        }

        static XDHECredentials valueOf(NamedGroup namedGroup,
            byte[] encodedPoint) throws IOException, GeneralSecurityException {

            if (namedGroup.type != NamedGroupType.NAMED_GROUP_XDH) {
                throw new RuntimeException(
                    "Credentials decoding:  Not XDH named group");
            }

            if (encodedPoint == null || encodedPoint.length == 0) {
                return null;
            }

            NamedParameterSpec namedSpec = new NamedParameterSpec(namedGroup.algorithm);
            XECPublicKeySpec xecKeySpec = ECUtil.decodeXecPublicKey(encodedPoint, namedSpec);
            KeyFactory factory = JsseJce.getKeyFactory(namedGroup.algorithm);

            XECPublicKey publicKey = (XECPublicKey)factory.generatePublic(xecKeySpec);
            return new XDHECredentials(publicKey, namedGroup);
        }
    }

    static final class XDHEPossession implements SSLPossession {
        final PrivateKey privateKey;
        final XECPublicKey publicKey;
        final NamedGroup namedGroup;

        XDHEPossession(NamedGroup namedGroup, SecureRandom random) {
            try {
                KeyPairGenerator kpg = JsseJce.getKeyPairGenerator(namedGroup.algorithm);
                AlgorithmParameterSpec params = namedGroup.getParameterSpec();
                kpg.initialize(params, random);
                KeyPair kp = kpg.generateKeyPair();
                privateKey = kp.getPrivate();
                publicKey = (XECPublicKey)kp.getPublic();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(
                    "Could not generate XDH keypair", e);
            }

            this.namedGroup = namedGroup;
        }

        XDHEPossession(XDHECredentials credentials, SecureRandom random) {
            AlgorithmParameterSpec params = credentials.popPublicKey.getParams();
            try {
                KeyPairGenerator kpg = JsseJce.getKeyPairGenerator(credentials.namedGroup.algorithm);
                kpg.initialize(params, random);
                KeyPair kp = kpg.generateKeyPair();
                privateKey = kp.getPrivate();
                publicKey = (XECPublicKey)kp.getPublic();
            } catch (GeneralSecurityException e) {
                throw new RuntimeException(
                    "Could not generate XDH keypair", e);
            }

            this.namedGroup = credentials.namedGroup;
        }

        @Override
        public byte[] encode() {
            try {
                return ECUtil.encodeXecPublicKey(publicKey.getU(), publicKey.getParams());
            } catch (InvalidParameterSpecException ex) {
                throw new RuntimeException(ex);
            }
        }

        // called by client handshaker
        SecretKey getAgreedSecret(
                PublicKey peerPublicKey) throws SSLHandshakeException {

            try {
                KeyAgreement ka = JsseJce.getKeyAgreement("XDH");
                ka.init(privateKey);
                ka.doPhase(peerPublicKey, true);
                return ka.generateSecret("TlsPremasterSecret");
            } catch (GeneralSecurityException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate secret").initCause(e);
            }
        }

        // called by ServerHandshaker
        SecretKey getAgreedSecret(
                byte[] encodedKey) throws SSLHandshakeException {
            try {
                AlgorithmParameterSpec params = publicKey.getParams();

                KeyFactory kf = JsseJce.getKeyFactory("XDH");
                XECPublicKeySpec spec = ECUtil.decodeXecPublicKey(encodedKey, params);
                PublicKey peerPublicKey = kf.generatePublic(spec);
                return getAgreedSecret(peerPublicKey);
            } catch (GeneralSecurityException | java.io.IOException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate secret").initCause(e);
            }
        }

        // Check constraints of the specified EC public key.
        void checkConstraints(AlgorithmConstraints constraints,
                byte[] encodedKey) throws SSLHandshakeException {
            try {

                AlgorithmParameterSpec params = publicKey.getParams();
                XECPublicKeySpec spec = ECUtil.decodeXecPublicKey(encodedKey, params);

                KeyFactory kf = JsseJce.getKeyFactory("XDH");
                PublicKey pubKey = kf.generatePublic(spec);

                // check constraints of ECPublicKey
                if (!constraints.permits(
                        EnumSet.of(CryptoPrimitive.KEY_AGREEMENT), pubKey)) {
                    throw new SSLHandshakeException(
                        "ECPublicKey does not comply to algorithm constraints");
                }
            } catch (GeneralSecurityException | java.io.IOException e) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                        "Could not generate ECPublicKey").initCause(e);
            }
        }
    }

    private static final
            class XDHEPossessionGenerator implements SSLPossessionGenerator {
        // Prevent instantiation of this class.
        private XDHEPossessionGenerator() {
            // blank
        }

        @Override
        public SSLPossession createPossession(HandshakeContext context) {
            NamedGroup preferableNamedGroup = null;
            if ((context.clientRequestedNamedGroups != null) &&
                    (!context.clientRequestedNamedGroups.isEmpty())) {
                preferableNamedGroup = SupportedGroups.getPreferredGroup(
                        context.negotiatedProtocol,
                        context.algorithmConstraints,
                        NamedGroupType.NAMED_GROUP_XDH,
                        context.clientRequestedNamedGroups);
            } else {
                preferableNamedGroup = SupportedGroups.getPreferredGroup(
                        context.negotiatedProtocol,
                        context.algorithmConstraints,
                        NamedGroupType.NAMED_GROUP_XDH);
            }

            if (preferableNamedGroup != null) {
                return new XDHEPossession(preferableNamedGroup,
                            context.sslContext.getSecureRandom());
            }

            // no match found, cannot use this cipher suite.
            //
            return null;
        }
    }

    private static final
            class XDHKAGenerator implements SSLKeyAgreementGenerator {
        // Prevent instantiation of this class.
        private XDHKAGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext context) throws IOException {
            if (context instanceof ServerHandshakeContext) {
                return createServerKeyDerivation(
                        (ServerHandshakeContext)context);
            } else {
                return createClientKeyDerivation(
                        (ClientHandshakeContext)context);
            }
        }

        private SSLKeyDerivation createServerKeyDerivation(
                ServerHandshakeContext shc) throws IOException {
            X509Possession x509Possession = null;
            XDHECredentials xdheCredentials = null;
            for (SSLPossession poss : shc.handshakePossessions) {
                if (!(poss instanceof X509Possession)) {
                    continue;
                }

                PrivateKey privateKey = ((X509Possession)poss).popPrivateKey;
                if (! (privateKey instanceof XECPrivateKey)) {
                    continue;
                }
                AlgorithmParameterSpec params = ((XECPrivateKey)privateKey).getParams();
                // group must be specified by name
                if (!(params instanceof NamedParameterSpec)) {
                    continue;
                }
                NamedParameterSpec namedParams = (NamedParameterSpec) params;
                NamedGroup ng = NamedGroup.valueOf(namedParams.getName());
                if (ng == null) {
                    // unlikely, have been checked during cipher suite negotiation.
                    shc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                        "Unsupported server cert for XDH key exchange");
                }

                for (SSLCredentials cred : shc.handshakeCredentials) {
                    if (!(cred instanceof XDHECredentials)) {
                        continue;
                    }
                    if (ng.equals(((XDHECredentials)cred).namedGroup)) {
                        xdheCredentials = (XDHECredentials)cred;
                        break;
                    }
                }

                if (xdheCredentials != null) {
                    x509Possession = (X509Possession)poss;
                    break;
                }
            }

            if (x509Possession == null || xdheCredentials == null) {
                shc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No sufficient XDHE key agreement parameters negotiated");
            }

            return new XDHEKAKeyDerivation(shc,
                x509Possession.popPrivateKey, xdheCredentials.popPublicKey);
        }

        private SSLKeyDerivation createClientKeyDerivation(
                ClientHandshakeContext chc) throws IOException {

            XDHEPossession xdhePossession = null;
            X509Credentials x509Credentials = null;
            for (SSLPossession poss : chc.handshakePossessions) {
                if (!(poss instanceof XDHEPossession)) {
                    continue;
                }

                NamedGroup ng = ((XDHEPossession)poss).namedGroup;
                for (SSLCredentials cred : chc.handshakeCredentials) {
                    if (!(cred instanceof X509Credentials)) {
                        continue;
                    }

                    PublicKey publicKey = ((X509Credentials)cred).popPublicKey;
                    if (!(publicKey instanceof XECPublicKey)) {
                        continue;
                    }
                    XECPublicKey xecPublicKey = (XECPublicKey) publicKey;
                    AlgorithmParameterSpec params = xecPublicKey.getParams();
                    // group must be specified by name
                    if (!(params instanceof NamedParameterSpec)) {
                        continue;
                    }
                    NamedParameterSpec namedParams = (NamedParameterSpec) params;

                    NamedGroup namedGroup = NamedGroup.valueOf(namedParams.getName());
                    if (namedGroup == null) {
                        // unlikely, should have been checked previously
                        chc.conContext.fatal(Alert.ILLEGAL_PARAMETER,
                            "Unsupported EC server cert for XDH key exchange");
                    }

                    if (ng.equals(namedGroup)) {
                        x509Credentials = (X509Credentials)cred;
                        break;
                    }
                }

                if (x509Credentials != null) {
                    xdhePossession = (XDHEPossession)poss;
                    break;
                }
            }

            if (xdhePossession == null || x509Credentials == null) {
                chc.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No sufficient XDH key agreement parameters negotiated");
            }

            return new XDHEKAKeyDerivation(chc,
                xdhePossession.privateKey, x509Credentials.popPublicKey);
        }
    }

    private static final
            class XDHEKAGenerator implements SSLKeyAgreementGenerator {
        // Prevent instantiation of this class.
        private XDHEKAGenerator() {
            // blank
        }

        @Override
        public SSLKeyDerivation createKeyDerivation(
                HandshakeContext context) throws IOException {
            XDHEPossession xdhePossession = null;
            XDHECredentials xdheCredentials = null;
            for (SSLPossession poss : context.handshakePossessions) {
                if (!(poss instanceof XDHEPossession)) {
                    continue;
                }

                NamedGroup ng = ((XDHEPossession)poss).namedGroup;
                for (SSLCredentials cred : context.handshakeCredentials) {
                    if (!(cred instanceof XDHECredentials)) {
                        continue;
                    }
                    if (ng.equals(((XDHECredentials)cred).namedGroup)) {
                        xdheCredentials = (XDHECredentials)cred;
                        break;
                    }
                }

                if (xdheCredentials != null) {
                    xdhePossession = (XDHEPossession)poss;
                    break;
                }
            }

            if (xdhePossession == null || xdheCredentials == null) {
                context.conContext.fatal(Alert.HANDSHAKE_FAILURE,
                    "No sufficient XDHE key agreement parameters negotiated");
            }

            return new XDHEKAKeyDerivation(context,
                xdhePossession.privateKey, xdheCredentials.popPublicKey);
        }
    }

    private static final
            class XDHEKAKeyDerivation implements SSLKeyDerivation {
        private final HandshakeContext context;
        private final PrivateKey localPrivateKey;
        private final PublicKey peerPublicKey;

        XDHEKAKeyDerivation(HandshakeContext context,
                PrivateKey localPrivateKey,
                PublicKey peerPublicKey) {
            this.context = context;
            this.localPrivateKey = localPrivateKey;
            this.peerPublicKey = peerPublicKey;
        }

        @Override
        public SecretKey deriveKey(String algorithm,
                AlgorithmParameterSpec params) throws IOException {
            if (!context.negotiatedProtocol.useTLS13PlusSpec()) {
                return t12DeriveKey(algorithm, params);
            } else {
                return t13DeriveKey(algorithm, params);
            }
        }

        private SecretKey t12DeriveKey(String algorithm,
                AlgorithmParameterSpec params) throws IOException {
            try {
                KeyAgreement ka = JsseJce.getKeyAgreement("XDH");
                ka.init(localPrivateKey);
                ka.doPhase(peerPublicKey, true);
                SecretKey preMasterSecret =
                        ka.generateSecret("TlsPremasterSecret");

                SSLMasterKeyDerivation mskd =
                        SSLMasterKeyDerivation.valueOf(
                                context.negotiatedProtocol);
                SSLKeyDerivation kd = mskd.createKeyDerivation(
                        context, preMasterSecret);
                return kd.deriveKey("TODO", params);
            } catch (GeneralSecurityException gse) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate secret").initCause(gse);
            }
        }

        private SecretKey t13DeriveKey(String algorithm,
                AlgorithmParameterSpec params) throws IOException {
            try {
                KeyAgreement ka = JsseJce.getKeyAgreement("XDH");
                ka.init(localPrivateKey);
                ka.doPhase(peerPublicKey, true);
                SecretKey sharedSecret =
                        ka.generateSecret("TlsPremasterSecret");

                HashAlg hashAlg = context.negotiatedCipherSuite.hashAlg;
                SSLKeyDerivation kd = context.handshakeKeyDerivation;
                HKDF hkdf = new HKDF(hashAlg.name);
                if (kd == null) {   // No PSK is in use.
                    // If PSK is not in use Early Secret will still be
                    // HKDF-Extract(0, 0).
                    byte[] zeros = new byte[hashAlg.hashLength];
                    SecretKeySpec ikm =
                            new SecretKeySpec(zeros, "TlsPreSharedSecret");
                    SecretKey earlySecret =
                            hkdf.extract(zeros, ikm, "TlsEarlySecret");
                    kd = new SSLSecretDerivation(context, earlySecret);
                }

                // derive salt secret
                SecretKey saltSecret = kd.deriveKey("TlsSaltSecret", null);

                // derive handshake secret
                return hkdf.extract(saltSecret, sharedSecret, algorithm);
            } catch (GeneralSecurityException gse) {
                throw (SSLHandshakeException) new SSLHandshakeException(
                    "Could not generate secret").initCause(gse);
            }
        }
    }
}
