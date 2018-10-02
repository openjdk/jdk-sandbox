/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.micro.jdk.java.util.stream;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BinaryOperator;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * JMH Benchmark created to mimic a JSR-166 benchmark
 */
@State(Scope.Benchmark)
public class IntegerSum {

    @Param({"1", "10", "100", "1000", "10000", "100000", "1000000"})
    private int size;

    private ArrayList<Integer> arrayList;
    private HashMap<Integer, Integer> hashMap;
    private ConcurrentHashMap<Integer, Integer> concurrentHashMap;

    private static final BinaryOperator<Integer> sum = (x, y) -> x + y;

    @Setup
    public final void setup() {
        Random srand = new Random(9820239874L);
        Set<Integer> set = new HashSet<>(size);
        while (set.size() < size) {
            set.add(srand.nextInt());
        }
        Integer[] values = set.toArray(new Integer[size]);
        Integer[] keys = Arrays.copyOf(values, size);

        for (int i = 0; i < size; i++) {
            swap(values, i, srand.nextInt(values.length));
            swap(keys, i, srand.nextInt(keys.length));
        }

        arrayList = new ArrayList<>(Arrays.asList(keys));
        hashMap = new HashMap<>(size);
        concurrentHashMap = new ConcurrentHashMap<>(size);
        for (int i = 0; i < size; i++) {
            hashMap.put(keys[i], values[i]);
            concurrentHashMap.put(keys[i], values[i]);
        }

        System.gc();
    }

    private void swap(Integer[] array, int first, int second) {
        Integer temp = array[first];
        array[first] = array[second];
        array[second] = temp;
    }

    @Benchmark
    public Integer ArrayListStream() {
        return arrayList.stream().reduce(0, sum);
    }

    @Benchmark
    public Integer HashMapStream() {
        return hashMap.keySet().stream().reduce(0, sum);
    }

    @Benchmark
    public Integer ConcurrentHashMapStream() {
        return concurrentHashMap.keySet().stream().reduce(0, sum);
    }

    @Benchmark
    public Integer ArrayListParallelStream() {
        return arrayList.parallelStream().reduce(0, sum);
    }

    @Benchmark
    public Integer HashMapParallelStream() {
        return hashMap.keySet().parallelStream().reduce(0, sum);
    }

    @Benchmark
    public Integer ConcurrentHashMapParallelStream() {
        return concurrentHashMap.keySet().parallelStream().reduce(0, sum);
    }
}
