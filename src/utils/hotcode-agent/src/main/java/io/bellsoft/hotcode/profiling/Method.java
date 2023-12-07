package io.bellsoft.hotcode.profiling;

import java.util.Objects;

public record Method(String type, String signature) implements Comparable<Method> {

    public Method {
        Objects.requireNonNull(type);
        Objects.requireNonNull(signature);
    }

    @Override
    public int compareTo(Method o) {
        int res = type.compareTo(o.type);
        return (res != 0) ? res : signature.compareTo(o.signature);
    }

}