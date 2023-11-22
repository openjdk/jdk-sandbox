package io.bellsoft.hotcode.profiling;

import java.io.IOException;

public interface Profiling {
    void fill(Profile<Method> profile) throws IOException;
}
