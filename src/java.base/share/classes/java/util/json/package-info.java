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

/**
 * Provides APIs for parsing JSON text, creating {@code JsonValue}s, and
 * offering a mapping between a {@code JsonValue} and its corresponding Java Object.
 *
 * <h2><a>Design</a></h2>
 * This API is designed so that JSON values are composed as Algebraic
 * Data Types (ADTs) defined by interfaces. Each JSON value is represented as a
 * sealed {@code JsonValue} <i>sum</i> type, which can be
 * pattern-matched into one of the following <i>product</i> types: {@code JsonObject},
 * {@code JsonArray}, {@code JsonString}, {@code JsonNumber}, {@code JsonBoolean},
 * {@code JsonNull}. These product types are defined as non-sealed interfaces that
 * allow flexibility in the implementation of the type. For example, {@code JsonArray}
 * is defined as follows:
 * <pre>{@code public non-sealed interface JsonArray extends JsonValue}</pre>
 *
 * <p> This API relies on pattern matching to allow for the extraction of a
 * JSON Value in a <i>single and class safe expression</i> as follows:
 * {@snippet lang=java:
 * JsonValue doc = Json.parse(text);
 * if (doc instanceof JsonObject o && o.members() instanceof Map<String, JsonValue> members
 *     && members.get("name") instanceof JsonString js && js.value() instanceof String name
 *     && members.get("age") instanceof JsonNumber jn && jn.toNumber() instanceof long age) {
 *         // can use both "name" and "age" from a single expression
 * }
 * }
 *
 * Both {@code JsonValue} instances and their underlying values are immutable.
 *
 * <h2><a>Parsing</a></h2>
 *
 * Parsing produces a {@code JsonValue} from JSON text and is done using either
 * {@link Json#parse(java.lang.String)} or {@link Json#parse(char[])}. A successful
 * parse indicates that the JSON text adheres to the
 * <a href="https://datatracker.ietf.org/doc/html/rfc8259">JSON grammar</a>.
 * The parsing APIs provided do not accept JSON text that contain JSON Objects
 * with duplicate names.
 *
 * <p>For the reference JDK implementation, {@code JsonValue}s created via parsing
 * procure their underlying values <i>lazily</i>.
 *
 * <h2><a>Formatting</a></h2>
 *
 * Formatting of a {@code JsonValue} is performed with either {@link
 * JsonValue#toString()} or {@link Json#toDisplayString(JsonValue, int)}.
 * These methods produce formatted String representations of a {@code JsonValue}.
 * The returned text adheres to the JSON grammar defined in RFC 8259.
 * {@code JsonValue.toString()} produces the most compact representation which does not
 * include extra whitespaces or line-breaks, preferable for network transaction
 * or storage. {@code Json.toDisplayString(JsonValue, int)} produces a text representation that
 * is human friendly, preferable for debugging or logging.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *      Object Notation (JSON) Data Interchange Format
 * @since 99
 */

@PreviewFeature(feature = PreviewFeature.Feature.JSON)
package java.util.json;

import jdk.internal.javac.PreviewFeature;
