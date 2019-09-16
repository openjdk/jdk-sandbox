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

package jdk.jfr.internal.consumer;

import java.io.IOException;
import java.nio.file.Path;
import java.security.AccessControlContext;
import java.time.Instant;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Objects;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.internal.Utils;
import jdk.jfr.internal.consumer.ChunkParser.ParserConfiguration;

/**
 * Implementation of an {@code EventStream}} that operates against a directory
 * with chunk files.
 *
 */
public final class EventDirectoryStream extends AbstractEventStream {

    private final static Comparator<? super RecordedEvent> EVENT_COMPARATOR = JdkJfrConsumer.instance().eventComparator();

    private final RepositoryFiles repositoryFiles;
    private final boolean active;
    private final FileAccess fileAccess;

    private ChunkParser chunkParser;
    private long chunkStartNanos;
    private RecordedEvent[] sortedList;

    public EventDirectoryStream(AccessControlContext acc, Path p, FileAccess fileAccess, boolean active) throws IOException {
        super(acc, active);
        this.fileAccess = Objects.requireNonNull(fileAccess);
        this.active = active;
        this.repositoryFiles = new RepositoryFiles(fileAccess, p);
    }

    @Override
    public void close() {
        setClosed(true);
        dispatcher().runCloseActions();
        repositoryFiles.close();
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
    protected void process() throws IOException {
        Dispatcher disp = dispatcher();

        Path path;
        boolean validStartTime = active || disp.startTime != null;
        if (validStartTime) {
            path = repositoryFiles.firstPath(disp.startNanos);
        } else {
            path = repositoryFiles.lastPath();
        }
        if (path == null) { // closed
            return;
        }
        chunkStartNanos = repositoryFiles.getTimestamp(path);
        try (RecordingInput input = new RecordingInput(path.toFile(), fileAccess)) {
            chunkParser = new ChunkParser(input, disp.parserConfiguration);
            long segmentStart = chunkParser.getStartNanos() + chunkParser.getChunkDuration();
            long filterStart = validStartTime ? disp.startNanos : segmentStart;
            long filterEnd = disp.endTime != null ? disp.endNanos: Long.MAX_VALUE;

            while (!isClosed()) {
                boolean awaitnewEvent = false;
                while (!isClosed() && !chunkParser.isChunkFinished()) {
                    disp = dispatcher();
                    ParserConfiguration pc = disp.parserConfiguration;
                    pc.filterStart = filterStart;
                    pc.filterEnd = filterEnd;
                    chunkParser.updateConfiguration(pc, true);
                    chunkParser.setFlushOperation(getFlushOperation());
                    if (pc.ordered) {
                        awaitnewEvent = processOrdered(disp, awaitnewEvent);
                    } else {
                        awaitnewEvent = processUnordered(disp, awaitnewEvent);
                    }
                    if (chunkParser.getStartNanos() + chunkParser.getChunkDuration() > filterEnd) {
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
                // TODO: Optimization. No need filter when we reach new chunk
                // Could set start = 0;
            }
        }
    }

    private boolean processOrdered(Dispatcher c, boolean awaitNewEvents) throws IOException {
        if (sortedList == null) {
            sortedList = new RecordedEvent[100_000];
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
            Arrays.sort(sortedList, 0, index, EVENT_COMPARATOR);
        }
        for (int i = 0; i < index; i++) {
            c.dispatch(sortedList[i]);
        }
        return awaitNewEvents;
    }

    private boolean processUnordered(Dispatcher c, boolean awaitNewEvents) throws IOException {
        while (true) {
            RecordedEvent e = chunkParser.readStreamingEvent(awaitNewEvents);
            if (e == null) {
                return true;
            } else {
                c.dispatch(e);
            }
        }
    }
}
