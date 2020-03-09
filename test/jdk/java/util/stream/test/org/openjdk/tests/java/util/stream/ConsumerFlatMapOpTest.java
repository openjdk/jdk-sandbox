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
 * @summary flat-map operations
 */

package org.openjdk.tests.java.util.stream;

import org.testng.annotations.Test;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.OpTestCase;
import java.util.stream.Stream;

import static java.util.stream.LambdaTestHelpers.*;

@Test
public class ConsumerFlatMapOpTest extends OpTestCase {

    @Test
    public void testFlatMap() {
        Stream<String> strings = List.of("hello", "there", "", "yada").stream();

        BiConsumer<String, Consumer<Character>> mapper = (s, c) ->
            flattenChars.apply(s).forEach(character -> c.accept(character));

        assertConcat(strings.flatMap(mapper).iterator(), "hellothereyada");
    }
}