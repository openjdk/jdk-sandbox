/*
 * Copyright (c) 1994, 2023, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import java.lang.invoke.MethodHandles;
import java.lang.constant.Constable;
import java.lang.constant.ConstantDesc;
import java.util.Optional;

import jdk.internal.math.FloatingDecimal;
import jdk.internal.math.DoubleConsts;
import jdk.internal.math.DoubleToDecimal;
import jdk.internal.vm.annotation.IntrinsicCandidate;

/// The `Double` class wraps a value of the primitive type
/// `double` in an object. An object of type
/// `Double` contains a single field whose type is
/// `double`.
///
/// In addition, this class provides several methods for converting a
/// `double` to a `String` and a
/// `String` to a `double`, as well as other
/// constants and methods useful when dealing with a
/// `double`.
///
/// This is a <a href="{@docRoot}/java.base/java/lang/doc-files/ValueBased.html">value-based</a>
/// class; programmers should treat instances that are
/// [equal][#equals(Object)] as interchangeable and should not
/// use instances for synchronization, or unpredictable behavior may
/// occur. For example, in a future release, synchronization may fail.
///
/// <h2><a id=equivalenceRelation>Floating-point Equality, Equivalence,
/// and Comparison</a></h2>
///
/// IEEE 754 floating-point values include finite nonzero values,
/// signed zeros (`+0.0` and `-0.0`), signed infinities
/// ([positive infinity][Double#POSITIVE_INFINITY] and
/// [negative infinity][Double#NEGATIVE_INFINITY]), and
/// [NaN][Double#NaN] (not-a-number).
///
/// An _equivalence relation_ on a set of values is a boolean
/// relation on pairs of values that is reflexive, symmetric, and
/// transitive. For more discussion of equivalence relations and object
/// equality, see the [`Object.equals`][Object#equals]
/// specification. An equivalence relation partitions the values it
/// operates over into sets called _equivalence classes_.  All the
/// members of the equivalence class are equal to each other under the
/// relation. An equivalence class may contain only a single member. At
/// least for some purposes, all the members of an equivalence class
/// are substitutable for each other.  In particular, in a numeric
/// expression equivalent values can be _substituted_ for one
/// another without changing the result of the expression, meaning
/// changing the equivalence class of the result of the expression.
///
/// Notably, the built-in `==` operation on floating-point
/// values is _not_ an equivalence relation. Despite not
/// defining an equivalence relation, the semantics of the IEEE 754
/// `==` operator were deliberately designed to meet other needs
/// of numerical computation. There are two exceptions where the
/// properties of an equivalence relation are not satisfied by
/// `==` on floating-point values:
///
///   - If `v1` and `v2` are both NaN, then `v1
/// == v2` has the value `false`. Therefore, for two NaN
/// arguments the <em>reflexive</em> property of an equivalence
/// relation is <em>not</em> satisfied by the `==` operator.
///
///   - If `v1` represents `+0.0` while `v2`
/// represents `-0.0`, or vice versa, then `v1 == v2` has
/// the value `true` even though `+0.0` and `-0.0`
/// are distinguishable under various floating-point operations. For
/// example, `1.0/+0.0` evaluates to positive infinity while
/// `1.0/-0.0` evaluates to <em>negative</em> infinity and
/// positive infinity and negative infinity are neither equal to each
/// other nor equivalent to each other. Thus, while a signed zero input
/// most commonly determines the sign of a zero result, because of
/// dividing by zero, `+0.0` and `-0.0` may not be
/// substituted for each other in general. The sign of a zero input
/// also has a non-substitutable effect on the result of some math
/// library methods.
///
/// For ordered comparisons using the built-in comparison operators
/// (`<`, `<=`, etc.), NaN values have another anomalous
/// situation: a NaN is neither less than, nor greater than, nor equal
/// to any value, including itself. This means the <i>trichotomy of
/// comparison</i> does _not_ hold.
///
/// To provide the appropriate semantics for `equals` and
/// `compareTo` methods, those methods cannot simply be wrappers
/// around `==` or ordered comparison operations. Instead,
/// [equals][Double#equals] uses <a href=#repEquivalence> representation
/// equivalence</a>, defining NaN arguments to be equal to each other,
/// restoring reflexivity, and defining `+0.0` to _not_ be
/// equal to `-0.0`. For comparisons, [`compareTo`][Double#compareTo]
/// efines a total order where `-0.0` is less than
/// `+0.0` and where a NaN is equal to itself and considered
/// greater than positive infinity.
///
/// The operational semantics of `equals` and
/// `compareTo` are expressed in terms of [bit-wise converting][#doubleToLongBits]
/// the floating-point values to integral values.
///
/// The _natural ordering_ implemented by [`compareTo`][#compareTo]
/// is [consistent with equals][Comparable]. That
/// is, two objects are reported as equal by `equals` if and only
/// if `compareTo` on those objects returns zero.
///
/// The adjusted behaviors defined for `equals` and
/// `compareTo` allow instances of wrapper classes to work properly with
/// conventional data structures. For example, defining NaN
/// values to be `equals` to one another allows NaN to be used as
/// an element of a [`HashSet`][java.util.HashSet] or as the key of
/// a [`HashMap`][java.util.HashMap]. Similarly, defining
/// `compareTo` as a total ordering, including `+0.0`,
/// `-0.0`, and NaN, allows instances of wrapper classes to be used as
/// elements of a [`SortedSet`][java.util.SortedSet] or as keys of a
/// [`SortedMap`][java.util.SortedMap].
///
/// Comparing numerical equality to various useful equivalence
/// relations that can be defined over floating-point values:
///
/// <dl>
/// <dt><a id=fpNumericalEq><i>numerical equality</i></a> ({@code ==}
/// operator): (<em>Not</em> an equivalence relation)</dt>
/// <dd>Two floating-point values represent the same extended real
/// number. The extended real numbers are the real numbers augmented
/// with positive infinity and negative infinity. Under numerical
/// equality, {@code +0.0} and {@code -0.0} are equal since they both
/// map to the same real value, 0. A NaN does not map to any real
/// number and is not equal to any value, including itself.
/// </dd>
///
/// <dt><i>bit-wise equivalence</i>:</dt>
/// <dd>The bits of the two floating-point values are the same. This
/// equivalence relation for {@code double} values {@code a} and {@code
/// b} is implemented by the expression
/// <br>{@code Double.doubleTo}<code><b>Raw</b></code>{@code LongBits(a) == Double.doubleTo}<code><b>Raw</b></code>{@code LongBits(b)}<br>
/// Under this relation, {@code +0.0} and {@code -0.0} are
/// distinguished from each other and every bit pattern encoding a NaN
/// is distinguished from every other bit pattern encoding a NaN.
/// </dd>
///
/// <dt><i><a id=repEquivalence>representation equivalence</a></i>:</dt>
/// <dd>The two floating-point values represent the same IEEE 754
/// <i>datum</i>. In particular, for [finite][#isFinite(double)]
/// values, the sign, [exponent][Math#getExponent(double)],
/// and significand components of the floating-point values
/// are the same. Under this relation:
///
///   -  `+0.0` and `-0.0` are distinguished from each other.
///   -  every bit pattern encoding a NaN is considered equivalent to each other
///   -  positive infinity is equivalent to positive infinity; negative
///      infinity is equivalent to negative infinity.
///
/// Expressions implementing this equivalence relation include:
///
///   - `Double.doubleToLongBits(a) == Double.doubleToLongBits(b)`
///   - `Double.valueOf(a).equals(Double.valueOf(b))`
///   - `Double.compare(a, b) == 0`
///
/// Note that representation equivalence is often an appropriate notion
/// of equivalence to test the behavior of [math libraries][StrictMath].
/// </dd>
/// </dl>
///
/// For two binary floating-point values `a` and `b`, if
/// neither of `a` and `b` is zero or NaN, then the three
/// relations numerical equality, bit-wise equivalence, and
/// representation equivalence of `a` and `b` have the same
/// `true`/`false` value. In other words, for binary
/// floating-point values, the three relations only differ if at least
/// one argument is zero or NaN.
///
/// <h2><a id=decimalToBinaryConversion>Decimal &harr; Binary Conversion Issues</a></h2>
///
/// Many surprising results of binary floating-point arithmetic trace
/// back to aspects of decimal to binary conversion and binary to
/// decimal conversion. While integer values can be exactly represented
/// in any base, which fractional values can be exactly represented in
/// a base is a function of the base. For example, in base 10, 1/3 is a
/// repeating fraction (0.33333....); but in base 3, 1/3 is exactly
/// 0.1<sub>(3)</sub>, that is 1&nbsp;&times;&nbsp;3<sup>-1</sup>.
/// Similarly, in base 10, 1/10 is exactly representable as 0.1
/// (1&nbsp;&times;&nbsp;10<sup>-1</sup>), but in base 2, it is a
/// repeating fraction (0.0001100110011...<sub>(2)</sub>).
///
/// Values of the `float` type have {@value Float#PRECISION}
/// bits of precision and values of the `double` type have
/// {@value Double#PRECISION} bits of precision. Therefore, since 0.1
/// is a repeating fraction in base 2 with a four-bit repeat,
/// `0.1f` != `0.1d`. In more detail, including hexadecimal
/// floating-point literals:
///
///   - The exact numerical value of `0.1f` (`0x1.99999a0000000p-4f`) is
///     0.100000001490116119384765625.
///   - The exact numerical value of `0.1d` (`0x1.999999999999ap-4d`) is
///     0.1000000000000000055511151231257827021181583404541015625.
///
/// These are the closest `float` and `double` values,
/// respectively, to the numerical value of 0.1.  These results are
/// consistent with a `float` value having the equivalent of 6 to
/// 9 digits of decimal precision and a `double` value having the
/// equivalent of 15 to 17 digits of decimal precision. (The
/// equivalent precision varies according to the different relative
/// densities of binary and decimal values at different points along the
/// real number line.)
///
/// This representation hazard of decimal fractions is one reason to
/// use caution when storing monetary values as `float` or
/// `double`. Alternatives include:
///
///   - using [java.math.BigDecimal] to store decimal
/// fractional values exactly
///
///   - scaling up so the monetary value is an integer &mdash; for
/// example, multiplying by 100 if the value is denominated in cents or
/// multiplying by 1000 if the value is denominated in mills &mdash;
/// and then storing that scaled value in an integer type
///
/// For each finite floating-point value and a given floating-point
/// type, there is a contiguous region of the real number line which
/// maps to that value. Under the default round to nearest rounding
/// policy (JLS {@jls 15.4}), this contiguous region for a value is
/// typically one [ulp][Math#ulp] (unit in the last place)
/// wide and centered around the exactly representable value. (At
/// exponent boundaries, the region is asymmetrical and larger on the
/// side with the larger exponent.) For example, for `0.1f`, the
/// region can be computed as follows:
///
/// <pre><code class="language-java">// Numeric values listed are exact values
/// oneTenthApproxAsFloat = 0.100000001490116119384765625;
/// ulpOfoneTenthApproxAsFloat = Math.ulp(0.1f) = 7.450580596923828125E-9;
/// // Numeric range that is converted to the float closest to 0.1, _excludes_ endpoints
/// (oneTenthApproxAsFloat - &frac12;ulpOfoneTenthApproxAsFloat, oneTenthApproxAsFloat + &frac12;ulpOfoneTenthApproxAsFloat) =
/// (0.0999999977648258209228515625, 0.1000000052154064178466796875)</code></pre>
///
/// In particular, a correctly rounded decimal to binary conversion
/// of any string representing a number in this range, say by
/// [Float#parseFloat(String)], will be converted to the same value:
///
/// {@snippet lang="java" :
/// Float.parseFloat("0.0999999977648258209228515625000001"); // rounds up to oneTenthApproxAsFloat
/// Float.parseFloat("0.099999998");                          // rounds up to oneTenthApproxAsFloat
/// Float.parseFloat("0.1");                                  // rounds up to oneTenthApproxAsFloat
/// Float.parseFloat("0.100000001490116119384765625");        // exact conversion
/// Float.parseFloat("0.100000005215406417846679687");        // rounds down to oneTenthApproxAsFloat
/// Float.parseFloat("0.100000005215406417846679687499999");  // rounds down to oneTenthApproxAsFloat
/// }
///
/// Similarly, an analogous range can be constructed  for the
/// `double` type based on the exact value of `double`
/// approximation to `0.1d` and the numerical value of
/// `Math.ulp(0.1d)` and likewise for other particular numerical values
/// in the `float` and `double` types.
///
/// As seen in the above conversions, compared to the exact
/// numerical value the operation would have without rounding, the same
/// floating-point value as a result can be:
///
///   - greater than the exact result
///   - equal to the exact result
///   - less than the exact result
///
/// A floating-point value doesn't "know" whether it was the result of
/// rounding up, or rounding down, or an exact operation; it contains
/// no history of how it was computed. Consequently, the sum of
/// {@snippet lang="java" :
/// 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f + 0.1f;
/// // Numerical value of computed sum: 1.00000011920928955078125,
/// // the next floating-point value larger than 1.0f, equal to Math.nextUp(1.0f).
/// }
/// or
/// {@snippet lang="java" :
/// 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d + 0.1d;
/// // Numerical value of computed sum: 0.99999999999999988897769753748434595763683319091796875,
/// // the next floating-point value smaller than 1.0d, equal to Math.nextDown(1.0d).
/// }
///
/// should _not_ be expected to be exactly equal to 1.0, but
/// only to be close to 1.0. Consequently, the following code is an
/// infinite loop:
///
/// {@snippet lang="java" :
/// double d = 0.0;
/// while (d != 1.0) { // Surprising infinite loop
///   d += 0.1; // Sum never _exactly_ equals 1.0
/// }
/// }
///
/// Instead, use an integer loop count for counted loops:
///
/// {@snippet lang="java" :
/// double d = 0.0;
/// for (int i = 0; i < 10; i++) {
///   d += 0.1;
/// } // Value of d is equal to Math.nextDown(1.0).
/// }
///
/// or test against a floating-point limit using ordered comparisons
/// (`<`, `<=`, `>`, `>=`):
///
/// {@snippet lang="java" :
///  double d = 0.0;
///  while (d <= 1.0) {
///    d += 0.1;
///  } // Value of d approximately 1.0999999999999999
///  }
///
/// While floating-point arithmetic may have surprising results, IEEE
/// 754 floating-point arithmetic follows a principled design and its
/// behavior is predictable on the Java platform.
///
/// @jls 4.2.3 Floating-Point Types, Formats, and Values
/// @jls 4.2.4. Floating-Point Operations
/// @jls 15.21.1 Numerical Equality Operators == and !=
/// @jls 15.20.1 Numerical Comparison Operators  {@code <}, {@code <=}, {@code >}, and {@code >=}
///
/// @see <a href="https://standards.ieee.org/ieee/754/6210/">
///      <cite>IEEE Standard for Floating-Point Arithmetic</cite></a>
///
/// @author  Lee Boynton
/// @author  Arthur van Hoff
/// @author  Joseph D. Darcy
/// @since 1.0
@jdk.internal.ValueBased
public final class Double extends Number
        implements Comparable<Double>, Constable, ConstantDesc {
    /// A constant holding the positive infinity of type
    /// `double`. It is equal to the value returned by
    /// `Double.longBitsToDouble(0x7ff0000000000000L)`.
    public static final double POSITIVE_INFINITY = 1.0 / 0.0;

    /// A constant holding the negative infinity of type
    /// `double`. It is equal to the value returned by
    /// `Double.longBitsToDouble(0xfff0000000000000L)`.
    public static final double NEGATIVE_INFINITY = -1.0 / 0.0;

    /// A constant holding a Not-a-Number (NaN) value of type
    /// `double`. It is equivalent to the value returned by
    /// `Double.longBitsToDouble(0x7ff8000000000000L)`.
    public static final double NaN = 0.0d / 0.0;

    /// A constant holding the largest positive finite value of type
    /// `double`,
    /// (2-2<sup>-52</sup>)&middot;2<sup>1023</sup>.  It is equal to
    /// the hexadecimal floating-point literal
    /// `0x1.fffffffffffffP+1023` and also equal to
    /// `Double.longBitsToDouble(0x7fefffffffffffffL)`.
    public static final double MAX_VALUE = 0x1.fffffffffffffP+1023; // 1.7976931348623157e+308

    /// A constant holding the smallest positive normal value of type
    /// `double`, 2<sup>-1022</sup>.  It is equal to the
    /// hexadecimal floating-point literal `0x1.0p-1022` and also
    /// equal to `Double.longBitsToDouble(0x0010000000000000L)`.
    ///
    /// @since 1.6
    public static final double MIN_NORMAL = 0x1.0p-1022; // 2.2250738585072014E-308

    /// A constant holding the smallest positive nonzero value of type
    /// `double`, 2<sup>-1074</sup>. It is equal to the
    /// hexadecimal floating-point literal
    /// `0x0.0000000000001P-1022` and also equal to
    /// `Double.longBitsToDouble(0x1L)`.
    public static final double MIN_VALUE = 0x0.0000000000001P-1022; // 4.9e-324

    /// The number of bits used to represent a `double` value.
    ///
    /// @since 1.5
    public static final int SIZE = 64;

    /// The number of bits in the significand of a `double` value.
    /// This is the parameter N in section {@jls 4.2.3} of
    /// <cite>The Java Language Specification</cite>.
    ///
    /// @since 19
    public static final int PRECISION = 53;

    /// Maximum exponent a finite `double` variable may have.
    /// It is equal to the value returned by
    /// `Math.getExponent(Double.MAX_VALUE)`.
    ///
    /// @since 1.6
    public static final int MAX_EXPONENT = (1 << (SIZE - PRECISION - 1)) - 1; // 1023

    /// Minimum exponent a normalized `double` variable may
    /// have.  It is equal to the value returned by
    /// `Math.getExponent(Double.MIN_NORMAL)`.
    ///
    /// @since 1.6
    public static final int MIN_EXPONENT = 1 - MAX_EXPONENT; // -1022

    /// The number of bytes used to represent a `double` value.
    ///
    /// @since 1.8
    public static final int BYTES = SIZE / Byte.SIZE;

    /// The `Class` instance representing the primitive type
    /// `double`.
    ///
    /// @since 1.1
    @SuppressWarnings("unchecked")
    public static final Class<Double>   TYPE = (Class<Double>) Class.getPrimitiveClass("double");

    /// Returns a string representation of the `double`
    /// argument. All characters mentioned below are ASCII characters.
    ///
    ///   - If the argument is NaN, the result is the string
    ///         "`NaN`".
    ///   - Otherwise, the result is a string that represents the sign and
    ///     magnitude (absolute value) of the argument. If the sign is negative,
    ///     the first character of the result is '`-`'
    ///     (`'\u005Cu002D'`); if the sign is positive, no sign character
    ///     appears in the result. As for the magnitude <i>m</i>:
    ///
    ///       - If <i>m</i> is infinity, it is represented by the characters
    ///         `"Infinity"`; thus, positive infinity produces the result
    ///         `"Infinity"` and negative infinity produces the result
    ///         `"-Infinity"`.
    ///
    ///       - If <i>m</i> is zero, it is represented by the characters
    ///         `"0.0"`; thus, negative zero produces the result
    ///         `"-0.0"` and positive zero produces the result
    ///         `"0.0"`.
    ///
    ///       -  Otherwise <i>m</i> is positive and finite.
    ///         It is converted to a string in two stages:
    ///
    ///           - _Selection of a decimal<_:
    ///             A well-defined decimal <i>d</i><sub><i>m</i></sub>
    ///             is selected to represent <i>m</i>.
    ///             This decimal is (almost always) the _shortest_ one that
    ///             rounds to <i>m</i> according to the round to nearest
    ///             rounding policy of IEEE 754 floating-point arithmetic.
    ///           - _Formatting as a string_:
    ///             The decimal <i>d</i><sub><i>m</i></sub> is formatted as a string,
    ///             either in plain or in computerized scientific notation,
    ///             depending on its value.
    ///
    /// A _decimal_ is a number of the form
    /// <i>s</i>&times;10<sup><i>i</i></sup>
    /// for some (unique) integers <i>s</i> &gt; 0 and <i>i</i> such that
    /// <i>s</i> is not a multiple of 10.
    /// These integers are the _significand_ and
    /// the _exponent_, respectively, of the decimal.
    /// The _length_ of the decimal is the (unique)
    /// positive integer <i>n</i> meeting
    /// 10<sup><i>n</i>-1</sup> &le; <i>s</i> &lt; 10<sup><i>n</i></sup>.
    ///
    /// The decimal <i>d</i><sub><i>m</i></sub> for a finite positive <i>m</i>
    /// is defined as follows:
    ///
    ///   - Let <i>R</i> be the set of all decimals that round to <i>m</i>
    ///     according to the usual <em>round to nearest</em> rounding policy of
    ///     IEEE 754 floating-point arithmetic.
    ///   - Let <i>p</i> be the minimal length over all decimals in <i>R</i>.
    ///     <li>When <i>p</i> &ge; 2, let <i>T</i> be the set of all decimals
    ///     in <i>R</i> with length <i>p</i>.
    ///     Otherwise, let <i>T</i> be the set of all decimals
    ///     in <i>R</i> with length 1 or 2.
    ///   - Define <i>d</i><sub><i>m</i></sub> as the decimal in <i>T</i>
    ///     that is closest to <i>m</i>.
    ///     Or if there are two such decimals in <i>T</i>,
    ///     select the one with the even significand.
    ///
    /// The (uniquely) selected decimal <i>d</i><sub><i>m</i></sub>
    /// is then formatted.
    /// Let <i>s</i>, <i>i</i> and <i>n</i> be the significand, exponent and
    /// length of <i>d</i><sub><i>m</i></sub>, respectively.
    /// Further, let <i>e</i> = <i>n</i> + <i>i</i> - 1 and let
    /// <i>s</i><sub>1</sub>&hellip;<i>s</i><sub><i>n</i></sub>
    /// be the usual decimal expansion of <i>s</i>.
    /// Note that <i>s</i><sub>1</sub> &ne; 0
    /// and <i>s</i><sub><i>n</i></sub> &ne; 0.
    /// Below, the decimal point `'.'` is `'\u005Cu002E'`
    /// and the exponent indicator `'E'` is `'\u005Cu0045'`.
    ///
    ///   - Case -3 &le; <i>e</i> &lt; 0:
    ///     <i>d</i><sub><i>m</i></sub> is formatted as
    ///     `0.0`&hellip;`0`<!--
    ///     --><i>s</i><sub>1</sub>&hellip;<i>s</i><sub><i>n</i></sub>,
    ///     where there are exactly -(<i>n</i> + <i>i</i>) zeroes between
    ///     the decimal point and <i>s</i><sub>1</sub>.
    ///     For example, 123 &times; 10<sup>-4</sup> is formatted as
    ///     `0.0123`.
    ///   - Case 0 &le; <i>e</i> &lt; 7:
    ///
    ///       - Subcase <i>i</i> &ge; 0:
    ///         <i>d</i><sub><i>m</i></sub> is formatted as
    ///         <i>s</i><sub>1</sub>&hellip;<i>s</i><sub><i>n</i></sub><!--
    ///         --><code>0</code>&hellip;<code>0.0</code>,
    ///         where there are exactly <i>i</i> zeroes
    ///         between <i>s</i><sub><i>n</i></sub> and the decimal point.
    ///         For example, 123 &times; 10<sup>2</sup> is formatted as
    ///         `12300.0`.
    ///       - Subcase <i>i</i> &lt; 0:
    ///         <i>d</i><sub><i>m</i></sub> is formatted as
    ///         <i>s</i><sub>1</sub>&hellip;<!--
    ///         --><i>s</i><sub><i>n</i>+<i>i</i></sub><code>.</code><!--
    ///         --><i>s</i><sub><i>n</i>+<i>i</i>+1</sub>&hellip;<!--
    ///         --><i>s</i><sub><i>n</i></sub>,
    ///         where there are exactly -<i>i</i> digits to the right of
    ///         the decimal point.
    ///         For example, 123 &times; 10<sup>-1</sup> is formatted as
    ///         `12.3`.
    ///
    ///   - Case <i>e</i> &lt; -3 or <i>e</i> &ge; 7:
    ///     computerized scientific notation is used to format
    ///     <i>d</i><sub><i>m</i></sub>.
    ///     Here <i>e</i> is formatted as by [Integer#toString(int)].
    ///
    ///       - Subcase <i>n</i> = 1:
    ///         <i>d</i><sub><i>m</i></sub> is formatted as
    ///         <i>s</i><sub>1</sub><code>.0E</code><i>e</i>.
    ///         For example, 1 &times; 10<sup>23</sup> is formatted as
    ///         `1.0E23`.
    ///       - Subcase <i>n</i> &gt; 1:
    ///         <i>d</i><sub><i>m</i></sub> is formatted as
    ///         <i>s</i><sub>1</sub><code>.</code><i>s</i><sub>2</sub><!--
    ///         -->&hellip;<i>s</i><sub><i>n</i></sub><code>E</code><i>e</i>.
    ///         For example, 123 &times; 10<sup>-21</sup> is formatted as
    ///         `1.23E-19`.
    ///
    /// To create localized string representations of a floating-point
    /// value, use subclasses of [java.text.NumberFormat].
    ///
    /// @param   d   the `double` to be converted.
    /// @return a string representation of the argument.
    public static String toString(double d) {
        return DoubleToDecimal.toString(d);
    }

    /// Returns a hexadecimal string representation of the
    /// `double` argument. All characters mentioned below
    /// are ASCII characters.
    ///
    ///   - If the argument is NaN, the result is the string
    ///         "`NaN`".
    ///   - Otherwise, the result is a string that represents the sign
    ///     and magnitude of the argument. If the sign is negative, the
    ///     first character of the result is '`-`'
    ///     (`'\u005Cu002D'`); if the sign is positive, no sign
    ///     character appears in the result. As for the magnitude <i>m</i>:
    ///
    ///       - If <i>m</i> is infinity, it is represented by the string
    ///         `"Infinity"`; thus, positive infinity produces the
    ///         result `"Infinity"` and negative infinity produces
    ///         the result `"-Infinity"`.
    ///
    ///       - If <i>m</i> is zero, it is represented by the string
    ///         `"0x0.0p0"`; thus, negative zero produces the result
    ///         `"-0x0.0p0"` and positive zero produces the result
    ///         `"0x0.0p0"`.
    ///
    ///       - If <i>m</i> is a `double` value with a
    ///         normalized representation, substrings are used to represent the
    ///         significand and exponent fields.  The significand is
    ///         represented by the characters `"0x1."`
    ///         followed by a lowercase hexadecimal representation of the rest
    ///         of the significand as a fraction.  Trailing zeros in the
    ///         hexadecimal representation are removed unless all the digits
    ///         are zero, in which case a single zero is used. Next, the
    ///         exponent is represented by `"p"` followed
    ///         by a decimal string of the unbiased exponent as if produced by
    ///         a call to [`Integer.toString`][Integer#toString(int)] on the
    ///         exponent value.
    ///
    ///       - If <i>m</i> is a `double` value with a subnormal
    ///         representation, the significand is represented by the
    ///         characters `"0x0."` followed by a
    ///         hexadecimal representation of the rest of the significand as a
    ///         fraction.  Trailing zeros in the hexadecimal representation are
    ///         removed. Next, the exponent is represented by
    ///         `"p-1022"`.  Note that there must be at
    ///         least one nonzero digit in a subnormal significand.
    ///
    ///
    /// <table class="striped">
    /// <caption>Examples</caption>
    /// <thead>
    /// <tr><th scope="col">Floating-point Value</th><th scope="col">Hexadecimal String</th>
    /// </thead>
    /// <tbody style="text-align:right">
    /// <tr><th scope="row">{@code 1.0}</th> <td>{@code 0x1.0p0}</td>
    /// <tr><th scope="row">{@code -1.0}</th>        <td>{@code -0x1.0p0}</td>
    /// <tr><th scope="row">{@code 2.0}</th> <td>{@code 0x1.0p1}</td>
    /// <tr><th scope="row">{@code 3.0}</th> <td>{@code 0x1.8p1}</td>
    /// <tr><th scope="row">{@code 0.5}</th> <td>{@code 0x1.0p-1}</td>
    /// <tr><th scope="row">{@code 0.25}</th>        <td>{@code 0x1.0p-2}</td>
    /// <tr><th scope="row">{@code Double.MAX_VALUE}</th>
    ///     <td>{@code 0x1.fffffffffffffp1023}</td>
    /// <tr><th scope="row">{@code Minimum Normal Value}</th>
    ///     <td>{@code 0x1.0p-1022}</td>
    /// <tr><th scope="row">{@code Maximum Subnormal Value}</th>
    ///     <td>{@code 0x0.fffffffffffffp-1022}</td>
    /// <tr><th scope="row">{@code Double.MIN_VALUE}</th>
    ///     <td>{@code 0x0.0000000000001p-1022}</td>
    /// </tbody>
    /// </table>
    /// @param   d   the {@code double} to be converted.
    /// @return a hex string representation of the argument.
    /// @since 1.5
    /// @author Joseph D. Darcy
    public static String toHexString(double d) {
        /*
         * Modeled after the "a" conversion specifier in C99, section
         * 7.19.6.1; however, the output of this method is more
         * tightly specified.
         */
        if (!isFinite(d) )
            // For infinity and NaN, use the decimal output.
            return Double.toString(d);
        else {
            // Initialized to maximum size of output.
            StringBuilder answer = new StringBuilder(24);

            if (Math.copySign(1.0, d) == -1.0)    // value is negative,
                answer.append("-");                  // so append sign info

            answer.append("0x");

            d = Math.abs(d);

            if(d == 0.0) {
                answer.append("0.0p0");
            } else {
                boolean subnormal = (d < Double.MIN_NORMAL);

                // Isolate significand bits and OR in a high-order bit
                // so that the string representation has a known
                // length.
                long signifBits = (Double.doubleToLongBits(d)
                                   & DoubleConsts.SIGNIF_BIT_MASK) |
                    0x1000000000000000L;

                // Subnormal values have a 0 implicit bit; normal
                // values have a 1 implicit bit.
                answer.append(subnormal ? "0." : "1.");

                // Isolate the low-order 13 digits of the hex
                // representation.  If all the digits are zero,
                // replace with a single 0; otherwise, remove all
                // trailing zeros.
                String signif = Long.toHexString(signifBits).substring(3,16);
                answer.append(signif.equals("0000000000000") ? // 13 zeros
                              "0":
                              signif.replaceFirst("0{1,12}$", ""));

                answer.append('p');
                // If the value is subnormal, use the E_min exponent
                // value for double; otherwise, extract and report d's
                // exponent (the representation of a subnormal uses
                // E_min -1).
                answer.append(subnormal ?
                              Double.MIN_EXPONENT:
                              Math.getExponent(d));
            }
            return answer.toString();
        }
    }

    /// Returns a `Double` object holding the
    /// `double` value represented by the argument string
    /// `s`.
    ///
    /// If `s` is `null`, then a
    /// `NullPointerException` is thrown.
    ///
    /// Leading and trailing whitespace characters in `s`
    /// are ignored.  Whitespace is removed as if by the
    /// [String#trim] method; that is, both ASCII space and control
    /// characters are removed. The rest of `s` should
    /// constitute a _FloatValue_ as described by the lexical
    /// syntax rules:
    ///
    /// <blockquote>
    /// <dl>
    /// <dt><i>FloatValue:</i>
    /// <dd><i>Sign<sub>opt</sub></i> {@code NaN}
    /// <dd><i>Sign<sub>opt</sub></i> {@code Infinity}
    /// <dd><i>Sign<sub>opt</sub> FloatingPointLiteral</i>
    /// <dd><i>Sign<sub>opt</sub> HexFloatingPointLiteral</i>
    /// <dd><i>SignedInteger</i>
    /// </dl>
    ///
    /// <dl>
    /// <dt><i>HexFloatingPointLiteral</i>:
    /// <dd> <i>HexSignificand BinaryExponent FloatTypeSuffix<sub>opt</sub></i>
    /// </dl>
    ///
    /// <dl>
    /// <dt><i>HexSignificand:</i>
    /// <dd><i>HexNumeral</i>
    /// <dd><i>HexNumeral</i> {@code .}
    /// <dd>{@code 0x} <i>HexDigits<sub>opt</sub>
    ///     </i>{@code .}<i> HexDigits</i>
    /// <dd>{@code 0X}<i> HexDigits<sub>opt</sub>
    ///     </i>{@code .} <i>HexDigits</i>
    /// </dl>
    ///
    /// <dl>
    /// <dt><i>BinaryExponent:</i>
    /// <dd><i>BinaryExponentIndicator SignedInteger</i>
    /// </dl>
    ///
    /// <dl>
    /// <dt><i>BinaryExponentIndicator:</i>
    /// <dd>{@code p}
    /// <dd>{@code P}
    /// </dl>
    ///
    /// </blockquote>
    ///
    /// where _Sign_, _FloatingPointLiteral_,
    /// _HexNumeral_, _HexDigits_, _SignedInteger_ and
    /// _FloatTypeSuffix_ are as defined in the lexical structure
    /// sections of
    /// <cite>The Java Language Specification</cite>,
    /// except that underscores are not accepted between digits.
    /// If `s` does not have the form of
    /// a _FloatValue_, then a `NumberFormatException`
    /// is thrown. Otherwise, `s` is regarded as
    /// representing an exact decimal value in the usual
    /// "computerized scientific notation" or as an exact
    /// hexadecimal value; this exact numerical value is then
    /// conceptually converted to an "infinitely precise"
    /// binary value that is then rounded to type `double`
    /// by the usual round-to-nearest rule of IEEE 754 floating-point
    /// arithmetic, which includes preserving the sign of a zero
    /// value.
    ///
    /// Note that the round-to-nearest rule also implies overflow and
    /// underflow behaviour; if the exact value of `s` is large
    /// enough in magnitude (greater than or equal to (
    /// [#MAX_VALUE] + [`ulp(MAX_VALUE)`][Math#ulp(double)]/2),
    /// rounding to `double` will result in an infinity and if the
    /// exact value of `s` is small enough in magnitude (less
    /// than or equal to [#MIN_VALUE]/2), rounding to float will
    /// result in a zero.
    ///
    /// Finally, after rounding a `Double` object representing
    /// this `double` value is returned.
    ///
    ///  To interpret localized string representations of a
    /// floating-point value, use subclasses of
    /// [java.text.NumberFormat].
    ///
    /// Note that trailing format specifiers, specifiers that
    /// determine the type of a floating-point literal
    /// (`1.0f` is a `float` value;
    /// `1.0d` is a `double` value), do
    /// _not_ influence the results of this method.  In other
    /// words, the numerical value of the input string is converted
    /// directly to the target floating-point type.  The two-step
    /// sequence of conversions, string to `float` followed
    /// by `float` to `double`, is _not_
    /// equivalent to converting a string directly to
    /// `double`. For example, the `float`
    /// literal `0.1f` is equal to the `double`
    /// value `0.10000000149011612`; the `float`
    /// literal `0.1f` represents a different numerical
    /// value than the `double` literal
    /// `0.1`. (The numerical value 0.1 cannot be exactly
    /// represented in a binary floating-point number.)
    ///
    /// To avoid calling this method on an invalid string and having
    /// a `NumberFormatException` be thrown, the regular
    /// expression below can be used to screen the input string:
    ///
    /// {@snippet lang="java" :
    ///  final String Digits     = "(\\p{Digit}+)";
    ///  final String HexDigits  = "(\\p{XDigit}+)";
    ///  // an exponent is 'e' or 'E' followed by an optionally
    ///  // signed decimal integer.
    ///  final String Exp        = "[eE][+-]?"+Digits;
    ///  final String fpRegex    =
    ///      ("[\\x00-\\x20]*"+  // Optional leading "whitespace"
    ///       "[+-]?(" + // Optional sign character
    ///       "NaN|" +           // "NaN" string
    ///       "Infinity|" +      // "Infinity" string
    ///
    ///       // A decimal floating-point string representing a finite positive
    ///       // number without a leading sign has at most five basic pieces:
    ///       // Digits . Digits ExponentPart FloatTypeSuffix
    ///       //
    ///       // Since this method allows integer-only strings as input
    ///       // in addition to strings of floating-point literals, the
    ///       // two sub-patterns below are simplifications of the grammar
    ///       // productions from section 3.10.2 of
    ///       // The Java Language Specification.
    ///
    ///       // Digits ._opt Digits_opt ExponentPart_opt FloatTypeSuffix_opt
    ///       "((("+Digits+"(\\.)?("+Digits+"?)("+Exp+")?)|"+
    ///
    ///       // . Digits ExponentPart_opt FloatTypeSuffix_opt
    ///       "(\\.("+Digits+")("+Exp+")?)|"+
    ///
    ///       // Hexadecimal strings
    ///       "((" +
    ///        // 0[xX] HexDigits ._opt BinaryExponent FloatTypeSuffix_opt
    ///        "(0[xX]" + HexDigits + "(\\.)?)|" +
    ///
    ///        // 0[xX] HexDigits_opt . HexDigits BinaryExponent FloatTypeSuffix_opt
    ///        "(0[xX]" + HexDigits + "?(\\.)" + HexDigits + ")" +
    ///
    ///        ")[pP][+-]?" + Digits + "))" +
    ///       "[fFdD]?))" +
    ///       "[\\x00-\\x20]");// Optional trailing "whitespace"
    ///  // @link region substring="Pattern.matches" target ="java.util.regex.Pattern#matches"
    ///  if (Pattern.matches(fpRegex, myString))
    ///      Double.valueOf(myString); // Will not throw NumberFormatException
    /// // @end
    ///  else {
    ///      // Perform suitable alternative action
    ///  }
    /// }
    ///
    /// @param      s   the string to be parsed.
    /// @return     a `Double` object holding the value
    ///             represented by the `String` argument.
    /// @throws     NumberFormatException  if the string does not contain a
    ///             parsable number.
    /// @see Double##decimalToBinaryConversion Decimal &harr; Binary Conversion Issues
    public static Double valueOf(String s) throws NumberFormatException {
        return new Double(parseDouble(s));
    }

    /// Returns a `Double` instance representing the specified
    /// `double` value.
    /// If a new `Double` instance is not required, this method
    /// should generally be used in preference to the constructor
    /// [#Double(double)], as this method is likely to yield
    /// significantly better space and time performance by caching
    /// frequently requested values.
    ///
    /// @param  d a double value.
    /// @return a `Double` instance representing `d`.
    /// @since  1.5
    @IntrinsicCandidate
    public static Double valueOf(double d) {
        return new Double(d);
    }

    /// Returns a new `double` initialized to the value
    /// represented by the specified `String`, as performed
    /// by the `valueOf` method of class
    /// `Double`.
    ///
    /// @param  s   the string to be parsed.
    /// @return the `double` value represented by the string
    ///         argument.
    /// @throws NullPointerException  if the string is null
    /// @throws NumberFormatException if the string does not contain
    ///         a parsable `double`.
    /// @see    java.lang.Double#valueOf(String)
    /// @see    Double##decimalToBinaryConversion Decimal &harr; Binary Conversion Issues
    /// @since 1.2
    public static double parseDouble(String s) throws NumberFormatException {
        return FloatingDecimal.parseDouble(s);
    }

    /// Returns `true` if the specified number is a
    /// Not-a-Number (NaN) value, `false` otherwise.
    ///
    /// @apiNote
    /// This method corresponds to the isNaN operation defined in IEEE
    /// 754.
    ///
    /// @param   v   the value to be tested.
    /// @return  `true` if the value of the argument is NaN;
    ///          `false` otherwise.
    public static boolean isNaN(double v) {
        return (v != v);
    }

    /// Returns `true` if the specified number is infinitely
    /// large in magnitude, `false` otherwise.
    ///
    /// @apiNote
    /// This method corresponds to the isInfinite operation defined in
    /// IEEE 754.
    ///
    /// @param   v   the value to be tested.
    /// @return  `true` if the value of the argument is positive
    ///          infinity or negative infinity; `false` otherwise.
    @IntrinsicCandidate
    public static boolean isInfinite(double v) {
        return Math.abs(v) > MAX_VALUE;
    }

    /// Returns `true` if the argument is a finite floating-point
    /// value; returns `false` otherwise (for NaN and infinity
    /// arguments).
    ///
    /// @apiNote
    /// This method corresponds to the isFinite operation defined in
    /// IEEE 754.
    ///
    /// @param d the `double` value to be tested
    /// @return `true` if the argument is a finite
    /// floating-point value, `false` otherwise.
    /// @since 1.8
    @IntrinsicCandidate
    public static boolean isFinite(double d) {
        return Math.abs(d) <= Double.MAX_VALUE;
    }

    /// The value of the Double.
    ///
    /// @serial
    private final double value;

    /// Constructs a newly allocated `Double` object that
    /// represents the primitive `double` argument.
    ///
    /// @param   value   the value to be represented by the `Double`.
    ///
    /// @deprecated
    /// It is rarely appropriate to use this constructor. The static factory
    /// [#valueOf(double)] is generally a better choice, as it is
    /// likely to yield significantly better space and time performance.
    @Deprecated(since="9", forRemoval = true)
    public Double(double value) {
        this.value = value;
    }

    /// Constructs a newly allocated `Double` object that
    /// represents the floating-point value of type `double`
    /// represented by the string. The string is converted to a
    /// `double` value as if by the `valueOf` method.
    ///
    /// @param  s  a string to be converted to a `Double`.
    /// @throws    NumberFormatException if the string does not contain a
    ///            parsable number.
    ///
    /// @deprecated
    /// It is rarely appropriate to use this constructor.
    /// Use [#parseDouble(String)] to convert a string to a
    /// `double` primitive, or use [#valueOf(String)]
    /// to convert a string to a `Double` object.
    @Deprecated(since="9", forRemoval = true)
    public Double(String s) throws NumberFormatException {
        value = parseDouble(s);
    }

    /// Returns `true` if this `Double` value is
    /// a Not-a-Number (NaN), `false` otherwise.
    ///
    /// @return  `true` if the value represented by this object is
    ///          NaN; `false` otherwise.
    public boolean isNaN() {
        return isNaN(value);
    }

    /// Returns `true` if this `Double` value is
    /// infinitely large in magnitude, `false` otherwise.
    ///
    /// @return  `true` if the value represented by this object is
    ///          positive infinity or negative infinity;
    ///          `false` otherwise.
    public boolean isInfinite() {
        return isInfinite(value);
    }

    /// Returns a string representation of this `Double` object.
    /// The primitive `double` value represented by this
    /// object is converted to a string exactly as if by the method
    /// `toString` of one argument.
    ///
    /// @return  a `String` representation of this object.
    /// @see java.lang.Double#toString(double)
    public String toString() {
        return toString(value);
    }

    /// Returns the value of this `Double` as a `byte`
    /// after a narrowing primitive conversion.
    ///
    /// @return  the `double` value represented by this object
    ///          converted to type `byte`
    /// @jls 5.1.3 Narrowing Primitive Conversion
    /// @since 1.1
    public byte byteValue() {
        return (byte)value;
    }

    /// Returns the value of this `Double` as a `short`
    /// after a narrowing primitive conversion.
    ///
    /// @return  the `double` value represented by this object
    ///          converted to type `short`
    /// @jls 5.1.3 Narrowing Primitive Conversion
    /// @since 1.1
    public short shortValue() {
        return (short)value;
    }

    /// Returns the value of this `Double` as an `int`
    /// after a narrowing primitive conversion.
    /// @jls 5.1.3 Narrowing Primitive Conversion
    ///
    /// @return  the `double` value represented by this object
    ///          converted to type `int`
    public int intValue() {
        return (int)value;
    }

    /// Returns the value of this `Double` as a `long`
    /// after a narrowing primitive conversion.
    ///
    /// @return  the `double` value represented by this object
    ///          converted to type `long`
    /// @jls 5.1.3 Narrowing Primitive Conversion
    public long longValue() {
        return (long)value;
    }

    /// Returns the value of this `Double` as a `float`
    /// after a narrowing primitive conversion.
    ///
    /// @apiNote
    /// This method corresponds to the convertFormat operation defined
    /// in IEEE 754.
    ///
    /// @return  the `double` value represented by this object
    ///          converted to type `float`
    /// @jls 5.1.3 Narrowing Primitive Conversion
    /// @since 1.0
    public float floatValue() {
        return (float)value;
    }

    /// Returns the `double` value of this `Double` object.
    ///
    /// @return the `double` value represented by this object
    @IntrinsicCandidate
    public double doubleValue() {
        return value;
    }

    /// Returns a hash code for this `Double` object. The
    /// result is the exclusive OR of the two halves of the
    /// `long` integer bit representation, exactly as
    /// produced by the method [#doubleToLongBits(double)], of
    /// the primitive `double` value represented by this
    /// `Double` object. That is, the hash code is the value
    /// of the expression:
    ///
    /// <blockquote>
    ///  {@code (int)(v^(v>>>32))}
    /// </blockquote>
    ///
    /// where `v` is defined by:
    ///
    /// <blockquote>
    ///  {@code long v = Double.doubleToLongBits(this.doubleValue());}
    /// </blockquote>
    ///
    /// @return  a `hash code` value for this object.
    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    /// Returns a hash code for a `double` value; compatible with
    /// `Double.hashCode()`.
    ///
    /// @param value the value to hash
    /// @return a hash code value for a `double` value.
    /// @since 1.8
    public static int hashCode(double value) {
        return Long.hashCode(doubleToLongBits(value));
    }

    /// Compares this object against the specified object.  The result
    /// is `true` if and only if the argument is not
    /// `null` and is a `Double` object that
    /// represents a `double` that has the same value as the
    /// `double` represented by this object. For this
    /// purpose, two `double` values are considered to be
    /// the same if and only if the method
    /// [#doubleToLongBits(double)] returns the identical
    /// `long` value when applied to each.
    ///
    /// @apiNote
    /// This method is defined in terms of
    /// [#doubleToLongBits(double)] rather than the `==` operator
    /// on `double` values since the `==` operator does
    /// _not_ define an equivalence relation and to satisfy the
    /// [equals contract][Object#equals] an equivalence
    /// relation must be implemented; see <a
    /// href="#equivalenceRelation">this discussion</a> for details of
    /// floating-point equality and equivalence.
    ///
    /// @see java.lang.Double#doubleToLongBits(double)
    /// @jls 15.21.1 Numerical Equality Operators == and !=
    public boolean equals(Object obj) {
        return (obj instanceof Double)
               && (doubleToLongBits(((Double)obj).value) ==
                      doubleToLongBits(value));
    }

    /// Returns a representation of the specified floating-point value
    /// according to the IEEE 754 floating-point "double
    /// format" bit layout.
    ///
    /// Bit 63 (the bit that is selected by the mask
    /// `0x8000000000000000L`) represents the sign of the
    /// floating-point number. Bits
    /// 62-52 (the bits that are selected by the mask
    /// `0x7ff0000000000000L`) represent the exponent. Bits 51-0
    /// (the bits that are selected by the mask
    /// `0x000fffffffffffffL`) represent the significand
    /// (sometimes called the mantissa) of the floating-point number.
    ///
    /// If the argument is positive infinity, the result is
    /// `0x7ff0000000000000L`.
    ///
    /// If the argument is negative infinity, the result is
    /// `0xfff0000000000000L`.
    ///
    /// If the argument is NaN, the result is
    /// `0x7ff8000000000000L`.
    ///
    /// In all cases, the result is a `long` integer that, when
    /// given to the [#longBitsToDouble(long)] method, will produce a
    /// floating-point value the same as the argument to
    /// `doubleToLongBits` (except all NaN values are
    /// collapsed to a single "canonical" NaN value).
    ///
    /// @param   value   a `double` precision floating-point number.
    /// @return the bits that represent the floating-point number.
    @IntrinsicCandidate
    public static long doubleToLongBits(double value) {
        if (!isNaN(value)) {
            return doubleToRawLongBits(value);
        }
        return 0x7ff8000000000000L;
    }

    /// Returns a representation of the specified floating-point value
    /// according to the IEEE 754 floating-point "double
    /// format" bit layout, preserving Not-a-Number (NaN) values.
    ///
    /// Bit 63 (the bit that is selected by the mask
    /// `0x8000000000000000L`) represents the sign of the
    /// floating-point number. Bits
    /// 62-52 (the bits that are selected by the mask
    /// `0x7ff0000000000000L`) represent the exponent. Bits 51-0
    /// (the bits that are selected by the mask
    /// `0x000fffffffffffffL`) represent the significand
    /// (sometimes called the mantissa) of the floating-point number.
    ///
    /// If the argument is positive infinity, the result is
    /// `0x7ff0000000000000L`.
    ///
    /// If the argument is negative infinity, the result is
    /// `0xfff0000000000000L`.
    ///
    /// If the argument is NaN, the result is the `long`
    /// integer representing the actual NaN value.  Unlike the
    /// `doubleToLongBits` method,
    /// `doubleToRawLongBits` does not collapse all the bit
    /// patterns encoding a NaN to a single "canonical" NaN
    /// value.
    ///
    /// In all cases, the result is a `long` integer that,
    /// when given to the [#longBitsToDouble(long)] method, will
    /// produce a floating-point value the same as the argument to
    /// `doubleToRawLongBits`.
    ///
    /// @param   value   a `double` precision floating-point number.
    /// @return the bits that represent the floating-point number.
    /// @since 1.3
    @IntrinsicCandidate
    public static native long doubleToRawLongBits(double value);

    /// Returns the `double` value corresponding to a given
    /// bit representation.
    /// The argument is considered to be a representation of a
    /// floating-point value according to the IEEE 754 floating-point
    /// "double format" bit layout.
    ///
    /// If the argument is `0x7ff0000000000000L`, the result
    /// is positive infinity.
    ///
    /// If the argument is `0xfff0000000000000L`, the result
    /// is negative infinity.
    ///
    /// If the argument is any value in the range
    /// `0x7ff0000000000001L` through
    /// `0x7fffffffffffffffL` or in the range
    /// `0xfff0000000000001L` through
    /// `0xffffffffffffffffL`, the result is a NaN.  No IEEE
    /// 754 floating-point operation provided by Java can distinguish
    /// between two NaN values of the same type with different bit
    /// patterns.  Distinct values of NaN are only distinguishable by
    /// use of the `Double.doubleToRawLongBits` method.
    ///
    /// In all other cases, let <i>s</i>, <i>e</i>, and <i>m</i> be three
    /// values that can be computed from the argument:
    ///
    /// {@snippet lang="java" :
    /// int s = ((bits >> 63) == 0) ? 1 : -1;
    /// int e = (int)((bits >> 52) & 0x7ffL);
    /// long m = (e == 0) ?
    ///                 (bits & 0xfffffffffffffL) << 1 :
    ///                 (bits & 0xfffffffffffffL) | 0x10000000000000L;
    /// }
    ///
    /// Then the floating-point result equals the value of the mathematical
    /// expression <i>s</i>&middot;<i>m</i>&middot;2<sup><i>e</i>-1075</sup>.
    ///
    /// Note that this method may not be able to return a
    /// `double` NaN with exactly same bit pattern as the
    /// `long` argument.  IEEE 754 distinguishes between two
    /// kinds of NaNs, quiet NaNs and _signaling NaNs_.  The
    /// differences between the two kinds of NaN are generally not
    /// visible in Java.  Arithmetic operations on signaling NaNs turn
    /// them into quiet NaNs with a different, but often similar, bit
    /// pattern.  However, on some processors merely copying a
    /// signaling NaN also performs that conversion.  In particular,
    /// copying a signaling NaN to return it to the calling method
    /// may perform this conversion.  So `longBitsToDouble`
    /// may not be able to return a `double` with a
    /// signaling NaN bit pattern.  Consequently, for some
    /// `long` values,
    /// `doubleToRawLongBits(longBitsToDouble(start))` may
    /// _not_ equal `start`.  Moreover, which
    /// particular bit patterns represent signaling NaNs is platform
    /// dependent; although all NaN bit patterns, quiet or signaling,
    /// must be in the NaN range identified above.
    ///
    /// @param   bits   any `long` integer.
    /// @return  the `double` floating-point value with the same
    ///          bit pattern.
    @IntrinsicCandidate
    public static native double longBitsToDouble(long bits);

    /// Compares two `Double` objects numerically.
    ///
    /// This method imposes a total order on `Double` objects
    /// with two differences compared to the incomplete order defined by
    /// the Java language numerical comparison operators (`<, <=,
    /// ==, >=, >`) on `double` values.
    ///
    ///   -  A NaN is _unordered_ with respect to other
    ///      values and unequal to itself under the comparison
    ///      operators.  This method chooses to define
    ///      `Double.NaN` to be equal to itself and greater than all
    ///      other `double` values (including
    ///      `Double.POSITIVE_INFINITY`).
    ///
    ///   - Positive zero and negative zero compare equal
    ///     numerically, but are distinct and distinguishable values.
    ///     This method chooses to define positive zero (`+0.0d`),
    ///     to be greater than negative zero (`-0.0d`).
    ///
    /// This ensures that the _natural ordering_ of `Double`
    /// objects imposed by this method is _consistent with
    /// equals_; see [this discussion][##equivalenceRelation]
    /// for details of floating-point comparison and
    /// ordering.
    ///
    /// @param   anotherDouble   the `Double` to be compared.
    /// @return  the value `0` if `anotherDouble` is
    ///          numerically equal to this `Double`; a value
    ///          less than `0` if this `Double`
    ///          is numerically less than `anotherDouble`;
    ///          and a value greater than `0` if this
    ///          `Double` is numerically greater than
    ///          `anotherDouble`.
    ///
    /// @jls 15.20.1 Numerical Comparison Operators {@code <}, {@code <=}, {@code >}, and {@code >=}
    /// @since   1.2
    public int compareTo(Double anotherDouble) {
        return Double.compare(value, anotherDouble.value);
    }

    /// Compares the two specified `double` values. The sign
    /// of the integer value returned is the same as that of the
    /// integer that would be returned by the call:
    /// ```java
    ///    Double.valueOf(d1).compareTo(Double.valueOf(d2))
    /// ```
    ///
    /// @param   d1        the first `double` to compare
    /// @param   d2        the second `double` to compare
    /// @return  the value `0` if `d1` is
    ///          numerically equal to `d2`; a value less than
    ///          `0` if `d1` is numerically less than
    ///          `d2`; and a value greater than `0`
    ///          if `d1` is numerically greater than
    ///          `d2`.
    /// @since 1.4
    public static int compare(double d1, double d2) {
        if (d1 < d2)
            return -1;           // Neither val is NaN, thisVal is smaller
        if (d1 > d2)
            return 1;            // Neither val is NaN, thisVal is larger

        // Cannot use doubleToRawLongBits because of possibility of NaNs.
        long thisBits    = Double.doubleToLongBits(d1);
        long anotherBits = Double.doubleToLongBits(d2);

        return (thisBits == anotherBits ?  0 : // Values are equal
                (thisBits < anotherBits ? -1 : // (-0.0, 0.0) or (!NaN, NaN)
                 1));                          // (0.0, -0.0) or (NaN, !NaN)
    }

    /// Adds two `double` values together as per the + operator.
    ///
    /// @apiNote This method corresponds to the addition operation
    /// defined in IEEE 754.
    ///
    /// @param a the first operand
    /// @param b the second operand
    /// @return the sum of `a` and `b`
    /// @jls 4.2.4 Floating-Point Operations
    /// @see java.util.function.BinaryOperator
    /// @since 1.8
    public static double sum(double a, double b) {
        return a + b;
    }

    /// Returns the greater of two `double` values
    /// as if by calling [`Math.max`][Math#max(double, double)].
    ///
    /// @apiNote
    /// This method corresponds to the maximum operation defined in
    /// IEEE 754.
    ///
    /// @param a the first operand
    /// @param b the second operand
    /// @return the greater of `a` and `b`
    /// @see java.util.function.BinaryOperator
    /// @since 1.8
    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    /// Returns the smaller of two `double` values
    /// as if by calling [`Math.min`][Math#min(double, double)].
    ///
    /// @apiNote
    /// This method corresponds to the minimum operation defined in
    /// IEEE 754.
    ///
    /// @param a the first operand
    /// @param b the second operand
    /// @return the smaller of `a` and `b`.
    /// @see java.util.function.BinaryOperator
    /// @since 1.8
    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    /// Returns an [Optional] containing the nominal descriptor for this
    /// instance, which is the instance itself.
    ///
    /// @return an [Optional] describing the [Double] instance
    /// @since 12
    @Override
    public Optional<Double> describeConstable() {
        return Optional.of(this);
    }

    /// Resolves this instance as a [ConstantDesc], the result of which is
    /// the instance itself.
    ///
    /// @param lookup ignored
    /// @return the [Double] instance
    /// @since 12
    @Override
    public Double resolveConstantDesc(MethodHandles.Lookup lookup) {
        return this;
    }

    /** use serialVersionUID from JDK 1.0.2 for interoperability */
    @java.io.Serial
    private static final long serialVersionUID = -9172774392245257468L;
}
