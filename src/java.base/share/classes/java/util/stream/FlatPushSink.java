package java.util.stream;

import java.util.function.Consumer;

final class FlatPushSink<T> implements AutoCloseable, Consumer<T> {
    Sink<? super T> sink;

    FlatPushSink(Sink<? super T> sink) {
        this.sink = sink;
    }

    @Override
    public void accept(T u) {
        sink.accept(u);
    }

    // Dereference to ensure buffer is inaccessible after use
    @Override
    public void close() {
        sink = null;
    }
}
