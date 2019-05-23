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

import java.math.BigInteger;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

/**
 * The {@code Rng} interface is designed to provide a common protocol
 * for objects that generate random or (more typically) pseudorandom
 * sequences of numbers (or Boolean values).  Such a sequence may be
 * obtained by either repeatedly invoking a method that returns a
 * single (pseudo)randomly chosen value, or by invoking a method that
 * returns a stream of (pseudo)randomly chosen values.
 *
 * <p>Ideally, given an implicitly or explicitly specified range of values,
 * each value would be chosen independently and uniformly from that range.
 * In practice, one may have to settle for some approximation to independence
 * and uniformity.
 *
 * <p>In the case of {@code int}, {@code long}, and {@code Boolean}
 * values, if there is no explicit specification of range, then the
 * range includes all possible values of the type.  In the case of
 * {@code float} and {@code double} values, a value is always chosen
 * from the set of 2<sup><it>w</it></sup> values between 0.0 (inclusive)
 * and 1.0 (exclusive), where <it>w</it> is 23 for {@code float}
 * values and 52 for {@code double} values, such that adjacent values
 * differ by 2<sup>&minus;<it>w</it></sup>; if an explicit range is
 * specified, then the chosen number is computationally scaled and
 * translated so as to appear to have been chosen from that range.
 *
 * <p>Each method that returns a stream produces a stream of values each of
 * which is chosen in the same manner as for a method that
 * returns a single (pseudo)randomly chosen value.  For example, if {@code r}
 * implements {@code Rng}, then the method call {@code r.ints(100)} returns
 * a stream of 100 {@code int} values.  These are not necessarily the exact
 * same values that would have been returned if instead {@code r.nextInt()}
 * had been called 100 times; all that is guaranteed is that each value in
 * the stream is chosen in a similar (pseudo)random manner from the same range.
 *
 * <p>Every object that implements the {@code Rng} interface is assumed
 * to contain a finite amount of state.  Using such an object to
 * generate a pseudorandomly chosen value alters its state.  The
 * number of distinct possible states of such an object is called its
 * <it>period</it>.  (Some implementations of the {@code Rng} interface
 * may be truly random rather than pseudorandom, for example relying
 * on the statistical behavior of a physical object to derive chosen
 * values.  Such implementations do not have a fixed period.)
 *
 * <p>As a rule, objects that implement the {@code Rng} interface need not
 * be thread-safe.  It is recommended that multithreaded applications
 * use either {@code ThreadLocalRandom} or (preferably) pseudorandom
 * number generators that implement the {@code SplittableRng} or
 * {@code JumpableRng} interface.

 * To implement this interface, a class only needs to provide concrete
 * definitions for the methods {@code nextLong()} and {@code period()}.
 * Default implementations are provided for all other methods
 * (but it may be desirable to override some of them, especially
 * {@code nextInt()} if the underlying algorithm is {@code int}-based).
 * Moerover, it may be preferable instead to implement another interface
 * such as {@link java.util.JumpableRng} or {@link java.util.LeapableRng},
 * or to extend an abstract class such as {@link java.util.AbstractSplittableRng}
 * or {@link java.util.AbstractArbitrarilyJumpableRng}.
 *
 * <p>Objects that implement {@code java.util.Rng} are typically
 * not cryptographically secure.  Consider instead using
 * {@link java.security.SecureRandom} to get a cryptographically
 * secure pseudorandom number generator for use by
 * security-sensitive applications.  Note, however, that
 * {@code java.security.SecureRandom} does implement the {@code Rng}
 * interface, so that instances of {@code java.security.SecureRandom}
 * may be used interchangeably with other types of pseudorandom
 * generators in applications that do not require a secure generator.
 *
 * @author  Guy Steele
 * @since   1.9
 */

interface Rng {

    /**
     * Returns an effectively unlimited stream of pseudorandomly chosen
     * {@code double} values.
     *
     * @implNote It is permitted to implement this method in a manner
     * equivalent to {@code doubles(Long.MAX_VALUE)}.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextDouble()}.
     *
     * @return a stream of pseudorandomly chosen {@code double} values
     */

    default DoubleStream doubles() {
        return DoubleStream.generate(this::nextDouble).sequential();
    }

    /**
     * Returns an effectively unlimited stream of pseudorandomly chosen
     * {@code double} values, where each value is between the specified
     * origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote It is permitted to implement this method in a manner
     *           equivalent to 
     * {@code doubles(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextDouble(randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     * @return a stream of pseudorandomly chosen {@code double} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    default DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return DoubleStream.generate(() -> nextDouble(randomNumberOrigin, randomNumberBound)).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandomly chosen {@code double} values.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextDouble()}.
     *
     * @param streamSize the number of values to generate
     * @return a stream of pseudorandomly chosen {@code double} values
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    default DoubleStream doubles(long streamSize) {
	RngSupport.checkStreamSize(streamSize);
	return doubles().limit(streamSize);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandomly chosen {@code double} values, where each value is between
     * the specified origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextDouble(randomNumberOrigin, randomNumberBound)}.
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     * @return a stream of pseudorandomly chosen {@code double} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    default DoubleStream doubles(long streamSize, double randomNumberOrigin,
			  double randomNumberBound) {
	RngSupport.checkStreamSize(streamSize);
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
	return doubles(randomNumberOrigin, randomNumberBound).limit(streamSize);
    }
   
    /**
     * Returns an effectively unlimited stream of pseudorandomly chosen
     * {@code int} values.
     *
     * @implNote It is permitted to implement this method in a manner
     * equivalent to {@code ints(Long.MAX_VALUE)}.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextInt()}.
     *
     * @return a stream of pseudorandomly chosen {@code int} values
     */

    default IntStream ints() {
        return IntStream.generate(this::nextInt).sequential();
    }

    /**
     * Returns an effectively unlimited stream of pseudorandomly chosen
     * {@code int} values, where each value is between the specified
     * origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote It is permitted to implement this method in a manner
     *           equivalent to 
     * {@code ints(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextInt(randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     * @return a stream of pseudorandomly chosen {@code int} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    default IntStream ints(int randomNumberOrigin, int randomNumberBound) {
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return IntStream.generate(() -> nextInt(randomNumberOrigin, randomNumberBound)).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandomly chosen {@code int} values.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextInt()}.
     *
     * @param streamSize the number of values to generate
     * @return a stream of pseudorandomly chosen {@code int} values
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    default IntStream ints(long streamSize) {
	RngSupport.checkStreamSize(streamSize);
	return ints().limit(streamSize);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandomly chosen {@code int} values, where each value is between
     * the specified origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextInt(randomNumberOrigin, randomNumberBound)}.
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     * @return a stream of pseudorandomly chosen {@code int} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    default IntStream ints(long streamSize, int randomNumberOrigin,
			  int randomNumberBound) {
	RngSupport.checkStreamSize(streamSize);
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
	return ints(randomNumberOrigin, randomNumberBound).limit(streamSize);
    }

    /**
     * Returns an effectively unlimited stream of pseudorandomly chosen
     * {@code long} values.
     *
     * @implNote It is permitted to implement this method in a manner
     * equivalent to {@code longs(Long.MAX_VALUE)}.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextLong()}.
     *
     * @return a stream of pseudorandomly chosen {@code long} values
     */

    default LongStream longs() {
        return LongStream.generate(this::nextLong).sequential();
    }

    /**
     * Returns an effectively unlimited stream of pseudorandomly chosen
     * {@code long} values, where each value is between the specified
     * origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote It is permitted to implement this method in a manner
     *           equivalent to 
     * {@code longs(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextLong(randomNumberOrigin, randomNumberBound)}.
     *
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     * @return a stream of pseudorandomly chosen {@code long} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     * @throws IllegalArgumentException if {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    default LongStream longs(long randomNumberOrigin, long randomNumberBound) {
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
        return LongStream.generate(() -> nextLong(randomNumberOrigin, randomNumberBound)).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandomly chosen {@code long} values.
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextLong()}.
     *
     * @param streamSize the number of values to generate
     * @return a stream of pseudorandomly chosen {@code long} values
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    default LongStream longs(long streamSize) {
	RngSupport.checkStreamSize(streamSize);
	return longs().limit(streamSize);
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * pseudorandomly chosen {@code long} values, where each value is between
     * the specified origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation produces a sequential stream
     * that repeatedly calls {@code nextLong(randomNumberOrigin, randomNumberBound)}.
     *
     * @param streamSize the number of values to generate
     * @param randomNumberOrigin the least value that can be produced
     * @param randomNumberBound the upper bound (exclusive) for each value produced
     * @return a stream of pseudorandomly chosen {@code long} values, each between
     *         the specified origin (inclusive) and the specified bound (exclusive)
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero, or {@code randomNumberOrigin}
     *         is greater than or equal to {@code randomNumberBound}
     */
    default LongStream longs(long streamSize, long randomNumberOrigin,
			  long randomNumberBound) {
	RngSupport.checkStreamSize(streamSize);
	RngSupport.checkRange(randomNumberOrigin, randomNumberBound);
	return longs(randomNumberOrigin, randomNumberBound).limit(streamSize);
    }

    /**
     * Returns a pseudorandomly chosen {@code boolean} value.
     *
     * <p>The default implementation tests the high-order bit (sign bit)
     * of a value produced by {@code nextInt()}, on the grounds
     * that some algorithms for pseudorandom number generation
     * produce values whose high-order bits have better
     * statistical quality than the low-order bits.
     *
     * @return a pseudorandomly chosen {@code boolean} value
     */
    default boolean nextBoolean() {
        return nextInt() < 0;
    }

    /**
     * Returns a pseudorandom {@code float} value between zero
     * (inclusive) and one (exclusive).
     *
     * The default implementation uses the 24 high-order bits
     * from a call to {@code nextInt()}.
     *
     * @return a pseudorandom {@code float} value between zero
     *         (inclusive) and one (exclusive)
     */
    default float nextFloat() {
        return (nextInt() >>> 8) * 0x1.0p-24f;
    }

    /**
     * Returns a pseudorandomly chosen {@code float} value between zero
     * (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation simply calls
     *     {@code RngSupport.checkBound(bound)} and then
     *     {@code RngSupport.boundedNextFloat(this, bound)}.
     *
     * @param bound the upper bound (exclusive) for the returned value.
     *        Must be positive and finite
     * @return a pseudorandomly chosen {@code float} value between
     *         zero (inclusive) and the bound (exclusive)
     * @throws IllegalArgumentException if {@code bound} is not
     *         positive and finite
     */
    default float nextFloat(float bound) {
	RngSupport.checkBound(bound);
	return RngSupport.boundedNextFloat(this, bound);
    }

    /**
     * Returns a pseudorandomly chosen {@code float} value between the
     * specified origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation simply calls
     *     {@code RngSupport.checkRange(origin, bound)} and then
     *     {@code RngSupport.boundedNextFloat(this, origin, bound)}.
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive)
     * @return a pseudorandomly chosen {@code float} value between the
     *         origin (inclusive) and the bound (exclusive)
     * @throws IllegalArgumentException unless {@code origin} is finite,
     *         {@code bound} is finite, and {@code origin} is less than
     *         {@code bound}
     */
    default float nextFloat(float origin, float bound) {
	RngSupport.checkRange(origin, bound);
        return RngSupport.boundedNextFloat(this, origin, bound);
    }

    /**
     * Returns a pseudorandom {@code double} value between zero
     * (inclusive) and one (exclusive).
     *
     * The default implementation uses the 53 high-order bits
     * from a call to {@code nextLong()}.
     *
     * @return a pseudorandom {@code double} value between zero
     *         (inclusive) and one (exclusive)
     */
    default double nextDouble() {
        return (nextLong() >>> 11) * 0x1.0p-53;
    }

    /**
     * Returns a pseudorandomly chosen {@code double} value between zero
     * (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation simply calls
     *     {@code RngSupport.checkBound(bound)} and then
     *     {@code RngSupport.boundedNextDouble(this, bound)}.
     *
     * @param bound the upper bound (exclusive) for the returned value.
     *        Must be positive and finite
     * @return a pseudorandomly chosen {@code double} value between
     *         zero (inclusive) and the bound (exclusive)
     * @throws IllegalArgumentException if {@code bound} is not
     *         positive and finite
     */
    default double nextDouble(double bound) {
	RngSupport.checkBound(bound);
	return RngSupport.boundedNextDouble(this, bound);
    }

    /**
     * Returns a pseudorandomly chosen {@code double} value between the
     * specified origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation simply calls
     *     {@code RngSupport.checkRange(origin, bound)} and then
     *     {@code RngSupport.boundedNextDouble(this, origin, bound)}.
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive) for the returned value
     * @return a pseudorandomly chosen {@code double} value between the
     *         origin (inclusive) and the bound (exclusive)
     * @throws IllegalArgumentException unless {@code origin} is finite,
     *         {@code bound} is finite, and {@code origin} is less than
     *         {@code bound}
     */
    default double nextDouble(double origin, double bound) {
	RngSupport.checkRange(origin, bound);
        return RngSupport.boundedNextDouble(this, origin, bound);
    }

    /**
     * Returns a pseudorandomly chosen {@code int} value.
     *
     * The default implementation uses the 32 high-order bits
     * from a call to {@code nextLong()}.
     *
     * @return a pseudorandomly chosen {@code int} value
     */
    default public int nextInt() {
	return (int)(nextLong() >>> 32);
    }

    /**
     * Returns a pseudorandomly chosen {@code int} value between
     * zero (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation simply calls
     *     {@code RngSupport.checkBound(bound)} and then
     *     {@code RngSupport.boundedNextInt(this, bound)}.
     *
     * @param bound the upper bound (exclusive) for the returned value.  Must be positive.
     * @return a pseudorandomly chosen {@code int} value between
     *         zero (inclusive) and the bound (exclusive)
     * @throws IllegalArgumentException if {@code bound} is not positive
     */
    default int nextInt(int bound) {
	RngSupport.checkBound(bound);
	return RngSupport.boundedNextInt(this, bound);
    }

    /**
     * Returns a pseudorandomly chosen {@code int} value between the
     * specified origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation simply calls
     *     {@code RngSupport.checkRange(origin, bound)} and then
     *     {@code RngSupport.boundedNextInt(this, origin, bound)}.
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive) for the returned value
     * @return a pseudorandomly chosen {@code int} value between the
     *         origin (inclusive) and the bound (exclusive)
     * @throws IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     */
    default int nextInt(int origin, int bound) {
	RngSupport.checkRange(origin, bound);
        return RngSupport.boundedNextInt(this, origin, bound);
    }

    /**
     * Returns a pseudorandomly chosen {@code long} value.
     *
     * @return a pseudorandomly chosen {@code long} value
     */
    long nextLong();

    /**
     * Returns a pseudorandomly chosen {@code long} value between
     * zero (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation simply calls
     *     {@code RngSupport.checkBound(bound)} and then
     *     {@code RngSupport.boundedNextLong(this, bound)}.
     *
     * @param bound the upper bound (exclusive) for the returned value.  Must be positive.
     * @return a pseudorandomly chosen {@code long} value between
     *         zero (inclusive) and the bound (exclusive)
     * @throws IllegalArgumentException if {@code bound} is not positive
     */
    default long nextLong(long bound) {
	RngSupport.checkBound(bound);
	return RngSupport.boundedNextLong(this, bound);
    }

    /**
     * Returns a pseudorandomly chosen {@code long} value between the
     * specified origin (inclusive) and the specified bound (exclusive).
     *
     * @implNote The default implementation simply calls
     *     {@code RngSupport.checkRange(origin, bound)} and then
     *     {@code RngSupport.boundedNextInt(this, origin, bound)}.
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive) for the returned value
     * @return a pseudorandomly chosen {@code long} value between the
     *         origin (inclusive) and the bound (exclusive)
     * @throws IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     */
    default long nextLong(long origin, long bound) {
	RngSupport.checkRange(origin, bound);
        return RngSupport.boundedNextLong(this, origin, bound);
    }
    
    /**
     * Returns a {@code double} value pseudorandomly chosen from
     * a Gaussian (normal) distribution whose mean is 0 and whose
     * standard deviation is 1.
     *
     * @return a {@code double} value pseudorandomly chosen from a
     *         Gaussian distribution
     */
    default double nextGaussian() {
	return RngSupport.computeNextGaussian(this);
    }

    /**
     * Returns a {@code double} value pseudorandomly chosen from
     * a Gaussian (normal) distribution with a mean and
     * standard deviation specified by the arguments.
     *
     * @param mean the mean of the Gaussian distribution to be drawn from
     * @param stddev the standard deviation of the Gaussian distribution to be drawn from
     * @return a {@code double} value pseudorandomly chosen from the
     *         specified Gaussian distribution
     */
    default double nextGaussian(double mean, double stddev) {
	return mean + RngSupport.computeNextGaussian(this) * stddev * stddev;
    }

    /**
     * Returns a nonnegative {@code double} value pseudorandomly chosen
     * from an exponential distribution whose mean is 1.
     *
     * @return a nonnegative {@code double} value pseudorandomly chosen from an
     *         exponential distribution
     */
    default double nextExponential() {
	return RngSupport.computeNextExponential(this);
    }

    
    /**
     * Returns the period of this {@code Rng} object.
     *
     * @return a {@code BigInteger} whose value is the number of
     *         distinct possible states of this {@code Rng} object,
     *         or 0 if unknown, or negative if extremely large.
     */
    BigInteger period();

    /**
     * The value (0) returned by the {@code period()} method if the period is unknown.
     */
    static final BigInteger UNKNOWN_PERIOD = BigInteger.ZERO;

    /**
     * The (negative) value returned by the {@code period()} method if this generator
     * has no period because it is truly random rather than just pseudorandom.
     */
    static final BigInteger TRULY_RANDOM = BigInteger.valueOf(-1);

    /**
     * The (negative) value that may be returned by the {@code period()} method
     * if this generator has a huge period (larger than 2**(2**16)).
     */
    static final BigInteger HUGE_PERIOD = BigInteger.valueOf(-2);
}
