package sun.security.ssl;

import java.security.PublicKey;

interface SSLKeyAgreementCredentials extends SSLCredentials {

    PublicKey getPublicKey();
}
