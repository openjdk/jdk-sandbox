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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

import jdk.jfr.internal.LogLevel;
import jdk.jfr.internal.LogTag;
import jdk.jfr.internal.Logger;
import jdk.jfr.internal.Repository;
import jdk.jfr.internal.consumer.ChunkHeader;

/**
 * This class corresponds to a disk repository.
 * <p>
 * Main purpose is to act as a cache if multiple {@code EventStream} want to
 * access the same repository. An {@code EventSetLocation} should be released
 * when it is no longer being used.
 *
 */
final class EventSetLocation {
    private static Map<Path, EventSetLocation> locations = new HashMap<>();

    private final SortedMap<Long, EventSet> eventSets = new TreeMap<>();
    private final Map<Path, Long> lastPaths = new HashMap<>();

    final Path path;
    private int count = 0;
    private volatile boolean closed;

    private EventSetLocation(Path path) {
        this.path = path;
    }

    public static EventSetLocation get(Path absolutPath) {
        synchronized (locations) {
            EventSetLocation esl = locations.get(absolutPath);
            if (esl == null) {
                esl = new EventSetLocation(absolutPath);
                locations.put(absolutPath, esl);
            }
            esl.count++;
            return esl;
        }
    }

    public static EventSetLocation current() throws IOException {
        Repository.getRepository().ensureRepository();
        return get(Repository.getRepository().getRepositoryPath().toPath());
    }

    public void release() {
        synchronized (locations) {
            count--;
            if (count == 0) {
                locations.remove(path);
                closed = true;
            }
        }
    }

    public synchronized EventSet acquire(long startTimeNanos, EventSet previousEventSet) {
        synchronized (eventSets) {
            while (!closed) {
                SortedMap<Long, EventSet> after = eventSets.tailMap(startTimeNanos);
                if (!after.isEmpty()) {
                    EventSet es =  after.get(after.firstKey());
                    Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.TRACE, "Acquired " + startTimeNanos + ", got " + es);
                    es.acquire();
                    return es;
                }
                try {
                    updateEventSets(previousEventSet);
                } catch (IOException e) {
                    e.printStackTrace();
                    // This can happen if a chunk is being removed
                    // between the file was discovered and an instance
                    // of an EventSet was constructed. Just ignore,
                    // and retry later.
                }
                try {
                    eventSets.wait(1000);
                } catch (InterruptedException e) {
                    // ignore
                }
            }
        }
        return null;
    }

    private void updateEventSets(EventSet previousEventSet) throws IOException {
        List<Path> added = new ArrayList<>();
        Set<Path> current = new HashSet<>();
        try (DirectoryStream<Path> dirStream = Files.newDirectoryStream(path, "*.jfr")) {
            for (Path p : dirStream) {
                if (!lastPaths.containsKey(p)) {
                    added.add(p);
                    Logger.log(LogTag.JFR_SYSTEM_STREAMING, LogLevel.DEBUG, "New file found: " + p.toAbsolutePath());
                }
                current.add(p);
            }
        }
        List<Path> removed = new ArrayList<>();
        for (Path p : lastPaths.keySet()) {
            if (!current.contains(p)) {
                removed.add(p);
            }
        }

        for (Path remove : removed) {
            Long time = lastPaths.get(remove);
            eventSets.remove(time);
            lastPaths.remove(remove);
        }
        Collections.sort(added, (p1,p2) -> p1.compareTo(p2));
        for (Path p : added) {
            // Only add files that have a complete header
            // as the JVM may be in progress writing the file
            long size = Files.size(p);
            if (size >= ChunkHeader.HEADER_SIZE) {
                EventSet es = new EventSet(this, previousEventSet, p);
                long startTime = es.getStartTimeNanos();
                if (startTime == 0) {
                    String errorMsg = "Chunk header should always contain a valid start time";
                    System.err.println(errorMsg);
                    throw new InternalError(errorMsg);
                }
                EventSet previous = eventSets.get(startTime);
                if (previous != null) {
                    String errorMsg = "Found chunk " + p + " with the same start time " + startTime + " as previous chunk " + previous.getPath();
                    System.err.println(errorMsg);
                    throw new InternalError(errorMsg);
                }
                eventSets.put(startTime, es);
                lastPaths.put(p, startTime);
                previousEventSet = es;
            }
        }
    }
}
