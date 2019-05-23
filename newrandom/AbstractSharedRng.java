/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

// package java.util;

import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.DoubleConsumer;
import java.util.stream.StreamSupport;
import java.util.stream.Stream;

/**
 * This class provides much of the implementation of the {@code Rng}
 * interface, to minimize the effort required to implement that interface.
 *
 * To implement a pseudorandom number generator, the programmer needs
 * only to extend this class and provide implementations for the
 * {@code nextInt()} and {@code nextLong()} methods.  In order for
 * the implementations of other methods in this class to operate
 * correctly, it must be safe for multiple threads to call these
 * methods on that same object.  The principal purpose of this class
 * is to support the implementations of {@code java.util.Random}
 * and {@code java.util.concurrent.ThreadLocalRandom}, but it could
 * in principle be used to implement others as well.
 *
 * (If the pseudorandom number generator has the ability to split or
 * jump, then the programmer may wish to consider instead extending
 * another abstract class, such as {@code AbstractSplittableRng},
 * {@code AbstractJumpableRng}, {@code AbstractArbitrarilyJumpableRng},
 * {@code AbstractSplittableJumpableRng}, or
 * {@code AbstractSplittableArbitrarilyJumpableRng}.)
 *
 * The programmer should generally provide at least three constructors:
 * one that takes no arguments, one that accepts a {@code long}
 * seed value, and one that accepts an array of seed {@code byte} values.
 * This class provides a public {@code initialSeed()} method that may
 * be useful in initializing some static state from which to derive
 * defaults seeds for use by the no-argument constructor.
 *
 * For the stream methods (such as {@code ints()} and {@code splits()}),
 * this class provides {@code Spliterator}-based implementations that
 * allow parallel execution when appropriate.
 *
 * The documentation for each non-abstract method in this class
 * describes its implementation in detail. Each of these methods may
 * be overridden if the pseudorandom number generator being
 * implemented admits a more efficient implementation.
 *
 * @author  Guy Steele
 * @author  Doug Lea
 * @since   1.9
 */
public abstract class AbstractSharedRng extends AbstractSpliteratorRng {

    /*
     * Implementation Overview.
     *
     * This class provides most of the "user API" methods needed to
     * satisfy the interface java.util.Rng.  Most of these methods
     * are in turn inherited from AbstractRng and the non-public class
     * AbstractSpliteratorRng; this file implements methods and spliterators
     * necessary to support the latter.
     *
     * File organization: First some non-public methods, followed by
     * some custom spliterator classes needed for stream methods.
     */

    // Methods required by class AbstractSpliteratorRng
    Spliterator.OfInt makeIntsSpliterator(long index, long fence, int origin, int bound) {
	return new RandomIntsSpliterator(this, index, fence, origin, bound);
    }
    Spliterator.OfLong makeLongsSpliterator(long index, long fence, long origin, long bound) {
	return new RandomLongsSpliterator(this, index, fence, origin, bound);
    }
    Spliterator.OfDouble makeDoublesSpliterator(long index, long fence, double origin, double bound) {
	return new RandomDoublesSpliterator(this, index, fence, origin, bound);
    }

    // Spliterators for producing streams. These are based on abstract
    // spliterator classes provided by class AbstractSpliteratorRng.
    // Each one needs to define only a constructor and two methods.

    static class RandomIntsSpliterator extends RngSupport.RandomSpliterator implements Spliterator.OfInt {
	final AbstractSharedRng generatingRng;
        final int origin;
        final int bound;

        RandomIntsSpliterator(AbstractSharedRng generatingRng, long index, long fence, int origin, int bound) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
            this.origin = origin; this.bound = bound;
        }
	
        public Spliterator.OfInt trySplit() {
            long i = index, m = (i + fence) >>> 1;
	    if (m <= i) return null;
	    index = m;
	    // The same generatingRng is used, with no splitting or copying.
	    return new RandomIntsSpliterator(generatingRng, i, m, origin, bound);
        }

        public boolean tryAdvance(IntConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                consumer.accept(RngSupport.boundedNextInt(generatingRng, origin, bound));
                index = i + 1;
                return true;
            }
            else return false;
        }

        public void forEachRemaining(IntConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
                Rng r = generatingRng;
                int o = origin, b = bound;
                do {
                    consumer.accept(RngSupport.boundedNextInt(r, o, b));
                } while (++i < f);
            }
        }
    }

    /**
     * Spliterator for long streams.
     */
    static class RandomLongsSpliterator extends RngSupport.RandomSpliterator implements Spliterator.OfLong {
	final AbstractSharedRng generatingRng;
        final long origin;
        final long bound;

        RandomLongsSpliterator(AbstractSharedRng generatingRng, long index, long fence, long origin, long bound) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
            this.origin = origin; this.bound = bound;
        }
	
        public Spliterator.OfLong trySplit() {
            long i = index, m = (i + fence) >>> 1;
	    if (m <= i) return null;
	    index = m;
	    // The same generatingRng is used, with no splitting or copying.
	    return new RandomLongsSpliterator(generatingRng, i, m, origin, bound);
        }

        public boolean tryAdvance(LongConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                consumer.accept(RngSupport.boundedNextLong(generatingRng, origin, bound));
                index = i + 1;
                return true;
            }
            else return false;
        }

        public void forEachRemaining(LongConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
                Rng r = generatingRng;
                long o = origin, b = bound;
                do {
                    consumer.accept(RngSupport.boundedNextLong(r, o, b));
                } while (++i < f);
            }
        }
    }

    /**
     * Spliterator for double streams.
     */
    static class RandomDoublesSpliterator extends RngSupport.RandomSpliterator implements Spliterator.OfDouble {
	final AbstractSharedRng generatingRng;
        final double origin;
        final double bound;

        RandomDoublesSpliterator(AbstractSharedRng generatingRng, long index, long fence, double origin, double bound) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
            this.origin = origin; this.bound = bound;
        }
	
        public Spliterator.OfDouble trySplit() {
            long i = index, m = (i + fence) >>> 1;
	    if (m <= i) return null;
	    index = m;
	    // The same generatingRng is used, with no splitting or copying.
	    return new RandomDoublesSpliterator(generatingRng, i, m, origin, bound);
        }

        public boolean tryAdvance(DoubleConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                consumer.accept(RngSupport.boundedNextDouble(generatingRng, origin, bound));
                index = i + 1;
                return true;
            }
            else return false;
        }

        public void forEachRemaining(DoubleConsumer consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
                Rng r = generatingRng;
                double o = origin, b = bound;
                do {
                    consumer.accept(RngSupport.boundedNextDouble(r, o, b));
                } while (++i < f);
            }
        }
    }

}
