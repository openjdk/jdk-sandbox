/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
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
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.DoubleStream;

/**
 * This class overrides the stream-producing methods (such as {@code ints()})
 * in class {@code AbstractRng} to provide {@code Spliterator}-based
 * implmentations that support potentially parallel execution.
 *
 * To implement a pseudorandom number generator, the programmer needs
 * only to extend this class and provide implementations for the methods
 * {@code nextInt()}, {@code nextLong()}, {@code makeIntsSpliterator},
 * {@code makeLongsSpliterator}, and {@code makeDoublesSpliterator}.
 *
 * This class is not public; it provides shared code to the public
 * classes {@code AbstractSplittableRng}, {@code AbstractSharedRng},
 * and {@code AbstractArbitrarilyJumpableRng}.
 *
 * @author  Guy Steele
 * @author  Doug Lea
 * @since   1.9
 */

abstract class AbstractSpliteratorRng implements Rng {
    /*
     * Implementation Overview.
     *
     * This class provides most of the "user API" methods needed to
     * satisfy the interface java.util.Rng.  An implementation of this
     * interface need only extend this class and provide implementations
     * of six methods: nextInt, nextLong, and nextDouble (the versions
     * that take no arguments) and makeIntsSpliterator,
     * makeLongsSpliterator, and makeDoublesSpliterator.
     *
     * File organization: First the non-public abstract methods needed
     * to create spliterators, then the main public methods.
     */

    abstract Spliterator.OfInt makeIntsSpliterator(long index, long fence, int origin, int bound);
    abstract Spliterator.OfLong makeLongsSpliterator(long index, long fence, long origin, long bound);
    abstract Spliterator.OfDouble makeDoublesSpliterator(long index, long fence, double origin, double bound);
	
    /* ---------------- public methods ---------------- */

    // stream methods, coded in a way intended to better isolate for
    // maintenance purposes the small differences across forms.

    /**
     * Returns a stream producing the given {@code streamSize} number
     * of pseudorandom {@code int} values from this generator and/or
     * one split from it.
     *
     * @param streamSize the number of values to generate
     * @return a stream of pseudorandom {@code int} values
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    public IntStream ints(long streamSize) {
	RngSupport.checkStreamSize(streamSize);
        return StreamSupport.intStream
            (makeIntsSpliterator(0L, streamSize, Integer.MAX_VALUE, 0),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandomly chosen
     * {@code int} values.
     *
     * @implNote The implementation of this method is effectively
     * equivalent to {@code ints(Long.MAX_VALUE)}.
     *
     * @return a stream of pseudorandomly chosen {@code int} values
     */
    
    public IntStream ints() {
        return StreamSupport.intStream
            (makeIntsSpliterator(0L, Long.MAX_VALUE, Integer.MAX_VALUE, 0),
             false);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number
     * of pseudorandom {@code int} values from this generator and/or one split
     * from it; each value conforms to the given origin (inclusive) and bound
     * (exclusive).
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the origin (inclusive) of each random value
     * @param randomNumberBound the bound (exclusive) of each random value
     * @return a stream of pseudorandom {@code int} values,
     *         each with the given origin (inclusive) and bound (exclusive)
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public IntStream ints(long streamSize, int randomNumberOrigin,
			   int randomNumberBound) {
	RngSupport.checkStreamSize(streamSize);
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return StreamSupport.intStream
            (makeIntsSpliterator(0L, streamSize, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * int} values from this generator and/or one split from it; each value
     * conforms to the given origin (inclusive) and bound (exclusive).
     *
     * @implNote This method is implemented to be equivalent to {@code
     * ints(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the origin (inclusive) of each random value
     * @param randomNumberBound the bound (exclusive) of each random value
     * @return a stream of pseudorandom {@code int} values,
     *         each with the given origin (inclusive) and bound (exclusive)
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return StreamSupport.intStream
            (makeIntsSpliterator(0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number
     * of pseudorandom {@code long} values from this generator and/or
     * one split from it.
     *
     * @param streamSize the number of values to generate
     * @return a stream of pseudorandom {@code long} values
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    public LongStream longs(long streamSize) {
	RngSupport.checkStreamSize(streamSize);
        return StreamSupport.longStream
            (makeLongsSpliterator(0L, streamSize, Long.MAX_VALUE, 0L),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * long} values from this generator and/or one split from it.
     *
     * @implNote This method is implemented to be equivalent to {@code
     * longs(Long.MAX_VALUE)}.
     *
     * @return a stream of pseudorandom {@code long} values
     */
    public LongStream longs() {
        return StreamSupport.longStream
            (makeLongsSpliterator(0L, Long.MAX_VALUE, Long.MAX_VALUE, 0L),
             false);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandom {@code long} values from this generator and/or one split
     * from it; each value conforms to the given origin (inclusive) and bound
     * (exclusive).
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the origin (inclusive) of each random value
     * @param randomNumberBound the bound (exclusive) of each random value
     * @return a stream of pseudorandom {@code long} values,
     *         each with the given origin (inclusive) and bound (exclusive)
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public LongStream longs(long streamSize, long randomNumberOrigin,
			     long randomNumberBound) {
	RngSupport.checkStreamSize(streamSize);
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return StreamSupport.longStream
            (makeLongsSpliterator(0L, streamSize, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * long} values from this generator and/or one split from it; each value
     * conforms to the given origin (inclusive) and bound (exclusive).
     *
     * @implNote This method is implemented to be equivalent to {@code
     * longs(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the origin (inclusive) of each random value
     * @param randomNumberBound the bound (exclusive) of each random value
     * @return a stream of pseudorandom {@code long} values,
     *         each with the given origin (inclusive) and bound (exclusive)
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return StreamSupport.longStream
            (makeLongsSpliterator(0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandom {@code double} values from this generator and/or one split
     * from it; each value is between zero (inclusive) and one (exclusive).
     *
     * @param streamSize the number of values to generate
     * @return a stream of {@code double} values
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    public DoubleStream doubles(long streamSize) {
	RngSupport.checkStreamSize(streamSize);
        return StreamSupport.doubleStream
            (makeDoublesSpliterator(0L, streamSize, Double.MAX_VALUE, 0.0),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * double} values from this generator and/or one split from it; each value
     * is between zero (inclusive) and one (exclusive).
     *
     * @implNote This method is implemented to be equivalent to {@code
     * doubles(Long.MAX_VALUE)}.
     *
     * @return a stream of pseudorandom {@code double} values
     */
    public DoubleStream doubles() {
        return StreamSupport.doubleStream
            (makeDoublesSpliterator(0L, Long.MAX_VALUE, Double.MAX_VALUE, 0.0),
             false);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandom {@code double} values from this generator and/or one split
     * from it; each value conforms to the given origin (inclusive) and bound
     * (exclusive).
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the origin (inclusive) of each random value
     * @param randomNumberBound the bound (exclusive) of each random value
     * @return a stream of pseudorandom {@code double} values,
     *         each with the given origin (inclusive) and bound (exclusive)
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public DoubleStream doubles(long streamSize, double randomNumberOrigin,
				 double randomNumberBound) {
	RngSupport.checkStreamSize(streamSize);
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return StreamSupport.doubleStream
            (makeDoublesSpliterator(0L, streamSize, randomNumberOrigin, randomNumberBound),
             false);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandom {@code
     * double} values from this generator and/or one split from it; each value
     * conforms to the given origin (inclusive) and bound (exclusive).
     *
     * @implNote This method is implemented to be equivalent to {@code
     * doubles(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the origin (inclusive) of each random value
     * @param randomNumberBound the bound (exclusive) of each random value
     * @return a stream of pseudorandom {@code double} values,
     *         each with the given origin (inclusive) and bound (exclusive)
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return StreamSupport.doubleStream
            (makeDoublesSpliterator(0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound),
             false);
    }

}
