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

import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.Filter;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.math.BigInteger;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.PasswordAuthentication;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import sun.net.www.HeaderParser;

/**
 * A simple HTTP server that supports Basic or Digest authentication.
 * By default this server will echo back whatever is present
 * in the request body. Note that the Digest authentication is
 * a test implementation implemented only for tests purposes.
 * @author danielfuchs
 */
public class DigestEchoServer {

    public static final boolean DEBUG =
            Boolean.parseBoolean(System.getProperty("test.debug", "false"));
    public enum HttpAuthType {
        SERVER, PROXY, SERVER307, PROXY305
        /* add PROXY_AND_SERVER and SERVER_PROXY_NONE */
    };
    public enum HttpAuthSchemeType { NONE, BASICSERVER, BASIC, DIGEST };
    public static final HttpAuthType DEFAULT_HTTP_AUTH_TYPE = HttpAuthType.SERVER;
    public static final String DEFAULT_PROTOCOL_TYPE = "https";
    public static final HttpAuthSchemeType DEFAULT_SCHEME_TYPE = HttpAuthSchemeType.DIGEST;

    public static class HttpTestAuthenticator extends Authenticator {
        private final String realm;
        private final String username;
        // Used to prevent incrementation of 'count' when calling the
        // authenticator from the server side.
        private final ThreadLocal<Boolean> skipCount = new ThreadLocal<>();
        // count will be incremented every time getPasswordAuthentication()
        // is called from the client side.
        final AtomicInteger count = new AtomicInteger();

        public HttpTestAuthenticator(String realm, String username) {
            this.realm = realm;
            this.username = username;
        }
        @Override
        protected PasswordAuthentication getPasswordAuthentication() {
            if (skipCount.get() == null || skipCount.get().booleanValue() == false) {
                System.out.println("Authenticator called: " + count.incrementAndGet());
            }
            return new PasswordAuthentication(getUserName(),
                    new char[] {'d','e','n', 't'});
        }
        // Called by the server side to get the password of the user
        // being authentified.
        public final char[] getPassword(String user) {
            if (user.equals(username)) {
                skipCount.set(Boolean.TRUE);
                try {
                    return getPasswordAuthentication().getPassword();
                } finally {
                    skipCount.set(Boolean.FALSE);
                }
            }
            throw new SecurityException("User unknown: " + user);
        }
        public final String getUserName() {
            return username;
        }
        public final String getRealm() {
            return realm;
        }
    }

    public static final HttpTestAuthenticator AUTHENTICATOR;
    static {
        AUTHENTICATOR = new HttpTestAuthenticator("earth", "arthur");
    }


    final HttpServer       serverImpl; // this server endpoint
    final DigestEchoServer redirect;   // the target server where to redirect 3xx
    final HttpHandler      delegate;   // unused

    private DigestEchoServer(HttpServer server, DigestEchoServer target,
                           HttpHandler delegate) {
        this.serverImpl = server;
        this.redirect = target;
        this.delegate = delegate;
    }

    public static void main(String[] args)
            throws IOException {

        DigestEchoServer server = create(DEFAULT_PROTOCOL_TYPE,
                DEFAULT_HTTP_AUTH_TYPE,
                AUTHENTICATOR,
                DEFAULT_SCHEME_TYPE);
        try {
            System.out.println("Server created at " + server.getAddress());
            System.out.println("Strike <Return> to exit");
            System.in.read();
        } finally {
            System.out.println("stopping server");
            server.stop();
        }
    }

    private static String toString(Headers headers) {
        return headers.entrySet().stream()
                .map((e) -> e.getKey() + ": " + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    public static DigestEchoServer create(String protocol,
                                          HttpAuthType authType,
                                          HttpAuthSchemeType schemeType)
            throws IOException {
        return create(protocol, authType, AUTHENTICATOR, schemeType);
    }

    public static DigestEchoServer create(String protocol,
                                          HttpAuthType authType,
                                          HttpTestAuthenticator auth,
                                          HttpAuthSchemeType schemeType)
            throws IOException {
        return create(protocol, authType, auth, schemeType, null);
    }

    public static DigestEchoServer create(String protocol,
                                        HttpAuthType authType,
                                        HttpTestAuthenticator auth,
                                        HttpAuthSchemeType schemeType,
                                        HttpHandler delegate)
            throws IOException {
        Objects.requireNonNull(authType);
        Objects.requireNonNull(auth);
        switch(authType) {
            // A server that performs Server Digest authentication.
            case SERVER: return createServer(protocol, authType, auth,
                                             schemeType, delegate, "/");
            // A server that pretends to be a Proxy and performs
            // Proxy Digest authentication. If protocol is HTTPS,
            // then this will create a HttpsProxyTunnel that will
            // handle the CONNECT request for tunneling.
            case PROXY: return createProxy(protocol, authType, auth,
                                           schemeType, delegate, "/");
            // A server that sends 307 redirect to a server that performs
            // Digest authentication.
            // Note: 301 doesn't work here because it transforms POST into GET.
            case SERVER307: return createServerAndRedirect(protocol,
                                                        HttpAuthType.SERVER,
                                                        auth, schemeType,
                                                        delegate, 307);
            // A server that sends 305 redirect to a proxy that performs
            // Digest authentication.
            // Note: this is not correctly stubbed/implemented in this test.
            case PROXY305:  return createServerAndRedirect(protocol,
                                                        HttpAuthType.PROXY,
                                                        auth, schemeType,
                                                        delegate, 305);
            default:
                throw new InternalError("Unknown server type: " + authType);
        }
    }


    /**
     * The SocketBindableFactory ensures that the local port used by an HttpServer
     * or a proxy ServerSocket previously created by the current test/VM will not
     * get reused by a subsequent test in the same VM.
     * This is to avoid having the test client trying to reuse cached connections.
     */
    private static abstract class SocketBindableFactory<B> {
        private static final int MAX = 10;
        private static final CopyOnWriteArrayList<String> addresses =
                new CopyOnWriteArrayList<>();
        protected B createInternal() throws IOException {
            final int max = addresses.size() + MAX;
            final List<B> toClose = new ArrayList<>();
            try {
                for (int i = 1; i <= max; i++) {
                    B bindable = createBindable();
                    SocketAddress address = getAddress(bindable);
                    String key = address.toString();
                    if (addresses.addIfAbsent(key)) {
                        System.out.println("Socket bound to: " + key
                                + " after " + i + " attempt(s)");
                        return bindable;
                    }
                    System.out.println("warning: address " + key
                            + " already used. Retrying bind.");
                    // keep the port bound until we get a port that we haven't
                    // used already
                    toClose.add(bindable);
                }
            } finally {
                // if we had to retry, then close the socket we're not
                // going to use.
                for (B b : toClose) {
                    try { close(b); } catch (Exception x) { /* ignore */ }
                }
            }
            throw new IOException("Couldn't bind socket after " + max + " attempts: "
                    + "addresses used before: " + addresses);
        }

        protected abstract B createBindable() throws IOException;

        protected abstract SocketAddress getAddress(B bindable);

        protected abstract void close(B bindable) throws IOException;
    }

    /*
     * Used to create ServerSocket for a proxy.
     */
    private static final class ServerSocketFactory
    extends SocketBindableFactory<ServerSocket> {
        private static final ServerSocketFactory instance = new ServerSocketFactory();

        static ServerSocket create() throws IOException {
            return instance.createInternal();
        }

        @Override
        protected ServerSocket createBindable() throws IOException {
            return new ServerSocket(0, 0, InetAddress.getByName("127.0.0.1"));
        }

        @Override
        protected SocketAddress getAddress(ServerSocket socket) {
            return socket.getLocalSocketAddress();
        }

        @Override
        protected void close(ServerSocket socket) throws IOException {
            socket.close();
        }
    }

    /*
     * Used to create HttpServer for a NTLMTestServer.
     */
    private static abstract class WebServerFactory<S extends HttpServer>
            extends SocketBindableFactory<S> {
        @Override
        protected S createBindable() throws IOException {
            S server = newHttpServer();
            server.bind(new InetSocketAddress("127.0.0.1", 0), 0);
            return server;
        }

        @Override
        protected SocketAddress getAddress(S server) {
            return server.getAddress();
        }

        @Override
        protected void close(S server) throws IOException {
            server.stop(1);
        }

        /*
         * Returns a HttpServer or a HttpsServer in different subclasses.
         */
        protected abstract S newHttpServer() throws IOException;
    }

    private static final class HttpServerFactory extends WebServerFactory<HttpServer> {
        private static final HttpServerFactory instance = new HttpServerFactory();

        static HttpServer create() throws IOException {
            return instance.createInternal();
        }

        @Override
        protected HttpServer newHttpServer() throws IOException {
            return HttpServer.create();
        }
    }

    private static final class HttpsServerFactory extends WebServerFactory<HttpsServer> {
        private static final HttpsServerFactory instance = new HttpsServerFactory();

        static HttpsServer create() throws IOException {
            return instance.createInternal();
        }

        @Override
        protected HttpsServer newHttpServer() throws IOException {
            return HttpsServer.create();
        }
    }

    static HttpServer createHttpServer(String protocol) throws IOException {
        final HttpServer server;
        if ("http".equalsIgnoreCase(protocol)) {
            server = HttpServerFactory.create();
        } else if ("https".equalsIgnoreCase(protocol)) {
            server = configure(HttpsServerFactory.create());
        } else {
            throw new InternalError("unsupported protocol: " + protocol);
        }
        return server;
    }

    static HttpsServer configure(HttpsServer server) throws IOException {
        try {
            SSLContext ctx = SSLContext.getDefault();
            server.setHttpsConfigurator(new Configurator(ctx));
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
        return server;
    }


    static void setContextAuthenticator(HttpContext ctxt,
                                        HttpTestAuthenticator auth) {
        final String realm = auth.getRealm();
        com.sun.net.httpserver.Authenticator authenticator =
            new BasicAuthenticator(realm) {
                @Override
                public boolean checkCredentials(String username, String pwd) {
                    return auth.getUserName().equals(username)
                           && new String(auth.getPassword(username)).equals(pwd);
                }
        };
        ctxt.setAuthenticator(authenticator);
    }

    public static DigestEchoServer createServer(String protocol,
                                        HttpAuthType authType,
                                        HttpTestAuthenticator auth,
                                        HttpAuthSchemeType schemeType,
                                        HttpHandler delegate,
                                        String path)
            throws IOException {
        Objects.requireNonNull(authType);
        Objects.requireNonNull(auth);

        HttpServer impl = createHttpServer(protocol);
        final DigestEchoServer server = new DigestEchoServer(impl, null, delegate);
        final HttpHandler hh = server.createHandler(schemeType, auth, authType, false);
        HttpContext ctxt = impl.createContext(path, hh);
        server.configureAuthentication(ctxt, schemeType, auth, authType);
        impl.start();
        return server;
    }

    public static DigestEchoServer createProxy(String protocol,
                                        HttpAuthType authType,
                                        HttpTestAuthenticator auth,
                                        HttpAuthSchemeType schemeType,
                                        HttpHandler delegate,
                                        String path)
            throws IOException {
        Objects.requireNonNull(authType);
        Objects.requireNonNull(auth);

        HttpServer impl = createHttpServer(protocol);
        final DigestEchoServer server = "https".equalsIgnoreCase(protocol)
                ? new HttpsProxyTunnel(impl, null, delegate)
                : new DigestEchoServer(impl, null, delegate);

        final HttpHandler hh = server.createHandler(HttpAuthSchemeType.NONE,
                                    null, HttpAuthType.SERVER,
                                         server instanceof HttpsProxyTunnel);
        HttpContext ctxt = impl.createContext(path, hh);
        server.configureAuthentication(ctxt, schemeType, auth, authType);
        impl.start();

        return server;
    }

    public static DigestEchoServer createServerAndRedirect(
                                        String protocol,
                                        HttpAuthType targetAuthType,
                                        HttpTestAuthenticator auth,
                                        HttpAuthSchemeType schemeType,
                                        HttpHandler targetDelegate,
                                        int code300)
            throws IOException {
        Objects.requireNonNull(targetAuthType);
        Objects.requireNonNull(auth);

        // The connection between client and proxy can only
        // be a plain connection: SSL connection to proxy
        // is not supported by our client connection.
        String targetProtocol = targetAuthType == HttpAuthType.PROXY
                                          ? "http"
                                          : protocol;
        DigestEchoServer redirectTarget =
                (targetAuthType == HttpAuthType.PROXY)
                ? createProxy(protocol, targetAuthType,
                              auth, schemeType, targetDelegate, "/")
                : createServer(targetProtocol, targetAuthType,
                               auth, schemeType, targetDelegate, "/");
        HttpServer impl = createHttpServer(protocol);
        final DigestEchoServer redirectingServer =
                 new DigestEchoServer(impl, redirectTarget, null);
        InetSocketAddress redirectAddr = redirectTarget.getAddress();
        URL locationURL = url(targetProtocol, redirectAddr, "/");
        final HttpHandler hh = redirectingServer.create300Handler(locationURL,
                                             HttpAuthType.SERVER, code300);
        impl.createContext("/", hh);
        impl.start();
        return redirectingServer;
    }

    public InetSocketAddress getAddress() {
        return serverImpl.getAddress();
    }

    public InetSocketAddress getServerAddress() {
        return serverImpl.getAddress();
    }

    public InetSocketAddress getProxyAddress() {
        return serverImpl.getAddress();
    }

    public void stop() {
        serverImpl.stop(0);
        if (redirect != null) {
            redirect.stop();
        }
    }

    protected void writeResponse(HttpExchange he) throws IOException {
        if (delegate == null) {
            he.sendResponseHeaders(HttpURLConnection.HTTP_OK, 0);
            he.getResponseBody().write(he.getRequestBody().readAllBytes());
        } else {
            delegate.handle(he);
        }
    }

    private HttpHandler createHandler(HttpAuthSchemeType schemeType,
                                      HttpTestAuthenticator auth,
                                      HttpAuthType authType,
                                      boolean tunelled) {
        return new HttpNoAuthHandler(authType, tunelled);
    }

    void configureAuthentication(HttpContext ctxt,
                            HttpAuthSchemeType schemeType,
                            HttpTestAuthenticator auth,
                            HttpAuthType authType) {
        switch(schemeType) {
            case DIGEST:
                // DIGEST authentication is handled by the handler.
                ctxt.getFilters().add(new HttpDigestFilter(auth, authType));
                break;
            case BASIC:
                // BASIC authentication is handled by the filter.
                ctxt.getFilters().add(new HttpBasicFilter(auth, authType));
                break;
            case BASICSERVER:
                switch(authType) {
                    case PROXY: case PROXY305:
                        // HttpServer can't support Proxy-type authentication
                        // => we do as if BASIC had been specified, and we will
                        //    handle authentication in the handler.
                        ctxt.getFilters().add(new HttpBasicFilter(auth, authType));
                        break;
                    case SERVER: case SERVER307:
                        // Basic authentication is handled by HttpServer
                        // directly => the filter should not perform
                        // authentication again.
                        setContextAuthenticator(ctxt, auth);
                        ctxt.getFilters().add(new HttpNoAuthFilter(authType));
                        break;
                    default:
                        throw new InternalError("Invalid combination scheme="
                             + schemeType + " authType=" + authType);
                }
            case NONE:
                // No authentication at all.
                ctxt.getFilters().add(new HttpNoAuthFilter(authType));
                break;
            default:
                throw new InternalError("No such scheme: " + schemeType);
        }
    }

    private HttpHandler create300Handler(URL proxyURL,
        HttpAuthType type, int code300) throws MalformedURLException {
        return new Http3xxHandler(proxyURL, type, code300);
    }

    // Abstract HTTP filter class.
    private abstract static class AbstractHttpFilter extends Filter {

        final HttpAuthType authType;
        final String type;
        public AbstractHttpFilter(HttpAuthType authType, String type) {
            this.authType = authType;
            this.type = type;
        }

        String getLocation() {
            return "Location";
        }
        String getAuthenticate() {
            return authType == HttpAuthType.PROXY
                    ? "Proxy-Authenticate" : "WWW-Authenticate";
        }
        String getAuthorization() {
            return authType == HttpAuthType.PROXY
                    ? "Proxy-Authorization" : "Authorization";
        }
        int getUnauthorizedCode() {
            return authType == HttpAuthType.PROXY
                    ? HttpURLConnection.HTTP_PROXY_AUTH
                    : HttpURLConnection.HTTP_UNAUTHORIZED;
        }
        String getKeepAlive() {
            return "keep-alive";
        }
        String getConnection() {
            return authType == HttpAuthType.PROXY
                    ? "Proxy-Connection" : "Connection";
        }
        protected abstract boolean isAuthentified(HttpExchange he) throws IOException;
        protected abstract void requestAuthentication(HttpExchange he) throws IOException;
        protected void accept(HttpExchange he, Chain chain) throws IOException {
            chain.doFilter(he);
        }

        @Override
        public String description() {
            return "Filter for " + type;
        }
        @Override
        public void doFilter(HttpExchange he, Chain chain) throws IOException {
            try {
                System.out.println(type + ": Got " + he.getRequestMethod()
                    + ": " + he.getRequestURI()
                    + "\n" + DigestEchoServer.toString(he.getRequestHeaders()));
                if (!isAuthentified(he)) {
                    try {
                        requestAuthentication(he);
                        he.sendResponseHeaders(getUnauthorizedCode(), 0);
                        System.out.println(type
                            + ": Sent back " + getUnauthorizedCode());
                    } finally {
                        he.close();
                    }
                } else {
                    accept(he, chain);
                }
            } catch (RuntimeException | Error | IOException t) {
               System.err.println(type
                    + ": Unexpected exception while handling request: " + t);
               t.printStackTrace(System.err);
               he.close();
               throw t;
            }
        }

    }

    // WARNING: This is not a full fledged implementation of DIGEST.
    // It does contain bugs and inaccuracy.
    final static class DigestResponse {
        final String realm;
        final String username;
        final String nonce;
        final String cnonce;
        final String nc;
        final String uri;
        final String algorithm;
        final String response;
        final String qop;
        final String opaque;

        public DigestResponse(String realm, String username, String nonce,
                              String cnonce, String nc, String uri,
                              String algorithm, String qop, String opaque,
                              String response) {
            this.realm = realm;
            this.username = username;
            this.nonce = nonce;
            this.cnonce = cnonce;
            this.nc = nc;
            this.uri = uri;
            this.algorithm = algorithm;
            this.qop = qop;
            this.opaque = opaque;
            this.response = response;
        }

        String getAlgorithm(String defval) {
            return algorithm == null ? defval : algorithm;
        }
        String getQoP(String defval) {
            return qop == null ? defval : qop;
        }

        // Code stolen from DigestAuthentication:

        private static final char charArray[] = {
            '0', '1', '2', '3', '4', '5', '6', '7',
            '8', '9', 'a', 'b', 'c', 'd', 'e', 'f'
        };

        private static String encode(String src, char[] passwd, MessageDigest md) {
            try {
                md.update(src.getBytes("ISO-8859-1"));
            } catch (java.io.UnsupportedEncodingException uee) {
                assert false;
            }
            if (passwd != null) {
                byte[] passwdBytes = new byte[passwd.length];
                for (int i=0; i<passwd.length; i++)
                    passwdBytes[i] = (byte)passwd[i];
                md.update(passwdBytes);
                Arrays.fill(passwdBytes, (byte)0x00);
            }
            byte[] digest = md.digest();

            StringBuilder res = new StringBuilder(digest.length * 2);
            for (int i = 0; i < digest.length; i++) {
                int hashchar = ((digest[i] >>> 4) & 0xf);
                res.append(charArray[hashchar]);
                hashchar = (digest[i] & 0xf);
                res.append(charArray[hashchar]);
            }
            return res.toString();
        }

        public static String computeDigest(boolean isRequest,
                                            String reqMethod,
                                            char[] password,
                                            DigestResponse params)
            throws NoSuchAlgorithmException
        {

            String A1, HashA1;
            String algorithm = params.getAlgorithm("MD5");
            boolean md5sess = algorithm.equalsIgnoreCase ("MD5-sess");

            MessageDigest md = MessageDigest.getInstance(md5sess?"MD5":algorithm);

            if (params.username == null) {
                throw new IllegalArgumentException("missing username");
            }
            if (params.realm == null) {
                throw new IllegalArgumentException("missing realm");
            }
            if (params.uri == null) {
                throw new IllegalArgumentException("missing uri");
            }
            if (params.nonce == null) {
                throw new IllegalArgumentException("missing nonce");
            }

            A1 = params.username + ":" + params.realm + ":";
            HashA1 = encode(A1, password, md);

            String A2;
            if (isRequest) {
                A2 = reqMethod + ":" + params.uri;
            } else {
                A2 = ":" + params.uri;
            }
            String HashA2 = encode(A2, null, md);
            String combo, finalHash;

            if ("auth".equals(params.qop)) { /* RRC2617 when qop=auth */
                if (params.cnonce == null) {
                    throw new IllegalArgumentException("missing nonce");
                }
                if (params.nc == null) {
                    throw new IllegalArgumentException("missing nonce");
                }
                combo = HashA1+ ":" + params.nonce + ":" + params.nc + ":" +
                            params.cnonce + ":auth:" +HashA2;

            } else { /* for compatibility with RFC2069 */
                combo = HashA1 + ":" +
                           params.nonce + ":" +
                           HashA2;
            }
            finalHash = encode(combo, null, md);
            return finalHash;
        }

        public static DigestResponse create(String raw) {
            String username, realm, nonce, nc, uri, response, cnonce,
                   algorithm, qop, opaque;
            HeaderParser parser = new HeaderParser(raw);
            username = parser.findValue("username");
            realm = parser.findValue("realm");
            nonce = parser.findValue("nonce");
            nc = parser.findValue("nc");
            uri = parser.findValue("uri");
            cnonce = parser.findValue("cnonce");
            response = parser.findValue("response");
            algorithm = parser.findValue("algorithm");
            qop = parser.findValue("qop");
            opaque = parser.findValue("opaque");
            return new DigestResponse(realm, username, nonce, cnonce, nc, uri,
                                      algorithm, qop, opaque, response);
        }

    }

    private class HttpNoAuthFilter extends AbstractHttpFilter {

        public HttpNoAuthFilter(HttpAuthType authType) {
            super(authType, authType == HttpAuthType.SERVER
                            ? "NoAuth Server" : "NoAuth Proxy");
        }

        @Override
        protected boolean isAuthentified(HttpExchange he) throws IOException {
            return true;
        }

        @Override
        protected void requestAuthentication(HttpExchange he) throws IOException {
            throw new InternalError("Should not com here");
        }

        @Override
        public String description() {
            return "Passthrough Filter";
        }

    }

    // An HTTP Filter that performs Basic authentication
    private class HttpBasicFilter extends AbstractHttpFilter {

        private final HttpTestAuthenticator auth;
        public HttpBasicFilter(HttpTestAuthenticator auth, HttpAuthType authType) {
            super(authType, authType == HttpAuthType.SERVER
                            ? "Basic Server" : "Basic Proxy");
            this.auth = auth;
        }

        @Override
        protected void requestAuthentication(HttpExchange he)
            throws IOException {
            he.getResponseHeaders().add(getAuthenticate(),
                 "Basic realm=\"" + auth.getRealm() + "\"");
            System.out.println(type + ": Requesting Basic Authentication "
                 + he.getResponseHeaders().getFirst(getAuthenticate()));
        }

        @Override
        protected boolean isAuthentified(HttpExchange he) {
            if (he.getRequestHeaders().containsKey(getAuthorization())) {
                List<String> authorization =
                    he.getRequestHeaders().get(getAuthorization());
                for (String a : authorization) {
                    System.out.println(type + ": processing " + a);
                    int sp = a.indexOf(' ');
                    if (sp < 0) return false;
                    String scheme = a.substring(0, sp);
                    if (!"Basic".equalsIgnoreCase(scheme)) {
                        System.out.println(type + ": Unsupported scheme '"
                                           + scheme +"'");
                        return false;
                    }
                    if (a.length() <= sp+1) {
                        System.out.println(type + ": value too short for '"
                                            + scheme +"'");
                        return false;
                    }
                    a = a.substring(sp+1);
                    return validate(a);
                }
                return false;
            }
            return false;
        }

        boolean validate(String a) {
            byte[] b = Base64.getDecoder().decode(a);
            String userpass = new String (b);
            int colon = userpass.indexOf (':');
            String uname = userpass.substring (0, colon);
            String pass = userpass.substring (colon+1);
            return auth.getUserName().equals(uname) &&
                   new String(auth.getPassword(uname)).equals(pass);
        }

        @Override
        public String description() {
            return "Filter for " + type;
        }

    }


    // An HTTP Filter that performs Digest authentication
    // WARNING: This is not a full fledged implementation of DIGEST.
    // It does contain bugs and inaccuracy.
    private class HttpDigestFilter extends AbstractHttpFilter {

        // This is a very basic DIGEST - used only for the purpose of testing
        // the client implementation. Therefore we can get away with never
        // updating the server nonce as it makes the implementation of the
        // server side digest simpler.
        private final HttpTestAuthenticator auth;
        private final byte[] nonce;
        private final String ns;
        public HttpDigestFilter(HttpTestAuthenticator auth, HttpAuthType authType) {
            super(authType, authType == HttpAuthType.SERVER
                            ? "Digest Server" : "Digest Proxy");
            this.auth = auth;
            nonce = new byte[16];
            new Random(Instant.now().toEpochMilli()).nextBytes(nonce);
            ns = new BigInteger(1, nonce).toString(16);
        }

        @Override
        protected void requestAuthentication(HttpExchange he)
            throws IOException {
            he.getResponseHeaders().add(getAuthenticate(),
                 "Digest realm=\"" + auth.getRealm() + "\","
                 + "\r\n    qop=\"auth\","
                 + "\r\n    nonce=\"" + ns +"\"");
            System.out.println(type + ": Requesting Digest Authentication "
                 + he.getResponseHeaders().getFirst(getAuthenticate()));
        }

        @Override
        protected boolean isAuthentified(HttpExchange he) {
            if (he.getRequestHeaders().containsKey(getAuthorization())) {
                List<String> authorization = he.getRequestHeaders().get(getAuthorization());
                for (String a : authorization) {
                    System.out.println(type + ": processing " + a);
                    int sp = a.indexOf(' ');
                    if (sp < 0) return false;
                    String scheme = a.substring(0, sp);
                    if (!"Digest".equalsIgnoreCase(scheme)) {
                        System.out.println(type + ": Unsupported scheme '" + scheme +"'");
                        return false;
                    }
                    if (a.length() <= sp+1) {
                        System.out.println(type + ": value too short for '" + scheme +"'");
                        return false;
                    }
                    a = a.substring(sp+1);
                    DigestResponse dgr = DigestResponse.create(a);
                    return validate(he.getRequestURI(), he.getRequestMethod(), dgr);
                }
                return false;
            }
            return false;
        }

        boolean validate(URI uri, String reqMethod, DigestResponse dg) {
            if (!"MD5".equalsIgnoreCase(dg.getAlgorithm("MD5"))) {
                System.out.println(type + ": Unsupported algorithm "
                                   + dg.algorithm);
                return false;
            }
            if (!"auth".equalsIgnoreCase(dg.getQoP("auth"))) {
                System.out.println(type + ": Unsupported qop "
                                   + dg.qop);
                return false;
            }
            try {
                if (!dg.nonce.equals(ns)) {
                    System.out.println(type + ": bad nonce returned by client: "
                                    + nonce + " expected " + ns);
                    return false;
                }
                if (dg.response == null) {
                    System.out.println(type + ": missing digest response.");
                    return false;
                }
                char[] pa = auth.getPassword(dg.username);
                return verify(uri, reqMethod, dg, pa);
            } catch(IllegalArgumentException | SecurityException
                    | NoSuchAlgorithmException e) {
                System.out.println(type + ": " + e.getMessage());
                return false;
            }
        }


        boolean verify(URI uri, String reqMethod, DigestResponse dg, char[] pw)
            throws NoSuchAlgorithmException {
            String response = DigestResponse.computeDigest(true, reqMethod, pw, dg);
            if (!dg.response.equals(response)) {
                System.out.println(type + ": bad response returned by client: "
                                    + dg.response + " expected " + response);
                return false;
            } else {
                // A real server would also verify the uri=<request-uri>
                // parameter - but this is just a test...
                System.out.println(type + ": verified response " + response);
            }
            return true;
        }


        @Override
        public String description() {
            return "Filter for DIGEST authentication";
        }
    }

    // Abstract HTTP handler class.
    private abstract static class AbstractHttpHandler implements HttpHandler {

        final HttpAuthType authType;
        final String type;
        public AbstractHttpHandler(HttpAuthType authType, String type) {
            this.authType = authType;
            this.type = type;
        }

        String getLocation() {
            return "Location";
        }

        @Override
        public void handle(HttpExchange he) throws IOException {
            try {
                sendResponse(he);
            } catch (RuntimeException | Error | IOException t) {
               System.err.println(type
                    + ": Unexpected exception while handling request: " + t);
               t.printStackTrace(System.err);
               throw t;
            } finally {
                he.close();
            }
        }

        protected abstract void sendResponse(HttpExchange he) throws IOException;

    }

    private class HttpNoAuthHandler extends AbstractHttpHandler {

        // true if this server is behind a proxy tunnel.
        final boolean tunnelled;
        public HttpNoAuthHandler(HttpAuthType authType, boolean tunnelled) {
            super(authType, authType == HttpAuthType.SERVER
                            ? "NoAuth Server" : "NoAuth Proxy");
            this.tunnelled = tunnelled;
        }

        @Override
        protected void sendResponse(HttpExchange he) throws IOException {
            if (DEBUG) {
                System.out.println(type + ": headers are: "
                        + DigestEchoServer.toString(he.getRequestHeaders()));
            }
            if (authType == HttpAuthType.SERVER && tunnelled) {
                // Verify that the client doesn't send us proxy-* headers
                // used to establish the proxy tunnel
                Optional<String> proxyAuth = he.getRequestHeaders()
                        .keySet().stream()
                        .filter("proxy-authorization"::equalsIgnoreCase)
                        .findAny();
                if (proxyAuth.isPresent()) {
                    System.out.println(type + " found "
                            + proxyAuth.get() + ": failing!");
                    throw new IOException(proxyAuth.get()
                            + " found by " + type + " for "
                            + he.getRequestURI());
                }
            }
            DigestEchoServer.this.writeResponse(he);
        }

    }

    // A dummy HTTP Handler that redirects all incoming requests
    // by sending a back 3xx response code (301, 305, 307 etc..)
    private class Http3xxHandler extends AbstractHttpHandler {

        private final URL redirectTargetURL;
        private final int code3XX;
        public Http3xxHandler(URL proxyURL, HttpAuthType authType, int code300) {
            super(authType, "Server" + code300);
            this.redirectTargetURL = proxyURL;
            this.code3XX = code300;
        }

        int get3XX() {
            return code3XX;
        }

        @Override
        public void sendResponse(HttpExchange he) throws IOException {
            System.out.println(type + ": Got " + he.getRequestMethod()
                    + ": " + he.getRequestURI()
                    + "\n" + DigestEchoServer.toString(he.getRequestHeaders()));
            System.out.println(type + ": Redirecting to "
                               + (authType == HttpAuthType.PROXY305
                                    ? "proxy" : "server"));
            he.getResponseHeaders().add(getLocation(),
                redirectTargetURL.toExternalForm().toString());
            he.sendResponseHeaders(get3XX(), 0);
            System.out.println(type + ": Sent back " + get3XX() + " "
                 + getLocation() + ": " + redirectTargetURL.toExternalForm().toString());
        }
    }

    static class Configurator extends HttpsConfigurator {
        public Configurator(SSLContext ctx) {
            super(ctx);
        }

        @Override
        public void configure (HttpsParameters params) {
            params.setSSLParameters (getSSLContext().getSupportedSSLParameters());
        }
    }

    static final long start = System.nanoTime();
    public static String now() {
        long now = System.nanoTime() - start;
        long secs = now / 1000_000_000;
        long mill = (now % 1000_000_000) / 1000_000;
        long nan = now % 1000_000;
        return String.format("[%d s, %d ms, %d ns] ", secs, mill, nan);
    }

    static class  ProxyAuthorization {
        final HttpAuthSchemeType schemeType;
        final HttpTestAuthenticator authenticator;
        private final byte[] nonce;
        private final String ns;

        ProxyAuthorization(HttpAuthSchemeType schemeType, HttpTestAuthenticator auth) {
            this.schemeType = schemeType;
            this.authenticator = auth;
            nonce = new byte[16];
            new Random(Instant.now().toEpochMilli()).nextBytes(nonce);
            ns = new BigInteger(1, nonce).toString(16);
        }

        String doBasic(Optional<String> authorization) {
            String offset = "proxy-authorization: basic ";
            String authstring = authorization.orElse("");
            if (!authstring.toLowerCase(Locale.US).startsWith(offset)) {
                return "Proxy-Authenticate: BASIC " + "realm=\""
                        + authenticator.getRealm() +"\"";
            }
            authstring = authstring
                    .substring(offset.length())
                    .trim();
            byte[] base64 = Base64.getDecoder().decode(authstring);
            String up = new String(base64, StandardCharsets.UTF_8);
            int colon = up.indexOf(':');
            if (colon < 1) {
                return "Proxy-Authenticate: BASIC " + "realm=\""
                        + authenticator.getRealm() +"\"";
            }
            String u = up.substring(0, colon);
            String p = up.substring(colon+1);
            char[] pw = authenticator.getPassword(u);
            if (!p.equals(new String(pw))) {
                return "Proxy-Authenticate: BASIC " + "realm=\""
                        + authenticator.getRealm() +"\"";
            }
            System.out.println(now() + " Proxy basic authentication success");
            return null;
        }

        String doDigest(Optional<String> authorization) {
            String offset = "proxy-authorization: digest ";
            String authstring = authorization.orElse("");
            if (!authstring.toLowerCase(Locale.US).startsWith(offset)) {
                return "Proxy-Authenticate: " +
                        "Digest realm=\"" + authenticator.getRealm() + "\","
                        + "\r\n    qop=\"auth\","
                        + "\r\n    nonce=\"" + ns +"\"";
            }
            authstring = authstring
                    .substring(offset.length())
                    .trim();
            boolean validated = false;
            try {
                DigestResponse dgr = DigestResponse.create(authstring);
                validated = validate("CONNECT", dgr);
            } catch (Throwable t) {
                t.printStackTrace();
            }
            if (!validated) {
                return "Proxy-Authenticate: " +
                        "Digest realm=\"" + authenticator.getRealm() + "\","
                        + "\r\n    qop=\"auth\","
                        + "\r\n    nonce=\"" + ns +"\"";
            }
            return null;
        }




        boolean validate(String reqMethod, DigestResponse dg) {
            String type = now() + this.getClass().getSimpleName();
            if (!"MD5".equalsIgnoreCase(dg.getAlgorithm("MD5"))) {
                System.out.println(type + ": Unsupported algorithm "
                        + dg.algorithm);
                return false;
            }
            if (!"auth".equalsIgnoreCase(dg.getQoP("auth"))) {
                System.out.println(type + ": Unsupported qop "
                        + dg.qop);
                return false;
            }
            try {
                if (!dg.nonce.equals(ns)) {
                    System.out.println(type + ": bad nonce returned by client: "
                            + nonce + " expected " + ns);
                    return false;
                }
                if (dg.response == null) {
                    System.out.println(type + ": missing digest response.");
                    return false;
                }
                char[] pa = authenticator.getPassword(dg.username);
                return verify(type, reqMethod, dg, pa);
            } catch(IllegalArgumentException | SecurityException
                    | NoSuchAlgorithmException e) {
                System.out.println(type + ": " + e.getMessage());
                return false;
            }
        }


        boolean verify(String type, String reqMethod, DigestResponse dg, char[] pw)
                throws NoSuchAlgorithmException {
            String response = DigestResponse.computeDigest(true, reqMethod, pw, dg);
            if (!dg.response.equals(response)) {
                System.out.println(type + ": bad response returned by client: "
                        + dg.response + " expected " + response);
                return false;
            } else {
                // A real server would also verify the uri=<request-uri>
                // parameter - but this is just a test...
                System.out.println(type + ": verified response " + response);
            }
            return true;
        }

        public boolean authorize(StringBuilder response, String requestLine, String headers) {
            String message = "<html><body><p>Authorization Failed%s</p></body></html>\r\n";
            if (authenticator == null && schemeType != HttpAuthSchemeType.NONE) {
                message = String.format(message, " No Authenticator Set");
                response.append("HTTP/1.1 407 Proxy Authentication Failed\r\n");
                response.append("Content-Length: ")
                        .append(message.getBytes(StandardCharsets.UTF_8).length)
                        .append("\r\n\r\n");
                response.append(message);
                return false;
            }
            Optional<String> authorization = Stream.of(headers.split("\r\n"))
                    .filter((k) -> k.toLowerCase(Locale.US).startsWith("proxy-authorization:"))
                    .findFirst();
            String authenticate = null;
            switch(schemeType) {
                case BASIC:
                case BASICSERVER:
                    authenticate = doBasic(authorization);
                    break;
                case DIGEST:
                    authenticate = doDigest(authorization);
                    break;
                case NONE:
                    response.append("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
                    return true;
                default:
                    throw new InternalError("Unknown scheme type: " + schemeType);
            }
            if (authenticate != null) {
                message = String.format(message, "");
                response.append("HTTP/1.1 407 Proxy Authentication Required\r\n");
                response.append("Content-Length: ")
                        .append(message.getBytes(StandardCharsets.UTF_8).length)
                        .append("\r\n")
                        .append(authenticate)
                        .append("\r\n\r\n");
                response.append(message);
                return false;
            }
            response.append("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            return true;
        }
    }

    // This is a bit hacky: HttpsProxyTunnel is an HTTPTestServer hidden
    // behind a fake proxy that only understands CONNECT requests.
    // The fake proxy is just a server socket that intercept the
    // CONNECT and then redirect streams to the real server.
    static class HttpsProxyTunnel extends DigestEchoServer
            implements Runnable {

        final ServerSocket ss;
        final CopyOnWriteArrayList<CompletableFuture<Void>> connectionCFs
                = new CopyOnWriteArrayList<>();
        volatile ProxyAuthorization authorization;
        volatile boolean stopped;
        public HttpsProxyTunnel(HttpServer server, DigestEchoServer target,
                               HttpHandler delegate)
                throws IOException {
            super(server, target, delegate);
            System.out.flush();
            System.err.println("WARNING: HttpsProxyTunnel is an experimental test class");
            ss = ServerSocketFactory.create();
            start();
        }

        final void start() throws IOException {
            Thread t = new Thread(this, "ProxyThread");
            t.setDaemon(true);
            t.start();
        }

        @Override
        public void stop() {
            stopped = true;
            super.stop();
            try {
                ss.close();
            } catch (IOException ex) {
                if (DEBUG) ex.printStackTrace(System.out);
            }
        }


        @Override
        void configureAuthentication(HttpContext ctxt,
                                     HttpAuthSchemeType schemeType,
                                     HttpTestAuthenticator auth,
                                     HttpAuthType authType) {
            if (authType == HttpAuthType.PROXY || authType == HttpAuthType.PROXY305) {
                authorization = new ProxyAuthorization(schemeType, auth);
            } else {
                super.configureAuthentication(ctxt, schemeType, auth, authType);
            }
        }

        boolean authorize(StringBuilder response, String requestLine, String headers) {
            if (authorization != null) {
                return authorization.authorize(response, requestLine, headers);
            }
            response.append("HTTP/1.1 200 OK\r\nContent-Length: 0\r\n\r\n");
            return true;
        }

        // Pipe the input stream to the output stream.
        private synchronized Thread pipe(InputStream is, OutputStream os, char tag, CompletableFuture<Void> end) {
            return new Thread("TunnelPipe("+tag+")") {
                @Override
                public void run() {
                    try {
                        try {
                            int c;
                            while ((c = is.read()) != -1) {
                                os.write(c);
                                os.flush();
                                // if DEBUG prints a + or a - for each transferred
                                // character.
                                if (DEBUG) System.out.print(tag);
                            }
                            is.close();
                        } finally {
                            os.close();
                        }
                    } catch (IOException ex) {
                        if (DEBUG) ex.printStackTrace(System.out);
                    } finally {
                        end.complete(null);
                    }
                }
            };
        }

        @Override
        public InetSocketAddress getAddress() {
            return new InetSocketAddress(ss.getInetAddress(), ss.getLocalPort());
        }
        public InetSocketAddress getProxyAddress() {
            return getAddress();
        }
        public InetSocketAddress getServerAddress() {
            return serverImpl.getAddress();
        }


        // This is a bit shaky. It doesn't handle continuation
        // lines, but our client shouldn't send any.
        // Read a line from the input stream, swallowing the final
        // \r\n sequence. Stops at the first \n, doesn't complain
        // if it wasn't preceded by '\r'.
        //
        String readLine(InputStream r) throws IOException {
            StringBuilder b = new StringBuilder();
            int c;
            while ((c = r.read()) != -1) {
                if (c == '\n') break;
                b.appendCodePoint(c);
            }
            if (b.codePointAt(b.length() -1) == '\r') {
                b.delete(b.length() -1, b.length());
            }
            return b.toString();
        }

        @Override
        public void run() {
            Socket clientConnection = null;
            try {
                while (!stopped) {
                    System.out.println(now() + "Tunnel: Waiting for client");
                    Socket toClose;
                    try {
                        toClose = clientConnection = ss.accept();
                    } catch (IOException io) {
                        if (DEBUG || !stopped) io.printStackTrace(System.out);
                        break;
                    }
                    System.out.println(now() + "Tunnel: Client accepted");
                    StringBuilder headers = new StringBuilder();
                    Socket targetConnection = null;
                    InputStream  ccis = clientConnection.getInputStream();
                    OutputStream ccos = clientConnection.getOutputStream();
                    Writer w = new OutputStreamWriter(
                                   clientConnection.getOutputStream(), "UTF-8");
                    PrintWriter pw = new PrintWriter(w);
                    System.out.println(now() + "Tunnel: Reading request line");
                    String requestLine = readLine(ccis);
                    System.out.println(now() + "Tunnel: Request line: " + requestLine);
                    if (requestLine.startsWith("CONNECT ")) {
                        // We should probably check that the next word following
                        // CONNECT is the host:port of our HTTPS serverImpl.
                        // Some improvement for a followup!

                        // Read all headers until we find the empty line that
                        // signals the end of all headers.
                        String line = requestLine;
                        while(!line.equals("")) {
                            System.out.println(now() + "Tunnel: Reading header: "
                                               + (line = readLine(ccis)));
                            headers.append(line).append("\r\n");
                        }

                        StringBuilder response = new StringBuilder();
                        final boolean authorize = authorize(response, requestLine, headers.toString());
                        if (!authorize) {
                            System.out.println(now() + "Tunnel: Sending "
                                    + response);
                            // send the 407 response
                            pw.print(response.toString());
                            pw.flush();
                            toClose.close();
                            continue;
                        }
                        targetConnection = new Socket(
                                serverImpl.getAddress().getAddress(),
                                serverImpl.getAddress().getPort());

                        // Then send the 200 OK response to the client
                        System.out.println(now() + "Tunnel: Sending "
                                           + response);
                        pw.print(response);
                        pw.flush();
                    } else {
                        // This should not happen. If it does then just print an
                        // error - both on out and err, and close the accepted
                        // socket
                        System.out.println("WARNING: Tunnel: Unexpected status line: "
                                + requestLine + " received by "
                                + ss.getLocalSocketAddress()
                                + " from "
                                + toClose.getRemoteSocketAddress()
                                + " - closing accepted socket");
                        // Print on err
                        System.err.println("WARNING: Tunnel: Unexpected status line: "
                                             + requestLine + " received by "
                                           + ss.getLocalSocketAddress()
                                           + " from "
                                           + toClose.getRemoteSocketAddress());
                        // close accepted socket.
                        toClose.close();
                        System.err.println("Tunnel: accepted socket closed.");
                        continue;
                    }

                    // Pipe the input stream of the client connection to the
                    // output stream of the target connection and conversely.
                    // Now the client and target will just talk to each other.
                    System.out.println(now() + "Tunnel: Starting tunnel pipes");
                    CompletableFuture<Void> end, end1, end2;
                    Thread t1 = pipe(ccis, targetConnection.getOutputStream(), '+',
                            end1 = new CompletableFuture<>());
                    Thread t2 = pipe(targetConnection.getInputStream(), ccos, '-',
                            end2 = new CompletableFuture<>());
                    end = CompletableFuture.allOf(end1, end2);
                    end.whenComplete(
                            (r,t) -> {
                                try { toClose.close(); } catch (IOException x) { }
                                finally {connectionCFs.remove(end);}
                            });
                    connectionCFs.add(end);
                    t1.start();
                    t2.start();
                }
            } catch (Throwable ex) {
                try {
                    ss.close();
                } catch (IOException ex1) {
                    ex.addSuppressed(ex1);
                }
                ex.printStackTrace(System.err);
            } finally {
                System.out.println(now() + "Tunnel: exiting (stopped=" + stopped + ")");
                connectionCFs.forEach(cf -> cf.complete(null));
            }
        }
    }

    private static String protocol(String protocol) {
        if ("http".equalsIgnoreCase(protocol)) return "http";
        else if ("https".equalsIgnoreCase(protocol)) return "https";
        else throw new InternalError("Unsupported protocol: " + protocol);
    }

    public static URL url(String protocol, InetSocketAddress address,
                          String path) throws MalformedURLException {
        return new URL(protocol(protocol),
                address.getHostString(),
                address.getPort(), path);
    }

    public static URI uri(String protocol, InetSocketAddress address,
                          String path) throws URISyntaxException {
        return new URI(protocol(protocol) + "://" +
                address.getHostString() + ":" +
                address.getPort() + path);
    }
}
