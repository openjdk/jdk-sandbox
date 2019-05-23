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
 * generate subtasks.  Class {@code MRG32k3a} implements
 * interfaces {@link java.util.Rng} and {@link java.util.AbstractArbitrarilyJumpableRng},
 * and therefore supports methods for producing pseudorandomly chosen
 * numbers of type {@code int}, {@code long}, {@code float}, and {@code double}
 * as well as creating new {@code Xoroshiro128PlusMRG32k3a} objects
 * by "jumping" or "leaping".
 *
 * <p>Instances {@code Xoroshiro128Plus} are <em>not</em> thread-safe.
 * They are designed to be used so that each thread as its own instance.
 * The methods {@link #jump} and {@link #leap} and {@link #jumps} and {@link #leaps}
 * can be used to construct new instances of {@code Xoroshiro128Plus} that traverse
 * other parts of the state cycle.
 *
 * <p>Instances of {@code MRG32k3a} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @author  Guy Steele
 * @since   1.9
 */
public final class MRG32k3a extends AbstractArbitrarilyJumpableRng {

    /*
     * Implementation Overview.
     *
     * xxxx
     *
     * File organization: First the non-public methods that constitute
     * the main algorithm, then the main public methods, followed by
     * some custom spliterator classes needed for stream methods.
     */

    private final static double norm1 = 2.328306549295728e-10;
    private final static double norm2 = 2.328318824698632e-10;
    private final static double m1 =   4294967087.0;
    private final static double m2 =   4294944443.0;
    private final static double a12 =     1403580.0;
    private final static double a13n =     810728.0;
    private final static double a21 =      527612.0;
    private final static double a23n =    1370589.0;
    private final static int m1_deficit = 209;
    
    // IllegalArgumentException messages
    private static final String BadLogDistance  = "logDistance must be non-negative and not greater than 192";

    /**
     * The per-instance state.
     The seeds for s10, s11, s12 must be integers in [0, m1 - 1] and not all 0. 
     The seeds for s20, s21, s22 must be integers in [0, m2 - 1] and not all 0. 
     */
    private double s10, s11, s12,
	           s20, s21, s22;

    /**
     * The seed generator for default constructors.
     */
    private static final AtomicLong defaultGen = new AtomicLong(RngSupport.initialSeed());

    /*
      32-bits Random number generator U(0,1): MRG32k3a
      Author: Pierre L'Ecuyer,
      Source: Good Parameter Sets for Combined Multiple Recursive Random
           Number Generators,
           Shorter version in Operations Research,
           47, 1 (1999), 159--164.
	   ---------------------------------------------------------
    */

    private void nextState() {
	/* Component 1 */
	double p1 = a12 * s11 - a13n * s10;
	double k1 = p1 / m1;   p1 -= k1 * m1;   if (p1 < 0.0) p1 += m1;
	s10 = s11;   s11 = s12;   s12 = p1;
	/* Component 2 */
	double p2 = a21 * s22 - a23n * s20;
	double k2 = p2 / m2;   p2 -= k2 * m2;   if (p2 < 0.0) p2 += m2;
	s20 = s21;   s21 = s22;   s22 = p2;
    }

    
    /**
     * The form of nextInt used by IntStream Spliterators.
     * Exactly the same as long version, except for types.
     *
     * @param origin the least value, unless greater than bound
     * @param bound the upper bound (exclusive), must not equal origin
     * @return a pseudorandom value
     */
    protected int internalNextInt(int origin, int bound) {
        if (origin < bound) {
            final int n = bound - origin;
	    final int m = n - 1;
	    if (n > 0) {
		int r;
                for (int u = (int)nextDouble() >>> 1;
                     u + m + ((m1_deficit + 1) >>> 1) - (r = u % n) < 0;
                     u = (int)nextDouble() >>> 1)
                    ;
                return (r + origin);
            } else {
		return RngSupport.boundedNextInt(this, origin, bound);
            }
        } else {
	    return nextInt();
	}
    }

    protected int internalNextInt(int bound) {
        // Specialize internalNextInt for origin == 0, bound > 0
	final int n = bound;
	final int m = n - 1;
	int r;
	for (int u = (int)nextDouble() >>> 1;
	     u + m + ((m1_deficit + 1) >>> 1) - (r = u % n) < 0;
	     u = (int)nextDouble() >>> 1)
	    ;
	return r;
    }

    /**
     * Constructor used by all others except default constructor.
     * All arguments must be known to be nonnegative integral values.
     */
    private MRG32k3a(double s10, double s11, double s12,
		     double s20, double s21, double s22) {
	this.s10 = s10; this.s11 = s11; this.s12 = s12;
	this.s20 = s20; this.s21 = s21; this.s22 = s22;
	if ((s10 == 0.0) && (s11 == 0.0) && (s12 == 0.0)) this.s10 = 12345.0;
	if ((s20 == 0.0) && (s21 == 0.0) && (s22 == 0.0)) this.s20 = 12345.0;
    }

    /* ---------------- public methods ---------------- */

    public MRG32k3a(int s10, int s11, int s12,
		    int s20, int s21, int s22) {
	this(((double)(((long)s10) & 0x00000000ffffffffL)) % m1,
	     ((double)(((long)s11) & 0x00000000ffffffffL)) % m1,
	     ((double)(((long)s12) & 0x00000000ffffffffL)) % m1,
	     ((double)(((long)s20) & 0x00000000ffffffffL)) % m2,
	     ((double)(((long)s21) & 0x00000000ffffffffL)) % m2,
	     ((double)(((long)s22) & 0x00000000ffffffffL)) % m2);
    }

    /**
     * Creates a new MRG32k3a instance using the specified
     * initial seed. MRG32k3a instances created with the same
     * seed in the same program generate identical sequences of values.
     * An argument of 0 seeds the generator to a widely used initialization
     * of MRG32k3a: all six state variables are set to 12345.
     *
     * @param seed the initial seed
     */
    public MRG32k3a(long seed) {
        this((double)((seed & 0x7FF) + 12345),
	     (double)(((seed >>> 11) & 0x7FF) + 12345),
	     (double)(((seed >>> 22) & 0x7FF) + 12345),
	     (double)(((seed >>> 33) & 0x7FF) + 12345),
	     (double)(((seed >>> 44) & 0x7FF) + 12345),
	     (double)((seed >>> 55) + 12345));
    }

    /**
     * Creates a new MRG32k3a instance that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program; and
     * may, and typically does, vary across program invocations.
     */
    public MRG32k3a() {
	this(defaultGen.getAndAdd(RngSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@code Xoshiro256StarStar} using the specified array of
     * initial seed bytes. Instances of {@code Xoshiro256StarStar} created with the same
     * seed array in the same program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public MRG32k3a(byte[] seed) {
	// Convert the seed to 6 int values.
	int[] data = RngSupport.convertSeedBytesToInts(seed, 6, 0);
	int s10 = data[0], s11 = data[1], s12 = data[2];
	int s20 = data[3], s21 = data[4], s22 = data[5];
	this.s10 = ((double)(((long)s10) & 0x00000000ffffffffL)) % m1;
	this.s11 = ((double)(((long)s11) & 0x00000000ffffffffL)) % m1;
	this.s12 = ((double)(((long)s12) & 0x00000000ffffffffL)) % m1;
	this.s20 = ((double)(((long)s20) & 0x00000000ffffffffL)) % m2;
	this.s21 = ((double)(((long)s21) & 0x00000000ffffffffL)) % m2;
	this.s22 = ((double)(((long)s22) & 0x00000000ffffffffL)) % m2;
	if ((s10 == 0.0) && (s11 == 0.0) && (s12 == 0.0)) this.s10 = 12345.0;
	if ((s20 == 0.0) && (s21 == 0.0) && (s22 == 0.0)) this.s20 = 12345.0;
    }

    public MRG32k3a copy() { return new MRG32k3a(s10, s11, s12, s20, s21, s22); }

    /**
     * Returns a pseudorandom {@code double} value between zero
     * (exclusive) and one (exclusive).
     *
     * @return a pseudorandom {@code double} value between zero
     *         (exclusive) and one (exclusive)
     */
    public double nextOpenDouble() {
	nextState();
	double p1 = s12, p2 = s22;
	if (p1 <= p2)
	    return ((p1 - p2 + m1) * norm1);
	else
	    return ((p1 - p2) * norm1);
    }

    /**
     * Returns a pseudorandom {@code double} value between zero
     * (inclusive) and one (exclusive).
     *
     * @return a pseudorandom {@code double} value between zero
     *         (inclusive) and one (exclusive)
     */
    public double nextDouble() {
	nextState();
	double p1 = s12, p2 = s22;
	final double p = p1 * norm1 - p2 * norm2;
	if (p < 0.0) return (p + 1.0);
	else return p;
    }

    
    /**
     * Returns a pseudorandom {@code float} value between zero
     * (inclusive) and one (exclusive).
     *
     * @return a pseudorandom {@code float} value between zero
     *         (inclusive) and one (exclusive)
     */
    public float nextFloat() {
        return (float)nextDouble();
    }

    /**
     * Returns a pseudorandom {@code int} value.
     *
     * @return a pseudorandom {@code int} value
     */
    public int nextInt() {
	return (internalNextInt(0x10000) << 16) | internalNextInt(0x10000);
    }

    /**
     * Returns a pseudorandom {@code long} value.
     *
     * @return a pseudorandom {@code long} value
     */

    public long nextLong() {
 	return (((long)internalNextInt(0x200000) << 43) |
		((long)internalNextInt(0x200000) << 22) |
		((long)internalNextInt(0x400000)));
    }

    // Period is (m1**3 - 1)(m2**3 - 1)/2, or approximately 2**191.
    static BigInteger calculateThePeriod() {
	BigInteger bigm1 = BigInteger.valueOf((long)m1);
	BigInteger bigm2 = BigInteger.valueOf((long)m2);
	BigInteger t1 = bigm1.multiply(bigm1).multiply(bigm1).subtract(BigInteger.ONE);
	BigInteger t2 = bigm2.multiply(bigm2).multiply(bigm2).subtract(BigInteger.ONE);
	return t1.shiftRight(1).multiply(t2);
    }
    static final BigInteger thePeriod = calculateThePeriod();
    public BigInteger period() { return thePeriod; }

    // Jump and leap distances recommended in Section 1.3 of this paper:
    // Pierre L'Ecuyer, Richard Simard, E. Jack Chen, and W. David Kelton.
    // An Object-Oriented Random-Number Package with Many Long Streams and Substreams.
    // Operations Research 50, 6 (Nov--Dec 2002), 1073--1075.

    public double defaultJumpDistance() { return 0x1.0p76; }  // 2**76
    public double defaultLeapDistance() { return 0x1.0p127; }  // 2**127
        
    public void jump(double distance) {
        if (distance < 0.0 || Double.isInfinite(distance) || distance != Math.floor(distance))
            throw new IllegalArgumentException("jump distance must be a nonnegative finite integer");
    	// We will compute a jump transformation (s => M s) for each LCG.
    	// We initialize each transformation to the identity transformation.
    	// Each will be turned into the d'th power of the corresponding base transformation.
	long m1_00 = 1, m1_01 = 0, m1_02 = 0,
	     m1_10 = 0, m1_11 = 1, m1_12 = 0,
	     m1_20 = 0, m1_21 = 0, m1_22 = 1;
	long m2_00 = 1, m2_01 = 0, m2_02 = 0,
	     m2_10 = 0, m2_11 = 1, m2_12 = 0,
	     m2_20 = 0, m2_21 = 0, m2_22 = 1;
	// These are the base transformations, which will be repeatedly squared,
	// and composed with the computed transformations for each 1-bit in distance.
	long t1_00 = 0,           t1_01 = 1,         t1_02 = 0,
	     t1_10 = 0,           t1_11 = 0,         t1_12 = 1,
	     t1_20 = -(long)a13n, t1_21 = (long)a12, t1_22 = 0;
	long t2_00 = 0,           t2_01 = 1,         t2_02 = 0,
	     t2_10 = 0,           t2_11 = 0,         t2_12 = 1,
	     t2_20 = -(long)a23n, t2_21 = (long)a21, t2_22 = 0;
	while (distance > 0.0) {
	    final double dhalf = 0.5 * distance;
	    if (Math.floor(dhalf) != dhalf) {
		// distance is odd: accumulate current squaring
		final long n1_00 = m1_00 * t1_00 + m1_01 * t1_10 + m1_02 * t1_20;
		final long n1_01 = m1_00 * t1_01 + m1_01 * t1_11 + m1_02 * t1_21;
		final long n1_02 = m1_00 * t1_02 + m1_01 * t1_12 + m1_02 * t1_22;
		final long n1_10 = m1_10 * t1_00 + m1_11 * t1_10 + m1_12 * t1_20;
		final long n1_11 = m1_10 * t1_01 + m1_11 * t1_11 + m1_12 * t1_21;
		final long n1_12 = m1_10 * t1_02 + m1_11 * t1_12 + m1_12 * t1_22;
		final long n1_20 = m1_20 * t1_00 + m1_21 * t1_10 + m1_22 * t1_20;
		final long n1_21 = m1_20 * t1_01 + m1_21 * t1_11 + m1_22 * t1_21;
		final long n1_22 = m1_20 * t1_02 + m1_21 * t1_12 + m1_22 * t1_22;
		m1_00 = Math.floorMod(n1_00, (long)m1);
		m1_01 = Math.floorMod(n1_01, (long)m1);
		m1_02 = Math.floorMod(n1_02, (long)m1);
		m1_10 = Math.floorMod(n1_10, (long)m1);
		m1_11 = Math.floorMod(n1_11, (long)m1);
		m1_12 = Math.floorMod(n1_12, (long)m1);
		m1_20 = Math.floorMod(n1_20, (long)m1);
		m1_21 = Math.floorMod(n1_21, (long)m1);
		m1_22 = Math.floorMod(n1_22, (long)m1);
		final long n2_00 = m2_00 * t2_00 + m2_01 * t2_10 + m2_02 * t2_20;
		final long n2_01 = m2_00 * t2_01 + m2_01 * t2_11 + m2_02 * t2_21;
		final long n2_02 = m2_00 * t2_02 + m2_01 * t2_12 + m2_02 * t2_22;
		final long n2_10 = m2_10 * t2_00 + m2_11 * t2_10 + m2_12 * t2_20;
		final long n2_11 = m2_10 * t2_01 + m2_11 * t2_11 + m2_12 * t2_21;
		final long n2_12 = m2_10 * t2_02 + m2_11 * t2_12 + m2_12 * t2_22;
		final long n2_20 = m2_20 * t2_00 + m2_21 * t2_10 + m2_22 * t2_20;
		final long n2_21 = m2_20 * t2_01 + m2_21 * t2_11 + m2_22 * t2_21;
		final long n2_22 = m2_20 * t2_02 + m2_21 * t2_12 + m2_22 * t2_22;
		m2_00 = Math.floorMod(n2_00, (long)m2);
		m2_01 = Math.floorMod(n2_01, (long)m2);
		m2_02 = Math.floorMod(n2_02, (long)m2);
		m2_10 = Math.floorMod(n2_10, (long)m2);
		m2_11 = Math.floorMod(n2_11, (long)m2);
		m2_12 = Math.floorMod(n2_12, (long)m2);
		m2_20 = Math.floorMod(n2_20, (long)m2);
		m2_21 = Math.floorMod(n2_21, (long)m2);
		m2_22 = Math.floorMod(n2_22, (long)m2);
	    }
	    // Square the base transformations.
	    {
		final long z1_00 = m1_00 * m1_00 + m1_01 * m1_10 + m1_02 * m1_20;
		final long z1_01 = m1_00 * m1_01 + m1_01 * m1_11 + m1_02 * m1_21;
		final long z1_02 = m1_00 * m1_02 + m1_01 * m1_12 + m1_02 * m1_22;
		final long z1_10 = m1_10 * m1_00 + m1_11 * m1_10 + m1_12 * m1_20;
		final long z1_11 = m1_10 * m1_01 + m1_11 * m1_11 + m1_12 * m1_21;
		final long z1_12 = m1_10 * m1_02 + m1_11 * m1_12 + m1_12 * m1_22;
		final long z1_20 = m1_20 * m1_00 + m1_21 * m1_10 + m1_22 * m1_20;
		final long z1_21 = m1_20 * m1_01 + m1_21 * m1_11 + m1_22 * m1_21;
		final long z1_22 = m1_20 * m1_02 + m1_21 * m1_12 + m1_22 * m1_22;
		m1_00 = Math.floorMod(z1_00, (long)m1);
		m1_01 = Math.floorMod(z1_01, (long)m1);
		m1_02 = Math.floorMod(z1_02, (long)m1);
		m1_10 = Math.floorMod(z1_10, (long)m1);
		m1_11 = Math.floorMod(z1_11, (long)m1);
		m1_12 = Math.floorMod(z1_12, (long)m1);
		m1_20 = Math.floorMod(z1_20, (long)m1);
		m1_21 = Math.floorMod(z1_21, (long)m1);
		m1_22 = Math.floorMod(z1_22, (long)m1);
		final long z2_00 = m2_00 * m2_00 + m2_01 * m2_10 + m2_02 * m2_20;
		final long z2_01 = m2_00 * m2_01 + m2_01 * m2_11 + m2_02 * m2_21;
		final long z2_02 = m2_00 * m2_02 + m2_01 * m2_12 + m2_02 * m2_22;
		final long z2_10 = m2_10 * m2_00 + m2_11 * m2_10 + m2_12 * m2_20;
		final long z2_11 = m2_10 * m2_01 + m2_11 * m2_11 + m2_12 * m2_21;
		final long z2_12 = m2_10 * m2_02 + m2_11 * m2_12 + m2_12 * m2_22;
		final long z2_20 = m2_20 * m2_00 + m2_21 * m2_10 + m2_22 * m2_20;
		final long z2_21 = m2_20 * m2_01 + m2_21 * m2_11 + m2_22 * m2_21;
		final long z2_22 = m2_20 * m2_02 + m2_21 * m2_12 + m2_22 * m2_22;
		m2_00 = Math.floorMod(z2_00, (long)m2);
		m2_01 = Math.floorMod(z2_01, (long)m2);
		m2_02 = Math.floorMod(z2_02, (long)m2);
		m2_10 = Math.floorMod(z2_10, (long)m2);
		m2_11 = Math.floorMod(z2_11, (long)m2);
		m2_12 = Math.floorMod(z2_12, (long)m2);
		m2_20 = Math.floorMod(z2_20, (long)m2);
		m2_21 = Math.floorMod(z2_21, (long)m2);
		m2_22 = Math.floorMod(z2_22, (long)m2);
	    }
	    // Divide distance by 2.
	    distance = dhalf;
	}
	final long w10 = m1_00 * (long)s10 + m1_01 * (long)s11 + m1_02 * (long)s12;
	final long w11 = m1_10 * (long)s10 + m1_11 * (long)s11 + m1_12 * (long)s12;
	final long w12 = m1_20 * (long)s10 + m1_21 * (long)s11 + m1_22 * (long)s12;
	s10 = Math.floorMod(w10, (long)m1);
	s11 = Math.floorMod(w11, (long)m1);
	s12 = Math.floorMod(w12, (long)m1);
	final long w20 = m2_00 * (long)s20 + m2_01 * (long)s21 + m2_02 * (long)s22;
	final long w21 = m2_10 * (long)s20 + m2_11 * (long)s21 + m2_12 * (long)s22;
	final long w22 = m2_20 * (long)s20 + m2_21 * (long)s21 + m2_22 * (long)s22;
	s20 = Math.floorMod(w20, (long)m2);
	s21 = Math.floorMod(w21, (long)m2);
	s22 = Math.floorMod(w22, (long)m2);
    }
        
    /**
     * Alter the state of this pseudorandom number generator so as to
     * jump forward a distance equal to 2<sup>{@code logDistance}</sup>
     * within its state cycle.
     *
     * @param logDistance the base-2 logarithm of the distance to jump
     *        forward within the state cycle.  Must be non-negative and
     *        not greater than 192.
     * @throws IllegalArgumentException if {@code logDistance} is
     *         less than zero or 2<sup>{@code logDistance}</sup> is
     *         greater than the period of this generator
     */
    public void jumpPowerOfTwo(int logDistance) {
        if (logDistance < 0 || logDistance > 192)
            throw new IllegalArgumentException(BadLogDistance);
	jump(Math.scalb(1.0, logDistance));
    }

}
