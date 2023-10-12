package io.bellsoft.hotcode.util;

import java.time.Duration;
import java.util.regex.Pattern;

public class DurationParser {

    public enum TimeUnit {

        MILLIS("ms") {
            public Duration toDuration(long value) {
                return Duration.ofMillis(value);
            }
        },
        SECONDS("s") {
            public Duration toDuration(long value) {
                return Duration.ofSeconds(value);
            }
        },
        MINUTES("m") {
            public Duration toDuration(long value) {
                return Duration.ofMinutes(value);
            }
        },
        HOURS("h") {
            public Duration toDuration(long value) {
                return Duration.ofHours(value);
            }
        },
        DAYS("d") {
            public Duration toDuration(long value) {
                return Duration.ofDays(value);
            }
        };

        private final String suffix;

        TimeUnit(String suffix) {
            this.suffix = suffix;
        }

        public abstract Duration toDuration(long value);

        public static TimeUnit fromSuffix(String suffix) {
            for (var x : values()) {
                if (x.suffix.equals(suffix)) {
                    return x;
                }
            }
            throw new IllegalArgumentException("cannot recognize time unit from suffix: " + suffix);
        }
    }

    private static final Pattern pattern = Pattern
            .compile("^((\\d+)(d))?((\\d+)(h))?((\\d+)(m))?((\\d+)(s))?((\\d+)(ms))?$");

    public static Duration parse(String value) {
        var matcher = pattern.matcher(value);
        var result = Duration.ZERO;
        if (matcher.matches()) {
            for (var i = 3; i <= matcher.groupCount(); i += 3) {
                if (matcher.group(i) != null) {
                    long units = Long.parseLong(matcher.group(i - 1));
                    result = result.plus(TimeUnit.fromSuffix(matcher.group(i)).toDuration(units));
                }
            }
        } else {
            throw new IllegalArgumentException("cannot parse " + value + " as duration");
        }
        return result;
    }

}
