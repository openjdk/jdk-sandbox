package com.oracle.jmx.remote.rest.http;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public interface RestCollectionFilter<T> {
    List<T> filter(List<T> input);
}
