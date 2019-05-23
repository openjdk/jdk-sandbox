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
 * that generate pseudorandom sequences of numbers (or Boolean values)
 * and furthermore can easily <it>jump</it> forward (by a fixed amount)
 * to a distant point in the state cycle.
 *
 * <p>Ideally, all {@code JumpableRng} objects produced by iterative
 * jumping from a single original {@code JumpableRng} object are
 * statistically independent of one another and individually uniform.
 * In practice, one must settle for some approximation to independence
 * and uniformity.  In particular, a specific implementation may
 * assume that each generator in a stream produced by the {@code jumps}
 * method is used to produce a number of values no larger than either
 * 2<sup>64</sup> or the square root of its period.  Implementors are
 * advised to use algorithms whose period is at least 2<sup>127</sup>.
 *
 * <p>Methods are provided to perform a single jump operation and also
 * to produce a stream of generators produced from the original by
 * iterative copying and jumping of internal state.  A typical
 * strategy for a multithreaded application is to create a single
 * {@code JumpableRng} object, calls its {@code jumps} method exactly
 * once, and then parcel out generators from the resulting stream, one
 * to each thread.  It is generally not a good idea to call {@code jump}
 * on a generator that was itself produced by the {@code jumps} method,
 * because the result may be a generator identical to another
 * generator already produce by that call to the {@code jumps} method.
 * For this reason, the return type of the {@code jumps} method is
 * {@code Stream<Rng>} rather than {@code Stream<JumpableRng>}, even
 * though the actual generator objects in that stream likely do also
 * implement the {@code JumpableRng} interface.
 *
 * <p>An implementation of the {@code JumpableRng} interface must provide
 * concrete definitions for the methods {@code nextInt()}, {@code nextLong},
 * {@code period()}, {@code copy()}, {@code jump()}, and {@code defaultJumpDistance()}.
 * Default implementations are provided for all other methods.
 *
 * <p>Objects that implement {@code java.util.JumpableRng} are
 * typically not cryptographically secure.  Consider instead using
 * {@link java.security.SecureRandom} to get a cryptographically
 * secure pseudo-random number generator for use by
 * security-sensitive applications.
 *
 * @author  Guy Steele
 * @since   1.9
 */
interface JumpableRng extends StreamableRng {
    /**
     * Returns a new generator whose internal state is an exact copy
     * of this generator (therefore their future behavior should be
     * identical if subjected to the same series of operations).
     *
     * @return a new object that is a copy of this generator
     */
    JumpableRng copy();

    /**
     * Alter the state of this pseudorandom number generator so as to
     * jump forward a large, fixed distance (typically 2<sup>64</sup>
     * or more) within its state cycle.
     */
    void jump();
 
    /**
     * Returns the distance by which the {@code jump()} method will jump
     * forward within the state cycle of this generator object.
     *
     * @return the default jump distance (as a {@code double} value)
     */
    double defaultJumpDistance();

    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code Rng}
     * interface.
     *
     * @implNote It is permitted to implement this method in a manner
     * equivalent to {@code jumps(Long.MAX_VALUE)}.
     *
     * @implNote The default implementation produces a sequential stream
     * that  repeatedly calls {@code copy()} and {@code jump()} on this generator,
     * and the copies become the generators produced by the stream.
     *
     * @return a stream of objects that implement the {@code Rng} interface
     */
    default Stream<Rng> jumps() {
	return Stream.generate(this::copyAndJump).sequential();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code Rng} interface.
     *
     * @implNote The default implementation produces a sequential stream
     * that  repeatedly calls {@code copy()} and {@code jump()} on this generator,
     * and the copies become the generators produced by the stream.
     *
     * @param streamSize the number of generators to generate
     * @return a stream of objects that implement the {@code Rng} interface
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    default Stream<Rng> jumps(long streamSize) {
        return jumps().limit(streamSize);
    }
    
    /**
     * Returns an effectively unlimited stream of new pseudorandom
     * number generators, each of which implements the {@code Rng}
     * interface.  Ideally the generators in the stream will appear
     * to be statistically independent.
     *
     * @implNote The default implementation calls {@code jumps()}.
     *
     * @return a stream of objects that implement the {@code Rng} interface
     */
    default Stream<Rng> rngs() {
	return this.jumps();
    }

    /**
     * Returns a stream producing the given {@code streamSize} number of
     * new pseudorandom number generators, each of which implements the
     * {@code Rng} interface.  Ideally the generators in the stream will
     * appear to be statistically independent.
     *
     * @implNote The default implementation calls {@code jumps(streamSize)}.
     *
     * @param streamSize the number of generators to generate
     * @return a stream of objects that implement the {@code Rng} interface
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    default Stream<Rng> rngs(long streamSize) {
	return this.jumps(streamSize);
    }

    /**
     * Copy this generator, jump this generator forward, then return the copy.
     */
    default Rng copyAndJump() {
	Rng result = copy();
	jump();
	return result;
    }

}
