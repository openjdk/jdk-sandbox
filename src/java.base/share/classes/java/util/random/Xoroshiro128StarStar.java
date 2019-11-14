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
import java.util.random.RandomGenerator.LeapableGenerator;

/**
 * A generator of uniform pseudorandom values applicable for use in
 * (among other contexts) isolated parallel computations that may
 * generate subtasks.  Class {@link Xoroshiro128StarStar} implements
 * interfaces {@link RandomGenerator} and {@link LeapableGenerator},
 * and therefore supports methods for producing pseudorandomly chosen
 * numbers of type {@code int}, {@code long}, {@code float}, and {@code double}
 * as well as creating new {@link Xoroshiro128StarStar} objects
 * by "jumping" or "leaping".
 * <p>
 * Series of generated values pass the TestU01 BigCrush and PractRand test suites
 * that measure independence and uniformity properties of random number generators.
 * <p>
 * The class {@link Xoroshiro128StarStar} uses the {@code xoroshiro128} algorithm,
 * version 1.0 (parameters 24, 16, 37), with the "**" scrambler (a mixing function).
 * Its state consists of two {@code long} fields {@code x0} and {@code x1},
 * which can take on any values provided that they are not both zero.
 * The period of this generator is 2<sup>128</sup>-1.
 * <p>
 * The 64-bit values produced by the {@code nextLong()} method are equidistributed.
 * To be precise, over the course of the cycle of length 2<sup>128</sup>-1,
 * each nonzero {@code long} value is generated 2<sup>64</sup> times,
 * but the value 0 is generated only 2<sup>64</sup>-1 times.
 * The values produced by the {@code nextInt()}, {@code nextFloat()}, and {@code nextDouble()}
 * methods are likewise equidistributed.
 * <p>
 * In fact, the 64-bit values produced by the {@code nextLong()} method are 2-equidistributed.
 * To be precise: consider the (overlapping) length-2 subsequences of the cycle of 64-bit
 * values produced by {@code nextLong()} (assuming no other methods are called that would
 * affect the state).  There are 2<sup>128</sup>-1 such subsequences, and each subsequence,
 * which consists of 2 64-bit values, can have one of 2<sup>128</sup> values.  Of those
 * 2<sup>128</sup> subsequence values, each one is generated exactly once over the course
 * of the entire cycle, except that the subsequence (0, 0) never appears.
 * The values produced by the {@code nextInt()}, {@code nextFloat()}, and {@code nextDouble()}
 * methods are likewise 2-equidistributed, but note that that the subsequence (0, 0)
 * can also appear (but occurring somewhat less frequently than all other subsequences),
 * because the values produced by those methods have fewer than 64 randomly chosen bits.
 * <p>
 * Instances {@link Xoroshiro128StarStar} are <em>not</em> thread-safe.
 * They are designed to be used so that each thread as its own instance.
 * The methods {@link #jump} and {@link #leap} and {@link #jumps} and {@link #leaps}
 * can be used to construct new instances of {@link Xoroshiro128StarStar} that traverse
 * other parts of the state cycle.
 * <p>
 * Instances of {@link Xoroshiro128StarStar} are not cryptographically
 * secure.  Consider instead using {@link java.security.SecureRandom}
 * in security-sensitive applications. Additionally,
 * default-constructed instances do not use a cryptographically random
 * seed unless the {@linkplain System#getProperty system property}
 * {@code java.util.secureRandomSeed} is set to {@code true}.
 *
 * @since 14
 */
public final class Xoroshiro128StarStar implements LeapableGenerator {

    /*
     * Implementation Overview.
     *
     * This is an implementation of the xoroshiro128** algorithm written
     * in 2016 by David Blackman and Sebastiano Vigna (vigna@acm.org),
     * and updated with improved parameters in 2018.
     * See http://xoshiro.di.unimi.it and these two papers:
     *
     *    Sebastiano Vigna. 2016. An Experimental Exploration of Marsaglia's
     *    xorshift Generators, Scrambled. ACM Transactions on Mathematical
     *    Software 42, 4, Article 30 (June 2016), 23 pages.
     *    https://doi.org/10.1145/2845077
     *
     *    David Blackman and Sebastiano Vigna.  2018.  Scrambled Linear
     *    Pseudorandom Number Generators.  Computing Research Repository (CoRR).
     *    http://arxiv.org/abs/1805.01407
     *
     * The jump operation moves the current generator forward by 2*64
     * steps; this has the same effect as calling nextLong() 2**64
     * times, but is much faster.  Similarly, the leap operation moves
     * the current generator forward by 2*96 steps; this has the same
     * effect as calling nextLong() 2**96 times, but is much faster.
     * The copy method may be used to make a copy of the current
     * generator.  Thus one may repeatedly and cumulatively copy and
     * jump to produce a sequence of generators whose states are well
     * spaced apart along the overall state cycle (indeed, the jumps()
     * and leaps() methods each produce a stream of such generators).
     * The generators can then be parceled out to other threads.
     *
     * File organization: First the non-public methods that constitute the
     * main algorithm, then the public methods.  Note that many methods are
     * defined by classes {@link AbstractJumpableGenerator} and {@link AbstractGenerator}.
     */

    /* ---------------- static fields ---------------- */

    /**
     * The seed generator for default constructors.
     */
    private static final AtomicLong DEFAULT_GEN = new AtomicLong(RandomSupport.initialSeed());

    /*
     * The period of this generator, which is 2**128 - 1.
     */
    private static final BigInteger PERIOD =
        BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE);

    /* ---------------- instance fields ---------------- */

    /**
     * The per-instance state.
     * At least one of the two fields x0 and x1 must be nonzero.
     */
    private long x0, x1;

    /* ---------------- constructors ---------------- */

    /**
     * Basic constructor that initializes all fields from parameters.
     * It then adjusts the field values if necessary to ensure that
     * all constraints on the values of fields are met.
      *
     * @param x0 first word of the initial state
     * @param x1 second word of the initial state
    */
    public Xoroshiro128StarStar(long x0, long x1) {
        this.x0 = x0;
        this.x1 = x1;
        // If x0 and x1 are both zero, we must choose nonzero values.
        if ((x0 | x1) == 0) {
            this.x0 = RandomSupport.GOLDEN_RATIO_64;
            this.x1 = RandomSupport.SILVER_RATIO_64;
        }
    }

    /**
     * Creates a new instance of {@link Xoroshiro128StarStar} using the
     * specified {@code long} value as the initial seed. Instances of
     * {@link Xoroshiro128StarStar} created with the same seed in the same
     * program generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public Xoroshiro128StarStar(long seed) {
        // Using a value with irregularly spaced 1-bits to xor the seed
        // argument tends to improve "pedestrian" seeds such as 0 or
        // other small integers.  We may as well use SILVER_RATIO_64.
        //
        // The x values are then filled in as if by a SplitMix PRNG with
        // GOLDEN_RATIO_64 as the gamma value and Stafford13 as the mixer.
        this(RandomSupport.mixStafford13(seed ^= RandomSupport.SILVER_RATIO_64),
             RandomSupport.mixStafford13(seed + RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link Xoroshiro128StarStar} that is likely to
     * generate sequences of values that are statistically independent
     * of those of any other instances in the current program execution,
     * but may, and typically does, vary across program invocations.
     */
    public Xoroshiro128StarStar() {
        // Using GOLDEN_RATIO_64 here gives us a good Weyl sequence of values.
        this(DEFAULT_GEN.getAndAdd(RandomSupport.GOLDEN_RATIO_64));
    }

    /**
     * Creates a new instance of {@link Xoroshiro128StarStar} using the specified array of
     * initial seed bytes. Instances of {@link Xoroshiro128StarStar} created with the same
     * seed array in the same program execution generate identical sequences of values.
     *
     * @param seed the initial seed
     */
    public Xoroshiro128StarStar(byte[] seed) {
        // Convert the seed to 2 long values, which are not both zero.
        long[] data = RandomSupport.convertSeedBytesToLongs(seed, 2, 2);
        long x0 = data[0], x1 = data[1];
        this.x0 = x0;
        this.x1 = x1;
    }

    /* ---------------- public methods ---------------- */

    public Xoroshiro128StarStar copy() { return new Xoroshiro128StarStar(x0, x1); }

    /*
     * To the extent possible under law, the author has dedicated all copyright and related and
     * neighboring rights to this software to the public domain worldwide. This software is
     * distributed without any warranty.
     * <p>
     * See <http://creativecommons.org/publicdomain/zero/1.0/>.
     */

    /*
     * This is the successor to xorshift128+. It is the fastest full-period generator passing
     * BigCrush without systematic failures, but due to the relatively short period it is acceptable
     * only for applications with a mild amount of parallelism; otherwise, use a xorshift1024*
     * generator.
     * <p>
     * Beside passing BigCrush, this generator passes the PractRand test suite up to (and included)
     * 16TB, with the exception of binary rank tests, which fail due to the lowest bit being an
     * LFSR; all other bits pass all tests. We suggest to use a sign test to extract a random
     * Boolean value.
     * <p>
     * Note that the generator uses a simulated rotate operation, which most C compilers will turn
     * into a single instruction. In Java, you can use Long.rotateLeft(). In languages that do not
     * make low-level rotation instructions accessible xorshift128+ could be faster.
     * <p>
     * The state must be seeded so that it is not everywhere zero. If you have a 64-bit seed, we
     * suggest to seed a splitmix64 generator and use its output to fill s.
     */

    /**
     * Returns a pseudorandom {@code long} value.
     *
     * @return a pseudorandom {@code long} value
     */
    public long nextLong() {
        final long s0 = x0;
        long s1 = x1;
	// Compute the result based on current state information
	// (this allows the computation to be overlapped with state update).
        final long result = Long.rotateLeft(s0 * 5, 7) * 9;  // "starstar" mixing function

        s1 ^= s0;
        x0 = Long.rotateLeft(s0, 24) ^ s1 ^ (s1 << 16); // a, b
        x1 = Long.rotateLeft(s1, 37); // c

        return result;
    }

    public BigInteger period() {
        return PERIOD;
    }

    public double defaultJumpDistance() {
        return 0x1.0p64;
    }

    public double defaultLeapDistance() {
        return 0x1.0p96;
    }

    private static final long[] JUMP_TABLE = { 0xdf900294d8f554a5L, 0x170865df4b3201fcL };

    private static final long[] LEAP_TABLE = { 0xd2a98b26625eee7bL, 0xdddf9b1090aa7ac1L };

    /**
     * This is the jump function for the generator. It is equivalent to 2**64 calls to nextLong();
     * it can be used to generate 2**64 non-overlapping subsequences for parallel computations.
     */
    public void jump() {
        jumpAlgorithm(JUMP_TABLE);
    }

    /**
     * This is the long-jump function for the generator. It is equivalent to 2**96 calls to next();
     * it can be used to generate 2**32 starting points, from each of which jump() will generate
     * 2**32 non-overlapping subsequences for parallel distributed computations.
     */
    public void leap() {
        jumpAlgorithm(LEAP_TABLE);
    }

    private void jumpAlgorithm(long[] table) {
        long s0 = 0, s1 = 0;
        for (int i = 0; i < table.length; i++) {
            for (int b = 0; b < 64; b++) {
                if ((table[i] & (1L << b)) != 0) {
                    s0 ^= x0;
                    s1 ^= x1;
                }
                nextLong();
            }
            x0 = s0;
            x1 = s1;
        }
    }
}
