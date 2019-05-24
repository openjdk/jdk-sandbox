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

import jdk.jfr.internal.consumer.EventConsumer;

final class EventDirectoryStream implements EventStream {

    private static class EventRunner extends EventConsumer {
        private EventSetLocation location;
        private EventSet eventSet;
        private int eventSetIndex;
        private int eventArrayIndex;
        private RecordedEvent[] currentEventArray = new RecordedEvent[0];

        public EventRunner(AccessControlContext acc) throws IOException {
            super(acc);
        }

        public void process() throws Exception, IOException {
            this.location = EventSetLocation.current();
            this.eventSet = location.acquire(startNanos, null); // use timestamp
                                                                // from
            if (eventSet == null) {
                return;
            }
            while (!isClosed()) {
                processSegment();
                runFlushActions();
                do {
                    if (isClosed()) {
                        return;
                    }
                    currentEventArray = eventSet.readEvents(eventSetIndex);
                    if (currentEventArray == EventSet.END_OF_SET) {
                        eventSet = eventSet.next(eventFilter);
                        if (eventSet == null || isClosed()) {
                            return;
                        }
                        eventSetIndex = 0;
                        continue;
                    }
                    if (currentEventArray == null) {
                        return; // no more events
                    }
                    eventSetIndex++;
                } while (currentEventArray.length == 0);
                eventArrayIndex = 0;
            }
        }

        private void processSegment() {
            while (eventArrayIndex < currentEventArray.length) {
                RecordedEvent e = currentEventArray[eventArrayIndex++];
                if (e == null) {
                    return;
                }
                dispatch(e);
            }
        }

        public void close() {
            setClosed(true);
            // TODO: Data races here, must fix
            synchronized (this) {
                if (eventSet != null) {
                    eventSet.release(null);
                }
                if (location != null) {
                    location.release();
                }
            }
            runCloseActions();
        }
    }

    private final EventRunner eventConsumer;

    public EventDirectoryStream(AccessControlContext acc) throws IOException {
        eventConsumer = new EventRunner(acc);
    }

    public void close() {
        eventConsumer.close();
    }

    public void onFlush(Runnable action) {
        Objects.requireNonNull(action);
        eventConsumer.onFlush(action);
    }

    void start(long startNanos) {
        eventConsumer.start(startNanos);
    }

    @Override
    public void start() {
        start(Instant.now().toEpochMilli() * 1000 * 1000L);
    }

    @Override
    public void startAsync() {
        startAsync(Instant.now().toEpochMilli() * 1000 * 1000L);
    }

    void startAsync(long startNanos) {
        eventConsumer.startAsync(startNanos);
    }

    @Override
    public void onEvent(Consumer<RecordedEvent> action) {
        Objects.requireNonNull(action);
        eventConsumer.onEvent(action);
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        Objects.requireNonNull(eventName);
        Objects.requireNonNull(action);
        eventConsumer.onEvent(eventName, action);
    }

    @Override
    public void onClose(Runnable action) {
        Objects.requireNonNull(action);
        eventConsumer.addCloseAction(action);
    }

    @Override
    public boolean remove(Object action) {
        Objects.requireNonNull(action);
        return eventConsumer.remove(action);
    }

    @Override
    public void awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
        eventConsumer.awaitTermination(timeout);
    }

    @Override
    public void awaitTermination() {
        eventConsumer.awaitTermination(Duration.ofMillis(0));
    }
}
