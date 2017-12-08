/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
 * @summary Basic test for WebSocketHandshakeException
 * @library /lib/testlibrary
 * @build jdk.testlibrary.SimpleSSLContext
 * @modules jdk.incubator.httpclient
 *          jdk.httpserver
 * @run testng/othervm WSHandshakeException
 */
import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;
import jdk.incubator.http.HttpClient;
import jdk.incubator.http.WebSocket;
import jdk.incubator.http.WebSocketHandshakeException;
import jdk.testlibrary.SimpleSSLContext;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;
import javax.net.ssl.SSLContext;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.fail;

public class WSHandshakeException {

    SSLContext sslContext;
    HttpServer httpTestServer;         // HTTP/1.1    [ 2 servers ]
    HttpsServer httpsTestServer;       // HTTPS/1.1
    String httpURI;
    String httpsURI;

    static final int ITERATION_COUNT = 10;
    // a shared executor helps reduce the amount of threads created by the test
    static final ExecutorService executor = Executors.newCachedThreadPool();

    @DataProvider(name = "variants")
    public Object[][] variants() {
        return new Object[][]{
                { httpURI,    false },
                { httpsURI,   false },
                { httpURI,    true },
                { httpsURI,   true },
        };
    }

    HttpClient newHttpClient() {
        return HttpClient.newBuilder()
                         .executor(executor)
                         .sslContext(sslContext)
                         .build();
    }

    @Test(dataProvider = "variants")
    public void test(String uri, boolean sameClient) {
        HttpClient client = null;
        for (int i = 0; i < ITERATION_COUNT; i++) {
            if (!sameClient || client == null)
                client = newHttpClient();

            try {
                client.newWebSocketBuilder()
                      .buildAsync(URI.create(uri), new WebSocket.Listener() { })
                      .join();
                fail("Expected to throw");
            } catch (CompletionException ce) {
                Throwable t = ce.getCause();
                assertEquals(t.getClass(), WebSocketHandshakeException.class);
                WebSocketHandshakeException wse = (WebSocketHandshakeException) t;
                assertNotNull(wse.getResponse());
                assertEquals(wse.getResponse().statusCode(), 404);
            }
        }
    }

    @BeforeTest
    public void setup() throws Exception {
        sslContext = new SimpleSSLContext().get();
        if (sslContext == null)
            throw new AssertionError("Unexpected null sslContext");

        // HTTP/1.1
        InetSocketAddress sa = new InetSocketAddress("localhost", 0);
        httpTestServer = HttpServer.create(sa, 0);
        httpURI = "ws://127.0.0.1:" + httpTestServer.getAddress().getPort() + "/";

        httpsTestServer = HttpsServer.create(sa, 0);
        httpsTestServer.setHttpsConfigurator(new HttpsConfigurator(sslContext));
        httpsURI = "wss://127.0.0.1:" + httpsTestServer.getAddress().getPort() + "/";

        httpTestServer.start();
        httpsTestServer.start();
    }

    @AfterTest
    public void teardown() {
        httpTestServer.stop(0);
        httpsTestServer.stop(0);
        executor.shutdownNow();
    }
}
