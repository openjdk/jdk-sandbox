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
import jdk.incubator.http.WebSocket.MessagePart;
import org.testng.annotations.Test;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.concurrent.CompletableFuture.completedStage;
import static jdk.incubator.http.WebSocket.MessagePart.FIRST;
import static jdk.incubator.http.WebSocket.MessagePart.LAST;
import static jdk.incubator.http.WebSocket.MessagePart.PART;
import static jdk.incubator.http.WebSocket.MessagePart.WHOLE;
import static jdk.incubator.http.WebSocket.NORMAL_CLOSURE;
import static jdk.incubator.http.internal.common.Pair.pair;
import static jdk.incubator.http.internal.websocket.WebSocketImpl.newInstance;

public class ReceivingTest {

    // TODO: request in onClose/onError
    // TODO: throw exception in onClose/onError

    @Test
    public void testNonPositiveRequest() {
        URI uri = URI.create("ws://localhost");
        String subprotocol = "";
        CompletableFuture<Throwable> result = new CompletableFuture<>();
        newInstance(uri, subprotocol, new MockListener(Long.MAX_VALUE) {

            final AtomicInteger onOpenCount = new AtomicInteger();
            volatile WebSocket webSocket;

            @Override
            public void onOpen(WebSocket webSocket) {
                int i = onOpenCount.incrementAndGet();
                if (i > 1) {
                    result.completeExceptionally(new IllegalStateException());
                } else {
                    this.webSocket = webSocket;
                    webSocket.request(0);
                }
            }

            @Override
            public CompletionStage<?> onBinary(WebSocket webSocket,
                                               ByteBuffer message,
                                               MessagePart part) {
                result.completeExceptionally(new IllegalStateException());
                return null;
            }

            @Override
            public CompletionStage<?> onText(WebSocket webSocket,
                                             CharSequence message,
                                             MessagePart part) {
                result.completeExceptionally(new IllegalStateException());
                return null;
            }

            @Override
            public CompletionStage<?> onPing(WebSocket webSocket,
                                             ByteBuffer message) {
                result.completeExceptionally(new IllegalStateException());
                return null;
            }

            @Override
            public CompletionStage<?> onPong(WebSocket webSocket,
                                             ByteBuffer message) {
                result.completeExceptionally(new IllegalStateException());
                return null;
            }

            @Override
            public CompletionStage<?> onClose(WebSocket webSocket,
                                              int statusCode,
                                              String reason) {
                result.completeExceptionally(new IllegalStateException());
                return null;
            }

            @Override
            public void onError(WebSocket webSocket, Throwable error) {
                if (!this.webSocket.equals(webSocket)) {
                    result.completeExceptionally(new IllegalArgumentException());
                } else if (error == null || error.getClass() != IllegalArgumentException.class) {
                    result.completeExceptionally(new IllegalArgumentException());
                } else {
                    result.complete(null);
                }
            }
        }, new MockTransport() {
            @Override
            protected Receiver newReceiver(MessageStreamConsumer consumer) {
                return new MockReceiver(consumer, channel, pair(now(), m -> m.onText("1", WHOLE) ));
            }
        });
        result.join();
    }

    @Test
    public void testText1() throws InterruptedException {
        URI uri = URI.create("ws://localhost");
        String subprotocol = "";
        newInstance(uri, subprotocol, new MockListener(Long.MAX_VALUE),
                    new MockTransport() {
                        @Override
                        protected Receiver newReceiver(MessageStreamConsumer consumer) {
                            return new MockReceiver(consumer, channel,
                                                    pair(now(), m -> m.onText("1", FIRST)),
                                                    pair(now(), m -> m.onText("2", PART)),
                                                    pair(now(), m -> m.onText("3", PART)),
                                                    pair(now(), m -> m.onText("4", LAST)),
                                                    pair(now(), m -> m.onClose(NORMAL_CLOSURE, "no reason")));
                        }
                    });
        Thread.sleep(2000);
    }

    private CompletionStage<?> inSeconds(long s) {
        return new CompletableFuture<>().completeOnTimeout(null, s, TimeUnit.SECONDS);
    }

    private CompletionStage<?> now() {
        return completedStage(null);
    }
}
