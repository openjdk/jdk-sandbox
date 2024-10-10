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
 * @run junit TestLazyInflation
 */

import jdk.internal.util.json.JsonArray;
import jdk.internal.util.json.JsonObject;
import jdk.internal.util.json.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestLazyInflation {

    private static final String basicJsonObject =
            """
            { "name": "Brian",
              "shoeSize": 10,
              "nestedObject": { "name": "Jane", "age": 20, "city": "Boston" }
            }
            """;

    private static final String expectedObjectString =
            "{\"shoeSize\":10,\"name\":\"Brian\",\"nestedObject\":{\"city\":\"Boston\",\"name\":\"Jane\",\"age\":20}}";

    private static final String smallJsonArray =
            """
            [ 10, false ]
            """;

    private static final String basicJsonArray =
            """
            [ 10, false, "test", [1,2,3]]
            """;

    private static final String expectedArrayString =
            "[10,false,\"test\",[1,2,3]]";

    // Verify correctness of lazy inflation for basic get() and values()
    @Test
    public void basicObjectTest() {
        JsonObject doc = (JsonObject) JsonParser.parse(basicJsonObject);
        assertEquals("\"Brian\"", doc.get("name").toString());
        assertEquals("10", doc.get("shoeSize").toString());
        doc.keys(); // full inflate
        assertEquals(expectedObjectString, doc.toString());
    }

    // Check lazy inflation correctness of a nested structural JsonValue
    // inside JsonObject
    @Test
    public void nestedObjectTest() {
        JsonObject doc = (JsonObject) JsonParser.parse(basicJsonObject);
        doc.keys();
        JsonObject nestedDoc = (JsonObject) doc.get("nestedObject");
        assertEquals("\"Jane\"", nestedDoc.get("name").toString());
        nestedDoc.keys();
        assertEquals("\"Boston\"", nestedDoc.get("city").toString());
        // Capture only the nested jsonObject string
        assertEquals(expectedObjectString.substring(45, expectedObjectString.length() - 1),
                nestedDoc.toString());
    }

    // Use get to fully inflate a simple array
    @Test
    public void basicArrayMultipleGetTest() {
        JsonArray doc = (JsonArray) JsonParser.parse(smallJsonArray);
        assertEquals("10", doc.get(0).toString());
        assertEquals("false", doc.get(1).toString());
    }

    // Verify correctness of lazy inflation for basic get() and values()
    @Test
    public void basicArrayTest() {
        JsonArray doc = (JsonArray) JsonParser.parse(basicJsonArray);
        assertEquals("10", doc.get(0).toString());
        int len = doc.values().size(); // full inflate
        assertEquals("false", doc.get(1).toString());
        assertEquals(expectedArrayString, doc.toString());
        // Sanity check all elements accessible
        for (int i = 0; i < len; i++) {
            doc.get(i);
        }
    }

    // Check lazy inflation correctness of a nested structural JsonValue
    // inside JsonArray
    @Test
    public void nestedArrayTest() {
        JsonArray doc = (JsonArray) JsonParser.parse(basicJsonArray);
        doc.values();
        JsonArray nestedDoc = (JsonArray) doc.get(3);
        assertEquals("1", nestedDoc.get(0).toString());
        nestedDoc.values();
        assertEquals("2", nestedDoc.get(1).toString());
        assertEquals("[1,2,3]", nestedDoc.toString());
    }

    // Verify toString() lazy inflation for empty object
    @Test
    public void emptyObjectGetTest() {
        JsonObject emptyDoc = (JsonObject) JsonParser.parse("{  }");
        assertEquals("{}", emptyDoc.toString());
        emptyDoc.keys(); // check toString after inflation
        assertEquals("{}", emptyDoc.toString());
    }

    // Verify toString() lazy inflation for empty array
    @Test
    public void emptyArrayGetTest() {
        JsonArray emptyDoc = (JsonArray) JsonParser.parse("[  ]");
        assertEquals("[]", emptyDoc.toString());
        emptyDoc.values(); // check toString after inflation
        assertEquals("[]", emptyDoc.toString());
    }

    // Ensure proper invalid behavior for lazy JsonObject.get(string)
    @Test
    public void objectInvalidGetTest() {
        JsonObject doc = (JsonObject) JsonParser.parse(basicJsonObject);
        // invalid key, returns null
        assertNull(doc.get("invalidKey"));
        // Ensure null returned on subsequent get
        assertNull(doc.get("invalidKey"));
    }

    // Similar to objectInvalidIndexTest, but valid get, then invalid get
    @Test
    public void objectInvalidIndexAfterValidIndexTest() {
        JsonObject doc = (JsonObject) JsonParser.parse(basicJsonObject);
        // valid key
        doc.get("name");
        // Ensure null returned on subsequent get
        assertNull(doc.get("invalidKey"));
    }

    // Ensure proper invalid behavior for lazy JsonArray.get(int)
    @Test
    public void arrayInvalidIndexTest() {
        JsonArray doc = (JsonArray) JsonParser.parse(basicJsonArray);
        // invalid index, throws IOOBE
        IndexOutOfBoundsException msg = assertThrows(IndexOutOfBoundsException.class, () -> doc.get(4));
        assertEquals("Index 4 is out of bounds for length 4", msg.getMessage());
        // Ensure IOOBE, on subsequent get
        assertThrows(IndexOutOfBoundsException.class, () -> doc.get(4));
    }

    // Similar to arrayInvalidIndexTest, but valid get, then invalid get
    @Test
    public void arrayInvalidIndexAfterValidIndexTest() {
        JsonArray doc = (JsonArray) JsonParser.parse(basicJsonArray);
        // valid index
        doc.get(0);
        // Ensure IOOBE, on subsequent get
        assertThrows(IndexOutOfBoundsException.class, () -> doc.get(4));
    }
}
