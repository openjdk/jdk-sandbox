/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

/*
 * @test
 * @summary flatPush(BiConsumer) + primitive stream operations
 */

package org.openjdk.tests.java.util.stream;

import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.DefaultMethodStreams;
import java.util.stream.DoubleStream;
import java.util.stream.DoubleStreamTestDataProvider;
import java.util.stream.IntStream;
import java.util.stream.IntStreamTestDataProvider;
import java.util.stream.LongStream;
import java.util.stream.LongStreamTestDataProvider;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.StreamTestDataProvider;
import java.util.stream.TestData;

import static java.util.stream.DefaultMethodStreams.delegateTo;
import static java.util.stream.LambdaTestHelpers.LONG_STRING;
import static java.util.stream.LambdaTestHelpers.assertConcat;
import static java.util.stream.LambdaTestHelpers.assertContents;
import static java.util.stream.LambdaTestHelpers.assertCountSum;
import static java.util.stream.LambdaTestHelpers.countTo;
import static java.util.stream.LambdaTestHelpers.flattenChars;
import static java.util.stream.LambdaTestHelpers.mfId;
import static java.util.stream.LambdaTestHelpers.mfLt;
import static java.util.stream.LambdaTestHelpers.mfNull;
import static java.util.stream.ThrowableHelper.checkNPE;

@Test
public class FlatPushOpTest extends OpTestCase {

    BiConsumer<Consumer<Integer>, Integer> nullConsumer =
            (sink, e) -> mfNull.apply(e).forEach(sink::accept);
    BiConsumer<Consumer<Integer>, Integer> idConsumer =
            (sink, e) -> mfId.apply(e).forEach(sink::accept);
    BiConsumer<Consumer<Integer>, Integer> listConsumer =
            (sink, e) -> mfLt.apply(e).forEach(sink::accept);
    BiConsumer<Consumer<Character>, String> charConsumer =
            (sink, e) -> flattenChars.apply(e).forEach(sink::accept);
    BiConsumer<Consumer<Integer>, Integer> emptyStreamConsumer =
            (sink, e) -> Stream.empty().forEach(i->sink.accept((Integer)i));

    BiConsumer<Consumer<Integer>, Integer> intRangeConsumer =
            (sink, e) -> IntStream.range(0, e).boxed().forEach(sink::accept);
    BiConsumer<Consumer<Integer>, Integer> rangeConsumer100 =
            (sink, e) -> IntStream.range(0, 100).boxed().forEach(sink::accept);
    BiConsumer<Consumer<Integer>, Integer> rangeConsumerWithLimit =
            (sink, e) -> IntStream.range(0, e).boxed().limit(10)
                    .forEach(sink::accept);

    @DataProvider(name="Stream<Integer>")
    public Object[][] streamTest() {
        return new Object[][] {
                {Stream.of(0, 1, 2)},
                {DefaultMethodStreams.delegateTo(Stream.of(0, 1, 2))}
        };
    }

    @Test(dataProvider = "Stream<Integer>")
    public void testNullMapper(Stream<Integer> s) {
        checkNPE(() -> s.flatPush(null));
        checkNPE(() -> IntStream.of(1).flatPush(null));
        checkNPE(() -> DoubleStream.of(1).flatPush(null));
        checkNPE(() -> LongStream.of(1).flatPush(null));
    }


    @Test(dataProvider = "Stream<Integer>")
    public void testOpsShortCircuit(Stream<Integer> s) {
        AtomicInteger count = new AtomicInteger();
        s.flatPush(rangeConsumer100)
                .peek(i -> count.incrementAndGet())
                .limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    @Test
    public void testFlatPush() {
        String[] stringsArray = {"hello", "there", "", "yada"};
        Stream<String> strings = Arrays.asList(stringsArray).stream();

        assertConcat(strings.flatPush(charConsumer)
                .iterator(), "hellothereyada");
        assertCountSum((countTo(10).stream().flatPush(idConsumer)),
                10, 55);
        assertCountSum(countTo(10).stream().flatPush(nullConsumer),
                0, 0);
        assertCountSum(countTo(3).stream().flatPush(listConsumer),
                6, 4);

        exerciseOps(TestData.Factory.ofArray("stringsArray",
                stringsArray), s -> s.flatPush(charConsumer));
        exerciseOps(TestData.Factory.ofArray("LONG_STRING",
                new String[]{LONG_STRING}), s -> s.flatPush(charConsumer));
    }

    @Test
    public void testDefaultFlatPush() {
        String[] stringsArray = {"hello", "there", "", "yada"};
        Stream<String> strings = Arrays.asList(stringsArray).stream();

        assertConcat(delegateTo(strings)
                .flatPush(charConsumer).iterator(), "hellothereyada");
        assertCountSum(delegateTo(countTo(10).stream())
                .flatPush(idConsumer), 10, 55);
        assertCountSum(delegateTo(countTo(10).stream())
                .flatPush(nullConsumer), 0, 0);
        assertCountSum(delegateTo(countTo(3).stream())
                .flatPush(listConsumer), 6, 4);

        exerciseOps(TestData.Factory.ofArray("stringsArray",
                stringsArray), s -> delegateTo(s).flatPush(charConsumer));
        exerciseOps(TestData.Factory.ofArray("LONG_STRING",
                new String[]{LONG_STRING}), s -> delegateTo(s).flatPush(charConsumer));
    }

    @Test(dataProvider = "StreamTestData<Integer>",
            dataProviderClass = StreamTestDataProvider.class)
    public void testOps(String name, TestData.OfRef<Integer> data) {
        Collection<Integer> result;
        result = exerciseOps(data, s -> s.flatPush(idConsumer));
        assertEquals(data.size(), result.size());

        result = exerciseOps(data, s -> s.flatPush(nullConsumer));
        assertEquals(0, result.size());

        result = exerciseOps(data, s -> s.flatPush(emptyStreamConsumer));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "StreamTestData<Integer>",
            dataProviderClass = StreamTestDataProvider.class)
    public void testDefaultOps(String name, TestData.OfRef<Integer> data) {
        Collection<Integer> result;
        result = exerciseOps(data, s -> delegateTo(s).flatPush(idConsumer));
        assertEquals(data.size(), result.size());

        result = exerciseOps(data, s -> delegateTo(s).flatPush(nullConsumer));
        assertEquals(0, result.size());

        result = exerciseOps(data, s -> delegateTo(s).flatPush(emptyStreamConsumer));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "StreamTestData<Integer>.small",
            dataProviderClass = StreamTestDataProvider.class)
    public void testOpsX(String name, TestData.OfRef<Integer> data) {
        exerciseOps(data, s -> s.flatPush(listConsumer));
        exerciseOps(data, s -> s.flatPush(intRangeConsumer));
        exerciseOps(data, s -> s.flatPush(rangeConsumerWithLimit));
    }

    @Test(dataProvider = "StreamTestData<Integer>.small",
            dataProviderClass = StreamTestDataProvider.class)
    public void testDefaultOpsX(String name, TestData.OfRef<Integer> data) {
        exerciseOps(data, s -> delegateTo(s).flatPush(listConsumer));
        exerciseOps(data, s -> delegateTo(s).flatPush(intRangeConsumer));
        exerciseOps(data, s -> delegateTo(s).flatPush(rangeConsumerWithLimit));
    }

    @Test(dataProvider = "Stream<Integer>")
    public void testConsumerContained(Stream<Integer> s) {
        Consumer<Integer>[] capture = new Consumer[1];
        BiConsumer<Consumer<Integer>, Integer> mapper = (c, i) -> {
            c.accept(i);
            capture[0] = c;
        };
        expectThrows(NullPointerException.class,
                () -> s.flatPush(mapper)
                        .peek(e -> capture[0].accept(666))
                        .collect(Collectors.toList()));
    }

    // Int

    @Test(dataProvider = "IntStreamTestData", dataProviderClass = IntStreamTestDataProvider.class)
    public void testIntOps(String name, TestData.OfInt data) {
        Collection<Integer> result = exerciseOps(data, s -> s.flatPush((sink, i) -> IntStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.boxed().flatPushToInt((sink, i) -> IntStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.flatPush((sink,i) -> IntStream.empty().forEach(sink::accept)));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "IntStreamTestData", dataProviderClass = IntStreamTestDataProvider.class)
    public void testDefaultIntOps(String name, TestData.OfInt data) {
        Collection<Integer> result = exerciseOps(data, s -> delegateTo(s).flatPush((sink, i) -> IntStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> delegateTo(s).boxed().flatPushToInt((sink, i) -> IntStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> delegateTo(s).flatPush((sink,i) -> IntStream.empty().forEach(sink::accept)));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "IntStreamTestData.small", dataProviderClass = IntStreamTestDataProvider.class)
    public void testIntOpsX(String name, TestData.OfInt data) {

        exerciseOps(data, s -> s.flatPush((sink,e) -> IntStream.range(0, e).forEach(sink::accept)));
        exerciseOps(data, s -> s.flatPush((sink,e) -> IntStream.range(0, e).limit(10).forEach(sink::accept)));

        exerciseOps(data, s -> s.boxed().flatPushToInt((sink,e) -> IntStream.range(0, e).forEach(sink::accept)));
        exerciseOps(data, s -> s.boxed().flatPushToInt((sink,e) -> IntStream.range(0, e).limit(10).forEach(sink::accept)));
    }

    @Test(dataProvider = "IntStreamTestData.small", dataProviderClass = IntStreamTestDataProvider.class)
    public void testDefaultIntOpsX(String name, TestData.OfInt data) {

        exerciseOps(data, s -> delegateTo(s).flatPush((sink,e) -> IntStream.range(0, e).forEach(sink::accept)));
        exerciseOps(data, s -> delegateTo(s).flatPush((sink,e) -> IntStream.range(0, e).limit(10).forEach(sink::accept)));

        exerciseOps(data, s -> s.boxed().flatPushToInt((sink,e) -> IntStream.range(0, e).forEach(sink::accept)));
        exerciseOps(data, s -> s.boxed().flatPushToInt((sink,e) -> IntStream.range(0, e).limit(10).forEach(sink::accept)));
    }

    @Test
    public void testIntOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        IntStream.of(0).flatPush((sink, e) -> IntStream.range(0, 100).forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        Stream.of(0).flatPushToInt((sink, e) -> IntStream.range(0, 100).forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    @Test
    public void testDefaultIntOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        delegateTo(IntStream.of(0)).flatPush((sink, e) -> IntStream.range(0, 100).forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        delegateTo(Stream.of(0)).flatPushToInt((sink, e) -> IntStream.range(0, 100).forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    // Double

    @Test(dataProvider = "DoubleStreamTestData", dataProviderClass = DoubleStreamTestDataProvider.class)
    public void testDoubleOps(String name, TestData.OfDouble data) {
        Collection<Double> result = exerciseOps(data, s -> s.flatPush((sink, i) -> DoubleStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.boxed().flatPushToDouble((sink, i) -> DoubleStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.flatPush((sink, i) -> DoubleStream.empty().forEach(sink::accept)));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "DoubleStreamTestData", dataProviderClass = DoubleStreamTestDataProvider.class)
    public void testDefaultDoubleOps(String name, TestData.OfDouble data) {
        Collection<Double> result = exerciseOps(data, s -> delegateTo(s).flatPush((sink, i) -> DoubleStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> delegateTo(s).boxed().flatPushToDouble((sink, i) -> DoubleStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> delegateTo(s).flatPush((sink, i) -> DoubleStream.empty().forEach(sink::accept)));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "DoubleStreamTestData.small", dataProviderClass = DoubleStreamTestDataProvider.class)
    public void testDoubleOpsX(String name, TestData.OfDouble data) {
        exerciseOps(data, s -> s.flatPush((sink, e) -> IntStream.range(0, (int) e).asDoubleStream().forEach(sink::accept)));
        exerciseOps(data, s -> s.flatPush((sink, e) -> IntStream.range(0, (int) e).limit(10).asDoubleStream().forEach(sink::accept)));
    }

    @Test(dataProvider = "DoubleStreamTestData.small", dataProviderClass = DoubleStreamTestDataProvider.class)
    public void testDefaultDoubleOpsX(String name, TestData.OfDouble data) {
        exerciseOps(data, s -> delegateTo(s).flatPush((sink, e) -> IntStream.range(0, (int) e).asDoubleStream().forEach(sink::accept)));
        exerciseOps(data, s -> delegateTo(s).flatPush((sink, e) -> IntStream.range(0, (int) e).limit(10).asDoubleStream().forEach(sink::accept)));
    }

    @Test
    public void testDoubleOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        DoubleStream.of(0).flatPush((sink, i) -> IntStream.range(0, 100).asDoubleStream().forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        Stream.of(0).flatPushToDouble((sink, i) -> IntStream.range(0, 100).asDoubleStream().forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    @Test
    public void testDefaultDoubleOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        delegateTo(DoubleStream.of(0)).flatPush((sink, i) -> IntStream.range(0, 100).asDoubleStream().forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        delegateTo(Stream.of(0)).flatPushToDouble((sink, i) -> IntStream.range(0, 100).asDoubleStream().forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    // Long

    @Test(dataProvider = "LongStreamTestData", dataProviderClass = LongStreamTestDataProvider.class)
    public void testLongOps(String name, TestData.OfLong data) {
        Collection<Long> result = exerciseOps(data, s -> s.flatPush((sink, i) -> LongStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.boxed().flatPushToLong((sink, i) -> LongStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> s.flatPush((sink, i) -> LongStream.empty().forEach(sink::accept)));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "LongStreamTestData", dataProviderClass = LongStreamTestDataProvider.class)
    public void testDefaultLongOps(String name, TestData.OfLong data) {
        Collection<Long> result = exerciseOps(data, s -> delegateTo(s).flatPush((sink, i) -> LongStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> delegateTo(s).boxed().flatPushToLong((sink, i) -> LongStream.of(i).forEach(sink::accept)));
        assertEquals(data.size(), result.size());
        assertContents(data, result);

        result = exerciseOps(data, s -> delegateTo(s).flatPush((sink, i) -> LongStream.empty().forEach(sink::accept)));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "LongStreamTestData.small", dataProviderClass = LongStreamTestDataProvider.class)
    public void testLongOpsX(String name, TestData.OfLong data) {
        exerciseOps(data, s -> s.flatPush((sink, e) -> LongStream.range(0, e).forEach(sink::accept)));
        exerciseOps(data, s -> s.flatPush((sink, e) -> LongStream.range(0, e).limit(10).forEach(sink::accept)));
    }

    @Test(dataProvider = "LongStreamTestData.small", dataProviderClass = LongStreamTestDataProvider.class)
    public void testDefaultLongOpsX(String name, TestData.OfLong data) {
        exerciseOps(data, s -> delegateTo(s).flatPush((sink, e) -> LongStream.range(0, e).forEach(sink::accept)));
        exerciseOps(data, s -> delegateTo(s).flatPush((sink, e) -> LongStream.range(0, e).limit(10).forEach(sink::accept)));
    }

    @Test
    public void testLongOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        LongStream.of(0).flatPush((sink, i) -> LongStream.range(0, 100).forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        Stream.of(0).flatPushToLong((sink, i) -> LongStream.range(0, 100).forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    @Test
    public void testDefaultLongOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        delegateTo(LongStream.of(0)).flatPush((sink, i) -> LongStream.range(0, 100).forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);

        count.set(0);
        delegateTo(Stream.of(0)).flatPushToLong((sink, i) -> LongStream.range(0, 100).forEach(sink::accept)).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }
}
