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
 * The interface that represents JSON object
 */
public sealed interface JsonObject extends JsonValue permits JsonObjectImpl {
    /**
     * {@return the map of {@code String} to {@code JsonValue} members in this
     * JSON object}
     */
    Map<String, JsonValue> keys();

    /**
     * {@return the {@code JsonValue} member in this JSON object}
     * @param key the String key
     */
    JsonValue get(String key);

    /**
     * {@return the {@code JsonValue} member, or {@code defaultValue} if this
     * JSON object does not contain the key}
     * @param key the String key
     */
    JsonValue getOrDefault(String key, JsonValue defaultValue);

    /**
     * {@return {@code true} if this JSON object contains a mapping for
     * the specified key}
     * @param key the String key
     */
    boolean contains(String key);

    /**
     * {@return the map of {@code String} to {@code Object} in this
     * JSON object}
     */
    Map<String, Object> to();

    /**
     * {@return the size of this JSON object}
     */
    int size();

    /**
     * {@return the {@code JsonObject} created from the given
     * Map of {@code Object}s}
     *
     * @param from the Map of {@code Object}s. Non-null.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    static JsonObject from(Map<?, ?> from) {
        Objects.requireNonNull(from);
        return new JsonObjectImpl(from);
    }

    /**
     * Used to build instances of {@code JsonObject}
     *
     * @apiNote Use this class to construct a new {@code JsonObject} from
     * an existing {@code JsonObject}.
     */
    final class Builder {

        // A mutable form of 'theKeys' to be used by the Builder
        private final Map<String, JsonValue> map;

        /**
         * Constructs a {@code Builder} composed of the {@code JsonObject} provided.
         *
         * @param obj the {@code JsonObject} to initialize the {@code Builder} with.
         * @throws NullPointerException if {@code JsonObject} is null.
         */
        public Builder(JsonObject obj) {
            Objects.requireNonNull(obj);
            var impl = (JsonObjectImpl) obj;
            if (!impl.inflated) {
                // Finish inflation to get all elements if not fully inflated
                impl.inflateAll();
            }
            // This is safe as JsonValue is final and immutable.
            // 'theKeys' is also unmodifiable once fully inflated
            this.map = new HashMap<>(impl.theKeys);
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
            return JsonObject.from(map);
        }
    }
}
