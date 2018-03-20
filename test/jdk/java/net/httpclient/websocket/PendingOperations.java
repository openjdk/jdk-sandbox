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
 * @run testng/othervm/timeout=300
 *      -Djdk.internal.httpclient.websocket.debug=true
 *       PendingOperations
 */

import org.testng.annotations.AfterTest;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static java.net.http.HttpClient.newHttpClient;

public class PendingOperations {

    private static final Class<IllegalStateException> ISE = IllegalStateException.class;
    private static final Class<IOException> IOE = IOException.class;

    private DummyWebSocketServer server;
    private WebSocket webSocket;

    @AfterTest
    public void cleanup() {
        server.close();
        webSocket.abort();
    }

    @Test(dataProvider = "booleans")
    public void pendingTextPingClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        CharBuffer data = CharBuffer.allocate(65536);
        CompletableFuture<WebSocket> cfText;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfText = webSocket.sendText(data, last);
            try {
                cfText.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendText("", true));
        assertFails(ISE, webSocket.sendText("", false));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), true));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), false));
        CompletableFuture<WebSocket> cfPing = webSocket.sendPing(ByteBuffer.allocate(125));
        assertHangs(cfPing);
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfClose = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfText);
        assertFails(IOE, cfPing);
        assertFails(IOE, cfClose);
    }

    /* shortcut */
    public static void assertHangs(CompletionStage<?> stage) {
        Support.assertHangs(stage);
    }

    /* shortcut */
    private static void assertFails(Class<? extends Throwable> clazz,
                                    CompletionStage<?> stage) {
        Support.assertCompletesExceptionally(clazz, stage);
    }

    @Test(dataProvider = "booleans")
    public void pendingTextPongClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        CharBuffer data = CharBuffer.allocate(65536);
        CompletableFuture<WebSocket> cfText;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfText = webSocket.sendText(data, last);
            try {
                cfText.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendText("", true));
        assertFails(ISE, webSocket.sendText("", false));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), true));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), false));
        CompletableFuture<WebSocket> cfPong = webSocket.sendPong(ByteBuffer.allocate(125));
        assertHangs(cfPong);
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfClose = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfText);
        assertFails(IOE, cfPong);
        assertFails(IOE, cfClose);
    }

    @Test(dataProvider = "booleans")
    public void pendingBinaryPingClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        ByteBuffer data = ByteBuffer.allocate(65536);
        CompletableFuture<WebSocket> cfBinary;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfBinary = webSocket.sendBinary(data, last);
            try {
                cfBinary.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendText("", true));
        assertFails(ISE, webSocket.sendText("", false));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), true));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), false));
        CompletableFuture<WebSocket> cfPing = webSocket.sendPing(ByteBuffer.allocate(125));
        assertHangs(cfPing);
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfClose = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfBinary);
        assertFails(IOE, cfPing);
        assertFails(IOE, cfClose);
    }

    @Test(dataProvider = "booleans")
    public void pendingBinaryPongClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        ByteBuffer data = ByteBuffer.allocate(65536);
        CompletableFuture<WebSocket> cfBinary;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfBinary = webSocket.sendBinary(data, last);
            try {
                cfBinary.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendText("", true));
        assertFails(ISE, webSocket.sendText("", false));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), true));
        assertFails(ISE, webSocket.sendBinary(ByteBuffer.allocate(0), false));
        CompletableFuture<WebSocket> cfPong = webSocket.sendPong(ByteBuffer.allocate(125));
        assertHangs(cfPong);
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfClose = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfBinary);
        assertFails(IOE, cfPong);
        assertFails(IOE, cfClose);
    }

    @Test(dataProvider = "booleans")
    public void pendingPingTextClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        ByteBuffer data = ByteBuffer.allocate(125);
        CompletableFuture<WebSocket> cfPing;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfPing = webSocket.sendPing(data);
            try {
                cfPing.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfText = webSocket.sendText("hello", last);
        assertHangs(cfText);
        CompletableFuture<WebSocket> cfClose
                = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfPing);
        assertFails(IOE, cfText);
        assertFails(IOE, cfClose);
    }

    @Test(dataProvider = "booleans")
    public void pendingPingBinaryClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        ByteBuffer data = ByteBuffer.allocate(125);
        CompletableFuture<WebSocket> cfPing;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfPing = webSocket.sendPing(data);
            try {
                cfPing.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfBinary
                = webSocket.sendBinary(ByteBuffer.allocate(4), last);
        assertHangs(cfBinary);
        CompletableFuture<WebSocket> cfClose
                = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfPing);
        assertFails(IOE, cfBinary);
        assertFails(IOE, cfClose);
    }

    @Test(dataProvider = "booleans")
    public void pendingPongTextClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();
        ByteBuffer data = ByteBuffer.allocate(125);
        CompletableFuture<WebSocket> cfPong;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfPong = webSocket.sendPong(data);
            try {
                cfPong.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfText = webSocket.sendText("hello", last);
        assertHangs(cfText);
        CompletableFuture<WebSocket> cfClose
                = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfPong);
        assertFails(IOE, cfText);
        assertFails(IOE, cfClose);
    }

    @Test(dataProvider = "booleans")
    public void pendingPongBinaryClose(boolean last) throws Exception {
        server = Support.notReadingServer();
        server.open();
        webSocket = newHttpClient().newWebSocketBuilder()
                .buildAsync(server.getURI(), new WebSocket.Listener() { })
                .join();

        ByteBuffer data = ByteBuffer.allocate(125);
        CompletableFuture<WebSocket> cfPong;
        for (int i = 0; ; i++) {  // fill up the send buffer
            long start = System.currentTimeMillis();
            System.out.printf("begin cycle #%s at %s%n", i, start);
            cfPong = webSocket.sendPong(data);
            try {
                cfPong.get(5, TimeUnit.SECONDS);
                data.clear();
            } catch (TimeoutException e) {
                break;
            } finally {
                long stop = System.currentTimeMillis();
                System.out.printf("end cycle #%s at %s (%s ms)%n", i, stop, stop - start);
            }
        }
        assertFails(ISE, webSocket.sendPing(ByteBuffer.allocate(125)));
        assertFails(ISE, webSocket.sendPong(ByteBuffer.allocate(125)));
        CompletableFuture<WebSocket> cfBinary
                = webSocket.sendBinary(ByteBuffer.allocate(4), last);
        assertHangs(cfBinary);
        CompletableFuture<WebSocket> cfClose
                = webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
        assertHangs(cfClose);
        webSocket.abort();
        assertFails(IOE, cfPong);
        assertFails(IOE, cfBinary);
        assertFails(IOE, cfClose);
    }

    @DataProvider(name = "booleans")
    public Object[][] booleans() {
        return new Object[][]{{Boolean.TRUE}, {Boolean.FALSE}};
    }
}
