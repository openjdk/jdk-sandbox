package io.bellsoft.hotcode.profiling;

import java.util.List;

public interface Profile<T> {

    boolean addSample(T elem);

    List<T> getTop();

    int occurrences(T elem);

    int getTotal();

    int getTotalUnique();
}
