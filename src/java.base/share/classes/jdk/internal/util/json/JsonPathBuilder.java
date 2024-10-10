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

package jdk.internal.util.json;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

// this can be user's code outside the JDK, on top of our API
/**
 * A path-based accessor to a leaf JsonValue. For example:
 * {@snippet lang = java:
 *    JsonValue doc = JsonParser.parse(
 *        """
 *        [
 *          { "name": "John", "age": 30, "city": "New York" },
 *          { "name": "Jane", "age": 20, "city": "Boston" }
 *        ]
 *        """);
 *    JsonPathBuilder jpb = new JsonPathBuilder();
 *    if (jpb.arrayIndex(1).objectKey("name").build().apply(doc) instanceof JsonString name) {
 *        // name should be "Jane"
 *    }
 * }
 */
/*public*/ class JsonPathBuilder {
    private final List<Function<JsonValue, JsonValue>> funcs;

    /**
     * Creates a builder
     */
    public JsonPathBuilder() {
        funcs = new ArrayList<>();
    }

    /**
     * Obtains the member value for the specified key in this JsonObject
     *
     * @param key the key in this JsonObject
     * @return this builder
     * @throws IllegalStateException if the target JsonValue is not a JsonObject
     */
    public JsonPathBuilder objectKey(String key) {
        funcs.add(jsonValue -> {
            if (jsonValue instanceof JsonObject jo) {
                return jo.keys().get(key);
            } else {
                throw new IllegalStateException("Not a JsonObject: %s".formatted(jsonValue));
            }
        });
        return this;
    }

    /**
     * Obtains the element value for the specified index in this JsonArray
     *
     * @param index the index in this JsonArray
     * @return this builder
     * @throws IllegalStateException if the target JsonValue is not a JsonArray
     */
    public JsonPathBuilder arrayIndex(int index) {
        funcs.add(jsonValue -> {
            if (jsonValue instanceof JsonArray ja) {
                return ja.values().get(index);
            } else {
                throw new IllegalStateException("Not a JsonArray: %s".formatted(jsonValue));
            }
        });
        return this;
    }

    /**
     * Clears this builder.
     * @return this builder
     */
    public JsonPathBuilder clear() {
        funcs.clear();
        return this;
    }

    /**
     * Builds the function to obtain the leaf JsonValue
     * @return the function to the leaf JsonValue
     */
    public Function<JsonValue, JsonValue> build() {
        return jsonValue -> {
            JsonValue ret = jsonValue;
            for (Function<JsonValue, JsonValue> f : funcs) {
                ret = f.apply(ret);
            }
            return ret;
        };
    }
}
