package io.bellsoft.hotcode.profiling.jfr;

import io.bellsoft.hotcode.profiling.TopKProfile;
import io.bellsoft.hotcode.profiling.Method;
import io.bellsoft.hotcode.profiling.ProfilingTask;
import io.bellsoft.hotcode.profiling.Profile;
import jdk.jfr.consumer.EventStream;

import java.io.IOException;

public abstract class AbstractJfrProfiling implements ProfilingTask {

    protected static final String EXECUTION_SAMPLE_EVENT_NAME = "jdk.ExecutionSample";
    private final int topK;
    private final int maxStackDepth;

    abstract protected EventStream openEventStream() throws IOException;

    public AbstractJfrProfiling(int topK, int maxStackDepth) {
        this.topK = topK;
        this.maxStackDepth = maxStackDepth;
    }

    @Override
    public Profile<Method> call() {
        var counter = new ExecutionSampleCounter(new TopKProfile<>(topK), maxStackDepth);

        try (var rs = openEventStream()) {
            rs.onEvent(EXECUTION_SAMPLE_EVENT_NAME, counter);
            rs.start();
        } catch (IOException e) {
            throw new RuntimeException("cannot open recording stream", e);
        }

        return counter.getProfile();
    }

}
