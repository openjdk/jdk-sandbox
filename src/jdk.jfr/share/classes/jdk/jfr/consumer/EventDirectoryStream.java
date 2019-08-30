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

import jdk.jfr.internal.Utils;
import jdk.jfr.internal.consumer.FileAccess;
import jdk.jfr.internal.consumer.RecordingInput;
import jdk.jfr.internal.consumer.RepositoryFiles;

/**
 * Implementation of an {@code EventStream}} that operates against a directory
 * with chunk files.
 *
 */
class EventDirectoryStream implements EventStream {

    static final class DirectoryStream extends AbstractEventStream {

        private static final Comparator<? super RecordedEvent> END_TIME = (e1, e2) -> Long.compare(e1.endTimeTicks, e2.endTimeTicks);
        private static final int DEFAULT_ARRAY_SIZE = 10_000;

        private final RepositoryFiles repositoryFiles;
        private final boolean active;
        private final FileAccess fileAccess;
        private ChunkParser chunkParser;
        private RecordedEvent[] sortedList;
        protected long chunkStartNanos;

        public DirectoryStream(AccessControlContext acc, Path p, FileAccess fileAccess, boolean active) throws IOException {
            super(acc, active);
            this.fileAccess = fileAccess;
            this.active = active;
            repositoryFiles = new RepositoryFiles(fileAccess, p);
        }

        @Override
        public void process() throws Exception {
            final StreamConfiguration c1 = configuration;
            Path path;
            boolean validStartTime = active || c1.getStartTime() != null;
            if (validStartTime) {
                path = repositoryFiles.firstPath(c1.getStartNanos());
            } else {
                path = repositoryFiles.lastPath();
            }
            if (path == null) { // closed
                return;
            }
            chunkStartNanos = repositoryFiles.getTimestamp(path);
            try (RecordingInput input = new RecordingInput(path.toFile(), fileAccess)) {
                chunkParser = new ChunkParser(input, c1.getReuse());
                long segmentStart = chunkParser.getStartNanos() + chunkParser.getChunkDuration();
                long start = validStartTime ? c1.getStartNanos() : segmentStart;
                long end = c1.getEndTime() != null ? c1.getEndNanos() : Long.MAX_VALUE;
                while (!isClosed()) {
                    boolean awaitnewEvent = false;
                    while (!isClosed() && !chunkParser.isChunkFinished()) {
                        final StreamConfiguration c2 = configuration;
                        boolean ordered = c2.getOrdered();
                        chunkParser.setFlushOperation(flushOperation);
                        chunkParser.setReuse(c2.getReuse());
                        chunkParser.setOrdered(ordered);
                        chunkParser.setFirstNanos(start);
                        chunkParser.setLastNanos(end);
                        chunkParser.resetEventCache();
                        chunkParser.setParserFilter(c2.getFilter());
                        chunkParser.updateEventParsers();
                        clearLastDispatch();
                        if (ordered) {
                            awaitnewEvent = processOrdered(awaitnewEvent);
                        } else {
                            awaitnewEvent = processUnordered(awaitnewEvent);
                        }
                        if (chunkParser.getStartNanos() + chunkParser.getChunkDuration() > end) {
                            close();
                            return;
                        }
                    }


                    if (isClosed()) {
                        return;
                    }
                    long durationNanos = chunkParser.getChunkDuration();
                    path = repositoryFiles.nextPath(chunkStartNanos + durationNanos);
                    if (path == null) {
                        return; // stream closed
                    }
                    chunkStartNanos = repositoryFiles.getTimestamp(path);
                    input.setFile(path);
                    chunkParser = chunkParser.newChunkParser();
                    // No need filter when we reach new chunk
                    // start = 0;
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
                    return true;
                } else {
                    dispatch(e);
                }
            }
        }

        @Override
        public void close() {
            setClosed(true);
            repositoryFiles.close();
        }
    }

    private final AbstractEventStream eventStream;

    public EventDirectoryStream(AccessControlContext acc, Path p, FileAccess access, boolean active) throws IOException {
        eventStream = new DirectoryStream(acc, p, access, active);
    }

    @Override
    public void close() {
        eventStream.close();
    }

    @Override
    public void onFlush(Runnable action) {
        Objects.requireNonNull(action);
        eventStream.onFlush(action);
    }

    @Override
    public void start() {
        start(Utils.timeToNanos(Instant.now()));
    }

    @Override
    public void startAsync() {
        startAsync(Utils.timeToNanos(Instant.now()));
    }

    @Override
    public void onEvent(Consumer<RecordedEvent> action) {
        Objects.requireNonNull(action);
        eventStream.onEvent(action);
    }

    @Override
    public void onEvent(String eventName, Consumer<RecordedEvent> action) {
        Objects.requireNonNull(eventName);
        Objects.requireNonNull(action);
        eventStream.onEvent(eventName, action);
    }

    @Override
    public void onClose(Runnable action) {
        Objects.requireNonNull(action);
        eventStream.addCloseAction(action);
    }

    @Override
    public boolean remove(Object action) {
        Objects.requireNonNull(action);
        return eventStream.remove(action);
    }

    @Override
    public void awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
        eventStream.awaitTermination(timeout);
    }

    @Override
    public void awaitTermination() {
        eventStream.awaitTermination(Duration.ofMillis(0));
    }

    @Override
    public void setReuse(boolean reuse) {
        eventStream.setReuse(reuse);
    }

    @Override
    public void setOrdered(boolean ordered) {
        eventStream.setOrdered(ordered);
    }

    @Override
    public void setStartTime(Instant startTime) {
        eventStream.setStartTime(startTime);
    }

    @Override
    public void setEndTime(Instant endTime) {
        eventStream.setEndTime(endTime);
    }


    public void start(long startNanos) {
        eventStream.start(startNanos);
    }

    public void startAsync(long startNanos) {
        eventStream.startAsync(startNanos);
    }

    @Override
    public void onError(Consumer<Throwable> action) {
        // TODO Auto-generated method stub

    }


}
