/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.websocket;

import jdk.incubator.http.internal.common.Demand;
import jdk.incubator.http.internal.common.MinimalFuture;
import jdk.incubator.http.internal.common.Pair;
import jdk.incubator.http.internal.common.SequentialScheduler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static java.util.Objects.requireNonNull;
import static jdk.incubator.http.internal.common.Pair.pair;

public class TransportImpl<T> implements Transport<T> {

    /* This flag is used solely for assertions */
    private final AtomicBoolean busy = new AtomicBoolean();
    private OutgoingMessage message;
    private Consumer<Exception> completionHandler;
    private final RawChannel channel;
    private final RawChannel.RawEvent writeEvent = createWriteEvent();
    private final SequentialScheduler sendScheduler = new SequentialScheduler(new SendTask());
    private final Queue<Pair<OutgoingMessage, CompletableFuture<T>>>
            queue = new ConcurrentLinkedQueue<>();
    private final OutgoingMessage.Context context = new OutgoingMessage.Context();
    private final Supplier<T> resultSupplier;

    private final MessageStreamConsumer messageConsumer;
    private final FrameConsumer frameConsumer;
    private final Frame.Reader reader = new Frame.Reader();
    private final RawChannel.RawEvent readEvent = createReadEvent();
    private final Demand demand = new Demand();
    private final SequentialScheduler receiveScheduler;

    private ByteBuffer data;
    private volatile int state;

    private static final int UNREGISTERED = 0;
    private static final int AVAILABLE    = 1;
    private static final int WAITING      = 2;

    private final Object lock = new Object();
    private boolean inputClosed;
    private boolean outputClosed;

    public TransportImpl(Supplier<T> sendResultSupplier,
                         MessageStreamConsumer consumer,
                         RawChannel channel) {
        this.resultSupplier = sendResultSupplier;
        this.messageConsumer = consumer;
        this.channel = channel;
        this.frameConsumer = new FrameConsumer(this.messageConsumer);
        this.data = channel.initialByteBuffer();
        // To ensure the initial non-final `data` will be visible
        // (happens-before) when `readEvent.handle()` invokes `receiveScheduler`
        // the following assignment is done last:
        receiveScheduler = new SequentialScheduler(new ReceiveTask());
    }

    /**
     * The supplied handler may be invoked in the calling thread.
     * A {@code StackOverflowError} may thus occur if there's a possibility
     * that this method is called again by the supplied handler.
     */
    public void send(OutgoingMessage message,
                     Consumer<Exception> completionHandler) {
        requireNonNull(message);
        requireNonNull(completionHandler);
        if (!busy.compareAndSet(false, true)) {
            throw new IllegalStateException();
        }
        send0(message, completionHandler);
    }

    private RawChannel.RawEvent createWriteEvent() {
        return new RawChannel.RawEvent() {

            @Override
            public int interestOps() {
                return SelectionKey.OP_WRITE;
            }

            @Override
            public void handle() {
                // registerEvent(e) happens-before subsequent e.handle(), so
                // we're fine reading the stored message and the completionHandler
                send0(message, completionHandler);
            }
        };
    }

    private void send0(OutgoingMessage message, Consumer<Exception> handler) {
        boolean b = busy.get();
        assert b; // Please don't inline this, as busy.get() has memory
                  // visibility effects and we don't want the program behaviour
                  // to depend on whether the assertions are turned on
                  // or turned off
        try {
            boolean sent = message.sendTo(channel);
            if (sent) {
                busy.set(false);
                handler.accept(null);
            } else {
                // The message has not been fully sent, the transmitter needs to
                // remember the message until it can continue with sending it
                this.message = message;
                this.completionHandler = handler;
                try {
                    channel.registerEvent(writeEvent);
                } catch (IOException e) {
                    this.message = null;
                    this.completionHandler = null;
                    busy.set(false);
                    handler.accept(e);
                }
            }
        } catch (IOException e) {
            busy.set(false);
            handler.accept(e);
        }
    }

    public CompletableFuture<T> sendText(CharSequence message,
                                         boolean isLast) {
        return enqueue(new OutgoingMessage.Text(message, isLast));
    }

    public CompletableFuture<T> sendBinary(ByteBuffer message,
                                           boolean isLast) {
        return enqueue(new OutgoingMessage.Binary(message, isLast));
    }

    public CompletableFuture<T> sendPing(ByteBuffer message) {
        return enqueue(new OutgoingMessage.Ping(message));
    }

    public CompletableFuture<T> sendPong(ByteBuffer message) {
        return enqueue(new OutgoingMessage.Pong(message));
    }

    public CompletableFuture<T> sendClose(int statusCode, String reason) {
        return enqueue(new OutgoingMessage.Close(statusCode, reason));
    }

    private CompletableFuture<T> enqueue(OutgoingMessage m) {
        CompletableFuture<T> cf = new MinimalFuture<>();
        boolean added = queue.add(pair(m, cf));
        if (!added) {
            // The queue is supposed to be unbounded
            throw new InternalError();
        }
        sendScheduler.runOrSchedule();
        return cf;
    }

    /*
     * This is a message sending task. It pulls messages from the queue one by
     * one and sends them. It may be run in different threads, but never
     * concurrently.
     */
    private class SendTask implements SequentialScheduler.RestartableTask {

        @Override
        public void run(SequentialScheduler.DeferredCompleter taskCompleter) {
            Pair<OutgoingMessage, CompletableFuture<T>> p = queue.poll();
            if (p == null) {
                taskCompleter.complete();
                return;
            }
            OutgoingMessage message = p.first;
            CompletableFuture<T> cf = p.second;
            try {
                if (!message.contextualize(context)) { // Do not send the message
                    cf.complete(resultSupplier.get());
                    repeat(taskCompleter);
                    return;
                }
                Consumer<Exception> h = e -> {
                    if (e == null) {
                        cf.complete(resultSupplier.get());
                    } else {
                        cf.completeExceptionally(e);
                    }
                    repeat(taskCompleter);
                };
                send(message, h);
            } catch (Throwable t) {
                cf.completeExceptionally(t);
                repeat(taskCompleter);
            }
        }

        private void repeat(SequentialScheduler.DeferredCompleter taskCompleter) {
            taskCompleter.complete();
            // More than a single message may have been enqueued while
            // the task has been busy with the current message, but
            // there is only a single signal recorded
            sendScheduler.runOrSchedule();
        }
    }

    private RawChannel.RawEvent createReadEvent() {
        return new RawChannel.RawEvent() {

            @Override
            public int interestOps() {
                return SelectionKey.OP_READ;
            }

            @Override
            public void handle() {
                state = AVAILABLE;
                receiveScheduler.runOrSchedule();
            }
        };
    }

    @Override
    public void request(long n) {
        if (demand.increase(n)) {
            receiveScheduler.runOrSchedule();
        }
    }

    @Override
    public void acknowledgeReception() {
        boolean decremented = demand.tryDecrement();
        if (!decremented) {
            throw new InternalError();
        }
    }

    private class ReceiveTask extends SequentialScheduler.CompleteRestartableTask {

        @Override
        public void run() {
            while (!receiveScheduler.isStopped()) {
                if (data.hasRemaining()) {
                    if (!demand.isFulfilled()) {
                        try {
                            int oldPos = data.position();
                            reader.readFrame(data, frameConsumer);
                            int newPos = data.position();
                            assert oldPos != newPos : data; // reader always consumes bytes
                        } catch (Throwable e) {
                            receiveScheduler.stop();
                            messageConsumer.onError(e);
                        }
                        continue;
                    }
                    break;
                }
                switch (state) {
                    case WAITING:
                        return;
                    case UNREGISTERED:
                        try {
                            state = WAITING;
                            channel.registerEvent(readEvent);
                        } catch (Throwable e) {
                            receiveScheduler.stop();
                            messageConsumer.onError(e);
                        }
                        return;
                    case AVAILABLE:
                        try {
                            data = channel.read();
                        } catch (Throwable e) {
                            receiveScheduler.stop();
                            messageConsumer.onError(e);
                            return;
                        }
                        if (data == null) { // EOF
                            receiveScheduler.stop();
                            messageConsumer.onComplete();
                            return;
                        } else if (!data.hasRemaining()) {
                            // No data at the moment Pretty much a "goto",
                            // reusing the existing code path for registration
                            state = UNREGISTERED;
                        }
                        continue;
                    default:
                        throw new InternalError(String.valueOf(state));
                }
            }
        }
    }

    /*
     * Permanently stops reading from the channel and delivering messages
     * regardless of the current demand and data availability.
     */
    @Override
    public void closeInput() throws IOException {
        synchronized (lock) {
            if (!inputClosed) {
                inputClosed = true;
                try {
                    receiveScheduler.stop();
                    channel.shutdownInput();
                } finally {
                    if (outputClosed) {
                        channel.close();
                    }
                }
            }
        }
    }

    @Override
    public void closeOutput() throws IOException {
        synchronized (lock) {
            if (!outputClosed) {
                outputClosed = true;
                try {
                    channel.shutdownOutput();
                } finally {
                    if (inputClosed) {
                        channel.close();
                    }
                }
            }
        }
    }
}
