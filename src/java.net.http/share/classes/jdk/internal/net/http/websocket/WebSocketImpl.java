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
import java.io.InterruptedIOException;
import java.lang.ref.Reference;
import java.lang.System.Logger.Level;
import java.net.ProtocolException;
import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.Function;

import static java.util.Objects.requireNonNull;
import static jdk.internal.net.http.common.MinimalFuture.completedFuture;
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

    private static final boolean DEBUG = Utils.DEBUG_WS;
    private static final System.Logger debug =
            Utils.getWebSocketLogger("[Websocket]"::toString, DEBUG);
    private final AtomicLong sendCounter = new AtomicLong();
    private final AtomicLong receiveCounter = new AtomicLong();

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

    private final AtomicReference<ByteBuffer> lastAutomaticPong = new AtomicReference<>();
    private final MinimalFuture<WebSocket> DONE = MinimalFuture.completedFuture(this);
    private final long closeTimeout;
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
        closeTimeout = readCloseTimeout();
    }

    private static int readCloseTimeout() {
        String property = "jdk.httpclient.websocket.closeTimeout";
        int defaultValue = 30;
        String value = Utils.getNetProperty(property);
        int v;
        if (value == null) {
            v = defaultValue;
        } else {
            try {
                v = Integer.parseUnsignedInt(value);
            } catch (NumberFormatException ignored) {
                v = defaultValue;
            }
        }
        debug.log(Level.DEBUG, "%s=%s, using value %s",
                              property, value, v);
        return v;
    }

    // FIXME: add to action handling of errors -> signalError()

    @Override
    public CompletableFuture<WebSocket> sendText(CharSequence message,
                                                 boolean isLast) {
        Objects.requireNonNull(message);
        long id = 0;
        if (debug.isLoggable(Level.DEBUG)) {
            id = sendCounter.incrementAndGet();
            debug.log(Level.DEBUG, "%s send text: payload length=%s last=%s",
                              id, message.length(), isLast);
        }
        CompletableFuture<WebSocket> result;
        if (!outstandingSend.compareAndSet(false, true)) {
            result = failedFuture(new IllegalStateException("Send pending"));
        } else {
            result = transport.sendText(message, isLast, this,
                                        (r, e) -> outstandingSend.set(false));
        }
        debug.log(Level.DEBUG,
                  "%s send text: returned %s",
                  id, result);

        return replaceNull(result);
    }

    @Override
    public CompletableFuture<WebSocket> sendBinary(ByteBuffer message,
                                                   boolean isLast) {
        Objects.requireNonNull(message);
        long id = 0;
        if (debug.isLoggable(Level.DEBUG)) {
            id = sendCounter.incrementAndGet();
            debug.log(Level.DEBUG,
                    "%s send binary: payload=%s last=%s",
                    id, message, isLast);
        }
        CompletableFuture<WebSocket> result;
        if (!outstandingSend.compareAndSet(false, true)) {
            result = failedFuture(new IllegalStateException("Send pending"));
        } else {
            result = transport.sendBinary(message, isLast, this,
                                          (r, e) -> outstandingSend.set(false));
        }
        debug.log(Level.DEBUG,
                "%s send binary: returned %s", id, result);
        return replaceNull(result);
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
        long id = 0;
        if (debug.isLoggable(Level.DEBUG)) {
            id = sendCounter.incrementAndGet();
            debug.log(Level.DEBUG, "%s send ping: payload=%s",
                              id, message);
        }
        CompletableFuture<WebSocket> result = transport.sendPing(message, this,
                                                                 (r, e) -> { });
        debug.log(Level.DEBUG, "%s send ping: returned %s", id, result);
        return replaceNull(result);
    }

    @Override
    public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
        Objects.requireNonNull(message);
        long id = 0;
        if (debug.isLoggable(Level.DEBUG)) {
            id = sendCounter.incrementAndGet();
            debug.log(Level.DEBUG, "%s send pong: payload=%s",
                              id, message);
        }
        CompletableFuture<WebSocket> result = transport.sendPong(message, this,
                                                                 (r, e) -> { });
        debug.log(Level.DEBUG, "%s send pong: returned %s",
                          id, result);
        return replaceNull(result);
    }

    @Override
    public CompletableFuture<WebSocket> sendClose(int statusCode,
                                                  String reason) {
        Objects.requireNonNull(reason);
        long id = 0;
        if (debug.isLoggable(Level.DEBUG)) {
            id = sendCounter.incrementAndGet();
            debug.log(Level.DEBUG,
                      "%s send close: statusCode=%s, reason.length=%s",
                      id, statusCode, reason);
        }
        CompletableFuture<WebSocket> result;
        if (!isLegalToSendFromClient(statusCode)) {
            result = failedFuture(new IllegalArgumentException("statusCode"));
        } else {
            // check outputClosed
            result = sendClose0(statusCode, reason);
        }
        debug.log(Level.DEBUG, "%s send close: returned %s",
                          id, result);
        return replaceNull(result);
    }

    private CompletableFuture<WebSocket> sendClose0(int statusCode,
                                                    String reason) {
        outputClosed = true;
        CompletableFuture<WebSocket> cf
                = transport.sendClose(statusCode, reason, this, (r, e) -> { });
        CompletableFuture<WebSocket> closeOrTimeout
                = replaceNull(cf).orTimeout(closeTimeout, TimeUnit.SECONDS);
        // The snippet below, whose purpose might not be immediately obvious,
        // is a trick used to complete a dependant stage with an IOException.
        // A checked IOException cannot be thrown from inside the BiConsumer
        // supplied to the handle method. Instead a CompletionStage completed
        // exceptionally with this IOException is returned.
        return closeOrTimeout.handle(this::processCloseOutcome)
                             .thenCompose(Function.identity());
    }

    private CompletionStage<WebSocket> processCloseOutcome(WebSocket webSocket,
                                                           Throwable e) {
        if (e == null) {
            debug.log(Level.DEBUG, "send close completed successfully");
        } else {
            debug.log(Level.DEBUG, "send close completed with error", e);
        }

        if (e == null) {
            return completedFuture(webSocket);
        }
        Throwable cause = Utils.getCompletionCause(e);
        if (cause instanceof IllegalArgumentException) {
            return failedFuture(cause);
        }
        try {
            transport.closeOutput();
        } catch (IOException ignored) { }

        if (cause instanceof TimeoutException) {
            inputClosed = true;
            try {
                transport.closeInput();
            } catch (IOException ignored) { }
            return failedFuture(new InterruptedIOException(
                    "Could not send close within a reasonable timeout"));
        }
        return failedFuture(cause);
    }

    @Override
    public void request(long n) {
        debug.log(Level.DEBUG, "request %s", n);
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
        debug.log(Level.DEBUG, "abort");
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
            debug.log(Level.DEBUG, "enter receive task");
            loop:
            while (true) {
                State s = state.get();
                debug.log(Level.DEBUG, "receive state: %s", s);

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
                            break loop;
                        case ERROR:
                            processError();
                            break loop;
                        case IDLE:
                            if (demand.tryDecrement()
                                    && tryChangeState(IDLE, WAITING)) {
                                transport.request(1);
                            }
                            break loop;
                        case WAITING:
                            // For debugging spurious signalling: when there was a
                            // signal, but apparently nothing has changed
                            break loop;
                        default:
                            throw new InternalError(String.valueOf(s));
                    }
                } catch (Throwable t) {
                    signalError(t);
                }
            }
            debug.log(Level.DEBUG, "exit receive task");
        }

        private void processError() throws IOException {
            debug.log(Level.DEBUG, "processError");
            transport.closeInput();
            receiveScheduler.stop();
            Throwable err = error.get();
            if (err instanceof FailWebSocketException) {
                int code1 = ((FailWebSocketException) err).getStatusCode();
                err = new ProtocolException().initCause(err);
                debug.log(Level.DEBUG,
                          "failing %s with error=%s statusCode=%s",
                          WebSocketImpl.this, err, code1);
                sendClose0(code1, "") // TODO handle errors from here
                        .whenComplete(
                                (r, e) -> {
                                    if (e != null) {
                                        Log.logError(e);
                                    }
                                });
            }
            long id = 0;
            if (debug.isLoggable(Level.DEBUG)) {
                id = receiveCounter.incrementAndGet();
                debug.log(Level.DEBUG, "enter onError %s error=%s",
                                  id, err);
            }
            try {
                listener.onError(WebSocketImpl.this, err);
            } finally {
                debug.log(Level.DEBUG, "exit onError %s", id);
            }
        }

        private void processClose() throws IOException {
            debug.log(Level.DEBUG, "processClose");
            transport.closeInput();
            receiveScheduler.stop();
            CompletionStage<?> cs = null; // when the listener is ready to close
            long id = 0;
            if (debug.isLoggable(Level.DEBUG)) {
                id = receiveCounter.incrementAndGet();
                debug.log(Level.DEBUG, "enter onClose %s statusCode=%s reason.length=%s",
                                  id, statusCode, reason.length());
            }
            try {
                cs = listener.onClose(WebSocketImpl.this, statusCode, reason);
            } finally {
                debug.log(Level.DEBUG,
                          "exit onClose %s returned %s",
                           id, cs);
            }
            if (cs == null) {
                cs = DONE;
            }
            int code;
            if (statusCode == NO_STATUS_CODE || statusCode == CLOSED_ABNORMALLY) {
                code = NORMAL_CLOSURE;
                debug.log(Level.DEBUG,
                          "using statusCode %s instead of %s",
                           statusCode, code);

            } else {
                code = statusCode;
            }
            cs.whenComplete((r, e) -> { // TODO log
                sendClose0(code, "") // TODO handle errors from here
                        .whenComplete((r1, e1) -> {
                                if (e1 != null) {
                                    debug.log(Level.DEBUG,
                                              "processClose completed with errors",
                                               e1);
                                }
                        });
            });
        }

        private void processPong() {
            long id = 0;
            if (debug.isLoggable(Level.DEBUG)) {
                id = receiveCounter.incrementAndGet();
                debug.log(Level.DEBUG, "enter onPong %s payload=%s",
                                  id, binaryData);
            }
            CompletionStage<?> cs = null;
            try {
                cs = listener.onPong(WebSocketImpl.this, binaryData);
            } finally {
                debug.log(Level.DEBUG,
                          "exit onPong %s returned %s",
                          id, cs);
            }
        }

        private void processPing() {
            debug.log(Level.DEBUG, "processPing");
            ByteBuffer slice = binaryData.slice();
            // A full copy of this (small) data is made. This way sending a
            // replying Pong could be done in parallel with the listener
            // handling this Ping.
            ByteBuffer copy = ByteBuffer.allocate(binaryData.remaining())
                    .put(binaryData)
                    .flip();
            if (!trySwapAutomaticPong(copy)) {
                // Non-exclusive send;
                BiConsumer<WebSocketImpl, Throwable> reporter = (r, e) -> {
                    if (e != null) { // TODO: better error handing. What if already closed?
                        signalError(Utils.getCompletionCause(e));
                    }
                };
                transport.sendPong(WebSocketImpl.this::clearAutomaticPong,
                                   WebSocketImpl.this,
                                   reporter);
            }
            long id = 0;
            if (debug.isLoggable(Level.DEBUG)) {
                id = receiveCounter.incrementAndGet();
                debug.log(Level.DEBUG, "enter onPing %s payload=%s",
                                  id, slice);
            }
            CompletionStage<?> cs = null;
            try {
                cs = listener.onPing(WebSocketImpl.this, slice);
            } finally {
                debug.log(Level.DEBUG,
                          "exit onPing %s returned %s",
                          id, cs);
            }
        }

        private void processBinary() {
            long id = 0;
            if (debug.isLoggable(Level.DEBUG)) {
                id = receiveCounter.incrementAndGet();
                debug.log(Level.DEBUG,
                          "enter onBinary %s payload=%s part=%s",
                          id, binaryData, part);
            }
            CompletionStage<?> cs = null;
            try {
                cs = listener.onBinary(WebSocketImpl.this, binaryData, part);
            } finally {
                debug.log(Level.DEBUG,
                          "exit onBinary %s returned %s",
                          id, cs);
            }
        }

        private void processText() {
            long id = 0;
            if (debug.isLoggable(Level.DEBUG)) {
                id = receiveCounter.incrementAndGet();
                debug.log(Level.DEBUG,
                          "enter onText %s payload.length=%s part=%s",
                          id, text.length(), part);
            }
            CompletionStage<?> cs = null;
            try {
                cs = listener.onText(WebSocketImpl.this, text, part);
            } finally {
                debug.log(Level.DEBUG,
                          "exit onText %s returned %s", id, cs);
            }
        }

        private void processOpen() {
            long id = 0;
            if (debug.isLoggable(Level.DEBUG)) {
                id = receiveCounter.incrementAndGet();
                debug.log(Level.DEBUG, "enter onOpen %s", id);
            }
            try {
                listener.onOpen(WebSocketImpl.this);
            } finally {
                debug.log(Level.DEBUG, "exit onOpen %s", id);
            }
        }
    }

    private ByteBuffer clearAutomaticPong() {
        ByteBuffer data;
        do {
            data = lastAutomaticPong.get();
            if (data == null) {
                // This method must never be called unless a message that is
                // using it has been added previously
                throw new InternalError();
            }
        } while (!lastAutomaticPong.compareAndSet(data, null));
        return data;
    }

    private boolean trySwapAutomaticPong(ByteBuffer copy) {
        ByteBuffer message;
        boolean swapped;
        while (true) {
            message = lastAutomaticPong.get();
            if (message == null) {
                if (!lastAutomaticPong.compareAndSet(null, copy)) {
                    // It's only this method that can change null to ByteBuffer,
                    // and this method is invoked at most by one thread at a
                    // time. Thus no failure in the atomic operation above is
                    // expected.
                    throw new InternalError();
                }
                swapped = false;
                break;
            } else if (lastAutomaticPong.compareAndSet(message, copy)) {
                swapped = true;
                break;
            }
        }
        debug.log(Level.DEBUG, "swapped automatic pong from %s to %s%n",
                              message, copy);
        return swapped;
    }

    private void signalOpen() {
        debug.log(Level.DEBUG, "signalOpen");
        receiveScheduler.runOrSchedule();
    }

    private void signalError(Throwable error) {
        debug.log(Level.DEBUG, "signalError %s", error);
        inputClosed = true;
        outputClosed = true;
        if (!this.error.compareAndSet(null, error) || !trySetState(ERROR)) {
            Log.logError(error);
        } else {
            close();
        }
    }

    private void close() {
        debug.log(Level.DEBUG, "close");
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
                if (e != null) {
                    debug.log(Level.DEBUG,
                              "unexpected exception in close: ",
                              e);
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
        debug.log(Level.DEBUG,
                  "signalClose statusCode=%s, reason.length()=%s: %s",
                  statusCode, reason.length(), managed);
        if (managed) {
            try {
                transport.closeInput();
            } catch (Throwable t) {
                debug.log(Level.DEBUG,
                          "unexpected exception closing input", t);
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
        debug.log(Level.DEBUG,
                  "set state %s (previous %s) %s",
                  newState, currentState, success);
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
        debug.log(Level.DEBUG,
                  "change state from %s to %s %s",
                  expectedState, newState, success);
        return success;
    }

    /* Exposed for testing purposes */
    protected Transport transport() {
        return transport;
    }
}
