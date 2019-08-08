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
import java.security.AccessController;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.function.Consumer;

import jdk.jfr.internal.consumer.FileAccess;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Implementation of an event stream that operates against a recording file.
 *
 */
final class EventFileStream implements EventStream {

    private final static class FileStream extends AbstractEventStream {
        private static final int DEFAULT_ARRAY_SIZE = 100_000;

        private final RecordingInput input;

        private ChunkParser chunkParser;
        private RecordedEvent[] sortedList;

        public FileStream(AccessControlContext acc, Path path) throws IOException {
            super(acc, false);
            this.input = new RecordingInput(path.toFile(), FileAccess.UNPRIVILIGED);
;        }

        @Override
        public void process() throws IOException {
            final StreamConfiguration c1 = configuration;
            long start = 0;
            long end = Long.MAX_VALUE;
            if (c1.getStartTime() != null) {
                start = c1.getStartNanos();
            }
            if (c1.getEndTime() != null) {
                end = c1.getEndNanos();
            }

            chunkParser = new ChunkParser(input, c1.getReuse());
            while (!isClosed()) {
                if (chunkParser.getStartNanos() > end) {
                    close();
                    return;
                }
                StreamConfiguration c2 = configuration;
                boolean ordered = c2.getOrdered();
                chunkParser.setFirstNanos(start);
                chunkParser.setLastNanos(end);
                chunkParser.setReuse(c2.getReuse());
                chunkParser.setOrdered(ordered);
                chunkParser.resetEventCache();
                chunkParser.setParserFilter(c2.getFiler());
                chunkParser.updateEventParsers();
                clearLastDispatch();
                if (ordered) {
                    processOrdered();
                } else {
                    processUnordered();
                }
                runFlushActions();
                if (chunkParser.isLastChunk()) {
                    return;
                }
                chunkParser = chunkParser.nextChunkParser();
            }
        }

        private void processOrdered() throws IOException {
            if (sortedList == null) {
                sortedList = new RecordedEvent[DEFAULT_ARRAY_SIZE];
            }
            RecordedEvent event;
            int index = 0;
            while (true) {
                event = chunkParser.readEvent();
                if (event == null) {
                    Arrays.sort(sortedList, 0, index, END_TIME);
                    for (int i = 0; i < index; i++) {
                        dispatch(sortedList[i]);
                    }
                    return;
                }
                if (index == sortedList.length) {
                    RecordedEvent[] tmp = sortedList;
                    sortedList = new RecordedEvent[2 * tmp.length];
                    System.arraycopy(tmp, 0, sortedList, 0, tmp.length);
                }
                sortedList[index++] = event;
            }
        }

        private void processUnordered() throws IOException {
            RecordedEvent event;
            while (!isClosed()) {
                event = chunkParser.readEvent();
                if (event == null) {
                    return;
                }
                dispatch(event);
            }
        }

        @Override
        public void close() {
            setClosed(true);;
            runCloseActions();
            try {
                input.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    private final FileStream eventStream;

    public EventFileStream(Path path, Instant from, Instant to) throws IOException {
        Objects.requireNonNull(path);
        eventStream = new FileStream(AccessController.getContext(), path);
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
    public void onFlush(Runnable action) {
        Objects.requireNonNull(action);
        eventStream.onFlush(action);
    }

    @Override
    public void onClose(Runnable action) {
        Objects.requireNonNull(action);
        eventStream.addCloseAction(action);
    }

    @Override
    public void close() {
        eventStream.close();
    }

    @Override
    public boolean remove(Object action) {
        Objects.requireNonNull(action);
        return eventStream.remove(action);
    }

    @Override
    public void start() {
        eventStream.start(0);
    }

    @Override
    public void setReuse(boolean reuse) {
        eventStream.setReuse(reuse);
    }

    @Override
    public void startAsync() {
        eventStream.startAsync(0);
    }

    @Override
    public void awaitTermination(Duration timeout) {
        Objects.requireNonNull(timeout);
        eventStream.awaitTermination(timeout);
    }

    @Override
    public void awaitTermination() {
        eventStream.awaitTermination();
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
}
