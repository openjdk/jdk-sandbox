/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.ProxySelector;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.net.ssl.SSLContext;
import javax.net.ServerSocketFactory;
import javax.net.ssl.SSLServerSocketFactory;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpClient.Version;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.testlibrary.SimpleSSLContext;
import sun.net.www.HeaderParser;
import static java.lang.System.out;
import static java.lang.String.format;
import static jdk.incubator.http.HttpResponse.BodyHandler.asLines;

/**
 * @test
 * @summary this test verifies that a client may provides authorization
 *          headers directly when connecting with a server.
 * @bug 8087112
 * @library /lib/testlibrary
 * @build jdk.testlibrary.SimpleSSLContext DigestEchoServer DigestEchoClient
 * @modules jdk.incubator.httpclient
 *          java.base/sun.net.www
 * @run main/othervm DigestEchoClient
 */

public class DigestEchoClient {

    static final String data[] = {
        "Lorem ipsum",
        "dolor sit amet",
        "consectetur adipiscing elit, sed do eiusmod tempor",
        "quis nostrud exercitation ullamco",
        "laboris nisi",
        "ut",
        "aliquip ex ea commodo consequat." +
        "Duis aute irure dolor in reprehenderit in voluptate velit esse" +
        "cillum dolore eu fugiat nulla pariatur.",
        "Excepteur sint occaecat cupidatat non proident."
    };

    static final AtomicLong serverCount = new AtomicLong();
    static final class EchoServers {
        final DigestEchoServer.HttpAuthType authType;
        final DigestEchoServer.HttpAuthSchemeType authScheme;
        final String protocolScheme;
        final String key;
        final DigestEchoServer server;

        private EchoServers(DigestEchoServer server,
                    String protocolScheme,
                    DigestEchoServer.HttpAuthType authType,
                    DigestEchoServer.HttpAuthSchemeType authScheme) {
            this.authType = authType;
            this.authScheme = authScheme;
            this.protocolScheme = protocolScheme;
            this.key = key(protocolScheme, authType, authScheme);
            this.server = server;
        }

        static String key(String protocolScheme,
                          DigestEchoServer.HttpAuthType authType,
                          DigestEchoServer.HttpAuthSchemeType authScheme) {
            return String.format("%s:%s:%s", protocolScheme, authType, authScheme);
        }

        private static EchoServers create(String protocolScheme,
                                   DigestEchoServer.HttpAuthType authType,
                                   DigestEchoServer.HttpAuthSchemeType authScheme) {
            try {
                serverCount.incrementAndGet();
                DigestEchoServer server =
                    DigestEchoServer.create(protocolScheme, authType, authScheme);
                return new EchoServers(server, protocolScheme, authType, authScheme);
            } catch (IOException x) {
                throw new UncheckedIOException(x);
            }
        }

        public static DigestEchoServer of(String protocolScheme,
                                    DigestEchoServer.HttpAuthType authType,
                                    DigestEchoServer.HttpAuthSchemeType authScheme) {
            String key = key(protocolScheme, authType, authScheme);
            return servers.computeIfAbsent(key, (k) ->
                    create(protocolScheme, authType, authScheme)).server;
        }

        public static void stop() {
            for (EchoServers s : servers.values()) {
                s.server.stop();
            }
        }

        private static final ConcurrentMap<String, EchoServers> servers = new ConcurrentHashMap<>();
    }


    static final AtomicInteger NC = new AtomicInteger();
    static final Random random = new Random();
    static final SSLContext context;
    static {
        try {
            context = new SimpleSSLContext().get();
            SSLContext.setDefault(context);
        } catch (Exception x) {
            throw new ExceptionInInitializerError(x);
        }
    }
    static final List<Boolean> BOOLEANS = List.of(true, false);

    final ServerSocketFactory factory;
    final boolean useSSL;
    final DigestEchoServer.HttpAuthSchemeType authScheme;
    final DigestEchoServer.HttpAuthType authType;
    DigestEchoClient(boolean useSSL,
                     DigestEchoServer.HttpAuthSchemeType authScheme,
                     DigestEchoServer.HttpAuthType authType)
            throws IOException {
        this.useSSL = useSSL;
        this.authScheme = authScheme;
        this.authType = authType;
        factory = useSSL ? SSLServerSocketFactory.getDefault()
                         : ServerSocketFactory.getDefault();
    }

    static final AtomicLong clientCount = new AtomicLong();
    public HttpClient newHttpClient(DigestEchoServer server) {
        clientCount.incrementAndGet();
        HttpClient.Builder builder = HttpClient.newBuilder();
        if (useSSL) {
            builder.sslContext(context);
        }
        switch (authScheme) {
            case BASIC:
                builder = builder.authenticator(DigestEchoServer.AUTHENTICATOR);
                break;
            case BASICSERVER:
                // don't set the authenticator: we will handle the header ourselves.
                // builder = builder.authenticator(DigestEchoServer.AUTHENTICATOR);
                break;
            default:
                break;
        }
        switch (authType) {
            case PROXY:
                builder = builder.proxy(ProxySelector.of(server.getProxyAddress()));
                break;
            case PROXY305:
                builder = builder.proxy(ProxySelector.of(server.getProxyAddress()));
                builder = builder.followRedirects(HttpClient.Redirect.SAME_PROTOCOL);
                break;
            case SERVER307:
                builder = builder.followRedirects(HttpClient.Redirect.SAME_PROTOCOL);
                break;
            default:
                break;
        }
        return builder.build();
    }

    public static void main(String[] args) throws Exception {
        boolean useSSL = false;
        EnumSet<DigestEchoServer.HttpAuthType> types =
                EnumSet.complementOf(EnumSet.of(DigestEchoServer.HttpAuthType.PROXY305));
        if (args != null && args.length >= 1) {
            useSSL = "SSL".equals(args[0]);
            if (args.length > 1) {
                List<DigestEchoServer.HttpAuthType> httpAuthTypes =
                        Stream.of(Arrays.copyOfRange(args, 1, args.length))
                                .map(DigestEchoServer.HttpAuthType::valueOf)
                                .collect(Collectors.toList());
                types = EnumSet.copyOf(httpAuthTypes);
            }
        }
        try {
            for (DigestEchoServer.HttpAuthType authType : types) {
                // The test server does not support PROXY305 properly
                if (authType == DigestEchoServer.HttpAuthType.PROXY305) continue;
                EnumSet<DigestEchoServer.HttpAuthSchemeType> basics =
                        EnumSet.of(DigestEchoServer.HttpAuthSchemeType.BASICSERVER,
                                DigestEchoServer.HttpAuthSchemeType.BASIC);
                for (DigestEchoServer.HttpAuthSchemeType authScheme : basics) {
                    DigestEchoClient dec = new DigestEchoClient(useSSL,
                            authScheme,
                            authType);
                    for (Version version : HttpClient.Version.values()) {
                        for (boolean expectContinue : BOOLEANS) {
                            for (boolean async : BOOLEANS) {
                                for (boolean preemptive : BOOLEANS) {
                                    dec.testBasic(version, async,
                                            expectContinue, preemptive);
                                }
                            }
                        }
                    }
                }
                EnumSet<DigestEchoServer.HttpAuthSchemeType> digests =
                        EnumSet.of(DigestEchoServer.HttpAuthSchemeType.DIGEST);
                for (DigestEchoServer.HttpAuthSchemeType authScheme : digests) {
                    DigestEchoClient dec = new DigestEchoClient(useSSL,
                            authScheme,
                            authType);
                    for (Version version : HttpClient.Version.values()) {
                        for (boolean expectContinue : BOOLEANS) {
                            for (boolean async : BOOLEANS) {
                                dec.testDigest(version, async, expectContinue);
                            }
                        }
                    }
                }
            }
        } finally {
            EchoServers.stop();
            System.out.println(" ---------------------------------------------------------- ");
            System.out.println(String.format("DigestEchoClient %s %s", useSSL ? "SSL" : "CLEAR", types));
            System.out.println(String.format("Created %d clients and %d servers",
                    clientCount.get(), serverCount.get()));
            System.out.println(String.format("basics:  %d requests sent, %d ns / req",
                    basicCount.get(), basics.get()));
            System.out.println(String.format("digests: %d requests sent, %d ns / req",
                    digestCount.get(), digests.get()));
            System.out.println(" ---------------------------------------------------------- ");
        }
    }

    final static AtomicLong basics = new AtomicLong();
    final static AtomicLong basicCount = new AtomicLong();
    // @Test
    void testBasic(HttpClient.Version version, boolean async,
                   boolean expectContinue, boolean preemptive)
        throws Exception
    {
        final boolean addHeaders = authScheme == DigestEchoServer.HttpAuthSchemeType.BASICSERVER;
        // !preemptive has no meaning if we don't handle the authorization
        // headers ourselves
        if (!preemptive && !addHeaders) return;

        out.println(format("*** testBasic: version: %s,  async: %s, useSSL: %s, " +
                        "authScheme: %s, authType: %s, expectContinue: %s preemptive: %s***",
                version, async, useSSL, authScheme, authType, expectContinue, preemptive));

        DigestEchoServer server = EchoServers.of(useSSL ? "https" : "http", authType, authScheme);
        URI uri = DigestEchoServer.uri(useSSL ? "https" : "http", server.getServerAddress(), "/foo/");

        HttpClient client = newHttpClient(server);
        HttpResponse<String> r;
        CompletableFuture<HttpResponse<String>> cf1;
        String auth = null;

        try {
            for (int i=0; i<data.length; i++) {
                out.println(DigestEchoServer.now() + " ----- iteration " + i + " -----");
                List<String> lines = List.of(Arrays.copyOfRange(data, 0, i+1));
                assert lines.size() == i + 1;
                String body = lines.stream().collect(Collectors.joining("\r\n"));
                HttpRequest.BodyPublisher reqBody = HttpRequest.BodyPublisher.fromString(body);
                HttpRequest.Builder builder = HttpRequest.newBuilder(uri).version(version)
                        .POST(reqBody).expectContinue(expectContinue);
                boolean isTunnel = isProxy(authType) && useSSL;
                if (addHeaders) {
                    // handle authentication ourselves
                    assert !client.authenticator().isPresent();
                    if (auth == null) auth = "Basic " + getBasicAuth("arthur");
                    try {
                        if ((i > 0 || preemptive) && (!isTunnel || i == 0)) {
                            // In case of a SSL tunnel through proxy then only the
                            // first request should require proxy authorization
                            // Though this might be invalidated if the server decides
                            // to close the connection...
                            out.println(String.format("%s adding %s: %s",
                                    DigestEchoServer.now(),
                                    authorizationKey(authType),
                                    auth));
                            builder = builder.header(authorizationKey(authType), auth);
                        }
                    } catch (IllegalArgumentException x) {
                        throw x;
                    }
                } else {
                    // let the stack do the authentication
                    assert client.authenticator().isPresent();
                }
                long start = System.nanoTime();
                HttpRequest request = builder.build();
                HttpResponse<Stream<String>> resp;
                try {
                    if (async) {
                        resp = client.sendAsync(request, asLines()).join();
                    } else {
                        resp = client.send(request, asLines());
                    }
                } catch (Throwable t) {
                    long stop = System.nanoTime();
                    synchronized (basicCount) {
                        long n = basicCount.getAndIncrement();
                        basics.set((basics.get() * n + (stop - start)) / (n + 1));
                    }
                    // unwrap CompletionException
                    if (t instanceof CompletionException) {
                        assert t.getCause() != null;
                        t = t.getCause();
                    }
                    throw new RuntimeException("Unexpected exception: " + t, t);
                }

                if (addHeaders && !preemptive && i==0) {
                    assert resp.statusCode() == 401 || resp.statusCode() == 407;
                    request = HttpRequest.newBuilder(uri).version(version)
                            .POST(reqBody).header(authorizationKey(authType), auth).build();
                    if (async) {
                        resp = client.sendAsync(request, asLines()).join();
                    } else {
                        resp = client.send(request, asLines());
                    }
                }
                assert resp.statusCode() == 200;
                List<String> respLines = resp.body().collect(Collectors.toList());
                long stop = System.nanoTime();
                synchronized (basicCount) {
                    long n = basicCount.getAndIncrement();
                    basics.set((basics.get() * n + (stop - start)) / (n + 1));
                }
                if (!lines.equals(respLines)) {
                    throw new RuntimeException("Unexpected response: " + respLines);
                }
            }
        } finally {
        }
        System.out.println("OK");
    }

    String getBasicAuth(String username) {
        StringBuilder builder = new StringBuilder(username);
        builder.append(':');
        for (char c : DigestEchoServer.AUTHENTICATOR.getPassword(username)) {
            builder.append(c);
        }
        return Base64.getEncoder().encodeToString(builder.toString().getBytes(StandardCharsets.UTF_8));
    }

    final static AtomicLong digests = new AtomicLong();
    final static AtomicLong digestCount = new AtomicLong();
    // @Test
    void testDigest(HttpClient.Version version, boolean async, boolean expectContinue)
            throws Exception
    {
        out.println(format("*** testDigest: version: %s,  async: %s, useSSL: %s, " +
                        "authScheme: %s, authType: %s, expectContinue: %s  ***",
                version, async, useSSL, authScheme, authType, expectContinue));
        DigestEchoServer server = EchoServers.of(useSSL ? "https" : "http", authType, authScheme);

        URI uri = DigestEchoServer.uri(useSSL ? "https" : "http", server.getServerAddress(), "/foo/");

        HttpClient client = newHttpClient(server);
        HttpResponse<String> r;
        CompletableFuture<HttpResponse<String>> cf1;
        byte[] cnonce = new byte[16];
        String cnonceStr = null;
        DigestEchoServer.DigestResponse challenge = null;

        try {
            for (int i=0; i<data.length; i++) {
                out.println(DigestEchoServer.now() + "----- iteration " + i + " -----");
                List<String> lines = List.of(Arrays.copyOfRange(data, 0, i+1));
                assert lines.size() == i + 1;
                String body = lines.stream().collect(Collectors.joining("\r\n"));
                HttpRequest.BodyPublisher reqBody = HttpRequest.BodyPublisher.fromString(body);
                HttpRequest.Builder reqBuilder = HttpRequest
                        .newBuilder(uri).version(version).POST(reqBody)
                        .expectContinue(expectContinue);

                boolean isTunnel = isProxy(authType) && useSSL;
                String digestMethod = isTunnel ? "CONNECT" : "POST";

                // In case of a tunnel connection only the first request
                // which establishes the tunnel needs to authenticate with
                // the proxy.
                if (challenge != null && !isTunnel) {
                    assert cnonceStr != null;
                    String auth = digestResponse(uri, digestMethod, challenge, cnonceStr);
                    try {
                        reqBuilder = reqBuilder.header(authorizationKey(authType), auth);
                    } catch (IllegalArgumentException x) {
                        throw x;
                    }
                }

                long start = System.nanoTime();
                HttpRequest request = reqBuilder.build();
                HttpResponse<Stream<String>> resp;
                if (async) {
                    resp = client.sendAsync(request, asLines()).join();
                } else {
                    resp = client.send(request, asLines());
                }
                System.out.println(resp);
                assert challenge != null || resp.statusCode() == 401 || resp.statusCode() == 407;
                if (resp.statusCode() == 401 || resp.statusCode() == 407) {
                    // This assert may need to be relaxed if our server happened to
                    // decide to close the tunnel connection, in which case we would
                    // receive 407 again...
                    assert challenge == null || !isTunnel
                            : "No proxy auth should be required after establishing an SSL tunnel";

                    System.out.println("Received " + resp.statusCode() + " answering challenge...");
                    random.nextBytes(cnonce);
                    cnonceStr = new BigInteger(1, cnonce).toString(16);
                    System.out.println("Response headers: " + resp.headers());
                    Optional<String> authenticateOpt = resp.headers().firstValue(authenticateKey(authType));
                    String authenticate = authenticateOpt.orElseThrow(
                            () -> new RuntimeException(authenticateKey(authType) + ": not found"));
                    assert authenticate.startsWith("Digest ");
                    HeaderParser hp = new HeaderParser(authenticate.substring("Digest ".length()));
                    String qop = hp.findValue("qop");
                    String nonce = hp.findValue("nonce");
                    if (qop == null && nonce == null) {
                        throw new RuntimeException("QOP and NONCE not found");
                    }
                    challenge = DigestEchoServer.DigestResponse
                            .create(authenticate.substring("Digest ".length()));
                    String auth = digestResponse(uri, digestMethod, challenge, cnonceStr);
                    try {
                        request = HttpRequest.newBuilder(uri).version(version)
                            .POST(reqBody).header(authorizationKey(authType), auth).build();
                    } catch (IllegalArgumentException x) {
                        throw x;
                    }

                    if (async) {
                        resp = client.sendAsync(request, asLines()).join();
                    } else {
                        resp = client.send(request, asLines());
                    }
                    System.out.println(resp);
                }
                assert resp.statusCode() == 200;
                List<String> respLines = resp.body().collect(Collectors.toList());
                long stop = System.nanoTime();
                synchronized (digestCount) {
                    long n = digestCount.getAndIncrement();
                    digests.set((digests.get() * n + (stop - start)) / (n + 1));
                }
                if (!lines.equals(respLines)) {
                    throw new RuntimeException("Unexpected response: " + respLines);
                }
            }
        } finally {
        }
        System.out.println("OK");
    }

    // WARNING: This is not a full fledged implementation of DIGEST.
    // It does contain bugs and inaccuracy.
    static String digestResponse(URI uri, String method, DigestEchoServer.DigestResponse challenge, String cnonce)
            throws NoSuchAlgorithmException {
        int nc = NC.incrementAndGet();
        DigestEchoServer.DigestResponse response1 = new DigestEchoServer.DigestResponse("earth",
                "arthur", challenge.nonce, cnonce, String.valueOf(nc), uri.toASCIIString(),
                challenge.algorithm, challenge.qop, challenge.opaque, null);
        String response = DigestEchoServer.DigestResponse.computeDigest(true, method,
                DigestEchoServer.AUTHENTICATOR.getPassword("arthur"), response1);
        String auth = "Digest username=\"arthur\", realm=\"earth\""
                + ", response=\"" + response + "\", uri=\""+uri.toASCIIString()+"\""
                + ", qop=\"" + response1.qop + "\", cnonce=\"" + response1.cnonce
                + "\", nc=\"" + nc + "\", nonce=\"" + response1.nonce + "\"";
        if (response1.opaque != null) {
            auth = auth + ", opaque=\"" + response1.opaque + "\"";
        }
        return auth;
    }

    static String authenticateKey(DigestEchoServer.HttpAuthType authType) {
        switch (authType) {
            case SERVER: return "www-authenticate";
            case SERVER307: return "www-authenticate";
            case PROXY: return "proxy-authenticate";
            case PROXY305: return "proxy-authenticate";
            default: throw new InternalError("authType: " + authType);
        }
    }

    static String authorizationKey(DigestEchoServer.HttpAuthType authType) {
        switch (authType) {
            case SERVER: return "authorization";
            case SERVER307: return "Authorization";
            case PROXY: return "Proxy-Authorization";
            case PROXY305: return "proxy-Authorization";
            default: throw new InternalError("authType: " + authType);
        }
    }

    static boolean isProxy(DigestEchoServer.HttpAuthType authType) {
        switch (authType) {
            case SERVER: return false;
            case SERVER307: return false;
            case PROXY: return true;
            case PROXY305: return true;
            default: throw new InternalError("authType: " + authType);
        }
    }
}
