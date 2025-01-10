/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @enablePreview
 * @run junit TestCircularReference
 */

import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.json.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestCircularReference {

    // Basic single depth circular reference
    @Test
    public void arrayTest() {
        ArrayList<Object> arr = new ArrayList<>();
        arr.add(arr);
        assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(arr));
    }

    // Basic single depth circular reference
    @Test
    public void objectTest() {
        HashMap<String,Object> map = new HashMap<>();
        map.put("foo", map);
        assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(map));
    }

    // Deeper nest circular reference
    @Test
    public void multiDepthCircularReferenceTest() {
        HashMap<String,Object> mapRoot = new HashMap<>();
        List<Object> listNode = new ArrayList<>();
        List<Object> lowerListNode = new ArrayList<>();
        HashMap<String, Object> mapNode = new HashMap<>();

        mapRoot.put("foo", listNode);
        listNode.add(lowerListNode);
        lowerListNode.add(mapNode);
        mapNode.put("bar", mapRoot);

        assertThrows(IllegalArgumentException.class, () -> Json.fromUntyped(mapRoot));
    }
}
