/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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
import jdk.internal.net.http.common.MinimalFuture;
import jdk.internal.net.http.common.SequentialScheduler;
import jdk.internal.net.http.common.SequentialScheduler.CompleteRestartableTask;
import jdk.internal.net.http.common.Utils;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.channels.SelectionKey;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BiConsumer;

import static jdk.internal.net.http.websocket.TransportImpl.ChannelState.AVAILABLE;
import static jdk.internal.net.http.websocket.TransportImpl.ChannelState.UNREGISTERED;
import static jdk.internal.net.http.websocket.TransportImpl.ChannelState.WAITING;

public class TransportImpl implements Transport {

    // -- Debugging infrastructure --

    private final static boolean DEBUG = false;

    /* Used for correlating enters to and exists from a method */
    private final static AtomicLong COUNTER = new AtomicLong();

    private final SequentialScheduler sendScheduler = new SequentialScheduler(new SendTask());

    private final MessageQueue queue = new MessageQueue();
    private final MessageEncoder encoder = new MessageEncoder();
    /* A reusable buffer for writing, initially with no remaining bytes */
    private final ByteBuffer dst = createWriteBuffer().position(0).limit(0);
    /* This array is created once for gathering writes accepted by RawChannel */
    private final ByteBuffer[] dstArray = new ByteBuffer[]{dst};
    private final MessageStreamConsumer messageConsumer;
    private final MessageDecoder decoder;
    private final Frame.Reader reader = new Frame.Reader();

    private final Demand demand = new Demand();
    private final SequentialScheduler receiveScheduler;
    private final RawChannel channel;
    private final Object closeLock = new Object();
    private final RawChannel.RawEvent writeEvent = new WriteEvent();
    private final RawChannel.RawEvent readEvent = new ReadEvent();
    private volatile ChannelState writeState = UNREGISTERED;
    private ByteBuffer data;
    private volatile ChannelState readState = UNREGISTERED;
    private boolean inputClosed;
    private boolean outputClosed;
    public TransportImpl(MessageStreamConsumer consumer, RawChannel channel) {
        this.messageConsumer = consumer;
        this.channel = channel;
        this.decoder = new MessageDecoder(this.messageConsumer);
        this.data = channel.initialByteBuffer();
        // To ensure the initial non-final `data` will be visible
        // (happens-before) when `readEvent.handle()` invokes `receiveScheduler`
        // the following assignment is done last:
        receiveScheduler = new SequentialScheduler(new ReceiveTask());
    }

    private ByteBuffer createWriteBuffer() {
        String name = "jdk.httpclient.websocket.writeBufferSize";
        int capacity = Utils.getIntegerNetProperty(name, 16384);
        if (DEBUG) {
            System.out.printf("[Transport] write buffer capacity %s", capacity);
        }
        // TODO (optimization?): allocateDirect if SSL?
        return ByteBuffer.allocate(capacity);
    }

    private boolean write() throws IOException {
        if (DEBUG) {
            System.out.printf("[Transport] writing to the channel%n");
        }
        long count = channel.write(dstArray, 0, dstArray.length);
        if (DEBUG) {
            System.out.printf("[Transport] %s bytes written%n", count);
        }
        for (ByteBuffer b : dstArray) {
            if (b.hasRemaining()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public <T> CompletableFuture<T> sendText(CharSequence message,
                                             boolean isLast,
                                             T attachment,
                                             BiConsumer<? super T, ? super Throwable> action) {
        long id;
        if (DEBUG) {
            id = COUNTER.incrementAndGet();
            System.out.printf("[Transport] %s: sendText message.length()=%s, last=%s%n",
                              id, message.length(), isLast);
        }
        // TODO (optimization?):
        // These sendXXX methods might be a good place to decide whether or not
        // we can write straight ahead, possibly returning null instead of
        // creating a CompletableFuture

        // Even if the text is already CharBuffer, the client will not be happy
        // if they discover the position is changing. So, no instanceof
        // cheating, wrap always.
        CharBuffer text = CharBuffer.wrap(message);
        MinimalFuture<T> f = new MinimalFuture<>();
        try {
            queue.addText(text, isLast, attachment, action, f);
            sendScheduler.runOrSchedule();
        } catch (IOException e) {
            f.completeExceptionally(e);
        }
        if (DEBUG) {
            System.out.printf("[Transport] %s: sendText returned %s%n", id, f);
        }
        return f;
    }

    @Override
    public <T> CompletableFuture<T> sendBinary(ByteBuffer message,
                                               boolean isLast,
                                               T attachment,
                                               BiConsumer<? super T, ? super Throwable> action) {
        long id;
        if (DEBUG) {
            id = COUNTER.incrementAndGet();
            System.out.printf("[Transport] %s: sendBinary message.remaining()=%s, last=%s%n",
                              id, message.remaining(), isLast);
        }
        MinimalFuture<T> f = new MinimalFuture<>();
        try {
            queue.addBinary(message, isLast, attachment, action, f);
            sendScheduler.runOrSchedule();
        } catch (IOException e) {
            f.completeExceptionally(e);
        }
        if (DEBUG) {
            System.out.printf("[Transport] %s: sendBinary returned %s%n", id, f);
        }
        return f;
    }

    @Override
    public <T> CompletableFuture<T> sendPing(ByteBuffer message,
                                             T attachment,
                                             BiConsumer<? super T, ? super Throwable> action) {
        long id;
        if (DEBUG) {
            id = COUNTER.incrementAndGet();
            System.out.printf("[Transport] %s: sendPing message.remaining()=%s%n",
                              id, message.remaining());
        }
        MinimalFuture<T> f = new MinimalFuture<>();
        try {
            queue.addPing(message, attachment, action, f);
            sendScheduler.runOrSchedule();
        } catch (IOException e) {
            f.completeExceptionally(e);
        }
        if (DEBUG) {
            System.out.printf("[Transport] %s: sendPing returned %s%n", id, f);
        }
        return f;
    }

    @Override
    public <T> CompletableFuture<T> sendPong(ByteBuffer message,
                                             T attachment,
                                             BiConsumer<? super T, ? super Throwable> action) {
        long id;
        if (DEBUG) {
            id = COUNTER.incrementAndGet();
            System.out.printf("[Transport] %s: sendPong message.remaining()=%s%n",
                              id, message.remaining());
        }
        MinimalFuture<T> f = new MinimalFuture<>();
        try {
            queue.addPong(message, attachment, action, f);
            sendScheduler.runOrSchedule();
        } catch (IOException e) {
            f.completeExceptionally(e);
        }
        if (DEBUG) {
            System.out.printf("[Transport] %s: sendPong returned %s%n", id, f);
        }
        return f;
    }

    @Override
    public <T> CompletableFuture<T> sendClose(int statusCode,
                                              String reason,
                                              T attachment,
                                              BiConsumer<? super T, ? super Throwable> action) {
        long id;
        if (DEBUG) {
            id = COUNTER.incrementAndGet();
            System.out.printf("[Transport] %s: sendClose statusCode=%s, reason.length()=%s%n",
                              id, statusCode, reason.length());
        }
        MinimalFuture<T> f = new MinimalFuture<>();
        try {
            queue.addClose(statusCode, CharBuffer.wrap(reason), attachment, action, f);
            sendScheduler.runOrSchedule();
        } catch (IOException e) {
            f.completeExceptionally(e);
        }
        if (DEBUG) {
            System.out.printf("[Transport] %s: sendClose returned %s%n", id, f);
        }
        return f;
    }

    @Override
    public void request(long n) {
        if (DEBUG) {
            System.out.printf("[Transport] request %s%n", n);
        }
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

    @Override
    public void closeOutput() throws IOException {
        if (DEBUG) {
            System.out.printf("[Transport] closeOutput%n");
        }
        synchronized (closeLock) {
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

    /*
     * Permanently stops reading from the channel and delivering messages
     * regardless of the current demand and data availability.
     */
    @Override
    public void closeInput() throws IOException {
        if (DEBUG) {
            System.out.printf("[Transport] closeInput%n");
        }
        synchronized (closeLock) {
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

    /*  Common states for send and receive tasks */
    enum ChannelState {
        UNREGISTERED,
        AVAILABLE,
        WAITING
    }

    @SuppressWarnings({"rawtypes"})
    private class SendTask extends CompleteRestartableTask {

        private final MessageQueue.QueueCallback<Boolean, IOException>
                encodingCallback = new MessageQueue.QueueCallback<>() {

            @Override
            public <T> Boolean onText(CharBuffer message,
                                      boolean isLast,
                                      T attachment,
                                      BiConsumer<? super T, ? super Throwable> action,
                                      CompletableFuture<? super T> future) throws IOException
            {
                return encoder.encodeText(message, isLast, dst);
            }

            @Override
            public <T> Boolean onBinary(ByteBuffer message,
                                        boolean isLast,
                                        T attachment,
                                        BiConsumer<? super T, ? super Throwable> action,
                                        CompletableFuture<? super T> future) throws IOException
            {
                return encoder.encodeBinary(message, isLast, dst);
            }

            @Override
            public <T> Boolean onPing(ByteBuffer message,
                                      T attachment,
                                      BiConsumer<? super T, ? super Throwable> action,
                                      CompletableFuture<? super T> future) throws IOException
            {
                return encoder.encodePing(message, dst);
            }

            @Override
            public <T> Boolean onPong(ByteBuffer message,
                                      T attachment,
                                      BiConsumer<? super T, ? super Throwable> action,
                                      CompletableFuture<? super T> future) throws IOException
            {
                return encoder.encodePong(message, dst);
            }

            @Override
            public <T> Boolean onClose(int statusCode,
                                       CharBuffer reason,
                                       T attachment,
                                       BiConsumer<? super T, ? super Throwable> action,
                                       CompletableFuture<? super T> future) throws IOException
            {
                return encoder.encodeClose(statusCode, reason, dst);
            }

            @Override
            public Boolean onEmpty() {
                return false;
            }
        };

        /* Whether the task sees the current head message for first time */
        private boolean firstPass = true;
        /* Whether the message has been fully encoded */
        private boolean encoded;

        // -- Current message completion communication fields --

        private Object attachment;
        private BiConsumer action;
        private CompletableFuture future;
        private final MessageQueue.QueueCallback<Boolean, RuntimeException>
                /* If there is a message, loads its completion communication fields */
                loadCallback = new MessageQueue.QueueCallback<Boolean, RuntimeException>() {

            @Override
            public <T> Boolean onText(CharBuffer message,
                                      boolean isLast,
                                      T attachment,
                                      BiConsumer<? super T, ? super Throwable> action,
                                      CompletableFuture<? super T> future)
            {
                SendTask.this.attachment = attachment;
                SendTask.this.action = action;
                SendTask.this.future = future;
                return true;
            }

            @Override
            public <T> Boolean onBinary(ByteBuffer message,
                                        boolean isLast,
                                        T attachment,
                                        BiConsumer<? super T, ? super Throwable> action,
                                        CompletableFuture<? super T> future)
            {
                SendTask.this.attachment = attachment;
                SendTask.this.action = action;
                SendTask.this.future = future;
                return true;
            }

            @Override
            public <T> Boolean onPing(ByteBuffer message,
                                      T attachment,
                                      BiConsumer<? super T, ? super Throwable> action,
                                      CompletableFuture<? super T> future)
            {
                SendTask.this.attachment = attachment;
                SendTask.this.action = action;
                SendTask.this.future = future;
                return true;
            }

            @Override
            public <T> Boolean onPong(ByteBuffer message,
                                      T attachment,
                                      BiConsumer<? super T, ? super Throwable> action,
                                      CompletableFuture<? super T> future)
            {
                SendTask.this.attachment = attachment;
                SendTask.this.action = action;
                SendTask.this.future = future;
                return true;
            }

            @Override
            public <T> Boolean onClose(int statusCode,
                                       CharBuffer reason,
                                       T attachment,
                                       BiConsumer<? super T, ? super Throwable> action,
                                       CompletableFuture<? super T> future)
            {
                SendTask.this.attachment = attachment;
                SendTask.this.action = action;
                SendTask.this.future = future;
                return true;
            }

            @Override
            public Boolean onEmpty() {
                return false;
            }
        };

        @Override
        public void run() {
            // Could have been only called in one of the following cases:
            //   (a) A message has been added to the queue
            //   (b) The channel is ready for writing
            if (DEBUG) {
                System.out.printf("[Transport] begin send task%n");
            }
            while (!queue.isEmpty()) {
                try {
                    if (dst.hasRemaining()) {
                        if (DEBUG) {
                            System.out.printf("[Transport] %s bytes in buffer%n",
                                              dst.remaining());
                        }
                        // The previous part of the binary representation of the message
                        // hasn't been fully written
                        if (!tryCompleteWrite()) {
                            return;
                        }
                    } else if (!encoded) {
                        if (firstPass) {
                            firstPass = false;
                            queue.peek(loadCallback);
                            if (DEBUG) {
                                System.out.printf("[Transport] loaded message%n");
                            }
                        }
                        dst.clear();
                        encoded = queue.peek(encodingCallback);
                        dst.flip();
                        if (!tryCompleteWrite()) {
                            return;
                        }
                    } else {
                        // All done, remove and complete
                        encoder.reset();
                        removeAndComplete(null);
                    }
                } catch (Throwable t) {
                    if (DEBUG) {
                        System.out.printf("[Transport] exception %s; cleanup%n", t);
                    }
                    // buffer cleanup: if there is an exception, the buffer
                    // should appear empty for the next write as there is
                    // nothing to write
                    dst.position(dst.limit());
                    encoder.reset();
                    removeAndComplete(t);
                }
            }
            if (DEBUG) {
                System.out.printf("[Transport] end send task%n");
            }
        }

        private boolean tryCompleteWrite() throws IOException {
            if (DEBUG) {
                System.out.printf("[Transport] begin writing%n");
            }
            boolean finished = false;
            loop:
            while (true) {
                final ChannelState ws = writeState;
                if (DEBUG) {
                    System.out.printf("[Transport] write state: %s%n", ws);
                }
                switch (ws) {
                    case WAITING:
                        break loop;
                    case UNREGISTERED:
                        if (DEBUG) {
                            System.out.printf("[Transport] registering write event%n");
                        }
                        writeState = WAITING;
                        try {
                            channel.registerEvent(writeEvent);
                        } catch (Throwable t) {
                            writeState = UNREGISTERED;
                            throw t;
                        }
                        if (DEBUG) {
                            System.out.printf("[Transport] registered write event%n");
                        }
                        break loop;
                    case AVAILABLE:
                        boolean written = write();
                        if (written) {
                            if (DEBUG) {
                                System.out.printf("[Transport] finished writing to the channel%n");
                            }
                            finished = true;
                            break loop;   // All done
                        } else {
                            writeState = UNREGISTERED;
                            continue loop; //  Effectively "goto UNREGISTERED"
                        }
                    default:
                        throw new InternalError(String.valueOf(ws));
                }
            }
            if (DEBUG) {
                System.out.printf("[Transport] end writing%n");
            }
            return finished;
        }

        @SuppressWarnings("unchecked")
        private void removeAndComplete(Throwable error) {
            if (DEBUG) {
                System.out.printf("[Transport] removeAndComplete error=%s%n", error);
            }
            queue.remove();
            if (error != null) {
                try {
                    action.accept(null, error);
                } finally {
                    future.completeExceptionally(error);
                }
            } else {
                try {
                    action.accept(attachment, null);
                } finally {
                    future.complete(attachment);
                }
            }
            encoded = false;
            firstPass = true;
            attachment = null;
            action = null;
            future = null;
        }
    }

    private class ReceiveTask extends CompleteRestartableTask {

        @Override
        public void run() {
            if (DEBUG) {
                System.out.printf("[Transport] begin receive task%n");
            }
            loop:
            while (!receiveScheduler.isStopped()) {
                if (data.hasRemaining()) {
                    if (DEBUG) {
                        System.out.printf("[Transport] remaining bytes received %s%n",
                                          data.remaining());
                    }
                    if (!demand.isFulfilled()) {
                        try {
                            int oldPos = data.position();
                            reader.readFrame(data, decoder);
                            int newPos = data.position();
                            // Reader always consumes bytes:
                            assert oldPos != newPos : data;
                        } catch (Throwable e) {
                            receiveScheduler.stop();
                            messageConsumer.onError(e);
                        }
                        continue;
                    }
                    break loop;
                }
                final ChannelState rs = readState;
                if (DEBUG) {
                    System.out.printf("[Transport] receive state: %s%n", rs);
                }
                switch (rs) {
                    case WAITING:
                        break loop;
                    case UNREGISTERED:
                        try {
                            readState = WAITING;
                            channel.registerEvent(readEvent);
                        } catch (Throwable e) {
                            receiveScheduler.stop();
                            messageConsumer.onError(e);
                        }
                        break loop;
                    case AVAILABLE:
                        try {
                            data = channel.read();
                        } catch (Throwable e) {
                            receiveScheduler.stop();
                            messageConsumer.onError(e);
                            break loop;
                        }
                        if (data == null) { // EOF
                            receiveScheduler.stop();
                            messageConsumer.onComplete();
                            break loop;
                        } else if (!data.hasRemaining()) {
                            // No data at the moment. Pretty much a "goto",
                            // reusing the existing code path for registration
                            readState = UNREGISTERED;
                        }
                        continue loop;
                    default:
                        throw new InternalError(String.valueOf(rs));
                }
            }
            if (DEBUG) {
                System.out.printf("[Transport] end receive task%n");
            }
        }
    }

    private class WriteEvent implements RawChannel.RawEvent {

        @Override
        public int interestOps() {
            return SelectionKey.OP_WRITE;
        }

        @Override
        public void handle() {
            if (DEBUG) {
                System.out.printf("[Transport] ready to write%n");
            }
            writeState = AVAILABLE;
            sendScheduler.runOrSchedule();
        }
    }

    private class ReadEvent implements RawChannel.RawEvent {

        @Override
        public int interestOps() {
            return SelectionKey.OP_READ;
        }

        @Override
        public void handle() {
            if (DEBUG) {
                System.out.printf("[Transport] ready to read%n");
            }
            readState = AVAILABLE;
            receiveScheduler.runOrSchedule();
        }
    }
}
