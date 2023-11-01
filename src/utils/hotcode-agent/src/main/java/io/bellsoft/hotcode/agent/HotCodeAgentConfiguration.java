package io.bellsoft.hotcode.agent;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.bellsoft.hotcode.util.DurationParser;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Properties;

public class HotCodeAgentConfiguration {
    private static final int MAX_INLINE_LEVEL;
    static {
        var diagnosticBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        var option = diagnosticBean.getVMOption("MaxInlineLevel");
        MAX_INLINE_LEVEL = option != null ? Integer.parseInt(option.getValue()) : 15;
    }

    Duration profilingDelay = Duration.ofMinutes(30);
    Duration profilingPeriod = Duration.ZERO;
    Duration samplingInterval = Duration.ofMillis(20);
    Duration profilingDuration = Duration.ofSeconds(300);
    int top = 1000;
    int maxStackDepth = MAX_INLINE_LEVEL + 1;

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
                case "max-stack-depth" -> config.maxStackDepth = Integer.parseInt(value);
            }
        }
        if (!config.profilingPeriod.isZero()) {
            if (config.profilingPeriod.compareTo(config.profilingDuration) < 0) {
                throw new IllegalArgumentException("period < duration");
            }
        }
        if (config.top < 1) {
            throw new IllegalArgumentException("top < 1");
        }
        if (config.maxStackDepth < 1) {
            throw new IllegalArgumentException("max-stack-depth < 1");
        }
        return config;
    }

}
