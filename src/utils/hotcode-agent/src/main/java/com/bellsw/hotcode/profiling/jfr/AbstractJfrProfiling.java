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

import com.bellsw.hotcode.profiling.Method;
import com.bellsw.hotcode.profiling.Profile;
import com.bellsw.hotcode.profiling.Profiling;

import jdk.jfr.consumer.EventStream;

public abstract class AbstractJfrProfiling implements Profiling {

    protected static final String EXECUTION_SAMPLE_EVENT_NAME = "jdk.ExecutionSample";
    private final int maxStackDepth;

    protected abstract EventStream openEventStream() throws IOException;

    public AbstractJfrProfiling(int maxStackDepth) {
        this.maxStackDepth = maxStackDepth;
    }

    public final void fill(Profile<Method> profile) throws IOException {
        var counter = new ExecutionSampleCounter(profile, maxStackDepth);

        try (var rs = openEventStream()) {
            rs.onEvent(EXECUTION_SAMPLE_EVENT_NAME, counter);
            rs.start();
        }
    }

}
