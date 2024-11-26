/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

/*
 * @test
 * @modules java.base/jdk.internal.util.json:+open
 * @run junit TestCircularReference
 */

import jdk.internal.util.json.*;

import java.util.ArrayList;
import java.util.HashMap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Either handled via a depth limit that that prevents StackOverflow
// Or clearly documented to avoid circular references. For now, the latter
public class TestCircularReference {

    @Test
    public void arrayTest() {
        ArrayList<Object> arr = new ArrayList<>();
        arr.add(arr);
        assertThrows(StackOverflowError.class, () -> Json.fromUntyped(arr));
    }

    @Test
    public void objectTest() {
        HashMap<String,Object> map = new HashMap<>();
        map.put("foo", map);
        assertThrows(StackOverflowError.class, () -> Json.fromUntyped(map));
    }
}
