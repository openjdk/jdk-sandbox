/*
 *     Copyright 2023 BELLSOFT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.bellsw.hotcode.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

public final class ListUtils {

    private ListUtils() {
    }

    /**
     * Values of list2 are subtracted from values of list1, the order is preserved.
     *
     * @param list1
     * @param list2
     * @return New modifiable list
     */
    public static <E> List<E> diff(List<E> list1, List<E> list2) {
        var toRemove = new HashSet<E>(list2);
        var result = new ArrayList<E>(list1.size());
        for (E e : list1) {
            if (!toRemove.contains(e)) {
                result.add(e);
            }
        }
        return result;
    }

    public static <E> List<E> limit(List<E> list, int maxSize) {
        return list.subList(0, Math.min(maxSize, list.size()));
    }

    public static <E> List<E> concat(List<E> list1, List<E> list2) {
        var result = new ArrayList<E>(list1.size() + list2.size());
        result.addAll(list1);
        result.addAll(list2);
        return result;
    }

}
