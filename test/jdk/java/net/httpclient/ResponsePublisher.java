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

/*
 * @test
 * @summary Tests an asynchronous BodySubscriber that completes
 *          immediately with a Publisher<List<ByteBuffer>>
 * @library /lib/testlibrary http2/server
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules java.base/sun.net.www.http
 *          java.net.http/jdk.internal.net.http.common
 *          java.net.http/jdk.internal.net.http.frame
 *          java.net.http/jdk.internal.net.http.hpack
 * @run testng/othervm ResponsePublisher
 */

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.testlibrary.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandler;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpResponse.BodySubscribers;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Flow;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.atomic.AtomicReference;

import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;

public class ResponsePublisher {

    SSLContext sslContext;
    HttpServer httpTestServer;         // HTTP/1.1    [ 4 servers ]
    HttpsServer httpsTestServer;       // HTTPS/1.1
    Http2TestServer http2TestServer;   // HTTP/2 ( h2c )
    Http2TestServer https2TestServer;  // HTTP/2 ( h2  )
    String httpURI_fixed;
    String httpURI_chunk;
    String httpsURI_fixed;
    String httpsURI_chunk;
    String http2URI_fixed;
    String http2URI_chunk;
    String https2URI_fixed;
    String https2URI_chunk;

    static final int ITERATION_COUNT = 10;
    // a shared executor helps reduce the amount of threads created by the test
    static final Executor executor = Executors.newCachedThreadPool();

    @DataProvider(name = "variants")
    public Object[][] variants() {
        return new Object[][]{
                { httpURI_fixed,    false },
                { httpURI_chunk,    false },
                { httpsURI_fixed,   false },
                { httpsURI_chunk,   false },
                { http2URI_fixed,   false },
                { http2URI_chunk,   false },
                { https2URI_fixed,  false,},
                { https2URI_chunk,  false },

                { httpURI_fixed,    true },
                { httpURI_chunk,    true },
                { httpsURI_fixed,   true },
                { httpsURI_chunk,   true },
                { http2URI_fixed,   true },
                { http2URI_chunk,   true },
                { https2URI_fixed,  true,},
                { https2URI_chunk,  true },
        };
    }

    HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                         .executor(executor)
                         .sslContext(sslContext)
                         .build();
    }

    @Test(dataProvider = "variants")
    public void testNoBody(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                    .build();
            BodyHandler<Publisher<List<ByteBuffer>>> handler = new PublishingBodyHandler();
            HttpResponse<Publisher<List<ByteBuffer>>> response = client.send(req, handler);
            // We can reuse our BodySubscribers implementations to subscribe to the
            // Publisher<List<ByteBuffer>>
            BodySubscriber<String> ofString = BodySubscribers.ofString(UTF_8);
            // get the Publisher<List<ByteBuffer>> and
            // subscribe to it.
            response.body().subscribe(ofString);
            // Get the final result and compare it with the expected body
            String body = ofString.getBody().toCompletableFuture().get();
            assertEquals(body, "");
        }
    }

    @Test(dataProvider = "variants")
    public void testNoBodyAsync(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                    .build();
            BodyHandler<Publisher<List<ByteBuffer>>> handler = new PublishingBodyHandler();
            // We can reuse our BodySubscribers implementations to subscribe to the
            // Publisher<List<ByteBuffer>>
            BodySubscriber<String> ofString = BodySubscribers.ofString(UTF_8);
            CompletableFuture<String> result =
                    client.sendAsync(req, handler).thenCompose(
                            (responsePublisher) -> {
                                // get the Publisher<List<ByteBuffer>> and
                                // subscribe to it.
                                responsePublisher.body().subscribe(ofString);
                                return ofString.getBody();
                            });
            // Get the final result and compare it with the expected body
            assertEquals(result.get(), "");
        }
    }

    @Test(dataProvider = "variants")
    public void testAsString(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri+"/withBody"))
                    .build();
            BodyHandler<Publisher<List<ByteBuffer>>> handler = new PublishingBodyHandler();
            HttpResponse<Publisher<List<ByteBuffer>>> response = client.send(req, handler);
            // We can reuse our BodySubscribers implementations to subscribe to the
            // Publisher<List<ByteBuffer>>
            BodySubscriber<String> ofString = BodySubscribers.ofString(UTF_8);
            // get the Publisher<List<ByteBuffer>> and
            // subscribe to it.
            response.body().subscribe(ofString);
            // Get the final result and compare it with the expected body
            String body = ofString.getBody().toCompletableFuture().get();
            assertEquals(body, WITH_BODY);
        }
    }

    @Test(dataProvider = "variants")
    public void testAsStringAsync(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri+"/withBody"))
                    .build();
            BodyHandler<Publisher<List<ByteBuffer>>> handler = new PublishingBodyHandler();
            // We can reuse our BodySubscribers implementations to subscribe to the
            // Publisher<List<ByteBuffer>>
            BodySubscriber<String> ofString = BodySubscribers.ofString(UTF_8);
            CompletableFuture<String> result = client.sendAsync(req, handler)
                    .thenCompose((responsePublisher) -> {
                        // get the Publisher<List<ByteBuffer>> and
                        // subscribe to it.
                        responsePublisher.body().subscribe(ofString);
                        return ofString.getBody();
                    });
            // Get the final result and compare it with the expected body
            String body = result.get();
            assertEquals(body, WITH_BODY);
        }
    }

    // A BodyHandler that returns PublishingBodySubscriber instances
    static class PublishingBodyHandler implements BodyHandler<Publisher<List<ByteBuffer>>> {
        @Override
        public BodySubscriber<Publisher<List<ByteBuffer>>> apply(int statusCode, HttpHeaders responseHeaders) {
            assertEquals(statusCode, 200);
            return new PublishingBodySubscriber();
        }
    }

    // A BodySubscriber that returns a Publisher<List<ByteBuffer>>
    static class PublishingBodySubscriber implements BodySubscriber<Publisher<List<ByteBuffer>>> {
        private final CompletableFuture<Flow.Subscription> subscriptionCF = new CompletableFuture<>();
        private final CompletableFuture<Flow.Subscriber<? super List<ByteBuffer>>> subscribedCF = new CompletableFuture<>();
        private AtomicReference<Flow.Subscriber<? super List<ByteBuffer>>> subscriberRef = new AtomicReference<>();
        private final CompletionStage<Publisher<List<ByteBuffer>>> body =
                //subscriptionCF.thenCompose((s) -> CompletableFuture.completedStage(this::subscribe));
                CompletableFuture.completedStage(this::subscribe);

        private void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            Objects.requireNonNull(subscriber, "subscriber must not be null");
            if (subscriberRef.compareAndSet(null, subscriber)) {
                subscriptionCF.thenAccept((s) -> {
                    subscriber.onSubscribe(s);
                    subscribedCF.complete(subscriber);
                });
            } else {
                subscriber.onSubscribe(new Flow.Subscription() {
                    @Override public void request(long n) { }
                    @Override public void cancel() { }
                });
                subscriber.onError(new IOException("This publisher has already one subscriber"));
            }
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            subscriptionCF.complete(subscription);
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            assert subscriptionCF.isDone(); // cannot be called before onSubscribe()
            Flow.Subscriber<? super List<ByteBuffer>> subscriber = subscriberRef.get();
            assert subscriber != null; // cannot be called before subscriber calls request(1)
            subscriber.onNext(item);
        }

        @Override
        public void onError(Throwable throwable) {
            assert subscriptionCF.isDone(); // cannot be called before onSubscribe()
            // onError can be called before request(1), and therefore can
            // be called before subscriberRef is set.
            subscribedCF.thenAccept(s -> s.onError(throwable));
        }

        @Override
        public void onComplete() {
            assert subscriptionCF.isDone(); // cannot be called before onSubscribe()
            // onComplete can be called before request(1), and therefore can
            // be called before subscriberRef is set.
            subscribedCF.thenAccept(s -> s.onComplete());
        }

        @Override
        public CompletionStage<Publisher<List<ByteBuffer>>> getBody() {
            return body;
        }
    }

    static String serverAuthority(HttpServer server) {
        return InetAddress.getLoopbackAddress().getHostName() + ":"
                + server.getAddress().getPort();
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        // HTTP/1.1
        HttpHandler h1_fixedLengthHandler = new HTTP1_FixedLengthHandler();
        HttpHandler h1_chunkHandler = new HTTP1_ChunkedHandler();
        InetSocketAddress sa = new InetSocketAddress(InetAddress.getLoopbackAddress(), 0);
        httpTestServer = HttpServer.create(sa, 0);
        httpTestServer.createContext("/http1/fixed", h1_fixedLengthHandler);
        httpTestServer.createContext("/http1/chunk", h1_chunkHandler);
        httpURI_fixed = "http://" + serverAuthority(httpTestServer) + "/http1/fixed";
        httpURI_chunk = "http://" + serverAuthority(httpTestServer) + "/http1/chunk";

        httpsTestServer = HttpsServer.create(sa, 0);
        httpsTestServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsTestServer.createContext("/https1/fixed", h1_fixedLengthHandler);
        httpsTestServer.createContext("/https1/chunk", h1_chunkHandler);
        httpsURI_fixed = "https://" + serverAuthority(httpsTestServer) + "/https1/fixed";
        httpsURI_chunk = "https://" + serverAuthority(httpsTestServer) + "/https1/chunk";

        // HTTP/2
        Http2Handler h2_fixedLengthHandler = new HTTP2_FixedLengthHandler();
        Http2Handler h2_chunkedHandler = new HTTP2_VariableHandler();

        http2TestServer = new Http2TestServer("localhost", false, 0);
        http2TestServer.addHandler(h2_fixedLengthHandler, "/http2/fixed");
        http2TestServer.addHandler(h2_chunkedHandler, "/http2/chunk");
        http2URI_fixed = "http://" + http2TestServer.serverAuthority() + "/http2/fixed";
        http2URI_chunk = "http://" + http2TestServer.serverAuthority() + "/http2/chunk";

        https2TestServer = new Http2TestServer("localhost", true, 0);
        https2TestServer.addHandler(h2_fixedLengthHandler, "/https2/fixed");
        https2TestServer.addHandler(h2_chunkedHandler, "/https2/chunk");
        https2URI_fixed = "https://" + https2TestServer.serverAuthority() + "/https2/fixed";
        https2URI_chunk = "https://" + https2TestServer.serverAuthority() + "/https2/chunk";

        httpTestServer.start();
        httpsTestServer.start();
        http2TestServer.start();
        https2TestServer.start();
    }

    @AfterTest
    public void teardown() throws Exception {
        httpTestServer.stop(0);
        httpsTestServer.stop(0);
        http2TestServer.stop();
        https2TestServer.stop();
    }

    static final String WITH_BODY = "Lorem ipsum dolor sit amet, consectetur" +
            " adipiscing elit, sed do eiusmod tempor incididunt ut labore et" +
            " dolore magna aliqua. Ut enim ad minim veniam, quis nostrud" +
            " exercitation ullamco laboris nisi ut aliquip ex ea" +
            " commodo consequat. Duis aute irure dolor in reprehenderit in " +
            "voluptate velit esse cillum dolore eu fugiat nulla pariatur." +
            " Excepteur sint occaecat cupidatat non proident, sunt in culpa qui" +
            " officia deserunt mollit anim id est laborum.";

    static class HTTP1_FixedLengthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            out.println("HTTP1_FixedLengthHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            if (t.getRequestURI().getPath().endsWith("/withBody")) {
                byte[] bytes = WITH_BODY.getBytes(UTF_8);
                t.sendResponseHeaders(200, bytes.length);  // body
                try (OutputStream os = t.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                t.sendResponseHeaders(200, -1);  //no body
            }
        }
    }

    static class HTTP1_ChunkedHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            out.println("HTTP1_ChunkedHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            t.sendResponseHeaders(200, 0);  //chunked
            if (t.getRequestURI().getPath().endsWith("/withBody")) {
                byte[] bytes = WITH_BODY.getBytes(UTF_8);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                t.getResponseBody().close();   // no body
            }
        }
    }

    static class HTTP2_FixedLengthHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange t) throws IOException {
            out.println("HTTP2_FixedLengthHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            if (t.getRequestURI().getPath().endsWith("/withBody")) {
                byte[] bytes = WITH_BODY.getBytes(UTF_8);
                t.sendResponseHeaders(200, bytes.length);  // body
                try (OutputStream os = t.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                t.sendResponseHeaders(200, -1);  //no body
            }
        }
    }

    static class HTTP2_VariableHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange t) throws IOException {
            out.println("HTTP2_VariableHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            t.sendResponseHeaders(200, 0);  //chunked
            if (t.getRequestURI().getPath().endsWith("/withBody")) {
                byte[] bytes = WITH_BODY.getBytes(UTF_8);
                try (OutputStream os = t.getResponseBody()) {
                    os.write(bytes);
                }
            } else {
                t.getResponseBody().close();   // no body
            }
        }
    }
}
