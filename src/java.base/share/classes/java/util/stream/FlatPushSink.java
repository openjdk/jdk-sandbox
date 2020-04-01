package java.util.stream;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

abstract class FlatPushSink<T> implements AutoCloseable {
    Sink<? super T> sink;

    FlatPushSink(Sink<? super T> sink) {
        this.sink = sink;
    }

    // Dereference to ensure buffer is inaccessible after use
    @Override
    public void close() {
        sink = null;
    }

    static class OfRef<T> extends FlatPushSink<T> implements Consumer<T> {

        OfRef(Sink<? super T> sink) {
            super(sink);
        }

        @Override
        public void accept(T u) {
            sink.accept(u);
        }
    }

    static class OfInt extends FlatPushSink<Integer> implements IntConsumer {

        OfInt(Sink<? super Integer> sink) {
            super(sink);
        }

        @Override
        public void accept(int i) {
            sink.accept(i);
        }
    }

    static class OfLong extends FlatPushSink<Long> implements LongConsumer {

        OfLong(Sink<? super Long> sink) {
            super(sink);
        }

        @Override
        public void accept(long l) {
            sink.accept(l);
        }
    }

    static class OfDouble extends FlatPushSink<Double> implements DoubleConsumer {

        OfDouble(Sink<? super Double> sink) {
            super(sink);
        }

        @Override
        public void accept(double d) {
            sink.accept(d);
        }
    }
}
