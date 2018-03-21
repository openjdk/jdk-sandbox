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
 * @build DummyWebSocketServer
 * @run testng/othervm
 *      -Djdk.internal.httpclient.websocket.debug=true
 *       SendTest
 */

import org.testng.annotations.AfterTest;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.WebSocket.NORMAL_CLOSURE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class SendTest {

    private static final Class<NullPointerException> NPE = NullPointerException.class;

    private DummyWebSocketServer server;
    private WebSocket webSocket;

    @AfterTest
    public void cleanup() {
        server.close();
        webSocket.abort();
    }

    @Test
    public void sendMethodsThrowNPE() throws IOException {
        server = new DummyWebSocketServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();

        assertThrows(NPE, () -> webSocket.sendText(null, false));
        assertThrows(NPE, () -> webSocket.sendText(null, true));
        assertThrows(NPE, () -> webSocket.sendBinary(null, false));
        assertThrows(NPE, () -> webSocket.sendBinary(null, true));
        assertThrows(NPE, () -> webSocket.sendPing(null));
        assertThrows(NPE, () -> webSocket.sendPong(null));
        assertThrows(NPE, () -> webSocket.sendClose(NORMAL_CLOSURE, null));

        webSocket.abort();

        assertThrows(NPE, () -> webSocket.sendText(null, false));
        assertThrows(NPE, () -> webSocket.sendText(null, true));
        assertThrows(NPE, () -> webSocket.sendBinary(null, false));
        assertThrows(NPE, () -> webSocket.sendBinary(null, true));
        assertThrows(NPE, () -> webSocket.sendPing(null));
        assertThrows(NPE, () -> webSocket.sendPong(null));
        assertThrows(NPE, () -> webSocket.sendClose(NORMAL_CLOSURE, null));
    }

    // TODO: request in onClose/onError
    // TODO: throw exception in onClose/onError
    // TODO: exception is thrown from request()

    @Test
    public void sendCloseCompleted() throws IOException {
        server = new DummyWebSocketServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        webSocket.sendClose(NORMAL_CLOSURE, "").join();
        assertTrue(webSocket.isOutputClosed());
        assertEquals(webSocket.getSubprotocol(), "");
        webSocket.request(1); // No exceptions must be thrown
    }

    @Test
    public void sendClosePending() throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        ByteBuffer data = ByteBuffer.allocate(65536);
        for (int i = 0; ; i++) { // fill up the send buffer
            System.out.printf("begin cycle #%s at %s%n",
                              i, System.currentTimeMillis());
            try {
                webSocket.sendBinary(data, true).get(10, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                System.out.printf("end cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
            }
        }
        CompletableFuture<WebSocket> cf = webSocket.sendClose(NORMAL_CLOSURE, "");
        // The output closes even if the Close message has not been sent
        assertFalse(cf.isDone());
        assertTrue(webSocket.isOutputClosed());
        assertEquals(webSocket.getSubprotocol(), "");
    }

    @Test
    public void abortPendingSendBinary() throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        ByteBuffer data = ByteBuffer.allocate(65536);
        CompletableFuture<WebSocket> cf = null;
        for (int i = 0; ; i++) {  // fill up the send buffer
            System.out.printf("begin cycle #%s at %s%n",
                              i, System.currentTimeMillis());
            try {
                cf = webSocket.sendBinary(data, true);
                cf.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                System.out.printf("end cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
            }
        }
        webSocket.abort();
        assertTrue(webSocket.isOutputClosed());
        assertTrue(webSocket.isInputClosed());
        Support.assertFails(IOException.class, cf);
    }

    @Test
    public void abortPendingSendText() throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient()
                .newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        String data = Support.stringWith2NBytes(32768);
        CompletableFuture<WebSocket> cf = null;
        for (int i = 0; ; i++) {  // fill up the send buffer
            System.out.printf("begin cycle #%s at %s%n",
                              i, System.currentTimeMillis());
            try {
                cf = webSocket.sendText(data, true);
                cf.get(5, TimeUnit.SECONDS);
            } catch (TimeoutException e) {
                break;
            } finally {
                System.out.printf("end cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
            }
        }
        webSocket.abort();
        assertTrue(webSocket.isOutputClosed());
        assertTrue(webSocket.isInputClosed());
        Support.assertFails(IOException.class, cf);
    }
}
