/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

 /**
  * Classes and interfaces that support the definition and use of "random generators", a term that
  * is meant to cover what have traditionally been called "random number generators" as well as
  * generators of other sorts of randomly chosen values, and also to cover not only deterministic
  * (pseudorandom) algorithms but also generators of values that use some "truly random" physical
  * source (perhaps making use of thermal noise, for example, or quantum-mechanical effects).
  *
  * The principal interface is {@link java.util.random.RandomGenerator}, which provides methods
  * for requesting individual values of type {@code int}, {@code long}, {@code float}, {@code double}, or {@code boolean}
  * chosen (pseudo)randomly from a uniform distribution; methods for requesting values of type {@code double}
  * chosen (pseudo)randomly from a normal distribution or from an exponential distribution;
  * and methods for creating streams of (pseudo)randomly chosen values of type {@code int}, {@code long}, or {@code double}.
  * These streams are spliterator-based, allowing for parallel processing of their elements.
  *
  * An important subsidiary interface is {@link java.util.random.RandomGenerator.StreamableGenerator},
  * which provides methods for creating spliterator-based streams of {@code RandomGenerator} objects,
  * allowing for allowing for parallel processing of these objects using multiple threads.
  * Unlike {@link java.util.Random}, most implementations of {@code java.util.random.RandomGenerator}
  * are <i>not</i> thread-safe.  The intent is that instances should not be shared among threads;
  * rather, each thread should have its own random generator(s) to use.  The various pseudorandom algorithms
  * provided by this package are designed so that multiple instances will (with very high probability) behave as
  * if statistically independent.
  *
  * Historically, most pseudorandom generator algorithms have been based on some sort of
  * finite-state machine with a single, large cycle of states; when it is necessary to have
  * multiple threads use the same algorithm simultaneously, the usual technique is to arrange for
  * each thread to traverse a different region of the state cycle.  These regions may be doled out
  * to threads by starting with a single initial state and then using a "jump function" that
  * travels a long distance around the cycle (perhaps 2<sup>64</sup> steps or more); the jump function is applied repeatedly
  * and sequentially, to identify widely spaced initial states for each thread's generator.  This strategy is
  * supported by the interface {@link java.util.random.RandomGenerator.JumpableGenerator}.
  * Sometimes it is desirable to support two levels of jumping (by long distances and
  * by <i>really</i> long distances); this strategy is supported by the interface
  * {@link java.util.random.RandomGenerator.LeapableGenerator}.  There is also an interface
  * {@link java.util.random.RandomGenerator.ArbitrarilyJumpableGenerator} for algorithms that
  * allow jumping along the state cycle by any user-specified distance.
  * In this package, implementations of these interfaces include
  * {@link java.util.random.Xoroshiro128PlusPlus},
  * {@link java.util.random.Xoroshiro128StarStar},
  * {@link java.util.random.Xoshiro256StarStar},
  * and {@link java.util.random.MRG32K3A}.
  *
  * A more recent category of "splittable" pseudorandom generator algorithms uses a large family
  * of state cycles and makes some attempt to ensure that distinct instances use different state
  * cycles; but even if two instances "accidentally" use the same state cycle, they are highly
  * likely to traverse different regions parts of that shared state cycle.  This strategy is
  * supported by the interface {@link java.util.random.RandomGenerator.SplittableGenerator}.
  * In this package, implementations of this interface include
  * {@link java.util.random.L32X64MixRandom},
  * {@link java.util.random.L64X128MixRandom},
  * {@link java.util.random.L64X128PlusPlusRandom},
  * {@link java.util.random.L64X128StarStarMixRandom},
  * {@link java.util.random.L64X256MixRandom},
  * {@link java.util.random.L64X1024MixRandom},
  * {@link java.util.random.L128X128MixRandom},
  * {@link java.util.random.L128X128PlusPlusRandom},
  * {@link java.util.random.L128X128StarStarMixRandom},
  * {@link java.util.random.L128X256MixRandom},
  * {@link java.util.random.L128X1024MixRandom},
  * and {@link java.util.SplittableRandom}.
  * Generally speaking, among the "{@code LmmmXnnn}" generators, the state size of the generator is
  * {@code (mmm - 1 + nnn)} bits and the memory required for an instance is {@code (2 * mmm + nnn)} bits;
  * larger values of "{@code mmm}" imply a lower probability that two instances will traverse the
  * same state cycle; and larger values of "{@code nnn}" imply that the generator is equidistributed
  * in a larger number of dimensions.  A class with "{@code Mix}" in its name uses a strong mixing
  * function with excellent avalanche characteristics; a class with "{@code StarStar}" or "{@code PlusPlus}"
  * in its name uses a weaker but faster mixing function.  See the documentation for individual classes
  * for details about their specific characteristics.
  *
  * The class {@link java.util.random.RandomSupport} provides utility methods, constants, and
  * abstract classes frequently useful in the implementation of pseudorandom number generators
  * that satisfy the interface {@link RandomGenerator}.
  *
  * @since 14
  */

 package java.util.random;


