/*
 * Copyright (c) 2013, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package java.util.random;

import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;
import java.util.random.RandomGenerator.SplittableGenerator;
import java.util.Spliterator;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.LongStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Low-level utility methods helpful for implementing pseudorandom number generators.
 *
 * This class is mostly for library writers creating specific implementations of the
 * interface {@link RandomGenerator}.
 *
 * @since 14
 */
public class RandomSupport {

    /*
     * Implementation Overview.
     *
     * This class provides utility methods and constants frequently
     * useful in the implementation of pseudorandom number generators
     * that satisfy the interface {@link RandomGenerator}.
     *
     * File organization: First some message strings, then the main
     * public methods, followed by a non-public base spliterator class.
     */

    // IllegalArgumentException messages
    static final String BAD_SIZE = "size must be non-negative";
    static final String BAD_DISTANCE = "jump distance must be finite, positive, and an exact integer";
    static final String BAD_BOUND = "bound must be positive";
    static final String BAD_FLOATING_BOUND = "bound must be finite and positive";
    static final String BAD_RANGE = "bound must be greater than origin";

    /* ---------------- public methods ---------------- */

    /**
     * Check a {@code long} proposed stream size for validity.
     *
     * @param streamSize the proposed stream size
     *
     * @throws IllegalArgumentException if {@code streamSize} is negative
     */
    public static void checkStreamSize(long streamSize) {
        if (streamSize < 0L)
            throw new IllegalArgumentException(BAD_SIZE);
    }

    /**
     * Check a {@code double} proposed jump distance for validity.
     *
     * @param distance the proposed jump distance
     *
     * @throws IllegalArgumentException if {@code size} fails to be positive, finite, and an exact integer
     */
    public static void checkJumpDistance(double distance) {
        if (!(distance > 0.0 && distance < Float.POSITIVE_INFINITY
                             && distance == Math.floor(distance))) {
            throw new IllegalArgumentException(BAD_DISTANCE);
        }
    }

    /**
     * Checks a {@code float} upper bound value for validity.
     *
     * @param bound the upper bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code bound} fails to be positive and finite
     */
    public static void checkBound(float bound) {
        if (!(bound > 0.0 && bound < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_FLOATING_BOUND);
        }
    }

    /**
     * Checks a {@code double} upper bound value for validity.
     *
     * @param bound the upper bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code bound} fails to be positive and finite
     */
    public static void checkBound(double bound) {
        if (!(bound > 0.0 && bound < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_FLOATING_BOUND);
        }
    }

    /**
     * Checks an {@code int} upper bound value for validity.
     *
     * @param bound the upper bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code bound} is not positive
     */
    public static void checkBound(int bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException(BAD_BOUND);
        }
    }

    /**
     * Checks a {@code long} upper bound value for validity.
     *
     * @param bound the upper bound (exclusive)
     *
     * @throws IllegalArgumentException if {@code bound} is not positive
     */
    public static void checkBound(long bound) {
        if (bound <= 0) {
            throw new IllegalArgumentException(BAD_BOUND);
        }
    }

    /**
     * Checks a {@code float} range for validity.
     *
     * @param origin the least value (inclusive) in the range
     * @param bound  the upper bound (exclusive) of the range
     *
     * @throws IllegalArgumentException if {@code origin} is not finite, {@code bound} is not finite,
     *                                  or {@code bound - origin} is not finite
     */
    public static void checkRange(float origin, float bound) {
        if (!(origin < bound && (bound - origin) < Float.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
    }

    /**
     * Checks a {@code double} range for validity.
     *
     * @param origin the least value (inclusive) in the range
     * @param bound  the upper bound (exclusive) of the range
     *
     * @throws IllegalArgumentException if {@code origin} is not finite, {@code bound} is not finite,
     *                                  or {@code bound - origin} is not finite
     */
    public static void checkRange(double origin, double bound) {
        if (!(origin < bound && (bound - origin) < Double.POSITIVE_INFINITY)) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
    }

    /**
     * Checks an {@code int} range for validity.
     *
     * @param origin the least value that can be returned
     * @param bound  the upper bound (exclusive) for the returned value
     *
     * @throws IllegalArgumentException if {@code origin} is greater than or equal to {@code bound}
     */
    public static void checkRange(int origin, int bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
    }

    /**
     * Checks a {@code long} range for validity.
     *
     * @param origin the least value that can be returned
     * @param bound  the upper bound (exclusive) for the returned value
     *
     * @throws IllegalArgumentException if {@code origin} is greater than or equal to {@code bound}
     */
    public static void checkRange(long origin, long bound) {
        if (origin >= bound) {
            throw new IllegalArgumentException(BAD_RANGE);
        }
    }

    /**
     * Given an array of seed bytes of any length, construct an array
     * of {@code long} seed values of length {@code n}, such that the
     * last {@code z} values are not all zero.
     *
     * @param seed an array of {@code byte} values
     * @param n the length of the result array (should be nonnegative)
     * @param z the number of trailing result elements that are required
     *        to be not all zero (should be nonnegative but not larger
     *        than {@code n})
     *
     * @return an array of length {@code n} containing {@code long} seed values
     */
    public static long[] convertSeedBytesToLongs(byte[] seed, int n, int z) {
        final long[] result = new long[n];
        final int m = Math.min(seed.length, n << 3);
        // Distribute seed bytes into the words to be formed.
        for (int j = 0; j < m; j++) {
            result[j>>3] = (result[j>>3] << 8) | seed[j];
        }
        // If there aren't enough seed bytes for all the words we need,
        // use a SplitMix-style PRNG to fill in the rest.
        long v = result[0];
        for (int j = (m + 7) >> 3; j < n; j++) {
            result[j] = mixMurmur64(v += SILVER_RATIO_64);
        }
        // Finally, we need to make sure the last z words are not all zero.
        search: {
            for (int j = n - z; j < n; j++) {
                if (result[j] != 0) break search;
            }
            // If they are, fill in using a SplitMix-style PRNG.
            // Using "& ~1L" in the next line defends against the case z==1
            // by guaranteeing that the first generated value will be nonzero.
            long w = result[0] & ~1L;
            for (int j = n - z; j < n; j++) {
                result[j] = mixMurmur64(w += SILVER_RATIO_64);
            }
        }
        return result;
    }

    /**
     * Given an array of seed bytes of any length, construct an array
     * of {@code int} seed values of length {@code n}, such that the
     * last {@code z} values are not all zero.
     *
     * @param seed an array of {@code byte} values
     * @param n the length of the result array (should be nonnegative)
     * @param z the number of trailing result elements that are required
     *        to be not all zero (should be nonnegative but not larger
     *        than {@code n})
     *
     * @return an array of length {@code n} containing {@code int} seed values
     */
    public static int[] convertSeedBytesToInts(byte[] seed, int n, int z) {
        final int[] result = new int[n];
        final int m = Math.min(seed.length, n << 2);
        // Distribute seed bytes into the words to be formed.
        for (int j = 0; j < m; j++) {
            result[j>>2] = (result[j>>2] << 8) | seed[j];
        }
        // If there aren't enough seed bytes for all the words we need,
        // use a SplitMix-style PRNG to fill in the rest.
        int v = result[0];
        for (int j = (m + 3) >> 2; j < n; j++) {
            result[j] = mixMurmur32(v += SILVER_RATIO_32);
        }
        // Finally, we need to make sure the last z words are not all zero.
        search: {
            for (int j = n - z; j < n; j++) {
                if (result[j] != 0) break search;
            }
            // If they are, fill in using a SplitMix-style PRNG.
            // Using "& ~1" in the next line defends against the case z==1
            // by guaranteeing that the first generated value will be nonzero.
            int w = result[0] & ~1;
            for (int j = n - z; j < n; j++) {
                result[j] = mixMurmur32(w += SILVER_RATIO_32);
            }
        }
        return result;
    }

    /*
     * Bounded versions of nextX methods used by streams, as well as
     * the public nextX(origin, bound) methods.  These exist mainly to
     * avoid the need for multiple versions of stream spliterators
     * across the different exported forms of streams.
     */

    /**
     * This is the form of {@code nextLong} used by a {@link LongStream}
     * {@link Spliterator} and by the public method
     * {@code nextLong(origin, bound)}.  If {@code origin} is greater
     * than {@code bound}, then this method simply calls the unbounded
     * version of {@code nextLong()}, choosing pseudorandomly from
     * among all 2<sup>64</sup> possible {@code long} values}, and
     * otherwise uses one or more calls to {@code nextLong()} to
     * choose a value pseudorandomly from the possible values
     * between {@code origin} (inclusive) and {@code bound} (exclusive).
     *
     * @implNote This method first calls {@code nextLong()} to obtain
     * a {@code long} value that is assumed to be pseudorandomly
     * chosen uniformly and independently from the 2<sup>64</sup>
     * possible {@code long} values (that is, each of the 2<sup>64</sup>
     * possible long values is equally likely to be chosen).
     * Under some circumstances (when the specified range is not
     * a power of 2), {@code nextLong()} may be called additional times
     * to ensure that that the values in the specified range are
     * equally likely to be chosen (provided the assumption holds).
     * <p>
     * The implementation considers four cases:
     * <ol>
     *
     * <li> If the {@code} bound} is less than or equal to the {@code origin}
     *      (indicated an unbounded form), the 64-bit {@code long} value
     *      obtained from {@code nextLong()} is returned directly.
     *
     * <li> Otherwise, if the length <i>n</i> of the specified range is an
     *      exact power of two 2<sup><i>m</i></sup> for some integer
     *      <i>m</i>, then return the sum of {@code origin} and the
     *      <i>m</i> lowest-order bits of the value from {@code nextLong()}.
     *
     * <li> Otherwise, if the length <i>n</i> of the specified range
     *      is less than 2<sup>63</sup>, then the basic idea is to use the
     *      remainder modulo <i>n</i> of the value from {@code nextLong()},
     *      but with this approach some values will be over-represented.
     *      Therefore a loop is used to avoid potential bias by rejecting
     *      candidates that are too large.  Assuming that the results from
     *      {@code nextLong()} are truly chosen uniformly and independently,
     *      the expected number of iterations will be somewhere between
     *      1 and 2, depending on the precise value of <i>n</i>.
     *
     * <li> Otherwise, the length <i>n</i> of the specified range
     *      cannot be represented as a positive {@code long} value.
     *      A loop repeatedly calls {@code nextlong()} until obtaining
     *      a suitable candidate,  Again, the expected number of iterations
     *      is less than 2.
     *
     * </ol>
     *
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code long} values
     * @param origin the least value that can be produced,
     *        unless greater than or equal to {@code bound}
     * @param bound the upper bound (exclusive), unless {@code origin}
     *        is greater than or equal to {@code bound}
     *
     * @return a pseudorandomly chosen {@code long} value,
     *         which will be between {@code origin} (inclusive) and
     *         {@code bound} exclusive unless {@code origin}
     *         is greater than or equal to {@code bound}
     */
    public static long boundedNextLong(RandomGenerator rng, long origin, long bound) {
        long r = rng.nextLong();
        if (origin < bound) {
            // It's not case (1).
            final long n = bound - origin;
            final long m = n - 1;
            if ((n & m) == 0L) {
                // It is case (2): length of range is a power of 2.
                r = (r & m) + origin;
            } else if (n > 0L) {
                // It is case (3): need to reject over-represented candidates.
                /* This loop takes an unlovable form (but it works):
                   because the first candidate is already available,
                   we need a break-in-the-middle construction,
                   which is concisely but cryptically performed
                   within the while-condition of a body-less for loop. */
                for (long u = r >>> 1;            // ensure nonnegative
                     u + m - (r = u % n) < 0L;    // rejection check
                     u = rng.nextLong() >>> 1) // retry
                    ;
                r += origin;
            }
            else {
                // It is case (4): length of range not representable as long.
                while (r < origin || r >= bound)
                    r = rng.nextLong();
            }
        }
        return r;
    }

    /**
     * This is the form of {@code nextLong} used by the public method
     * {@code nextLong(bound)}.  This is essentially a version of
     * {@code boundedNextLong(origin, bound)} that has been
     * specialized for the case where the {@code origin} is zero
     * and the {@code bound} is greater than zero.  The value
     * returned is chosen pseudorandomly from nonnegative integer
     * values less than {@code bound}.
     *
     * @implNote This method first calls {@code nextLong()} to obtain
     * a {@code long} value that is assumed to be pseudorandomly
     * chosen uniformly and independently from the 2<sup>64</sup>
     * possible {@code long} values (that is, each of the 2<sup>64</sup>
     * possible long values is equally likely to be chosen).
     * Under some circumstances (when the specified range is not
     * a power of 2), {@code nextLong()} may be called additional times
     * to ensure that that the values in the specified range are
     * equally likely to be chosen (provided the assumption holds).
     * <p>
     * The implementation considers two cases:
     * <ol>
     *
     * <li> If {@code bound} is an exact power of two 2<sup><i>m</i></sup>
     *      for some integer <i>m</i>, then return the sum of {@code origin}
     *      and the <i>m</i> lowest-order bits of the value from
     *      {@code nextLong()}.
     *
     * <li> Otherwise, the basic idea is to use the remainder modulo
     *      <i>bound</i> of the value from {@code nextLong()},
     *      but with this approach some values will be over-represented.
     *      Therefore a loop is used to avoid potential bias by rejecting
     *      candidates that vare too large.  Assuming that the results from
     *      {@code nextLong()} are truly chosen uniformly and independently,
     *      the expected number of iterations will be somewhere between
     *      1 and 2, depending on the precise value of <i>bound</i>.
     *
     * </ol>
     *
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code long} values
     * @param bound the upper bound (exclusive); must be greater than zero
     *
     * @return a pseudorandomly chosen {@code long} value
     */
    public static long boundedNextLong(RandomGenerator rng, long bound) {
        // Specialize boundedNextLong for origin == 0, bound > 0
        final long m = bound - 1;
        long r = rng.nextLong();
        if ((bound & m) == 0L) {
            // The bound is a power of 2.
            r &= m;
        } else {
            // Must reject over-represented candidates
            /* This loop takes an unlovable form (but it works):
               because the first candidate is already available,
               we need a break-in-the-middle construction,
               which is concisely but cryptically performed
               within the while-condition of a body-less for loop. */
            for (long u = r >>> 1;
                 u + m - (r = u % bound) < 0L;
                 u = rng.nextLong() >>> 1)
                ;
        }
        return r;
    }

    /**
     * This is the form of {@code nextInt} used by an {@link IntStream}
     * {@link Spliterator} and by the public method
     * {@code nextInt(origin, bound)}.  If {@code origin} is greater
     * than {@code bound}, then this method simply calls the unbounded
     * version of {@code nextInt()}, choosing pseudorandomly from
     * among all 2<sup>64</sup> possible {@code int} values}, and
     * otherwise uses one or more calls to {@code nextInt()} to
     * choose a value pseudorandomly from the possible values
     * between {@code origin} (inclusive) and {@code bound} (exclusive).
     *
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code int} values
     * @param origin the least value that can be produced,
     *        unless greater than or equal to {@code bound}
     * @param bound the upper bound (exclusive), unless {@code origin}
     *        is greater than or equal to {@code bound}
     *
     * @return a pseudorandomly chosen {@code int} value,
     *         which will be between {@code origin} (inclusive) and
     *         {@code bound} exclusive unless {@code origin}
     *         is greater than or equal to {@code bound}
     *
     * @implNote The implementation of this method is identical to
     *           the implementation of {@code nextLong(origin, bound)}
     *           except that {@code int} values and the {@code nextInt()}
     *           method are used rather than {@code long} values and the
     *           {@code nextLong()} method.
     */
    public static int boundedNextInt(RandomGenerator rng, int origin, int bound) {
        int r = rng.nextInt();
        if (origin < bound) {
            // It's not case (1).
            final int n = bound - origin;
            final int m = n - 1;
            if ((n & m) == 0) {
                // It is case (2): length of range is a power of 2.
                r = (r & m) + origin;
            } else if (n > 0) {
                // It is case (3): need to reject over-represented candidates.
                for (int u = r >>> 1;
                     u + m - (r = u % n) < 0;
                     u = rng.nextInt() >>> 1)
                    ;
                r += origin;
            }
            else {
                // It is case (4): length of range not representable as long.
                while (r < origin || r >= bound) {
                    r = rng.nextInt();
                }
            }
        }
        return r;
    }

    /**
     * This is the form of {@code nextInt} used by the public method
     * {@code nextInt(bound)}.  This is essentially a version of
     * {@code boundedNextInt(origin, bound)} that has been
     * specialized for the case where the {@code origin} is zero
     * and the {@code bound} is greater than zero.  The value
     * returned is chosen pseudorandomly from nonnegative integer
     * values less than {@code bound}.
     *
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code long} values
     * @param bound the upper bound (exclusive); must be greater than zero
     *
     * @return a pseudorandomly chosen {@code long} value
     *
     * @implNote The implementation of this method is identical to
     *           the implementation of {@code nextLong(bound)}
     *           except that {@code int} values and the {@code nextInt()}
     *           method are used rather than {@code long} values and the
     *           {@code nextLong()} method.
     */
    public static int boundedNextInt(RandomGenerator rng, int bound) {
        // Specialize boundedNextInt for origin == 0, bound > 0
        final int m = bound - 1;
        int r = rng.nextInt();
        if ((bound & m) == 0) {
            // The bound is a power of 2.
            r &= m;
        } else {
            // Must reject over-represented candidates
            for (int u = r >>> 1;
                 u + m - (r = u % bound) < 0;
                 u = rng.nextInt() >>> 1)
                ;
        }
        return r;
    }

    /**
     * This is the form of {@code nextDouble} used by a {@link DoubleStream}
     * {@link Spliterator} and by the public method
     * {@code nextDouble(origin, bound)}.  If {@code origin} is greater
     * than {@code bound}, then this method simply calls the unbounded
     * version of {@code nextDouble()}, and otherwise scales and translates
     * the result of a call to {@code nextDouble()} so that it lies
     * between {@code origin} (inclusive) and {@code bound} (exclusive).
     *
     * @implNote The implementation considers two cases:
     * <ol>
     *
     * <li> If the {@code bound} is less than or equal to the {@code origin}
     *      (indicated an unbounded form), the 64-bit {@code double} value
     *      obtained from {@code nextDouble()} is returned directly.
     *
     * <li> Otherwise, the result of a call to {@code nextDouble} is
     *      multiplied by {@code (bound - origin)}, then {@code origin}
     *      is added, and then if this this result is not less than
     *      {@code bound} (which can sometimes occur because of rounding),
     *      it is replaced with the largest {@code double} value that
     *      is less than {@code bound}.
     *
     * </ol>
     *
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code double} values
     * @param origin the least value that can be produced,
     *        unless greater than or equal to {@code bound}; must be finite
     * @param bound the upper bound (exclusive), unless {@code origin}
     *        is greater than or equal to {@code bound}; must be finite
     * @return a pseudorandomly chosen {@code double} value,
     *         which will be between {@code origin} (inclusive) and
     *         {@code bound} exclusive unless {@code origin}
     *         is greater than or equal to {@code bound},
     *         in which case it will be between 0.0 (inclusive)
     *         and 1.0 (exclusive)
     */
    public static double boundedNextDouble(RandomGenerator rng, double origin, double bound) {
        double r = rng.nextDouble();
        if (origin < bound) {
            r = r * (bound - origin) + origin;
            if (r >= bound)  // may need to correct a rounding problem
                r = Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
        }
        return r;
    }

    /**
     * This is the form of {@code nextDouble} used by the public method
     * {@code nextDouble(bound)}.  This is essentially a version of
     * {@code boundedNextDouble(origin, bound)} that has been
     * specialized for the case where the {@code origin} is zero
     * and the {@code bound} is greater than zero.
     *
     * @implNote The result of a call to {@code nextDouble} is
     *      multiplied by {@code bound}, and then if this result is
     *      not less than {@code bound} (which can sometimes occur
     *      because of rounding), it is replaced with the largest
     *      {@code double} value that is less than {@code bound}.
     *
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code double} values
     * @param bound the upper bound (exclusive); must be finite and
     *        greater than zero
     * @return a pseudorandomly chosen {@code double} value
     *         between zero (inclusive) and {@code bound} (exclusive)
     */
    public static double boundedNextDouble(RandomGenerator rng, double bound) {
        // Specialize boundedNextDouble for origin == 0, bound > 0
        double r = rng.nextDouble();
        r = r * bound;
        if (r >= bound)  // may need to correct a rounding problem
            r = Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
        return r;
    }

    /**
     * This is the form of {@code nextFloat} used by a {@code Stream<Float>}
     * {@link Spliterator} (if there were any) and by the public method
     * {@code nextFloat(origin, bound)}.  If {@code origin} is greater
     * than {@code bound}, then this method simply calls the unbounded
     * version of {@code nextFloat()}, and otherwise scales and translates
     * the result of a call to {@code nextFloat()} so that it lies
     * between {@code origin} (inclusive) and {@code bound} (exclusive).
     *
     * @implNote The implementation of this method is identical to
     *     the implementation of {@code nextDouble(origin, bound)}
     *     except that {@code float} values and the {@code nextFloat()}
     *     method are used rather than {@code double} values and the
     *     {@code nextDouble()} method.
     *
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code float} values
     * @param origin the least value that can be produced,
     *        unless greater than or equal to {@code bound}; must be finite
     * @param bound the upper bound (exclusive), unless {@code origin}
     *        is greater than or equal to {@code bound}; must be finite
     * @return a pseudorandomly chosen {@code float} value,
     *         which will be between {@code origin} (inclusive) and
     *         {@code bound} exclusive unless {@code origin}
     *         is greater than or equal to {@code bound},
     *         in which case it will be between 0.0 (inclusive)
     *         and 1.0 (exclusive)
     */
    public static float boundedNextFloat(RandomGenerator rng, float origin, float bound) {
        float r = rng.nextFloat();
        if (origin < bound) {
            r = r * (bound - origin) + origin;
            if (r >= bound) // may need to correct a rounding problem
                r = Float.intBitsToFloat(Float.floatToIntBits(bound) - 1);
        }
        return r;
    }

    /**
     * This is the form of {@code nextFloat} used by the public method
     * {@code nextFloat(bound)}.  This is essentially a version of
     * {@code boundedNextFloat(origin, bound)} that has been
     * specialized for the case where the {@code origin} is zero
     * and the {@code bound} is greater than zero.
     *
     * @implNote The implementation of this method is identical to
     *     the implementation of {@code nextDouble(bound)}
     *     except that {@code float} values and the {@code nextFloat()}
     *     method are used rather than {@code double} values and the
     *     {@code nextDouble()} method.
     *
     * @param rng a random number generator to be used as a
     *        source of pseudorandom {@code float} values
     * @param bound the upper bound (exclusive); must be finite and
     *        greater than zero
     * @return a pseudorandomly chosen {@code float} value
     *         between zero (inclusive) and {@code bound} (exclusive)
     */
    public static float boundedNextFloat(RandomGenerator rng, float bound) {
        // Specialize boundedNextFloat for origin == 0, bound > 0
        float r = rng.nextFloat();
        r = r * bound;
        if (r >= bound) // may need to correct a rounding problem
            r = Float.intBitsToFloat(Float.floatToIntBits(bound) - 1);
        return r;
    }

    // The following decides which of two strategies initialSeed() will use.
    private static boolean secureRandomSeedRequested() {
        String pp = java.security.AccessController.doPrivileged(
                new sun.security.action.GetPropertyAction(
                        "java.util.secureRandomSeed"));
        return (pp != null && pp.equalsIgnoreCase("true"));
    }

    private static final boolean useSecureRandomSeed = secureRandomSeedRequested();

    /**
     * Returns a {@code long} value (chosen from some
     * machine-dependent entropy source) that may be useful for
     * initializing a source of seed values for instances of {@link RandomGenerator}
     * created by zero-argument constructors.  (This method should
     * <i>not</i> be called repeatedly, once per constructed
     * object; at most it should be called once per class.)
     *
     * @return a {@code long} value, randomly chosen using
     *         appropriate environmental entropy
     */
    public static long initialSeed() {
        if (useSecureRandomSeed) {
            byte[] seedBytes = java.security.SecureRandom.getSeed(8);
            long s = (long)(seedBytes[0]) & 0xffL;
            for (int i = 1; i < 8; ++i)
                s = (s << 8) | ((long)(seedBytes[i]) & 0xffL);
            return s;
        }
        return (mixStafford13(System.currentTimeMillis()) ^
                mixStafford13(System.nanoTime()));
    }

    /**
     * The first 32 bits of the golden ratio (1+sqrt(5))/2, forced to be odd.
     * Useful for producing good Weyl sequences or as an arbitrary nonzero odd value.
     */
    public static final int  GOLDEN_RATIO_32 = 0x9e3779b9;

    /**
     * The first 64 bits of the golden ratio (1+sqrt(5))/2, forced to be odd.
     * Useful for producing good Weyl sequences or as an arbitrary nonzero odd value.
     */
    public static final long GOLDEN_RATIO_64 = 0x9e3779b97f4a7c15L;

    /**
     * The first 32 bits of the silver ratio 1+sqrt(2), forced to be odd.
     * Useful for producing good Weyl sequences or as an arbitrary nonzero odd value.
     */
    public static final int  SILVER_RATIO_32 = 0x6A09E667;

    /**
     * The first 64 bits of the silver ratio 1+sqrt(2), forced to be odd.
     * Useful for producing good Weyl sequences or as an arbitrary nonzero odd value.
     */
    public static final long SILVER_RATIO_64 = 0x6A09E667F3BCC909L;

    /**
     * Computes the 64-bit mixing function for MurmurHash3.
     * This is a 64-bit hashing function with excellent avalanche statistics.
     * https://github.com/aappleby/smhasher/wiki/MurmurHash3
     *
     * Note that if the argument {@code z} is 0, the result is 0.
     *
     * @param z any long value
     *
     * @return the result of hashing z
     */
    public static long mixMurmur64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    /**
     * Computes Stafford variant 13 of the 64-bit mixing function for MurmurHash3.
     * This is a 64-bit hashing function with excellent avalanche statistics.
     * http://zimbry.blogspot.com/2011/09/better-bit-mixing-improving-on.html
     *
     * Note that if the argument {@code z} is 0, the result is 0.
     *
     * @param z any long value
     *
     * @return the result of hashing z
     */
    public static long mixStafford13(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    /**
     * Computes Doug Lea's 64-bit mixing function.
     * This is a 64-bit hashing function with excellent avalanche statistics.
     * It has the advantages of using the same multiplicative constant twice
     * and of using only 32-bit shifts.
     *
     * Note that if the argument {@code z} is 0, the result is 0.
     *
     * @param z any long value
     *
     * @return the result of hashing z
     */
    public static long mixLea64(long z) {
        z = (z ^ (z >>> 32)) * 0xdaba0b6eb09322e3L;
        z = (z ^ (z >>> 32)) * 0xdaba0b6eb09322e3L;
        return z ^ (z >>> 32);
    }

    /**
     * Computes the 32-bit mixing function for MurmurHash3.
     * This is a 32-bit hashing function with excellent avalanche statistics.
     * https://github.com/aappleby/smhasher/wiki/MurmurHash3
     *
     * Note that if the argument {@code z} is 0, the result is 0.
     *
     * @param z any long value
     *
     * @return the result of hashing z
     */
    public static int mixMurmur32(int z) {
        z = (z ^ (z >>> 16)) * 0x85ebca6b;
        z = (z ^ (z >>> 13)) * 0xc2b2ae35;
        return z ^ (z >>> 16);
    }

    /**
     * Computes Doug Lea's 32-bit mixing function.
     * This is a 32-bit hashing function with excellent avalanche statistics.
     * It has the advantages of using the same multiplicative constant twice
     * and of using only 16-bit shifts.
     *
     * Note that if the argument {@code z} is 0, the result is 0.
     *
     * @param z any long value
     *
     * @return the result of hashing z
     */
    public static int mixLea32(int z) {
        z = (z ^ (z >>> 16)) * 0xd36d884b;
        z = (z ^ (z >>> 16)) * 0xd36d884b;
        return z ^ (z >>> 16);
    }

    // Non-public (package only) support for spliterators needed by AbstractSplittableGenerator
    // and AbstractArbitrarilyJumpableGenerator and AbstractSharedGenerator

    /**
     * Base class for making Spliterator classes for streams of randomly chosen values.
     */
    public abstract static class RandomSpliterator {

        /** low range value */
        public long index;

        /** high range value */
        public final long fence;

        /**
         * Constructor
         *
         * @param index  low range value
         * @param fence  high range value
         */
        public RandomSpliterator(long index, long fence) {
            this.index = index; this.fence = fence;
        }

        /**
         * Returns estimated size.
         *
         * @return estimated size
         */
        public long estimateSize() {
            return fence - index;
        }

        /**
         * Returns characteristics.
         *
         * @return characteristics
         */
        public int characteristics() {
            return (Spliterator.SIZED | Spliterator.SUBSIZED |
                    Spliterator.NONNULL | Spliterator.IMMUTABLE);
        }
    }


    /*
     * Implementation support for nextExponential() and nextGaussian() methods of RandomGenerator.
     *
     * Each is implemented using McFarland's fast modified ziggurat algorithm (largely
     * table-driven, with rare cases handled by computation and rejection sampling).
     * Walker's alias method for sampling a discrete distribution also plays a role.
     *
     * The tables themselves, as well as a number of associated parameters, are defined
     * in class java.util.DoubleZigguratTables, which is automatically generated by the
     * program create_ziggurat_tables.c (which takes only a few seconds to run).
     *
     * For more information about the algorithms, see these articles:
     *
     * Christopher D. McFarland.  2016 (published online 24 Jun 2015).  A modified ziggurat
     * algorithm for generating exponentially and normally distributed pseudorandom numbers.
     * Journal of Statistical Computation and Simulation 86 (7), pages 1281-1294.
     * https://www.tandfonline.com/doi/abs/10.1080/00949655.2015.1060234
     * Also at https://arxiv.org/abs/1403.6870 (26 March 2014).
     *
     * Alastair J. Walker.  1977.  An efficient method for generating discrete random
     * variables with general distributions. ACM Trans. Math. Software 3, 3
     * (September 1977), 253-256. DOI: https://doi.org/10.1145/355744.355749
     *
     * Certain details of these algorithms depend critically on the quality of the
     * low-order bits delivered by NextLong().  These algorithms should not be used
     * with RNG algorithms (such as a simple Linear Congruential Generator) whose
     * low-order output bits do not have good statistical quality.
     */

    // Implementation support for nextExponential()

    static double computeNextExponential(RandomGenerator rng) {
        long U1 = rng.nextLong();
        // Experimentation on a variety of machines indicates that it is overall much faster
        // to do the following & and < operations on longs rather than first cast U1 to int
        // (but then we need to cast to int before doing the array indexing operation).
        long i = U1 & DoubleZigguratTables.exponentialLayerMask;
        if (i < DoubleZigguratTables.exponentialNumberOfLayers) {
            // This is the fast path (occurring more than 98% of the time).  Make an early exit.
            return DoubleZigguratTables.exponentialX[(int)i] * (U1 >>> 1);
        }
        // We didn't use the upper part of U1 after all.  We'll be able to use it later.

        for (double extra = 0.0; ; ) {
            // Use Walker's alias method to sample an (unsigned) integer j from a discrete
            // probability distribution that includes the tail and all the ziggurat overhangs;
            // j will be less than DoubleZigguratTables.exponentialNumberOfLayers + 1.
            long UA = rng.nextLong();
            int j = (int)UA & DoubleZigguratTables.exponentialAliasMask;
            if (UA >= DoubleZigguratTables.exponentialAliasThreshold[j]) {
                j = DoubleZigguratTables.exponentialAliasMap[j] &
                    DoubleZigguratTables.exponentialSignCorrectionMask;
            }
            if (j > 0) {   // Sample overhang j
                // For the exponential distribution, every overhang is convex.
                final double[] X = DoubleZigguratTables.exponentialX;
                final double[] Y = DoubleZigguratTables.exponentialY;
                for (;; U1 = (rng.nextLong() >>> 1)) {
                    long U2 = (rng.nextLong() >>> 1);
                    // Compute the actual x-coordinate of the randomly chosen point.
                    double x = (X[j] * 0x1.0p63) + ((X[j-1] - X[j]) * (double)U1);
                    // Does the point lie below the curve?
                    long Udiff = U2 - U1;
                    if (Udiff < 0) {
                        // We picked a point in the upper-right triangle.  None of those can be
                        // accepted.  So remap the point into the lower-left triangle and try that.
                        // In effect, we swap U1 and U2, and invert the sign of Udiff.
                        Udiff = -Udiff;
                        U2 = U1;
                        U1 -= Udiff;
                    }
                    if (Udiff >= DoubleZigguratTables.exponentialConvexMargin) {
                        return x + extra;   // The chosen point is way below the curve; accept it.
                    }
                    // Compute the actual y-coordinate of the randomly chosen point.
                    double y = (Y[j] * 0x1.0p63) + ((Y[j] - Y[j-1]) * (double)U2);
                    // Now see how that y-coordinate compares to the curve
                    if (y <= Math.exp(-x)) {
                        return x + extra;   // The chosen point is below the curve; accept it.
                    }
                    // Otherwise, we reject this sample and have to try again.
                }
            }
            // We are now committed to sampling from the tail.  We could do a recursive call
            // and then add X[0] but we save some time and stack space by using an iterative loop.
            extra += DoubleZigguratTables.exponentialX0;
            // This is like the first five lines of this method, but if it returns, it first adds "extra".
            U1 = rng.nextLong();
            i = U1 & DoubleZigguratTables.exponentialLayerMask;
            if (i < DoubleZigguratTables.exponentialNumberOfLayers) {
                return DoubleZigguratTables.exponentialX[(int)i] * (U1 >>> 1) + extra;
            }
        }
    }

    // Implementation support for nextGaussian()

    static double computeNextGaussian(RandomGenerator rng) {
        long U1 = rng.nextLong();
        // Experimentation on a variety of machines indicates that it is overall much faster
        // to do the following & and < operations on longs rather than first cast U1 to int
        // (but then we need to cast to int before doing the array indexing operation).
        long i = U1 & DoubleZigguratTables.normalLayerMask;

        if (i < DoubleZigguratTables.normalNumberOfLayers) {
            // This is the fast path (occurring more than 98% of the time).  Make an early exit.
            return DoubleZigguratTables.normalX[(int)i] * U1;   // Note that the sign bit of U1 is used here.
        }
        // We didn't use the upper part of U1 after all.
        // Pull U1 apart into a sign bit and a 63-bit value for later use.
        double signBit = (U1 >= 0) ? 1.0 : -1.0;
        U1 = (U1 << 1) >>> 1;

        // Use Walker's alias method to sample an (unsigned) integer j from a discrete
        // probability distribution that includes the tail and all the ziggurat overhangs;
        // j will be less than DoubleZigguratTables.normalNumberOfLayers + 1.
        long UA = rng.nextLong();
        int j = (int)UA & DoubleZigguratTables.normalAliasMask;
        if (UA >= DoubleZigguratTables.normalAliasThreshold[j]) {
            j = DoubleZigguratTables.normalAliasMap[j] & DoubleZigguratTables.normalSignCorrectionMask;
        }

        double x;
        // Now the goal is to choose the result, which will be multiplied by signBit just before return.

        // There are four kinds of overhangs:
        //
        //  j == 0                          :  Sample from tail
        //  0 < j < normalInflectionIndex   :  Overhang is convex; can reject upper-right triangle
        //  j == normalInflectionIndex      :  Overhang includes the inflection point
        //  j > normalInflectionIndex       :  Overhang is concave; can accept point in lower-left triangle
        //
        // Choose one of four loops to compute x, each specialized for a specific kind of overhang.
        // Conditional statements are arranged such that the more likely outcomes are first.

        // In the three cases other than the tail case:
        // U1 represents a fraction (scaled by 2**63) of the width of rectangle measured from the left.
        // U2 represents a fraction (scaled by 2**63) of the height of rectangle measured from the top.
        // Together they indicate a randomly chosen point within the rectangle.

        final double[] X = DoubleZigguratTables.normalX;
        final double[] Y = DoubleZigguratTables.normalY;
        if (j > DoubleZigguratTables.normalInflectionIndex) {   // Concave overhang
            for (;; U1 = (rng.nextLong() >>> 1)) {
                long U2 = (rng.nextLong() >>> 1);
                // Compute the actual x-coordinate of the randomly chosen point.
                x = (X[j] * 0x1.0p63) + ((X[j-1] - X[j]) * (double)U1);
                // Does the point lie below the curve?
                long Udiff = U2 - U1;
                if (Udiff >= 0) {
                    break;   // The chosen point is in the lower-left triangle; accept it.
                }
                if (Udiff <= -DoubleZigguratTables.normalConcaveMargin) {
                    continue;   // The chosen point is way above the curve; reject it.
                }
                // Compute the actual y-coordinate of the randomly chosen point.
                double y = (Y[j] * 0x1.0p63) + ((Y[j] - Y[j-1]) * (double)U2);
                // Now see how that y-coordinate compares to the curve
                if (y <= Math.exp(-0.5*x*x)) {
                    break;   // The chosen point is below the curve; accept it.
                }
                // Otherwise, we reject this sample and have to try again.
            }
        } else if (j == 0) {   // Tail
            // Tail-sampling method of Marsaglia and Tsang.  See any one of:
            // Marsaglia and Tsang. 1984. A fast, easily implemented method for sampling from decreasing
            //    or symmetric unimodal density functions. SIAM J. Sci. Stat. Comput. 5, 349-359.
            // Marsaglia and Tsang. 1998. The Monty Python method for generating random variables.
            //    ACM Trans. Math. Softw. 24, 3 (September 1998), 341-350.  See page 342, step (4).
            //    http://doi.org/10.1145/292395.292453
            // Thomas, Luk, Leong, and Villasenor. 2007. Gaussian random number generators.
            //    ACM Comput. Surv. 39, 4, Article 11 (November 2007).  See Algorithm 16.
            //    http://doi.org/10.1145/1287620.1287622
            // Compute two separate random exponential samples and then compare them in certain way.
            do {
                x = (1.0 / DoubleZigguratTables.normalX0) * computeNextExponential(rng);
            } while (computeNextExponential(rng) < 0.5*x*x);
            x += DoubleZigguratTables.normalX0;
        } else if (j < DoubleZigguratTables.normalInflectionIndex) {   // Convex overhang
            for (;; U1 = (rng.nextLong() >>> 1)) {
                long U2 = (rng.nextLong() >>> 1);
                // Compute the actual x-coordinate of the randomly chosen point.
                x = (X[j] * 0x1.0p63) + ((X[j-1] - X[j]) * (double)U1);
                // Does the point lie below the curve?
                long Udiff = U2 - U1;
                if (Udiff < 0) {
                    // We picked a point in the upper-right triangle.  None of those can be accepted.
                    // So remap the point into the lower-left triangle and try that.
                    // In effect, we swap U1 and U2, and invert the sign of Udiff.
                    Udiff = -Udiff;
                    U2 = U1;
                    U1 -= Udiff;
                }
                if (Udiff >= DoubleZigguratTables.normalConvexMargin) {
                    break;   // The chosen point is way below the curve; accept it.
                }
                // Compute the actual y-coordinate of the randomly chosen point.
                double y = (Y[j] * 0x1.0p63) + ((Y[j] - Y[j-1]) * (double)U2);
                // Now see how that y-coordinate compares to the curve
                if (y <= Math.exp(-0.5*x*x)) break; // The chosen point is below the curve; accept it.
                // Otherwise, we reject this sample and have to try again.
            }
        } else {
            // The overhang includes the inflection point, so the curve is both convex and concave
            for (;; U1 = (rng.nextLong() >>> 1)) {
                long U2 = (rng.nextLong() >>> 1);
                // Compute the actual x-coordinate of the randomly chosen point.
                x = (X[j] * 0x1.0p63) + ((X[j-1] - X[j]) * (double)U1);
                // Does the point lie below the curve?
                long Udiff = U2 - U1;
                if (Udiff >= DoubleZigguratTables.normalConvexMargin) {
                    break;   // The chosen point is way below the curve; accept it.
                }
                if (Udiff <= -DoubleZigguratTables.normalConcaveMargin) {
                    continue;   // The chosen point is way above the curve; reject it.
                }
                // Compute the actual y-coordinate of the randomly chosen point.
                double y = (Y[j] * 0x1.0p63) + ((Y[j] - Y[j-1]) * (double)U2);
                // Now see how that y-coordinate compares to the curve
                if (y <= Math.exp(-0.5*x*x)) {
                    break;   // The chosen point is below the curve; accept it.
                }
                // Otherwise, we reject this sample and have to try again.
            }
        }
        return signBit*x;
    }

    /**
     * This class overrides the stream-producing methods (such as {@code ints()})
     * in class {@link AbstractGenerator} to provide {@link Spliterator}-based
     * implmentations that support potentially parallel execution.
     *
     * To implement a pseudorandom number generator, the programmer needs
     * only to extend this class and provide implementations for the methods
     * {@code nextInt()}, {@code nextLong()}, {@code makeIntsSpliterator},
     * {@code makeLongsSpliterator}, and {@code makeDoublesSpliterator}.
     *
     * This class is not public; it provides shared code to the public
     * classes {@link AbstractSplittableGenerator}, {@link AbstractSharedGenerator},
     * and {@link AbstractArbitrarilyJumpableGenerator}.
     *
     * @since 14
     */
    public abstract static class AbstractSpliteratorGenerator implements RandomGenerator {
        /*
         * Implementation Overview.
         *
         * This class provides most of the "user API" methods needed to
         * satisfy the interface RandomGenerator.  An implementation of this
         * interface need only extend this class and provide implementations
         * of six methods: nextInt, nextLong, and nextDouble (the versions
         * that take no arguments) and makeIntsSpliterator,
         * makeLongsSpliterator, and makeDoublesSpliterator.
         *
         * File organization: First the non-public abstract methods needed
         * to create spliterators, then the main public methods.
         */

        /**
         * Create an instance of {@link Spliterator.OfInt} that for each traversal position
	 * between the specified index (inclusive) and the specified fence (exclusive) generates
	 * a pseudorandomly chosen {@code int} value between the specified origin (inclusive) and
	 * the specified bound (exclusive).
         *
         * @param index the (inclusive) lower bound on traversal positions
         * @param fence the (exclusive) upper bound on traversal positions
         * @param origin the (inclusive) lower bound on the pseudorandom values to be generated
         * @param bound the (exclusive) upper bound on the pseudorandom values to be generated
         * @return an instance of {@link Spliterator.OfInt}
         */
        public abstract Spliterator.OfInt makeIntsSpliterator(long index, long fence, int origin, int bound);

        /**
         * Create an instance of {@link Spliterator.OfLong} that for each traversal position
	 * between the specified index (inclusive) and the specified fence (exclusive) generates
	 * a pseudorandomly chosen {@code long} value between the specified origin (inclusive) and
	 * the specified bound (exclusive).
         *
         * @param index the (inclusive) lower bound on traversal positions
         * @param fence the (exclusive) upper bound on traversal positions
         * @param origin the (inclusive) lower bound on the pseudorandom values to be generated
         * @param bound the (exclusive) upper bound on the pseudorandom values to be generated
         * @return an instance of {@link Spliterator.OfLong}
         */
        public abstract Spliterator.OfLong makeLongsSpliterator(long index, long fence, long origin, long bound);

        /**
         * Create an instance of {@link Spliterator.OfDouble} that for each traversal position
	 * between the specified index (inclusive) and the specified fence (exclusive) generates
	 * a pseudorandomly chosen {@code double} value between the specified origin (inclusive) and
	 * the specified bound (exclusive).
         *
         * @param index the (inclusive) lower bound on traversal positions
         * @param fence the (exclusive) upper bound on traversal positions
         * @param origin the (inclusive) lower bound on the pseudorandom values to be generated
         * @param bound the (exclusive) upper bound on the pseudorandom values to be generated
         * @return an instance of {@link Spliterator.OfDouble}
         */
        public abstract Spliterator.OfDouble makeDoublesSpliterator(long index, long fence, double origin, double bound);

        /* ---------------- public methods ---------------- */

        // stream methods, coded in a way intended to better isolate for
        // maintenance purposes the small differences across forms.

        private static IntStream intStream(Spliterator.OfInt srng) {
            return StreamSupport.intStream(srng, false);
        }

        private static LongStream longStream(Spliterator.OfLong srng) {
            return StreamSupport.longStream(srng, false);
        }

        private static DoubleStream doubleStream(Spliterator.OfDouble srng) {
            return StreamSupport.doubleStream(srng, false);
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of pseudorandom {@code int}
         * values from this generator and/or one split from it.
         *
         * @param streamSize the number of values to generate
         *
         * @return a stream of pseudorandom {@code int} values
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        public IntStream ints(long streamSize) {
            RandomSupport.checkStreamSize(streamSize);
            return intStream(makeIntsSpliterator(0L, streamSize, Integer.MAX_VALUE, 0));
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
            return intStream(makeIntsSpliterator(0L, Long.MAX_VALUE, Integer.MAX_VALUE, 0));
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of pseudorandom {@code int}
         * values from this generator and/or one split from it; each value conforms to the given origin
         * (inclusive) and bound (exclusive).
         *
         * @param streamSize         the number of values to generate
         * @param randomNumberOrigin the origin (inclusive) of each random value
         * @param randomNumberBound  the bound (exclusive) of each random value
         *
         * @return a stream of pseudorandom {@code int} values, each with the given origin (inclusive)
         *         and bound (exclusive)
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero, or {@code
         *                                  randomNumberOrigin} is greater than or equal to {@code
         *                                  randomNumberBound}
         */
        public IntStream ints(long streamSize, int randomNumberOrigin, int randomNumberBound) {
            RandomSupport.checkStreamSize(streamSize);
            RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
            return intStream(makeIntsSpliterator(0L, streamSize, randomNumberOrigin, randomNumberBound));
        }

        /**
         * Returns an effectively unlimited stream of pseudorandom {@code int} values from this
         * generator and/or one split from it; each value conforms to the given origin (inclusive) and
         * bound (exclusive).
         *
         * @param randomNumberOrigin the origin (inclusive) of each random value
         * @param randomNumberBound  the bound (exclusive) of each random value
         *
         * @return a stream of pseudorandom {@code int} values, each with the given origin (inclusive)
         *         and bound (exclusive)
         *
         * @throws IllegalArgumentException if {@code randomNumberOrigin} is greater than or equal to
         *                                  {@code randomNumberBound}
         *
         * @implNote This method is implemented to be equivalent to {@code ints(Long.MAX_VALUE,
         *         randomNumberOrigin, randomNumberBound)}.
         */
        public IntStream ints(int randomNumberOrigin, int randomNumberBound) {
            RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
            return intStream(makeIntsSpliterator(0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound));
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of pseudorandom {@code long}
         * values from this generator and/or one split from it.
         *
         * @param streamSize the number of values to generate
         *
         * @return a stream of pseudorandom {@code long} values
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        public LongStream longs(long streamSize) {
            RandomSupport.checkStreamSize(streamSize);
            return longStream(makeLongsSpliterator(0L, streamSize, Long.MAX_VALUE, 0L));
        }

        /**
         * Returns an effectively unlimited stream of pseudorandom {@code long} values from this
         * generator and/or one split from it.
         *
         * @return a stream of pseudorandom {@code long} values
         *
         * @implNote This method is implemented to be equivalent to {@code
         *         longs(Long.MAX_VALUE)}.
         */
        public LongStream longs() {
            return longStream(makeLongsSpliterator(0L, Long.MAX_VALUE, Long.MAX_VALUE, 0L));
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of pseudorandom {@code long}
         * values from this generator and/or one split from it; each value conforms to the given origin
         * (inclusive) and bound (exclusive).
         *
         * @param streamSize         the number of values to generate
         * @param randomNumberOrigin the origin (inclusive) of each random value
         * @param randomNumberBound  the bound (exclusive) of each random value
         *
         * @return a stream of pseudorandom {@code long} values, each with the given origin (inclusive)
         *         and bound (exclusive)
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero, or {@code
         *                                  randomNumberOrigin} is greater than or equal to {@code
         *                                  randomNumberBound}
         */
        public LongStream longs(long streamSize, long randomNumberOrigin,
                                 long randomNumberBound) {
            RandomSupport.checkStreamSize(streamSize);
            RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
            return longStream(makeLongsSpliterator(0L, streamSize, randomNumberOrigin, randomNumberBound));
        }

        /**
         * Returns an effectively unlimited stream of pseudorandom {@code long} values from this
         * generator and/or one split from it; each value conforms to the given origin (inclusive) and
         * bound (exclusive).
         *
         * @param randomNumberOrigin the origin (inclusive) of each random value
         * @param randomNumberBound  the bound (exclusive) of each random value
         *
         * @return a stream of pseudorandom {@code long} values, each with the given origin (inclusive)
         *         and bound (exclusive)
         *
         * @throws IllegalArgumentException if {@code randomNumberOrigin} is greater than or equal to
         *                                  {@code randomNumberBound}
         *
         * @implNote This method is implemented to be equivalent to {@code longs(Long.MAX_VALUE,
         *         randomNumberOrigin, randomNumberBound)}.
         */
        public LongStream longs(long randomNumberOrigin, long randomNumberBound) {
            RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
            return StreamSupport.longStream
                (makeLongsSpliterator(0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound),
                 false);
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of pseudorandom {@code double}
         * values from this generator and/or one split from it; each value is between zero (inclusive)
         * and one (exclusive).
         *
         * @param streamSize the number of values to generate
         *
         * @return a stream of {@code double} values
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        public DoubleStream doubles(long streamSize) {
            RandomSupport.checkStreamSize(streamSize);
            return doubleStream(makeDoublesSpliterator(0L, streamSize, Double.MAX_VALUE, 0.0));
        }

        /**
         * Returns an effectively unlimited stream of pseudorandom {@code double} values from this
         * generator and/or one split from it; each value is between zero (inclusive) and one
         * (exclusive).
         *
         * @return a stream of pseudorandom {@code double} values
         *
         * @implNote This method is implemented to be equivalent to {@code
         *         doubles(Long.MAX_VALUE)}.
         */
        public DoubleStream doubles() {
            return doubleStream(makeDoublesSpliterator(0L, Long.MAX_VALUE, Double.MAX_VALUE, 0.0));
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of pseudorandom {@code double}
         * values from this generator and/or one split from it; each value conforms to the given origin
         * (inclusive) and bound (exclusive).
         *
         * @param streamSize         the number of values to generate
         * @param randomNumberOrigin the origin (inclusive) of each random value
         * @param randomNumberBound  the bound (exclusive) of each random value
         *
         * @return a stream of pseudorandom {@code double} values, each with the given origin
         *         (inclusive) and bound (exclusive)
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         * @throws IllegalArgumentException if {@code randomNumberOrigin} is greater than or equal to
         *                                  {@code randomNumberBound}
         */
        public DoubleStream doubles(long streamSize, double randomNumberOrigin, double randomNumberBound) {
            RandomSupport.checkStreamSize(streamSize);
            RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
            return doubleStream(makeDoublesSpliterator(0L, streamSize, randomNumberOrigin, randomNumberBound));
        }

        /**
         * Returns an effectively unlimited stream of pseudorandom {@code double} values from this
         * generator and/or one split from it; each value conforms to the given origin (inclusive) and
         * bound (exclusive).
         *
         * @param randomNumberOrigin the origin (inclusive) of each random value
         * @param randomNumberBound  the bound (exclusive) of each random value
         *
         * @return a stream of pseudorandom {@code double} values, each with the given origin
         *         (inclusive) and bound (exclusive)
         *
         * @throws IllegalArgumentException if {@code randomNumberOrigin} is greater than or equal to
         *                                  {@code randomNumberBound}
         *
         * @implNote This method is implemented to be equivalent to {@code
         *         doubles(Long.MAX_VALUE, randomNumberOrigin, randomNumberBound)}.
         */
        public DoubleStream doubles(double randomNumberOrigin, double randomNumberBound) {
            RandomSupport.checkRange(randomNumberOrigin, randomNumberBound);
            return doubleStream(makeDoublesSpliterator(0L, Long.MAX_VALUE, randomNumberOrigin, randomNumberBound));
        }

    }

    /**
     * This class provides much of the implementation of the
     * {@link ArbitrarilyJumpableGenerator} interface, to minimize the effort
     * required to implement that interface.
     *
     * To implement a pseudorandom number generator, the programmer needs
     * only to extend this class and provide implementations for the
     * methods {@link #nextInt()}, {@link #nextLong()}, {@link #copy()},
     * {@link #jump(double)}, {@link #jumpPowerOfTwo(int)},
     * {@link #defaultJumpDistance()}, and {@link #defaultLeapDistance()}.
     *
     * (If the pseudorandom number generator also has the ability to split,
     * then the programmer may wish to consider instead extending
     * {@link AbstractSplittableGenerator}.)
     *
     * The programmer should generally provide at least three constructors:
     * one that takes no arguments, one that accepts a {@code long}
     * seed value, and one that accepts an array of seed {@code byte} values.
     * This class provides a public {@code initialSeed()} method that may
     * be useful in initializing some static state from which to derive
     * defaults seeds for use by the no-argument constructor.
     *
     * For the stream methods (such as {@code ints()} and {@code splits()}),
     * this class provides {@link Spliterator}-based implementations that
     * allow parallel execution when appropriate.  In this respect
     * {@link ArbitrarilyJumpableGenerator} differs from {@link JumpableGenerator},
     * which provides very simple implementations that produce
     * sequential streams only.
     *
     * <p>An implementation of the {@link AbstractArbitrarilyJumpableGenerator} class
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
     * @since 14
     */
    public abstract static class AbstractArbitrarilyJumpableGenerator
        extends AbstractSpliteratorGenerator implements RandomGenerator.ArbitrarilyJumpableGenerator {

        /*
         * Implementation Overview.
         *
         * This class provides most of the "user API" methods needed to satisfy
         * the interface ArbitrarilyJumpableGenerator.  Most of these methods
         * are in turn inherited from AbstractGenerator and the non-public class
         * AbstractSpliteratorGenerator; this file implements four versions of the
         * jumps method and defines the spliterators necessary to support them.
         *
         * File organization: First the non-public methods needed by the class
         * AbstractSpliteratorGenerator, then the main public methods, followed by some
         * custom spliterator classes needed for stream methods.
         */

        // IllegalArgumentException messages
        static final String BadLogDistance  = "logDistance must be non-negative";

        // Methods required by class AbstractSpliteratorGenerator
        public Spliterator.OfInt makeIntsSpliterator(long index, long fence, int origin, int bound) {
            return new RandomIntsSpliterator(this, index, fence, origin, bound);
        }
        public Spliterator.OfLong makeLongsSpliterator(long index, long fence, long origin, long bound) {
            return new RandomLongsSpliterator(this, index, fence, origin, bound);
        }
        public Spliterator.OfDouble makeDoublesSpliterator(long index, long fence, double origin, double bound) {
            return new RandomDoublesSpliterator(this, index, fence, origin, bound);
        }

        // Similar methods used by this class
        Spliterator<RandomGenerator> makeJumpsSpliterator(long index, long fence, double distance) {
            return new RandomJumpsSpliterator(this, index, fence, distance);
        }
        Spliterator<JumpableGenerator> makeLeapsSpliterator(long index, long fence, double distance) {
            return new RandomLeapsSpliterator(this, index, fence, distance);
        }
        Spliterator<ArbitrarilyJumpableGenerator> makeArbitraryJumpsSpliterator(long index, long fence, double distance) {
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
        public abstract AbstractArbitrarilyJumpableGenerator copy();

        // Stream methods for jumping

        private static <T> Stream<T> stream(Spliterator<T> srng) {
            return StreamSupport.stream(srng, false);
        }

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of which
         * implements the {@link RandomGenerator} interface, produced by jumping copies of this
         * generator by different integer multiples of the default jump distance.
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @implNote This method is implemented to be equivalent to {@code
         *         jumps(Long.MAX_VALUE)}.
         */
        public Stream<RandomGenerator> jumps() {
            return stream(makeJumpsSpliterator(0L, Long.MAX_VALUE, defaultJumpDistance()));
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of
         * new pseudorandom number generators, each of which implements the
         * {@link RandomGenerator} interface, produced by jumping copies of this generator
         * by different integer multiples of the default jump distance.
         *
         * @param streamSize the number of generators to generate
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        public Stream<RandomGenerator> jumps(long streamSize) {
            RandomSupport.checkStreamSize(streamSize);
            return stream(makeJumpsSpliterator(0L, streamSize, defaultJumpDistance()));
        }

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of which
         * implements the {@link RandomGenerator} interface, produced by jumping copies of this
         * generator by different integer multiples of the specified jump distance.
         *
         * @param distance a distance to jump forward within the state cycle
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @implNote This method is implemented to be equivalent to {@code
         *         jumps(Long.MAX_VALUE)}.
         */
        public Stream<ArbitrarilyJumpableGenerator> jumps(double distance) {
            return stream(makeArbitraryJumpsSpliterator(0L, Long.MAX_VALUE, distance));
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of new pseudorandom number
         * generators, each of which implements the {@link RandomGenerator} interface, produced by
         * jumping copies of this generator by different integer multiples of the specified jump
         * distance.
         *
         * @param streamSize the number of generators to generate
         * @param distance   a distance to jump forward within the state cycle
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        public Stream<ArbitrarilyJumpableGenerator> jumps(long streamSize, double distance) {
            RandomSupport.checkStreamSize(streamSize);
            return stream(makeArbitraryJumpsSpliterator(0L, streamSize, distance));
        }

        /**
         * Alter the state of this pseudorandom number generator so as to
         * jump forward a very large, fixed distance (typically 2<sup>128</sup>
         * or more) within its state cycle.  The distance used is that
         * returned by method {@code defaultLeapDistance()}.
         */
        public void leap() {
            jump(defaultLeapDistance());
        }

        // Stream methods for leaping

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of which
         * implements the {@link RandomGenerator} interface, produced by jumping copies of this
         * generator by different integer multiples of the default leap distance.
         *
         * @implNote This method is implemented to be equivalent to {@code leaps(Long.MAX_VALUE)}.
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         */
        public Stream<JumpableGenerator> leaps() {
            return stream(makeLeapsSpliterator(0L, Long.MAX_VALUE, defaultLeapDistance()));
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of new pseudorandom number
         * generators, each of which implements the {@link RandomGenerator} interface, produced by
         * jumping copies of this generator by different integer multiples of the default leap
         * distance.
         *
         * @param streamSize the number of generators to generate
         *
         * @return a stream of objects that implement the {@link RandomGenerator} interface
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        public Stream<JumpableGenerator> leaps(long streamSize) {
            return stream(makeLeapsSpliterator(0L, streamSize, defaultLeapDistance()));
        }


        /**
         * Spliterator for int streams.  We multiplex the four int versions into one class by treating a
         * bound less than origin as unbounded, and also by treating "infinite" as equivalent to
         * {@code Long.MAX_VALUE}. For splits, we choose to override the method {@code trySplit()} to
         * try to optimize execution speed: instead of dividing a range in half, it breaks off the
         * largest possible chunk whose size is a power of two such that the remaining chunk is not
         * empty. In this way, the necessary jump distances will tend to be powers of two.  The long
         * and double versions of this class are identical except for types.
         */
        static class RandomIntsSpliterator extends RandomSupport.RandomSpliterator implements Spliterator.OfInt {
            final ArbitrarilyJumpableGenerator generatingGenerator;
            final int origin;
            final int bound;

            RandomIntsSpliterator(ArbitrarilyJumpableGenerator generatingGenerator, long index, long fence, int origin, int bound) {
                super(index, fence);
                this.origin = origin; this.bound = bound;
                this.generatingGenerator = generatingGenerator;
            }

            public Spliterator.OfInt trySplit() {
                long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
                if (m <= i) return null;
                index = m;
                ArbitrarilyJumpableGenerator r = generatingGenerator;
                return new RandomIntsSpliterator(r.copyAndJump((double)delta), i, m, origin, bound);
            }

            public boolean tryAdvance(IntConsumer consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(RandomSupport.boundedNextInt(generatingGenerator, origin, bound));
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
                    ArbitrarilyJumpableGenerator r = generatingGenerator;
                    int o = origin, b = bound;
                    do {
                        consumer.accept(RandomSupport.boundedNextInt(r, o, b));
                    } while (++i < f);
                }
            }
        }

        /**
         * Spliterator for long streams.
         */
        static class RandomLongsSpliterator extends RandomSupport.RandomSpliterator implements Spliterator.OfLong {
            final ArbitrarilyJumpableGenerator generatingGenerator;
            final long origin;
            final long bound;

            RandomLongsSpliterator(ArbitrarilyJumpableGenerator generatingGenerator, long index, long fence, long origin, long bound) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator;
                this.origin = origin; this.bound = bound;
            }

            public Spliterator.OfLong trySplit() {
                long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
                if (m <= i) return null;
                index = m;
                ArbitrarilyJumpableGenerator r = generatingGenerator;
                return new RandomLongsSpliterator(r.copyAndJump((double)delta), i, m, origin, bound);
            }

            public boolean tryAdvance(LongConsumer consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(RandomSupport.boundedNextLong(generatingGenerator, origin, bound));
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
                    ArbitrarilyJumpableGenerator r = generatingGenerator;
                    long o = origin, b = bound;
                    do {
                        consumer.accept(RandomSupport.boundedNextLong(r, o, b));
                    } while (++i < f);
                }
            }
        }

        /**
         * Spliterator for double streams.
         */
        static class RandomDoublesSpliterator extends RandomSupport.RandomSpliterator implements Spliterator.OfDouble {
            final ArbitrarilyJumpableGenerator generatingGenerator;
            final double origin;
            final double bound;

            RandomDoublesSpliterator(ArbitrarilyJumpableGenerator generatingGenerator, long index, long fence, double origin, double bound) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator;
                this.origin = origin; this.bound = bound;
            }

            public Spliterator.OfDouble trySplit() {

                long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
                if (m <= i) return null;
                index = m;
                ArbitrarilyJumpableGenerator r = generatingGenerator;
                return new RandomDoublesSpliterator(r.copyAndJump((double)delta), i, m, origin, bound);
            }

            public boolean tryAdvance(DoubleConsumer consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(RandomSupport.boundedNextDouble(generatingGenerator, origin, bound));
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
                    ArbitrarilyJumpableGenerator r = generatingGenerator;
                    double o = origin, b = bound;
                    do {
                        consumer.accept(RandomSupport.boundedNextDouble(r, o, b));
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
         * Spliterator for stream of generators of type RandomGenerator produced by jumps.
         */
        static class RandomJumpsSpliterator extends RandomSupport.RandomSpliterator implements Spliterator<RandomGenerator> {
            ArbitrarilyJumpableGenerator generatingGenerator;
            final double distance;

            RandomJumpsSpliterator(ArbitrarilyJumpableGenerator generatingGenerator, long index, long fence, double distance) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator; this.distance = distance;
            }

            public Spliterator<RandomGenerator> trySplit() {
                long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
                if (m <= i) return null;
                index = m;
                ArbitrarilyJumpableGenerator r = generatingGenerator;
                // Because delta is a power of two, (distance * (double)delta) can always be computed exactly.
                return new RandomJumpsSpliterator(r.copyAndJump(distance * (double)delta), i, m, distance);
            }

            public boolean tryAdvance(Consumer<? super RandomGenerator> consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(generatingGenerator.copyAndJump(distance));
                    index = i + 1;
                    return true;
                }
                return false;
            }

            public void forEachRemaining(Consumer<? super RandomGenerator> consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    index = f;
                    ArbitrarilyJumpableGenerator r = generatingGenerator;
                    do {
                        consumer.accept(r.copyAndJump(distance));
                    } while (++i < f);
                }
            }
        }

        /**
         * Spliterator for stream of generators of type RandomGenerator produced by leaps.
         */
        static class RandomLeapsSpliterator extends RandomSupport.RandomSpliterator implements Spliterator<JumpableGenerator> {
            ArbitrarilyJumpableGenerator generatingGenerator;
            final double distance;

            RandomLeapsSpliterator(ArbitrarilyJumpableGenerator generatingGenerator, long index, long fence, double distance) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator; this.distance = distance;
            }

            public Spliterator<JumpableGenerator> trySplit() {
                long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
                if (m <= i) return null;
                index = m;
                // Because delta is a power of two, (distance * (double)delta) can always be computed exactly.
                return new RandomLeapsSpliterator(generatingGenerator.copyAndJump(distance * (double)delta), i, m, distance);
            }

            public boolean tryAdvance(Consumer<? super JumpableGenerator> consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(generatingGenerator.copyAndJump(distance));
                    index = i + 1;
                    return true;
                }
                return false;
            }

            public void forEachRemaining(Consumer<? super JumpableGenerator> consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    index = f;
                    ArbitrarilyJumpableGenerator r = generatingGenerator;
                    do {
                        consumer.accept(r.copyAndJump(distance));
                    } while (++i < f);
                }
            }
        }

        /**
         * Spliterator for stream of generators of type RandomGenerator produced by arbitrary jumps.
         */
        static class RandomArbitraryJumpsSpliterator extends RandomSupport.RandomSpliterator implements Spliterator<ArbitrarilyJumpableGenerator> {
            ArbitrarilyJumpableGenerator generatingGenerator;
            final double distance;

            RandomArbitraryJumpsSpliterator(ArbitrarilyJumpableGenerator generatingGenerator, long index, long fence, double distance) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator; this.distance = distance;
            }

            public Spliterator<ArbitrarilyJumpableGenerator> trySplit() {
                long i = index, delta = Long.highestOneBit((fence - i) - 1), m = i + delta;
                if (m <= i) return null;
                index = m;
                // Because delta is a power of two, (distance * (double)delta) can always be computed exactly.
                return new RandomArbitraryJumpsSpliterator(generatingGenerator.copyAndJump(distance * (double)delta), i, m, distance);
            }

            public boolean tryAdvance(Consumer<? super ArbitrarilyJumpableGenerator> consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(generatingGenerator.copyAndJump(distance));
                    index = i + 1;
                    return true;
                }
                return false;
            }

            public void forEachRemaining(Consumer<? super ArbitrarilyJumpableGenerator> consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    index = f;
                    ArbitrarilyJumpableGenerator r = generatingGenerator;
                    do {
                        consumer.accept(r.copyAndJump(distance));
                    } while (++i < f);
                }
            }
        }

    }

    /**
     * This class provides much of the implementation of the {@link SplittableGenerator} interface, to
     * minimize the effort required to implement this interface.
     * <p>
     * To implement a pseudorandom number generator, the programmer needs only to extend this class and
     * provide implementations for the methods {@code nextInt()}, {@code nextLong()}, {@code period()},
     * and {@code split(SplittableGenerator)}.
     * <p>
     * (If the pseudorandom number generator also has the ability to jump an arbitrary
     * specified distance, then the programmer may wish to consider instead extending the
     * class {@link AbstractArbitrarilyJumpableGenerator}.  See also the class
     * {@link AbstractSplittableWithBrineGenerator}.)
     * <p>
     * The programmer should generally provide at least three constructors: one that takes no arguments,
     * one that accepts a {@code long} seed value, and one that accepts an array of seed {@code byte}
     * values. This class provides a public {@code initialSeed()} method that may be useful in
     * initializing some static state from which to derive defaults seeds for use by the no-argument
     * constructor.
     * <p>
     * For the stream methods (such as {@code ints()} and {@code splits()}), this class provides
     * {@link Spliterator}-based implementations that allow parallel execution when appropriate.
     * <p>
     * The documentation for each non-abstract method in this class describes its implementation in
     * detail. Each of these methods may be overridden if the pseudorandom number generator being
     * implemented admits a more efficient implementation.
     *
     * @since 14
     */
    public abstract static class AbstractSplittableGenerator extends AbstractSpliteratorGenerator implements SplittableGenerator {

        /*
         * Implementation Overview.
         *
         * This class provides most of the "user API" methods needed to
         * satisfy the interface SplittableGenerator.  Most of these methods
         * are in turn inherited from AbstractGenerator and the non-public class
         * AbstractSpliteratorGenerator; this class provides two versions of the
         * splits method and defines the spliterators necessary to support
         * them.
         *
         * File organization: First the non-public methods needed by the class
         * AbstractSpliteratorGenerator, then the main public methods, followed by some
         * custom spliterator classes.
         */

        public Spliterator.OfInt makeIntsSpliterator(long index, long fence, int origin, int bound) {
            return new RandomIntsSpliterator(this, index, fence, origin, bound);
        }

        public Spliterator.OfLong makeLongsSpliterator(long index, long fence, long origin, long bound) {
            return new RandomLongsSpliterator(this, index, fence, origin, bound);
        }

        public Spliterator.OfDouble makeDoublesSpliterator(long index, long fence, double origin, double bound) {
            return new RandomDoublesSpliterator(this, index, fence, origin, bound);
        }

        Spliterator<SplittableGenerator> makeSplitsSpliterator(long index, long fence, SplittableGenerator source) {
            return new RandomSplitsSpliterator(source, index, fence, this);
        }

        /* ---------------- public methods ---------------- */

        /**
         * Implements the @code{split()} method as {@code this.split(this)}.
         *
         * @return the new {@link SplittableGenerator} instance
         */
        public SplittableGenerator split() {
            return this.split(this);
        }

        // Stream methods for splittings

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of which
         * implements the {@link SplittableGenerator} interface.
         * <p>
         * This pseudorandom number generator provides the entropy used to seed the new ones.
         *
         * @return a stream of {@link SplittableGenerator} objects
         *
         * @implNote This method is implemented to be equivalent to {@code splits(Long.MAX_VALUE)}.
         */
        public Stream<SplittableGenerator> splits() {
            return this.splits(Long.MAX_VALUE, this);
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of new pseudorandom number
         * generators, each of which implements the {@link SplittableGenerator} interface.
         * <p>
         * This pseudorandom number generator provides the entropy used to seed the new ones.
         *
         * @param streamSize the number of values to generate
         *
         * @return a stream of {@link SplittableGenerator} objects
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        public Stream<SplittableGenerator> splits(long streamSize) {
            return this.splits(streamSize, this);
        }

        /**
         * Returns an effectively unlimited stream of new pseudorandom number generators, each of which
         * implements the {@link SplittableGenerator} interface.
         *
         * @param source a {@link SplittableGenerator} instance to be used instead of this one as a source of
         *               pseudorandom bits used to initialize the state of the new ones.
         *
         * @return a stream of {@link SplittableGenerator} objects
         *
         * @implNote This method is implemented to be equivalent to {@code splits(Long.MAX_VALUE)}.
         */
        public Stream<SplittableGenerator> splits(SplittableGenerator source) {
            return this.splits(Long.MAX_VALUE, source);
        }

        /**
         * Returns a stream producing the given {@code streamSize} number of new pseudorandom number
         * generators, each of which implements the {@link SplittableGenerator} interface.
         *
         * @param streamSize the number of values to generate
         * @param source     a {@link SplittableGenerator} instance to be used instead of this one as a source
         *                   of pseudorandom bits used to initialize the state of the new ones.
         *
         * @return a stream of {@link SplittableGenerator} objects
         *
         * @throws IllegalArgumentException if {@code streamSize} is less than zero
         */
        public Stream<SplittableGenerator> splits(long streamSize, SplittableGenerator source) {
            RandomSupport.checkStreamSize(streamSize);
            return StreamSupport.stream(makeSplitsSpliterator(0L, streamSize, source), false);
        }

        /**
         * Spliterator for int streams.  We multiplex the four int versions into one class by treating a
         * bound less than origin as unbounded, and also by treating "infinite" as equivalent to
         * {@code Long.MAX_VALUE}. For splits, it uses the standard divide-by-two approach. The long and
         * double versions of this class are identical except for types.
         */
        static class RandomIntsSpliterator extends RandomSupport.RandomSpliterator implements Spliterator.OfInt {
            final SplittableGenerator generatingGenerator;
            final int origin;
            final int bound;

            RandomIntsSpliterator(SplittableGenerator generatingGenerator, long index, long fence, int origin, int bound) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator;
                this.origin = origin; this.bound = bound;
            }

            public Spliterator.OfInt trySplit() {
                long i = index, m = (i + fence) >>> 1;
                if (m <= i) return null;
                index = m;
                return new RandomIntsSpliterator(generatingGenerator.split(), i, m, origin, bound);
            }

            public boolean tryAdvance(IntConsumer consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(RandomSupport.boundedNextInt(generatingGenerator, origin, bound));
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
                    RandomGenerator r = generatingGenerator;
                    int o = origin, b = bound;
                    do {
                        consumer.accept(RandomSupport.boundedNextInt(r, o, b));
                    } while (++i < f);
                }
            }
        }

        /**
         * Spliterator for long streams.
         */
        static class RandomLongsSpliterator extends RandomSupport.RandomSpliterator implements Spliterator.OfLong {
            final SplittableGenerator generatingGenerator;
            final long origin;
            final long bound;

            RandomLongsSpliterator(SplittableGenerator generatingGenerator, long index, long fence, long origin, long bound) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator;
                this.origin = origin; this.bound = bound;
            }

            public Spliterator.OfLong trySplit() {
                long i = index, m = (i + fence) >>> 1;
                if (m <= i) return null;
                index = m;
                return new RandomLongsSpliterator(generatingGenerator.split(), i, m, origin, bound);
            }

            public boolean tryAdvance(LongConsumer consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(RandomSupport.boundedNextLong(generatingGenerator, origin, bound));
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
                    RandomGenerator r = generatingGenerator;
                    long o = origin, b = bound;
                    do {
                        consumer.accept(RandomSupport.boundedNextLong(r, o, b));
                    } while (++i < f);
                }
            }
        }

        /**
         * Spliterator for double streams.
         */
        static class RandomDoublesSpliterator extends RandomSupport.RandomSpliterator implements Spliterator.OfDouble {
            final SplittableGenerator generatingGenerator;
            final double origin;
            final double bound;

            RandomDoublesSpliterator(SplittableGenerator generatingGenerator, long index, long fence, double origin, double bound) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator;
                this.origin = origin; this.bound = bound;
            }

            public Spliterator.OfDouble trySplit() {
                long i = index, m = (i + fence) >>> 1;
                if (m <= i) return null;
                index = m;
                return new RandomDoublesSpliterator(generatingGenerator.split(), i, m, origin, bound);
            }

            public boolean tryAdvance(DoubleConsumer consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(RandomSupport.boundedNextDouble(generatingGenerator, origin, bound));
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
                    RandomGenerator r = generatingGenerator;
                    double o = origin, b = bound;
                    do {
                        consumer.accept(RandomSupport.boundedNextDouble(r, o, b));
                    } while (++i < f);
                }
            }
        }

        /**
         * Spliterator for stream of generators of type SplittableGenerator.  We multiplex the two
         * versions into one class by treating "infinite" as equivalent to Long.MAX_VALUE.
         * For splits, it uses the standard divide-by-two approach.
         */
        static class RandomSplitsSpliterator extends RandomSpliterator implements Spliterator<SplittableGenerator> {
            final SplittableGenerator generatingGenerator;
            final SplittableGenerator constructingGenerator;

            RandomSplitsSpliterator(SplittableGenerator generatingGenerator,
				    long index, long fence,
				    SplittableGenerator constructingGenerator) {
                super(index, fence);
                this.generatingGenerator = generatingGenerator;
                this.constructingGenerator = constructingGenerator;
            }

            public Spliterator<SplittableGenerator> trySplit() {
                long i = index, m = (i + fence) >>> 1;
                if (m <= i) return null;
                index = m;
                return new RandomSplitsSpliterator(generatingGenerator.split(), i, m, constructingGenerator);
            }

            public boolean tryAdvance(Consumer<? super SplittableGenerator> consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    consumer.accept(constructingGenerator.split(generatingGenerator));
                    index = i + 1;
                    return true;
                }
                else return false;
            }

            public void forEachRemaining(Consumer<? super SplittableGenerator> consumer) {
                if (consumer == null) throw new NullPointerException();
                long i = index, f = fence;
                if (i < f) {
                    index = f;
                    SplittableGenerator c = constructingGenerator;
                    SplittableGenerator r = generatingGenerator;
                    do {
                        consumer.accept(c.split(r));
                    } while (++i < f);
                }
            }
        }

    }

    /**
     * This class provides much of the implementation of the {@link SplittableGenerator} interface, to
     * minimize the effort required to implement this interface.  It is similar to the class
     * {@link AbstractSplittableGenerator} but makes use of the brine technique for ensuring that
     * distinct generators created by a single call to a {@code splits} method have distinct state cycles.
     * <p>
     * To implement a pseudorandom number generator, the programmer needs only to extend this class and
     * provide implementations for the methods {@code nextInt()}, {@code nextLong()}, {@code period()},
     * and {@code split(SplittableGenerator, long)}.
     * <p>
     * The programmer should generally provide at least three constructors: one that takes no arguments,
     * one that accepts a {@code long} seed value, and one that accepts an array of seed {@code byte}
     * values. This class provides a public {@code initialSeed()} method that may be useful in
     * initializing some static state from which to derive defaults seeds for use by the no-argument
     * constructor.
     * <p>
     * For the stream methods (such as {@code ints()} and {@code splits()}), this class provides
     * {@link Spliterator}-based implementations that allow parallel execution when appropriate.
     * <p>
     * The documentation for each non-abstract method in this class describes its implementation in
     * detail. Each of these methods may be overridden if the pseudorandom number generator being
     * implemented admits a more efficient implementation.
     *
     * @since 14
     */
    public abstract static class AbstractSplittableWithBrineGenerator
	extends AbstractSplittableGenerator {

	/*
	 * Implementation Overview.
	 *
         * This class provides most of the "user API" methods needed to
         * satisfy the interface SplittableGenerator.  Most of these methods
         * are in turn inherited from AbstractSplittableGenerator and the non-public class
         * AbstractSpliteratorGenerator; this class provides four versions of the
         * splits method and defines the spliterators necessary to support
         * them.
	 *
	 * File organization: First the non-public methods needed by the class
	 * AbstractSplittableWithBrineGenerator, then the main public methods,
	 * followed by some custom spliterator classes needed for stream methods.
	 */

	// The salt consists groups of bits each SALT_SHIFT in size, starting from
	// the left-hand (high-order) end of the word.  We can regard them as
	// digits base (1 << SALT_SHIFT).  If SALT_SHIFT does not divide 64
	// evenly, then any leftover bits at the low end of the word are zero.
	// The lowest digit of the salt is set to the largest possible digit
	// (all 1-bits, or ((1 << SALT_SHIFT) - 1)); all other digits are set
	// to a randomly chosen value less than that largest possible digit.
	// The salt may be shifted left by SALT_SHIFT any number of times.
	// If any salt remains in the word, its right-hand end can be identified
	// by searching from left to right for an occurrence of a digit that is
	// all 1-bits (not that we ever do that; this is simply a proof that one
	// can identify the boundary between the salt and the index if any salt
	// remains in the word).  The idea is that before computing the bitwise OR
	// of an index and the salt, one can first check to see whether the
	// bitwise AND is nonzero; if so, one can shift the salt left by
	// SALT_SHIFT and try again.  In this way, when the bitwise OR is
	// computed, if the salt is nonzero then its rightmost 1-bit is to the
	// left of the leftmost 1-bit of the index.

	// We need 2 <= SALT_SHIFT <= 63 (3 through 8 are good values; 4 is probably best)
	static final int SALT_SHIFT = 4;

	// Methods required by class AbstractSpliteratorGenerator (override)
	Spliterator<SplittableGenerator> makeSplitsSpliterator(long index, long fence, SplittableGenerator source) {
	    // This little algorithm to generate a new salt value is carefully
	    // designed to work even if SALT_SHIFT does not evenly divide 64
	    // (the number of bits in a long value).
	    long bits = nextLong();
	    long multiplier = (1 << SALT_SHIFT) - 1;
	    long salt = multiplier << (64 - SALT_SHIFT);
	    while ((salt & multiplier) != 0) {
		long digit = Math.multiplyHigh(bits, multiplier);
		salt = (salt >>> SALT_SHIFT) | (digit << (64 - SALT_SHIFT));
		bits *= multiplier;
	    }
	    // This is the point at which newly generated salt gets injected into
	    // the root of a newly created brine-generating splits-spliterator.
	    return new RandomSplitsSpliteratorWithSalt(source, index, fence, this, salt);
	}

	/* ---------------- public methods ---------------- */

	// Stream methods for splitting

	/**
	 * Constructs and returns a new instance of {@code AbstractSplittableWithBrineGenerator}
	 * that shares no mutable state with this instance. However, with very high
	 * probability, the set of values collectively generated by the two objects
	 * should have the same statistical properties as if the same quantity of
	 * values were generated by a single thread using a single may be
	 * {@code AbstractSplittableWithBrineGenerator} object. Either or both of the two objects
	 * further split using the {@code split()} method, and the same expected
	 * statistical properties apply to the entire set of generators constructed
	 * by such recursive splitting.
	 *
	 * @param brine a long value, of which the low 63 bits provide a unique id
	 * among calls to this method for constructing a single series of Generator objects.
	 *
	 * @return the new {@code AbstractSplittableWithBrineGenerator} instance
	 */
	public SplittableGenerator split(long brine) {
	    return this.split(this, brine);
	}

	/**
	 * Constructs and returns a new instance of {@code L64X128MixRandom}
	 * that shares no mutable state with this instance.
	 * However, with very high probability, the set of values collectively
	 * generated by the two objects has the same statistical properties as if
	 * same the quantity of values were generated by a single thread using
	 * a single {@code L64X128MixRandom} object.  Either or both of the two
	 * objects may be further split using the {@code split} method,
	 * and the same expected statistical properties apply to the
	 * entire set of generators constructed by such recursive splitting.
	 *
	 * @param source a {@code SplittableGenerator} instance to be used instead
	 *               of this one as a source of pseudorandom bits used to
	 *               initialize the state of the new ones.
	 * @return a new instance of {@code L64X128MixRandom}
	 */
	public SplittableGenerator split(SplittableGenerator source) {
	    // It's a one-off: supply randomly chosen brine
	    return this.split(source, source.nextLong());
	}

	/**
	 * Constructs and returns a new instance of {@code AbstractSplittableWithBrineGenerator}
	 * that shares no mutable state with this instance. However, with very high
	 * probability, the set of values collectively generated by the two objects
	 * should have the same statistical properties as if the same quantity of
	 * values were generated by a single thread using a single may be
	 * {@code AbstractSplittableWithBrineGenerator} object. Either or both of the two objects
	 * further split using the {@code split()} method, and the same expected
	 * statistical properties apply to the entire set of generators constructed
	 * by such recursive splitting.
	 *
	 * @param source a {@code SplittableGenerator} instance to be used instead
	 *               of this one as a source of pseudorandom bits used to
	 *               initialize the state of the new ones.
	 * @param brine a long value, of which the low 63 bits provide a unique id
	 *              among calls to this method for constructing a single series of
	 *              {@code RandomGenerator} objects.
	 *
	 * @return the new {@code AbstractSplittableWithBrineGenerator} instance
	 */
	public abstract SplittableGenerator split(SplittableGenerator source, long brine);


	/* ---------------- spliterator ---------------- */
	/**
	 * Alternate spliterator for stream of generators of type SplittableGenerator.  We multiplex
	 * the two versions into one class by treating "infinite" as equivalent to Long.MAX_VALUE.
	 * For splits, it uses the standard divide-by-two approach.
	 *
	 * This differs from {@code SplittableGenerator.RandomSplitsSpliterator} in that it provides
	 * a brine argument (a mixture of salt and an index) when calling the {@code split} method.
	 */
	static class RandomSplitsSpliteratorWithSalt
	    extends RandomSpliterator implements Spliterator<SplittableGenerator> {

	    final SplittableGenerator generatingGenerator;
	    final AbstractSplittableWithBrineGenerator constructingGenerator;
	    long salt;

	    // Important invariant: 0 <= index <= fence

	    // Important invariant: if salt and index are both nonzero,
	    // the rightmost 1-bit of salt is to the left of the leftmost 1-bit of index.
	    // If necessary, the salt can be leftshifted by SALT_SHIFT as many times as
	    // necessary to maintain the invariant.

	    RandomSplitsSpliteratorWithSalt(SplittableGenerator generatingGenerator, long index, long fence,
					    AbstractSplittableWithBrineGenerator constructingGenerator, long salt) {
		super(index, fence);
		this.generatingGenerator = generatingGenerator;
		this.constructingGenerator = constructingGenerator;
		while ((salt != 0) && (Long.compareUnsigned(salt & -salt, index) <= 0)) {
		    salt = salt << SALT_SHIFT;
		}
		this.salt = salt;
	    }

            public Spliterator<SplittableGenerator> trySplit() {
                long i = index, m = (i + fence) >>> 1;
                if (m <= i) return null;
		RandomSplitsSpliteratorWithSalt result =
		    new RandomSplitsSpliteratorWithSalt(generatingGenerator.split(), i, m, constructingGenerator, salt);
                index = m;
		while ((salt != 0) && (Long.compareUnsigned(salt & -salt, index) <= 0)) {
		    salt = salt << SALT_SHIFT;
		}
		return result;
            }

	    public boolean tryAdvance(Consumer<? super SplittableGenerator> consumer) {
		if (consumer == null) throw new NullPointerException();
		long i = index, f = fence;
		if (i < f) {
		    consumer.accept(constructingGenerator.split(generatingGenerator, salt | i));
		    ++i;
		    index = i;
		    if ((i & salt) != 0) salt <<= SALT_SHIFT;
		    return true;
		}
		return false;
	    }

	    public void forEachRemaining(Consumer<? super SplittableGenerator> consumer) {
		if (consumer == null) throw new NullPointerException();
		long i = index, f = fence;
		if (i < f) {
		    index = f;
		    AbstractSplittableWithBrineGenerator c = constructingGenerator;
		    SplittableGenerator r = generatingGenerator;
		    do {
			consumer.accept(c.split(r, salt | i));
			++i;
			if ((i & salt) != 0) salt <<= SALT_SHIFT;
		    } while (i < f);
		}
	    }
	}

    }

}
