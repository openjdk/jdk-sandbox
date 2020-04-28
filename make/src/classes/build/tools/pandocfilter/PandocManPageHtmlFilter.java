/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import build.tools.pandocfilter.json.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;

public class PandocManPageHtmlFilter {

    /**
     * Traverse a tree of pandoc format objects, calling callback on each
     * element, and replacing it if callback returns a new object.
     * <p>
     * Inspired by the walk method in
     * https://github.com/jgm/pandocfilters/blob/master/pandocfilters.py
     */

    private static JSONValue callback(String type, JSONValue value) {
        return null;
    }

    private static JSONValue traverse(JSONValue obj, Object callback2) {
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;

            JSONArray processed_array = new JSONArray();
            for (JSONValue elem : array) {
                if (elem instanceof JSONObject && elem.contains("t")) {
                    JSONValue replacement = callback(elem.get("t").asString(), elem.contains("c") ? elem.get("c") : new JSONArray());
                    if (replacement == null) {
                        // no replacement object returned, use original
                        processed_array.add(traverse(elem, callback2));
                    } else if (replacement instanceof JSONArray) {
                        // array of objects returned, splice all elements into array
                        JSONArray replacement_array = (JSONArray) replacement;
                        for (JSONValue repl_elem : replacement_array) {
                            processed_array.add(traverse(repl_elem, callback2));
                        }
                    } else {
                        // replacement object given, traverse it
                        processed_array.add(traverse(replacement, callback2));
                    }
                } else {
                    processed_array.add(traverse(elem, callback2));
                }
            }
            return processed_array;
        } else if (obj instanceof JSONObject) {
            if (obj.contains("t")) {
                JSONValue replacement = callback(obj.get("t").asString(), obj.contains("c") ? obj.get("c") : new JSONArray());
                if (replacement != null) {
                    return replacement;
                }
            }
            JSONObject obj_obj = (JSONObject) obj;
            var processed_obj = new JSONObject();
            for (String key : obj_obj.keys()) {
                processed_obj.put(key, traverse(obj_obj.get(key), callback2));
            }
            return processed_obj;
        } else {
            return obj;
        }
    }

    /*
     * Helper constructors to create pandoc format objects
     */
    private Object Space() {
        return new JSONObject(Map.of(
                "t", new JSONString("Space")));
    }

    private Object Str(JSONValue value) {
        return new JSONObject(Map.of(
                "t", new JSONString("Str"),
                "c", value));
    }

    private Object MetaInlines(JSONValue value) {
        return new JSONObject(Map.of(
                "t", new JSONString("'MetaInlines'"),
                "c", value));
    }

    private JSONValue change_title(String type, JSONValue value) {
        if (type.equals("MetaInlines")) {
            if (value.get(0).get("t").asString().equals("Str")) {
            /*
            var match = value[0].c.match(/^([A-Z0-9]+)\([0-9]+\)$/);
            if (match) {
                return MetaInlines([
                        Str("The"), Space(),
			Str(match[1].toLowerCase()),
			Space(), Str("Command")
		    ]);
            }
            */
            }
        }
        return null;
    }

    /**
     * Main function
     */
    public static void main(String[] args) {
        StringBuffer input = new StringBuffer();
        new BufferedReader(new InputStreamReader(System.in)).lines().forEach(line -> input.append(line));

        JSONValue json = JSON.parse(input.toString());

        JSONValue meta = json.get("meta");
        if (meta != null && meta instanceof JSONObject) {
            JSONObject metaobj = (JSONObject) meta;
            metaobj.remove("date");
            JSONValue title = meta.get("title");
            if (title != null) {
//            metaobj.put(title, traverse(meta.title, change_title));
                metaobj.put("title", traverse(title, null));
            }
        }

        System.out.println(json);

    }
}
