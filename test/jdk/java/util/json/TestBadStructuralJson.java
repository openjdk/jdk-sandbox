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
 * @enablePreview
 * @run junit TestBadStructuralJson
 */

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.json.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class TestBadStructuralJson {

    @ParameterizedTest
    @MethodSource
    public void badJsonParseTest(String badJson) {
        assertThrows(JsonParseException.class, () -> Json.parse(badJson));
    }

    private static Stream<String> badJsonParseTest() {
        return Stream.of(
                "{ :name\": \"Brian\"}",
                "{ \"name:: \"Brian\"}",
                "{ \"name\": :Brian\"}",
                "{ \"name\": \"Brian:}",
                "{ \"name\": ,Brian\"}",
                "{ foo \"name\": \"Brian\"}", // Garbage before key
                "{ \"name\" foo : \"Brian\"}", // Garbage after key, but before colon
                // Garbage in second key/val
                "{ \"name\": \"Brian\" , \"name2\": \"Brian\" 5}",
                "{ \"name\": \"Brian\" 5}", // Garbage next to closing bracket
                "{ \"name\": \"Brian\"5   }", // Garbage next to value
                "{ \"name\": \"Brian\" 5 }", // Garbage with ws
                // Other cases, where non index based JsonValue occurs first
                "{ \"name\": 5 \"Brian\"  }",
                "{ \"name\": 5  null  }",
                // Garbage after JsonValue in the form of index based JsonValue
                "{ \"name\": \"Brian\" { \"name2\": \"another String\"} }",
                "{ \"name\": \"Brian\" [\"another String\"] }",
                "{ \"name\": \"Brian\" \"another String\"}"
        );
    }
}
