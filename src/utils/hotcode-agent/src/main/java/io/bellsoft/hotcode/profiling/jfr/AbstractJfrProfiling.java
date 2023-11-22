package io.bellsoft.hotcode.profiling.jfr;

import io.bellsoft.hotcode.profiling.Method;
import io.bellsoft.hotcode.profiling.Profiling;
import io.bellsoft.hotcode.profiling.Profile;
import jdk.jfr.consumer.EventStream;

import java.io.IOException;

public abstract class AbstractJfrProfiling implements Profiling {

    protected static final String EXECUTION_SAMPLE_EVENT_NAME = "jdk.ExecutionSample";
    private final int maxStackDepth;

    abstract protected EventStream openEventStream() throws IOException;

    public AbstractJfrProfiling(int maxStackDepth) {
        this.maxStackDepth = maxStackDepth;
    }

    public void fill(Profile<Method> profile) throws IOException {
        var counter = new ExecutionSampleCounter(profile, maxStackDepth);

        try (var rs = openEventStream()) {
            rs.onEvent(EXECUTION_SAMPLE_EVENT_NAME, counter);
            rs.start();
        }
    }

}
