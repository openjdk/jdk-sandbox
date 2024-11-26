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

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * The interface that represents JSON object.
 * <p>
 * A {@code JsonObject} can be produced by a {@link JsonParser#parse(String)}.
 * <p> Alternatively, {@link Json#fromUntyped(Map)} can be used to obtain a {@code JsonObject}
 * from a {@code Map}. {@link Json#toUntyped(JsonObject)} is the inverse operation,
 * producing a {@code Map} from a {@code JsonObject}. These methods are not
 * guaranteed to produce a round-trip. Callers of {@link Json#fromUntyped(Map)} should be
 * aware that a {@code Map} containing a circular reference will cause a
 * {@code StackOverFlowError}.
 */
public sealed interface JsonObject extends JsonValue permits JsonObjectImpl {
    /**
     * {@return the map of {@code String} to {@code JsonValue} members in this
     * JSON object}
     */
    Map<String, JsonValue> keys();

    /**
     * {@return the {@code JsonObject} created from the given
     * map of {@code String} to {@code JsonValue}s}
     *
     * @param map the map of {@code JsonValue}s. Non-null.
     */
    static JsonObject of(Map<String, JsonValue> map) {
        Objects.requireNonNull(map);
        return new JsonObjectImpl(map);
    }

    /**
     * {@code Builder} is used to build new instances of {@code JsonObject}.
     * For example,
     * {@snippet lang=java:
     *     var original = JsonParser.parse("{ \"name\" : \"Foo\" }");
     *     if (original instanceof JsonObject json) {
     *         var modified = new JsonObject.Builder(json).put("name", Json.from("Bar")).build();
     *     }
     * }
     *
     * @apiNote Use this class to construct a new {@code JsonObject} from
     * an existing {@code JsonObject}. This is useful for mutating a {@code JsonObject}
     * without having to modify the underlying data.
     */
    final class Builder {

        // A mutable form of 'theKeys' to be used by the Builder
        private final Map<String, JsonValue> map;

        /**
         * Constructs a {@code Builder} composed of the underlying data provided
         * by {@code json}.
         *
         * @param json the {@code JsonObject} to initialize the {@code Builder} with.
         * @throws NullPointerException if {@code JsonObject} is null.
         */
        public Builder(JsonObject json) {
            Objects.requireNonNull(json);
            this.map = new HashMap<>(json.keys());
        }

        /**
         * Constructs an empty {@code Builder}.
         */
        public Builder() {
            this.map = new HashMap<>();
        }

        /**
         * Associates the specified value with the specified key in this
         * {@code Builder}. If the {@code Builder} previously contained a mapping
         * for the key, the old value is replaced.
         *
         * @param key key with which the specified value is to be associated
         * @param val value to be associated with the specified key
         * @return This {@code Builder}.
         */
        public Builder put(String key, JsonValue val) {
            this.map.put(key, val);
            return this;
        }

        /**
         * Removes the mapping for the specified key from this {@code Builder},
         * if present.
         *
         * @param key whose mapping is to be removed from this {@code Builder}
         * @return This {@code Builder}.
         */
        public Builder remove(String key) {
            this.map.remove(key);
            return this;
        }

        /**
         * Resets the {@code Builder} to its initial, empty state.
         *
         * @return This {@code Builder}.
         */
        public Builder clear() {
            this.map.clear();
            return this;
        }

        /**
         * Returns an instance of {@code JsonObject} obtained from the
         * operations performed on this {@code Builder}.
         *
         * @return A {@code JsonObject}.
         */
        public JsonObject build() {
            return new JsonObjectImpl(map);
        }
    }
}
