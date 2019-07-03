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
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.jfr.internal.consumer.RecordingInput;
import jdk.jfr.internal.consumer.RepositoryFiles;

/**
 * Implementation of an {@code EventStream}} that operates against a directory
 * with chunk files.
 *
 */
final class EventDirectoryStream implements EventStream {

    static final class DirectoryConsumer extends EventConsumer {

        private static final Comparator<? super RecordedEvent> END_TIME = (e1, e2) -> Long.compare(e1.endTime, e2.endTime);
        private static final int DEFAULT_ARRAY_SIZE = 10_000;
        private final RepositoryFiles repositoryFiles;
        private ChunkParser chunkParser;
        private RecordedEvent[] sortedList;
        protected long chunkStartNanos;

        public DirectoryConsumer(AccessControlContext acc, Path p) throws IOException {
            super(acc);
            repositoryFiles = new RepositoryFiles(p);
        }

        @Override
        public void process() throws IOException {
            chunkStartNanos = startNanos;
            Path path;
            if (startTime == EventConsumer.NEXT_EVENT) {
                // TODO: Need to skip forward to the next event
                // For now, use the last chunk.
                path = repositoryFiles.lastPath();
            } else {
                path = repositoryFiles.nextPath(chunkStartNanos);
            }
            chunkStartNanos = repositoryFiles.getTimestamp(path) + 1;
            try (RecordingInput input = new RecordingInput(path.toFile())) {
                chunkParser = new ChunkParser(input, this.reuse);
                while (!isClosed()) {
                    boolean awaitnewEvent = false;
                    while (!isClosed() && !chunkParser.isChunkFinished()) {
                        chunkParser.setReuse(this.reuse);
                        chunkParser.setOrdered(this.ordered);
                        chunkParser.resetEventCache();
                        chunkParser.setParserFilter(this.eventFilter);
                        chunkParser.updateEventParsers();
                        if (ordered) {
                            awaitnewEvent = processOrdered(awaitnewEvent);
                        } else {
                            awaitnewEvent = processUnordered(awaitnewEvent);
                        }
                        runFlushActions();
                    }

                    path = repositoryFiles.nextPath(chunkStartNanos);
                    if (path == null) {
                        return; // stream closed
                    }
                    chunkStartNanos = repositoryFiles.getTimestamp(path) + 1;
                    input.setFile(path);
                    chunkParser = chunkParser.newChunkParser();
                }
            }
        }

        private boolean processOrdered(boolean awaitNewEvents) throws IOException {
            if (sortedList == null) {
                sortedList = new RecordedEvent[DEFAULT_ARRAY_SIZE];
            }
            int index = 0;
            while (true) {
                RecordedEvent e = chunkParser.readStreamingEvent(awaitNewEvents);
                if (e == null) {
                    // wait for new event with next call to
                    // readStreamingEvent()
                    awaitNewEvents = true;
                    break;
                }
                awaitNewEvents = false;
                if (index == sortedList.length) {
                    sortedList = Arrays.copyOf(sortedList, sortedList.length * 2);
                }
                sortedList[index++] = e;
            }

            // no events found
            if (index == 0 && chunkParser.isChunkFinished()) {
                return awaitNewEvents;
            }
            // at least 2 events, sort them
            if (index > 1) {
                Arrays.sort(sortedList, 0, index, END_TIME);
            }
            for (int i = 0; i < index; i++) {
                dispatch(sortedList[i]);
            }
            return awaitNewEvents;
        }

        private boolean processUnordered(boolean awaitNewEvents) throws IOException {
            while (true) {
                RecordedEvent e = chunkParser.readStreamingEvent(awaitNewEvents);
                if (e == null) {
                    awaitNewEvents = true;
                    break;
                } else {
                    dispatch(e);
                }
            }
            return awaitNewEvents;
        }

        @Override
        public void close() {
            repositoryFiles.close();
        }
    }

    private final EventConsumer eventConsumer;

    public EventDirectoryStream(AccessControlContext acc, Path p, Instant startTime) throws IOException {
        eventConsumer = new DirectoryConsumer(acc, p);
        eventConsumer.startTime = startTime;
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

    @Override
    public void setReuse(boolean reuse) {
        eventConsumer.setReuse(reuse);
    }

    @Override
    public void setOrdered(boolean ordered) {
        eventConsumer.setOrdered(ordered);
    }

    @Override
    public void setStartTime(Instant startTime) {
        eventConsumer.setStartTime(startTime);
    }
}
