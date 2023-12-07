package io.bellsoft.hotcode.agent;

import io.bellsoft.hotcode.util.DurationParser;
import java.time.Duration;
import java.util.Properties;

public class HotCodeAgentConfiguration {

    Duration profilingDelay = Duration.ofMinutes(10);
    Duration profilingPeriod = Duration.ZERO;
    Duration samplingInterval = Duration.ofMillis(10);
    Duration profilingDuration = Duration.ofSeconds(60);
    int top = 1000;
    int chunk = 100;
    boolean print = false;

    public static HotCodeAgentConfiguration from(Properties props) {
        var config = new HotCodeAgentConfiguration();
        for (var key : props.stringPropertyNames()) {
            String value = props.getProperty(key);
            switch (key) {
            case "delay" -> config.profilingDelay = DurationParser.parse(value);
            case "period" -> config.profilingPeriod = DurationParser.parse(value);
            case "interval" -> config.samplingInterval = DurationParser.parse(value);
            case "duration" -> config.profilingDuration = DurationParser.parse(value);
            case "top" -> config.top = Integer.parseInt(value);
            case "chunk" -> config.chunk = Integer.parseInt(value);
            case "print" -> config.print = Boolean.parseBoolean(value);
            }
        }

        if (config.profilingPeriod.isZero()) {
            config.chunk = config.top;
        } else {
            checkArgNot(config.profilingPeriod.compareTo(config.profilingDuration) < 0, "period < duration");
            checkArgNot(config.chunk > config.top, "chunk > top");
        }

        checkArgNot(config.top < 1, "top < 1");
        checkArgNot(config.chunk < 1, "chunk < 1");

        return config;
    }

    private static void checkArgNot(boolean badCond, String msg) {
        if (badCond) {
            throw new IllegalArgumentException(msg);
        }
    }

}
