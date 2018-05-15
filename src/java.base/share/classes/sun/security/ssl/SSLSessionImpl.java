/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.math.BigInteger;
import java.net.InetAddress;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Queue;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;
import java.util.Optional;
import java.util.concurrent.ConcurrentLinkedQueue;
import javax.crypto.SecretKey;
import javax.net.ssl.ExtendedSSLSession;
import javax.net.ssl.SNIServerName;
import javax.net.ssl.SSLPeerUnverifiedException;
import javax.net.ssl.SSLPermission;
import javax.net.ssl.SSLSessionBindingEvent;
import javax.net.ssl.SSLSessionBindingListener;
import javax.net.ssl.SSLSessionContext;

/**
 * Implements the SSL session interface, and exposes the session context
 * which is maintained by SSL servers.
 *
 * <P> Servers have the ability to manage the sessions associated with
 * their authentication context(s).  They can do this by enumerating the
 * IDs of the sessions which are cached, examining those sessions, and then
 * perhaps invalidating a given session so that it can't be used again.
 * If servers do not explicitly manage the cache, sessions will linger
 * until memory is low enough that the runtime environment purges cache
 * entries automatically to reclaim space.
 *
 * <P><em> The only reason this class is not package-private is that
 * there's no other public way to get at the server session context which
 * is associated with any given authentication context. </em>
 *
 * @author David Brownell
 */
final class SSLSessionImpl extends ExtendedSSLSession {

    /*
     * we only really need a single null session
     */
    static final SSLSessionImpl         nullSession = new SSLSessionImpl();

    // compression methods
    private static final byte           compression_null = 0;

    /*
     * The state of a single session, as described in section 7.1
     * of the SSLv3 spec.
     */
    private final ProtocolVersion       protocolVersion;
    private final SessionId             sessionId;
    private X509Certificate[]   peerCerts;
    private byte                compressionMethod;
    private CipherSuite         cipherSuite;
    private SecretKey           masterSecret;
    final boolean               useExtendedMasterSecret;

    /*
     * Information not part of the SSLv3 protocol spec, but used
     * to support session management policies.
     */
    private final long          creationTime;
    private long                lastUsedTime = 0;
    private final String        host;
    private final int           port;
    private SSLSessionContextImpl       context;
    private int                 sessionCount;
    private boolean             invalidated;
    private X509Certificate[]   localCerts;
    private PrivateKey          localPrivateKey;
    private final String[]      localSupportedSignAlgs;
    private String[]            peerSupportedSignAlgs;
    private List<byte[]>        statusResponses;
    private SecretKey           resumptionMasterSecret;
    private SecretKey           preSharedKey;
    private byte[]              pskIdentity;
    private final long          ticketCreationTime = System.currentTimeMillis();
    private int                 ticketAgeAdd;

    private int                 negotiatedMaxFragLen;
    private int                 maximumPacketSize;

    // Principals for non-certificate based cipher suites
    private Principal peerPrincipal;
    private Principal localPrincipal;

    private Queue<SSLSessionImpl> childSessions = new ConcurrentLinkedQueue<SSLSessionImpl>();

    /*
     * Is the session currently re-established with a session-resumption
     * abbreviated initial handshake?
     *
     * Note that currently we only set this variable in client side.
     */
    private boolean isSessionResumption = false;

    /*
     * We count session creations, eventually for statistical data but
     * also since counters make shorter debugging IDs than the big ones
     * we use in the protocol for uniqueness-over-time.
     */
    private static volatile int counter;

    /*
     * Use of session caches is globally enabled/disabled.
     */
    private static boolean      defaultRejoinable = true;

    // server name indication
    final SNIServerName         serverNameIndication;
    private final List<SNIServerName>    requestedServerNames;

    // Counter used to create unique nonces in NewSessionTicket
    private BigInteger ticketNonceCounter = BigInteger.ONE;

    /*
     * Create a new non-rejoinable session, using the default (null)
     * cipher spec.  This constructor returns a session which could
     * be used either by a client or by a server, as a connection is
     * first opened and before handshaking begins.
     */
    private SSLSessionImpl() {
        this.protocolVersion = ProtocolVersion.NONE;
        this.cipherSuite = CipherSuite.C_NULL;
        this.sessionId = new SessionId(false, null);
        this.host = null;
        this.port = -1;
        this.localSupportedSignAlgs = new String[0];
        this.serverNameIndication = null;
        this.requestedServerNames = Collections.<SNIServerName>emptyList();
        this.useExtendedMasterSecret = false;
        this.creationTime = System.currentTimeMillis();
    }

    /*
     * Create a new session, using a given cipher spec.  This will
     * be rejoinable if session caching is enabled; the constructor
     * is intended mostly for use by serves.
     */
    SSLSessionImpl(HandshakeContext hc, CipherSuite cipherSuite) {
        this(hc, cipherSuite,
            new SessionId(defaultRejoinable, hc.sslContext.getSecureRandom()));
    }

    /*
     * Record a new session, using a given cipher spec and session ID.
     */
    SSLSessionImpl(HandshakeContext hc, CipherSuite cipherSuite, SessionId id) {
        this(hc, cipherSuite, id, System.currentTimeMillis());
    }

    /*
     * Record a new session, using a given cipher spec, session ID,
     * and creation time
     */
    SSLSessionImpl(HandshakeContext hc, CipherSuite cipherSuite, SessionId id, long creationTime) {
        this.creationTime = creationTime;
        this.protocolVersion = hc.negotiatedProtocol;
        this.sessionId = id;
        peerCerts = null;
        compressionMethod = compression_null;
        this.cipherSuite = cipherSuite;
        masterSecret = null;
        this.host = hc.conContext.transport.getPeerHost();
        this.port = hc.conContext.transport.getPeerPort();
        sessionCount = ++counter;
        this.localSupportedSignAlgs =
            SignatureScheme.getAlgorithmNames(hc.localSupportedSignAlgs);
        negotiatedMaxFragLen = -1;
        statusResponses = null;
        this.requestedServerNames =
                Collections.unmodifiableList(hc.requestedServerNames);
        this.serverNameIndication = hc.negotiatedServerName;
        if (hc.sslConfig.isClientMode) {
            this.useExtendedMasterSecret =
                (hc.handshakeExtensions.get(
                        SSLExtension.CH_EXTENDED_MASTER_SECRET) != null) &&
                (hc.handshakeExtensions.get(
                        SSLExtension.SH_EXTENDED_MASTER_SECRET) != null);
        } else {
            this.useExtendedMasterSecret =
                (hc.handshakeExtensions.get(
                        SSLExtension.CH_EXTENDED_MASTER_SECRET) != null) &&
                (!hc.negotiatedProtocol.useTLS13PlusSpec());
        }

        if (SSLLogger.isOn && SSLLogger.isOn("session")) {
             SSLLogger.finest("Session initialized:  " + this);
        }
    }

    void setMasterSecret(SecretKey secret) {
        if (masterSecret == null) {
            masterSecret = secret;
        } else {
            throw new RuntimeException("setMasterSecret() error");
        }
    }

    void setResumptionMasterSecret(SecretKey secret) {
        if (resumptionMasterSecret == null) {
            resumptionMasterSecret = secret;
        } else {
            throw new RuntimeException("setResumptionMasterSecret() error");
        }
    }

    void setPreSharedKey(SecretKey key) {
        if (preSharedKey == null) {
            preSharedKey = key;
        } else {
            throw new RuntimeException("setPreSharedKey() error");
        }
    }

    void addChild(SSLSessionImpl session) {
        childSessions.add(session);
    }

    void setTicketAgeAdd(int ticketAgeAdd) {
        this.ticketAgeAdd = ticketAgeAdd;
    }

    void setPskIdentity(byte[] pskIdentity) {
        if (this.pskIdentity == null) {
            this.pskIdentity = pskIdentity;
        } else {
            throw new RuntimeException("setPskIdentity() error");
        }
    }

    BigInteger incrTicketNonceCounter() {
        BigInteger result = ticketNonceCounter;
        ticketNonceCounter = ticketNonceCounter.add(BigInteger.valueOf(1));
        return result;
    }

    /**
     * Returns the master secret ... treat with extreme caution!
     */
    SecretKey getMasterSecret() {
        return masterSecret;
    }

    Optional<SecretKey> getResumptionMasterSecret() {
        return Optional.ofNullable(resumptionMasterSecret);
    }

    synchronized Optional<SecretKey> getPreSharedKey() {
        return Optional.ofNullable(preSharedKey);
    }

    synchronized Optional<SecretKey> consumePreSharedKey() {
        Optional<SecretKey> result = Optional.ofNullable(preSharedKey);
        preSharedKey = null;
        return result;
    }

    int getTicketAgeAdd() {
        return ticketAgeAdd;
    }

    /*
     * Get the PSK identity. Take care not to use it in multiple connections.
     */
    synchronized Optional<byte[]> getPskIdentity() {
        return Optional.ofNullable(pskIdentity);
    }

    /* PSK identities created from new_session_ticket messages should only
     * be used once. This method will return the identity and then clear it
     * so it cannot be used again.
     */
    synchronized Optional<byte[]> consumePskIdentity() {
        Optional<byte[]> result = Optional.ofNullable(pskIdentity);
        pskIdentity = null;
        return result;
    }

    void setPeerCertificates(X509Certificate[] peer) {
        if (peerCerts == null) {
            peerCerts = peer;
        }
    }

    void setLocalCertificates(X509Certificate[] local) {
        localCerts = local;
    }

    void setLocalPrivateKey(PrivateKey privateKey) {
        localPrivateKey = privateKey;
    }

    void setPeerSupportedSignatureAlgorithms(
            Collection<SignatureScheme> signatureSchemes) {
        peerSupportedSignAlgs =
            SignatureScheme.getAlgorithmNames(signatureSchemes);
    }

    /**
     * Provide status response data obtained during the SSL handshake.
     *
     * @param responses a {@link List} of responses in binary form.
     */
    void setStatusResponses(List<byte[]> responses) {
        if (responses != null && !responses.isEmpty()) {
            statusResponses = responses;
        } else {
            statusResponses = Collections.emptyList();
        }
    }

    /**
     * Set the peer principal.
     */
    void setPeerPrincipal(Principal principal) {
        if (peerPrincipal == null) {
            peerPrincipal = principal;
        }
    }

    /**
     * Set the local principal.
     */
    void setLocalPrincipal(Principal principal) {
        localPrincipal = principal;
    }

    /**
     * Returns true iff this session may be resumed ... sessions are
     * usually resumable.  Security policies may suggest otherwise,
     * for example sessions that haven't been used for a while (say,
     * a working day) won't be resumable, and sessions might have a
     * maximum lifetime in any case.
     */
    boolean isRejoinable() {
        return sessionId != null && sessionId.length() != 0 &&
            !invalidated && isLocalAuthenticationValid();
    }

    @Override
    public synchronized boolean isValid() {
        return isRejoinable();
    }

    /**
     * Check if the authentication used when establishing this session
     * is still valid. Returns true if no authentication was used
     */
    private boolean isLocalAuthenticationValid() {
        if (localPrivateKey != null) {
            try {
                // if the private key is no longer valid, getAlgorithm()
                // should throw an exception
                // (e.g. Smartcard has been removed from the reader)
                localPrivateKey.getAlgorithm();
            } catch (Exception e) {
                invalidate();
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the ID for this session.  The ID is fixed for the
     * duration of the session; neither it, nor its value, changes.
     */
    @Override
    public byte[] getId() {
        return sessionId.getId();
    }

    /**
     * For server sessions, this returns the set of sessions which
     * are currently valid in this process.  For client sessions,
     * this returns null.
     */
    @Override
    public SSLSessionContext getSessionContext() {
        /*
         * An interim security policy until we can do something
         * more specific in 1.2. Only allow trusted code (code which
         * can set system properties) to get an
         * SSLSessionContext. This is to limit the ability of code to
         * look up specific sessions or enumerate over them. Otherwise,
         * code can only get session objects from successful SSL
         * connections which implies that they must have had permission
         * to make the network connection in the first place.
         */
        SecurityManager sm;
        if ((sm = System.getSecurityManager()) != null) {
            sm.checkPermission(new SSLPermission("getSSLSessionContext"));
        }

        return context;
    }


    SessionId getSessionId() {
        return sessionId;
    }


    /**
     * Returns the cipher spec in use on this session
     */
    CipherSuite getSuite() {
        return cipherSuite;
    }

    /**
     * Resets the cipher spec in use on this session
     */
    void setSuite(CipherSuite suite) {
       cipherSuite = suite;

        if (SSLLogger.isOn && SSLLogger.isOn("session")) {
             SSLLogger.finest("Negotiating session:  " + this);
       }
    }

    /**
     * Return true if the session is currently re-established with a
     * session-resumption abbreviated initial handshake.
     */
    boolean isSessionResumption() {
        return isSessionResumption;
    }

    /**
     * Resets whether the session is re-established with a session-resumption
     * abbreviated initial handshake.
     */
    void setAsSessionResumption(boolean flag) {
        isSessionResumption = flag;
    }

    /**
     * Returns the name of the cipher suite in use on this session
     */
    @Override
    public String getCipherSuite() {
        return getSuite().name;
    }

    ProtocolVersion getProtocolVersion() {
        return protocolVersion;
    }

    /**
     * Returns the standard name of the protocol in use on this session
     */
    @Override
    public String getProtocol() {
        return getProtocolVersion().name;
    }

    /**
     * Returns the compression technique used in this session
     */
    byte getCompression() {
        return compressionMethod;
    }

    /**
     * Returns the hashcode for this session
     */
    @Override
    public int hashCode() {
        return sessionId.hashCode();
    }

    /**
     * Returns true if sessions have same ids, false otherwise.
     */
    @Override
    public boolean equals(Object obj) {

        if (obj == this) {
            return true;
        }

        if (obj instanceof SSLSessionImpl) {
            SSLSessionImpl sess = (SSLSessionImpl) obj;
            return (sessionId != null) && (sessionId.equals(
                        sess.getSessionId()));
        }

        return false;
    }


    /**
     * Return the cert chain presented by the peer in the
     * java.security.cert format.
     * Note: This method can be used only when using certificate-based
     * cipher suites; using it with non-certificate-based cipher suites,
     * such as Kerberos, will throw an SSLPeerUnverifiedException.
     *
     * @return array of peer X.509 certs, with the peer's own cert
     *  first in the chain, and with the "root" CA last.
     */
    @Override
    public java.security.cert.Certificate[] getPeerCertificates()
            throws SSLPeerUnverifiedException {
        //
        // clone to preserve integrity of session ... caller can't
        // change record of peer identity even by accident, much
        // less do it intentionally.
        //
        if (peerCerts == null) {
            throw new SSLPeerUnverifiedException("peer not authenticated");
        }
        // Certs are immutable objects, therefore we don't clone them.
        // But do need to clone the array, so that nothing is inserted
        // into peerCerts.
        return (java.security.cert.Certificate[])peerCerts.clone();
    }

    /**
     * Return the cert chain presented to the peer in the
     * java.security.cert format.
     * Note: This method is useful only when using certificate-based
     * cipher suites.
     *
     * @return array of peer X.509 certs, with the peer's own cert
     *  first in the chain, and with the "root" CA last.
     */
    @Override
    public java.security.cert.Certificate[] getLocalCertificates() {
        //
        // clone to preserve integrity of session ... caller can't
        // change record of peer identity even by accident, much
        // less do it intentionally.
        return (localCerts == null ? null :
            (java.security.cert.Certificate[])localCerts.clone());
    }

    /**
     * Return the cert chain presented by the peer in the
     * javax.security.cert format.
     * Note: This method can be used only when using certificate-based
     * cipher suites; using it with non-certificate-based cipher suites,
     * such as Kerberos, will throw an SSLPeerUnverifiedException.
     *
     * @return array of peer X.509 certs, with the peer's own cert
     *  first in the chain, and with the "root" CA last.
     *
     * @deprecated This method returns the deprecated
     *  {@code javax.security.cert.X509Certificate} type.
     *  Use {@code getPeerCertificates()} instead.
     */
    @Override
    @Deprecated
    public javax.security.cert.X509Certificate[] getPeerCertificateChain()
            throws SSLPeerUnverifiedException {
        //
        // clone to preserve integrity of session ... caller can't
        // change record of peer identity even by accident, much
        // less do it intentionally.
        //
        if (peerCerts == null) {
            throw new SSLPeerUnverifiedException("peer not authenticated");
        }
        javax.security.cert.X509Certificate[] certs;
        certs = new javax.security.cert.X509Certificate[peerCerts.length];
        for (int i = 0; i < peerCerts.length; i++) {
            byte[] der = null;
            try {
                der = peerCerts[i].getEncoded();
                certs[i] = javax.security.cert.X509Certificate.getInstance(der);
            } catch (CertificateEncodingException e) {
                throw new SSLPeerUnverifiedException(e.getMessage());
            } catch (javax.security.cert.CertificateException e) {
                throw new SSLPeerUnverifiedException(e.getMessage());
            }
        }

        return certs;
    }

    /**
     * Return the cert chain presented by the peer.
     * Note: This method can be used only when using certificate-based
     * cipher suites; using it with non-certificate-based cipher suites,
     * such as Kerberos, will throw an SSLPeerUnverifiedException.
     *
     * @return array of peer X.509 certs, with the peer's own cert
     *  first in the chain, and with the "root" CA last.
     */
    public X509Certificate[] getCertificateChain()
            throws SSLPeerUnverifiedException {
        /*
         * clone to preserve integrity of session ... caller can't
         * change record of peer identity even by accident, much
         * less do it intentionally.
         */
        if (peerCerts != null) {
            return peerCerts.clone();
        } else {
            throw new SSLPeerUnverifiedException("peer not authenticated");
        }
    }

    /**
     * Return a List of status responses presented by the peer.
     * Note: This method can be used only when using certificate-based
     * server authentication; otherwise an empty {@code List} will be returned.
     *
     * @return an unmodifiable {@code List} of byte arrays, each consisting
     * of a DER-encoded OCSP response (see RFC 6960).  If no responses have
     * been presented by the server or non-certificate based server
     * authentication is used then an empty {@code List} is returned.
     */
    @Override
    public List<byte[]> getStatusResponses() {
        if (statusResponses == null || statusResponses.isEmpty()) {
            return Collections.emptyList();
        } else {
            // Clone both the list and the contents
            List<byte[]> responses = new ArrayList<>(statusResponses.size());
            for (byte[] respBytes : statusResponses) {
                responses.add(respBytes.clone());
            }
            return Collections.unmodifiableList(responses);
        }
    }

    /**
     * Returns the identity of the peer which was established as part of
     * defining the session.
     *
     * @return the peer's principal. Returns an X500Principal of the
     * end-entity certificate for X509-based cipher suites, and
     * Principal for Kerberos cipher suites, etc.
     *
     * @throws SSLPeerUnverifiedException if the peer's identity has not
     *          been verified
     */
    @Override
    public Principal getPeerPrincipal()
                throws SSLPeerUnverifiedException
    {
        if (peerCerts == null) {
            throw new SSLPeerUnverifiedException("peer not authenticated");
        }
        return peerCerts[0].getSubjectX500Principal();
    }

    /**
     * Returns the principal that was sent to the peer during handshaking.
     *
     * @return the principal sent to the peer. Returns an X500Principal
     * of the end-entity certificate for X509-based cipher suites, and
     * Principal for Kerberos cipher suites, etc. If no principal was
     * sent, then null is returned.
     */
    @Override
    public Principal getLocalPrincipal() {
        return ((localCerts == null && localCerts.length != 0) ? null :
                localCerts[0].getSubjectX500Principal());
    }

    /*
     * Return the time the ticket for this session was created.
     */
    public long getTicketCreationTime() {
        return ticketCreationTime;
    }

    /**
     * Returns the time this session was created.
     */
    @Override
    public long getCreationTime() {
        return creationTime;
    }

    /**
     * Returns the last time this session was used to initialize
     * a connection.
     */
    @Override
    public long getLastAccessedTime() {
        return (lastUsedTime != 0) ? lastUsedTime : creationTime;
    }

    void setLastAccessedTime(long time) {
        lastUsedTime = time;
    }


    /**
     * Returns the network address of the session's peer.  This
     * implementation does not insist that connections between
     * different ports on the same host must necessarily belong
     * to different sessions, though that is of course allowed.
     */
    public InetAddress getPeerAddress() {
        try {
            return InetAddress.getByName(host);
        } catch (java.net.UnknownHostException e) {
            return null;
        }
    }

    @Override
    public String getPeerHost() {
        return host;
    }

    /**
     * Need to provide the port info for caching sessions based on
     * host and port. Accessed by SSLSessionContextImpl
     */
    @Override
    public int getPeerPort() {
        return port;
    }

    void setContext(SSLSessionContextImpl ctx) {
        if (context == null) {
            context = ctx;
        }
    }

    /**
     * Invalidate a session.  Active connections may still exist, but
     * no connections will be able to rejoin this session.
     */
    @Override
    public synchronized void invalidate() {
        //
        // Can't invalidate the NULL session -- this would be
        // attempted when we get a handshaking error on a brand
        // new connection, with no "real" session yet.
        //
        if (this == nullSession) {
            return;
        }

        if (context != null) {
            context.remove(sessionId);
            context = null;
        }
        if (invalidated) {
            return;
        }
        invalidated = true;
        if (SSLLogger.isOn && SSLLogger.isOn("session")) {
             SSLLogger.finest("Invalidated session:  " + this);
        }
        for (SSLSessionImpl child : childSessions) {
            child.invalidate();
        }
    }

    /*
     * Table of application-specific session data indexed by an application
     * key and the calling security context. This is important since
     * sessions can be shared across different protection domains.
     */
    private Hashtable<SecureKey, Object> table = new Hashtable<>();

    /**
     * Assigns a session value.  Session change events are given if
     * appropriate, to any original value as well as the new value.
     */
    @Override
    public void putValue(String key, Object value) {
        if ((key == null) || (value == null)) {
            throw new IllegalArgumentException("arguments can not be null");
        }

        SecureKey secureKey = new SecureKey(key);
        Object oldValue = table.put(secureKey, value);

        if (oldValue instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent e;

            e = new SSLSessionBindingEvent(this, key);
            ((SSLSessionBindingListener)oldValue).valueUnbound(e);
        }
        if (value instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent e;

            e = new SSLSessionBindingEvent(this, key);
            ((SSLSessionBindingListener)value).valueBound(e);
        }
    }

    /**
     * Returns the specified session value.
     */
    @Override
    public Object getValue(String key) {
        if (key == null) {
            throw new IllegalArgumentException("argument can not be null");
        }

        SecureKey secureKey = new SecureKey(key);
        return table.get(secureKey);
    }


    /**
     * Removes the specified session value, delivering a session changed
     * event as appropriate.
     */
    @Override
    public void removeValue(String key) {
        if (key == null) {
            throw new IllegalArgumentException("argument can not be null");
        }

        SecureKey secureKey = new SecureKey(key);
        Object value = table.remove(secureKey);

        if (value instanceof SSLSessionBindingListener) {
            SSLSessionBindingEvent e;

            e = new SSLSessionBindingEvent(this, key);
            ((SSLSessionBindingListener)value).valueUnbound(e);
        }
    }


    /**
     * Lists the names of the session values.
     */
    @Override
    public String[] getValueNames() {
        Enumeration<SecureKey> e;
        Vector<Object> v = new Vector<>();
        SecureKey key;
        Object securityCtx = SecureKey.getCurrentSecurityContext();

        for (e = table.keys(); e.hasMoreElements(); ) {
            key = e.nextElement();

            if (securityCtx.equals(key.getSecurityContext())) {
                v.addElement(key.getAppKey());
            }
        }
        String[] names = new String[v.size()];
        v.copyInto(names);

        return names;
    }

    /**
     * Use large packet sizes now or follow RFC 2246 packet sizes (2^14)
     * until changed.
     *
     * In the TLS specification (section 6.2.1, RFC2246), it is not
     * recommended that the plaintext has more than 2^14 bytes.
     * However, some TLS implementations violate the specification.
     * This is a workaround for interoperability with these stacks.
     *
     * Application could accept large fragments up to 2^15 bytes by
     * setting the system property jsse.SSLEngine.acceptLargeFragments
     * to "true".
     */
    private boolean acceptLargeFragments =
            Utilities.getBooleanProperty(
                    "jsse.SSLEngine.acceptLargeFragments", false);

    /**
     * Expand the buffer size of both SSL/TLS network packet and
     * application data.
     */
    protected synchronized void expandBufferSizes() {
        acceptLargeFragments = true;
    }

    /**
     * Gets the current size of the largest SSL/TLS packet that is expected
     * when using this session.
     */
    @Override
    public synchronized int getPacketBufferSize() {
        // Use the bigger packet size calculated from maximumPacketSize
        // and negotiatedMaxFragLen.
        int packetSize = 0;
        if (negotiatedMaxFragLen > 0) {
            packetSize = cipherSuite.calculatePacketSize(
                    negotiatedMaxFragLen, protocolVersion,
                    protocolVersion.isDTLS);
        }

        if (maximumPacketSize > 0) {
            return (maximumPacketSize > packetSize) ?
                    maximumPacketSize : packetSize;
        }

        if (packetSize != 0) {
           return packetSize;
        }

        if (protocolVersion.isDTLS) {
            return DTLSRecord.maxRecordSize;
        } else {
            return acceptLargeFragments ?
                    SSLRecord.maxLargeRecordSize : SSLRecord.maxRecordSize;
        }
    }

    /**
     * Gets the current size of the largest application data that is
     * expected when using this session.
     */
    @Override
    public synchronized int getApplicationBufferSize() {
        // Use the bigger fragment size calculated from maximumPacketSize
        // and negotiatedMaxFragLen.
        int fragmentSize = 0;
        if (maximumPacketSize > 0) {
            fragmentSize = cipherSuite.calculateFragSize(
                    maximumPacketSize, protocolVersion,
                    protocolVersion.isDTLS);
        }

        if (negotiatedMaxFragLen > 0) {
            return (negotiatedMaxFragLen > fragmentSize) ?
                    negotiatedMaxFragLen : fragmentSize;
        }

        if (fragmentSize != 0) {
            return fragmentSize;
        }

        if (protocolVersion.isDTLS) {
            return Record.maxDataSize;
        } else {
            int maxPacketSize = acceptLargeFragments ?
                        SSLRecord.maxLargeRecordSize : SSLRecord.maxRecordSize;
            return (maxPacketSize - SSLRecord.headerSize);
        }
    }

    /**
     * Sets the negotiated maximum fragment length, as specified by the
     * max_fragment_length ClientHello extension in RFC 6066.
     *
     * @param  negotiatedMaxFragLen
     *         the negotiated maximum fragment length, or {@code -1} if
     *         no such length has been negotiated.
     */
    synchronized void setNegotiatedMaxFragSize(
            int negotiatedMaxFragLen) {

        this.negotiatedMaxFragLen = negotiatedMaxFragLen;
    }

    /**
     * Get the negotiated maximum fragment length, as specified by the
     * max_fragment_length ClientHello extension in RFC 6066.
     *
     * @return the negotiated maximum fragment length, or {@code -1} if
     *         no such length has been negotiated.
     */
    synchronized int getNegotiatedMaxFragSize() {
        return negotiatedMaxFragLen;
    }

    synchronized void setMaximumPacketSize(int maximumPacketSize) {
        this.maximumPacketSize = maximumPacketSize;
    }

    synchronized int getMaximumPacketSize() {
        return maximumPacketSize;
    }

    /**
     * Gets an array of supported signature algorithms that the local side is
     * willing to verify.
     */
    @Override
    public String[] getLocalSupportedSignatureAlgorithms() {
        if (localSupportedSignAlgs != null) {
            return localSupportedSignAlgs.clone();
        }

        return new String[0];
    }

    /**
     * Gets an array of supported signature algorithms that the peer is
     * able to verify.
     */
    @Override
    public String[] getPeerSupportedSignatureAlgorithms() {
        if (peerSupportedSignAlgs != null) {
            return peerSupportedSignAlgs.clone();
        }

        return new String[0];
    }

    /**
     * Obtains a <code>List</code> containing all {@link SNIServerName}s
     * of the requested Server Name Indication (SNI) extension.
     */
    @Override
    public List<SNIServerName> getRequestedServerNames() {
        if (requestedServerNames != null && !requestedServerNames.isEmpty()) {
            return Collections.<SNIServerName>unmodifiableList(
                                                requestedServerNames);
        }

        return Collections.<SNIServerName>emptyList();
    }

    /** Returns a string representation of this SSL session */
    @Override
    public String toString() {
        return "[Session-" + sessionCount
            + ", " + getCipherSuite()
            + "]";
    }
}


/**
 * This "struct" class serves as a Hash Key that combines an
 * application-specific key and a security context.
 */
class SecureKey {
    private static Object       nullObject = new Object();
    private Object        	appKey;
    private Object      	securityCtx;

    static Object getCurrentSecurityContext() {
        SecurityManager sm = System.getSecurityManager();
        Object context = null;

        if (sm != null)
            context = sm.getSecurityContext();
        if (context == null)
            context = nullObject;
        return context;
    }

    SecureKey(Object key) {
        this.appKey = key;
        this.securityCtx = getCurrentSecurityContext();
    }

    Object getAppKey() {
        return appKey;
    }

    Object getSecurityContext() {
        return securityCtx;
    }

    @Override
    public int hashCode() {
        return appKey.hashCode() ^ securityCtx.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof SecureKey && ((SecureKey)o).appKey.equals(appKey)
                        && ((SecureKey)o).securityCtx.equals(securityCtx);
    }
}
