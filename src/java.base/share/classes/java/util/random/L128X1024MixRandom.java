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

import java.math.BigInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.random.RandomGenerator.SplittableGenerator;
import java.util.random.RandomSupport.AbstractSplittableWithBrineGenerator;

/**
 * A generator of uniform pseudorandom values applicable for use in
 * (among other contexts) isolated parallel computations that may
 * generate subtasks.  Class {@link L128X1024MixRandom} implements
 * interfaces {@link RandomGenerator} and {@link SplittableGenerator},
 * and therefore supports methods for producing pseudorandomly chosen
 * numbers of type {@code int}, {@code long}, {@code float}, and {@code double}
 * as well as creating new split-off {@link L128X1024MixRandom} objects,
 * with similar usages as for class {@link java.util.SplittableRandom}.
 * <p>
 * Series of generated values pass the TestU01 BigCrush and PractRand test suites
 * that measure independence and uniformity properties of random number generators.
 * (Most recently validated with
 * <a href="http://simul.iro.umontreal.ca/testu01/tu01.html">version 1.2.3 of TestU01</a>
 * and <a href="http://pracrand.sourceforge.net">version 0.90 of PractRand</a>.
 * Note that TestU01 BigCrush was used to test not only values produced by the {@code nextLong()}
 * method but also the result of bit-reversing each value produced by {@code nextLong()}.)
 * These tests validate only the methods for certain
 * types and ranges, but similar properties are expected to hold, at
 * least approximately, for others as well.
 * <p>
 * {@link L128X1024MixRandom} is a specific member of the LXM family of algorithms
 * for pseudorandom number generators.  Every LXM generator consists of two
 * subgenerators; one is an LCG (Linear Congruential Generator) and the other is
 * an Xorshift generator.  Each output of an LXM generator is the result of
 * combining state from the LCG with state from the Xorshift generator by
 * using a Mixing function (and then the state of the LCG and the state of the
 * Xorshift generator are advanced).
 * <p>
 * The LCG subgenerator for {@link L128X256MixRandom} has an update step of the
 * form {@code s = m * s + a}, where {@code s}, {@code m}, and {@code a} are all
 * 128-bit integers; {@code s} is the mutable state, the multiplier {@code m}
 * is fixed (the same for all instances of {@link L128X256MixRandom}) and the addend
 * {@code a} is a parameter (a final field of the instance).  The parameter
 * {@code a} is required to be odd (this allows the LCG to have the maximal
 * period, namely 2<sup>128</sup>); therefore there are 2<sup>127</sup> distinct choices
 * of parameter.
 * <p>
 * The Xorshift subgenerator for {@link L128X1024MixRandom} is the {@code xoroshiro1024}
 * algorithm (parameters 25, 27, and 36), without any final scrambler such as "+" or "**".
 * Its state consists of an array {@code x} of sixteen {@code long} values,
 * which can take on any values provided that they are not all zero.
 * The period of this subgenerator is 2<sup>1024</sup>-1.
 * <p>
 * The mixing function for {@link L128X1024MixRandom} is {@link RandomSupport.mixLea64}
 * applied to the argument {@code (sh + s0)}, where {@code sh} is the high half of {@code s}
 * and {@code s0} is the most recently computed element of {@code x}.
 * <p>
 * Because the periods 2<sup>128</sup> and 2<sup>1024</sup>-1 of the two subgenerators
 * are relatively prime, the <em>period</em> of any single {@link L128X1024MixRandom} object
 * (the length of the series of generated 64-bit values before it repeats) is the product
 * of the periods of the subgenerators, that is, 2<sup>128</sup>(2<sup>1024</sup>-1),
 * which is just slightly smaller than 2<sup>1152</sup>.  Moreover, if two distinct
 * {@link L128X1024MixRandom} objects have different {@code a} parameters, then their
 * cycles of produced values will be different.
 * <p>
 * The 64-bit values produced by the {@code nextLong()} method are exactly equidistributed.
 * For any specific instance of {@link L128X1024MixRandom}, over the course of its cycle each
 * of the 2<sup>64</sup> possible {@code long} values will be produced
 * 2<sup>64</sup>(2<sup>1024</sup>-1 times.  The values produced by the {@code nextInt()},
 * {@code nextFloat()}, and {@code nextDouble()} methods are likewise exactly equidistributed.
 * <p>
 * Moreover, 64-bit values produced by the {@code nextLong()} method are conjectured to be
 * "very nearly" 16-equidistributed: all possible 16-tuples of 64-bit values are generated,
 * and some pairs occur more often than others, but only very slightly more often.
 * However, this conjecture has not yet been proven mathematically.
 * If this conjecture is true, then the values produced by the {@code nextInt()}, {@code nextFloat()},
 * and {@code nextDouble()} methods are likewise approximately 16-equidistributed.
 * <p>
 * Method {@link #split} constructs and returns a new {@link L128X1024MixRandom}
 * instance that shares no mutable state with the current instance. However, with
 * very high probability, the values collectively generated by the two objects
 * have the same statistical properties as if the same quantity of values were
 * generated by a single thread using a single {@link L128X1024MixRandom} object.
 * This is because, with high probability, distinct {@link L128X1024MixRandom} objects
 * have distinct {@code a} parameters and therefore use distinct members of the
 * algorithmic family; and even if their {@code a} parameters are the same, with
 * very high probability they will traverse different parts of their common state
 * cycle.
 * <p>
 * As with {@link java.util.SplittableRandom}, instances of
 * {@link L128X1024MixRandom} are <em>not</em> thread-safe.
 * They are designed to be split, not shared, across threads. For
 * example, a {@link java.util.concurrent.ForkJoinTask} fork/join-style
 * computation using random numbers might include a construction
 * of the form {@code new Subtask(someL128X1024MixRandom.split()).fork()}.
 * <p>
 * This class provides additional methods for generating random
 * streams, that employ the above techniques when used in
 * {@code stream.parallel()} mode.
 * <p>
 * Instances of {@link L128X1024MixRandom} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @since 14
 */
public final class L128X1024MixRandom extends AbstractSplittableWithBrineGenerator {

    /*
     * Implementation Overview.
     *
     * The 128-bit parameter `a` is represented as two long fields `ah` and `al`.
     * The 128-bit state variable `s` is represented as two long fields `sh` and `sl`.
     *
     * The split operation uses the current generator to choose 20
     * new 64-bit long values that are then used to initialize the
     * parameters `ah` and `al`, the state variables `sh`, `sl`,
     * and the array `x` for a newly constructed generator.
     *
     * With extremely high probability, no two generators so chosen
     * will have the same `a` parameter, and testing has indicated
     * that the values generated by two instances of {@link L128X1024MixRandom}
     * will be (approximately) independent if have different values for `a`.
     *
     * The default (no-argument) constructor, in essence, uses
     * "defaultGen" to generate 20 new 64-bit values for the same
     * purpose.  Multiple generators created in this way will certainly
     * differ in their `a` parameters.  The defaultGen state must be accessed
     * in a thread-safe manner, so we use an AtomicLong to represent
     * this state.  To bootstrap the defaultGen, we start off using a
     * seed based on current time unless the
     * java.util.secureRandomSeed property is set. This serves as a
     * slimmed-down (and insecure) variant of SecureRandom that also
     * avoids stalls that may occur when using /dev/random.
     *
     * File organization: First static fields, then instance
     * fields, then constructors, then instance methods.
     */

    /* ---------------- static fields ---------------- */

    /*
     * The length of the array x.
     */

    private static final int N = 16;

    /**
     * The seed generator for default constructors.
     */
    private static final AtomicLong defaultGen = new AtomicLong(RandomSupport.initialSeed());

    /*
     * The period of this generator, which is (2**1024 - 1) * 2**128.
     */
    private static final BigInteger PERIOD =
        BigInteger.ONE.shiftLeft(N*64).subtract(BigInteger.ONE).shiftLeft(128);

    /*
     * Low half of multiplier used in the LCG portion of the algorithm;
     * the overall multiplier is (2**64 + ML).
     * Chosen based on research by Sebastiano Vigna and Guy Steele (2019).
     * The spectral scores for dimensions 2 through 8 for the multiplier 0x1d605bbb58c8abbfdLL
     * are [0.991889, 0.907938, 0.830964, 0.837980, 0.780378, 0.797464, 0.761493].
     */

    private static final long ML = 0xd605bbb58c8abbfdL;

    /* ---------------- instance fields ---------------- */

    /**
     * The parameter that is used as an additive constant for the LCG.
     * Must be odd (therefore al must be odd).
     */
    private final long ah, al;

    /**
     * The per-instance state: sh and sl for the LCG; the array x for the xorshift;
     * p is the rotating pointer into the array x.
     * At least one of the 16 elements of the array x must be nonzero.
     */
    private long sh, sl;
    private final long[] x;
    private int p = N - 1;

    /* ---------------- constructors ---------------- */

    /**
     * Basic constructor that initializes all fields from parameters.
     * It then adjusts the field values if necessary to ensure that
     * all constraints on the values of fields are met.
     *
     * @param ah high half of the additive parameter for the LCG
     * @param al low half of the additive parameter for the LCG
     * @param sh high half of the initial state for the LCG
     * @param sl low half of the initial state for the LCG
     * @param x0 first word of the initial state for the xorshift generator
     * @param x1 second word of the initial state for the xorshift generator
     * @param x2 third word of the initial state for the xorshift generator
     * @param x3 fourth word of the initial state for the xorshift generator
     * @param x4 fifth word of the initial state for the xorshift generator
     * @param x5 sixth word of the initial state for the xorshift generator
     * @param x6 seventh word of the initial state for the xorshift generator
     * @param x7 eight word of the initial state for the xorshift generator
     * @param x8 ninth word of the initial state for the xorshift generator
     * @param x9 tenth word of the initial state for the xorshift generator
     * @param x10 eleventh word of the initial state for the xorshift generator
     * @param x11 twelfth word of the initial state for the xorshift generator
     * @param x12 thirteenth word of the initial state for the xorshift generator
     * @param x13 fourteenth word of the initial state for the xorshift generator
     * @param x14 fifteenth word of the initial state for the xorshift generator
     * @param x15 sixteenth word of the initial state for the xorshift generator
     */
    public L128X1024MixRandom(long ah, long al, long sh, long sl,
			      long x0, long x1, long x2, long x3,
			      long x4, long x5, long x6, long x7,
			      long x8, long x9, long x10, long x11,
			      long x12, long x13, long x14, long x15) {
        // Force a to be odd.
	this.ah = ah;
        this.al = al | 1;
        this.sh = sh;
        this.sl = sl;
        this.x = new long[N];
        this.x[0] = x0;
        this.x[1] = x1;
        this.x[2] = x2;
        this.x[3] = x3;
        this.x[4] = x4;
        this.x[5] = x5;
        this.x[6] = x6;
        this.x[7] = x7;
        this.x[8] = x8;
        this.x[9] = x9;
        this.x[10] = x10;
        this.x[11] = x11;
        this.x[12] = x12;
        this.x[13] = x13;
        this.x[14] = x14;
        this.x[15] = x15;
        // If x0, x1, ..., x15 are all zero (very unlikely), we must choose nonzero values.
        if ((x0 | x1 | x2 | x3 | x4 | x5 | x6 | x7 | x8 | x9 | x10 | x11 | x12 | x13 | x14 | x15) == 0) {
	    long v = sh;
            // At least fifteen of the sixteen values generated here will be nonzero.
            for (int j = 0; j < N; j++) {
                this.x[j] = RandomSupport.mixStafford13(v += RandomSupport.GOLDEN_RATIO_64);
            }
        }
    }

    /**
     * Creates a new instance of {@link L128X1024MixRandom} using the
     * specified {@code long} value as the initial seed. Instances of
     * {@link L128X1024MixRandom} created with the same seed in the same
     * program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L128X1024MixRandom(long seed) {
        // Using a value with irregularly spaced 1-bits to xor the seed
        // argument tends to improve "pedestrian" seeds such as 0 or
        // other small integers.  We may as well use SILVER_RATIO_64.
        //
        // The seed is hashed by mixMurmur64 to produce the `a` parameter.
        // The seed is hashed by mixStafford13 to produce the initial `x[0]`,
        // which will then be used to produce the first generated value.
        // The other x values are filled in as if by a SplitMix PRNG with
        // GOLDEN_RATIO_64 as the gamma value and mixStafford13 as the mixer.
        this(RandomSupport.mixMurmur64(seed ^= RandomSupport.SILVER_RATIO_64),
             RandomSupport.mixMurmur64(seed += RandomSupport.GOLDEN_RATIO_64),
             0,
             1,
             RandomSupport.mixStafford13(seed),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed += RandomSupport.GOLDEN_RATIO_64),
             RandomSupport.mixStafford13(seed + RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link L128X1024MixRandom} that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program execution,
     * but may, and typically does, vary across program invocations.
     */
    public L128X1024MixRandom() {
        // Using GOLDEN_RATIO_64 here gives us a good Weyl sequence of values.
        this(defaultGen.getAndAdd(RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link L128X1024MixRandom} using the specified array of
     * initial seed bytes. Instances of {@link L128X1024MixRandom} created with the same
     * seed array in the same program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L128X1024MixRandom(byte[] seed) {
        // Convert the seed to 20 long values, of which the last 16 are not all zero.
        long[] data = RandomSupport.convertSeedBytesToLongs(seed, 20, 16);
	long ah = data[0], al = data[1], sh = data[2], sl = data[3];
        // Force a to be odd.
        this.ah = ah;
        this.al = al | 1;
        this.sh = sh;
        this.sl = sl;
        this.x = new long[N];
        for (int j = 0; j < N; j++) {
            this.x[j] = data[4+j];
        }
    }

    /* ---------------- public methods ---------------- */
    
    /**
     * Given 63 bits of "brine", constructs and returns a new instance of
     * {@code L128X1024MixRandom} that shares no mutable state with this instance.
     * However, with very high probability, the set of values collectively
     * generated by the two objects has the same statistical properties as if
     * same the quantity of values were generated by a single thread using
     * a single {@code L128X1024MixRandom} object.  Either or both of the two
     * objects may be further split using the {@code split} method,
     * and the same expected statistical properties apply to the
     * entire set of generators constructed by such recursive splitting.
     *
     * @param source a {@code SplittableGenerator} instance to be used instead
     *               of this one as a source of pseudorandom bits used to
     *               initialize the state of the new ones.
     * @param brine a long value, of which the low 63 bits are used to choose
     *              the {@code a} parameter for the new instance.
     * @return a new instance of {@code L128X1024MixRandom}
     */
    public SplittableGenerator split(SplittableGenerator source, long brine) {
	// Pick a new instance "at random", but use the brine for (the low half of) `a`.
        return new L128X1024MixRandom(source.nextLong(), brine << 1,
				      source.nextLong(), source.nextLong(),
				      source.nextLong(), source.nextLong(),
				      source.nextLong(), source.nextLong(),
				      source.nextLong(), source.nextLong(),
				      source.nextLong(), source.nextLong(),
				      source.nextLong(), source.nextLong(),
				      source.nextLong(), source.nextLong(),
				      source.nextLong(), source.nextLong(),
				      source.nextLong(), source.nextLong());
    }

    /**
     * Returns a pseudorandom {@code long} value.
     *
     * @return a pseudorandom {@code long} value
     */
    public long nextLong() {
        // First part of xoroshiro1024: fetch array data
        final int q = p;
        final long s0 = x[p = (p + 1) & (N - 1)];
        long s15 = x[q];

	// Compute the result based on current state information
	// (this allows the computation to be overlapped with state update).
        final long result = RandomSupport.mixLea64(sh + s0);

	// Update the LCG subgenerator
        // The LCG is, in effect, s = ((1LL << 64) + ML) * s + a, if only we had 128-bit arithmetic.
        final long u = ML * sl;
	// Note that Math.multiplyHigh computes the high half of the product of signed values,
	// but what we need is the high half of the product of unsigned values; for this we use the
	// formula "unsignedMultiplyHigh(a, b) = multiplyHigh(a, b) + ((a >> 63) & b) + ((b >> 63) & a)";
	// in effect, each operand is added to the result iff the sign bit of the other operand is 1.
	// (See Henry S. Warren, Jr., _Hacker's Delight_ (Second Edition), Addison-Wesley (2013),
	// Section 8-3, p. 175; or see the First Edition, Addison-Wesley (2003), Section 8-3, p. 133.)
	// If Math.unsignedMultiplyHigh(long, long) is ever implemented, the following line can become:
	//         sh = (ML * sh) + Math.unsignedMultiplyHigh(ML, sl) + sl + ah;
	// and this entire comment can be deleted.
        sh = (ML * sh) + (Math.multiplyHigh(ML, sl) + ((ML >> 63) & sl) + ((sl >> 63) & ML)) + sl + ah;
        sl = u + al;
        if (Long.compareUnsigned(sl, u) < 0) ++sh;  // Handle the carry propagation from low half to high half.

        // Second part of xoroshiro1024: update array data
        s15 ^= s0;
        x[q] = Long.rotateLeft(s0, 25) ^ s15 ^ (s15 << 27);
        x[p] = Long.rotateLeft(s15, 36);

        return result;
    }

    /**
     * Returns the period of this random generator.
     *
     * @return a {@link BigInteger} whose value is the number of distinct possible states of this
     *         {@link RandomGenerator} object (2<sup>128</sup>(2<sup>1024</sup>-1)).
     */
    public BigInteger period() {
        return PERIOD;
    }
}
