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

public final class RepositoryFiles {
    private final Path repostory;
    private final SortedMap<Long, Path> pathSet = new TreeMap<>();
    private final Map<Path, Long> pathLookup = new HashMap<>();
    private volatile boolean closed;

    public RepositoryFiles(Path repostory) {
        this.repostory = repostory;
    }

    public long getTimestamp(Path p) {
        return pathLookup.get(p);
    }

    public Path nextPath(long startTimeNanos) {
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