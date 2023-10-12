package io.bellsoft.hotcode.agent;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class HotCodeAgentConfigurationTest {

    @Test
    void topK() {
        int expected = 42;
        var props = new Properties();
        props.put("topK", String.valueOf(expected));
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.topK);
    }

    @Test
    void maxStackDepth() {
        int expected = 42;
        var props = new Properties();
        props.put("maxStackDepth", String.valueOf(expected));
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.maxStackDepth);
    }

    @Test
    void profilingDelay() {
        var expected = Duration.ofMinutes(5);
        var props = new Properties();
        props.put("delay", "5m");
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.profilingDelay);
    }

    @Test
    void profilingDuration() {
        var expected = Duration.ofSeconds(30);
        var props = new Properties();
        props.put("duration", "30s");
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.profilingDuration);
    }

    @Test
    void samplingInterval() {
        var expected = Duration.ofMillis(50);
        var props = new Properties();
        props.put("interval", "50ms");
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.samplingInterval);
    }

    @Test
    void samplingPeriod() {
        var expected = Duration.ofHours(2);
        var props = new Properties();
        props.put("period", "2h");
        var c = HotCodeAgentConfiguration.from(props);
        assertEquals(expected, c.profilingPeriod);
    }

}