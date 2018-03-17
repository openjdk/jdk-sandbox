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

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.WebSocket.NORMAL_CLOSURE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;

public class SendTest {

    private static final Class<NullPointerException> NPE = NullPointerException.class;

    /* shortcut */
    private static void assertFails(Class<? extends Throwable> clazz,
                                    CompletionStage<?> stage) {
        Support.assertCompletesExceptionally(clazz, stage);
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
}
