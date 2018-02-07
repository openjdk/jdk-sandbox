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

package java.net.http.internal.websocket;

import java.net.http.WebSocket;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.net.http.WebSocket.MessagePart.FIRST;
import static java.net.http.WebSocket.MessagePart.LAST;
import static java.net.http.WebSocket.MessagePart.PART;
import static java.net.http.WebSocket.MessagePart.WHOLE;
import static java.net.http.WebSocket.NORMAL_CLOSURE;
import static java.net.http.internal.websocket.MockListener.Invocation.onClose;
import static java.net.http.internal.websocket.MockListener.Invocation.onError;
import static java.net.http.internal.websocket.MockListener.Invocation.onOpen;
import static java.net.http.internal.websocket.MockListener.Invocation.onPing;
import static java.net.http.internal.websocket.MockListener.Invocation.onPong;
import static java.net.http.internal.websocket.MockListener.Invocation.onText;
import static java.net.http.internal.websocket.MockTransport.onClose;
import static java.net.http.internal.websocket.MockTransport.onPing;
import static java.net.http.internal.websocket.MockTransport.onPong;
import static java.net.http.internal.websocket.MockTransport.onText;
import static java.net.http.internal.websocket.TestSupport.assertCompletesExceptionally;
import static org.testng.Assert.assertEquals;

/*
 * Formatting in this file may seem strange:
 *
 *  (
 *   ( ...)
 *  ...
 *  )
 *  ...
 *
 *  However there is a rationale behind it. Sometimes the level of argument
 *  nesting is high, which makes it hard to manage parentheses.
 */
public class WebSocketImplTest {

    // TODO: request in onClose/onError
    // TODO: throw exception in onClose/onError
    // TODO: exception is thrown from request()
    // TODO: repeated sendClose complete normally
    // TODO: default Close message is sent if IAE is thrown from sendClose

    @Test
    public void testNonPositiveRequest() throws Exception {
        MockListener listener = new MockListener(Long.MAX_VALUE) {
            @Override
            protected void onOpen0(WebSocket webSocket) {
                webSocket.request(0);
            }
        };
        WebSocket ws = newInstance(listener, List.of(now(onText("1", WHOLE))));
        listener.onCloseOrOnErrorCalled().get(10, TimeUnit.SECONDS);
        List<MockListener.Invocation> invocations = listener.invocations();
        assertEquals(
                invocations,
                List.of(
                        onOpen(ws),
                        onError(ws, IllegalArgumentException.class)
                )
        );
    }

    @Test
    public void testText1() throws Exception {
        MockListener listener = new MockListener(Long.MAX_VALUE);
        WebSocket ws = newInstance(
                listener,
                List.of(
                        now(onText("1", FIRST)),
                        now(onText("2", PART)),
                        now(onText("3", LAST)),
                        now(onClose(NORMAL_CLOSURE, "no reason"))
                )
        );
        listener.onCloseOrOnErrorCalled().get(10, TimeUnit.SECONDS);
        List<MockListener.Invocation> invocations = listener.invocations();
        assertEquals(
                invocations,
                List.of(
                        onOpen(ws),
                        onText(ws, "1", FIRST),
                        onText(ws, "2", PART),
                        onText(ws, "3", LAST),
                        onClose(ws, NORMAL_CLOSURE, "no reason")
                )
        );
    }

    @Test
    public void testText2() throws Exception {
        MockListener listener = new MockListener(Long.MAX_VALUE);
        WebSocket ws = newInstance(
                listener,
                List.of(
                        now(onText("1", FIRST)),
                        seconds(1, onText("2", PART)),
                        now(onText("3", LAST)),
                        seconds(1, onClose(NORMAL_CLOSURE, "no reason"))
                )
        );
        listener.onCloseOrOnErrorCalled().get(10, TimeUnit.SECONDS);
        List<MockListener.Invocation> invocations = listener.invocations();
        assertEquals(
                invocations,
                List.of(
                        onOpen(ws),
                        onText(ws, "1", FIRST),
                        onText(ws, "2", PART),
                        onText(ws, "3", LAST),
                        onClose(ws, NORMAL_CLOSURE, "no reason")
                )
        );
    }

    @Test
    public void testTextIntermixedWithPongs() throws Exception {
        MockListener listener = new MockListener(Long.MAX_VALUE);
        WebSocket ws = newInstance(
                listener,
                List.of(
                        now(onText("1", FIRST)),
                        now(onText("2", PART)),
                        now(onPong(ByteBuffer.allocate(16))),
                        seconds(1, onPong(ByteBuffer.allocate(32))),
                        now(onText("3", LAST)),
                        now(onPong(ByteBuffer.allocate(64))),
                        now(onClose(NORMAL_CLOSURE, "no reason"))
                )
        );
        listener.onCloseOrOnErrorCalled().get(10, TimeUnit.SECONDS);
        List<MockListener.Invocation> invocations = listener.invocations();
        assertEquals(
                invocations,
                List.of(
                        onOpen(ws),
                        onText(ws, "1", FIRST),
                        onText(ws, "2", PART),
                        onPong(ws, ByteBuffer.allocate(16)),
                        onPong(ws, ByteBuffer.allocate(32)),
                        onText(ws, "3", LAST),
                        onPong(ws, ByteBuffer.allocate(64)),
                        onClose(ws, NORMAL_CLOSURE, "no reason")
                )
        );
    }

    @Test
    public void testTextIntermixedWithPings() throws Exception {
        MockListener listener = new MockListener(Long.MAX_VALUE);
        WebSocket ws = newInstance(
                listener,
                List.of(
                        now(onText("1", FIRST)),
                        now(onText("2", PART)),
                        now(onPing(ByteBuffer.allocate(16))),
                        seconds(1, onPing(ByteBuffer.allocate(32))),
                        now(onText("3", LAST)),
                        now(onPing(ByteBuffer.allocate(64))),
                        now(onClose(NORMAL_CLOSURE, "no reason"))
                )
        );
        listener.onCloseOrOnErrorCalled().get(10, TimeUnit.SECONDS);
        List<MockListener.Invocation> invocations = listener.invocations();
        assertEquals(
                invocations,
                List.of(
                        onOpen(ws),
                        onText(ws, "1", FIRST),
                        onText(ws, "2", PART),
                        onPing(ws, ByteBuffer.allocate(16)),
                        onPing(ws, ByteBuffer.allocate(32)),
                        onText(ws, "3", LAST),
                        onPing(ws, ByteBuffer.allocate(64)),
                        onClose(ws, NORMAL_CLOSURE, "no reason"))
        );
    }

    // Tease out "java.lang.IllegalStateException: Send pending" due to possible
    // race between sending a message and replenishing the permit
    @Test
    public void testManyTextMessages() {
        WebSocketImpl ws = newInstance(
                new MockListener(1),
                new TransportFactory() {
                    @Override
                    public <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                                            MessageStreamConsumer consumer) {

                        final Random r = new Random();

                        return new MockTransport<>(sendResultSupplier, consumer) {
                            @Override
                            protected CompletableFuture<T> defaultSend() {
                                return millis(r.nextInt(100), result());
                            }
                        };
                    }
                });
        int NUM_MESSAGES = 512;
        CompletableFuture<WebSocket> current = CompletableFuture.completedFuture(ws);
        for (int i = 0; i < NUM_MESSAGES; i++) {
            current = current.thenCompose(w -> w.sendText(" ", true));
        }
        current.join();
        MockTransport<WebSocket> transport = (MockTransport<WebSocket>) ws.transport();
        assertEquals(transport.invocations().size(), NUM_MESSAGES);
    }

    @Test
    public void testManyBinaryMessages() {
        WebSocketImpl ws = newInstance(
                new MockListener(1),
                new TransportFactory() {
                    @Override
                    public <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                                            MessageStreamConsumer consumer) {

                        final Random r = new Random();

                        return new MockTransport<>(sendResultSupplier, consumer) {
                            @Override
                            protected CompletableFuture<T> defaultSend() {
                                return millis(r.nextInt(150), result());
                            }
                        };
                    }
                });
        CompletableFuture<WebSocket> start = new CompletableFuture<>();

        int NUM_MESSAGES = 512;
        CompletableFuture<WebSocket> current = start;
        for (int i = 0; i < NUM_MESSAGES; i++) {
            current = current.thenComposeAsync(w -> w.sendBinary(ByteBuffer.allocate(1), true));
        }

        start.completeAsync(() -> ws);
        current.join();

        MockTransport<WebSocket> transport = (MockTransport<WebSocket>) ws.transport();
        assertEquals(transport.invocations().size(), NUM_MESSAGES);
    }


    @Test
    public void sendTextImmediately() {
        WebSocketImpl ws = newInstance(
                new MockListener(1),
                new TransportFactory() {
                    @Override
                    public <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                                            MessageStreamConsumer consumer) {
                        return new MockTransport<>(sendResultSupplier, consumer);
                    }
                });
        CompletableFuture.completedFuture(ws)
                .thenCompose(w -> w.sendText("1", true))
                .thenCompose(w -> w.sendText("2", true))
                .thenCompose(w -> w.sendText("3", true))
                .join();
        MockTransport<WebSocket> transport = (MockTransport<WebSocket>) ws.transport();
        assertEquals(transport.invocations().size(), 3);
    }

    @Test
    public void sendTextWithDelay() {
        MockListener listener = new MockListener(1);
        WebSocketImpl ws = newInstance(
                listener,
                new TransportFactory() {
                    @Override
                    public <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                                            MessageStreamConsumer consumer) {
                        return new MockTransport<>(sendResultSupplier, consumer) {
                            @Override
                            protected CompletableFuture<T> defaultSend() {
                                return seconds(1, result());
                            }
                        };
                    }
                });
        CompletableFuture.completedFuture(ws)
                .thenCompose(w -> w.sendText("1", true))
                .thenCompose(w -> w.sendText("2", true))
                .thenCompose(w -> w.sendText("3", true))
                .join();
        assertEquals(listener.invocations(), List.of(onOpen(ws)));
        MockTransport<WebSocket> transport = (MockTransport<WebSocket>) ws.transport();
        assertEquals(transport.invocations().size(), 3);
    }

    @Test
    public void sendTextMixedDelay() {
        MockListener listener = new MockListener(1);
        WebSocketImpl ws = newInstance(
                listener,
                new TransportFactory() {

                    final Random r = new Random();

                    @Override
                    public <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                                            MessageStreamConsumer consumer) {
                        return new MockTransport<>(sendResultSupplier, consumer) {
                            @Override
                            protected CompletableFuture<T> defaultSend() {
                                return r.nextBoolean()
                                        ? seconds(1, result())
                                        : now(result());
                            }
                        };
                    }
                });
        CompletableFuture.completedFuture(ws)
                .thenCompose(w -> w.sendText("1", true))
                .thenCompose(w -> w.sendText("2", true))
                .thenCompose(w -> w.sendText("3", true))
                .thenCompose(w -> w.sendText("4", true))
                .thenCompose(w -> w.sendText("5", true))
                .thenCompose(w -> w.sendText("6", true))
                .thenCompose(w -> w.sendText("7", true))
                .thenCompose(w -> w.sendText("8", true))
                .thenCompose(w -> w.sendText("9", true))
                .join();
        assertEquals(listener.invocations(), List.of(onOpen(ws)));
        MockTransport<WebSocket> transport = (MockTransport<WebSocket>) ws.transport();
        assertEquals(transport.invocations().size(), 9);
    }

    @Test(enabled = false) // temporarily disabled
    public void sendControlMessagesConcurrently() {
        MockListener listener = new MockListener(1);

        CompletableFuture<?> first = new CompletableFuture<>(); // barrier

        WebSocketImpl ws = newInstance(
                listener,
                new TransportFactory() {

                    final AtomicInteger i = new AtomicInteger();

                    @Override
                    public <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                                            MessageStreamConsumer consumer) {
                        return new MockTransport<>(sendResultSupplier, consumer) {
                            @Override
                            protected CompletableFuture<T> defaultSend() {
                                if (i.incrementAndGet() == 1) {
                                    return first.thenApply(o -> result());
                                } else {
                                    return now(result());
                                }
                            }
                        };
                    }
                });

        CompletableFuture<?> cf1 = ws.sendPing(ByteBuffer.allocate(0));
        CompletableFuture<?> cf2 = ws.sendPong(ByteBuffer.allocate(0));
        CompletableFuture<?> cf3 = ws.sendClose(NORMAL_CLOSURE, "");
        CompletableFuture<?> cf4 = ws.sendClose(NORMAL_CLOSURE, "");
        CompletableFuture<?> cf5 = ws.sendPing(ByteBuffer.allocate(0));
        CompletableFuture<?> cf6 = ws.sendPong(ByteBuffer.allocate(0));

        first.complete(null);
        // Don't care about exceptional completion, only that all of them have
        // completed
        CompletableFuture.allOf(cf1, cf2, cf3, cf4, cf5, cf6)
                .handle((v, e) -> null).join();

        cf3.join(); /* Check that sendClose has completed normally */
        cf4.join(); /* Check that repeated sendClose has completed normally */
        assertCompletesExceptionally(IllegalStateException.class, cf5);
        assertCompletesExceptionally(IllegalStateException.class, cf6);

        assertEquals(listener.invocations(), List.of(onOpen(ws)));
        MockTransport<WebSocket> transport = (MockTransport<WebSocket>) ws.transport();
        assertEquals(transport.invocations().size(), 3); // 6 minus 3 that were not accepted
    }

    private static <T> CompletableFuture<T> seconds(long val, T result) {
        return new CompletableFuture<T>()
                .completeOnTimeout(result, val, TimeUnit.SECONDS);
    }

    private static <T> CompletableFuture<T> millis(long val, T result) {
        return new CompletableFuture<T>()
                .completeOnTimeout(result, val, TimeUnit.MILLISECONDS);
    }

    private static <T> CompletableFuture<T> now(T result) {
        return CompletableFuture.completedFuture(result);
    }

    private static WebSocketImpl newInstance(
            WebSocket.Listener listener,
            Collection<CompletableFuture<Consumer<MessageStreamConsumer>>> input) {
        TransportFactory factory = new TransportFactory() {
            @Override
            public <T> Transport<T> createTransport(Supplier<T> sendResultSupplier,
                                                    MessageStreamConsumer consumer) {
                return new MockTransport<T>(sendResultSupplier, consumer) {
                    @Override
                    protected Collection<CompletableFuture<Consumer<MessageStreamConsumer>>> receive() {
                        return input;
                    }
                };
            }
        };
        return newInstance(listener, factory);
    }

    private static WebSocketImpl newInstance(WebSocket.Listener listener,
                                             TransportFactory factory) {
        URI uri = URI.create("ws://localhost");
        String subprotocol = "";
        return WebSocketImpl.newInstance(uri, subprotocol, listener, factory);
    }
}
