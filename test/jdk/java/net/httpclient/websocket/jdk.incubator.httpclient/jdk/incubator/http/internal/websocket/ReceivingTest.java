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
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

import static java.util.concurrent.CompletableFuture.completedStage;
import static jdk.incubator.http.WebSocket.MessagePart.FIRST;
import static jdk.incubator.http.WebSocket.MessagePart.LAST;
import static jdk.incubator.http.WebSocket.MessagePart.PART;
import static jdk.incubator.http.WebSocket.MessagePart.WHOLE;
import static jdk.incubator.http.WebSocket.NORMAL_CLOSURE;
import static jdk.incubator.http.internal.common.Pair.pair;
import static jdk.incubator.http.internal.websocket.MockListener.ListenerInvocation.onClose;
import static jdk.incubator.http.internal.websocket.MockListener.ListenerInvocation.onError;
import static jdk.incubator.http.internal.websocket.MockListener.ListenerInvocation.onOpen;
import static jdk.incubator.http.internal.websocket.MockListener.ListenerInvocation.onText;
import static org.testng.Assert.assertEquals;

public class ReceivingTest {

    // TODO: request in onClose/onError
    // TODO: throw exception in onClose/onError
    // TODO: exception is thrown from request()

    @Test
    public void testNonPositiveRequest() throws Exception {
        MockListener listener = new MockListener(Long.MAX_VALUE) {
            @Override
            protected void onOpen0(WebSocket webSocket) {
                webSocket.request(0);
            }
        };
        MockTransport transport = new MockTransport() {
            @Override
            protected Receiver newReceiver(MessageStreamConsumer consumer) {
                return new MockReceiver(consumer, channel, pair(now(), m -> m.onText("1", WHOLE)));
            }
        };
        WebSocket ws = newInstance(listener, transport);
        listener.onCloseOrOnErrorCalled().get(10, TimeUnit.SECONDS);
        List<MockListener.ListenerInvocation> invocations = listener.invocations();
        assertEquals(invocations, List.of(onOpen(ws), onError(ws, IllegalArgumentException.class)));
    }

    @Test
    public void testText1() throws Exception {
        MockListener listener = new MockListener(Long.MAX_VALUE);
        MockTransport transport = new MockTransport() {
            @Override
            protected Receiver newReceiver(MessageStreamConsumer consumer) {
                return new MockReceiver(consumer, channel,
                                        pair(now(), m -> m.onText("1", FIRST)),
                                        pair(now(), m -> m.onText("2", PART)),
                                        pair(now(), m -> m.onText("3", PART)),
                                        pair(now(), m -> m.onText("4", LAST)),
                                        pair(now(), m -> m.onClose(NORMAL_CLOSURE, "no reason")));
            }
        };
        WebSocket ws = newInstance(listener, transport);
        listener.onCloseOrOnErrorCalled().get(10, TimeUnit.SECONDS);
        List<MockListener.ListenerInvocation> invocations = listener.invocations();
        assertEquals(invocations, List.of(onOpen(ws),
                                          onText(ws, "1", FIRST),
                                          onText(ws, "2", PART),
                                          onText(ws, "3", PART),
                                          onText(ws, "4", LAST),
                                          onClose(ws, NORMAL_CLOSURE, "no reason")));
    }

    private static CompletionStage<?> seconds(long s) {
        return new CompletableFuture<>().completeOnTimeout(null, s, TimeUnit.SECONDS);
    }

    private static CompletionStage<?> now() {
        return completedStage(null);
    }

    private static WebSocket newInstance(WebSocket.Listener listener,
                                         TransportSupplier transport) {
        URI uri = URI.create("ws://localhost");
        String subprotocol = "";
        return WebSocketImpl.newInstance(uri, subprotocol, listener, transport);
    }
}
