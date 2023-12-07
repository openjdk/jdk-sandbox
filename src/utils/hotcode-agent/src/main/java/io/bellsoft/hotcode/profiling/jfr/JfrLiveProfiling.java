package io.bellsoft.hotcode.profiling.jfr;

import jdk.jfr.consumer.EventStream;
import jdk.jfr.consumer.RecordingStream;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.time.Instant;

import com.sun.management.HotSpotDiagnosticMXBean;

public class JfrLiveProfiling extends AbstractJfrProfiling {

    private final Duration samplingInterval;
    private final Duration duration;
    
    private static final int MAX_INLINE_LEVEL;
    static {
        var diagnosticBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        var option = diagnosticBean.getVMOption("MaxInlineLevel");
        MAX_INLINE_LEVEL = option != null ? Integer.parseInt(option.getValue()) : 15;
        // Initialize JFR early on agent start
        try (var activeEmptyEventStream = new RecordingStream()) {
            activeEmptyEventStream.setEndTime(Instant.now());
            activeEmptyEventStream.start();
        }
    }

    public JfrLiveProfiling(Duration samplingInterval, Duration duration) {
        super(MAX_INLINE_LEVEL + 1);
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
