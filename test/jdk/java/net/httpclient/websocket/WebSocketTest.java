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
 * @run testng/othervm/timeout=600
 *      -Djdk.internal.httpclient.websocket.debug=true
 *       WebSocketTest
 */

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.WebSocket.NORMAL_CLOSURE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class WebSocketTest {

    private static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    private static final Class<IllegalStateException> ISE = IllegalStateException.class;
    private static final Class<IOException> IOE = IOException.class;

    /* shortcut */
    private static void assertFails(Class<? extends Throwable> clazz,
                                    CompletionStage<?> stage) {
        Support.assertCompletesExceptionally(clazz, stage);
    }

    @Test
    public void illegalArgument() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            assertFails(IAE, ws.sendPing(ByteBuffer.allocate(126)));
            assertFails(IAE, ws.sendPing(ByteBuffer.allocate(127)));
            assertFails(IAE, ws.sendPing(ByteBuffer.allocate(128)));
            assertFails(IAE, ws.sendPing(ByteBuffer.allocate(129)));
            assertFails(IAE, ws.sendPing(ByteBuffer.allocate(256)));

            assertFails(IAE, ws.sendPong(ByteBuffer.allocate(126)));
            assertFails(IAE, ws.sendPong(ByteBuffer.allocate(127)));
            assertFails(IAE, ws.sendPong(ByteBuffer.allocate(128)));
            assertFails(IAE, ws.sendPong(ByteBuffer.allocate(129)));
            assertFails(IAE, ws.sendPong(ByteBuffer.allocate(256)));

            assertFails(IOE, ws.sendText(Support.incompleteString(), true));
            assertFails(IOE, ws.sendText(Support.incompleteString(), false));
            assertFails(IOE, ws.sendText(Support.malformedString(), true));
            assertFails(IOE, ws.sendText(Support.malformedString(), false));

            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(124)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(125)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(128)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(256)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, Support.stringWithNBytes(257)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, Support.stringWith2NBytes((123 / 2) + 1)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, Support.malformedString()));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, Support.incompleteString()));

            assertFails(IAE, ws.sendClose(-2, "a reason"));
            assertFails(IAE, ws.sendClose(-1, "a reason"));
            assertFails(IAE, ws.sendClose(0, "a reason"));
            assertFails(IAE, ws.sendClose(1, "a reason"));
            assertFails(IAE, ws.sendClose(500, "a reason"));
            assertFails(IAE, ws.sendClose(998, "a reason"));
            assertFails(IAE, ws.sendClose(999, "a reason"));
            assertFails(IAE, ws.sendClose(1002, "a reason"));
            assertFails(IAE, ws.sendClose(1003, "a reason"));
            assertFails(IAE, ws.sendClose(1006, "a reason"));
            assertFails(IAE, ws.sendClose(1007, "a reason"));
            assertFails(IAE, ws.sendClose(1009, "a reason"));
            assertFails(IAE, ws.sendClose(1010, "a reason"));
            assertFails(IAE, ws.sendClose(1012, "a reason"));
            assertFails(IAE, ws.sendClose(1013, "a reason"));
            assertFails(IAE, ws.sendClose(1015, "a reason"));
            assertFails(IAE, ws.sendClose(5000, "a reason"));
            assertFails(IAE, ws.sendClose(32768, "a reason"));
            assertFails(IAE, ws.sendClose(65535, "a reason"));
            assertFails(IAE, ws.sendClose(65536, "a reason"));
            assertFails(IAE, ws.sendClose(Integer.MAX_VALUE, "a reason"));
            assertFails(IAE, ws.sendClose(Integer.MIN_VALUE, "a reason"));

            assertThrows(IAE, () -> ws.request(Integer.MIN_VALUE));
            assertThrows(IAE, () -> ws.request(Long.MIN_VALUE));
            assertThrows(IAE, () -> ws.request(-1));
            assertThrows(IAE, () -> ws.request(0));
        }
    }

    @Test(dataProvider = "booleans")
    public void pendingTextPingClose(boolean last) throws Exception {
        try (DummyWebSocketServer server = Support.notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            CharBuffer data = CharBuffer.allocate(65536);
            CompletableFuture<WebSocket> cfText;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.printf("begin cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
                cfText = ws.sendText(data, last);
                try {
                    cfText.get(5, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                } finally {
                    System.out.printf("end cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                }
            }
            assertFails(ISE, ws.sendText("", true));
            assertFails(ISE, ws.sendText("", false));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), false));
            CompletableFuture<WebSocket> cfPing = ws.sendPing(ByteBuffer.allocate(125));
            assertHangs(cfPing);
            assertFails(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            CompletableFuture<WebSocket> cfClose = ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            assertHangs(cfClose);
            ws.abort();
            assertFails(IOE, cfText);
            assertFails(IOE, cfPing);
            assertFails(IOE, cfClose);
        }
    }

    /* shortcut */
    public static void assertHangs(CompletionStage<?> stage) {
        Support.assertHangs(stage);
    }

    @Test(dataProvider = "booleans")
    public void pendingTextPongClose(boolean last) throws Exception {
        try (DummyWebSocketServer server = Support.notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            CharBuffer data = CharBuffer.allocate(65536);
            CompletableFuture<WebSocket> cfText;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.printf("begin cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
                cfText = ws.sendText(data, last);
                try {
                    cfText.get(5, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                } finally {
                    System.out.printf("end cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                }
            }
            assertFails(ISE, ws.sendText("", true));
            assertFails(ISE, ws.sendText("", false));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), false));
            CompletableFuture<WebSocket> cfPong = ws.sendPong(ByteBuffer.allocate(125));
            assertHangs(cfPong);
            assertFails(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            CompletableFuture<WebSocket> cfClose = ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            assertHangs(cfClose);
            ws.abort();
            assertFails(IOE, cfText);
            assertFails(IOE, cfPong);
            assertFails(IOE, cfClose);
        }
    }

    @Test(dataProvider = "booleans")
    public void pendingBinaryPingClose(boolean last) throws Exception {
        try (DummyWebSocketServer server = Support.notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ByteBuffer data = ByteBuffer.allocate(65536);
            CompletableFuture<WebSocket> cfBinary;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.printf("begin cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
                cfBinary = ws.sendBinary(data, last);
                try {
                    cfBinary.get(5, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                } finally {
                    System.out.printf("end cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                }
            }
            assertFails(ISE, ws.sendText("", true));
            assertFails(ISE, ws.sendText("", false));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), false));
            CompletableFuture<WebSocket> cfPing = ws.sendPing(ByteBuffer.allocate(125));
            assertHangs(cfPing);
            assertFails(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            CompletableFuture<WebSocket> cfClose = ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            assertHangs(cfClose);
            ws.abort();
            assertFails(IOE, cfBinary);
            assertFails(IOE, cfPing);
            assertFails(IOE, cfClose);
        }
    }

    @Test(dataProvider = "booleans")
    public void pendingBinaryPongClose(boolean last) throws Exception {
        try (DummyWebSocketServer server = Support.notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ByteBuffer data = ByteBuffer.allocate(65536);
            CompletableFuture<WebSocket> cfBinary;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.printf("begin cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
                cfBinary = ws.sendBinary(data, last);
                try {
                    cfBinary.get(5, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                } finally {
                    System.out.printf("end cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                }
            }
            assertFails(ISE, ws.sendText("", true));
            assertFails(ISE, ws.sendText("", false));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), false));
            CompletableFuture<WebSocket> cfPong = ws.sendPong(ByteBuffer.allocate(125));
            assertHangs(cfPong);
            assertFails(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            CompletableFuture<WebSocket> cfClose = ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            assertHangs(cfClose);
            ws.abort();
            assertFails(IOE, cfBinary);
            assertFails(IOE, cfPong);
            assertFails(IOE, cfClose);
        }
    }

    @Test(dataProvider = "booleans")
    public void pendingPingTextClose(boolean last) throws Exception {
        try (DummyWebSocketServer server = Support.notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ByteBuffer data = ByteBuffer.allocate(125);
            CompletableFuture<WebSocket> cfPing;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.println("cycle #" + i);
                cfPing = ws.sendPing(data);
                try {
                    cfPing.get(5, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                }
            }
            assertFails(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            CompletableFuture<WebSocket> cfText = ws.sendText("hello", last);
            assertHangs(cfText);
            CompletableFuture<WebSocket> cfClose
                    = ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            assertHangs(cfClose);
            ws.abort();
            assertFails(IOE, cfPing);
            assertFails(IOE, cfText);
            assertFails(IOE, cfClose);
        }
    }

    @Test(dataProvider = "booleans")
    public void pendingPingBinaryClose(boolean last) throws Exception {
        try (DummyWebSocketServer server = Support.notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ByteBuffer data = ByteBuffer.allocate(125);
            CompletableFuture<WebSocket> cfPing;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.println("cycle #" + i);
                cfPing = ws.sendPing(data);
                try {
                    cfPing.get(5, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                }
            }
            assertFails(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            CompletableFuture<WebSocket> cfBinary
                    = ws.sendBinary(ByteBuffer.allocate(4), last);
            assertHangs(cfBinary);
            CompletableFuture<WebSocket> cfClose
                    = ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            assertHangs(cfClose);
            ws.abort();
            assertFails(IOE, cfPing);
            assertFails(IOE, cfBinary);
            assertFails(IOE, cfClose);
        }
    }

    @Test(dataProvider = "booleans")
    public void pendingPongTextClose(boolean last) throws Exception {
        try (DummyWebSocketServer server = Support.notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ByteBuffer data = ByteBuffer.allocate(125);
            CompletableFuture<WebSocket> cfPong;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.println("cycle #" + i);
                cfPong = ws.sendPong(data);
                try {
                    cfPong.get(5, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                }
            }
            assertFails(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            CompletableFuture<WebSocket> cfText = ws.sendText("hello", last);
            assertHangs(cfText);
            CompletableFuture<WebSocket> cfClose
                    = ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            assertHangs(cfClose);
            ws.abort();
            assertFails(IOE, cfPong);
            assertFails(IOE, cfText);
            assertFails(IOE, cfClose);
        }
    }

    @Test(dataProvider = "booleans")
    public void pendingPongBinaryClose(boolean last) throws Exception {
        try (DummyWebSocketServer server = Support.notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ByteBuffer data = ByteBuffer.allocate(125);
            CompletableFuture<WebSocket> cfPong;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.println("cycle #" + i);
                cfPong = ws.sendPong(data);
                try {
                    cfPong.get(5, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                }
            }
            assertFails(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            CompletableFuture<WebSocket> cfBinary
                    = ws.sendBinary(ByteBuffer.allocate(4), last);
            assertHangs(cfBinary);
            CompletableFuture<WebSocket> cfClose
                    = ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok");
            assertHangs(cfClose);
            ws.abort();
            assertFails(IOE, cfPong);
            assertFails(IOE, cfBinary);
            assertFails(IOE, cfClose);
        }
    }

    @Test
    public void partialBinaryThenText() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendBinary(ByteBuffer.allocate(16), false).join();
            assertFails(ISE, ws.sendText("text", false));
            assertFails(ISE, ws.sendText("text", true));
            // Pings & Pongs are fine
            ws.sendPing(ByteBuffer.allocate(125)).join();
            ws.sendPong(ByteBuffer.allocate(125)).join();
        }
    }

    @Test
    public void partialTextThenBinary() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendText("text", false).join();
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(16), false));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(16), true));
            // Pings & Pongs are fine
            ws.sendPing(ByteBuffer.allocate(125)).join();
            ws.sendPong(ByteBuffer.allocate(125)).join();
        }
    }

    @Test
    public void sendMethodsThrowIOE1() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendClose(NORMAL_CLOSURE, "ok").join();

            assertFails(IOE, ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok"));

            assertFails(IOE, ws.sendText("", true));
            assertFails(IOE, ws.sendText("", false));
            assertFails(IOE, ws.sendText("abc", true));
            assertFails(IOE, ws.sendText("abc", false));
            assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(0), false));
            assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(1), true));
            assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(1), false));

            assertFails(IOE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(IOE, ws.sendPing(ByteBuffer.allocate(124)));
            assertFails(IOE, ws.sendPing(ByteBuffer.allocate(1)));
            assertFails(IOE, ws.sendPing(ByteBuffer.allocate(0)));

            assertFails(IOE, ws.sendPong(ByteBuffer.allocate(125)));
            assertFails(IOE, ws.sendPong(ByteBuffer.allocate(124)));
            assertFails(IOE, ws.sendPong(ByteBuffer.allocate(1)));
            assertFails(IOE, ws.sendPong(ByteBuffer.allocate(0)));
        }
    }

    @Test
    public void sendMethodsThrowIOE2() throws Exception {
        try (DummyWebSocketServer server = Support.serverWithCannedData(0x88, 0x00)) {
            server.open();
            CompletableFuture<Void> onCloseCalled = new CompletableFuture<>();
            CompletableFuture<Void> canClose = new CompletableFuture<>();

            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() {
                        @Override
                        public CompletionStage<?> onClose(WebSocket webSocket,
                                                          int statusCode,
                                                          String reason) {
                            System.out.printf("onClose(%s, '%s')%n", statusCode, reason);
                            onCloseCalled.complete(null);
                            return canClose;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            System.out.println("onError(" + error + ")");
                            onCloseCalled.completeExceptionally(error);
                        }
                    })
                    .join();

            onCloseCalled.join();      // Wait for onClose to be called
            canClose.complete(null);   // Signal to the WebSocket it can close the output
            TimeUnit.SECONDS.sleep(5); // Give canClose some time to reach the WebSocket

            assertFails(IOE, ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok"));

            assertFails(IOE, ws.sendText("", true));
            assertFails(IOE, ws.sendText("", false));
            assertFails(IOE, ws.sendText("abc", true));
            assertFails(IOE, ws.sendText("abc", false));
            assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(0), false));
            assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(1), true));
            assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(1), false));

            assertFails(IOE, ws.sendPing(ByteBuffer.allocate(125)));
            assertFails(IOE, ws.sendPing(ByteBuffer.allocate(124)));
            assertFails(IOE, ws.sendPing(ByteBuffer.allocate(1)));
            assertFails(IOE, ws.sendPing(ByteBuffer.allocate(0)));

            assertFails(IOE, ws.sendPong(ByteBuffer.allocate(125)));
            assertFails(IOE, ws.sendPong(ByteBuffer.allocate(124)));
            assertFails(IOE, ws.sendPong(ByteBuffer.allocate(1)));
            assertFails(IOE, ws.sendPong(ByteBuffer.allocate(0)));
        }
    }

    @Test
    public void simpleAggregatingBinaryMessages() throws IOException {
        List<byte[]> expected = List.of("alpha", "beta", "gamma", "delta")
                .stream()
                .map(s -> s.getBytes(StandardCharsets.US_ASCII))
                .collect(Collectors.toList());
        int[] binary = new int[]{
                0x82, 0x05, 0x61, 0x6c, 0x70, 0x68, 0x61, // [alpha]
                0x02, 0x02, 0x62, 0x65,                   // [be
                0x80, 0x02, 0x74, 0x61,                   // ta]
                0x02, 0x01, 0x67,                         // [g
                0x00, 0x01, 0x61,                         // a
                0x00, 0x00,                               //
                0x00, 0x00,                               //
                0x00, 0x01, 0x6d,                         // m
                0x00, 0x01, 0x6d,                         // m
                0x80, 0x01, 0x61,                         // a]
                0x8a, 0x00,                               // <PONG>
                0x02, 0x04, 0x64, 0x65, 0x6c, 0x74,       // [delt
                0x00, 0x01, 0x61,                         // a
                0x80, 0x00,                               // ]
                0x88, 0x00                                // <CLOSE>
        };
        CompletableFuture<List<byte[]>> actual = new CompletableFuture<>();

        try (DummyWebSocketServer server = Support.serverWithCannedData(binary)) {
            server.open();

            WebSocket.Listener listener = new WebSocket.Listener() {

                List<byte[]> collectedBytes = new ArrayList<>();
                ByteBuffer binary;

                @Override
                public CompletionStage<?> onBinary(WebSocket webSocket,
                                                   ByteBuffer message,
                                                   WebSocket.MessagePart part) {
                    System.out.printf("onBinary(%s, %s)%n", message, part);
                    webSocket.request(1);
                    byte[] bytes = null;
                    switch (part) {
                        case FIRST:
                            binary = ByteBuffer.allocate(message.remaining() * 2);
                        case PART:
                            append(message);
                            return null;
                        case LAST:
                            append(message);
                            binary.flip();
                            bytes = new byte[binary.remaining()];
                            binary.get(bytes);
                            binary.clear();
                            break;
                        case WHOLE:
                            bytes = new byte[message.remaining()];
                            message.get(bytes);
                            break;
                    }
                    processWholeBinary(bytes);
                    return null;
                }

                private void append(ByteBuffer message) {
                    if (binary.remaining() < message.remaining()) {
                        assert message.remaining() > 0;
                        int cap = (binary.capacity() + message.remaining()) * 2;
                        ByteBuffer b = ByteBuffer.allocate(cap);
                        b.put(binary.flip());
                        binary = b;
                    }
                    binary.put(message);
                }

                private void processWholeBinary(byte[] bytes) {
                    String stringBytes = new String(bytes, StandardCharsets.UTF_8);
                    System.out.println("processWholeBinary: " + stringBytes);
                    collectedBytes.add(bytes);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket,
                                                  int statusCode,
                                                  String reason) {
                    actual.complete(collectedBytes);
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    actual.completeExceptionally(error);
                }
            };

            newHttpClient().newWebSocketBuilder()
                           .buildAsync(server.getURI(), listener)
                           .join();

            List<byte[]> a = actual.join();
            System.out.println("joined");
            assertEquals(a, expected);
        }
    }

    @Test
    public void simpleAggregatingTextMessages() throws IOException {

        List<String> expected = List.of("alpha", "beta", "gamma", "delta");

        int[] binary = new int[]{
                0x81, 0x05, 0x61, 0x6c, 0x70, 0x68, 0x61, // "alpha"
                0x01, 0x02, 0x62, 0x65,                   // "be
                0x80, 0x02, 0x74, 0x61,                   // ta"
                0x01, 0x01, 0x67,                         // "g
                0x00, 0x01, 0x61,                         // a
                0x00, 0x00,                               //
                0x00, 0x00,                               //
                0x00, 0x01, 0x6d,                         // m
                0x00, 0x01, 0x6d,                         // m
                0x80, 0x01, 0x61,                         // a"
                0x8a, 0x00,                               // <PONG>
                0x01, 0x04, 0x64, 0x65, 0x6c, 0x74,       // "delt
                0x00, 0x01, 0x61,                         // a
                0x80, 0x00,                               // "
                0x88, 0x00                                // <CLOSE>
        };
        CompletableFuture<List<String>> actual = new CompletableFuture<>();

        try (DummyWebSocketServer server = Support.serverWithCannedData(binary)) {
            server.open();

            WebSocket.Listener listener = new WebSocket.Listener() {

                List<String> collectedStrings = new ArrayList<>();
                StringBuilder text;

                @Override
                public CompletionStage<?> onText(WebSocket webSocket,
                                                 CharSequence message,
                                                 WebSocket.MessagePart part) {
                    System.out.printf("onText(%s, %s)%n", message, part);
                    webSocket.request(1);
                    String str = null;
                    switch (part) {
                        case FIRST:
                            text = new StringBuilder(message.length() * 2);
                        case PART:
                            text.append(message);
                            return null;
                        case LAST:
                            text.append(message);
                            str = text.toString();
                            break;
                        case WHOLE:
                            str = message.toString();
                            break;
                    }
                    processWholeText(str);
                    return null;
                }

                private void processWholeText(String string) {
                    System.out.println(string);
                    collectedStrings.add(string);
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket,
                                                  int statusCode,
                                                  String reason) {
                    actual.complete(collectedStrings);
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    actual.completeExceptionally(error);
                }
            };

            newHttpClient().newWebSocketBuilder()
                           .buildAsync(server.getURI(), listener)
                           .join();

            List<String> a = actual.join();
            assertEquals(a, expected);
        }
    }

    /*
     * Exercises the scenario where requests for more messages are made prior to
     * completing the returned CompletionStage instances.
     */
    @Test
    public void aggregatingTextMessages() throws IOException {

        List<String> expected = List.of("alpha", "beta", "gamma", "delta");

        int[] binary = new int[]{
                0x81, 0x05, 0x61, 0x6c, 0x70, 0x68, 0x61, // "alpha"
                0x01, 0x02, 0x62, 0x65,                   // "be
                0x80, 0x02, 0x74, 0x61,                   // ta"
                0x01, 0x01, 0x67,                         // "g
                0x00, 0x01, 0x61,                         // a
                0x00, 0x00,                               //
                0x00, 0x00,                               //
                0x00, 0x01, 0x6d,                         // m
                0x00, 0x01, 0x6d,                         // m
                0x80, 0x01, 0x61,                         // a"
                0x8a, 0x00,                               // <PONG>
                0x01, 0x04, 0x64, 0x65, 0x6c, 0x74,       // "delt
                0x00, 0x01, 0x61,                         // a
                0x80, 0x00,                               // "
                0x88, 0x00                                // <CLOSE>
        };
        CompletableFuture<List<String>> actual = new CompletableFuture<>();


        try (DummyWebSocketServer server = Support.serverWithCannedData(binary)) {
            server.open();

            WebSocket.Listener listener = new WebSocket.Listener() {

                List<CharSequence> parts;
                /*
                 * A CompletableFuture which will complete once the current
                 * message has been fully assembled (LAST/WHOLE). Until then
                 * the listener returns this instance for every call.
                 */
                CompletableFuture<?> currentCf;
                List<String> collected = new ArrayList<>();

                @Override
                public CompletionStage<?> onText(WebSocket webSocket,
                                                 CharSequence message,
                                                 WebSocket.MessagePart part) {
                    switch (part) {
                        case WHOLE:
                            CompletableFuture<?> cf = new CompletableFuture<>();
                            cf.thenRun(() -> webSocket.request(1));
                            processWholeMessage(List.of(message), cf);
                            return cf;
                        case FIRST:
                            parts = new ArrayList<>();
                            parts.add(message);
                            currentCf = new CompletableFuture<>();
                            currentCf.thenRun(() -> webSocket.request(1));
                            webSocket.request(1);
                            break;
                        case PART:
                            parts.add(message);
                            webSocket.request(1);
                            break;
                        case LAST:
                            parts.add(message);
                            CompletableFuture<?> copyCf = this.currentCf;
                            processWholeMessage(parts, copyCf);
                            currentCf = null;
                            parts = null;
                            return copyCf;
                    }
                    return currentCf;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket,
                                                  int statusCode,
                                                  String reason) {
                    actual.complete(collected);
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    actual.completeExceptionally(error);
                }

                public void processWholeMessage(List<CharSequence> data,
                                                CompletableFuture<?> cf) {
                    StringBuilder b = new StringBuilder();
                    data.forEach(b::append);
                    String s = b.toString();
                    System.out.println(s);
                    cf.complete(null);
                    collected.add(s);
                }
            };

            newHttpClient().newWebSocketBuilder()
                           .buildAsync(server.getURI(), listener)
                           .join();

            List<String> a = actual.join();
            assertEquals(a, expected);
        }
    }

    /*
     * The server sends Ping messages. The WebSocket replies to these messages
     * automatically. According to RFC 6455 a WebSocket client is free to reply
     * only to the most recent Pings.
     */
    @Test(dataProvider = "nPings")
    public void automaticPongs(int nPings) throws Exception {
        // big enough to not bother with resize
        ByteBuffer buffer = ByteBuffer.allocate(65536);
        Frame.HeaderWriter w = new Frame.HeaderWriter();
        for (int i = 0; i < nPings; i++) {
            w.fin(true)
             .opcode(Frame.Opcode.PING)
             .noMask()
             .payloadLen(4)
             .write(buffer);
            buffer.putInt(i);
        }
        w.fin(true)
         .opcode(Frame.Opcode.CLOSE)
         .noMask()
         .payloadLen(2)
         .write(buffer);
        buffer.putChar((char) 1000);
        buffer.flip();
        try (DummyWebSocketServer server = Support.serverWithCannedData(buffer.array())) {
            MockListener listener = new MockListener();
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), listener)
                    .join();
            List<MockListener.Invocation> inv = listener.invocations();
            assertEquals(inv.size(), nPings + 2); // onOpen + onClose + n*onPing

            ByteBuffer data = server.read();
            Frame.Reader reader = new Frame.Reader();

            Frame.Consumer consumer = new Frame.Consumer() {

                ByteBuffer number = ByteBuffer.allocate(4);
                Frame.Masker masker = new Frame.Masker();
                int i = -1;
                boolean closed;

                @Override
                public void fin(boolean value) { assertTrue(value); }
                @Override
                public void rsv1(boolean value) { assertFalse(value); }
                @Override
                public void rsv2(boolean value) { assertFalse(value); }
                @Override
                public void rsv3(boolean value) { assertFalse(value); }
                @Override
                public void opcode(Frame.Opcode value) {
                    if (value == Frame.Opcode.CLOSE) {
                        closed = true;
                        return;
                    }
                    assertEquals(value, Frame.Opcode.PONG);
                }
                @Override
                public void mask(boolean value) { assertTrue(value); }
                @Override
                public void payloadLen(long value) {
                    if (!closed)
                        assertEquals(value, 4);
                }
                @Override
                public void maskingKey(int value) {
                    masker.mask(value);
                }

                @Override
                public void payloadData(ByteBuffer src) {
                    masker.transferMasking(src, number);
                    if (closed) {
                        return;
                    }
                    number.flip();
                    int n = number.getInt();
                    System.out.printf("pong number=%s%n", n);
                    number.clear();
                    if (i >= n) {
                        fail(String.format("i=%s, n=%s", i, n));
                    }
                    i = n;
                }

                @Override
                public void endFrame() { }
            };
            while (data.hasRemaining()) {
                reader.readFrame(data, consumer);
            }
        }
    }

    @DataProvider(name = "nPings")
    public Object[][] nPings() {
        return new Object[][]{{1}, {2}, {4}, {8}, {9}, {1023}};
    }

    @DataProvider(name = "booleans")
    public Object[][] booleans() {
        return new Object[][]{{Boolean.TRUE}, {Boolean.FALSE}};
    }
}
