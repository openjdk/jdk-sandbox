package io.bellsoft.hotcode.profiling.jfr;

import jdk.jfr.consumer.EventStream;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JfrOfflineProfiling extends AbstractJfrProfiling {

    private final Path recordingPath;

    public JfrOfflineProfiling(int topKSamplesCount, int maxStackDepth, Path recordingPath) {
        super(topKSamplesCount, maxStackDepth);
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
