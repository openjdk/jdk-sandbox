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

import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.function.DoubleConsumer;
import java.util.Spliterator;
import java.util.stream.StreamSupport;
import java.util.stream.Stream;

/**
 * This class provides much of the implementation of the {@code SplittableRng}
 * interface, to minimize the effort required to implement this interface.
 *
 * To implement a pseudorandom number generator, the programmer needs
 * only to extend this class and provide implementations for the
 * methods {@code nextInt()}, {@code nextLong()}, {@code period()},
 * and {@code split(SplittableRng)}.
 *
 * (If the pseudorandom number generator also has the ability to jump,
 * then the programmer may wish to consider instead extending
 * the class {@code AbstractSplittableJumpableRng} or (if it can also leap)
 * {@code AbstractSplittableLeapableRng}.  But if the pseudorandom number
 * generator furthermore has the ability to jump an arbitrary specified
 * distance, then the programmer may wish to consider instead extending
 * the class {@code * AbstractSplittableArbitrarilyJumpableRng}.)
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
public abstract class AbstractSplittableRng extends AbstractSpliteratorRng implements SplittableRng {

    /*
     * Implementation Overview.
     *
     * This class provides most of the "user API" methods needed to
     * satisfy the interface java.util.JumpableRng.  Most of these methods
     * are in turn inherited from AbstractRng and the non-public class
     * AbstractSpliteratorRng; this file implements two versions of the
     * splits method and defines the spliterators necessary to support
     * them.
     *
     * The abstract split() method from interface SplittableRng is redeclared
     * here so as to narrow the return type to AbstractSplittableRng.
     *
     * File organization: First the non-public methods needed by the class
     * AbstractSpliteratorRng, then the main public methods, followed by some
     * custom spliterator classes.
     */

    Spliterator.OfInt makeIntsSpliterator(long index, long fence, int origin, int bound) {
	return new RandomIntsSpliterator(this, index, fence, origin, bound);
    }
    
    Spliterator.OfLong makeLongsSpliterator(long index, long fence, long origin, long bound) {
	return new RandomLongsSpliterator(this, index, fence, origin, bound);
    }
    
    Spliterator.OfDouble makeDoublesSpliterator(long index, long fence, double origin, double bound) {
	return new RandomDoublesSpliterator(this, index, fence, origin, bound);
    }

    Spliterator<SplittableRng> makeSplitsSpliterator(long index, long fence, SplittableRng source) {
	return new RandomSplitsSpliterator(source, index, fence, this);
    }

    /* ---------------- public methods ---------------- */

    /**
     * Implements the @code{split()} method as {@code this.split(this) }.
     *
     * @return the new {@code AbstractSplittableRng} instance
     */
    public SplittableRng split() { return this.split(this); }
    
    // Stream methods for splittings

    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code SplittableRng}
     * interface.
     *
     * This pseudorandom number generator provides the
     * entropy used to seed the new ones.
     *
     * @implNote This method is implemented to be equivalent to
     * {@code splits(Long.MAX_VALUE)}.
     *
     * @return a stream of {@code SplittableRng} objects
     */
    public Stream<SplittableRng> splits() {
        return this.splits(Long.MAX_VALUE, this);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code SplittableRng} interface.
     *
     * This pseudorandom number generator provides the
     * entropy used to seed the new ones.
     *
     * @param streamSize the number of values to generate
     * @return a stream of {@code SplittableRng} objects
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    public Stream<SplittableRng> splits(long streamSize) {
	return this.splits(streamSize, this);
    }

    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code SplittableRng}
     * interface.
     *
     * @implNote This method is implemented to be equivalent to
     * {@code splits(Long.MAX_VALUE)}.
     *
     * @param source a {@code SplittableRng} instance to be used instead
     *               of this one as a source of pseudorandom bits used to
     *               initialize the state of the new ones.
     * @return a stream of {@code SplittableRng} objects
     */
    public Stream<SplittableRng> splits(SplittableRng source) {
        return this.splits(Long.MAX_VALUE, source);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code SplittableRng} interface.
     *
     * @param streamSize the number of values to generate
     * @param source a {@code SplittableRng} instance to be used instead
     *               of this one as a source of pseudorandom bits used to
     *               initialize the state of the new ones.
     * @return a stream of {@code SplittableRng} objects
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    public Stream<SplittableRng> splits(long streamSize, SplittableRng source) {
	RngSupport.checkStreamSize(streamSize);
        return StreamSupport.stream(makeSplitsSpliterator(0L, streamSize, source), false);
    }
        
    /**
     * Spliterator for int streams.  We multiplex the four int
     * versions into one class by treating a bound less than origin as
     * unbounded, and also by treating "infinite" as equivalent to
     * Long.MAX_VALUE. For splits, it uses the standard divide-by-two
     * approach. The long and double versions of this class are
     * identical except for types.
     */
    static class RandomIntsSpliterator extends RngSupport.RandomSpliterator implements Spliterator.OfInt {
	final SplittableRng generatingRng;
        final int origin;
        final int bound;

        RandomIntsSpliterator(SplittableRng generatingRng, long index, long fence, int origin, int bound) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
            this.origin = origin; this.bound = bound;
        }
	
        public Spliterator.OfInt trySplit() {
            long i = index, m = (i + fence) >>> 1;
	    if (m <= i) return null;
	    index = m;
	    return new RandomIntsSpliterator(generatingRng.split(), i, m, origin, bound);
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
	final SplittableRng generatingRng;
        final long origin;
        final long bound;

        RandomLongsSpliterator(SplittableRng generatingRng, long index, long fence, long origin, long bound) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
            this.origin = origin; this.bound = bound;
        }
	
        public Spliterator.OfLong trySplit() {
            long i = index, m = (i + fence) >>> 1;
	    if (m <= i) return null;
	    index = m;
	    return new RandomLongsSpliterator(generatingRng.split(), i, m, origin, bound);
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
	final SplittableRng generatingRng;
        final double origin;
        final double bound;

        RandomDoublesSpliterator(SplittableRng generatingRng, long index, long fence, double origin, double bound) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
            this.origin = origin; this.bound = bound;
        }
	
        public Spliterator.OfDouble trySplit() {
            long i = index, m = (i + fence) >>> 1;
	    if (m <= i) return null;
	    index = m;
	    return new RandomDoublesSpliterator(generatingRng.split(), i, m, origin, bound);
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

    /**
     * Spliterator for stream of generators of type SplittableRng.  We multiplex the two
     * versions into one class by treating "infinite" as equivalent to Long.MAX_VALUE.
     * For splits, it uses the standard divide-by-two approach.
     */
    static class RandomSplitsSpliterator extends RngSupport.RandomSpliterator implements Spliterator<SplittableRng> {
	final SplittableRng generatingRng;
	final SplittableRng constructingRng;

        RandomSplitsSpliterator(SplittableRng generatingRng, long index, long fence, SplittableRng constructingRng) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
	    this.constructingRng = constructingRng;
        }
	
        public Spliterator<SplittableRng> trySplit() {
            long i = index, m = (i + fence) >>> 1;
	    if (m <= i) return null;
	    index = m;
	    return new RandomSplitsSpliterator(generatingRng.split(), i, m, constructingRng);
        }

        public boolean tryAdvance(Consumer<? super SplittableRng> consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                consumer.accept(constructingRng.split(generatingRng));
                index = i + 1;
                return true;
            }
            else return false;
        }

        public void forEachRemaining(Consumer<? super SplittableRng> consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
		SplittableRng c = constructingRng;
                SplittableRng r = generatingRng;
                do {
                    consumer.accept(c.split(r));
                } while (++i < f);
            }
        }
    }

}
