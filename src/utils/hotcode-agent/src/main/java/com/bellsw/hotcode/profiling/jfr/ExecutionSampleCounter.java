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

import java.util.function.Consumer;

import com.bellsw.hotcode.profiling.Method;
import com.bellsw.hotcode.profiling.Profile;

import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;

public final class ExecutionSampleCounter implements Consumer<RecordedEvent> {

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
