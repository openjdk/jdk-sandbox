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

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Flow;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.HttpRequest;
import jdk.incubator.http.HttpResponse;
import jdk.incubator.http.HttpResponse.BodyHandler;
import jdk.incubator.http.HttpResponse.BodySubscriber;
import jdk.testlibrary.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import javax.net.ssl.SSLContext;

import static java.nio.charset.StandardCharsets.UTF_16;
import static java.nio.charset.StandardCharsets.UTF_8;
import static jdk.incubator.http.HttpRequest.BodyPublisher.fromString;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.assertNotNull;

/*
 * @test
 * @summary Basic tests for line adapter subscribers as created by
 *          the BodyHandlers returned by BodyHandler::fromLineSubscriber
 *          and BodyHandler::asLines
 * @modules java.base/sun.net.www.http
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.common
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.frame
 *          jdk.incubator.httpclient/jdk.incubator.http.internal.hpack
 *          java.logging
 *          jdk.httpserver
 * @library /lib/testlibrary http2/server
 * @build Http2TestServer
 * @build jdk.testlibrary.SimpleSSLContext
 * @run testng/othervm LineBodyHandlerTest
 */

public class LineBodyHandlerTest {

    SSLContext sslContext;
    HttpServer httpTestServer;         // HTTP/1.1    [ 4 servers ]
    HttpsServer httpsTestServer;       // HTTPS/1.1
    Http2TestServer http2TestServer;   // HTTP/2 ( h2c )
    Http2TestServer https2TestServer;  // HTTP/2 ( h2  )
    String httpURI;
    String httpsURI;
    String http2URI;
    String https2URI;

    @DataProvider(name = "uris")
    public Object[][] variants() {
        return new Object[][]{
                { httpURI   },
                { httpsURI  },
                { http2URI  },
                { https2URI },
        };
    }

    static final Class<NullPointerException> NPE = NullPointerException.class;
    static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;

    @Test
    public void testNull() {
        assertThrows(NPE, () -> BodyHandler.fromLineSubscriber(null));
        assertNotNull(BodyHandler.fromLineSubscriber(new StringSubscriber()));
        assertThrows(NPE, () -> BodyHandler.fromLineSubscriber(null, Function.identity(), "\n"));
        assertThrows(NPE, () -> BodyHandler.fromLineSubscriber(new StringSubscriber(), null, "\n"));
        assertNotNull(BodyHandler.fromLineSubscriber(new StringSubscriber(), Function.identity(), null));
        assertThrows(NPE, () -> BodyHandler.fromLineSubscriber(null, null, "\n"));
        assertThrows(NPE, () -> BodyHandler.fromLineSubscriber(null, Function.identity(), null));
        assertThrows(NPE, () -> BodyHandler.fromLineSubscriber(new StringSubscriber(), null, null));

        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null, Function.identity(),
                Charset.defaultCharset(), System.lineSeparator()));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(new StringSubscriber(), null,
                Charset.defaultCharset(), System.lineSeparator()));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(new StringSubscriber(), Function.identity(),
                null, System.lineSeparator()));
        assertNotNull(BodySubscriber.fromLineSubscriber(new StringSubscriber(), Function.identity(),
                Charset.defaultCharset(), null));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null, null,
                Charset.defaultCharset(), System.lineSeparator()));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null, Function.identity(),
                null, System.lineSeparator()));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null, Function.identity(),
                Charset.defaultCharset(), null));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(new StringSubscriber(), null,
                null, System.lineSeparator()));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(new StringSubscriber(), null,
                Charset.defaultCharset(), null));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(new StringSubscriber(), Function.identity(),
                null, null));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(new StringSubscriber(), null, null, null));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null, Function.identity(),
                null, null));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null, null,
                Charset.defaultCharset(), null));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null, null,
                null, System.lineSeparator()));
        assertThrows(NPE, () -> BodySubscriber.fromLineSubscriber(null, null, null, null));
    }

    @Test
    public void testIAE() {
        assertThrows(IAE, () -> BodyHandler.fromLineSubscriber(new StringSubscriber(), Function.identity(),""));
        assertThrows(IAE, () -> BodyHandler.fromLineSubscriber(new CharSequenceSubscriber(), Function.identity(),""));
        assertThrows(IAE, () -> BodyHandler.fromLineSubscriber(new ObjectSubscriber(), Function.identity(), ""));
        assertThrows(IAE, () -> BodySubscriber.fromLineSubscriber(new StringSubscriber(), Function.identity(),
                    StandardCharsets.UTF_8, ""));
        assertThrows(IAE, () -> BodySubscriber.fromLineSubscriber(new CharSequenceSubscriber(), Function.identity(),
                    StandardCharsets.UTF_16, ""));
        assertThrows(IAE, () -> BodySubscriber.fromLineSubscriber(new ObjectSubscriber(), Function.identity(),
                    StandardCharsets.US_ASCII, ""));
    }

    private static final List<String> lines(String text, String eol) {
        if (eol == null) {
            return new BufferedReader(new StringReader(text)).lines().collect(Collectors.toList());
        } else {
            String replaced = text.replace(eol, "|");
            int i=0;
            while(replaced.endsWith("||")) {
                replaced = replaced.substring(0,replaced.length()-1);
                i++;
            }
            List<String> res = List.of(replaced.split("\\|"));
            if (i > 0) {
                res = new ArrayList<>(res);
                for (int j=0; j<i; j++) res.add("");
            }
            return res;
        }
    }

    @Test(dataProvider = "uris")
    void testStringWithFinisher(String url) {
        String body = "May the luck of the Irish be with you!";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        StringSubscriber subscriber = new StringSubscriber();
        HttpResponse<String> response = client.sendAsync(request,
                BodyHandler.fromLineSubscriber(subscriber, Supplier::get,"\n"))
                .join();
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, body);
        assertEquals(subscriber.list, lines(body, "\n"));
    }

    @Test(dataProvider = "uris")
    void testAsStream(String url) {
        String body = "May the luck of the Irish be with you!";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        HttpResponse<Stream<String>> response = client.sendAsync(request,
                BodyHandler.asLines()).join();
        Stream<String> stream = response.body();
        List<String> list = stream.collect(Collectors.toList());
        String text = list.stream().collect(Collectors.joining("|"));
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, body);
        assertEquals(list, List.of(body));
        assertEquals(list, lines(body, null));
    }

    @Test(dataProvider = "uris")
    void testStringWithFinisher2(String url) {
        String body = "May the luck\r\n\r\n of the Irish be with you!";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        StringSubscriber subscriber = new StringSubscriber();
        HttpResponse<Void> response = client.sendAsync(request,
                BodyHandler.fromLineSubscriber(subscriber)).join();
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, body.replace("\r\n", "\n"));
        assertEquals(subscriber.list, lines(body, null));
    }

    @Test(dataProvider = "uris")
    void testAsStreamWithCRLF(String url) {
        String body = "May the luck\r\n\r\n of the Irish be with you!";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        HttpResponse<Stream<String>> response = client.sendAsync(request,
                BodyHandler.asLines()).join();
        Stream<String> stream = response.body();
        List<String> list = stream.collect(Collectors.toList());
        String text = list.stream().collect(Collectors.joining("|"));
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "May the luck|| of the Irish be with you!");
        assertEquals(list, List.of("May the luck",
                                   "",
                                   " of the Irish be with you!"));
        assertEquals(list, lines(body, null));
    }

    @Test(dataProvider = "uris")
    void testStringWithFinisherBlocking(String url) throws Exception {
        String body = "May the luck of the Irish be with you!";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body)).build();

        StringSubscriber subscriber = new StringSubscriber();
        HttpResponse<String> response = client.send(request,
                BodyHandler.fromLineSubscriber(subscriber, Supplier::get, "\n"));
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "May the luck of the Irish be with you!");
        assertEquals(subscriber.list, lines(body, "\n"));
    }

    @Test(dataProvider = "uris")
    void testStringWithoutFinisherBlocking(String url) throws Exception {
        String body = "May the luck of the Irish be with you!";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body)).build();

        StringSubscriber subscriber = new StringSubscriber();
        HttpResponse<Void> response = client.send(request,
                BodyHandler.fromLineSubscriber(subscriber));
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, "May the luck of the Irish be with you!");
        assertEquals(subscriber.list, lines(body, null));
    }

    // Subscriber<Object>

    @Test(dataProvider = "uris")
    void testAsStreamWithMixedCRLF(String url) {
        String body = "May\r\n the wind\r\n always be\rat your back.\r\r";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        HttpResponse<Stream<String>> response = client.sendAsync(request,
                BodyHandler.asLines()).join();
        Stream<String> stream = response.body();
        List<String> list = stream.collect(Collectors.toList());
        String text = list.stream().collect(Collectors.joining("|"));
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
        assertEquals(text, "May| the wind| always be|at your back.|");
        assertEquals(list, List.of("May",
                                   " the wind",
                                   " always be",
                                   "at your back.",
                                   ""));
        assertEquals(list, lines(body, null));
    }

    @Test(dataProvider = "uris")
    void testAsStreamWithMixedCRLF_UTF8(String url) {
        String body = "May\r\n the wind\r\n always be\rat your back.\r\r";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-type", "text/text; charset=UTF-8")
                .POST(fromString(body, UTF_8)).build();

        HttpResponse<Stream<String>> response = client.sendAsync(request,
                BodyHandler.asLines()).join();
        Stream<String> stream = response.body();
        List<String> list = stream.collect(Collectors.toList());
        String text = list.stream().collect(Collectors.joining("|"));
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
        assertEquals(text, "May| the wind| always be|at your back.|");
        assertEquals(list, List.of("May",
                                   " the wind",
                                   " always be",
                                   "at your back.", ""));
        assertEquals(list, lines(body, null));
    }

    @Test(dataProvider = "uris")
    void testAsStreamWithMixedCRLF_UTF16(String url) {
        String body = "May\r\n the wind\r\n always be\rat your back.\r\r";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-type", "text/text; charset=UTF-16")
                .POST(fromString(body, UTF_16)).build();

        HttpResponse<Stream<String>> response = client.sendAsync(request,
                BodyHandler.asLines()).join();
        Stream<String> stream = response.body();
        List<String> list = stream.collect(Collectors.toList());
        String text = list.stream().collect(Collectors.joining("|"));
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
        assertEquals(text, "May| the wind| always be|at your back.|");
        assertEquals(list, List.of("May",
                                   " the wind",
                                   " always be",
                                   "at your back.",
                                   ""));
        assertEquals(list, lines(body, null));
    }

    @Test(dataProvider = "uris")
    void testObjectWithFinisher(String url) {
        String body = "May\r\n the wind\r\n always be\rat your back.";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<String> response = client.sendAsync(request,
                BodyHandler.fromLineSubscriber(subscriber, ObjectSubscriber::get, "\r\n"))
                .join();
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
        assertEquals(text, "May\n the wind\n always be\rat your back.");
        assertEquals(subscriber.list, List.of("May",
                                              " the wind",
                                              " always be\rat your back."));
        assertEquals(subscriber.list, lines(body, "\r\n"));
    }

    @Test(dataProvider = "uris")
    void testObjectWithFinisher_UTF16(String url) {
        String body = "May\r\n the wind\r\n always be\rat your back.\r\r";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext).build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Content-type", "text/text; charset=UTF-16")
                .POST(fromString(body, UTF_16)).build();
        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<String> response = client.sendAsync(request,
                BodyHandler.fromLineSubscriber(subscriber,
                                               ObjectSubscriber::get,
                                   null)).join();
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
        assertEquals(text, "May\n the wind\n always be\nat your back.\n");
        assertEquals(subscriber.list, List.of("May",
                                              " the wind",
                                              " always be",
                                              "at your back.",
                                              ""));
        assertEquals(subscriber.list, lines(body, null));
    }

    @Test(dataProvider = "uris")
    void testObjectWithoutFinisher(String url) {
        String body = "May\r\n the wind\r\n always be\rat your back.";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<Void> response = client.sendAsync(request,
                BodyHandler.fromLineSubscriber(subscriber)).join();
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
        assertEquals(text, "May\n the wind\n always be\nat your back.");
        assertEquals(subscriber.list, List.of("May",
                                              " the wind",
                                              " always be",
                                              "at your back."));
        assertEquals(subscriber.list, lines(body, null));
    }

    @Test(dataProvider = "uris")
    void testObjectWithFinisherBlocking(String url) throws Exception {
        String body = "May\r\n the wind\r\n always be\nat your back.";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<String> response = client.send(request,
                BodyHandler.fromLineSubscriber(subscriber,
                                               ObjectSubscriber::get,
                                   "\r\n"));
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
        assertEquals(text, "May\n the wind\n always be\nat your back.");
        assertEquals(subscriber.list, List.of("May",
                                              " the wind",
                                              " always be\nat your back."));
        assertEquals(subscriber.list, lines(body, "\r\n"));
    }

    @Test(dataProvider = "uris")
    void testObjectWithoutFinisherBlocking(String url) throws Exception {
        String body = "May\r\n the wind\r\n always be\nat your back.";
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext)
                .build();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(body))
                .build();

        ObjectSubscriber subscriber = new ObjectSubscriber();
        HttpResponse<Void> response = client.send(request,
                BodyHandler.fromLineSubscriber(subscriber));
        String text = subscriber.get();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertTrue(text.length() != 0);  // what else can be asserted!
        assertEquals(text, "May\n the wind\n always be\nat your back.");
        assertEquals(subscriber.list, List.of("May",
                                              " the wind",
                                              " always be",
                                              "at your back."));
        assertEquals(subscriber.list, lines(body, null));
    }

    static private final String LINE = "Bient\u00f4t nous plongerons dans les" +
            " fr\u00f4\ud801\udc00des t\u00e9n\u00e8bres, ";

    static private final String bigtext() {
        StringBuilder res = new StringBuilder((LINE.length() + 1) * 50);
        for (int i = 0; i<50; i++) {
            res.append(LINE);
            if (i%2 == 0) res.append("\r\n");
        }
        return res.toString();
    }

    @Test(dataProvider = "uris")
    void testBigTextFromLineSubscriber(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext)
                .build();
        String bigtext = bigtext();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(bigtext))
                .build();

        StringSubscriber subscriber = new StringSubscriber();
        HttpResponse<String> response = client.sendAsync(request,
                BodyHandler.fromLineSubscriber(subscriber, Supplier::get,"\r\n"))
                .join();
        String text = response.body();
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, bigtext.replace("\r\n", "\n"));
        assertEquals(subscriber.list, lines(bigtext, "\r\n"));
    }

    @Test(dataProvider = "uris")
    void testBigTextAsStream(String url) {
        HttpClient client = HttpClient.newBuilder().sslContext(sslContext)
                .build();
        String bigtext = bigtext();
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .POST(fromString(bigtext))
                .build();

        HttpResponse<Stream<String>> response = client.sendAsync(request,
                BodyHandler.asLines()).join();
        Stream<String> stream = response.body();
        List<String> list = stream.collect(Collectors.toList());
        String text = list.stream().collect(Collectors.joining("|"));
        System.out.println(text);
        assertEquals(response.statusCode(), 200);
        assertEquals(text, bigtext.replace("\r\n", "|"));
        assertEquals(list, List.of(bigtext.split("\r\n")));
        assertEquals(list, lines(bigtext, null));
    }

    /** An abstract Subscriber that converts all received data into a String. */
    static abstract class AbstractSubscriber implements Supplier<String> {
        protected volatile Flow.Subscription subscription;
        protected final StringBuilder baos = new StringBuilder();
        protected volatile String text;
        protected volatile RuntimeException error;
        protected final List<Object> list = new CopyOnWriteArrayList<>();

        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }
        public void onError(Throwable throwable) {
            System.out.println(this + " onError: " + throwable);
            error = new RuntimeException(throwable);
        }
        public void onComplete() {
            System.out.println(this + " onComplete");
            text = baos.toString();
        }
        @Override public String get() {
            if (error != null) throw error;
            return text;
        }
    }

    static class StringSubscriber extends AbstractSubscriber
            implements Flow.Subscriber<String>, Supplier<String>
    {
        @Override public void onNext(String item) {
            System.out.print(this + " onNext: \"" + item + "\"");
            if (baos.length() != 0) baos.append('\n');
            baos.append(item);
            list.add(item);
        }
    }

    static class CharSequenceSubscriber extends AbstractSubscriber
            implements Flow.Subscriber<CharSequence>, Supplier<String>
    {
        @Override public void onNext(CharSequence item) {
            System.out.print(this + " onNext: " + item);
            if (baos.length() != 0) baos.append('\n');
            baos.append(item);
            list.add(item);
        }
    }

    static class ObjectSubscriber extends AbstractSubscriber
            implements Flow.Subscriber<Object>, Supplier<String>
    {
        @Override public void onNext(Object item) {
            System.out.print(this + " onNext: " + item);
            if (baos.length() != 0) baos.append('\n');
            baos.append(item);
            list.add(item);
        }
    }


    static void uncheckedWrite(ByteArrayOutputStream baos, byte[] ba) {
        try {
            baos.write(ba);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        InetSocketAddress sa = new InetSocketAddress("localhost", 0);
        httpTestServer = HttpServer.create(sa, 0);
        httpTestServer.createContext("/http1/echo", new Http1EchoHandler());
        httpURI = "http://127.0.0.1:" + httpTestServer.getAddress().getPort() + "/http1/echo";

        httpsTestServer = HttpsServer.create(sa, 0);
        httpsTestServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsTestServer.createContext("/https1/echo", new Http1EchoHandler());
        httpsURI = "https://127.0.0.1:" + httpsTestServer.getAddress().getPort() + "/https1/echo";

        http2TestServer = new Http2TestServer("127.0.0.1", false, 0);
        http2TestServer.addHandler(new Http2EchoHandler(), "/http2/echo");
        int port = http2TestServer.getAddress().getPort();
        http2URI = "http://127.0.0.1:" + port + "/http2/echo";

        https2TestServer = new Http2TestServer("127.0.0.1", true, 0);
        https2TestServer.addHandler(new Http2EchoHandler(), "/https2/echo");
        port = https2TestServer.getAddress().getPort();
        https2URI = "https://127.0.0.1:" + port + "/https2/echo";

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

    static void printBytes(PrintStream out, String prefix, byte[] bytes) {
        int padding = 4 + 4 - (bytes.length % 4);
        padding = padding > 4 ? padding - 4 : 4;
        byte[] bigbytes = new byte[bytes.length + padding];
        System.arraycopy(bytes, 0, bigbytes, padding, bytes.length);
        out.println(prefix + bytes.length + " "
                    + new BigInteger(bigbytes).toString(16));
    }

    static class Http1EchoHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                printBytes(System.out,"Bytes: ", bytes);
                if (t.getRequestHeaders().containsKey("Content-type")) {
                    t.getResponseHeaders().add("Content-type",
                            t.getRequestHeaders().getFirst("Content-type"));
                }
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }

    static class Http2EchoHandler implements Http2Handler {
        @Override
        public void handle(Http2TestExchange t) throws IOException {
            try (InputStream is = t.getRequestBody();
                 OutputStream os = t.getResponseBody()) {
                byte[] bytes = is.readAllBytes();
                printBytes(System.out,"Bytes: ", bytes);
                if (t.getRequestHeaders().firstValue("Content-type").isPresent()) {
                    t.getResponseHeaders().addHeader("Content-type",
                            t.getRequestHeaders().firstValue("Content-type").get());
                }
                t.sendResponseHeaders(200, bytes.length);
                os.write(bytes);
            }
        }
    }
}
