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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.Repository;
import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.EventConsumer;
import jdk.jfr.internal.consumer.RecordingInput;

final class EventDirectoryStream implements EventStream {

    private static final class RepositoryFiles {
        private final Path repostory;
        private final SortedMap<Long, Path> pathSet = new TreeMap<>();
        private final Map<Path, Long> pathLookup = new HashMap<>();
        private volatile boolean closed;

        public RepositoryFiles(Path repostory) {
            this.repostory = repostory;
        }

        long getTimestamp(Path p) {
            return  pathLookup.get(p);
        }

        Path nextPath(long startTimeNanos) {
            while (!closed) {
                SortedMap<Long, Path> after = pathSet.tailMap(startTimeNanos);
                if (!after.isEmpty()) {
                    Path path = after.get(after.firstKey());
                    Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.TRACE, "Return path " + path + " for start time nanos " + startTimeNanos);
                    return path;
                }
                try {
                    if (updatePaths(repostory)) {
                        continue;
                    }
                } catch (IOException e) {
                    Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "IOException during repository file scan " + e.getMessage());
                    // This can happen if a chunk is being removed
                    // between the file was discovered and an instance
                    // of an EventSet was constructed. Just ignore,
                    // and retry later.
                }
                try {
                    synchronized (pathSet) {
                        pathSet.wait(1000);
                    }
                } catch (InterruptedException e) {
                    // ignore
                }
            }
            return null;
        }

        private boolean updatePaths(Path repo) throws IOException {
            if (repo == null) {
                repo = Repository.getRepository().getRepositoryPath().toPath();
            }
            boolean foundNew = false;
            List<Path> added = new ArrayList<>();
            Set<Path> current = new HashSet<>();
            if (!Files.exists(repo)) {
                // Repository removed, probably due to shutdown
                return true;
            }
            try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(repo, "*.jfr")) {
                for (Path p : dirStream) {
                    if (!pathLookup.containsKey(p)) {
                        added.add(p);
                        Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "New file found: " + p.toAbsolutePath());
                    }
                    current.add(p);
                }
            }
            List<Path> removed = new ArrayList<>();
            for (Path p : pathLookup.keySet()) {
                if (!current.contains(p)) {
                    removed.add(p);
                }
            }

            for (Path remove : removed) {
                Long time = pathLookup.get(remove);
                pathSet.remove(time);
                pathLookup.remove(remove);
            }
            Collections.sort(added, (p1, p2) -> p1.compareTo(p2));
            for (Path p : added) {
                // Only add files that have a complete header
                // as the JVM may be in progress writing the file
                long size = Files.size(p);
                if (size >= ChunkHeader.HEADER_SIZE) {
                    long startNanos = readStartTime(p);
                    pathSet.put(startNanos, p);
                    pathLookup.put(p, startNanos);
                    foundNew = true;
                }
            }
            return foundNew;
        }

        private long readStartTime(Path p) throws IOException {
            try (RecordingInput in = new RecordingInput(p.toFile(), 100)) {
                ChunkHeader c = new ChunkHeader(in);
                return c.getStartNanos();
            }
        }

        public void close() {
            synchronized (pathSet) {
                this.closed = true;
                pathSet.notify();
            }
        }
    }

    static final class ParserConsumer extends EventConsumer {

        private static final Comparator<? super RecordedEvent> END_TIME = (e1, e2) -> Long.compare(e1.endTime, e2.endTime);
        private static final int DEFAULT_ARRAY_SIZE = 10_000;
        private final RepositoryFiles repositoryFiles;
        private ChunkParser chunkParser;
        private boolean reuse = true;
        private RecordedEvent[] sortedList;
        private boolean ordered = true;

        public ParserConsumer(AccessControlContext acc, Path p) throws IOException {
            super(acc);
            repositoryFiles = new RepositoryFiles(p);
        }

        @Override
        public void process() throws IOException {
            Path path = repositoryFiles.nextPath(startNanos);
            startNanos = repositoryFiles.getTimestamp(path) + 1;
            try (RecordingInput input = new RecordingInput(path.toFile())) {
                while (!isClosed()) {
                    // chunkParser = chunkParser.nextChunkParser();
                    chunkParser = new ChunkParser(input, this.reuse);
                    boolean awaitnewEvent = false;
                    while (!isClosed() && !chunkParser.isChunkFinished()) {
                        chunkParser.setReuse(this.reuse);
                        chunkParser.setOrdered(this.ordered);
                        chunkParser.resetEventCache();
                        chunkParser.updateEventParsers();
                        if (ordered) {
                            awaitnewEvent = processOrdered(awaitnewEvent);
                        } else {
                            awaitnewEvent = processUnordered(awaitnewEvent);
                        }
                        runFlushActions();
                    }

                    path = repositoryFiles.nextPath(startNanos);
                    startNanos = repositoryFiles.getTimestamp(path) + 1;
                    input.setFile(path);
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

        public void setReuse(boolean reuse) {
            this.reuse = reuse;
        }

        public void setOrdered(boolean ordered) {
            this.ordered = ordered;
        }

        @Override
        public void close() {
            repositoryFiles.close();
        }
    }

    static final class SharedParserConsumer extends EventConsumer {
        private EventSetLocation location;
        private EventSet eventSet;
        private int eventSetIndex;
        private int eventArrayIndex;
        private RecordedEvent[] currentEventArray = new RecordedEvent[0];

        public SharedParserConsumer(AccessControlContext acc) throws IOException {
            super(acc);
        }

        public void process() throws IOException {
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

        public void setReuse(boolean reuse) {
            // ignore hint
        }
    }

    private final EventConsumer eventConsumer;

    public EventDirectoryStream(AccessControlContext acc, Path p) throws IOException {
        eventConsumer = new ParserConsumer(acc, p);
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
}
