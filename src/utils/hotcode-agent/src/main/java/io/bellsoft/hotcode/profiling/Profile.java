package io.bellsoft.hotcode.profiling;

import java.util.List;

public interface Profile<T> {
    boolean addSample(T elem);
    List<T> getTop(int k);
    int occurrences(T elem);
    int getTotal();
    void clear();
}
