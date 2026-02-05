/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.json;

import java.io.Serial;

/**
 * Indicates that an error has been detected while traversing the {@code JsonValue}.
 * This exception is thrown under the following conditions:
 * <ul>
 *   <li>
 *     An {@link JsonValue##access access} or a
 *     {@link JsonValue##conversion conversion} method is invoked on a
 *     {@code JsonValue} of an incompatible type. For example, calling
 *     {@code bool()} on a {@code JsonValue} representing a JSON string.
 *   </li>
 *   <li>
 *     An access method is invoked for a non-existent value, such as
 *     {@code get()} for a missing member in a JSON object, or {@code element()}
 *     for an out-of-bounds index in a JSON array.
 *   </li>
 *   <li>
 *     {@code toLong()} or {@code toDouble()} is invoked on a JSON number that
 *     cannot be represented without loss of information by the target type.
 *   </li>
 * </ul>
 * @since 99
 */
public class JsonAssertionException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 2040280066622450939L;

    /**
     * Constructs a JsonAssertionException with the specified detail message.
     * @param message the detail message
     */
    public JsonAssertionException(String message) {
        super(message);
    }
}
