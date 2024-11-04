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

import java.util.Objects;

/**
 * The interface that represents JSON number
 * <p>
 * A {@code JsonNumber} can be produced by a {@link JsonParser} parse.
 * <p> Alternatively, {@link #from(Number)} can be used to obtain a {@code JsonNumber}
 * from a {@code Number}. {@link #to()} is the inverse operation, producing a {@code Number} from a
 * {@code JsonNumber}. These methods are not guaranteed to produce a round-trip.
 */
public sealed interface JsonNumber extends JsonValue permits JsonNumberImpl {
    /**
     * {@return the {@code Number} value represented with this
     * {@code JsonNumber} value}
     */
    Number value();

    /**
     * {@return the {@code Number} value represented with this
     * {@code JsonNumber} value}. The actual Number type depends
     * on the number value in this JsonNumber object.
     */
    Number to();

    /**
     * {@return the {@code JsonNumber} created from the given
     * {@code Number} object}
     *
     * @param num the given {@code Number}. Non-null.
     * @throws IllegalArgumentException if {@code num} is infinite or NaN.
     */
    static JsonNumber from(Number num) {
        Objects.requireNonNull(num);
        return new JsonNumberImpl(num);
    }
}
