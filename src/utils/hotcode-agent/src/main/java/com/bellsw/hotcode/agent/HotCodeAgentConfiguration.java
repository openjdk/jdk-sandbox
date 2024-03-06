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
package com.bellsw.hotcode.agent;

import java.time.Duration;
import java.util.Properties;

import com.bellsw.hotcode.util.DurationParser;

public record HotCodeAgentConfiguration(Duration profilingDelay, Duration profilingPeriod, Duration samplingInterval,
        Duration profilingDuration, int top, int chunk, boolean print) {

    public HotCodeAgentConfiguration {
        if (!profilingPeriod.isZero()) {
            checkArgNot(profilingPeriod.compareTo(profilingDuration) < 0, "period < duration");
            checkArgNot(chunk > top, "chunk > top");
        }

        checkArgNot(top < 1, "top < 1");
        checkArgNot(chunk < 1, "chunk < 1");
    }

    public static HotCodeAgentConfiguration from(Properties props) {
        var profilingDelay = DurationParser.parse(props.getProperty("delay", "10m"));
        var profilingPeriod = DurationParser.parse(props.getProperty("period", "0m"));
        var samplingInterval = DurationParser.parse(props.getProperty("interval", "10ms"));
        var profilingDuration = DurationParser.parse(props.getProperty("duration", "60s"));
        int top = Integer.parseInt(props.getProperty("top", "1000"));
        int chunk = Integer.parseInt(props.getProperty("chunk", "100"));
        boolean print = Boolean.parseBoolean(props.getProperty("print", "false"));

        if (profilingPeriod.isZero()) {
            chunk = top;
        }

        return new HotCodeAgentConfiguration(profilingDelay, profilingPeriod, samplingInterval, profilingDuration, top,
                chunk, print);
    }

    private static void checkArgNot(boolean badCond, String msg) {
        if (badCond) {
            throw new IllegalArgumentException(msg);
        }
    }

}
