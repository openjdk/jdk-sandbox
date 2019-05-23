/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
 * The {@code StreamableRng} interface augments the {@code Rng} interface
 * to provide methods that return streams of {@code Rng} objects.
 * Ideally, such a stream of objects would have the property that the
 * behavior of each object is statistically independent of all the others.
 * In practice, one may have to settle for some approximation to this property.
 *
 * A generator that implements interface {@link java.util.SplittableRng}
 * may choose to use its {@code splits} method to implement the {@code rngs}
 * method required by this interface.
 *
 * A generator that implements interface {@link java.util.JumpableRng}
 * may choose to use its {@code jumps} method to implement the {@code rngs}
 * method required by this interface.
 *
 * A generator that implements interface {@link java.util.LeapableRng}
 * may choose to use its {@code leaps} method to implement the {@code rngs}
 * method required by this interface.
 *
 * <p>An implementation of the {@code StreamableRng} interface must provide
 * concrete definitions for the methods {@code nextInt()}, {@code nextLong},
 * {@code period()}, and {@code rngs()}.
 * Default implementations are provided for all other methods.
 *
 * <p>Objects that implement {@code java.util.StreamableRng} are typically
 * not cryptographically secure.  Consider instead using
 * {@link java.security.SecureRandom} to get a cryptographically
 * secure pseudo-random number generator for use by
 * security-sensitive applications.
 *
 * @author  Guy Steele
 * @since   1.9
 */

import java.util.stream.Stream;

interface StreamableRng extends Rng {
    /**
     * Returns an effectively unlimited stream of objects, each of
     * which implements the {@code Rng} interface.  Ideally the
     * generators in the stream will appear to be statistically
     * independent.  The new generators should be of the same kind
     * as this generator.
     *
     * @implNote It is permitted to implement this method in a manner
     * equivalent to {@code rngs(Long.MAX_VALUE)}.
     *
     * @return a stream of objects that implement the {@code Rng} interface
     */
    Stream<Rng> rngs();

    /**
     * Returns an effectively unlimited stream of objects, each of
     * which implements the {@code Rng} interface.  Ideally the
     * generators in the stream will appear to be statistically
     * independent.  The new generators should be of the same kind
     * as this generator.
     *
     * @implNote The default implementation calls {@code rngs()} and
     * then limits its length to {@code streamSize}.
     *
     * @param streamSize the number of generators to generate
     * @return a stream of objects that implement the {@code Rng} interface
     * @throws IllegalArgumentException if {@code streamSize} is
     *         less than zero
     */
    default Stream<Rng> rngs(long streamSize) {
	RngSupport.checkStreamSize(streamSize);
        return rngs().limit(streamSize);
    }
}
