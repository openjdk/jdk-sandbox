/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

// Copied from internal.jdk.util.json by BUILD_TOOLS_JDK target
import build.tools.json.*;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PandocFilter {
    /**
     * Traverse a tree of pandoc format objects, calling callback on each
     * element, and replacing it if callback returns a new object.
     * <p>
     * Inspired by the walk method in
     * https://github.com/jgm/pandocfilters/blob/master/pandocfilters.py
     */
    public JsonValue traverse(JsonValue jsonIn, Callback callback, boolean deep) {
        if (jsonIn instanceof JsonArray ja) {
            List<JsonValue> processedArray = new ArrayList<>();
            for (JsonValue jv : ja.values()) {
                if (jv instanceof JsonObject jo && jo.keys().containsKey("t") && jo.keys().get("t") instanceof JsonString type) {
                    JsonValue replacement = callback.invoke(
                            type.value(), jo.keys().containsKey("c") ? jo.keys().get("c") : Json.parse("[]"));
                    if (replacement == null) {
                        // no replacement object returned, use original value
                        processedArray.add(traverse(jv, callback, deep));
                    } else if (replacement instanceof JsonArray replacementArray) {
                        // array of objects returned, splice all elements into array
                        for (JsonValue replElem : replacementArray.values()) {
                            processedArray.add(traverse(replElem, callback, deep));
                        }
                    } else {
                        // replacement object given, traverse it
                        processedArray.add(traverse(replacement, callback, deep));
                    }
                } else {
                    processedArray.add(traverse(jv, callback, deep));
                }
            }
            return JsonArray.of(processedArray.toArray(new JsonValue[0]));
        } else if (jsonIn instanceof JsonObject jo) {
            if (deep && jo.keys().containsKey("t") && jo.keys().get("t") instanceof JsonString type) {
                JsonValue replacement = callback.invoke(type.value(),
                        jo.keys().containsKey("c") ? jo.keys().get("c") : Json.parse("[]"));
                if (replacement != null) {
                    return replacement;
                }
            }
            var processed_obj = new JsonObject.Builder();
            for (String key : jo.keys().keySet()) {
                processed_obj.put(key, traverse(jo.keys().get(key), callback, deep));
            }
            return processed_obj.build();
        } else {
            return jsonIn;
        }
    }

    public JsonValue createPandocNode(String type, JsonValue content) {
        if (content == null) {
            return new JsonObject.Builder()
                    .put("t", Json.fromUntyped(type)).build();
        } else {
            return new JsonObject.Builder()
                    .put("t", Json.fromUntyped(type))
                    .put("c", content).build();
        }
    }

    public JsonValue createPandocNode(String type) {
        return createPandocNode(type, null);
    }

    /*
     * Helper constructors to create pandoc format objects
     */
    public JsonValue createSpace() {
        return createPandocNode("Space");
    }

    public JsonValue createStr(String string) {
        return createPandocNode("Str", Json.fromUntyped(string));
    }

    public static JsonValue loadJson(String[] args) throws FileNotFoundException {
        StringBuffer input = new StringBuffer();
        InputStreamReader reader;
        if (args.length > 0)
            reader = new FileReader(args[0]);
        else {
            reader = new InputStreamReader(System.in);
        }
        new BufferedReader(reader).lines().forEach(input::append);

        return Json.parse(input.toString());
    }

    public interface Callback {
        JsonValue invoke(String type, JsonValue value);
    }
}
