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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;

public final class TopKProfile<T> implements Profile<T> {

    private final HashMap<T, Integer> samples;
    private int total;

    public TopKProfile(int initialCapacity) {
        this.samples = new HashMap<>(initialCapacity);
    }

    @Override
    public boolean addSample(T elem) {
        int newCount = samples.getOrDefault(elem, 0) + 1;
        samples.put(elem, newCount);
        total++;
        return newCount == 1;
    }

    private final Comparator<Map.Entry<T, Integer>> desc = new Comparator<>() {
        @Override
        public int compare(Entry<T, Integer> o1, Entry<T, Integer> o2) {
            return -o1.getValue().compareTo(o2.getValue());
        }
    };

    @Override
    public List<T> getTop(int k) {
        if (k < 1) {
            throw new IllegalArgumentException("At least 1 top element should be specified.");
        }
        int n = samples.entrySet().size();
        if (n == 0) {
            return List.of();
        }
        var pq = new PriorityQueue<Map.Entry<T, Integer>>(n, desc);
        pq.addAll(samples.entrySet());
        int len = Math.min(k, pq.size());
        var result = new ArrayList<T>(len);
        for (int i = 0; i < len; i++) {
            result.add(pq.poll().getKey());
        }
        return result;
    }

    @Override
    public int size() {
        return samples.size();
    }

    @Override
    public int occurrences(T elem) {
        return samples.getOrDefault(elem, 0);
    }

    @Override
    public int total() {
        return total;
    }

    @Override
    public void clear() {
        samples.clear();
        total = 0;
    }
}
