package io.bellsoft.hotcode.profiling.jfr;

import io.bellsoft.hotcode.profiling.Method;
import io.bellsoft.hotcode.profiling.Profile;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;

import java.util.function.Consumer;

public class ExecutionSampleCounter implements Consumer<RecordedEvent> {

    private final Profile<Method> profile;
    private final int maxStackDepth;

    public ExecutionSampleCounter(Profile<Method> profile, int maxStackDepth) {
        this.profile = profile;
        this.maxStackDepth = maxStackDepth;
    }

    @Override
    public void accept(RecordedEvent recordedEvent) {
        var st = recordedEvent.getStackTrace();
        if (st == null) {
            return;
        }
        int depth = 0;
        for (var frame : st.getFrames()) {
            if (maxStackDepth > 0 && depth > maxStackDepth) {
                return;
            }
            if (!frame.isJavaFrame()) {
                return;
            }
            if ("JIT compiled".equals(frame.getType())) {
                profile.addSample(createMethodFrom(frame));
                return;
            }
            depth++;
        }
    }

    private static Method createMethodFrom(RecordedFrame frame) {
        var method = frame.getMethod();
        var signature = method.getName() + method.getDescriptor();
        var type = method.getType().getName();
        // com.Clazz$$Lambda$1234+0x0123456789abcdef.0123456789
        int lambdaIdx = type.indexOf("$$Lambda");
        if (lambdaIdx >= 0) {
            int hashIdx = type.indexOf('.', lambdaIdx);
            if (hashIdx >= 0) {
                type = type.substring(0, hashIdx);

            }
        }
        return new Method(type, signature);
    }

}
