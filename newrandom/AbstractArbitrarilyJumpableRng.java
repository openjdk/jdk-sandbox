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
 * This class provides much of the implementation of the
 * {@code ArbitrarilyJumpableRng} interface, to minimize the effort
 * required to implement that interface.
 *
 * To implement a pseudorandom number generator, the programmer needs
 * only to extend this class and provide implementations for the
 * methods {@code nextInt()}, {@code nextLong()}, {@code copy()},
 * {@code jump(distance)}, {@code jumpPowerOfTwo(distance)},
 * {@code defaultJumpDistance()}, and {@code defaultLeapDistance()}.
 *
 * (If the pseudorandom number generator also has the ability to split,
 * then the programmer may wish to consider instead extending
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
 * allow parallel execution when appropriate.  In this respect
 * {@code ArbitrarilyJumpableRng} differs from {@code JumpableRng},
 * which provides very simple implementations that produce
 * sequential streams only.
 *
 * <p>An implementation of the {@code AbstractArbitrarilyJumpableRng} class
 * must provide concrete definitions for the methods {@code nextInt()},
 * {@code nextLong}, {@code period()}, {@code copy()}, {@code jump(double)},
 * {@code defaultJumpDistance()}, and {@code defaultLeapDistance()}.
 * Default implementations are provided for all other methods.
 *
 * The documentation for each non-abstract method in this class
 * describes its implementation in detail. Each of these methods may
 * be overridden if the pseudorandom number generator being
 * implemented admits a more efficient implementation.
 *
 * @author  Guy Steele
 * @since   1.9
 */
public abstract class AbstractArbitrarilyJumpableRng
    extends AbstractSpliteratorRng implements ArbitrarilyJumpableRng {

    /*
     * Implementation Overview.
     *
     * This class provides most of the "user API" methods needed to satisfy
     * the interface java.util.ArbitrarilyJumpableRng.  Most of these methods
     * are in turn inherited from AbstractRng and the non-public class
     * AbstractSpliteratorRng; this file implements four versions of the
     * jumps method and defines the spliterators necessary to support them.
     *
     * File organization: First the non-public methods needed by the class
     * AbstractSpliteratorRng, then the main public methods, followed by some
     * custom spliterator classes needed for stream methods.
     */

    // IllegalArgumentException messages
    static final String BadLogDistance  = "logDistance must be non-negative";

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

    // Similar methods used by this class
    Spliterator<Rng> makeJumpsSpliterator(long index, long fence, double distance) {
	return new RandomJumpsSpliterator(this, index, fence, distance);
    }
    Spliterator<JumpableRng> makeLeapsSpliterator(long index, long fence, double distance) {
	return new RandomLeapsSpliterator(this, index, fence, distance);
    }
    Spliterator<ArbitrarilyJumpableRng> makeArbitraryJumpsSpliterator(long index, long fence, double distance) {
	return new RandomArbitraryJumpsSpliterator(this, index, fence, distance);
    }

    /* ---------------- public methods ---------------- */

    /**
     * Returns a new generator whose internal state is an exact copy
     * of this generator (therefore their future behavior should be
     * identical if subjected to the same series of operations).
     *
     * @return a new object that is a copy of this generator
     */
    public abstract AbstractArbitrarilyJumpableRng copy();

    // Stream methods for jumping

    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code Rng}
     * interface, produced by jumping copies of this generator
     * by different integer multiples of the default jump distance.
     *
     * @implNote This method is implemented to be equivalent to
     * {@code jumps(Long.MAX_VALUE)}.
     *
     * @return a stream of objects that implement the {@code Rng} interface
     */
    public Stream<Rng> jumps() {
        return StreamSupport.stream
            (makeJumpsSpliterator(0L, Long.MAX_VALUE, defaultJumpDistance()),
             false);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code Rng} interface, produced by jumping copies of this generator
     * by different integer multiples of the default jump distance.
     *
     * @param streamSize the number of generators to generate
     * @return a stream of objects that implement the {@code Rng} interface
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    public Stream<Rng> jumps(long streamSize) {
        return StreamSupport.stream
            (makeJumpsSpliterator(0L, streamSize, defaultJumpDistance()),
             false);
    }

    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code Rng}
     * interface, produced by jumping copies of this generator
     * by different integer multiples of the specified jump distance.
     *
     * @implNote This method is implemented to be equivalent to
     * {@code jumps(Long.MAX_VALUE)}.
     *
     * @param distance a distance to jump forward within the state cycle
     * @return a stream of objects that implement the {@code Rng} interface
     */
    public Stream<ArbitrarilyJumpableRng> jumps(double distance) {
        return StreamSupport.stream
            (makeArbitraryJumpsSpliterator(0L, Long.MAX_VALUE, distance),
             false);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code Rng} interface, produced by jumping copies of this generator
     * by different integer multiples of the specified jump distance.
     *
     * @param streamSize the number of generators to generate
     * @param distance a distance to jump forward within the state cycle
     * @return a stream of objects that implement the {@code Rng} interface
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    public Stream<ArbitrarilyJumpableRng> jumps(long streamSize, double distance) {
	RngSupport.checkStreamSize(streamSize);
        return StreamSupport.stream
            (makeArbitraryJumpsSpliterator(0L, streamSize, distance),
             false);
    }

    /**
     * Alter the state of this pseudorandom number generator so as to
     * jump forward a very large, fixed distance (typically 2<sup>128</sup>
     * or more) within its state cycle.  The distance used is that
     * returned by method {@code defaultLeapDistance()}.
     */
    public void leap() { jump(defaultLeapDistance()); }

    // Stream methods for leaping

    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code Rng}
     * interface, produced by jumping copies of this generator
     * by different integer multiples of the default leap distance.
     *
     * @implNote This method is implemented to be equivalent to
     * {@code leaps(Long.MAX_VALUE)}.
     *
     * @return a stream of objects that implement the {@code Rng} interface
     */
    public Stream<JumpableRng> leaps() {
        return StreamSupport.stream
            (makeLeapsSpliterator(0L, Long.MAX_VALUE, defaultLeapDistance()),
             false);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code Rng} interface, produced by jumping copies of this generator
     * by different integer multiples of the default leap distance.
     *
     * @param streamSize the number of generators to generate
     * @return a stream of objects that implement the {@code Rng} interface
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    public Stream<JumpableRng> leaps(long streamSize) {
        return StreamSupport.stream
            (makeLeapsSpliterator(0L, streamSize, defaultLeapDistance()),
             false);
    }

    
    /**
     * Spliterator for int streams.  We multiplex the four int
     * versions into one class by treating a bound less than origin as
     * unbounded, and also by treating "infinite" as equivalent to
     * Long.MAX_VALUE. For splits, we choose to override the method
     * {@code trySplit()} to try to optimize execution speed: instead of
     * dividing a range in half, it breaks off the largest possible chunk
     * whose size is a power of two such that the remaining chunk is not
     * empty.  In this way, the necessary jump distances will tend to be
     * powers of two.  The long and double versions of this class are
     * identical except for types.
     */
    static class RandomIntsSpliterator extends RngSupport.RandomSpliterator implements Spliterator.OfInt {
	final ArbitrarilyJumpableRng generatingRng;
        final int origin;
        final int bound;

        RandomIntsSpliterator(ArbitrarilyJumpableRng generatingRng, long index, long fence, int origin, int bound) {
	    super(index, fence);
	    this.origin = origin; this.bound = bound;
	    this.generatingRng = generatingRng;
        }
	
        public Spliterator.OfInt trySplit() {
            long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
	    if (m <= i) return null;
	    index = m;
	    ArbitrarilyJumpableRng r = (ArbitrarilyJumpableRng) generatingRng;
	    return new RandomIntsSpliterator(r.copyAndJump((double)delta), i, m, origin, bound);
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
                ArbitrarilyJumpableRng r = generatingRng;
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
	final ArbitrarilyJumpableRng generatingRng;
        final long origin;
        final long bound;

        RandomLongsSpliterator(ArbitrarilyJumpableRng generatingRng, long index, long fence, long origin, long bound) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
	    this.origin = origin; this.bound = bound;
        }
	
        public Spliterator.OfLong trySplit() {
            long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
	    if (m <= i) return null;
	    index = m;
	    ArbitrarilyJumpableRng r = (ArbitrarilyJumpableRng) generatingRng;
	    return new RandomLongsSpliterator(r.copyAndJump((double)delta), i, m, origin, bound);
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
                ArbitrarilyJumpableRng r = generatingRng;
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
	final ArbitrarilyJumpableRng generatingRng;
        final double origin;
        final double bound;

        RandomDoublesSpliterator(ArbitrarilyJumpableRng generatingRng, long index, long fence, double origin, double bound) {
	    super(index, fence);
	    this.generatingRng = generatingRng;
	    this.origin = origin; this.bound = bound;
        }
	
        public Spliterator.OfDouble trySplit() {

            long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
	    if (m <= i) return null;
	    index = m;
	    ArbitrarilyJumpableRng r = (ArbitrarilyJumpableRng) generatingRng;
	    return new RandomDoublesSpliterator(r.copyAndJump((double)delta), i, m, origin, bound);
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
                ArbitrarilyJumpableRng r = generatingRng;
                double o = origin, b = bound;
                do {
                    consumer.accept(RngSupport.boundedNextDouble(r, o, b));
                } while (++i < f);
            }
        }
    }
    
    // Spliterators for producing new generators by jumping or leaping.  The
    // complete implementation of each of these spliterators is right here.
    // In the same manner as for the preceding spliterators, the method trySplit() is
    // coded to optimize execution speed: instead of dividing a range
    // in half, it breaks off the largest possible chunk whose
    // size is a power of two such that the remaining chunk is not
    // empty.  In this way, the necessary jump distances will tend to be
    // powers of two.

    /**
     * Spliterator for stream of generators of type Rng produced by jumps.
     */
    static class RandomJumpsSpliterator extends RngSupport.RandomSpliterator implements Spliterator<Rng> {
	ArbitrarilyJumpableRng generatingRng;
	final double distance;

        RandomJumpsSpliterator(ArbitrarilyJumpableRng generatingRng, long index, long fence, double distance) {
            super(index, fence);
            this.generatingRng = generatingRng; this.distance = distance;
        }

        public Spliterator<Rng> trySplit() {
	    long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
	    if (m <= i) return null;
	    index = m;
	    ArbitrarilyJumpableRng r = (ArbitrarilyJumpableRng) generatingRng;
	    // Because delta is a power of two, (distance * (double)delta) can always be computed exactly.
	    return new RandomJumpsSpliterator(r.copyAndJump(distance * (double)delta), i, m, distance);
        }

        public boolean tryAdvance(Consumer<? super Rng> consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
		consumer.accept(generatingRng.copyAndJump(distance));
                index = i + 1;
                return true;
            }
            return false;
        }

        public void forEachRemaining(Consumer<? super Rng> consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
		ArbitrarilyJumpableRng r = generatingRng;
                do {
                    consumer.accept(r.copyAndJump(distance));
                } while (++i < f);
            }
        }
    }
    
    /**
     * Spliterator for stream of generators of type Rng produced by leaps.
     */
    static class RandomLeapsSpliterator extends RngSupport.RandomSpliterator implements Spliterator<JumpableRng> {
	ArbitrarilyJumpableRng generatingRng;
	final double distance;

        RandomLeapsSpliterator(ArbitrarilyJumpableRng generatingRng, long index, long fence, double distance) {
            super(index, fence);
            this.generatingRng = generatingRng; this.distance = distance;
        }

        public Spliterator<JumpableRng> trySplit() {
	    long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
	    if (m <= i) return null;
	    index = m;
	    // Because delta is a power of two, (distance * (double)delta) can always be computed exactly.
	    return new RandomLeapsSpliterator(generatingRng.copyAndJump(distance * (double)delta), i, m, distance);
        }

        public boolean tryAdvance(Consumer<? super JumpableRng> consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
		consumer.accept(generatingRng.copyAndJump(distance));
                index = i + 1;
                return true;
            }
            return false;
        }

        public void forEachRemaining(Consumer<? super JumpableRng> consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
                ArbitrarilyJumpableRng r = generatingRng;
                do {
                    consumer.accept(r.copyAndJump(distance));
                } while (++i < f);
            }
        }
    }

    /**
     * Spliterator for stream of generators of type Rng produced by arbitrary jumps.
     */
    static class RandomArbitraryJumpsSpliterator extends RngSupport.RandomSpliterator implements Spliterator<ArbitrarilyJumpableRng> {
	ArbitrarilyJumpableRng generatingRng;
	final double distance;

        RandomArbitraryJumpsSpliterator(ArbitrarilyJumpableRng generatingRng, long index, long fence, double distance) {
            super(index, fence);
            this.generatingRng = generatingRng; this.distance = distance;
        }

        public Spliterator<ArbitrarilyJumpableRng> trySplit() {
	    long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
	    if (m <= i) return null;
	    index = m;
	    // Because delta is a power of two, (distance * (double)delta) can always be computed exactly.
	    return new RandomArbitraryJumpsSpliterator(generatingRng.copyAndJump(distance * (double)delta), i, m, distance);
        }

        public boolean tryAdvance(Consumer<? super ArbitrarilyJumpableRng> consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
		consumer.accept(generatingRng.copyAndJump(distance));
                index = i + 1;
                return true;
            }
            return false;
        }

        public void forEachRemaining(Consumer<? super ArbitrarilyJumpableRng> consumer) {
            if (consumer == null) throw new NullPointerException();
            long i = index, f = fence;
            if (i < f) {
                index = f;
		ArbitrarilyJumpableRng r = generatingRng;
                do {
                    consumer.accept(r.copyAndJump(distance));
                } while (++i < f);
            }
        }
    }

}
