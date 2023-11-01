package io.bellsoft.hotcode.profiling.jfr;

import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingStream;

import java.time.Duration;
import java.time.Instant;

public class JfrLiveProfiling extends AbstractJfrProfiling {

    private final Duration samplingInterval;
    private final Duration duration;
    
    static {
        // Initialize JFR early on agent start
        try (var activeEmptyEventStream = new RecordingStream()) {
            activeEmptyEventStream.setEndTime(Instant.now());
            activeEmptyEventStream.start();
        }
    }

    public JfrLiveProfiling(int maxStackDepth, Duration samplingInterval, Duration duration) {
        super(maxStackDepth);
        this.samplingInterval = samplingInterval;
        this.duration = duration;
    }

    @Override
    protected EventStream openEventStream() {
        var rs = new RecordingStream();
        rs.enable(EXECUTION_SAMPLE_EVENT_NAME).withPeriod(samplingInterval);
        rs.setOrdered(false);
        rs.setReuse(true);
        rs.setEndTime(Instant.now().plus(duration));
        return rs;
    }
}
