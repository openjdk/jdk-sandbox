/*
 *     Copyright 2023 BELLSOFT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bellsw.hotcode.profiling.jfr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import jdk.jfr.consumer.EventStream;

public final class JfrOfflineProfiling extends AbstractJfrProfiling {

    private final Path recordingPath;

    public JfrOfflineProfiling(Path recordingPath) {
        super(0);
        this.recordingPath = recordingPath;
    }

    @Override
    protected EventStream openEventStream() throws IOException {
        if (Files.isDirectory(recordingPath)) {
            return EventStream.openRepository(recordingPath);
        }
        return EventStream.openFile(recordingPath);
    }
}
