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

package java.util.json;

import jdk.internal.javac.PreviewFeature;

/**
 * The interface that represents a JSON value.
 * <p>
 * A {@code JsonValue} can be produced by {@link Json#parse(String)} or {@link
 * Json#fromUntyped(Object)}. See {@link #toString()}  for converting a {@code
 * JsonValue} to its corresponding JSON String. For example,
 * {@snippet lang=java:
 *     List<Object> values = Arrays.asList("foo", true, 25);
 *     JsonValue json = Json.fromUntyped(values);
 *     json.toString(); // returns "[\"foo\",true,25]"
 * }
 * Instances of {@code JsonValue} are immutable. The data contained in the instances,
 * once created, cannot be modified.
 * <p>
 * When two instances of {@code JsonValue} are equal (according to `equals`), a program
 * should not attempt to distinguish between their identities, whether directly via reference
 * equality or indirectly via an appeal to synchronization, identity hashing,
 * serialization, or any other identity-sensitive mechanism.
 * <p>
 * Synchronization on instances of {@code JsonValue} is strongly discouraged,
 * because the programmer cannot guarantee exclusive ownership of the
 * associated monitor.
 *
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
public sealed interface JsonValue
        permits JsonString, JsonNumber, JsonObject, JsonArray, JsonBoolean, JsonNull {

    /**
     * {@return the String representation of this {@code JsonValue} that conforms
     * to the JSON syntax} If this {@code JsonValue} is created by parsing a
     * JSON document, it preserves the text representation of the corresponding
     * JSON element, except that the returned string does not contain any white
     * spaces or newlines to produce a compact representation.
     * For a String representation suitable for display, use
     * {@link Json#toDisplayString(JsonValue)}.
     *
     * @see Json#toDisplayString(JsonValue)
     */
    String toString();
}
