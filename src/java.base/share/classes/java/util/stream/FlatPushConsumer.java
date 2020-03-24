package java.util.stream;

import java.util.function.Consumer;

final class FlatPushConsumer<T> implements Consumer<T>, AutoCloseable {
    SpinedBuffer<T> buffer;

    FlatPushConsumer(SpinedBuffer<T> buffer) {
        this.buffer = buffer;
    }

    @Override
    public void accept(T t) {
        buffer.accept(t);
    }

    // Dereference to ensure buffer is inaccessible after use
    @Override
    public void close() {
        buffer = null;
    }
}
