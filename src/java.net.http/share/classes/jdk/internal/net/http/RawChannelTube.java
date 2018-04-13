/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.http;

import jdk.internal.net.http.common.Demand;
import jdk.internal.net.http.common.FlowTube;
import jdk.internal.net.http.common.Utils;
import jdk.internal.net.http.websocket.RawChannel;

import java.io.EOFException;
import java.io.IOException;
import java.lang.ref.Cleaner;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import java.lang.System.Logger.Level;

/*
 * I/O abstraction used to implement WebSocket.
 *
 */
public class RawChannelTube implements RawChannel {

    final HttpConnection connection;
    final FlowTube tube;
    final WritePublisher writePublisher;
    final ReadSubscriber readSubscriber;
    final Supplier<ByteBuffer> initial;
    final AtomicBoolean inited = new AtomicBoolean();
    final AtomicBoolean outputClosed = new AtomicBoolean();
    final AtomicBoolean inputClosed = new AtomicBoolean();
    final AtomicBoolean closed = new AtomicBoolean();
    final String dbgTag;
    final System.Logger debug;
    private static final Cleaner cleaner =
            Utils.ASSERTIONSENABLED  && Utils.DEBUG_WS ? Cleaner.create() : null;

    RawChannelTube(HttpConnection connection,
                   Supplier<ByteBuffer> initial) {
        this.connection = connection;
        this.tube = connection.getConnectionFlow();
        this.initial = initial;
        this.writePublisher = new WritePublisher();
        this.readSubscriber = new ReadSubscriber();
        dbgTag = "[WebSocket] RawChannelTube(" + tube.toString() +")";
        debug = Utils.getWebSocketLogger(dbgTag::toString, Utils.DEBUG_WS);
        connection.client().webSocketOpen();
        connectFlows();
        if (Utils.ASSERTIONSENABLED && Utils.DEBUG_WS) {
            // this is just for debug...
            cleaner.register(this, new CleanupChecker(closed, debug));
        }
    }

    // Make sure no back reference to RawChannelTube can exist
    // from this class. In particular it would be dangerous
    // to reference connection, since connection has a reference
    // to SocketTube with which a RawChannelTube is registered.
    // Ditto for HttpClientImpl, which might have a back reference
    // to the connection.
    static final class CleanupChecker implements Runnable {
        final AtomicBoolean closed;
        final System.Logger debug;
        CleanupChecker(AtomicBoolean closed, System.Logger debug) {
            this.closed = closed;
            this.debug = debug;
        }

        @Override
        public void run() {
            if (!closed.get()) {
                debug.log(Level.DEBUG,
                         "RawChannelTube was not closed before being released");
            }
        }
    }

    private void connectFlows() {
        debug.log(Level.DEBUG, "connectFlows");
        tube.connectFlows(writePublisher, readSubscriber);
    }

    class WriteSubscription implements Flow.Subscription {
        final Flow.Subscriber<? super List<ByteBuffer>> subscriber;
        final Demand demand = new Demand();
        volatile boolean cancelled;
        WriteSubscription(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            this.subscriber = subscriber;
        }
        @Override
        public void request(long n) {
            debug.log(Level.DEBUG, "WriteSubscription::request %d", n);
            demand.increase(n);
            RawEvent event;
            while ((event = writePublisher.events.poll()) != null) {
                debug.log(Level.DEBUG, "WriteSubscriber: handling event");
                event.handle();
                if (demand.isFulfilled()) break;
            }
        }
        @Override
        public void cancel() {
            cancelled = true;
            debug.log(Level.DEBUG, "WriteSubscription::cancel");
            shutdownOutput();
            RawEvent event;
            while ((event = writePublisher.events.poll()) != null) {
                debug.log(Level.DEBUG, "WriteSubscriber: handling event");
                event.handle();
            }
        }
    }

    class WritePublisher implements FlowTube.TubePublisher {
        final ConcurrentLinkedQueue<RawEvent> events = new ConcurrentLinkedQueue<>();
        volatile WriteSubscription writeSubscription;
        @Override
        public void subscribe(Flow.Subscriber<? super List<ByteBuffer>> subscriber) {
            debug.log(Level.DEBUG, "WritePublisher::subscribe");
            WriteSubscription subscription = new WriteSubscription(subscriber);
            subscriber.onSubscribe(subscription);
            writeSubscription = subscription;
        }
    }

    class ReadSubscriber implements  FlowTube.TubeSubscriber {

        volatile Flow.Subscription readSubscription;
        volatile boolean completed;
        long initialRequest;
        final ConcurrentLinkedQueue<RawEvent> events = new ConcurrentLinkedQueue<>();
        final ConcurrentLinkedQueue<ByteBuffer> buffers = new ConcurrentLinkedQueue<>();
        final AtomicReference<Throwable> errorRef = new AtomicReference<>();

        void checkEvents() {
            Flow.Subscription subscription = readSubscription;
            if (subscription != null) {
                Throwable error = errorRef.get();
                while (!buffers.isEmpty() || error != null || closed.get() || completed) {
                    RawEvent event = events.poll();
                    if (event == null) break;
                    debug.log(Level.DEBUG, "ReadSubscriber: handling event");
                    event.handle();
                }
            }
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            //buffers.add(initial.get());
            long n;
            synchronized (this) {
                readSubscription = subscription;
                n = initialRequest;
                initialRequest = 0;
            }
            debug.log(Level.DEBUG, "ReadSubscriber::onSubscribe");
            if (n > 0) {
                Throwable error = errorRef.get();
                if (error == null && !closed.get() && !completed) {
                    debug.log(Level.DEBUG, "readSubscription: requesting " + n);
                    subscription.request(n);
                }
            }
            checkEvents();
        }

        @Override
        public void onNext(List<ByteBuffer> item) {
            debug.log(Level.DEBUG, () -> "ReadSubscriber::onNext "
                    + Utils.remaining(item) + " bytes");
            buffers.addAll(item);
            checkEvents();
        }

        @Override
        public void onError(Throwable throwable) {
            if (closed.get() || errorRef.compareAndSet(null, throwable)) {
                debug.log(Level.DEBUG, "ReadSubscriber::onError", throwable);
                if (buffers.isEmpty()) {
                    checkEvents();
                    shutdownInput();
                }
            }
        }

        @Override
        public void onComplete() {
            debug.log(Level.DEBUG, "ReadSubscriber::onComplete");
            completed = true;
            if (buffers.isEmpty()) {
                checkEvents();
                shutdownInput();
            }
        }
    }


    /*
     * Registers given event whose callback will be called once only (i.e.
     * register new event for each callback).
     *
     * Memory consistency effects: actions in a thread calling registerEvent
     * happen-before any subsequent actions in the thread calling event.handle
     */
    public void registerEvent(RawEvent event) throws IOException {
        int interestOps = event.interestOps();
        if ((interestOps & SelectionKey.OP_WRITE) != 0) {
            debug.log(Level.DEBUG, "register write event");
            if (outputClosed.get()) throw new IOException("closed output");
            writePublisher.events.add(event);
            WriteSubscription writeSubscription = writePublisher.writeSubscription;
            if (writeSubscription != null) {
                while (!writeSubscription.demand.isFulfilled()) {
                    event = writePublisher.events.poll();
                    if (event == null) break;
                    event.handle();
                }
            }
        }
        if ((interestOps & SelectionKey.OP_READ) != 0) {
            debug.log(Level.DEBUG, "register read event");
            if (inputClosed.get()) throw new IOException("closed input");
            readSubscriber.events.add(event);
            readSubscriber.checkEvents();
            if (readSubscriber.buffers.isEmpty()
                    && !readSubscriber.events.isEmpty()) {
                Flow.Subscription readSubscription =
                        readSubscriber.readSubscription;
                if (readSubscription == null) {
                    synchronized (readSubscriber) {
                        readSubscription = readSubscriber.readSubscription;
                        if (readSubscription == null) {
                            readSubscriber.initialRequest = 1;
                            return;
                        }
                    }
                }
                assert  readSubscription != null;
                debug.log(Level.DEBUG, "readSubscription: requesting 1");
                readSubscription.request(1);
            }
        }
    }

    /**
     * Hands over the initial bytes. Once the bytes have been returned they are
     * no longer available and the method will throw an {@link
     * IllegalStateException} on each subsequent invocation.
     *
     * @return the initial bytes
     * @throws IllegalStateException
     *         if the method has been already invoked
     */
    public ByteBuffer initialByteBuffer() throws IllegalStateException {
        if (inited.compareAndSet(false, true)) {
            return initial.get();
        } else throw new IllegalStateException("initial buffer already drained");
    }

    /*
     * Returns a ByteBuffer with the data read or null if EOF is reached. Has no
     * remaining bytes if no data available at the moment.
     */
    public ByteBuffer read() throws IOException {
        debug.log(Level.DEBUG, "read");
        Flow.Subscription readSubscription = readSubscriber.readSubscription;
        if (readSubscription == null) return Utils.EMPTY_BYTEBUFFER;
        ByteBuffer buffer = readSubscriber.buffers.poll();
        if (buffer != null) {
            debug.log(Level.DEBUG, () -> "read: " + buffer.remaining());
            return buffer;
        }
        Throwable error = readSubscriber.errorRef.get();
        if (error != null) error = Utils.getIOException(error);
        if (error instanceof EOFException) {
            debug.log(Level.DEBUG, "read: EOFException");
            shutdownInput();
            return null;
        }
        if (error != null) {
            debug.log(Level.DEBUG, "read: " + error);
            if (closed.get()) {
                return null;
            }
            shutdownInput();
            throw Utils.getIOException(error);
        }
        if (readSubscriber.completed) {
            debug.log(Level.DEBUG, "read: EOF");
            shutdownInput();
            return null;
        }
        if (inputClosed.get()) {
            debug.log(Level.DEBUG, "read: CLOSED");
            throw new IOException("closed output");
        }
        debug.log(Level.DEBUG, "read: nothing to read");
        return Utils.EMPTY_BYTEBUFFER;
    }

    /*
     * Writes a sequence of bytes to this channel from a subsequence of the
     * given buffers.
     */
    public long write(ByteBuffer[] srcs, int offset, int length) throws IOException {
        if (outputClosed.get()) {
            debug.log(Level.DEBUG, "write: CLOSED");
            throw new IOException("closed output");
        }
        WriteSubscription writeSubscription =  writePublisher.writeSubscription;
        if (writeSubscription == null) {
            debug.log(Level.DEBUG, "write: unsubscribed: 0");
            return 0;
        }
        if (writeSubscription.cancelled) {
            debug.log(Level.DEBUG, "write: CANCELLED");
            shutdownOutput();
            throw new IOException("closed output");
        }
        if (writeSubscription.demand.tryDecrement()) {
            List<ByteBuffer> buffers = copy(srcs, offset, length);
            long res = Utils.remaining(buffers);
            debug.log(Level.DEBUG, "write: writing %d", res);
            writeSubscription.subscriber.onNext(buffers);
            return res;
        } else {
            debug.log(Level.DEBUG, "write: no demand: 0");
            return 0;
        }
    }

    /**
     * Shutdown the connection for reading without closing the channel.
     *
     * <p> Once shutdown for reading then further reads on the channel will
     * return {@code null}, the end-of-stream indication. If the input side of
     * the connection is already shutdown then invoking this method has no
     * effect.
     *
     * @throws ClosedChannelException
     *         If this channel is closed
     * @throws IOException
     *         If some other I/O error occurs
     */
    public void shutdownInput() {
        if (inputClosed.compareAndSet(false, true)) {
            debug.log(Level.DEBUG, "shutdownInput");
            // TransportImpl will eventually call RawChannel::close.
            // We must not call it here as this would close the socket
            // and can cause an exception to back fire before
            // TransportImpl and WebSocketImpl have updated their state.
        }
    }

    /**
     * Shutdown the connection for writing without closing the channel.
     *
     * <p> Once shutdown for writing then further attempts to write to the
     * channel will throw {@link ClosedChannelException}. If the output side of
     * the connection is already shutdown then invoking this method has no
     * effect.
     *
     * @throws ClosedChannelException
     *         If this channel is closed
     * @throws IOException
     *         If some other I/O error occurs
     */
    public void shutdownOutput() {
        if (outputClosed.compareAndSet(false, true)) {
            debug.log(Level.DEBUG, "shutdownOutput");
            // TransportImpl will eventually call RawChannel::close.
            // We must not call it here as this would close the socket
            // and can cause an exception to back fire before
            // TransportImpl and WebSocketImpl have updated their state.
        }
    }

    /**
     * Closes this channel.
     *
     * @throws IOException
     *         If an I/O error occurs
     */
    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            debug.log(Level.DEBUG, "close");
            connection.client().webSocketClose();
            connection.close();
        }
    }

    private static List<ByteBuffer> copy(ByteBuffer[] src, int offset, int len) {
        int count = Math.min(len, src.length - offset);
        if (count <= 0) return Utils.EMPTY_BB_LIST;
        if (count == 1) return List.of(Utils.copy(src[offset]));
        if (count == 2) return List.of(Utils.copy(src[offset]), Utils.copy(src[offset+1]));
        List<ByteBuffer> list = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            list.add(Utils.copy(src[offset + i]));
        }
        return list;
    }
}
