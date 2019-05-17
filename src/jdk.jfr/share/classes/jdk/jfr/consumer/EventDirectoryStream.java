/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.consumer;

import java.io.IOException;
import java.security.AccessControlContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.jfr.EventType;

final class EventDirectoryStream implements EventStream {

    public final static class EventConsumer {
        final private String eventName;
        final Consumer<RecordedEvent> action;

        EventConsumer(String eventName, Consumer<RecordedEvent> eventConsumer) {
            this.eventName = eventName;
            this.action = eventConsumer;
        }

        public void offer(RecordedEvent event) {
            action.accept(event);
        }

        public boolean accepts(EventType eventType) {
            return (eventName == null || eventType.getName().equals(eventName));
        }
    }

    private final EventRunner eventRunner;
    private Thread thread;
    private boolean started;

    public EventDirectoryStream(AccessControlContext acc) throws IOException {
        eventRunner = new EventRunner(acc);
    }

    public void close() {
        synchronized (eventRunner) {
            eventRunner.close();
        }
    }

    public synchronized void onFlush(Runnable action) {
        Objects.requireNonNull(action);
        synchronized (eventRunner) {
            this.eventRunner.addFlush(action);
        }
    }

    void start(long startNanos) {
        synchronized (eventRunner) {
            if (started) {
                throw new IllegalStateException("Event stream can only be started once");
            }
            started = true;
            eventRunner.setStartNanos(startNanos);
        }
        eventRunner.run();
    }

    @Override
    public void start() {
        start(Instant.now().toEpochMilli() * 1000*1000L);
    }

    @Override
    public void startAsync() {
        startAsync(Instant.now().toEpochMilli() * 1000*1000L);
    }

    void startAsync(long startNanos) {
        synchronized (eventRunner) {
            eventRunner.setStartNanos(startNanos);
            thread = new Thread(eventRunner);
            thread.setDaemon(true);
            thread.start();
        }
    }

    public void addEventConsumer(EventConsumer action) {
        Objects.requireNonNull(action);
        synchronized (eventRunner) {
            eventRunner.add(action);
        }
    }



    @Override
    public void onEvent(Consumer<RecordedEvent> action) {
        Objects.requireNonNull(action);
        synchronized (eventRunner) {
            eventRunner.add(new EventConsumer(null, action));
        }
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        Objects.requireNonNull(eventName);
        Objects.requireNonNull(action);
        synchronized (eventRunner) {
            eventRunner.add(new EventConsumer(eventName, action));
        }
    }

    @Override
    public void onClose(Runnable action) {
        Objects.requireNonNull(action);
        synchronized (eventRunner) {
            eventRunner.addCloseAction(action);
        }
    }

    @Override
    public boolean remove(Object action) {
        Objects.requireNonNull(action);
        synchronized (eventRunner) {
            return eventRunner.remove(action);
        }
    }

    @Override
    public void awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
        Thread t = null;
        synchronized (eventRunner) {
            t = thread;
        }
        if (t != null && t != Thread.currentThread()) {
            try {
                t.join(timeout.toMillis());
            } catch (InterruptedException e) {
                // ignore
            }
        }
    }

    @Override
    public void awaitTermination() {
        awaitTermination(Duration.ofMillis(0));
    }


}
