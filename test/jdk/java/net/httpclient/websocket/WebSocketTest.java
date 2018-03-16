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
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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

    private static final Class<NullPointerException> NPE = NullPointerException.class;
    private static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
    private static final Class<IllegalStateException> ISE = IllegalStateException.class;
    private static final Class<IOException> IOE = IOException.class;

    /*
     * Examines WebSocket behaviour after a call to abort()
     */
    @Test
    public void immediateAbort() throws Exception {
        try (DummyWebSocketServer server = serverWithCannedData(0x81, 0x00, 0x88, 0x00)) {
            server.open();
            CompletableFuture<Void> messageReceived = new CompletableFuture<>();
            WebSocket.Listener listener = new WebSocket.Listener() {

                @Override
                public void onOpen(WebSocket webSocket) {
                    /* no initial request */
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket,
                                                 CharSequence message,
                                                 WebSocket.MessagePart part) {
                    messageReceived.complete(null);
                    return null;
                }

                @Override
                public CompletionStage<?> onBinary(WebSocket webSocket,
                                                   ByteBuffer message,
                                                   WebSocket.MessagePart part) {
                    messageReceived.complete(null);
                    return null;
                }

                @Override
                public CompletionStage<?> onPing(WebSocket webSocket,
                                                 ByteBuffer message) {
                    messageReceived.complete(null);
                    return null;
                }

                @Override
                public CompletionStage<?> onPong(WebSocket webSocket,
                                                 ByteBuffer message) {
                    messageReceived.complete(null);
                    return null;
                }

                @Override
                public CompletionStage<?> onClose(WebSocket webSocket,
                                                  int statusCode,
                                                  String reason) {
                    messageReceived.complete(null);
                    return null;
                }
            };

            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), listener)
                    .join();
            for (int i = 0; i < 3; i++) {
                System.out.printf("iteration #%s%n", i);
                // after the first abort() each consecutive one must be a no-op,
                // moreover, query methods should continue to return consistent,
                // permanent values
                for (int j = 0; j < 3; j++) {
                    System.out.printf("abort #%s%n", j);
                    ws.abort();
                    assertTrue(ws.isInputClosed());
                    assertTrue(ws.isOutputClosed());
                    assertEquals(ws.getSubprotocol(), "");
                }
                // at this point valid requests MUST be a no-op:
                for (int j = 0; j < 3; j++) {
                    System.out.printf("request #%s%n", j);
                    ws.request(1);
                    ws.request(2);
                    ws.request(8);
                    ws.request(Integer.MAX_VALUE);
                    ws.request(Long.MAX_VALUE);
                    // invalid requests MUST throw IAE:
                    assertThrows(IAE, () -> ws.request(Integer.MIN_VALUE));
                    assertThrows(IAE, () -> ws.request(Long.MIN_VALUE));
                    assertThrows(IAE, () -> ws.request(-1));
                    assertThrows(IAE, () -> ws.request(0));
                }
            }
            // even though there is a bunch of messages readily available on the
            // wire we shouldn't have received any of them as we aborted before
            // the first request
            try {
                messageReceived.get(10, TimeUnit.SECONDS);
                fail();
            } catch (TimeoutException expected) {
                System.out.println("Finished waiting");
            }
            for (int i = 0; i < 3; i++) {
                System.out.printf("send #%s%n", i);
                assertFails(IOE, ws.sendText("text!", false));
                assertFails(IOE, ws.sendText("text!", true));
                assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(16), false));
                assertFails(IOE, ws.sendBinary(ByteBuffer.allocate(16), true));
                assertFails(IOE, ws.sendPing(ByteBuffer.allocate(16)));
                assertFails(IOE, ws.sendPong(ByteBuffer.allocate(16)));
                assertFails(IOE, ws.sendClose(NORMAL_CLOSURE, "a reason"));
                assertThrows(NPE, () -> ws.sendText(null, false));
                assertThrows(NPE, () -> ws.sendText(null, true));
                assertThrows(NPE, () -> ws.sendBinary(null, false));
                assertThrows(NPE, () -> ws.sendBinary(null, true));
                assertThrows(NPE, () -> ws.sendPing(null));
                assertThrows(NPE, () -> ws.sendPong(null));
                assertThrows(NPE, () -> ws.sendClose(NORMAL_CLOSURE, null));
            }
        }
    }

    /* shortcut */
    private static void assertFails(Class<? extends Throwable> clazz,
                                    CompletionStage<?> stage) {
        Support.assertCompletesExceptionally(clazz, stage);
    }

    private static DummyWebSocketServer serverWithCannedData(int... data) {
        byte[] copy = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            copy[i] = (byte) data[i];
        }
        return serverWithCannedData(copy);
    }

    private static DummyWebSocketServer serverWithCannedData(byte... data) {
        byte[] copy = Arrays.copyOf(data, data.length);
        return new DummyWebSocketServer() {
            @Override
            protected void serve(SocketChannel channel) throws IOException {
                ByteBuffer closeMessage = ByteBuffer.wrap(copy);
                channel.write(closeMessage);
                super.serve(channel);
            }
        };
    }

    @Test
    public void sendMethodsThrowNPE() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            assertThrows(NPE, () -> ws.sendText(null, false));
            assertThrows(NPE, () -> ws.sendText(null, true));
            assertThrows(NPE, () -> ws.sendBinary(null, false));
            assertThrows(NPE, () -> ws.sendBinary(null, true));
            assertThrows(NPE, () -> ws.sendPing(null));
            assertThrows(NPE, () -> ws.sendPong(null));
            assertThrows(NPE, () -> ws.sendClose(NORMAL_CLOSURE, null));

            ws.abort();

            assertThrows(NPE, () -> ws.sendText(null, false));
            assertThrows(NPE, () -> ws.sendText(null, true));
            assertThrows(NPE, () -> ws.sendBinary(null, false));
            assertThrows(NPE, () -> ws.sendBinary(null, true));
            assertThrows(NPE, () -> ws.sendPing(null));
            assertThrows(NPE, () -> ws.sendPong(null));
            assertThrows(NPE, () -> ws.sendClose(NORMAL_CLOSURE, null));
        }
    }

    // TODO: request in onClose/onError
    // TODO: throw exception in onClose/onError
    // TODO: exception is thrown from request()

    @Test
    public void sendCloseCompleted() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();
            ws.sendClose(NORMAL_CLOSURE, "").join();
            assertTrue(ws.isOutputClosed());
            assertEquals(ws.getSubprotocol(), "");
            ws.request(1); // No exceptions must be thrown
        }
    }

    @Test
    public void sendClosePending() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();
            try {
                ByteBuffer data = ByteBuffer.allocate(65536);
                for (int i = 0; ; i++) { // fill up the send buffer
                    System.out.printf("begin cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                    try {
                        ws.sendBinary(data, true).get(10, TimeUnit.SECONDS);
                        data.clear();
                    } catch (TimeoutException e) {
                        break;
                    } finally {
                        System.out.printf("end cycle #%s at %s%n",
                                          i, System.currentTimeMillis());
                    }
                }
                CompletableFuture<WebSocket> cf = ws.sendClose(NORMAL_CLOSURE, "");
                // The output closes even if the Close message has not been sent
                assertFalse(cf.isDone());
                assertTrue(ws.isOutputClosed());
                assertEquals(ws.getSubprotocol(), "");
            } finally {
                ws.abort();
            }
        }
    }

    /*
     * This server does not read from the wire, allowing its client to fill up
     * their send buffer. Used to test scenarios with outstanding send
     * operations.
     */
    private static DummyWebSocketServer notReadingServer() {
        return new DummyWebSocketServer() {
            @Override
            protected void serve(SocketChannel channel) throws IOException {
                try {
                    Thread.sleep(Long.MAX_VALUE);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        };
    }

    @Test
    public void abortPendingSendBinary() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();
            ByteBuffer data = ByteBuffer.allocate(65536);
            CompletableFuture<WebSocket> cf = null;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.printf("begin cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
                try {
                    cf = ws.sendBinary(data, true);
                    cf.get(10, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                } finally {
                    System.out.printf("end cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                }
            }
            ws.abort();
            assertTrue(ws.isOutputClosed());
            assertTrue(ws.isInputClosed());
            assertFails(IOException.class, cf);
        }
    }

    @Test
    public void abortPendingSendText() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();
            String data = stringWith2NBytes(32768);
            CompletableFuture<WebSocket> cf = null;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.printf("begin cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
                try {
                    cf = ws.sendText(data, true);
                    cf.get(10, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    break;
                } finally {
                    System.out.printf("end cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                }
            }
            ws.abort();
            assertTrue(ws.isOutputClosed());
            assertTrue(ws.isInputClosed());
            assertFails(IOException.class, cf);
        }
    }

    private static String stringWith2NBytes(int n) {
        // -- Russian Alphabet (33 characters, 2 bytes per char) --
        char[] abc = {
                0x0410, 0x0411, 0x0412, 0x0413, 0x0414, 0x0415, 0x0401, 0x0416,
                0x0417, 0x0418, 0x0419, 0x041A, 0x041B, 0x041C, 0x041D, 0x041E,
                0x041F, 0x0420, 0x0421, 0x0422, 0x0423, 0x0424, 0x0425, 0x0426,
                0x0427, 0x0428, 0x0429, 0x042A, 0x042B, 0x042C, 0x042D, 0x042E,
                0x042F,
        };
        // repeat cyclically
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0, j = 0; i < n; i++, j = (j + 1) % abc.length) {
            sb.append(abc[j]);
        }
        String s = sb.toString();
        assert s.length() == n && s.getBytes(StandardCharsets.UTF_8).length == 2 * n;
        return s;
    }

    @Test
    public void sendCloseTimeout() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();
            String data = stringWith2NBytes(32768);
            CompletableFuture<WebSocket> cf = null;
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.printf("begin cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
                try {
                    cf = ws.sendText(data, true);
                    cf.get(10, TimeUnit.SECONDS);
                } catch (TimeoutException e) {
                    break;
                } finally {
                    System.out.printf("end cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                }
            }
            long before = System.currentTimeMillis();
            assertFails(IOException.class,
                        ws.sendClose(WebSocket.NORMAL_CLOSURE, "ok"));
            long after = System.currentTimeMillis();
            // default timeout should be 30 seconds
            long elapsed = after - before;
            System.out.printf("Elapsed %s ms%n", elapsed);
            assertTrue(elapsed >= 29_000, String.valueOf(elapsed));
            assertTrue(ws.isOutputClosed());
            assertTrue(ws.isInputClosed());
            assertFails(IOException.class, cf);
        }
    }

    @Test
    public void testIllegalArgument() throws IOException {
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

            assertFails(IOE, ws.sendText(incompleteString(), true));
            assertFails(IOE, ws.sendText(incompleteString(), false));
            assertFails(IOE, ws.sendText(malformedString(), true));
            assertFails(IOE, ws.sendText(malformedString(), false));

            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(124)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(125)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(128)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(256)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(257)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, stringWith2NBytes((123 / 2) + 1)));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, malformedString()));
            assertFails(IAE, ws.sendClose(NORMAL_CLOSURE, incompleteString()));

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

    private static String malformedString() {
        return new String(new char[]{0xDC00, 0xD800});
    }

    private static String incompleteString() {
        return new String(new char[]{0xD800});
    }

    private static String stringWithNBytes(int n) {
        char[] chars = new char[n];
        Arrays.fill(chars, 'A');
        return new String(chars);
    }

    @Test
    public void outstanding1() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ByteBuffer data = ByteBuffer.allocate(65536);
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.println("cycle #" + i);
                try {
                    ws.sendBinary(data, true).get(10, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                }
            }
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertFails(ISE, ws.sendText("", true));
        }
    }

    @Test
    public void outstanding2() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            CharBuffer data = CharBuffer.allocate(65536);
            for (int i = 0; ; i++) {  // fill up the send buffer
                System.out.printf("begin cycle #%s at %s%n",
                                  i, System.currentTimeMillis());
                try {
                    ws.sendText(data, true).get(10, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                } finally {
                    System.out.printf("end cycle #%s at %s%n",
                                      i, System.currentTimeMillis());
                }
            }
            assertFails(ISE, ws.sendText("", true));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
        }
    }

    @Test
    public void interleavingTypes1() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendBinary(ByteBuffer.allocate(16), false).join();
            assertFails(ISE, ws.sendText("text", false));
            assertFails(ISE, ws.sendText("text", true));
        }
    }

    @Test
    public void interleavingTypes2() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendText("text", false).join();
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(16), false));
            assertFails(ISE, ws.sendBinary(ByteBuffer.allocate(16), true));
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
        try (DummyWebSocketServer server = serverWithCannedData(0x88, 0x00)) {
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

        try (DummyWebSocketServer server = serverWithCannedData(binary)) {
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

        try (DummyWebSocketServer server = serverWithCannedData(binary)) {
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


        try (DummyWebSocketServer server = serverWithCannedData(binary)) {
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
     * The server sends Pong messages. The WebSocket replies to messages automatically.
     * According to RFC 6455 The WebSocket is free
     */
    @Test(dataProvider = "nPings")
    public void automaticPongs(int nPings) throws Exception {
        // big enough to not bother with resize
        ByteBuffer buffer = ByteBuffer.allocate(16384);
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
        try (DummyWebSocketServer server = serverWithCannedData(buffer.array())) {
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
}
