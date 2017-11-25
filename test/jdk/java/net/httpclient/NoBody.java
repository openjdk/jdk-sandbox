/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8161157
 * @summary Test response body handlers/subscribers when there is no body
 * @library /lib/testlibrary http2/server
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules java.base/sun.net.www.http
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.common
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.frame
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.hpack
 * @run testng/othervm NoBody
 */

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpResponse.BodyHandler;
import javax.net.ssl.SSLContext;
import jdk.testlibrary.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import static java.lang.System.out;
import static java.nio.charset.StandardCharsets.UTF_8;
import static jdk.incubator.http.HttpRequest.BodyPublisher.fromString;
import static jdk.incubator.http.HttpResponse.BodyHandler.asByteArray;
import static jdk.incubator.http.HttpResponse.BodyHandler.asByteArrayConsumer;
import static jdk.incubator.http.HttpResponse.BodyHandler.asFile;
import static jdk.incubator.http.HttpResponse.BodyHandler.asInputStream;
import static jdk.incubator.http.HttpResponse.BodyHandler.asString;
import static jdk.incubator.http.HttpResponse.BodyHandler.buffering;
import static jdk.incubator.http.HttpResponse.BodyHandler.discard;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class NoBody {

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

    static final String SIMPLE_STRING = "Hello world. Goodbye world";
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
    public void testAsString(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                                         .PUT(fromString(SIMPLE_STRING))
                                         .build();
            BodyHandler<String> handler = i % 2 == 0 ? asString() : asString(UTF_8);
            HttpResponse<String> response = client.send(req, handler);
            String body = response.body();
            assertEquals(body, "");
        }
    }

    @Test(dataProvider = "variants")
    public void testAsFile(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                                         .PUT(fromString(SIMPLE_STRING))
                                         .build();
            Path p = Paths.get("NoBody_testAsFile.txt");
            HttpResponse<Path> response = client.send(req, asFile(p));
            Path bodyPath = response.body();
            assertTrue(Files.exists(bodyPath));
            assertEquals(Files.size(bodyPath), 0);
        }
    }

    @Test(dataProvider = "variants")
    public void testAsByteArray(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                                         .PUT(fromString(SIMPLE_STRING))
                                         .build();
            HttpResponse<byte[]> response = client.send(req, asByteArray());
            byte[] body = response.body();
            assertEquals(body.length, 0);
        }
    }

    volatile boolean consumerHasBeenCalled;
    @Test(dataProvider = "variants")
    public void testAsByteArrayConsumer(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                                         .PUT(fromString(SIMPLE_STRING))
                                         .build();
            Consumer<Optional<byte[]>>  consumer = oba -> {
                consumerHasBeenCalled = true;
                oba.ifPresent(ba -> fail("Unexpected non-empty optional:" + ba));
            };
            consumerHasBeenCalled = false;
            client.send(req, asByteArrayConsumer(consumer));
            assertTrue(consumerHasBeenCalled);
        }
    }

    @Test(dataProvider = "variants")
    public void testAsInputStream(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                                         .PUT(fromString(SIMPLE_STRING))
                                         .build();
            HttpResponse<InputStream> response = client.send(req, asInputStream());
            byte[] body = response.body().readAllBytes();
            assertEquals(body.length, 0);
        }
    }

    @Test(dataProvider = "variants")
    public void testBuffering(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                                         .PUT(fromString(SIMPLE_STRING))
                                         .build();
            HttpResponse<byte[]> response = client.send(req, buffering(asByteArray(), 1024));
            byte[] body = response.body();
            assertEquals(body.length, 0);
        }
    }

    @Test(dataProvider = "variants")
    public void testDiscard(String uri, boolean sameClient) throws Exception {
        HttpClient client = null;
        for (int i=0; i< ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                                         .PUT(fromString(SIMPLE_STRING))
                                         .build();
            Object obj = new Object();
            HttpResponse<Object> response = client.send(req, discard(obj));
            assertEquals(response.body(), obj);
        }
    }


    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        // HTTP/1.1
        HttpHandler h1_fixedLengthNoBodyHandler = new HTTP1_FixedLengthNoBodyHandler();
        HttpHandler h1_chunkNoBodyHandler = new HTTP1_ChunkedNoBodyHandler();
        InetSocketAddress sa = new InetSocketAddress("localhost", 0);
        httpTestServer = HttpServer.create(sa, 0);
        httpTestServer.createContext("/http1/noBodyFixed", h1_fixedLengthNoBodyHandler);
        httpTestServer.createContext("/http1/noBodyChunk", h1_chunkNoBodyHandler);
        httpURI_fixed = "http://127.0.0.1:" + httpTestServer.getAddress().getPort() + "/http1/noBodyFixed";
        httpURI_chunk = "http://127.0.0.1:" + httpTestServer.getAddress().getPort() + "/http1/noBodyChunk";

        httpsTestServer = HttpsServer.create(sa, 0);
        httpsTestServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsTestServer.createContext("/https1/noBodyFixed", h1_fixedLengthNoBodyHandler);
        httpsTestServer.createContext("/https1/noBodyChunk", h1_chunkNoBodyHandler);
        httpsURI_fixed = "https://127.0.0.1:" + httpsTestServer.getAddress().getPort() + "/https1/noBodyFixed";
        httpsURI_chunk = "https://127.0.0.1:" + httpsTestServer.getAddress().getPort() + "/https1/noBodyChunk";

        // HTTP/2
        Http2Handler h2_fixedLengthNoBodyHandler = new HTTP2_FixedLengthNoBodyHandler();
        Http2Handler h2_chunkedNoBodyHandler = new HTTP2_ChunkedNoBodyHandler();

        http2TestServer = new Http2TestServer("127.0.0.1", false, 0);
        http2TestServer.addHandler(h2_fixedLengthNoBodyHandler, "/http2/noBodyFixed");
        http2TestServer.addHandler(h2_chunkedNoBodyHandler, "/http2/noBodyChunk");
        int port = http2TestServer.getAddress().getPort();
        http2URI_fixed = "http://127.0.0.1:" + port + "/http2/noBodyFixed";
        http2URI_chunk = "http://127.0.0.1:" + port + "/http2/noBodyChunk";

        https2TestServer = new Http2TestServer("127.0.0.1", true, 0);
        https2TestServer.addHandler(h2_fixedLengthNoBodyHandler, "/https2/noBodyFixed");
        https2TestServer.addHandler(h2_chunkedNoBodyHandler, "/https2/noBodyChunk");
        port = https2TestServer.getAddress().getPort();
        https2URI_fixed = "https://127.0.0.1:" + port + "/https2/noBodyFixed";
        https2URI_chunk = "https://127.0.0.1:" + port + "/https2/noBodyChunk";

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

    static class HTTP1_FixedLengthNoBodyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            //out.println("NoBodyHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            t.sendResponseHeaders(200, -1); // no body
        }
    }

    static class HTTP1_ChunkedNoBodyHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            //out.println("NoBodyHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            t.sendResponseHeaders(200, 0); // chunked
            t.getResponseBody().close();  // write nothing
        }
    }

    static class HTTP2_FixedLengthNoBodyHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange t) throws IOException {
            //out.println("NoBodyHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            t.sendResponseHeaders(200, 0);
        }
    }

    static class HTTP2_ChunkedNoBodyHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange t) throws IOException {
            //out.println("NoBodyHandler received request to " + t.getRequestURI());
            try (InputStream is = t.getRequestBody()) {
                is.readAllBytes();
            }
            t.sendResponseHeaders(200, -1);
            t.getResponseBody().close();  // write nothing
        }
    }
}
