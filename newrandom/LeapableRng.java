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
import java.util.stream.Stream;

/**
 * This interface is designed to provide a common protocol for objects
 * that generate sequences of pseudorandom numbers (or Boolean values)
 * and furthermore can easily not only jump but also <it>leap</it> to
 * a very distant point in the state cycle.
 *
 * Typically one will construct a series of {@code LeapableRng} objects
 * by iterative leaping from a single original {@code LeapableRng}
 * object, and then for each such object produce a subseries of objects
 * by iterative jumping.  There is little conceptual difference between
 * leaping and jumping, but typically a leap will be a very long jump
 * in the state cycle (perhaps distance 2<sup>128</sup> or so).
 *
 * <p>Ideally, all {@code LeapableRng} objects produced by iterative
 * leaping and jumping from a single original {@code LeapableRng} object
 * are statistically independent of one another and individually uniform.
 * In practice, one must settle for some approximation to independence
 * and uniformity.  In particular, a specific implementation may
 * assume that each generator in a stream produced by the {@code leaps}
 * method is used to produce (by jumping) a number of objects no larger
 * than 2<sup>64</sup>.  Implementors are advised to use algorithms
 * whose period is at least 2<sup>191</sup>.
 *
 * <p>Methods are provided to perform a single leap operation and also
 * to produce a stream of generators produced from the original by
 * iterative copying and leaping of internal state.  The generators
 * produced must implement the {@code JumpableRng} interface but need
 * not also implement the {@code LeapableRng} interface.  A typical
 * strategy for a multithreaded application is to create a single
 * {@code LeapableRng} object, calls its {@code leaps} method exactly
 * once, and then parcel out generators from the resulting stream, one
 * to each thread.  Then the {@code jumps} method of each such generator
 * be called to produce a substream of generator objects.
 *
 * <p>An implementation of the {@code LeapableRng} interface must provide
 * concrete definitions for the methods {@code nextInt()}, {@code nextLong},
 * {@code period()}, {@code copy()}, {@code jump()}, {@code defaultJumpDistance()},
 * {@code leap()}, and {@code defaultLeapDistance()}.
 * Default implementations are provided for all other methods.
 *
 * <p>Objects that implement {@code java.util.LeapableRng} are
 * typically not cryptographically secure.  Consider instead using
 * {@link java.security.SecureRandom} to get a cryptographically
 * secure pseudo-random number generator for use by
 * security-sensitive applications.
 *
 * @author  Guy Steele
 * @since   1.9
 */
interface LeapableRng extends JumpableRng {
    /**
     * Returns a new generator whose internal state is an exact copy
     * of this generator (therefore their future behavior should be
     * identical if subjected to the same series of operations).
     *
     * @return a new object that is a copy of this generator
     */
    LeapableRng copy();

    /**
     * Alter the state of this pseudorandom number generator so as to
     * leap forward a large, fixed distance (typically 2<sup>96</sup>
     * or more) within its state cycle.
     */
    void leap();
    
    /**
     * Returns the distance by which the {@code leap()} method will leap
     * forward within the state cycle of this generator object.
     *
     * @return the default leap distance (as a {@code double} value)
     */
    double defaultLeapDistance();

    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code JumpableRng}
     * interface.
     *
     * @implNote It is permitted to implement this method in a manner
     * equivalent to {@code leaps(Long.MAX_VALUE)}.
     *
     * @implNote The default implementation produces a sequential stream
     * that  repeatedly calls {@code copy()} and {@code leap()} on this generator,
     * and the copies become the generators produced by the stream.
     *
     * @return a stream of objects that implement the {@code JumpableRng} interface
     */
    default Stream<JumpableRng> leaps() {
	return Stream.generate(this::copyAndLeap).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code JumpableRng} interface.
     *
     * @implNote The default implementation produces a sequential stream
     * that  repeatedly calls {@code copy()} and {@code leap()} on this generator,
     * and the copies become the generators produced by the stream.
     *
     * @param streamSize the number of generators to generate
     * @return a stream of objects that implement the {@code JumpableRng} interface
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    default Stream<JumpableRng> leaps(long streamSize) {
        return leaps().limit(streamSize);
    }
        
    /**
     * Copy this generator, leap this generator forward, then return the copy.
     */
    default JumpableRng copyAndLeap() {
	JumpableRng result = copy();
	leap();
	return result;
    }

}
