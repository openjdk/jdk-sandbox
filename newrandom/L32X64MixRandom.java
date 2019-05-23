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
import java.util.concurrent.atomic.AtomicLong;

/**
 * A generator of uniform pseudorandom values applicable for use in
 * (among other contexts) isolated parallel computations that may
 * generate subtasks.  Class {@code L32X64MixRandom} implements
 * interfaces {@link java.util.Rng} and {@link java.util.SplittableRng},
 * and therefore supports methods for producing pseudorandomly chosen
 * numbers of type {@code int}, {@code long}, {@code float}, and {@code double}
 * as well as creating new split-off {@code L32X64MixRandom} objects,
 * with similar usages as for class {@link java.util.SplittableRandom}.
 *
 * <p>Series of generated values pass the TestU01 BigCrush and PractRand test suites
 * that measure independence and uniformity properties of random number generators.
 * (Most recently validated with
 * <a href="http://simul.iro.umontreal.ca/testu01/tu01.html">version 1.2.3 of TestU01</a>
 * and <a href="http://pracrand.sourceforge.net">version 0.90 of PractRand</a>.
 * Note that TestU01 BigCrush was used to test not only values produced by the {@code nextLong()}
 * method but also the result of bit-reversing each value produced by {@code nextLong()}.)
 * These tests validate only the methods for certain
 * types and ranges, but similar properties are expected to hold, at
 * least approximately, for others as well.
 *
 * <p>{@code L32X64MixRandom} is a specific member of the LXM family of algorithms
 * for pseudorandom number generators.  Every LXM generator consists of two
 * subgenerators; one is an LCG (Linear Congruential Generator) and the other is
 * an Xorshift generator.  Each output of an LXM generator is the sum of one
 * output from each subgenerator, possibly processed by a final mixing function
 * (and {@code L32X64MixRandom} does use a mixing function).
 *
 * <p>The LCG subgenerator for {@code L32X64MixRandom} has an update step of the
 * form {@code s = m * s + a}, where {@code s}, {@code m}, and {@code a} are all
 * of type {@code int}; {@code s} is the mutable state, the multiplier {@code m}
 * is fixed (the same for all instances of {@code L32X64MixRandom}}) and the addend
 * {@code a} is a parameter (a final field of the instance).  The parameter
 * {@code a} is required to be odd (this allows the LCG to have the maximal
 * period, namely 2<sup>32</sup>); therefore there are 2<sup>31</sup> distinct choices
 * of parameter.
 *
 * <p>The Xorshift subgenerator for {@code L32X64MixRandom} is the {@code xoroshiro64} algorithm,
 * version 1.0 (parameters 26, 9, 13), without any final scrambler such as "+" or "**".
 * Its state consists of two {@code int} fields {@code x0} and {@code x1},
 * which can take on any values provided that they are not both zero.
 * The period of this subgenerator is 2<sup>64</sup>-1.
 * 
 * <p> The mixing function for {@code L32X64MixRandom} is the "starstar" mixing function.
 *
 * <p> Because the periods 2<sup>32</sup> and 2<sup>64</sup>-1 of the two subgenerators
 * are relatively prime, the <em>period</em> of any single {@code L32X64MixRandom} object 
 * (the length of the series of generated 32-bit values before it repeats) is the product
 * of the periods of the subgenerators, that is, 2<sup>32</sup>(2<sup>64</sup>-1),
 * which is just slightly smaller than 2<sup>96</sup>.  Moreover, if two distinct
 * {@code L32X64MixRandom} objects have different {@code a} parameters, then their
 * cycles of produced values will be different.
 *
 * <p>The 32-bit values produced by the {@code nextInt()} method are exactly equidistributed.
 * For any specific instance of {@code L32X64MixRandom}, over the course of its cycle each
 * of the 2<sup>32</sup> possible {@code int} values will be produced 2<sup>64</sup>-1 times.
 * The values produced by the {@code nextFloat()} method are likewise exactly equidistributed.
 *
 * <p>In fact, the 32-bit values produced by the {@code nextInt()} method are 2-equidistributed.
 * To be precise: for any specific instance of {@code L32X64MixRandom}, consider
 * the (overlapping) length-2 subsequences of the cycle of 64-bit values produced by
 * {@code nextInt()} (assuming no other methods are called that would affect the state).
 * There are 2<sup>32</sup>(2<sup>64</sup>-1) such subsequences, and each subsequence,
 * which consists of 2 32-bit values, can have one of 2<sup>64</sup> values. Of those
 * 2<sup>64</sup> subsequence values, nearly all of them (2<sup>64</sup>-2<sup>32</sup>)
 * occur 2<sup>32</sup> times over the course of the entire cycle, and the other
 * 2<sup>32</sup> subsequence values occur only 2<sup>32</sup>-1 times.  So the ratio
 * of the probability of getting one of the less common subsequence values and the
 * probability of getting one of the more common subsequence values is 1-2<sup>-32</sup>.
 * (Note that the set of 2<sup>32</sup> less-common subsequence values will differ from
 * one instance of {@code L32X64MixRandom} to another, as a function of the additive
 * parameter of the LCG.)  As a consequence, the values produced by the {@code nextFloat()}
 * method are likewise 2-equidistributed, and the values produced by the {@code nextLong()}
 * and {@code nextDouble()} methods are equidistributed (but not 2-equidistributed).
 *
 * <p>Method {@link #split} constructs and returns a new {@code L32X64MixRandom}
 * instance that shares no mutable state with the current instance. However, with
 * very high probability, the values collectively generated by the two objects
 * have the same statistical properties as if the same quantity of values were
 * generated by a single thread using a single {@code L32X64MixRandom} object.
 * This is because, with high probability, distinct {@code L32X64MixRandom} objects
 * have distinct {@code a} parameters and therefore use distinct members of the
 * algorithmic family; and even if their {@code a} parameters are the same, with
 * very high probability they will traverse different parts of their common state
 * cycle.
 *
 * <p>As with {@link java.util.SplittableRandom}, instances of
 * {@code L32X64MixRandom} are <em>not</em> thread-safe.
 * They are designed to be split, not shared, across threads. For
 * example, a {@link java.util.concurrent.ForkJoinTask} fork/join-style
 * computation using random numbers might include a construction
 * of the form {@code new Subtask(someL32X64MixRandom.split()).fork()}.
 *
 * <p>This class provides additional methods for generating random
 * streams, that employ the above techniques when used in
 * {@code stream.parallel()} mode.
 *
 * <p>Instances of {@code L32X64MixRandom} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @author  Guy Steele
 * @since   1.9
 */
public final class L32X64MixRandom extends AbstractSplittableRng {

    /*
     * Implementation Overview.
     *
     * The split operation uses the current generator to choose four new 64-bit
     * int values that are then used to initialize the parameter `a` and the
     * state variables `s`, `x0`, and `x1` for a newly constructed generator.
     *
     * With high probability, no two generators so chosen
     * will have the same `a` parameter, and testing has indicated
     * that the values generated by two instances of {@code L32X64MixRandom}
     * will be (approximately) independent if have different values for `a`.
     *
     * The default (no-argument) constructor, in essence, uses
     * "defaultGen" to generate four new 32-bit values for the same
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
    private static final AtomicLong defaultGen = new AtomicLong(RngSupport.initialSeed());

    /*
     * The period of this generator, which is (2**64 - 1) * 2**32.
     */
    private static final BigInteger thePeriod =
	BigInteger.ONE.shiftLeft(64).subtract(BigInteger.ONE).shiftLeft(32);

    /*
     * Multiplier used in the LCG portion of the algorithm, taken from
     * Pierre L'Ecuyer, Tables of linear congruential generators of
     * different sizes and good lattice structure, <em>Mathematics of
     * Computation</em> 68, 225 (January 1999), pages 249–260,
     * Table 4 (third multiplier for size 2<sup>32</sup>).
     */

    private static final int m = 32310901;

    /* ---------------- instance fields ---------------- */
    
    /**
     * The parameter that is used as an additive constant for the LCG.
     * Must be odd.
     */
    private final int a;

    /**
     * The per-instance state: s for the LCG; x0 and x1 for the xorshift.
     * At least one of x0 and x1 must be nonzero.
     */
    private int s, x0, x1;

    /* ---------------- constructors ---------------- */

    /**
     * Basic constructor that initializes all fields from parameters.
     * It then adjusts the field values if necessary to ensure that
     * all constraints on the values of fields are met.
     */
    public L32X64MixRandom(int a, int s, int x0, int x1) {
	// Force a to be odd.
        this.a = a | 1;
        this.s = s;
	// If x0 and x1 are both zero, we must choose nonzero values.
        if ((x0 | x1) == 0) {
	    // At least one of the two values generated here will be nonzero.
	    this.x0 = RngSupport.mixMurmur32(s += RngSupport.GOLDEN_RATIO_32);
	    this.x1 = RngSupport.mixMurmur32(s + RngSupport.GOLDEN_RATIO_32);
	}
    }

    /**
     * Creates a new instance of {@code L32X64MixRandom} using the
     * specified {@code long} value as the initial seed. Instances of
     * {@code L32X64MixRandom} created with the same seed in the same
     * program generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L32X64MixRandom(long seed) {
	// Using a value with irregularly spaced 1-bits to xor the seed
	// argument tends to improve "pedestrian" seeds such as 0 or
	// other small integers.  We may as well use SILVER_RATIO_64.
	//
	// The high half of the seed is hashed by mixMurmur32 to produce the `a` parameter.
	// The low half of the seed is hashed by mixMurmur32 to produce the initial `x0`,
	// which will then be used to produce the first generated value.
	// Then x1 is filled in as if by a SplitMix PRNG with
	// GOLDEN_RATIO_32 as the gamma value and Murmur32 as the mixer.
        this(RngSupport.mixMurmur32((int)((seed ^= RngSupport.SILVER_RATIO_64) >>> 32)),
	     1,
	     RngSupport.mixLea32((int)(seed)),
	     RngSupport.mixLea32((int)(seed) + RngSupport.GOLDEN_RATIO_32));
    }

    /**
     * Creates a new instance of {@code L32X64MixRandom} that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program execution,
     * but may, and typically does, vary across program invocations.
     */
    public L32X64MixRandom() {
	// Using GOLDEN_RATIO_64 here gives us a good Weyl sequence of values.
        this(defaultGen.getAndAdd(RngSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@code L32X64MixRandom} using the specified array of
     * initial seed bytes. Instances of {@code L32X64MixRandom} created with the same
     * seed array in the same program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public L32X64MixRandom(byte[] seed) {
	// Convert the seed to 4 int values, of which the last 2 are not all zero.
	int[] data = RngSupport.convertSeedBytesToInts(seed, 4, 2);
	int a = data[0], s = data[1], x0 = data[2], x1 = data[3];
	// Force a to be odd.
        this.a = a | 1;
        this.s = s;
        this.x0 = x0;
        this.x1 = x1;
    }

    /* ---------------- public methods ---------------- */

    /**
     * Constructs and returns a new instance of {@code L32X64MixRandom}
     * that shares no mutable state with this instance.
     * However, with very high probability, the set of values collectively
     * generated by the two objects has the same statistical properties as if
     * same the quantity of values were generated by a single thread using
     * a single {@code L32X64MixRandom} object.  Either or both of the two
     * objects may be further split using the {@code split} method,
     * and the same expected statistical properties apply to the
     * entire set of generators constructed by such recursive splitting.
     *
     * @param source a {@code SplittableRng} instance to be used instead
     *               of this one as a source of pseudorandom bits used to
     *               initialize the state of the new ones.
     * @return a new instance of {@code L32X64MixRandom}
     */
    public L32X64MixRandom split(SplittableRng source) {
	// Literally pick a new instance "at random".
        return new L32X64MixRandom(source.nextInt(), source.nextInt(),
				   source.nextInt(), source.nextInt());
    }

    /**
     * Returns a pseudorandom {@code int} value.
     *
     * @return a pseudorandom {@code int} value
     */
    public int nextInt() {
	final int z = s + x0;
	s = m * s + a;  // LCG
	int q0 = x0, q1 = x1;
	{ q1 ^= q0; q0 = Integer.rotateLeft(q0, 26); q0 = q0 ^ q1 ^ (q1 << 9); q1 = Integer.rotateLeft(q1, 13); }  // xoroshiro64
	x0 = q0; x1 = q1;
	return Integer.rotateLeft(z * 5, 7) * 9;  // "starstar" mixing function
    }

    /**
     * Returns a pseudorandom {@code long} value.
     *
     * @return a pseudorandom {@code long} value
     */

    public long nextLong() {
	return ((long)(nextInt()) << 32) | nextInt();
    }

    public BigInteger period() { return thePeriod; }
}
