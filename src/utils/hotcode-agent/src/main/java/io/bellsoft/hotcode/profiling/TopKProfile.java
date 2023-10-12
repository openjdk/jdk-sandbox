package io.bellsoft.hotcode.profiling;

import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;

public class TopKProfile<T> implements Profile<T> {

    private final int k;
    private int maxCount;
    private final HashMap<T, Integer> samples;
    private int total = 0;

    public TopKProfile(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("at least 1 top element should be specified");
        }
        this.k = k;
        this.samples = new HashMap<>(k * 2);
    }

    @Override
    public boolean addSample(T elem) {
        int newCount = samples.getOrDefault(elem, 0) + 1;
        samples.put(elem, newCount);
        maxCount = Math.max(maxCount, newCount);
        total++;
        return newCount == 1;
    }

    @Override
    public List<T> getTop() {
        var occurrences = getTopKOccurrences();
        var result = new ArrayList<T>(occurrences.size());
        for (var elem : occurrences) {
            result.add(elem.getKey());
        }
        return result;
    }

    @Override
    public int getTotalUnique() {
        return samples.size();
    }

    @Override
    public int getTotal() {
        return total;
    }

    @Override
    public int occurrences(T elem) {
        return samples.getOrDefault(elem, 0);
    }

    // This method is expected to work with O(n) time complexity and O(maxCount)
    // space complexity.
    // It's more efficient than the obvious solution with using PriorityQueue
    // (O(NlogK), O(K)). With both
    // solutions we cannot preserve the order of recorded methods though.
    private List<Map.Entry<T, Integer>> getTopKOccurrences() {
        @SuppressWarnings("unchecked")
        List<Map.Entry<T, Integer>>[] count2samples = new List[maxCount + 1];
        for (var entry : samples.entrySet()) {
            int count = entry.getValue();
            if (count2samples[count] == null) {
                count2samples[count] = new ArrayList<>();
            }
            count2samples[count].add(entry);
        }

        var result = new ArrayList<Map.Entry<T, Integer>>();
        for (int count = maxCount, remainingK = this.k; count >= 0 && remainingK > 0; count--) {
            if (count2samples[count] != null) {
                // TODO: we can ensure here the list won't grow up greater than k elements
                result.addAll(count2samples[count]);
                remainingK -= count2samples[count].size();
            }
        }
        return result.subList(0, Math.min(k, result.size()));
    }
}
