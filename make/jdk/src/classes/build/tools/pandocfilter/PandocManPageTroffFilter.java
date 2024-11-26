/*
 * Copyright (c) 2018, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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

package build.tools.pandocfilter;

import build.tools.json.Json;
import build.tools.json.JsonArray;
import build.tools.json.JsonNumber;
import build.tools.json.JsonString;
import build.tools.json.JsonValue;

import java.io.FileNotFoundException;

public class PandocManPageTroffFilter extends PandocFilter {

    private JsonValue createStrong(JsonValue value) {
        return createPandocNode("Strong", value);
    }

    private JsonValue createHeader(JsonValue value) {
        return createPandocNode("Header", value);
    }

    /**
     * Callback to change all Str texts to upper case
     */
    private JsonValue uppercase(String type, JsonValue value) {
        if (type.equals("Str")) {
            if (value instanceof JsonString js) {
                return createStr(js.value().toUpperCase());
            } else {
                throw new RuntimeException("Json format incorrect");
            }
        }
        return null;
    }

    /**
     * Main callback function that performs our man page AST rewrites
     */
    private JsonValue manpageFilter(String type, JsonValue value) {
        // If it is a header, decrease the heading level by one, and
        // if it is a level 1 header, convert it to upper case.
        if (type.equals("Header")) {
            if (value instanceof JsonArray ja && ja.values().get(0) instanceof JsonNumber jn) {
                int level = jn.value().intValue();
                JsonValue[] arr = ja.values().toArray(new JsonValue[0]);
                arr[0] = Json.fromUntyped(level - 1);
                JsonArray array = JsonArray.of(arr);
                if (array.values().get(0) instanceof JsonNumber jn2 && jn2.value().intValue() == 1) {
                    return createHeader(traverse(array, this::uppercase, false));
                }
            } else {
                throw new RuntimeException("Json format incorrect");
            }
        }

        // Man pages does not have superscript. We use it for footnotes, so
        // enclose in [...] for best representation.
        if (type.equals("Superscript")) {
            return JsonArray.of(createStr("["), value, createStr("]"));
        }

        // If it is a link, put the link name in bold. If it is an external
        // link, put it in brackets. Otherwise, it is either an internal link
        // (like "#next-heading"), or a relative link to another man page
        // (like "java.html"), so remove it for man pages.
        if (type.equals("Link")) {
            if (value instanceof JsonArray ja && ja.values().get(2) instanceof JsonArray ja2 && ja2.values().get(0) instanceof JsonString js) {
                String targetStr = js.value();
                if (targetStr.startsWith("https:") || targetStr.startsWith("http:")) {
                    return JsonArray.of(
                            createStrong(ja.values().get(1)), createSpace(), createStr("[" + targetStr + "]"));
                } else {
                    return createStrong(ja.values().get(1));
                }
            } else {
                throw new RuntimeException("Json format incorrect");
            }
        }

        return null;
    }

    /**
     * Main function
     */
    public static void main(String[] args) throws FileNotFoundException {
        JsonValue json = loadJson(args);
        build.tools.pandocfilter.PandocManPageTroffFilter filter = new build.tools.pandocfilter.PandocManPageTroffFilter();

        JsonValue transformed_json = filter.traverse(json, filter::manpageFilter, false);

        System.out.println(transformed_json);
    }
}
