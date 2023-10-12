package io.bellsoft.hotcode.profiling.jfr;

import io.bellsoft.hotcode.profiling.Method;
import io.bellsoft.hotcode.profiling.Profile;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedMethod;

import java.util.List;
import java.util.function.Consumer;

public class ExecutionSampleCounter implements Consumer<RecordedEvent> {

    private final Profile<Method> profile;
    private final int maxStackDepth;

    public ExecutionSampleCounter(Profile<Method> topKStrategy) {
        this(topKStrategy, -1);
    }

    public ExecutionSampleCounter(Profile<Method> profile, int maxStackDepth) {
        this.profile = profile;
        this.maxStackDepth = maxStackDepth;
    }

    @Override
    public void accept(RecordedEvent recordedEvent) {
        var frames = recordedEvent.getStackTrace().getFrames();
        if (frames.isEmpty()) {
            // ignore samples with empty stacktrace, is it possible?
            return;
        }

        var frame = getFirstJitCompiledJavaFrame(frames);
        if (frame != null) {
            profile.addSample(createMethodFrom(frame.getMethod()));
        }
    }

    private RecordedFrame getFirstJitCompiledJavaFrame(List<RecordedFrame> frames) {
        int depth = 0;
        for (var frame : frames) {
            if (maxStackDepth > 0 && depth > maxStackDepth) {
                return null;
            }
            if (!frame.isJavaFrame()) {
                return null;
            }
            if (frame.getType().equals("JIT compiled")) {
                return frame;
            }
            depth++;
        }
        return null;
    }

    public Profile<Method> getProfile() {
        return profile;
    }

    private static Method createMethodFrom(RecordedMethod method) {
        return new Method(method.getName() + method.getDescriptor(), method.getType().getName());
    }

}
