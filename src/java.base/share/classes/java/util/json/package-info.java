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

///Contains APIs for parsing JSON Documents, creating `JsonValue`s, and
///offering a mapping between a `JsonValue` and its corresponding Java Object.
///
///#### Design
///
///This API is designed around the concept of Algebraic Data Types (ADTs). Each
///JSON value is represented as a sealed `JsonValue` sum type, which can be
///pattern-matched into one of the following product types: `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`,
///`JsonBoolean`,`JsonNull`.
///
///Each JSON value subtype is also defined as a sealed interface that allows for flexible implementation(s) of the
///type. For example, `JsonArray` type is defined as follows:
///```
///public sealed interface JsonArray extends JsonValue permits (JsonArray implementation classes)
///```
///
///This design allows for the extraction of a JSON Value in a single and class safe expression as follows:
///```
///JsonValue doc = Json.parse(inputString);
///if (doc instanceof JsonObject o && o.members() instanceof Map<String, JsonValue> members
///    && members.get("name") instanceof JsonString js && js.value() instanceof String name
///    && members.get("age") instanceof JsonNumber jn && jn.value() instanceof long age) {
///        // use "name" and "age"
///}
///```
///
///#### Parsing
///
///Parsing of a JSON Document is performed lazily. Parsing validates that the
///JSON Document is syntactically correct, while simultaneously storing the
///positions of key JSON tokens (such as `{}[]",:`). The parse is finalized
///by constructing the root JSON value with its start and end positions. The
///underlying value(s) are evaluated and allocated on-demand. Such an approach
///allows for a lightweight parse that scales the memory usage efficiently.
///
///#### Mapping
///
///Once a `JsonValue` is obtained, it can be converted into a simple Java object (and vice versa) as seen below:
///```
///Object map = Json.toUntyped(someJsonObject); // produces Map<String, Object>
///Json.fromUntyped(map); // produces the JsonObject
///```
///Each Json value type has a corresponding Java object type. This mapping is defined below:
///```
///JsonArray : List<Object>
///JsonObject: Map<String, Object>
///JsonString: String
///JsonNumber: Number
///JsonBoolean: Boolean
///JsonNull: `null`
///```
///Since a `JsonValue` may or may not retain the original information from which
///it was created with, `fromUntyped()`/`toUntyped()` may not offer a round-trip
///which produces equivalent Objects.
///
///#### Formatting
///
///Formatting of a `JsonValue` is performed with either `JsonValue.toString()` or `Json.toDisplayString(JsonValue)`.
///These methods produce formatted String representations of a `JsonValue`. `toString()` produces the most compact
///representation which does not include extra whitespaces or line-breaks, suitable for network transaction.
///`toDisplayString(JsonValue)` produces a text representation that is human friendly,
///suitable for debugging or logging needs.
///
/// @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
///     Object Notation (JSON) Data Interchange Format
/// @since 25

@PreviewFeature(feature = PreviewFeature.Feature.JSON)
package java.util.json;

import jdk.internal.javac.PreviewFeature;
