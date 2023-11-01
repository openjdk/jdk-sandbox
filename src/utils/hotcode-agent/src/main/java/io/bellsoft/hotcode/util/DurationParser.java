package io.bellsoft.hotcode.util;

import java.time.Duration;
import java.util.regex.Pattern;

public class DurationParser {

    private static final Pattern pattern = Pattern
            .compile("^((\\d+)(d))?((\\d+)(h))?((\\d+)(m))?((\\d+)(s))?((\\d+)(ms))?$");

    public static Duration parse(String str) {
        var matcher = pattern.matcher(str);
        var result = Duration.ZERO;
        if (matcher.matches()) {
            for (var i = 3; i <= matcher.groupCount(); i += 3) {
                if (matcher.group(i) != null) {
                    long value = Long.parseLong(matcher.group(i - 1));
                    var unit = matcher.group(i);
                    var part = switch (unit) {
                        case "ms" -> Duration.ofMillis(value);
                        case "s" -> Duration.ofSeconds(value);
                        case "m" -> Duration.ofMinutes(value);
                        case "h" -> Duration.ofHours(value);
                        case "d" -> Duration.ofDays(value);
                        default -> throw new IllegalArgumentException("cannot recognize time unit from suffix: " + unit);
                    };
                    result = result.plus(part);
                }
            }
        } else {
            throw new IllegalArgumentException("cannot parse " + str + " as duration");
        }
        return result;
    }

}
