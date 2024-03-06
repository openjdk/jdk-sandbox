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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

public class ListUtilsTest {

    @Test
    void diff() {
        var list1 = List.of(4, 3, 3, 2, 2, 2, 1);
        var list2 = List.of(1, 1, 2, 2);
        var expected = List.of(4, 3, 3);
        var actual = ListUtils.diff(list1, list2);
        assertEquals(expected, actual);
    }

    @Test
    void limit() {
        var list = List.of(1, 2, 3);

        var expected = List.of(1, 2);
        var actual = ListUtils.limit(list, 2);
        assertEquals(expected, actual);

        expected = list;
        actual = ListUtils.limit(list, 4);
        assertEquals(expected, actual);
    }

    @Test
    void concat() {
        var list1 = List.of(1, 2);
        var list2 = List.of(3, 4);
        var expected = List.of(1, 2, 3, 4);
        var actual = ListUtils.concat(list1, list2);
        assertEquals(expected, actual);
    }

}
