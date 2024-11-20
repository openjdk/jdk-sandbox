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

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * The interface that represents a JSON value. {@code JsonValue} is the type returned
 * by a {@link JsonParser} parse. Valid subtypes are either {@code JsonString},
 * {@code JsonNumber}, {@code JsonObject}, {@code JsonArray}, {@code JsonBoolean},
 * or {@code JsonNull}.
 * <p>
 * See {@link #to()} and {@link #from(Object)} for converting between a {@code JsonValue}
 * and its corresponding data type. For example,
 * {@snippet lang=java:
 *     var values = Arrays.asList("foo", true, 25);
 *     var json = JsonValue.from(values);
 *     json.to().equals(values); // returns true
 * }
 * See {@link #format(Option...)} for converting a {@code JsonValue}
 * to its corresponding JSON String. For example,
 * {@snippet lang=java:
 *     var values = Arrays.asList("foo", true, 25);
 *     var json = JsonValue.from(values);
 *     json.format(); // returns "[\"foo\",true,25]"
 * }
 */
public sealed interface JsonValue
        permits JsonString, JsonNumber, JsonObject, JsonArray, JsonBoolean, JsonNull {

    /**
     * {@return an {@code Object} that represents the data of this {@code JsonValue}}
     * The return type depends on the subtype of this interface.
     */
    Object to();

    /**
     * {@return a {@code JsonValue} that represents the data type of {@code from}}
     *
     * @param from the data to produce the {@code JsonValue} from. May be null.
     * @throws IllegalArgumentException if {@code from} cannot be converted
     * to any of the {@code JsonValue} subtypes.
     * @throws StackOverflowError if {@code from} contains a circular reference
     */
    static JsonValue from(Object from) {
        return switch (from) {
            case String str -> JsonString.from(str);
            case Map<?, ?> map -> JsonObject.from(map);
            case List<?> list-> JsonArray.from(list);
            case Object[] array -> JsonArray.from(Arrays.asList(array));
            case Boolean bool -> JsonBoolean.from(bool);
            case Number num-> JsonNumber.from(num);
            case null -> JsonNull.ofNull();
            default -> throw new IllegalArgumentException("Type not recognized.");
        };
    }

    /**
     * {@return the String representation of this {@code JsonValue} that conforms
     * to the JSON syntax}
     * @param options formatting options
     */
    String format(Option... options);

    /**
     * Indicates whether the given {@code obj} is "equal to" this {@code JsonValue}.
     * The comparison is based on the original document if it was produced by
     * parsing a JSON document.
     */
    @Override
    boolean equals(Object obj);

    // TBD: do we need this override?
    /**
     * {@return the hash code value of this {@code JsonValue}}
     * The returned hash code is based on the original document if it was
     * produced by parsing a JSON document.
     */
    @Override
    int hashCode();
}
