/*
 * Copyright (c) 2015, 2018, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package jdk.internal.net.http.websocket;

import jdk.internal.net.http.common.Demand;
import jdk.internal.net.http.common.Log;
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.websocket.OpeningHandshake.Result;

import java.io.IOException;
import java.lang.ref.Reference;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static jdk.internal.net.http.common.MinimalFuture.failedFuture;
import static jdk.internal.net.http.websocket.StatusCodes.CLOSED_ABNORMALLY;
import static jdk.internal.net.http.websocket.StatusCodes.NO_STATUS_CODE;
import static jdk.internal.net.http.websocket.StatusCodes.isLegalToSendFromClient;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.BINARY;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.CLOSE;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.ERROR;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.IDLE;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.OPEN;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.PING;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.PONG;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.TEXT;
import static jdk.internal.net.http.websocket.WebSocketImpl.State.WAITING;

/*
 * A WebSocket client.
 */
public final class WebSocketImpl implements WebSocket {

    private final static boolean DEBUG = false;

    enum State {
        OPEN,
        IDLE,
        WAITING,
        TEXT,
        BINARY,
        PING,
        PONG,
        CLOSE,
        ERROR;
    }

    private final MinimalFuture<WebSocket> DONE = MinimalFuture.completedFuture(this);
    private volatile boolean inputClosed;
    private volatile boolean outputClosed;

    private final AtomicReference<State> state = new AtomicReference<>(OPEN);

    /* Components of calls to Listener's methods */
    private MessagePart part;
    private ByteBuffer binaryData;
    private CharSequence text;
    private int statusCode;
    private String reason;
    private final AtomicReference<Throwable> error = new AtomicReference<>();

    private final URI uri;
    private final String subprotocol;
    private final Listener listener;

    private final AtomicBoolean outstandingSend = new AtomicBoolean();
    private final Transport transport;
    private final SequentialScheduler receiveScheduler
            = new SequentialScheduler(new ReceiveTask());
    private final Demand demand = new Demand();

    public static CompletableFuture<WebSocket> newInstanceAsync(BuilderImpl b) {
        Function<Result, WebSocket> newWebSocket = r -> {
            WebSocket ws = newInstance(b.getUri(),
                                       r.subprotocol,
                                       b.getListener(),
                                       r.transport);
            // Make sure we don't release the builder until this lambda
            // has been executed. The builder has a strong reference to
            // the HttpClientFacade, and we want to keep that live until
            // after the raw channel is created and passed to WebSocketImpl.
            Reference.reachabilityFence(b);
            return ws;
        };
        OpeningHandshake h;
        try {
            h = new OpeningHandshake(b);
        } catch (Throwable e) {
            return failedFuture(e);
        }
        return h.send().thenApply(newWebSocket);
    }

    /* Exposed for testing purposes */
    static WebSocketImpl newInstance(URI uri,
                                     String subprotocol,
                                     Listener listener,
                                     TransportFactory transport) {
        WebSocketImpl ws = new WebSocketImpl(uri, subprotocol, listener, transport);
        // This initialisation is outside of the constructor for the sake of
        // safe publication of WebSocketImpl.this
        ws.signalOpen();
        return ws;
    }

    private WebSocketImpl(URI uri,
                          String subprotocol,
                          Listener listener,
                          TransportFactory transportFactory) {
        this.uri = requireNonNull(uri);
        this.subprotocol = requireNonNull(subprotocol);
        this.listener = requireNonNull(listener);
        this.transport = transportFactory.createTransport(
                new SignallingMessageConsumer());
    }

    // FIXME: add to action handling of errors -> signalError()

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence message,
                                                 boolean isLast) {
        Objects.requireNonNull(message);
        if (!outstandingSend.compareAndSet(false, true)) {
            return failedFuture(new IllegalStateException("Send pending"));
        }
        CompletableFuture<WebSocket> cf
                = transport.sendText(message, isLast, this,
                                     (r, e) -> outstandingSend.set(false));
        return replaceNull(cf);
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer message,
                                                   boolean isLast) {
        Objects.requireNonNull(message);
        if (!outstandingSend.compareAndSet(false, true)) {
            return failedFuture(new IllegalStateException("Send pending"));
        }
        CompletableFuture<WebSocket> cf
                = transport.sendBinary(message, isLast, this,
                                       (r, e) -> outstandingSend.set(false));
        return replaceNull(cf);
    }

    private CompletableFuture<WebSocket> replaceNull(
            CompletableFuture<WebSocket> cf)
    {
        if (cf == null) {
            return DONE;
        } else {
            return cf;
        }
    }

    @Override
    public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
        Objects.requireNonNull(message);
        CompletableFuture<WebSocket> cf
                = transport.sendPing(message, this, (r, e) -> { });
        return replaceNull(cf);
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        Objects.requireNonNull(message);
        CompletableFuture<WebSocket> cf
                = transport.sendPong(message, this, (r, e) -> { });
        return replaceNull(cf);
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(int statusCode,
                                                  String reason) {
        Objects.requireNonNull(reason);
        if (!isLegalToSendFromClient(statusCode)) {
            return failedFuture(new IllegalArgumentException("statusCode"));
        }
        // check outputClosed
        CompletableFuture<WebSocket> cf = sendClose0(statusCode, reason);
        return replaceNull(cf);
    }

    /*
     * Sends a Close message, then shuts down the output since no more
     * messages are expected to be sent at this point.
     */
    private CompletableFuture<WebSocket> sendClose0(int statusCode,
                                                    String reason) {
        outputClosed = true;
        BiConsumer<WebSocket, Throwable> closer = (r, e) -> {
            Throwable cause = Utils.getCompletionCause(e);
            if (cause instanceof IllegalArgumentException) {
                // or pre=check it (isLegalToSendFromClient(statusCode))
                return;
            }
            try {
                transport.closeOutput();
            } catch (IOException ex) {
                Log.logError(ex);
            }
            if (cause instanceof TimeoutException) { // FIXME: it is not the case anymore
                if (DEBUG) {
                    System.out.println("[WebSocket] sendClose0 error: " + e);
                }
                try {
                    transport.closeInput();
                } catch (IOException ex) {
                    Log.logError(ex);
                }
            }
        };
        CompletableFuture<WebSocket> cf
                = transport.sendClose(statusCode, reason, this, closer);
        return cf;
    }

    @Override
    public void request(long n) {
        if (DEBUG) {
            System.out.printf("[WebSocket] request(%s)%n", n);
        }
        if (demand.increase(n)) {
            receiveScheduler.runOrSchedule();
        }
    }

    @Override
    public String getSubprotocol() {
        return subprotocol;
    }

    @Override
    public boolean isOutputClosed() {
        return outputClosed;
    }

    @Override
    public boolean isInputClosed() {
        return inputClosed;
    }

    @Override
    public void abort() {
        if (DEBUG) {
            System.out.printf("[WebSocket] abort()%n");
        }
        inputClosed = true;
        outputClosed = true;
        receiveScheduler.stop();
        close();
    }

    @Override
    public String toString() {
        return super.toString()
                + "[uri=" + uri
                + (!subprotocol.isEmpty() ? ", subprotocol=" + subprotocol : "")
                + "]";
    }

    /*
     * The assumptions about order is as follows:
     *
     *     - state is never changed more than twice inside the `run` method:
     *       x --(1)--> IDLE --(2)--> y (otherwise we're loosing events, or
     *       overwriting parts of messages creating a mess since there's no
     *       queueing)
     *     - OPEN is always the first state
     *     - no messages are requested/delivered before onOpen is called (this
     *       is implemented by making WebSocket instance accessible first in
     *       onOpen)
     *     - after the state has been observed as CLOSE/ERROR, the scheduler
     *       is stopped
     */
    private class ReceiveTask extends SequentialScheduler.CompleteRestartableTask {

        // Transport only asked here and nowhere else because we must make sure
        // onOpen is invoked first and no messages become pending before onOpen
        // finishes

        @Override
        public void run() {
            while (true) {
                State s = state.get();
                try {
                    switch (s) {
                        case OPEN:
                            processOpen();
                            tryChangeState(OPEN, IDLE);
                            break;
                        case TEXT:
                            processText();
                            tryChangeState(TEXT, IDLE);
                            break;
                        case BINARY:
                            processBinary();
                            tryChangeState(BINARY, IDLE);
                            break;
                        case PING:
                            processPing();
                            tryChangeState(PING, IDLE);
                            break;
                        case PONG:
                            processPong();
                            tryChangeState(PONG, IDLE);
                            break;
                        case CLOSE:
                            processClose();
                            return;
                        case ERROR:
                            processError();
                            return;
                        case IDLE:
                            if (demand.tryDecrement()
                                    && tryChangeState(IDLE, WAITING)) {
                                transport.request(1);
                            }
                            return;
                        case WAITING:
                            // For debugging spurious signalling: when there was a
                            // signal, but apparently nothing has changed
                            return;
                        default:
                            throw new InternalError(String.valueOf(s));
                    }
                } catch (Throwable t) {
                    signalError(t);
                }
            }
        }

        private void processError() throws IOException {
            if (DEBUG) {
                System.out.println("[WebSocket] processError");
            }
            transport.closeInput();
            receiveScheduler.stop();
            Throwable err = error.get();
            if (err instanceof FailWebSocketException) {
                int code1 = ((FailWebSocketException) err).getStatusCode();
                err = new ProtocolException().initCause(err);
                sendClose0(code1, "")
                        .whenComplete(
                                (r, e) -> {
                                    if (e != null) {
                                        Log.logError(e);
                                    }
                                });
            }
            listener.onError(WebSocketImpl.this, err);
        }

        private void processClose() throws IOException {
            if (DEBUG) {
                System.out.println("[WebSocket] processClose");
            }
            transport.closeInput();
            receiveScheduler.stop();
            CompletionStage<?> readyToClose;
            readyToClose = listener.onClose(WebSocketImpl.this, statusCode, reason);
            if (readyToClose == null) {
                readyToClose = DONE;
            }
            int code;
            if (statusCode == NO_STATUS_CODE || statusCode == CLOSED_ABNORMALLY) {
                code = NORMAL_CLOSURE;
                if (DEBUG) {
                    System.out.printf("[WebSocket] using statusCode %s instead of %s%n",
                                      statusCode, code);
                }
            } else {
                code = statusCode;
            }
            readyToClose.whenComplete((r, e) -> {
                sendClose0(code, "") // FIXME errors from here?
                        .whenComplete((r1, e1) -> {
                            if (DEBUG) {
                                if (e1 != null) {
                                    e1.printStackTrace(System.out);
                                }
                            }
                        });
            });
        }

        private void processPong() {
            listener.onPong(WebSocketImpl.this, binaryData);
        }

        private void processPing() {
            // Let's make a full copy of this tiny data. What we want here
            // is to rule out a possibility the shared data we send might be
            // corrupted by processing in the listener.
            ByteBuffer slice = binaryData.slice();
            ByteBuffer copy = ByteBuffer.allocate(binaryData.remaining())
                    .put(binaryData)
                    .flip();
            // Non-exclusive send;
            BiConsumer<WebSocketImpl, Throwable> reporter = (r, e) -> {
                if (e != null) { // Better error handing. What if already closed?
                    signalError(Utils.getCompletionCause(e));
                }
            };
            transport.sendPong(copy, WebSocketImpl.this, reporter);
            listener.onPing(WebSocketImpl.this, slice);
        }

        private void processBinary() {
            listener.onBinary(WebSocketImpl.this, binaryData, part);
        }

        private void processText() {
            listener.onText(WebSocketImpl.this, text, part);
        }

        private void processOpen() {
            listener.onOpen(WebSocketImpl.this);
        }
    }

    private void signalOpen() {
        if (DEBUG) {
            System.out.printf("[WebSocket] signalOpen%n");
        }
        receiveScheduler.runOrSchedule();
    }

    private void signalError(Throwable error) {
        if (DEBUG) {
            System.out.printf("[WebSocket] signalError %s%n", error);
        }
        inputClosed = true;
        outputClosed = true;
        if (!this.error.compareAndSet(null, error) || !trySetState(ERROR)) {
            Log.logError(error);
        } else {
            close();
        }
    }

    private void close() {
        if (DEBUG) {
            System.out.println("[WebSocket] close");
        }
        Throwable first = null;
        try {
            transport.closeInput();
        } catch (Throwable t1) {
            first = t1;
        } finally {
            Throwable second = null;
            try {
                transport.closeOutput();
            } catch (Throwable t2) {
                second = t2;
            } finally {
                Throwable e = null;
                if (first != null && second != null) {
                    first.addSuppressed(second);
                    e = first;
                } else if (first != null) {
                    e = first;
                } else if (second != null) {
                    e = second;
                }
                if (DEBUG) {
                    if (e != null) {
                        e.printStackTrace(System.out);
                    }
                }
            }
        }
    }

    private void signalClose(int statusCode, String reason) {
        // FIXME: make sure no race reason & close are not intermixed
        inputClosed = true;
        this.statusCode = statusCode;
        this.reason = reason;
        boolean managed = trySetState(CLOSE);
        if (DEBUG) {
            System.out.printf("[WebSocket] signalClose statusCode=%s, reason.length()=%s: %s%n",
                              statusCode, reason.length(), managed);
        }
        if (managed) {
            try {
                transport.closeInput();
            } catch (Throwable t) {
                if (DEBUG) {
                    t.printStackTrace(System.out);
                }
            }
        }
    }

    private class SignallingMessageConsumer implements MessageStreamConsumer {

        @Override
        public void onText(CharSequence data, MessagePart part) {
            transport.acknowledgeReception();
            text = data;
            WebSocketImpl.this.part = part;
            tryChangeState(WAITING, TEXT);
        }

        @Override
        public void onBinary(ByteBuffer data, MessagePart part) {
            transport.acknowledgeReception();
            binaryData = data;
            WebSocketImpl.this.part = part;
            tryChangeState(WAITING, BINARY);
        }

        @Override
        public void onPing(ByteBuffer data) {
            transport.acknowledgeReception();
            binaryData = data;
            tryChangeState(WAITING, PING);
        }

        @Override
        public void onPong(ByteBuffer data) {
            transport.acknowledgeReception();
            binaryData = data;
            tryChangeState(WAITING, PONG);
        }

        @Override
        public void onClose(int statusCode, CharSequence reason) {
            transport.acknowledgeReception();
            signalClose(statusCode, reason.toString());
        }

        @Override
        public void onComplete() {
            transport.acknowledgeReception();
            signalClose(CLOSED_ABNORMALLY, "");
        }

        @Override
        public void onError(Throwable error) {
            signalError(error);
        }
    }

    private boolean trySetState(State newState) {
        State currentState;
        boolean success = false;
        while (true) {
            currentState = state.get();
            if (currentState == ERROR || currentState == CLOSE) {
                break;
            } else if (state.compareAndSet(currentState, newState)) {
                receiveScheduler.runOrSchedule();
                success = true;
                break;
            }
        }
        if (DEBUG) {
            System.out.printf("[WebSocket] set state %s (previous %s) %s%n",
                              newState, currentState, success);
        }
        return success;
    }

    private boolean tryChangeState(State expectedState, State newState) {
        State witness = state.compareAndExchange(expectedState, newState);
        boolean success = false;
        if (witness == expectedState) {
            receiveScheduler.runOrSchedule();
            success = true;
        } else if (witness != ERROR && witness != CLOSE) {
            // This should be the only reason for inability to change the state
            // from IDLE to WAITING: the state has changed to terminal
            throw new InternalError();
        }
        if (DEBUG) {
            System.out.printf("[WebSocket] change state from %s to %s %s%n",
                              expectedState, newState, success);
        }
        return success;
    }

    /* Exposed for testing purposes */
    protected Transport transport() {
        return transport;
    }
}
