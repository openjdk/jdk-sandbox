package java.util.stream;

import java.util.function.Consumer;

final class FlatMapConsumer<T> implements Consumer<T>, AutoCloseable {
    SpinedBuffer<T> buffer;

    FlatMapConsumer(SpinedBuffer<T> buffer) {
        this.buffer = buffer;
    }

    @Override
    public void accept(T t) {
        buffer.accept(t);
    }

    // Deference to ensure buffer is inaccessible after use
    @Override
    public void close() {
        buffer = null;
    }
}
