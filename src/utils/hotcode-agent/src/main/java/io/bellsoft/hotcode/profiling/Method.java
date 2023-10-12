package io.bellsoft.hotcode.profiling;

import java.util.Objects;

public class Method {

    private final String signature;
    private final String type;

    public Method(String signature, String type) {
        this.signature = signature;
        this.type = type;
    }

    public String getType() {
        return type;
    }

    public String getSignature() {
        return signature;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof Method))
            return false;
        var method = (Method) o;
        return Objects.equals(signature, method.signature) && Objects.equals(type, method.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(signature, type);
    }

    @Override
    public String toString() {
        return "Method{" + "signature='" + signature + '\'' + ", type='" + type + '\'' + '}';
    }
}
