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

import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * This interface is designed to provide a common protocol for objects
 * that generate sequences of pseudorandom numbers (or Boolean values)
 * and furthermore can easily <it>jump</it> to an arbitrarily specified
 * distant point in the state cycle.
 *
 * <p>Ideally, all {@code ArbitrarilyJumpableRng} objects produced by
 * iterative jumping from a single original {@code ArbtrarilyJumpableRng}
 * object are statistically independent of one another and
 * individually uniform, provided that they do not traverse
 * overlapping portions of the state cycle.  In practice, one must
 * settle for some approximation to independence and uniformity.  In
 * particular, a specific implementation may assume that each
 * generator in a stream produced by the {@code jumps} method is used
 * to produce a number of values no larger than the jump distance
 * specified.  Implementors are advised to use algorithms whose period
 * is at least 2<sup>127</sup>.
 *
 * <p>For many applications, it suffices to jump forward by a power of
 * two or some small multiple of a power of two, but this power of two
 * may not be representable as a {@code long} value.  To avoid the
 * use of {@code BigInteger} values as jump distances, {@code double}
 * values are used instead.
 *
 * <p>Methods are provided to perform a single jump operation and also
 * to produce a stream of generators produced from the original by
 * iterative copying and jumping of internal state.  A typical
 * strategy for a multithreaded application is to create a single
 * {@code ArbitrarilyJumpableRng} object, call its {@code jumps}
 * method exactly once, and then parcel out generators from the
 * resulting stream, one to each thread.  However, each generator
 * produced also has type {@code ArbitrarilyJumpableRng}; with care,
 * different jump distances can be used to traverse the entire
 * state cycle in various ways.
 *
 * <p>An implementation of the {@code ArbitrarilyJumpableRng} interface must
 * provide concrete definitions for the methods {@code nextInt()},
 * {@code nextLong}, {@code period()}, {@code copy()}, {@code jump(double)},
 * {@code defaultJumpDistance()}, and {@code defaultLeapDistance()}.
 * Default implementations are provided for all other methods.
 * Perhaps the most convenient
 * way to implement this interface is to extend the abstract class
 * {@link java.util.AbstractArbitrarilyJumpableRng}, which provides
 * spliterator-based implementations of the methods {@code ints}, {@code longs},
 * {@code doubles}, {@code rngs}, {@code jumps}, and {@code leaps}.
 *
 * <p>Objects that implement {@code java.util.ArbitrarilyJumpableRng}
 * are typically not cryptographically secure.  Consider instead using
 * {@link java.security.SecureRandom} to get a cryptographically
 * secure pseudo-random number generator for use by
 * security-sensitive applications.
 *
 * @author  Guy Steele
 * @since   1.9
 */
interface ArbitrarilyJumpableRng extends LeapableRng {
    /**
     * Returns a new generator whose internal state is an exact copy
     * of this generator (therefore their future behavior should be
     * identical if subjected to the same series of operations).
     *
     * @return a new object that is a copy of this generator
     */
    ArbitrarilyJumpableRng copy();

    /**
     * Alter the state of this pseudorandom number generator so as to
     * jump forward a distance equal to 2<sup>{@code logDistance}</sup>
     * within its state cycle.
     *
     * @param logDistance the base-2 logarithm of the distance to jump
     *        forward within the state cycle
     * @throws IllegalArgumentException if {@code logDistance} is NaN
     *         or negative, or if 2<sup>{@code logDistance}</sup> is
     *         greater than the period of this generator
     */
    void jumpPowerOfTwo(int logDistance);

    /**
     * Alter the state of this pseudorandom number generator so as to
     * jump forward a specified distance within its state cycle.
     *
     * @param distance the distance to jump forward within the state cycle
     * @throws IllegalArgumentException if {@code distance} is Nan,
     *         negative, or greater than the period of this generator
     */
    void jump(double distance);

    /**
     * Alter the state of this pseudorandom number generator so as to
     * jump forward a large, fixed distance (typically 2<sup>64</sup>
     * or more) within its state cycle.  The distance used is that
     * returned by method {@code defaultJumpDistance()}.
     */
    default void jump() { jump(defaultJumpDistance()); }
    
    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code ArbitrarilyJumpableRng}
     * interface, produced by jumping copies of this generator
     * by different integer multiples of the specified jump distance.
     *
     * @implNote This method is implemented to be equivalent to
     * {@code jumps(Long.MAX_VALUE)}.
     *
     * @param distance a distance to jump forward within the state cycle
     * @return a stream of objects that implement the {@code Rng} interface
     */
    default Stream<ArbitrarilyJumpableRng> jumps(double distance) {
	return Stream.generate(() -> copyAndJump(distance)).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code ArbitrarilyJumpableRng} interface, produced by jumping copies of this generator
     * by different integer multiples of the specified jump distance.
     *
     * @param streamSize the number of generators to generate
     * @param distance a distance to jump forward within the state cycle
     * @return a stream of objects that implement the {@code Rng} interface
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    default Stream<ArbitrarilyJumpableRng> jumps(long streamSize, double distance) {
        return jumps(distance).limit(streamSize);
    }

    /**
     * Alter the state of this pseudorandom number generator so as to
     * jump forward a very large, fixed distance (typically 2<sup>128</sup>
     * or more) within its state cycle.  The distance used is that
     * returned by method {@code defaultJLeapDistance()}.
     */
    default void leap() { jump(defaultLeapDistance()); }
     
    /**
     * Copy this generator, jump this generator forward, then return the copy.
     */
    default ArbitrarilyJumpableRng copyAndJump(double distance) {
	ArbitrarilyJumpableRng result = copy();
	jump(distance);
	return result;
    }

}
