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
 * generate subtasks.  Class {@link L64X128MixRandom} implements
 * interfaces {@link RandomGenerator} and {@link SplittableGenerator},
 * and therefore supports methods for producing pseudorandomly chosen
 * numbers of type {@code int}, {@code long}, {@code float}, and {@code double}
 * as well as creating new split-off {@link L64X128MixRandom} objects,
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
 * {@link L64X128MixRandom} is a specific member of the LXM family of algorithms
 * for pseudorandom number generators.  Every LXM generator consists of two
 * subgenerators; one is an LCG (Linear Congruential Generator) and the other is
 * an Xorshift generator.  Each output of an LXM generator is the result of
 * combining state from the LCG with state from the Xorshift generator by
 * using a Mixing function (and then the state of the LCG and the state of the
 * Xorshift generator are advanced).
 * <p>
 * The LCG subgenerator for {@link L64X128MixRandom} has an update step of the
 * form {@code s = m * s + a}, where {@code s}, {@code m}, and {@code a} are all
 * of type {@code long}; {@code s} is the mutable state, the multiplier {@code m}
 * is fixed (the same for all instances of {@link L64X128MixRandom}}) and the addend
 * {@code a} is a parameter (a final field of the instance).  The parameter
 * {@code a} is required to be odd (this allows the LCG to have the maximal
 * period, namely 2<sup>64</sup>); therefore there are 2<sup>63</sup> distinct choices
 * of parameter.
 * <p>
 * The Xorshift subgenerator for {@link L64X128MixRandom} is the {@code xoroshiro128} algorithm,
 * version 1.0 (parameters 24, 16, 37), without any final scrambler such as "+" or "**".
 * Its state consists of two {@code long} fields {@code x0} and {@code x1},
 * which can take on any values provided that they are not both zero.
 * The period of this subgenerator is 2<sup>128</sup>-1.
 * <p>
 * The mixing function for {@link L64X128MixRandom} is {@link RandomSupport.mixLea64}
 * applied to the argument {@code (s + x0)}.
 * <p>
 * Because the periods 2<sup>64</sup> and 2<sup>128</sup>-1 of the two subgenerators
 * are relatively prime, the <em>period</em> of any single {@link L64X128MixRandom} object
 * (the length of the series of generated 64-bit values before it repeats) is the product
 * of the periods of the subgenerators, that is, 2<sup>64</sup>(2<sup>128</sup>-1),
 * which is just slightly smaller than 2<sup>192</sup>.  Moreover, if two distinct
 * {@link L64X128MixRandom} objects have different {@code a} parameters, then their
 * cycles of produced values will be different.
 * <p>
 * The 64-bit values produced by the {@code nextLong()} method are exactly equidistributed.
 * For any specific instance of {@link L64X128MixRandom}, over the course of its cycle each
 * of the 2<sup>64</sup> possible {@code long} values will be produced 2<sup>128</sup>-1 times.
 * The values produced by the {@code nextInt()}, {@code nextFloat()}, and {@code nextDouble()}
 * methods are likewise exactly equidistributed.
 * <p>
 * In fact, the 64-bit values produced by the {@code nextLong()} method are 2-equidistributed.
 * To be precise: for any specific instance of {@link L64X128MixRandom}, consider
 * the (overlapping) length-2 subsequences of the cycle of 64-bit values produced by
 * {@code nextLong()} (assuming no other methods are called that would affect the state).
 * There are 2<sup>64</sup>(2<sup>128</sup>-1) such subsequences, and each subsequence,
 * which consists of 2 64-bit values, can have one of 2<sup>128</sup> values. Of those
 * 2<sup>128</sup> subsequence values, nearly all of them (2<sup>128</sup>-2<sup>64</sup>)
 * occur 2<sup>64</sup> times over the course of the entire cycle, and the other
 * 2<sup>64</sup> subsequence values occur only 2<sup>64</sup>-1 times.  So the ratio
 * of the probability of getting any specific one of the less common subsequence values and the
 * probability of getting any specific one of the more common subsequence values is 1-2<sup>-64</sup>.
 * (Note that the set of 2<sup>64</sup> less-common subsequence values will differ from
 * one instance of {@link L64X128MixRandom} to another, as a function of the additive
 * parameter of the LCG.)  The values produced by the {@code nextInt()}, {@code nextFloat()},
 * and {@code nextDouble()} methods are likewise 2-equidistributed.
 * <p>
 * Method {@link #split} constructs and returns a new {@link L64X128MixRandom}
 * instance that shares no mutable state with the current instance. However, with
 * very high probability, the values collectively generated by the two objects
 * have the same statistical properties as if the same quantity of values were
 * generated by a single thread using a single {@link L64X128MixRandom} object.
 * This is because, with high probability, distinct {@link L64X128MixRandom} objects
 * have distinct {@code a} parameters and therefore use distinct members of the
 * algorithmic family; and even if their {@code a} parameters are the same, with
 * very high probability they will traverse different parts of their common state
 * cycle.
 * <p>
 * As with {@link java.util.SplittableRandom}, instances of
 * {@link L64X128MixRandom} are <em>not</em> thread-safe.
 * They are designed to be split, not shared, across threads. For
 * example, a {@link java.util.concurrent.ForkJoinTask} fork/join-style
 * computation using random numbers might include a construction
 * of the form {@code new Subtask(someL64X128MixRandom.split()).fork()}.
 * <p>
 * This class provides additional methods for generating random
 * streams, that employ the above techniques when used in
 * {@code stream.parallel()} mode.
 * <p>
 * Instances of {@link L64X128MixRandom} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @since 14
 */
public final class L64X128MixRandom extends AbstractSplittableWithBrineGenerator {

    /*
     * Implementation Overview.
     *
     * The split operation uses the current generator to choose four new 64-bit
     * long values that are then used to initialize the parameter `a` and the
     * state variables `s`, `x0`, and `x1` for a newly constructed generator.
     *
     * With extremely high probability, no two generators so chosen
     * will have the same `a` parameter, and testing has indicated
     * that the values generated by two instances of {@link L64X128MixRandom}
     * will be (approximately) independent if have different values for `a`.
     *
     * The default (no-argument) constructor, in essence, uses
     * "defaultGen" to generate four new 64-bit values for the same
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

    /**
     * The seed generator for default constructors.
     */
    private static final AtomicLong defaultGen = new AtomicLong(RandomSupport.initialSeed());

    /*
     * The period of this generator, which is (2**128 - 1) * 2**64.
     */
    private static final BigInteger PERIOD =
        BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE).shiftLeft(64);

    /*
     * Multiplier used in the LCG portion of the algorithm.
     * Chosen based on research by Sebastiano Vigna and Guy Steele (2019).
     * The spectral scores for dimensions 2 through 8 for the multiplier 0xd1342543de82ef95
     * are [0.958602, 0.937479, 0.870757, 0.822326, 0.820405, 0.813065, 0.760215].
     */

    private static final long M = 0xd1342543de82ef95L;

    /* ---------------- instance fields ---------------- */

    /**
     * The parameter that is used as an additive constant for the LCG.
     * Must be odd.
     */
    private final long a;

    /**
     * The per-instance state: s for the LCG; x0 and x1 for the xorshift.
     * At least one of x0 and x1 must be nonzero.
     */
    private long s, x0, x1;

    /* ---------------- constructors ---------------- */

    /**
     * Basic constructor that initializes all fields from parameters.
     * It then adjusts the field values if necessary to ensure that
     * all constraints on the values of fields are met.
     *
     * @param a additive parameter for the LCG
     * @param s initial state for the LCG
     * @param x0 first word of the initial state for the xorshift generator
     * @param x1 second word of the initial state for the xorshift generator
     */
    public L64X128MixRandom(long a, long s, long x0, long x1) {
        // Force a to be odd.
        this.a = a | 1;
        this.s = s;
        this.x0 = x0;
        this.x1 = x1;
        // If x0 and x1 are both zero, we must choose nonzero values.
        if ((x0 | x1) == 0) {
	    long v = s;
            // At least one of the two values generated here will be nonzero.
            this.x0 = RandomSupport.mixStafford13(v += RandomSupport.GOLDEN_RATIO_64);
            this.x1 = RandomSupport.mixStafford13(v + RandomSupport.GOLDEN_RATIO_64);
        }
    }

    /**
     * Creates a new instance of {@link L64X128MixRandom} using the
     * specified {@code long} value as the initial seed. Instances of
     * {@link L64X128MixRandom} created with the same seed in the same
     * program generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L64X128MixRandom(long seed) {
        // Using a value with irregularly spaced 1-bits to xor the seed
        // argument tends to improve "pedestrian" seeds such as 0 or
        // other small integers.  We may as well use SILVER_RATIO_64.
        //
        // The seed is hashed by mixMurmur64 to produce the `a` parameter.
        // The seed is hashed by mixStafford13 to produce the initial `x0`,
        // which will then be used to produce the first generated value.
        // Then x1 is filled in as if by a SplitMix PRNG with
        // GOLDEN_RATIO_64 as the gamma value and mixStafford13 as the mixer.
        this(RandomSupport.mixMurmur64(seed ^= RandomSupport.SILVER_RATIO_64),
             1,
             RandomSupport.mixStafford13(seed),
             RandomSupport.mixStafford13(seed + RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link L64X128MixRandom} that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program execution,
     * but may, and typically does, vary across program invocations.
     */
    public L64X128MixRandom() {
        // Using GOLDEN_RATIO_64 here gives us a good Weyl sequence of values.
        this(defaultGen.getAndAdd(RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link L64X128MixRandom} using the specified array of
     * initial seed bytes. Instances of {@link L64X128MixRandom} created with the same
     * seed array in the same program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L64X128MixRandom(byte[] seed) {
        // Convert the seed to 4 long values, of which the last 2 are not all zero.
        long[] data = RandomSupport.convertSeedBytesToLongs(seed, 4, 2);
        long a = data[0], s = data[1], x0 = data[2], x1 = data[3];
        // Force a to be odd.
        this.a = a | 1;
        this.s = s;
        this.x0 = x0;
        this.x1 = x1;
    }

    /* ---------------- public methods ---------------- */

    /**
     * Given 63 bits of "brine", constructs and returns a new instance of
     * {@code L64X128MixRandom} that shares no mutable state with this instance.
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
     * @param brine a long value, of which the low 63 bits are used to choose
     *              the {@code a} parameter for the new instance.
     * @return a new instance of {@code L64X128MixRandom}
     */
    public SplittableGenerator split(SplittableGenerator source, long brine) {
	// Pick a new instance "at random", but use the brine for `a`.
        return new L64X128MixRandom(brine << 1, source.nextLong(),
				    source.nextLong(), source.nextLong());
    }

    /**
     * Returns a pseudorandom {@code long} value.
     *
     * @return a pseudorandom {@code long} value
     */
    public long nextLong() {
	// Compute the result based on current state information
	// (this allows the computation to be overlapped with state update).
        final long result = RandomSupport.mixLea64(s + x0);
	// Update the LCG subgenerator
        s = M * s + a;
	// Update the Xorshift subgenerator
        long q0 = x0, q1 = x1;
        {   // xoroshiro128v1_0
            q1 ^= q0;
            q0 = Long.rotateLeft(q0, 24);
            q0 = q0 ^ q1 ^ (q1 << 16);
            q1 = Long.rotateLeft(q1, 37);
        }
        x0 = q0; x1 = q1;
        return result;
    }

    /**
     * Returns the period of this random generator.
     *
     * @return a {@link BigInteger} whose value is the number of distinct possible states of this
     *         {@link RandomGenerator} object (2<sup>64</sup>(2<sup>128</sup>-1)).
     */
    public BigInteger period() {
        return PERIOD;
    }
}
