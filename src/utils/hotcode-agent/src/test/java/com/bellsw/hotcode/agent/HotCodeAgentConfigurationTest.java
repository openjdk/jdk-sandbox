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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.Properties;

import org.junit.jupiter.api.Test;

class HotCodeAgentConfigurationTest {

    @Test
    void top() {
        int expected = 42;
        var props = new Properties();
        props.put("top", String.valueOf(expected));
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.top());
    }

    @Test
    void chunk() {
        int expected = 99;
        var props = new Properties();
        props.put("chunk", String.valueOf(expected));
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(c.top(), c.chunk());
        props.put("period", "1h");
        c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.chunk());
    }

    @Test
    void profilingDelay() {
        var expected = Duration.ofMinutes(5);
        var props = new Properties();
        props.put("delay", "5m");
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.profilingDelay());
    }

    @Test
    void profilingDuration() {
        var expected = Duration.ofSeconds(30);
        var props = new Properties();
        props.put("duration", "30s");
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.profilingDuration());
    }

    @Test
    void samplingInterval() {
        var expected = Duration.ofMillis(50);
        var props = new Properties();
        props.put("interval", "50ms");
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.samplingInterval());
    }

    @Test
    void samplingPeriod() {
        var expected = Duration.ofHours(2);
        var props = new Properties();
        props.put("period", "2h");
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.profilingPeriod());
    }

    @Test
    void durationLessThanPeriod() {
        var props = new Properties();
        props.put("duration", "30s");
        HotCodeAgentConfiguration.from(props);
        props.put("period", "20s");
        assertThrows(IllegalArgumentException.class, () -> HotCodeAgentConfiguration.from(props));
    }

}