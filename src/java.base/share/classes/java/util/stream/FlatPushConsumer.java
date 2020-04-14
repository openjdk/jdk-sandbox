package java.util.stream;

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

/**
 * Subtypes of {@link Consumer} that are used in the various flatPush
 * implementations, such as {@link Stream#flatPush(BiConsumer)} flatPush()}.
 *
 * @param <T> the type of Consumer
 */
abstract class FlatPushConsumer<T> implements AutoCloseable {
    T consumer;

    FlatPushConsumer(T consumer) {
        this.consumer = consumer;
    }

    // Dereference to ensure buffer is inaccessible after use
    @Override
    public void close() {
        consumer = null;
    }

    static class RefSink<U> extends FlatPushConsumer<Sink<? super U>>
            implements Consumer<U> {

        RefSink(Sink<? super U> consumer) {
            super(consumer);
        }

        @Override
        public void accept(U u) {
            consumer.accept(u);
        }
    }

    static class DoubleSink extends FlatPushConsumer<Sink<? super Double>>
            implements DoubleConsumer {

        DoubleSink(Sink<? super Double> consumer) {
            super(consumer);
        }

        @Override
        public void accept(double u) {
            consumer.accept(u);
        }
    }

    static class IntSink extends FlatPushConsumer<Sink<? super Integer>>
            implements IntConsumer {

        IntSink(Sink<? super Integer> consumer) {
            super(consumer);
        }

        @Override
        public void accept(int u) {
            consumer.accept(u);
        }
    }

    static class LongSink extends FlatPushConsumer<Sink<? super Long>>
            implements LongConsumer {

        LongSink(Sink<? super Long> consumer) {
            super(consumer);
        }

        @Override
        public void accept(long u) {
            consumer.accept(u);
        }
    }

    static class RefBuffer<U> extends FlatPushConsumer<SpinedBuffer<U>>
            implements Consumer<U> {

        RefBuffer(SpinedBuffer<U> consumer) {
            super(consumer);
        }

        @Override
        public void accept(U u) {
            consumer.accept(u);
        }
    }

    static class DoubleBuffer extends FlatPushConsumer<SpinedBuffer.OfDouble>
            implements DoubleConsumer {

        DoubleBuffer(SpinedBuffer.OfDouble consumer) {
            super(consumer);
        }

        @Override
        public void accept(double u) {
            consumer.accept(u);
        }
    }

    static class IntBuffer extends FlatPushConsumer<SpinedBuffer.OfInt>
            implements IntConsumer {

        IntBuffer(SpinedBuffer.OfInt consumer) {
            super(consumer);
        }

        @Override
        public void accept(int u) {
            consumer.accept(u);
        }
    }

    static class LongBuffer extends FlatPushConsumer<SpinedBuffer.OfLong>
            implements LongConsumer {

        LongBuffer(SpinedBuffer.OfLong consumer) {
            super(consumer);
        }

        @Override
        public void accept(long u) {
            consumer.accept(u);
        }
    }
}
