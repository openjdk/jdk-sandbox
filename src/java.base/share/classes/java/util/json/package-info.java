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
 * {@code JsonNull}. These product types are defined as sealed interfaces that
 * allow flexibility in the implementation of the type. For example, {@code JsonArray}
 * is defined as follows:
 * <pre>{@code public sealed interface JsonArray extends JsonValue permits (JsonArray implementation classes)}</pre>
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
 * JSON values represented by {@code JsonValue} instances are not intended to be
 * compared using {@code JsonValue.equals()}. Instead, equality should be determined
 * by comparing the underlying values, accessible via product type specific accessors
 * such as {@link JsonNumber#toNumber()} (and later deconstructors). Both
 * {@code JsonValue} instances and their underlying values are immutable.
 *
 * <h2><a>Parsing</a></h2>
 *
 * Parsing produces a {@code JsonValue} from JSON text and is done using either
 * {@link java.util.json.Json#parse(java.lang.String)} or {@link
 * java.util.json.Json#parse(char[])}. A successful parse indicates that the JSON text
 * adheres to the JSON grammar. The parsing APIs provided do not accept JSON text
 * that contain JSON Objects with duplicate keys.
 *
 * <p>Parsing is performed <i>lazily</i>. The underlying value(s) of the root
 * {@code JsonValue} are evaluated and allocated on-demand. This approach allows
 * for memory usage to scale as required. Consider the following example,
 * {@snippet lang=java:
 * String text = "{\"foo\" : [null, 15, \"baz\"], \"bar\" : true}";
 * JsonValue root = Json.parse(text);
 * if (root instanceof JsonObject jo) {
 *     Map<String, JsonValue> elements = jo.members();
 *     if (elements.get("foo") instanceof JsonArray ja) {
 *         List<JsonValue> values = ja.values();
 *         // use "values"
 *     }
 * }
 * }
 * The JSON text consists of a root JSON Object composed of the members "foo" and
 * "bar". The initial parse invocation only allocates an empty root JSON object.
 * It isn't until {@code JsonObject.members()} is invoked, that the underlying members
 * are allocated as well. Similarly, the JSON Array belonging to "foo"
 * remains empty, and the underlying values are not allocated until {@code
 * JsonArray.values()} is invoked.
 *
 * <h2><a>Mapping</a></h2>
 *
 * Once a {@code JsonValue} is obtained, it can be converted into a simple Java
 * object (and vice versa) via {@link java.util.json.Json#fromUntyped(java.lang.Object)}
 * /{@link java.util.json.Json#toUntyped(java.util.json.JsonValue)} as seen below:
 * {@snippet lang=java:
 * Object map = Json.toUntyped(someJsonObject); // produces Map<String, Object>
 * Json.fromUntyped(map); // produces the JsonObject
 * }
 * Each Json value type has a corresponding Java object type.
 * This mapping is defined below:
 * <table id="mapping-table" class="striped">
 * <caption>Mapping Table</caption>
 * <thead>
 *    <tr>
 *       <th scope="col" class="TableHeadingColor">Untyped Object</th>
 *       <th scope="col" class="TableHeadingColor">JsonValue</th>
 *    </tr>
 * </thead>
 * <tbody>
 * <tr>
 *     <th>{@code List<Object>}</th>
 *     <th> {@code JsonArray}</th>
 * </tr>
 * <tr>
 *     <th>{@code Boolean}</th>
 *     <th>{@code JsonBoolean}</th>
 * </tr>
 * <tr>
 *     <th>{@code `null`}</th>
 *     <th> {@code JsonNull}</th>
 * </tr>
 * <tr>
 *     <th>{@code Number*}</th>
 *     <th>{@code JsonNumber}</th>
 * </tr>
 * <tr>
 *     <th>{@code Map<String, Object>}</th>
 *     <th> {@code JsonObject}</th>
 * </tr>
 * <tr>
 *     <th>{@code String}</th>
 *     <th>{@code JsonString}</th>
 * </tr>
 * </tbody>
 * </table>
 *
 * <i>The supported Number subclasses are: Byte, Integer, Long, Short, Float,
 * Double, BigInteger, and BigDecimal</i>
 *
 * <p>Since a {@code JsonValue} may or may not retain the original information from which
 * it was created with, {@code fromUntyped()}/{@code toUntyped()} do not necessarily offer a
 * round-trip that produces equivalent Objects.
 *
 * <h2><a>Formatting</a></h2>
 *
 * Formatting of a {@code JsonValue} is performed with either {@link
 * java.util.json.JsonValue#toString()} or {@link
 * java.util.json.Json#toDisplayString(java.util.json.JsonValue)}.
 * These methods produce formatted String representations of a {@code JsonValue}.
 * The returned text adheres to the JSON grammar defined in RFC 8259.
 * {@code JsonValue.toString()} produces the most compact representation which does not
 * include extra whitespaces or line-breaks, preferable for network transaction
 * or storage. {@code Json.toDisplayString(JsonValue)} produces a text representation that
 * is human friendly, preferable for debugging or logging.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *      Object Notation (JSON) Data Interchange Format
 * @since 25
 */

@PreviewFeature(feature = PreviewFeature.Feature.JSON)
package java.util.json;

import jdk.internal.javac.PreviewFeature;
