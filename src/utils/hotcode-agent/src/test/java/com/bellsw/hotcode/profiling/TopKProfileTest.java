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
package com.bellsw.hotcode.profiling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

class TopKProfileTest {

    @Test
    void constructor() {
        var profile = new TopKProfile<>(16);
        assertEquals(0, profile.total(), "No occurrences must be recorded.");
        assertTrue(profile.getTop(42).isEmpty(), "Top k elements must be an empty list.");
    }

    @Test
    void topFiveOfNumbersWithTheSameFrequency() {
        var profile = new TopKProfile<Integer>(16);
        var input = List.of(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        for (var v : input) {
            profile.addSample(v);
        }
        var actual = topKMap(profile, 5);
        assertEquals(5, actual.size());
        for (var e : actual.entrySet()) {
            assertEquals(e.getValue(), 1);
        }
    }

    @Test
    void topFive() {
        var profile = new TopKProfile<Integer>(16);
        var input = List.of(
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
                0, 1, 2, 3, 4, 5, 6, 7, 8, 9,
                1, 1, 3, 3, 5, 5, 7, 7, 9, 9,
                3, 3, 3, 5, 5, 5, 5, 9, 9, 9);
        for (var v : input) {
            profile.addSample(v);
        }
        var expected = Map.of(
                5, 8,
                3, 7,
                9, 7,
                1, 4,
                7, 4);
        var actual = topKMap(profile, 5);
        assertEquals(expected, actual);
    }

    @Test
    void topTwoOfAllNumbersTheSame() {
        var profile = new TopKProfile<Integer>(16);
        var input = List.of(42, 42, 42, 42, 42, 42, 42, 42, 42, 42);
        for (var v : input) {
            profile.addSample(v);
        }
        var expected = Map.of(42, 10);
        var actual = topKMap(profile, 2);
        assertEquals(expected, actual);
    }

    @Test
    void topTenOfFiveNumbers() {
        var profile = new TopKProfile<Integer>(16);
        var input = Arrays.asList(1, 2, 3, 4, 5);
        for (var v : input) {
            profile.addSample(v);
        }
        var expected = Map.of(
                1, 1,
                2, 1,
                3, 1,
                4, 1,
                5, 1);
        var actual = topKMap(profile, 10);
        assertEquals(expected, actual);
    }

    @Test
    void topFourWhenNoInputGiven() {
        var profile = new TopKProfile<Integer>(16);
        var expected = Collections.emptyList();
        var topK = profile.getTop(4);
        assertEquals(expected, topK);
    }

    @Test
    void occurrencesWhenNullGiven() {
        var profile = new TopKProfile<Integer>(16);
        assertEquals(0, profile.occurrences(null));
    }

    @Test
    void occurrencesNotContainedInTopK() {
        var profile = new TopKProfile<Integer>(16);
        var input = List.of(1, 1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 4, 4, 5);
        for (var v : input) {
            profile.addSample(v);
        }

        var topK = profile.getTop(2);
        assertFalse(topK.contains(3));
        assertEquals(3, profile.occurrences(3));
        assertFalse(topK.contains(4));
        assertEquals(2, profile.occurrences(4));
        assertFalse(topK.contains(5));
        assertEquals(1, profile.occurrences(5));
    }

    private Map<Integer, Integer> topKMap(TopKProfile<Integer> profile, int k) {
        var topK = profile.getTop(k);
        return topK.stream()
                .collect(Collectors.toMap(Function.identity(), n -> profile.occurrences(n)));
    }

}