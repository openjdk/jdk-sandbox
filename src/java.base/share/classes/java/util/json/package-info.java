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

/**
 * Provides APIs for parsing JSON text, retrieving JSON values in the text, and
 * generating JSON text.
 *
 * <h2><a>Parsing JSON documents</a></h2>
 *
 * Parsing produces a {@code JsonValue} from JSON text and is done using either
 * {@link Json#parse(java.lang.String)} or {@link Json#parse(char[])}. A successful
 * parse indicates that the JSON text adheres to the
 * <a href="https://datatracker.ietf.org/doc/html/rfc8259">JSON grammar</a>.
 * The parsing APIs provided do not accept JSON text that contain JSON objects
 * with duplicate names.
 *
 * <h2><a>Retrieving JSON values</a></h2>
 *
 * Retrieving values from a JSON document involves two steps: first navigating
 * the document structure using a chain of "access" methods, and then converting
 * the result to the desired type using a "conversion" method. For example,
 * {@snippet lang=java:
 * var name = doc.get("foo").get("bar").element(0).string();
 * }
 * By chaining access methods, the "foo" member is retrieved from the root object,
 * then the "bar" member from "foo", followed by the element at index 0 from "bar".
 * The navigation process leads to a leaf JSON string element. The final call to the
 * {@code string()} conversion method returns the corresponding String object. For more
 * details on these methods, see {@link JsonValue JsonValue}.
 *
 * <h2><a>Generating JSON documents</a></h2>
 *
 * Generating JSON text is performed with either {@link
 * JsonValue#toString()} or {@link Json#toDisplayString(JsonValue, int)}.
 * These methods produce formatted String representations of a {@code JsonValue}.
 * The returned text adheres to the JSON grammar defined in RFC 8259.
 * {@code JsonValue.toString()} produces the most compact representation which does not
 * include extra whitespaces or line-breaks, preferable for network transaction
 * or storage. {@code Json.toDisplayString(JsonValue, int)} produces a text which
 * is human friendly, preferable for debugging or logging.
 *
 * @spec https://datatracker.ietf.org/doc/html/rfc8259 RFC 8259: The JavaScript
 *      Object Notation (JSON) Data Interchange Format
 * @since 99
 */
@PreviewFeature(feature = PreviewFeature.Feature.JSON)
package java.util.json;

import jdk.internal.javac.PreviewFeature;
