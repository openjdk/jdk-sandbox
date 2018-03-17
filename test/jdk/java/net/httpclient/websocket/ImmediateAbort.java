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
 *       ImmediateAbort
 */

import java.io.IOException;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.testng.annotations.Test;
import static java.net.http.HttpClient.newHttpClient;
import static java.net.http.WebSocket.NORMAL_CLOSURE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertThrows;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

public class ImmediateAbort {

    private static final Class<NullPointerException> NPE = NullPointerException.class;
    private static final Class<IllegalArgumentException> IAE = IllegalArgumentException.class;
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
}
