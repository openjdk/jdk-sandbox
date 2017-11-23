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

package jdk.incubator.http.internal.websocket;

import jdk.incubator.http.WebSocket;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Queue;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static jdk.incubator.http.WebSocket.NORMAL_CLOSURE;
import static jdk.incubator.http.internal.websocket.TestSupport.assertCompletesExceptionally;
import static org.testng.Assert.assertEquals;

public class SendingTest {

    @Test
    public void sendTextImmediately() {
        MockTransmitter t = new MockTransmitter() {
            @Override
            protected CompletionStage<?> whenSent() {
                return CompletableFuture.completedFuture(null);
            }
        };
        WebSocket ws = newWebSocket(t);
        CompletableFuture.completedFuture(ws)
                .thenCompose(w -> w.sendText("1", true))
                .thenCompose(w -> w.sendText("2", true))
                .thenCompose(w -> w.sendText("3", true))
                .join();

        assertEquals(t.queue().size(), 3);
    }

    @Test
    public void sendTextWithDelay() {
        MockTransmitter t = new MockTransmitter() {
            @Override
            protected CompletionStage<?> whenSent() {
                return new CompletableFuture<>()
                        .completeOnTimeout(null, 1, TimeUnit.SECONDS);
            }
        };
        WebSocket ws = newWebSocket(t);
        CompletableFuture.completedFuture(ws)
                .thenCompose(w -> w.sendText("1", true))
                .thenCompose(w -> w.sendText("2", true))
                .thenCompose(w -> w.sendText("3", true))
                .join();

        assertEquals(t.queue().size(), 3);
    }

    @Test
    public void sendTextMixedDelay() {
        Random r = new Random();

        MockTransmitter t = new MockTransmitter() {
            @Override
            protected CompletionStage<?> whenSent() {
                return r.nextBoolean() ?
                        new CompletableFuture<>().completeOnTimeout(null, 1, TimeUnit.SECONDS) :
                        CompletableFuture.completedFuture(null);
            }
        };
        WebSocket ws = newWebSocket(t);
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

        assertEquals(t.queue().size(), 9);
    }

    @Test
    public void sendControlMessagesConcurrently() {

        CompletableFuture<?> first = new CompletableFuture<>();

        MockTransmitter t = new MockTransmitter() {

            final AtomicInteger i = new AtomicInteger();

            @Override
            protected CompletionStage<?> whenSent() {
                if (i.incrementAndGet() == 1) {
                    return first;
                } else {
                    return CompletableFuture.completedFuture(null);
                }
            }
        };
        WebSocket ws = newWebSocket(t);

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

        assertEquals(t.queue().size(), 3); // 6 minus 3 that were not accepted
    }

    private static WebSocket newWebSocket(Transmitter transmitter) {
        URI uri = URI.create("ws://localhost");
        String subprotocol = "";
        RawChannel channel = new RawChannel() {

            @Override
            public void registerEvent(RawEvent event) {
                throw new UnsupportedOperationException();
            }

            @Override
            public ByteBuffer initialByteBuffer() {
                return ByteBuffer.allocate(0);
            }

            @Override
            public ByteBuffer read() {
                throw new UnsupportedOperationException();
            }

            @Override
            public long write(ByteBuffer[] srcs, int offset, int length) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void shutdownInput() {
            }

            @Override
            public void shutdownOutput() {
            }

            @Override
            public void close() {
            }
        };
        return new WebSocketImpl(
                uri,
                subprotocol,
                channel,
                new WebSocket.Listener() { }, transmitter);
    }

    private abstract class MockTransmitter extends Transmitter {

        private final long startTime = System.currentTimeMillis();

        private final Queue<OutgoingMessage> messages = new ConcurrentLinkedQueue<>();

        public MockTransmitter() {
            super(null);
        }

        @Override
        public void send(OutgoingMessage message,
                         Consumer<Exception> completionHandler) {
            System.out.printf("[%6s ms.] begin send(%s)%n",
                              System.currentTimeMillis() - startTime,
                              message);
            messages.add(message);
            whenSent().whenComplete((r, e) -> {
                System.out.printf("[%6s ms.] complete send(%s)%n",
                                  System.currentTimeMillis() - startTime,
                                  message);
                if (e != null) {
                    completionHandler.accept((Exception) e);
                } else {
                    completionHandler.accept(null);
                }
            });
            System.out.printf("[%6s ms.] end send(%s)%n",
                              System.currentTimeMillis() - startTime,
                              message);
        }

        protected abstract CompletionStage<?> whenSent();

        public Queue<OutgoingMessage> queue() {
            return messages;
        }
    }
}
