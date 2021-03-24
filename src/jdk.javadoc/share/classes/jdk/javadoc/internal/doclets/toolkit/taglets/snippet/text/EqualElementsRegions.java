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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.SortedLists.binarySearch;

/*
 * This sequence optimizes memory usage for the case where there are many more
 * elements than there are runs of equal elements.
 *
 * For example, consider the following sequence:
 *
 *     AAAAABBBBBBBCCCCCCCCCCC
 *
 * These 23 elements can be grouped into 3 consecutive runs of equal elements:
 * 5 A's, 7 B's and 11 C's.
 *
 * An instance of this class stores information about runs of equal elements
 * rather than about individual elements. This saves memory if the number (R)
 * of runs of equal elements is much smaller than the number (N) of elements.
 *
 * Characteristics
 * ===============
 *
 *   Naive impl: memory O(N); lookup O(1); insert/delete O(N)
 *    This impl: memory O(R); lookup O(log(R)); insert/delete O(R)
 *
 * Caveats
 * =======
 *
 * 1. There's no guarantee as to which of the equal elements will be stored
 *    for any particular run
 * 2. null elements are not accepted
 */
public final class EqualElementsRegions<E> implements Sequence<E> {

    /*
     * 1. Runs are stored in a random access list `runs`
     * 2. `runs` initially contains two special runs: their elements are `null`
     *    and are not equal to any other element (including each other!)
     * 3. Adjacent elements that are equal share the run (and conversely):
     *        at(i) = at(i + 1) <=> runIndex(i) = runIndex(i + 1)
     * 4. Clients are not allowed to pass `null` as elements.
     *    The algorithm uses null as a special value.
     * 5. regions is always sorted by Region.start in a strictly ascending order:
     *    region.get(i) < region.get(i + 1);
     * 6. Merge-split changes are local; shift changes ripple
     */

    // 1. LeR
    // 2. Le, R
    // 3. L, eR
    // 4. L, e, R
    // 5. X, e, X

    final ArrayList<Run> runs = new ArrayList<>(List.of(
            new Run(-1, null),
            new Run(0, null))
    );

    @Override
    public void insert(int index, E e) {
        if (index < 0 || index > size()) {
            // index == size() is used for addition;
            // there's no suitable Objects.check*Index method
            throw new IndexOutOfBoundsException();
        }
        Objects.requireNonNull(e);

        int rightRegionIndex = regionIndexOf(index);
        int leftRegionIndex = leftOf(rightRegionIndex, index);

        Run leftRun = regionAt(leftRegionIndex);
        Run rightRun = regionAt(rightRegionIndex);

        if (isEqual(leftRun.e, e)) {
            if (isEqual(e, rightRun.e)) {                    // (1)
                assert leftRun == rightRun;
                shiftRegions(leftRegionIndex + 1, 1);
            } else {                                         // (2)
                assert leftRun != rightRun;
                shiftRegions(rightRegionIndex, 1);
            }
        } else {
            if (isEqual(e, rightRun.e)) {                    // (3)
                rightRun.start = index;
                shiftRegions(rightRegionIndex + 1, 1);
            } else if (!isEqual(leftRun.e, rightRun.e)) {    // (4)
                assert leftRegionIndex + 1 == rightRegionIndex;
                shiftRegions(rightRegionIndex, 1);
                runs.add(leftRegionIndex + 1, new Run(index, e));
            } else {                                         // (5)
                shiftRegions(rightRegionIndex + 1, 1);
                runs.addAll(leftRegionIndex + 1, List.of(
                        new Run(index, e),
                        new Run(index + 1, leftRun.e)
                ));
            }
        }
    }

    @Override
    public void delete(int index) {
        Objects.checkIndex(index, size());

        int regionIndex = regionIndexOf(index);
        int leftRegionIndex = leftOf(regionIndex, index);
        int rightRegionIndex = rightOf(regionIndex, index);

        E e = regionAt(regionIndex).e;

        if (leftRegionIndex == regionIndex) {
            if (regionIndex == rightRegionIndex) {                // (1)
                shiftRegions(rightRegionIndex + 1, -1);
            } else {                                              // (2)
                shiftRegions(rightRegionIndex, -1);
            }
        } else {

            Run leftRun = regionAt(leftRegionIndex);
            Run rightRun = regionAt(rightRegionIndex);

            if (regionIndex == rightRegionIndex) {                // (3)
                shiftRegions(rightRegionIndex, -1);
                rightRun.start = index;
            } else if (!isEqual(leftRun.e, rightRun.e)) {         // (4)
                shiftRegions(rightRegionIndex, -1);
                runs.remove(regionIndex);
            } else {                                              // (5)
                shiftRegions(rightRegionIndex + 1, -1);
                runs.subList(regionIndex, regionIndex + 2).clear();
            }
        }
    }

    @Override
    public E at(int index) {
        Objects.checkIndex(index, size());
        int i = regionIndexOf(index);
        return runs.get(i).e;
    }

    @Override
    public int size() {
        return runs.get(runs.size() - 1).start;
    }

    // -------------------- mechanics --------------------

    private boolean isEqual(E e1, E e2) {
        if (e1 == null || e2 == null) {
            return false; // in particular, isEqual(null, null) == false
        }
        return e1.equals(e2);
    }

    private void shiftRegions(int fromRegion, int shiftValue) {
        for (int i = fromRegion; i < runs.size(); i++)
            runs.get(i).start += shiftValue;
    }

    int regionIndexOf(int index) {
//        Region r = new Region(index, null); // TODO: get rid of allocation
//        int i = Collections.binarySearch(regions, r);
        int i = binarySearch(runs, 1, runs.size(), index, (r, idx) -> Integer.compare(r.start, idx));
        if (i >= 0) {
            return i;
        }

        // When regions is not empty, regions[0] always contains the minimum index.
        // Although it seems strange to store it, it is not: it contains the link to <T>.
        // When regions[0] exists, it always contains the minimum index;
        // So we would either found it of a bigger index, so it's safe to -1 it (left).
        return -(i + 1) - 1;
    }

    // Supply regionIndex, not to repeat binarySearch
    private int leftOf(int regionIndex, int index) {
        if (runs.get(regionIndex).start <= index - 1)
            return regionIndex;
        return regionIndex - 1; // (index - 1) belongs to the region at (regionIndex - 1)
    }

    private int rightOf(int regionIndex, int index) {
        if (index < runs.get(regionIndex + 1).start - 1)
            return regionIndex;
        return regionIndex + 1; // (index + 1) belongs to the region at (regionIndex + 1)
    }

    private Run regionAt(int regionIndex) {
        return runs.get(regionIndex);
    }

    final class Run {

        Run(int start, E e) {
            this.start = start;
            this.e = e;
        }

        int start;
        E e;
    }

    int nRegions() {
        return runs.size() - 2;
    }

    int nElements() {
        Set<E> elements = new HashSet<>();
        for (int i = 1; i < runs.size() - 1; i++) {
            elements.add(runs.get(i).e);
        }
        return elements.size();
    }
}
