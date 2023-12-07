package io.bellsoft.hotcode.profiling;

import java.util.List;

public interface Profile<T> {
    boolean addSample(T elem);
    List<T> getTop(int k);
    int occurrences(T elem);
    /**
     * Total occurrences of all elements.
     */
    int total();
    /**
     * Number of unique elements.
     */
    int size();
    void clear();
}
