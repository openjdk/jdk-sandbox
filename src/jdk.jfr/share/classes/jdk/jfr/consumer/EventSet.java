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
import java.time.Instant;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import jdk.jfr.internal.consumer.ChunkHeader;
import jdk.jfr.internal.consumer.InternalEventFilter;
import jdk.jfr.internal.consumer.RecordingInput;

/**
 * Cache that represents all discovered events in a chunk.
 *
 */
final class EventSet {

    public static final RecordedEvent[] END_OF_SET = new RecordedEvent[0];
    private static final AtomicInteger idCounter = new AtomicInteger(-1);

    private volatile Object[][] segments = new Object[1000][];
    private volatile boolean closed;
    private final long startTimeNanos;
    private final EventSetLocation location;
    private final Path path;
    private final int id;

    // Guarded by lock
    private boolean awaitNewEvents;
    private RecordingInput input;
    private ChunkParser chunkParser;
    private int referenceCount;
    private final ReentrantLock lock = new ReentrantLock();
    private final Set<InternalEventFilter> filters = new HashSet<>();
    private InternalEventFilter globalFilter = InternalEventFilter.ACCEPT_ALL;
    private boolean dirtyFilter = true;

    public void release(InternalEventFilter eventFilter) {
        try {
            lock.lock();
            filters.remove(eventFilter);
            updateGlobalFilter();
            referenceCount--;
            if (referenceCount == 0) {
                closed = true;
                if (input != null) {
                    try {
                        input.close();
                    } catch (IOException e) {
                        // TODO: Flie locked by other process?
                    }
                    chunkParser = null;
                    input = null;
                }
            }
        } finally {
           lock.unlock();
        }
    }

    public EventSet(EventSetLocation location, EventSet previousEventSet, Path p) throws IOException {
        this.location = location;
        this.path = p;
        this.startTimeNanos = readStartTime(p);
        this.id = idCounter.incrementAndGet();
    }

    private long readStartTime(Path p) throws IOException {
        try (RecordingInput in = new RecordingInput(p.toFile(), 100)) {
            ChunkHeader c = new ChunkHeader(in);
            return c.getStartNanos();
        }
    }

    Path getPath() {
        return path;
    }

    // TODO: Use binary search, must use lock
    public int findIndex(Instant timestamp) {
        int index = 0;
        for (int i = 0; i < segments.length; i++) {
            RecordedEvent[] events = (RecordedEvent[]) segments[i];
            if (events == null || events.length == 0) {
                return Math.max(index - 1, 0);
            }
            RecordedEvent e = events[0]; // May not be sorted.
            if (timestamp.isAfter(e.getEndTime())) {
                return Math.max(index - 1, 0);
            }
        }
        return segments.length;
    }

    public void addFilter(InternalEventFilter filter) {
        try {
            lock.lock();
            filters.add(filter);
            updateGlobalFilter();
        } finally {
            lock.unlock();
        }
    }

    // held with lock
    private void updateGlobalFilter() {
        globalFilter = InternalEventFilter.merge(filters);
        dirtyFilter = true;
    }

    public RecordedEvent[] readEvents(int index) throws Exception {
        while (!closed) {

            RecordedEvent[] events = (RecordedEvent[]) segments[index];
            if (events != null) {
                return events;
            }
            if (lock.tryLock(250, TimeUnit.MILLISECONDS)) {
                try {
                    addSegment(index);
                } finally {
                    lock.unlock();
                }
            }
        }
        return null;
    }

    // held with lock
    private void addSegment(int index) throws IOException {
        if (chunkParser == null) {
            chunkParser = new ChunkParser(new RecordingInput(path.toFile()), false);
        }
        if (dirtyFilter) {
            chunkParser.setParserFilter(globalFilter);
        }
        if (segments[index] != null) {
            return;
        }
        if (index == segments.length - 2) {
            segments = Arrays.copyOf(segments, segments.length * 2);
        }
        RecordedEvent[] segment = new RecordedEvent[10];
        int i = 0;
        while (true) {
            RecordedEvent e = chunkParser.readStreamingEvent(awaitNewEvents);
            if (e == null) {
                // wait for new event with next call to readStreamingEvent()
                awaitNewEvents = true;
                break;
            }
            awaitNewEvents = false;
            if (i == segment.length) {
                segment = Arrays.copyOf(segment, segment.length * 2);
            }
            segment[i++] = e;
        }

        // no events found
        if (i == 0) {
            if (chunkParser.isChunkFinished()) {
                segments[index] = END_OF_SET;
                return;
            }
        }
        // at least 2 events, sort them
        if (i > 1) {
            Arrays.sort(segment, 0, i, (e1, e2) -> Long.compare(e1.endTime, e2.endTime));
        }
        segments[index] = segment;
        if (chunkParser.isChunkFinished()) {
            segments[index + 1] = END_OF_SET;
        }
    }

    public long getStartTimeNanos() {
        return startTimeNanos;
    }

    public EventSet next(InternalEventFilter filter) throws IOException {
        EventSet next = location.acquire(startTimeNanos + 1, this);
        if (next == null) {
            // closed
            return null;
        }
        next.addFilter(filter);
        release(filter);
        return next;
    }

    public void acquire() {
        try {
            lock.lock();
            referenceCount++;
        } finally {
            lock.unlock();
        }
    }

    public String toString() {
        return "Chunk:" + id + " (" + path + ")";
    }
}
