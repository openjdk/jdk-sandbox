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
 * The interface that represents JSON boolean
 * A {@code JsonBoolean} can be produced by a {@link JsonParser} parse. A {@code
 * JsonBoolean} string passed to a {@code parse} has syntax as follows,
 * <blockquote><pre>
 * <i>JSON boolean</i> =
 *      <i>true</i>
 *      <i>false</i>
 * </pre></blockquote>
 * A well-formed {@code JsonBoolean} string is simply one of the literal names:
 * {@code true} or {@code false}. Note that these values are case-sensitive, and
 * are required to be lowercase.
 * <p>
 * Alternatively, {@link #from(Boolean)} can be used to obtain a {@code JsonBoolean}
 * from a {@code Boolean}. {@link #to()} is the inverse operation, producing a {@code Boolean} from a
 * {@code JsonBoolean}.
 */
public sealed interface JsonBoolean extends JsonValue permits JsonBooleanImpl {

    /**
     * {@return the {@code boolean} value represented with this
     * {@code JsonBoolean} value}
     */
    boolean value();

    /**
     * {@return the {@code Boolean} value represented with this
     * {@code JsonBoolean} value}
     */
    Boolean to();

    /**
     * {@return the {@code JsonBoolean} created from the given
     * {@code Boolean} object}
     *
     * @param from the given {@code Boolean}. Non-null.
     */
    static JsonBoolean from(Boolean from) {
        Objects.requireNonNull(from);
        return from ? JsonBooleanImpl.TRUE : JsonBooleanImpl.FALSE;
    }
}
