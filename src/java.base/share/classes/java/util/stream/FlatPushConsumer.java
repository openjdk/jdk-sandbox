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

    static class OfRef<U> extends FlatPushConsumer<Consumer<? super U>>
            implements Consumer<U> {

        OfRef(Consumer<? super U> consumer) {
            super(consumer);
        }

        @Override
        public void accept(U u) {
            var consumer = this.consumer;
            if (consumer == null)
                throw new IllegalStateException("access out of method boundaries");
            this.consumer = null; // prevents recursive access
            try {
                consumer.accept(u);
            } finally {
                this.consumer = consumer;
            }
        }
    }

    static class OfDouble extends FlatPushConsumer<DoubleConsumer>
            implements DoubleConsumer {

        OfDouble(DoubleConsumer consumer) {
            super(consumer);
        }

        @Override
        public void accept(double u) {
            var consumer = this.consumer;
            if (consumer == null)
                throw new IllegalStateException("access out of method boundaries");
            this.consumer = null; // prevents recursive access
            try {
                consumer.accept(u);
            } finally {
                this.consumer = consumer;
            }
        }
    }

    static class OfInt extends FlatPushConsumer<IntConsumer>
            implements IntConsumer {

        OfInt(IntConsumer consumer) {
            super(consumer);
        }

        @Override
        public void accept(int u) {
            var consumer = this.consumer;
            if (consumer == null)
                throw new IllegalStateException("access out of method boundaries");
            this.consumer = null; // prevents recursive access
            try {
                consumer.accept(u);
            } finally {
                this.consumer = consumer;
            }
        }
    }

    static class OfLong extends FlatPushConsumer<LongConsumer>
            implements LongConsumer {

        OfLong(LongConsumer consumer) {
            super(consumer);
        }

        @Override
        public void accept(long u) {
            var consumer = this.consumer;
            if (consumer == null)
                throw new IllegalStateException("access out of method boundaries");
            this.consumer = null; // prevents recursive access
            try {
                consumer.accept(u);
            } finally {
                this.consumer = consumer;
            }
        }
    }
}
