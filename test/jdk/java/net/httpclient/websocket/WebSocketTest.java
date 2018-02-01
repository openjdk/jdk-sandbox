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
 * @run testng/othervm -Djdk.httpclient.HttpClient.log=trace WebSocketTest
 */

import jdk.incubator.http.WebSocket;
import org.testng.annotations.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static jdk.incubator.http.HttpClient.newHttpClient;
import static jdk.incubator.http.WebSocket.NORMAL_CLOSURE;
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

    @Test
    public void abort() throws Exception {
        try (DummyWebSocketServer server = serverWithCannedData(0x81, 0x00, 0x88, 0x00)) {
            server.open();
            CompletableFuture<Void> messageReceived = new CompletableFuture<>();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) { /* no initial request */ }

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
                    })
                    .join();

            ws.abort();
            // Each consecutive abort MUST be a no-op:
            ws.abort();
            assertTrue(ws.isInputClosed());
            assertTrue(ws.isOutputClosed());
            assertEquals(ws.getSubprotocol(), "");
            ws.abort();
            assertTrue(ws.isInputClosed());
            assertTrue(ws.isOutputClosed());
            assertEquals(ws.getSubprotocol(), "");
            // At this point request MUST be a no-op:
            ws.request(1);
            ws.request(Long.MAX_VALUE);
            // Throws IAE regardless of whether WebSocket is closed or not:
            assertThrows(IAE, () -> ws.request(Integer.MIN_VALUE));
            assertThrows(IAE, () -> ws.request(Long.MIN_VALUE));
            assertThrows(IAE, () -> ws.request(-1));
            assertThrows(IAE, () -> ws.request(0));
            // Even though there is a bunch of messages readily available on the
            // wire we shouldn't have received any of them as we aborted before
            // the first request
            try {
                messageReceived.get(10, TimeUnit.SECONDS);
                fail();
            } catch (TimeoutException expected) { }
            // TODO: No send operations MUST succeed
//            assertCompletesExceptionally(IOE, ws.sendText("text!", false));
//            assertCompletesExceptionally(IOE, ws.sendText("text!", true));
//            assertCompletesExceptionally(IOE, ws.sendBinary(ByteBuffer.allocate(16), false));
//            assertCompletesExceptionally(IOE, ws.sendBinary(ByteBuffer.allocate(16), true));
//            assertCompletesExceptionally(IOE, ws.sendPing(ByteBuffer.allocate(16)));
//            assertCompletesExceptionally(IOE, ws.sendPong(ByteBuffer.allocate(16)));
//            assertCompletesExceptionally(IOE, ws.sendClose(NORMAL_CLOSURE, "a reason"));
        }
    }

    private static DummyWebSocketServer serverWithCannedData(int... data) {
        byte[] copy = new byte[data.length];
        for (int i = 0; i < data.length; i++) {
            copy[i] = (byte) data[i];
        }
        return new DummyWebSocketServer() {
            @Override
            protected void serve(SocketChannel channel) throws IOException {
                ByteBuffer closeMessage = ByteBuffer.wrap(copy);
                int wrote = channel.write(closeMessage);
                System.out.println("Wrote bytes: " + wrote);
                super.serve(channel);
            }
        };
    }

    @Test
    public void testNull() throws IOException {
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
        }
    }

    @Test
    public void testSendClose1() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();
            ws.sendClose(NORMAL_CLOSURE, "").join();
            assertTrue(ws.isOutputClosed());
            assertFalse(ws.isInputClosed());
            assertEquals(ws.getSubprotocol(), "");
            ws.request(1); // No exceptions must be thrown
        }
    }

    @Test
    public void testSendClose2() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();
            ByteBuffer data = ByteBuffer.allocate(65536);
            for (int i = 0; ; i++) {
                System.out.println("cycle #" + i);
                try {
                    ws.sendBinary(data, true).get(10, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                }
            }
            CompletableFuture<WebSocket> cf = ws.sendClose(NORMAL_CLOSURE, "");
            assertTrue(ws.isOutputClosed());
            assertFalse(ws.isInputClosed());
            assertEquals(ws.getSubprotocol(), "");
            // The output closes regardless of whether or not the Close message
            // has been sent
            assertFalse(cf.isDone());
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
    public void testIllegalArgument() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            assertCompletesExceptionally(IAE, ws.sendPing(ByteBuffer.allocate(126)));
            assertCompletesExceptionally(IAE, ws.sendPing(ByteBuffer.allocate(127)));
            assertCompletesExceptionally(IAE, ws.sendPing(ByteBuffer.allocate(128)));
            assertCompletesExceptionally(IAE, ws.sendPing(ByteBuffer.allocate(129)));
            assertCompletesExceptionally(IAE, ws.sendPing(ByteBuffer.allocate(256)));

            assertCompletesExceptionally(IAE, ws.sendPong(ByteBuffer.allocate(126)));
            assertCompletesExceptionally(IAE, ws.sendPong(ByteBuffer.allocate(127)));
            assertCompletesExceptionally(IAE, ws.sendPong(ByteBuffer.allocate(128)));
            assertCompletesExceptionally(IAE, ws.sendPong(ByteBuffer.allocate(129)));
            assertCompletesExceptionally(IAE, ws.sendPong(ByteBuffer.allocate(256)));

            assertCompletesExceptionally(IAE, ws.sendText(incompleteString(), true));
            assertCompletesExceptionally(IAE, ws.sendText(incompleteString(), false));
            assertCompletesExceptionally(IAE, ws.sendText(malformedString(), true));
            assertCompletesExceptionally(IAE, ws.sendText(malformedString(), false));

            assertCompletesExceptionally(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(124)));
            assertCompletesExceptionally(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(125)));
            assertCompletesExceptionally(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(128)));
            assertCompletesExceptionally(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(256)));
            assertCompletesExceptionally(IAE, ws.sendClose(NORMAL_CLOSURE, stringWithNBytes(257)));
            assertCompletesExceptionally(IAE, ws.sendClose(NORMAL_CLOSURE, stringWith2NBytes((123 / 2) + 1)));
            assertCompletesExceptionally(IAE, ws.sendClose(NORMAL_CLOSURE, malformedString()));
            assertCompletesExceptionally(IAE, ws.sendClose(NORMAL_CLOSURE, incompleteString()));

            assertCompletesExceptionally(IAE, ws.sendClose(-2, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(-1, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(0, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(500, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(998, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(999, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1002, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1003, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1006, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1007, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1009, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1010, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1012, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1013, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(1015, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(5000, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(32768, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(65535, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(65536, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(Integer.MAX_VALUE, "a reason"));
            assertCompletesExceptionally(IAE, ws.sendClose(Integer.MIN_VALUE, "a reason"));

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
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) {
            sb.append("A");
        }
        return sb.toString();
    }

    private static String stringWith2NBytes(int n) {
        // Russian alphabet repeated cyclically
        char FIRST = '\u0410';
        char LAST = '\u042F';
        StringBuilder sb = new StringBuilder(n);
        char c = FIRST;
        for (int i = 0; i < n; i++) {
            if (++c > LAST) {
                c = FIRST;
            }
            sb.append(c);
        }
        String s = sb.toString();
        assert s.length() == n && s.getBytes(StandardCharsets.UTF_8).length == 2 * n;
        return s;
    }

    private static void assertCompletesExceptionally(Class<? extends Throwable> clazz,
                                                     CompletableFuture<?> stage) {
        stage.handle((result, error) -> {
            if (error instanceof CompletionException) {
                Throwable cause = error.getCause();
                if (cause == null) {
                    throw new AssertionError("Unexpected null cause: " + error);
                }
                assertException(clazz, cause);
            } else {
                assertException(clazz, error);
            }
            return null;
        }).join();
    }

    private static void assertException(Class<? extends Throwable> clazz,
                                        Throwable t) {
        if (t == null) {
            throw new AssertionError("Expected " + clazz + ", caught nothing");
        }
        if (!clazz.isInstance(t)) {
            throw new AssertionError("Expected " + clazz + ", caught " + t);
        }
    }

    @Test
    public void testIllegalStateOutstanding1() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ByteBuffer data = ByteBuffer.allocate(65536);
            for (int i = 0; ; i++) {
                System.out.println("cycle #" + i);
                try {
                    ws.sendBinary(data, true).get(10, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                }
            }
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertCompletesExceptionally(ISE, ws.sendText("", true));
        }
    }

    @Test
    public void testIllegalStateOutstanding2() throws Exception {
        try (DummyWebSocketServer server = notReadingServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            CharBuffer data = CharBuffer.allocate(65536);
            for (int i = 0; ; i++) {
                System.out.println("cycle #" + i);
                try {
                    ws.sendText(data, true).get(10, TimeUnit.SECONDS);
                    data.clear();
                } catch (TimeoutException e) {
                    break;
                }
            }
            assertCompletesExceptionally(ISE, ws.sendText("", true));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
        }
    }

    @Test
    public void testIllegalStateIntermixed1() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendBinary(ByteBuffer.allocate(16), false).join();
            assertCompletesExceptionally(ISE, ws.sendText("text", false));
            assertCompletesExceptionally(ISE, ws.sendText("text", true));
        }
    }

    @Test
    public void testIllegalStateIntermixed2() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendText("text", false).join();
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(16), false));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(16), true));
        }
    }

    @Test
    public void testIllegalStateSendClose() throws IOException {
        try (DummyWebSocketServer server = new DummyWebSocketServer()) {
            server.open();
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), new WebSocket.Listener() { })
                    .join();

            ws.sendClose(NORMAL_CLOSURE, "normal close").join();

            assertCompletesExceptionally(ISE, ws.sendText("", true));
            assertCompletesExceptionally(ISE, ws.sendText("", false));
            assertCompletesExceptionally(ISE, ws.sendText("abc", true));
            assertCompletesExceptionally(ISE, ws.sendText("abc", false));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(0), false));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(1), true));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(1), false));

            assertCompletesExceptionally(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertCompletesExceptionally(ISE, ws.sendPing(ByteBuffer.allocate(124)));
            assertCompletesExceptionally(ISE, ws.sendPing(ByteBuffer.allocate(1)));
            assertCompletesExceptionally(ISE, ws.sendPing(ByteBuffer.allocate(0)));

            assertCompletesExceptionally(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            assertCompletesExceptionally(ISE, ws.sendPong(ByteBuffer.allocate(124)));
            assertCompletesExceptionally(ISE, ws.sendPong(ByteBuffer.allocate(1)));
            assertCompletesExceptionally(ISE, ws.sendPong(ByteBuffer.allocate(0)));
        }
    }

    @Test
    public void testIllegalStateOnClose() throws Exception {
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
                            System.out.println("onClose(" + statusCode + ")");
                            onCloseCalled.complete(null);
                            return canClose;
                        }

                        @Override
                        public void onError(WebSocket webSocket, Throwable error) {
                            System.out.println("onError(" + error + ")");
                            error.printStackTrace();
                        }
                    })
                    .join();

            onCloseCalled.join();      // Wait for onClose to be called
            TimeUnit.SECONDS.sleep(5); // Give canClose some time to reach the WebSocket
            canClose.complete(null);   // Signal to the WebSocket it can close the output

            assertCompletesExceptionally(ISE, ws.sendText("", true));
            assertCompletesExceptionally(ISE, ws.sendText("", false));
            assertCompletesExceptionally(ISE, ws.sendText("abc", true));
            assertCompletesExceptionally(ISE, ws.sendText("abc", false));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(0), true));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(0), false));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(1), true));
            assertCompletesExceptionally(ISE, ws.sendBinary(ByteBuffer.allocate(1), false));

            assertCompletesExceptionally(ISE, ws.sendPing(ByteBuffer.allocate(125)));
            assertCompletesExceptionally(ISE, ws.sendPing(ByteBuffer.allocate(124)));
            assertCompletesExceptionally(ISE, ws.sendPing(ByteBuffer.allocate(1)));
            assertCompletesExceptionally(ISE, ws.sendPing(ByteBuffer.allocate(0)));

            assertCompletesExceptionally(ISE, ws.sendPong(ByteBuffer.allocate(125)));
            assertCompletesExceptionally(ISE, ws.sendPong(ByteBuffer.allocate(124)));
            assertCompletesExceptionally(ISE, ws.sendPong(ByteBuffer.allocate(1)));
            assertCompletesExceptionally(ISE, ws.sendPong(ByteBuffer.allocate(0)));
        }
    }

    @Test
    public void aggregatingMessages() throws IOException {

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

                List<CharSequence> parts = new ArrayList<>();
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
                            List<CharSequence> copy = List.copyOf(parts);
                            parts.clear();
                            CompletableFuture<?> cf1 = currentCf;
                            currentCf = null;
                            processWholeMessage(copy, cf1);
                            return cf1;
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
            WebSocket ws = newHttpClient()
                    .newWebSocketBuilder()
                    .buildAsync(server.getURI(), listener)
                    .join();

            List<String> a = actual.join();
            assertEquals(a, expected);
        }
    }
}
