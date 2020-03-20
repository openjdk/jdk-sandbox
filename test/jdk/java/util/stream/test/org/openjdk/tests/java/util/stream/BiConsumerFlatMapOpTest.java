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
 * @summary FlatMap(BiConsumer) operations
 */

package org.openjdk.tests.java.util.stream;

import org.testng.annotations.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;
import java.util.stream.StreamTestDataProvider;
import java.util.stream.TestData;

import static java.util.stream.LambdaTestHelpers.LONG_STRING;
import static java.util.stream.LambdaTestHelpers.assertConcat;
import static java.util.stream.LambdaTestHelpers.assertCountSum;
import static java.util.stream.LambdaTestHelpers.countTo;
import static java.util.stream.LambdaTestHelpers.flattenChars;
import static java.util.stream.LambdaTestHelpers.mfId;
import static java.util.stream.LambdaTestHelpers.mfLt;
import static java.util.stream.LambdaTestHelpers.mfNull;
import static java.util.stream.ThrowableHelper.checkNPE;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.expectThrows;

@Test
public class BiConsumerFlatMapOpTest extends OpTestCase {

    BiConsumer<Integer, Consumer<Integer>> nullConsumer =
            (e, sink) -> mfNull.apply(e).forEach(sink::accept);
    BiConsumer<Integer, Consumer<Integer>> idConsumer =
            (e, sink) -> mfId.apply(e).forEach(sink::accept);
    BiConsumer<Integer, Consumer<Integer>> listConsumer =
            (e, sink) -> mfLt.apply(e).forEach(sink::accept);
    BiConsumer<String, Consumer<Character>> charConsumer =
            (e, sink) -> flattenChars.apply(e).forEach(sink::accept);
    BiConsumer<Integer, Consumer<Integer>> emptyStreamConsumer =
            (e, sink) -> Stream.empty().forEach(i->sink.accept((Integer)i));

    BiConsumer<Integer, Consumer<Integer>> intRangeConsumer =
            (e, sink) -> IntStream.range(0, e).boxed().forEach(sink::accept);
    BiConsumer<Integer, Consumer<Integer>> rangeConsumer100 =
            (e, sink) -> IntStream.range(0, 100).boxed().forEach(sink::accept);
    BiConsumer<Integer, Consumer<Integer>> rangeConsumerWithLimit =
            (e, sink) -> IntStream.range(0, e).boxed().limit(10)
                    .forEach(sink::accept);
    @Test
    public void testNullMapper() {
        checkNPE(() -> Stream.of(1)
                .flatMap((BiConsumer<Object, Consumer<Object>>) null));
    }

    @Test
    public void testFlatMap() {
        String[] stringsArray = {"hello", "there", "", "yada"};
        Stream<String> strings = Arrays.asList(stringsArray).stream();

        assertConcat(strings.flatMap(charConsumer)
                        .iterator(), "hellothereyada");
        assertCountSum((countTo(10).stream().flatMap(idConsumer)),
                10, 55);
        assertCountSum(countTo(10).stream().flatMap(nullConsumer),
                0, 0);
        assertCountSum(countTo(3).stream().flatMap(listConsumer),
                6, 4);

        exerciseOps(TestData.Factory.ofArray("stringsArray",
                stringsArray), s -> s.flatMap(charConsumer));
        exerciseOps(TestData.Factory.ofArray("LONG_STRING",
                new String[]{LONG_STRING}), s -> s.flatMap(charConsumer));
    }

    @Test(dataProvider = "StreamTestData<Integer>",
            dataProviderClass = StreamTestDataProvider.class)
    public void testOps(String name, TestData.OfRef<Integer> data) {
        Collection<Integer> result;
        result = exerciseOps(data, s -> s.flatMap(idConsumer));
        assertEquals(data.size(), result.size());

        result = exerciseOps(data, s -> s.flatMap(nullConsumer));
        assertEquals(0, result.size());

        result = exerciseOps(data, s -> s.flatMap(emptyStreamConsumer));
        assertEquals(0, result.size());
    }

    @Test(dataProvider = "StreamTestData<Integer>.small",
            dataProviderClass = StreamTestDataProvider.class)
    public void testOpsX(String name, TestData.OfRef<Integer> data) {
        exerciseOps(data, s -> s.flatMap(listConsumer));
        exerciseOps(data, s -> s.flatMap(intRangeConsumer));
        exerciseOps(data, s -> s.flatMap(rangeConsumerWithLimit));
    }

    @Test
    public void testOpsShortCircuit() {
        AtomicInteger count = new AtomicInteger();
        Stream.of(0).flatMap(rangeConsumer100).
                peek(i -> count.incrementAndGet()).
                limit(10).toArray();
        assertEquals(count.get(), 10);
    }

    @Test
    public void testConsumerContained() {
        var s = List.of(0, 1, 2).stream();
        Consumer<Integer>[] capture = new Consumer[1];
        BiConsumer<Integer, Consumer<Integer>> mapper = (i, c) -> {
            c.accept(i);
            capture[0] = c;
        };
        expectThrows(NullPointerException.class,
                () -> s.flatMap(mapper)
                        .peek(e -> capture[0].accept(666))
                        .collect(Collectors.toList()));
    }
}