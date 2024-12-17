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

///## Json API
///
///### Objective
///
///To provide a simple parser and API to access JSON documents.
///
///### Goals
///   - Simple and minimal API to parse/build JSON values to/from the structure of Java objects. (JSON types <-> Java
///types)
///   - Parser that takes JSON document as a String or a char[]. By limiting the input JSON document, this gives a
///reasonable protection against the OOME issue. (String representation -> JSON values)
///   - Formatting of JSON objects, such as producing compact representation, or human friendly format. (JSON values
///     -> String representation)
///
///### Non-Goals
///   - General means to serialize/deserialize arbitrary POJOs to JSON
///   - Path based traversing of JSON values
///   - Parsing of JSON5 documents
///
///### Description
///The proposed API exploits the idea of Algebraic Data Type. Each JSON value is represented as a sealed `JsonValue`
///sum type, which is pattern-matched into one of product types; `JsonObject`, `JsonArray`, `JsonString`, `JsonNumber`,
///`JsonBoolean`, or `JsonNull`.
///```
///public sealed interface JsonValue permits JsonString, JsonNumber, JsonObject, JsonArray, JsonBoolean, JsonNull
///```
///Each JSON value type is also defined as a sealed interface that allows a flexible implementation of the
///type. For example, `JsonArray` type is defined as follows:
///```
///public sealed interface JsonArray extends JsonValue permits (JsonArray implementation classes)
///```
///Then the leaf JSON value can be extracted in a single expression as follows:
///```
///JsonValue doc = Json.parse(inputString);
///if (doc instanceof JsonObject o && o.keys() instanceof Map<String, JsonValue> keys
///    && keys.get("name") instanceof JsonString jstring && jstring.value() instanceof String name
///    && keys.get("age") instanceof JsonNumber number && jnumber.value() instanceof int age) {
///        // use "name" and "age"
///}
///```
///The above expression can be further simplified with the deconstructor pattern match.
///
///Once a `JsonValue` is obtained, it can convert into a simple Java object (and vice versa) with these methods:
///```
///Map<String, Object> map = Json.toUntyped(someJsonObject); // produces Map<String, Object>
///Json.fromUntyped(map); // produces the JsonObject
///```
///Each Json value type has corresponding Java object type. Here is the mapping:
///```
///JsonArray : List<Object>
///JsonObject: Map<String, Object>
///JsonString: String
///JsonNumber: Number
///JsonBoolean: Boolean
///JsonNull: `null`
///```
///Since the `JsonValue` may or may not retain the information of which the Json value is created, `fromUntyped()
///`/`toUntyped()` do not necessarily offer round-trip
///
///#### Parsing
///
///Parsing of a JSON document is done lazily. Initial parsing path only records the positions of those JSON value
///delimiting characters, such as `{}[]",:`. Then the parser constructs the top level JSON values only with the start
///and end positions, and the value itself is evaluated when the value is indeed to be realized. This way, the invocation
///of `Json.parse()` is lightweight and the parsing for the objective leaf can be minimized.
///A simple comparison with Jackson shows that retrieving a leaf node text (using CLDR's time zone names JSON document)
///is 70% faster with this implementation.
///
///#### Formatting
///
///Formatting of a JSON value is done with either `JsonValue.toString()` or `Json.toDisplayString(JsonValue)` methods.
///These methods produce formatted String representation of a JSON value. `toString()` produces the most compact
///representation which does not include extra whitespaces, line-breaks, suitable for network transaction, while
///`toDisplayString(JsonValue)` produces the text representation that is human friendly.
///
///### Issues
///   - Since the parser implements lazy-parsing, it may not necessarily fail on calling `parse()`. Parse exception may be
///delayed until actually accessing the malformed JSON values.
///
///### TBD
///   - module/package hierarchy
///   - naming of classes/methods
package java.util.json;
