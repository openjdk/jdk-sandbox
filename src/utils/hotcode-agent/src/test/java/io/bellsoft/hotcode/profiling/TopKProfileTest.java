package io.bellsoft.hotcode.profiling;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class TopKProfileTest {

    @Test
    void constructor() {
        var profile = new TopKProfile<>();
        assertEquals(0, profile.getTotal(), "no occurrences must be recorded");
        assertTrue(profile.getTop(42).isEmpty(), "top k elements must be an empty list");
    }

    @Test
    void topFiveOfNumbersWithTheSameFrequency() {
        var profile = new TopKProfile<Integer>();
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
        var profile = new TopKProfile<Integer>();
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
        var profile = new TopKProfile<Integer>();
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
        var profile = new TopKProfile<Integer>();
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
        var profile = new TopKProfile<Integer>();
        var expected = Collections.emptyList();
        var topK = profile.getTop(4);
        assertEquals(expected, topK);
    }

    @Test
    void occurrencesWhenNullGiven() {
        var profile = new TopKProfile<Integer>();
        assertEquals(0, profile.occurrences(null));
    }

    @Test
    void occurrencesNotContainedInTopK() {
        var profile = new TopKProfile<Integer>();
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