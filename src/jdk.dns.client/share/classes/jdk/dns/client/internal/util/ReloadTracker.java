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

package jdk.dns.client.internal.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.concurrent.atomic.AtomicLong;

public class ReloadTracker {

    public static ReloadTracker newInstance(Path fileToTrack, long refreshTimeoutMillis) {
        Path absolutePath = fileToTrack.toAbsolutePath();
        return new ReloadTracker(absolutePath, refreshTimeoutMillis);
    }

    public static class ReloadStatus {
        private final boolean isReloadNeeded;
        private final long lastModificationTimestamp;
        private final boolean fileExists;

        ReloadStatus(boolean fileExists, boolean isReloadNeeded, long lastModificationTimestamp) {
            this.isReloadNeeded = fileExists && isReloadNeeded;
            this.lastModificationTimestamp = lastModificationTimestamp;
            this.fileExists = fileExists;
        }

        public boolean isFileExists() {
            return fileExists;
        }

        public boolean isReloadNeeded() {
            return isReloadNeeded;
        }

        public long getLastModificationTimestamp() {
            return lastModificationTimestamp;
        }
    }


    private ReloadTracker(Path filePath, long refreshTimeoutMillis) {
        this.filePath = filePath;
        this.refreshTimeoutMillis = refreshTimeoutMillis;
    }


    public ReloadStatus getReloadStatus() {
        boolean fileExists;
        var pa = (PrivilegedAction<Boolean>) () -> filePath.toFile().isFile();
        fileExists = System.getSecurityManager() == null ? pa.run() : AccessController.doPrivileged(pa);

        long lastModificationTime = -1;
        long lastReload = lastRefreshed.get();

        // Do not update lastModification time if file doesn't exist
        if (fileExists) {
            try {
                var pea = (PrivilegedExceptionAction<Long>) () -> Files.getLastModifiedTime(filePath).toMillis();
                lastModificationTime = System.getSecurityManager() == null ? pea.run()
                        : AccessController.doPrivileged(pea);
            } catch (Exception e) {
                // In case of Exception the tracked file will be reloaded, ie lastModificationTime == -1
            }
        }
        return new ReloadStatus(fileExists,
                (lastModificationTime != lastSeenChanged.get()) ||
                        (System.currentTimeMillis() - lastReload > refreshTimeoutMillis),
                lastModificationTime);
    }

    public void updateTimestamps(ReloadStatus rs) {
        lastSeenChanged.set(rs.lastModificationTimestamp);
        lastRefreshed.set(System.currentTimeMillis());
    }

    // Last timestamp when tracked file has been reloaded by consumer of this utility class
    private final AtomicLong lastRefreshed = new AtomicLong(-1);
    // Last processed FS last modified timestamp
    private final AtomicLong lastSeenChanged = new AtomicLong(-1);
    // Refresh timeout
    private final long refreshTimeoutMillis;
    // Path of the tracked file
    private final Path filePath;
}
