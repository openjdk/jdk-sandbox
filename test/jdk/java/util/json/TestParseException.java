/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
 * @run junit TestParseException
 */

import java.util.json.*;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestParseException {

    private static final String basic = "foobarbaz";

    private static final String structural =
            """
            [
              null,   foobarbaz
            ]
            """;

    private static final String structuralWithNested =
            """
            {
                "key" :
                [
                    "value",
                    null, foobarbaz
                ]
            }
            """;

    private static final String duplicate =
            """
            {
                "baz" : "foo",
                "bar" : "quux",
                "baz" : "foo"
            }
            """;

    // Ensure that JPE is thrown, not SIIOBE
    @Test
    void testBasicNonStructural() {
        assertThrows(JsonParseException.class, () -> Json.parse("fals"));
        assertThrows(JsonParseException.class, () -> Json.parse("tru"));
        assertThrows(JsonParseException.class, () -> Json.parse("nul"));
    }

    @Test
    void testBasic() {
        Exception e = assertThrows(JsonParseException.class, () -> Json.parse(basic));
        assertEquals("Unexpected character(s): (foobarba) at Row 0, Col 0.", e.getMessage());
    }

    @Test
    void testStructural() {
        Exception e = assertThrows(JsonParseException.class, () -> Json.parse(structural));
        assertEquals("Unexpected character(s): (foobarba) at Row 1, Col 10.", e.getMessage());
    }

    @Test
    void testStructuralWithNested() {
        Exception e = assertThrows(JsonParseException.class, () -> Json.parse(structuralWithNested));
        assertEquals("Unexpected character(s): (foobarba) at Row 4, Col 14.", e.getMessage());
    }
}
