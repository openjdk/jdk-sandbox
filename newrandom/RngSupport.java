/*
 * Copyright (c) 2013, 2016, 2019, Oracle and/or its affiliates. All rights reserved.
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
// import java.util.DoubleZigguratTables;

/**
 * Low-level utility methods helpful for implementing pseudorandom number generators.
 *
 * This class is mostly for library writers creating specific implementations of the interface {@link java.util.Rng}.
 *
 * @author  Guy Steele
 * @author  Doug Lea
 * @since   1.9
 */
public class RngSupport {

    /*
     * Implementation Overview.
     *
     * This class provides utility methods and constants frequently
     * useful in the implentation of pseudorandom number generators
     * that satisfy the interface {@code java.util.Rng}.
     *
     * File organization: First some message strings, then the main
     * public methods, followed by a non-public base spliterator class.
     */

    // IllegalArgumentException messages
    static final String BadSize = "size must be non-negative";
    static final String BadDistance = "jump distance must be finite, positive, and an exact integer";
    static final String BadBound = "bound must be positive";
    static final String BadFloatingBound = "bound must be finite and positive";
    static final String BadRange = "bound must be greater than origin";

    /* ---------------- public methods ---------------- */

    /**
     * Check a {@code long} proposed stream size for validity.
     *
     * @param streamSize the proposed stream size
     * @throws IllegalArgumentException if {@code streamSize} is negative
     */
    public static void checkStreamSize(long streamSize) {
	if (streamSize < 0L)
            throw new IllegalArgumentException(BadSize);
    }

    /**
     * Check a {@code double} proposed jump distance for validity.
     *
     * @param distance the proposed jump distance
     * @throws IllegalArgumentException if {@code size} not positive,
     * finite, and an exact integer
     */
    public static void checkJumpDistance(double distance) {
	if (!(distance > 0.0 && distance < Float.POSITIVE_INFINITY && distance == Math.floor(distance)))
            throw new IllegalArgumentException(BadDistance);
    }

    /**
     * Checks a {@code float} upper bound value for validity.
     *
     * @param bound the upper bound (exclusive)
     * @throws IllegalArgumentException if {@code bound} is not
     *         positive and finite
     */
    public static void checkBound(float bound) {
	if (!(bound > 0.0 && bound < Float.POSITIVE_INFINITY))
            throw new IllegalArgumentException(BadFloatingBound);
    }

    /**
     * Checks a {@code double} upper bound value for validity.
     *
     * @param bound the upper bound (exclusive)
     * @throws IllegalArgumentException if {@code bound} is not
     *         positive and finite
     */
    public static void checkBound(double bound) {
	if (!(bound > 0.0 && bound < Double.POSITIVE_INFINITY))
            throw new IllegalArgumentException(BadFloatingBound);
    }

    /**
     * Checks an {@code int} upper bound value for validity.
     *
     * @param bound the upper bound (exclusive)
     * @throws IllegalArgumentException if {@code bound} is not positive
     */
    public static void checkBound(int bound) {
        if (bound <= 0)
            throw new IllegalArgumentException(BadBound);
    }

    /**
     * Checks a {@code long} upper bound value for validity.
     *
     * @param bound the upper bound (exclusive)
     * @throws IllegalArgumentException if {@code bound} is not positive
     */
    public static void checkBound(long bound) {
        if (bound <= 0)
            throw new IllegalArgumentException(BadBound);
    }

    /**
     * Checks a {@code float} range for validity.
     *
     * @param origin the least value (inclusive) in the range
     * @param bound the upper bound (exclusive) of the range
     * @throws IllegalArgumentException unless {@code origin} is finite,
     *         {@code bound} is finite, and {@code bound - origin} is finite
     */
    public static void checkRange(float origin, float bound) {
        if (!(origin < bound && (bound - origin) < Float.POSITIVE_INFINITY))
            throw new IllegalArgumentException(BadRange);
    }

    /**
     * Checks a {@code double} range for validity.
     *
     * @param origin the least value (inclusive) in the range
     * @param bound the upper bound (exclusive) of the range
     * @throws IllegalArgumentException unless {@code origin} is finite,
     *         {@code bound} is finite, and {@code bound - origin} is finite
     */
    public static void checkRange(double origin, double bound) {
        if (!(origin < bound && (bound - origin) < Double.POSITIVE_INFINITY))
            throw new IllegalArgumentException(BadRange);
    }

    /**
     * Checks an {@code int} range for validity.
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive) for the returned value
     * @throws IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     */
    public static void checkRange(int origin, int bound) {
        if (origin >= bound)
            throw new IllegalArgumentException(BadRange);
    }

    /**
     * Checks a {@code long} range for validity.
     *
     * @param origin the least value that can be returned
     * @param bound the upper bound (exclusive) for the returned value
     * @throws IllegalArgumentException if {@code origin} is greater than
     *         or equal to {@code bound}
     */
    public static void checkRange(long origin, long bound) {
        if (origin >= bound)
            throw new IllegalArgumentException(BadRange);
    }

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
     * This is the form of {@code nextLong} used by a {@code LongStream}
     * {@code Spliterator} and by the public method
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
     *
     * <p> The implementation considers four cases:
     * <ol>
     *
     * <li> If the {@code} bound} is less than or equal to the {@code origin}
     *      (indicated an unbounded form), the 64-bit {@code long} value
     *      obtained from {@code nextLong()} is returned directly.
     *
     * <li> Otherwise, if the length <it>n</it> of the specified range is an
     *      exact power of two 2<sup><it>m</it></sup> for some integer
     *      <it>m</it>, then return the sum of {@code origin} and the
     *      <it>m</it> lowest-order bits of the value from {@code nextLong()}.
     *
     * <li> Otherwise, if the length <it>n</it> of the specified range
     *      is less than 2<sup>63</sup>, then the basic idea is to use the
     *      remainder modulo <it>n</it> of the value from {@code nextLong()},
     *      but with this approach some values will be over-represented.
     *      Therefore a loop is used to avoid potential bias by rejecting
     *      candidates that are too large.  Assuming that the results from
     *      {@code nextLong()} are truly chosen uniformly and independently,
     *      the expected number of iterations will be somewhere between
     *      1 and 2, depending on the precise value of <it>n</it>.
     *
     * <li> Otherwise, the length <it>n</it> of the specified range
     *      cannot be represented as a positive {@code long} value.
     *      A loop repeatedly calls {@code nextlong()} until obtaining
     *      a suitable candidate,  Again, the expected number of iterations
     *      is less than 2.
     *
     * </ol>
     *
     * @param origin the least value that can be produced,
     *        unless greater than or equal to {@code bound}
     * @param bound the upper bound (exclusive), unless {@code origin}
     *        is greater than or equal to {@code bound}
     * @return a pseudorandomly chosen {@code long} value,
     *         which will be between {@code origin} (inclusive) and
     *         {@code bound} exclusive unless {@code origin}
     *         is greater than or equal to {@code bound}
     */
    public static long boundedNextLong(Rng rng, long origin, long bound) {
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
     *
     * <p> The implementation considers two cases:
     * <ol>
     *
     * <li> If {@code bound} is an exact power of two 2<sup><it>m</it></sup>
     *      for some integer <it>m</it>, then return the sum of {@code origin}
     *      and the <it>m</it> lowest-order bits of the value from
     *      {@code nextLong()}.
     *
     * <li> Otherwise, the basic idea is to use the remainder modulo
     *      <it>bound</it> of the value from {@code nextLong()},
     *      but with this approach some values will be over-represented.
     *      Therefore a loop is used to avoid potential bias by rejecting
     *      candidates that vare too large.  Assuming that the results from
     *      {@code nextLong()} are truly chosen uniformly and independently,
     *      the expected number of iterations will be somewhere between
     *      1 and 2, depending on the precise value of <it>bound</it>.
     *
     * </ol>
     *
     * @param bound the upper bound (exclusive); must be greater than zero
     * @return a pseudorandomly chosen {@code long} value
     */
    public static long boundedNextLong(Rng rng, long bound) {
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
     * This is the form of {@code nextInt} used by an {@code IntStream}
     * {@code Spliterator} and by the public method
     * {@code nextInt(origin, bound)}.  If {@code origin} is greater
     * than {@code bound}, then this method simply calls the unbounded
     * version of {@code nextInt()}, choosing pseudorandomly from
     * among all 2<sup>64</sup> possible {@code int} values}, and
     * otherwise uses one or more calls to {@code nextInt()} to
     * choose a value pseudorandomly from the possible values
     * between {@code origin} (inclusive) and {@code bound} (exclusive).
     *
     * @implNote The implementation of this method is identical to
     *     the implementation of {@code nextLong(origin, bound)}
     *     except that {@code int} values and the {@code nextInt()}
     *     method are used rather than {@code long} values and the
     *     {@code nextLong()} method.
     *
     * @param origin the least value that can be produced,
     *        unless greater than or equal to {@code bound}
     * @param bound the upper bound (exclusive), unless {@code origin}
     *        is greater than or equal to {@code bound}
     * @return a pseudorandomly chosen {@code int} value,
     *         which will be between {@code origin} (inclusive) and
     *         {@code bound} exclusive unless {@code origin}
     *         is greater than or equal to {@code bound}
     */
    public static int boundedNextInt(Rng rng, int origin, int bound) {
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
                while (r < origin || r >= bound)


		    r = rng.nextInt();
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
     * @implNote The implementation of this method is identical to
     *     the implementation of {@code nextLong(bound)}
     *     except that {@code int} values and the {@code nextInt()}
     *     method are used rather than {@code long} values and the
     *     {@code nextLong()} method.
     *
     * @param bound the upper bound (exclusive); must be greater than zero
     * @return a pseudorandomly chosen {@code long} value
     */
    public static int boundedNextInt(Rng rng, int bound) {
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
     * This is the form of {@code nextDouble} used by a {@code DoubleStream}
     * {@code Spliterator} and by the public method
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
    public static double boundedNextDouble(Rng rng, double origin, double bound) {
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
     * @param bound the upper bound (exclusive); must be finite and
     *        greater than zero
     * @return a pseudorandomly chosen {@code double} value
     *         between zero (inclusive) and {@code bound} (exclusive)
     */
    public static double boundedNextDouble(Rng rng, double bound) {
        // Specialize boundedNextDouble for origin == 0, bound > 0
        double r = rng.nextDouble();
	r = r * bound;
	if (r >= bound)  // may need to correct a rounding problem
	    r = Double.longBitsToDouble(Double.doubleToLongBits(bound) - 1);
        return r;
    }

    /**
     * This is the form of {@code nextFloat} used by a {@code FloatStream}
     * {@code Spliterator} (if there were any) and by the public method
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
    public static float boundedNextFloat(Rng rng, float origin, float bound) {
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
     * @param bound the upper bound (exclusive); must be finite and
     *        greater than zero
     * @return a pseudorandomly chosen {@code float} value
     *         between zero (inclusive) and {@code bound} (exclusive)
     */
    public static float boundedNextFloat(Rng rng, float bound) {
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
     * initializing a source of seed values for instances of {@code Rng}
     * created by zero-argument constructors.  (This method should
     * <it>not</it> be called repeatedly, once per constructed
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
     * The fractional part (first 32 or 64 bits, then forced odd) of
     * the golden ratio (1+sqrt(5))/2 and of the silver ratio 1+sqrt(2).
     * Useful for producing good Weyl sequences or as arbitrary nonzero values.
     */
    public static final int  GOLDEN_RATIO_32 = 0x9e3779b9;
    public static final long GOLDEN_RATIO_64 = 0x9e3779b97f4a7c15L;
    public static final int  SILVER_RATIO_32 = 0x6A09E667;
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

    // Non-public (package only) support for spliterators needed by AbstractSplittableRng
    // and AbstractArbitrarilyJumpableRng and AbstractSharedRng

    /**
     * Base class for making Spliterator classes for streams of randomly chosen values.
     */
    static abstract class RandomSpliterator {
        long index;
        final long fence;

	RandomSpliterator(long index, long fence) {
	    this.index = index; this.fence = fence;
        }
	
        public long estimateSize() {
            return fence - index;
        }

        public int characteristics() {
            return (Spliterator.SIZED | Spliterator.SUBSIZED |
                    Spliterator.NONNULL | Spliterator.IMMUTABLE);
        }
    }
    
    
    /* 
     * Implementation support for nextExponential() and nextGaussian() methods of Rng.
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

    static double computeNextExponential(Rng rng) {
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
		j = DoubleZigguratTables.exponentialAliasMap[j] & DoubleZigguratTables.exponentialSignCorrectionMask;
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
			// We picked a point in the upper-right triangle.  None of those can be accepted.
			// So remap the point into the lower-left triangle and try that.
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

    static double computeNextGaussian(Rng rng) {
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
		if (y <= Math.exp(-0.5*x*x)) break;                // The chosen point is below the curve; accept it.
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
    
}
    
