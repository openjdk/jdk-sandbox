package io.bellsoft.hotcode.agent;

import com.sun.management.HotSpotDiagnosticMXBean;
import io.bellsoft.hotcode.util.DurationParser;

import java.lang.management.ManagementFactory;
import java.time.Duration;
import java.util.Properties;
import java.util.function.BiConsumer;

public class HotCodeAgentConfiguration {

    Duration profilingDelay = (Duration) Option.PROFILING_DELAY.defaultValue;
    Duration profilingPeriod = (Duration) Option.PROFILING_PERIOD.defaultValue;
    Duration samplingInterval = (Duration) Option.SAMPLING_INTERVAL.defaultValue;
    Duration profilingDuration = (Duration) Option.PROFILING_DURATION.defaultValue;
    int topK = (int) Option.TOP_K.defaultValue;
    int maxStackDepth = (int) Option.MAX_STACK_DEPTH.defaultValue;

    public enum Option {
        PROFILING_DELAY("delay", Duration.ofMinutes(30), new BiConsumer<HotCodeAgentConfiguration, String>() {
            @Override
            public void accept(HotCodeAgentConfiguration config, String value) {
                try {
                    config.profilingDelay = DurationParser.parse(value);
                } catch (IllegalArgumentException e) {
                }
            }
        }),
        PROFILING_PERIOD("period", Duration.ZERO, new BiConsumer<HotCodeAgentConfiguration, String>() {
            @Override
            public void accept(HotCodeAgentConfiguration config, String value) {
                try {
                    config.profilingPeriod = DurationParser.parse(value);
                } catch (IllegalArgumentException e) {
                }
            }
        }),
        SAMPLING_INTERVAL("interval", Duration.ofMillis(20), new BiConsumer<HotCodeAgentConfiguration, String>() {
            @Override
            public void accept(HotCodeAgentConfiguration config, String value) {
                try {
                    config.samplingInterval = DurationParser.parse(value);
                } catch (IllegalArgumentException e) {
                }
            }
        }),
        PROFILING_DURATION("duration", Duration.ofSeconds(300), new BiConsumer<HotCodeAgentConfiguration, String>() {
            @Override
            public void accept(HotCodeAgentConfiguration config, String value) {
                try {
                    config.profilingDuration = DurationParser.parse(value);
                } catch (IllegalArgumentException e) {
                }
            }
        }),
        TOP_K("topK", 1000, new BiConsumer<HotCodeAgentConfiguration, String>() {
            @Override
            public void accept(HotCodeAgentConfiguration config, String value) {
                try {
                    config.topK = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                }
            }
        }),
        MAX_STACK_DEPTH("maxStackDepth", 15, new BiConsumer<HotCodeAgentConfiguration, String>() {
            @Override
            public void accept(HotCodeAgentConfiguration config, String value) {
                try {
                    config.maxStackDepth = Integer.parseInt(value);
                } catch (NumberFormatException e) {
                }
            }
        });

        private final String label;
        private final BiConsumer<HotCodeAgentConfiguration, String> setter;
        private final Object defaultValue;

        public Object getDefaultValue() {
            return defaultValue;
        }

        Option(String label, Object defaultValue, BiConsumer<HotCodeAgentConfiguration, String> setter) {
            this.label = label;
            this.defaultValue = defaultValue;
            this.setter = setter;
        }
    }

    private HotCodeAgentConfiguration() {
        var mxBean = ManagementFactory.getPlatformMXBean(HotSpotDiagnosticMXBean.class);
        var maxInlineLevelOpt = mxBean.getVMOption("MaxInlineLevel");
        if (maxInlineLevelOpt != null) {
            Option.MAX_STACK_DEPTH.setter.accept(this, maxInlineLevelOpt.getValue());
        }
    }

    public static HotCodeAgentConfiguration from(Properties properties) {
        var config = new HotCodeAgentConfiguration();
        for (var option : Option.values()) {
            var value = properties.getProperty(option.label);
            if (value != null) {
                option.setter.accept(config, value);
            }
        }
        return config;
    }

    public static HotCodeAgentConfiguration from(String argumentString) {
        var properties = new Properties();
        if (argumentString != null) {
            var arguments = argumentString.split(",");
            for (var argument : arguments) {
                int idx = argument.indexOf('=');
                if (idx >= 0) {
                    var key = argument.substring(0, idx);
                    var value = argument.substring(idx + 1);
                    properties.put(key, value);
                } else {
                    properties.put(argument, "");
                }
            }
        }
        return HotCodeAgentConfiguration.from(properties);
    }
}
