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

/**
 * @test
 * @bug 8087112
 * @modules java.net.http
 *          jdk.httpserver
 * @run main/othervm RedirectMethodChange
 */

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.Headers;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.*;
import static java.nio.charset.StandardCharsets.US_ASCII;

public class RedirectMethodChange {

    static volatile boolean ok;
    static final String RESPONSE = "Hello world";
    static final String POST_BODY = "This is the POST body 123909090909090";
    static volatile URI TEST_URI, REDIRECT_URI;
    static volatile HttpClient client;

    public static void main(String[] args) throws Exception {
        //Logger l = Logger.getLogger("com.sun.net.httpserver");
        //l.setLevel(Level.ALL);
        //ConsoleHandler ch = new ConsoleHandler();
        //ch.setLevel(Level.ALL);
        //l.addHandler(ch);

        InetSocketAddress addr = new InetSocketAddress(InetAddress.getLoopbackAddress(),0);
        HttpServer server = HttpServer.create(addr, 10);
        ExecutorService e = Executors.newCachedThreadPool();
        Handler h = new Handler();
        HttpContext serverContext = server.createContext("/test/", h);
        HttpContext serverContext1 = server.createContext("/redirect/", h);
        int port = server.getAddress().getPort();
        System.out.println("Server address = " + server.getAddress());

        server.setExecutor(e);
        server.start();
        client = HttpClient.newBuilder()
                      .followRedirects(HttpClient.Redirect.NORMAL)
                      .build();

        try {
            TEST_URI = new URI("http://localhost:" + Integer.toString(port) + "/test/foo");
            REDIRECT_URI = new URI("http://localhost:" + Integer.toString(port) + "/redirect/foo");
            test("GET", 301, "GET");
            test("GET", 302, "GET");
            test("GET", 303, "GET");
            test("GET", 307, "GET");
            test("GET", 308, "GET");
            test("POST", 301, "GET");
            test("POST", 302, "GET");
            test("POST", 303, "GET");
            test("POST", 307, "POST");
            test("POST", 308, "POST");
            test("PUT", 301, "PUT");
            test("PUT", 302, "PUT");
            test("PUT", 303, "GET");
            test("PUT", 307, "PUT");
            test("PUT", 308, "PUT");
        } finally {
            server.stop(0);
            e.shutdownNow();
        }
        System.out.println("OK");
    }

    static HttpRequest.BodyPublisher getRequestBodyFor(String method) {
        switch (method) {
            case "GET":
            case "DELETE":
            case "HEAD":
                return HttpRequest.BodyPublishers.noBody();
            case "POST":
            case "PUT":
                return HttpRequest.BodyPublishers.ofString(POST_BODY);
            default:
                throw new InternalError();
        }
    }

    static void test(String method, int redirectCode, String expectedMethod) throws Exception {
        System.err.printf("Test %s, %d, %s %s\n", method, redirectCode, expectedMethod, TEST_URI.toString());
        HttpRequest req = HttpRequest.newBuilder(TEST_URI)
            .method(method, getRequestBodyFor(method))
            .header("X-Redirect-Code", Integer.toString(redirectCode))
            .header("X-Expect-Method", expectedMethod)
            .build();
        HttpResponse<String> resp = client.send(req, BodyHandlers.ofString());

        if (resp.statusCode() != 200 || !resp.body().equals(RESPONSE)) {
            String msg = "Failed: " + resp.statusCode();
            throw new RuntimeException(msg);
        }
    }

    /**
     * request to /test is first test. The following headers are checked:
     * X-Redirect-Code: nnn    <the redirect code to send back>
     * X-Expect-Method: the method that the client should use for the next request
     *
     * Following request should be to /redirect and should use the method indicated
     * previously. If all ok, return a 200 response. Otherwise 500 error.
     */
    static class Handler implements HttpHandler {

        volatile boolean inTest = false;
        volatile String expectedMethod;

        boolean readAndCheckBody(HttpExchange e) throws IOException {
            InputStream is = e.getRequestBody();
            String method = e.getRequestMethod();
            String requestBody = new String(is.readAllBytes(), US_ASCII);
            is.close();
            if (method.equals("POST") || method.equals("PUT")) {
                if (!requestBody.equals(POST_BODY)) {
                    e.sendResponseHeaders(503, -1);
                    return false;
                }
            }
            return true;
        }

        @Override
        public void handle(HttpExchange he) throws IOException {
            boolean newtest = he.getRequestURI().getPath().startsWith("/test");
            if ((newtest && inTest) || (!newtest && !inTest)) {
                he.sendResponseHeaders(500, -1);
            }
            if (newtest) {
                String method = he.getRequestMethod();
                Headers hdrs = he.getRequestHeaders();
                int redirectCode = Integer.parseInt(hdrs.getFirst("X-Redirect-Code"));
                expectedMethod = hdrs.getFirst("X-Expect-Method");
                boolean ok = readAndCheckBody(he);
                if (!ok)
                    return;
                hdrs = he.getResponseHeaders();
                hdrs.set("Location", REDIRECT_URI.toString());
                he.sendResponseHeaders(redirectCode, -1);
                inTest = true;
            } else {
                // should be the redirect
                if (!he.getRequestURI().getPath().startsWith("/redirect")) {
                    he.sendResponseHeaders(501, -1);
                } else if (!he.getRequestMethod().equals(expectedMethod)) {
                    System.err.println("Expected: " + expectedMethod + " Got: " + he.getRequestMethod());
                    he.sendResponseHeaders(504, -1);
                } else {
                    boolean ok = readAndCheckBody(he);
                    if (ok) {
                        he.sendResponseHeaders(200, RESPONSE.length());
                        OutputStream os = he.getResponseBody();
                        os.write(RESPONSE.getBytes(US_ASCII));
                        os.close();
                    }
                }
                inTest = false;
            }
        }
    }
}
