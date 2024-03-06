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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DurationParserTest {

    @ParameterizedTest
    @ValueSource(strings = { "100ms", "20s", "339m", "4h", "365d" })
    void basicValidStrings(String value) {
        DurationParser.parse(value);
    }

    @Test
    void millis() {
        int millis = 837;
        var value = String.format("%dms", millis);
        var duration = DurationParser.parse(value);
        assertEquals(millis, duration.toMillis());
    }

    @Test
    void seconds() {
        int seconds = 2392;
        var value = String.format("%ds", seconds);
        var duration = DurationParser.parse(value);
        assertEquals(seconds, duration.getSeconds());
    }

    @Test
    void minutes() {
        int minutes = 99;
        var value = String.format("%dm", minutes);
        var duration = DurationParser.parse(value);
        assertEquals(minutes, duration.toMinutes());
    }

    @Test
    void hours() {
        int hours = 23;
        var value = String.format("%dh", hours);
        var duration = DurationParser.parse(value);
        assertEquals(hours, duration.toHours());
    }

    @Test
    void days() {
        int days = 163;
        var value = String.format("%dd", days);
        var duration = DurationParser.parse(value);
        assertEquals(days, duration.toDays());
    }

    @ParameterizedTest
    @ValueSource(strings = { "ms", "a0s", "3.333m", "0x20h", "1e100d" })
    void someInvalidValues(String value) {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "20xs", "12abc", "339A", "1111" })
    void someInvalidUnits(String value) {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "100MS", "20S", "339M", "4H", "365D" })
    void noUppercase(String value) {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "0ms", "0s", "0m", "0h", "0d" })
    void zeroDurations(String value) {
        var duration = DurationParser.parse(value);
        assertTrue(duration.isZero(), "duration must be zero");
    }

    @ParameterizedTest
    @ValueSource(strings = { "2 0 ms", "312 s", "939 m ", "12   h", " 13 d" })
    void noSpacesAllowed(String value) {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "-2ms", "-233s", "-419m", "-99h", "-1200d" })
    void negativeDurationIsIllegal(String value) {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = { "+2ms", "+233s", "+419m", "+99h", "+1200d" })
    void explicitPositiveDurationIsIllegal(String value) {
        assertThrows(IllegalArgumentException.class, () -> DurationParser.parse(value));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "2d", "1d24h", "1d23h60m", "1d23h59m60s", "1d23h59m59s1000ms",
            "48h", "47h60m", "47h59m60s", "47h59m59s1000ms",
            "2880m", "2879m60s", "2879m59s1000ms",
            "172800s", "172799s1000ms",
            "172800000ms"
            })
    void complexDurationsOfTwoDays(String value) {
        var expected = Duration.ofDays(2);
        assertEquals(expected, DurationParser.parse(value));
    }
}