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
package com.bellsw.hotcode.util;

import java.time.Duration;
import java.util.regex.Pattern;

public final class DurationParser {

    private static final Pattern PATTERN = Pattern
            .compile("^((\\d+)(d))?((\\d+)(h))?((\\d+)(m))?((\\d+)(s))?((\\d+)(ms))?$");

    private static final int SEGMENT = 3;

    private DurationParser() {
    }

    public static Duration parse(String str) {
        var matcher = PATTERN.matcher(str);
        var result = Duration.ZERO;
        if (matcher.matches()) {
            for (var i = SEGMENT; i <= matcher.groupCount(); i += SEGMENT) {
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
