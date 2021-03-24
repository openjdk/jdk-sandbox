/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text;

import java.util.List;
import java.util.RandomAccess;
import java.util.function.BiFunction;
import java.util.function.Function;

/*
 * Combined and adapted from binary search in Arrays and Collections for extra flexibility.
 *
 *   1. fromIndex <= i < toIndex
 *   2. list is RandomAccess
 */
class SortedLists {

    private SortedLists() { }

    @FunctionalInterface
    interface ComparatorInt<T> {
        int compare(T t, int k);
    }

    // Although ToIntBiFunction<T, K> could've been used instead of this interface,
    // there's no ToIntTriFunction<T, K1, K2> to be used instead of Comparator2<T, K1, K2>.
    //
    // For symmetry's sake an interface Comparator1<T, K> is introduced.
    @FunctionalInterface
    interface Comparator1<T, K> {
        int compare(T t, K k);
    }

    @FunctionalInterface
    interface Comparator2<T, K1, K2> {
        int compare(T t, K1 k1, K2 k2);
    }

    static <T, K> int binarySearch(List<T> l, int fromIndex, int toIndex, int k, ComparatorInt<? super T> c) {

        assert l instanceof RandomAccess;

        int low = fromIndex;
        int high = toIndex - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = l.get(mid);
            int cmp = c.compare(midVal, k);

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found
    }

    static <T, K> T computeIfAbsent(List<T> l,
                                    K k,
                                    Comparator1<? super T, ? super K> c,
                                    Function<? super K, ? extends T> computingFunction) {
        int i = binarySearch(l, k, c);
        if (i >= 0) {
            return l.get(i);
        }
        T t = computingFunction.apply(k);
        l.add(-i - 1, t);
        return t;
    }

    static <T, K1, K2> T computeIfAbsent(List<T> l,
                                         K1 k1,
                                         K2 k2,
                                         Comparator2<? super T, ? super K1, ? super K2> c,
                                         BiFunction<? super K1, ? super K2, ? extends T> computingFunction) {
        int i = binarySearch(l, k1, k2, c);
        if (i >= 0) {
            return l.get(i);
        }
        T t = computingFunction.apply(k1, k2);
        l.add(-i - 1, t);
        return t;
    }


    static <T, K> int binarySearch(List<T> l, K k, Comparator1<? super T, ? super K> c) {

        assert l instanceof RandomAccess;

        int low = 0;
        int high = l.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = l.get(mid);
            int cmp = c.compare(midVal, k);

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found
    }

    static <T, K1, K2> int binarySearch(List<T> l, K1 k1, K2 k2, Comparator2<? super T, ? super K1, ? super K2> c) {

        assert l instanceof RandomAccess;

        int low = 0;
        int high = l.size() - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            T midVal = l.get(mid);
            int cmp = c.compare(midVal, k1, k2);

            if (cmp < 0)
                low = mid + 1;
            else if (cmp > 0)
                high = mid - 1;
            else
                return mid; // key found
        }
        return -(low + 1);  // key not found
    }
}
