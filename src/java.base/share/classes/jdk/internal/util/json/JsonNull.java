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

/**
 * The interface that represents JSON null
 * A {@code JsonNull} can be produced by a {@link JsonParser} parse. A {@code
 * JsonNull} string passed to a {@code parse} has syntax as follows,
 * <blockquote><pre>
 * <i>JSON Null</i> = <i>null</i>
 * </pre></blockquote>
 * A well-formed {@code JsonNull} string is simply the literal {@code null}.
 * Note that this value is case-sensitive, and is required to be lowercase.
 * <p>
 * Alternatively, {@link #ofNull()} can be used to obtain a {@code JsonNull}.
 */
public sealed interface JsonNull extends JsonValue permits JsonNullImpl {
    /**
     * {@return {@code null}}
     */
    Object to();

    /**
     * {@return the {@code JsonNull} that represents "null" JSON value}
     */
    static JsonNull ofNull() {
        return JsonNullImpl.NULL;
    }
}
