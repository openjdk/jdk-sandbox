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

package jdk.internal.net.http.websocket;

import java.net.http.WebSocket.MessagePart;
import jdk.internal.net.http.common.Demand;
import jdk.internal.net.http.common.SequentialScheduler;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static jdk.internal.net.http.websocket.TestSupport.fullCopy;

public class MockTransport<T> implements Transport<T> {

    private final long startTime = System.currentTimeMillis();
    private final Queue<Invocation> output = new ConcurrentLinkedQueue<>();
    private final Queue<CompletableFuture<Consumer<MessageStreamConsumer>>>
            input = new ConcurrentLinkedQueue<>();
    private final Supplier<T> supplier;
    private final MessageStreamConsumer consumer;
    private final SequentialScheduler scheduler
            = new SequentialScheduler(new ReceiveTask());
    private final Demand demand = new Demand();

    public MockTransport(Supplier<T> sendResultSupplier,
                         MessageStreamConsumer consumer) {
        this.supplier = sendResultSupplier;
        this.consumer = consumer;
        input.addAll(receive());
    }

    @Override
    public final CompletableFuture<T> sendText(CharSequence message,
                                               boolean isLast) {
        output.add(Invocation.sendText(message, isLast));
        return send(String.format("sendText(%s, %s)", message, isLast),
                    () -> sendText0(message, isLast));
    }

    protected CompletableFuture<T> sendText0(CharSequence message,
                                             boolean isLast) {
        return defaultSend();
    }

    protected CompletableFuture<T> defaultSend() {
        return CompletableFuture.completedFuture(result());
    }

    @Override
    public final CompletableFuture<T> sendBinary(ByteBuffer message,
                                                 boolean isLast) {
        output.add(Invocation.sendBinary(message, isLast));
        return send(String.format("sendBinary(%s, %s)", message, isLast),
                    () -> sendBinary0(message, isLast));
    }

    protected CompletableFuture<T> sendBinary0(ByteBuffer message,
                                               boolean isLast) {
        return defaultSend();
    }

    @Override
    public final CompletableFuture<T> sendPing(ByteBuffer message) {
        output.add(Invocation.sendPing(message));
        return send(String.format("sendPing(%s)", message),
                    () -> sendPing0(message));
    }

    protected CompletableFuture<T> sendPing0(ByteBuffer message) {
        return defaultSend();
    }

    @Override
    public final CompletableFuture<T> sendPong(ByteBuffer message) {
        output.add(Invocation.sendPong(message));
        return send(String.format("sendPong(%s)", message),
                    () -> sendPong0(message));
    }

    protected CompletableFuture<T> sendPong0(ByteBuffer message) {
        return defaultSend();
    }

    @Override
    public final CompletableFuture<T> sendClose(int statusCode, String reason) {
        output.add(Invocation.sendClose(statusCode, reason));
        return send(String.format("sendClose(%s, %s)", statusCode, reason),
                    () -> sendClose0(statusCode, reason));
    }

    protected CompletableFuture<T> sendClose0(int statusCode, String reason) {
        return defaultSend();
    }

    protected Collection<CompletableFuture<Consumer<MessageStreamConsumer>>> receive() {
        return List.of();
    }

    public static Consumer<MessageStreamConsumer> onText(CharSequence data,
                                                         MessagePart part) {
        return c -> c.onText(data.toString(), part);
    }

    public static Consumer<MessageStreamConsumer> onBinary(ByteBuffer data,
                                                           MessagePart part) {
        return c -> c.onBinary(fullCopy(data), part);
    }

    public static Consumer<MessageStreamConsumer> onPing(ByteBuffer data) {
        return c -> c.onPing(fullCopy(data));
    }

    public static Consumer<MessageStreamConsumer> onPong(ByteBuffer data) {
        return c -> c.onPong(fullCopy(data));
    }

    public static Consumer<MessageStreamConsumer> onClose(int statusCode,
                                                          String reason) {
        return c -> c.onClose(statusCode, reason);
    }

    public static Consumer<MessageStreamConsumer> onError(Throwable error) {
        return c -> c.onError(error);
    }

    public static Consumer<MessageStreamConsumer> onComplete() {
        return c -> c.onComplete();
    }

    @Override
    public void request(long n) {
        demand.increase(n);
        scheduler.runOrSchedule();
    }

    @Override
    public void acknowledgeReception() {
        demand.tryDecrement();
    }

    @Override
    public final void closeOutput() throws IOException {
        output.add(Invocation.closeOutput());
        begin("closeOutput()");
        closeOutput0();
        end("closeOutput()");
    }

    protected void closeOutput0() throws IOException {
        defaultClose();
    }

    protected void defaultClose() throws IOException {
    }

    @Override
    public final void closeInput() throws IOException {
        output.add(Invocation.closeInput());
        begin("closeInput()");
        closeInput0();
        end("closeInput()");
    }

    protected void closeInput0() throws IOException {
        defaultClose();
    }

    public abstract static class Invocation {

        static Invocation.SendText sendText(CharSequence message,
                                            boolean isLast) {
            return new SendText(message, isLast);
        }

        static Invocation.SendBinary sendBinary(ByteBuffer message,
                                                boolean isLast) {
            return new SendBinary(message, isLast);
        }

        static Invocation.SendPing sendPing(ByteBuffer message) {
            return new SendPing(message);
        }

        static Invocation.SendPong sendPong(ByteBuffer message) {
            return new SendPong(message);
        }

        static Invocation.SendClose sendClose(int statusCode, String reason) {
            return new SendClose(statusCode, reason);
        }

        public static CloseOutput closeOutput() {
            return new CloseOutput();
        }

        public static CloseInput closeInput() {
            return new CloseInput();
        }

        public static final class SendText extends Invocation {

            final CharSequence message;
            final boolean isLast;

            SendText(CharSequence message, boolean isLast) {
                this.message = message.toString();
                this.isLast = isLast;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                SendText sendText = (SendText) obj;
                return isLast == sendText.isLast &&
                        Objects.equals(message, sendText.message);
            }

            @Override
            public int hashCode() {
                return Objects.hash(isLast, message);
            }
        }

        public static final class SendBinary extends Invocation {

            final ByteBuffer message;
            final boolean isLast;

            SendBinary(ByteBuffer message, boolean isLast) {
                this.message = fullCopy(message);
                this.isLast = isLast;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                SendBinary that = (SendBinary) obj;
                return isLast == that.isLast &&
                        Objects.equals(message, that.message);
            }

            @Override
            public int hashCode() {
                return Objects.hash(message, isLast);
            }
        }

        private static final class SendPing extends Invocation {

            final ByteBuffer message;

            SendPing(ByteBuffer message) {
                this.message = fullCopy(message);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                SendPing sendPing = (SendPing) obj;
                return Objects.equals(message, sendPing.message);
            }

            @Override
            public int hashCode() {
                return Objects.hash(message);
            }
        }

        private static final class SendPong extends Invocation {

            final ByteBuffer message;

            SendPong(ByteBuffer message) {
                this.message = fullCopy(message);
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                SendPing sendPing = (SendPing) obj;
                return Objects.equals(message, sendPing.message);
            }

            @Override
            public int hashCode() {
                return Objects.hash(message);
            }
        }

        private static final class SendClose extends Invocation {

            final int statusCode;
            final String reason;

            SendClose(int statusCode, String reason) {
                this.statusCode = statusCode;
                this.reason = reason;
            }

            @Override
            public boolean equals(Object obj) {
                if (this == obj) return true;
                if (obj == null || getClass() != obj.getClass()) return false;
                SendClose sendClose = (SendClose) obj;
                return statusCode == sendClose.statusCode &&
                        Objects.equals(reason, sendClose.reason);
            }

            @Override
            public int hashCode() {
                return Objects.hash(statusCode, reason);
            }
        }

        private static final class CloseOutput extends Invocation {

            CloseOutput() { }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof CloseOutput;
            }
        }

        private static final class CloseInput extends Invocation {

            CloseInput() { }

            @Override
            public int hashCode() {
                return 0;
            }

            @Override
            public boolean equals(Object obj) {
                return obj instanceof CloseInput;
            }
        }
    }

    public Queue<Invocation> invocations() {
        return new LinkedList<>(output);
    }

    protected final T result() {
        return supplier.get();
    }

    private CompletableFuture<T> send(String name,
                                      Supplier<CompletableFuture<T>> supplier) {
        begin(name);
        CompletableFuture<T> cf = supplier.get().whenComplete((r, e) -> {
            System.out.printf("[%6s ms.] complete %s%n", elapsedTime(), name);
        });
        end(name);
        return cf;
    }

    private void begin(String name) {
        System.out.printf("[%6s ms.] begin %s%n", elapsedTime(), name);
    }

    private void end(String name) {
        System.out.printf("[%6s ms.] end %s%n", elapsedTime(), name);
    }

    private long elapsedTime() {
        return System.currentTimeMillis() - startTime;
    }

    private final class ReceiveTask implements SequentialScheduler.RestartableTask {

        @Override
        public void run(SequentialScheduler.DeferredCompleter taskCompleter) {
            if (!scheduler.isStopped() && !demand.isFulfilled() && !input.isEmpty()) {
                CompletableFuture<Consumer<MessageStreamConsumer>> cf = input.remove();
                if (cf.isDone()) { // Forcing synchronous execution
                    cf.join().accept(consumer);
                    repeat(taskCompleter);
                } else {
                    cf.whenCompleteAsync((r, e) -> {
                        r.accept(consumer);
                        repeat(taskCompleter);
                    });
                }
            } else {
                taskCompleter.complete();
            }
        }

        private void repeat(SequentialScheduler.DeferredCompleter taskCompleter) {
            taskCompleter.complete();
            scheduler.runOrSchedule();
        }
    }
}
