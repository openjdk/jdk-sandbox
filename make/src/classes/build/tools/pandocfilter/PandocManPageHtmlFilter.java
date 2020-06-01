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
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PandocManPageHtmlFilter {
    public interface Callback {
        JSONValue callback(String type, JSONValue value);
    }

    /**
     * Traverse a tree of pandoc format objects, calling callback on each
     * element, and replacing it if callback returns a new object.
     * <p>
     * Inspired by the walk method in
     * https://github.com/jgm/pandocfilters/blob/master/pandocfilters.py
     */

    private JSONValue traverse(JSONValue obj, Callback callback) {
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;

            JSONArray processed_array = new JSONArray();
            for (JSONValue elem : array) {
                if (elem instanceof JSONObject && elem.contains("t")) {
                    JSONValue replacement = callback.callback(elem.get("t").asString(), elem.contains("c") ? elem.get("c") : new JSONArray());
                    if (replacement == null) {
                        // no replacement object returned, use original
                        processed_array.add(traverse(elem, callback));
                    } else if (replacement instanceof JSONArray) {
                        // array of objects returned, splice all elements into array
                        JSONArray replacement_array = (JSONArray) replacement;
                        for (JSONValue repl_elem : replacement_array) {
                            processed_array.add(traverse(repl_elem, callback));
                        }
                    } else {
                        // replacement object given, traverse it
                        processed_array.add(traverse(replacement, callback));
                    }
                } else {
                    processed_array.add(traverse(elem, callback));
                }
            }
            return processed_array;
        } else if (obj instanceof JSONObject) {
            if (obj.contains("t")) {
                JSONValue replacement = callback.callback(obj.get("t").asString(), obj.contains("c") ? obj.get("c") : new JSONArray());
                if (replacement != null) {
                    return replacement;
                }
            }
            JSONObject obj_obj = (JSONObject) obj;
            var processed_obj = new JSONObject();
            for (String key : obj_obj.keys()) {
                processed_obj.put(key, traverse(obj_obj.get(key), callback));
            }
            return processed_obj;
        } else {
            return obj;
        }
    }

    private JSONValue PandocAtom(String type, JSONValue content) {
        if (content == null) {
            return new JSONObject(Map.of(
                    "t", new JSONString(type)));
        } else {
            return new JSONObject(Map.of(
                    "t", new JSONString(type),
                    "c", content));
        }
    }

    private JSONValue PandocAtom(String type) {
        return PandocAtom(type, null);
    }

    /*
     * Helper constructors to create pandoc format objects
     */
    private JSONValue Space() {
        return PandocAtom("Space");
    }

    private JSONValue Str(String string) {
        return PandocAtom("Str", new JSONString(string));
    }

    private JSONValue MetaInlines(JSONValue value) {
        return PandocAtom("MetaInlines", value);
    }

    private JSONValue change_title(String type, JSONValue value) {
        if (type.equals("MetaInlines")) {
            String subType = value.get(0).get("t").asString();
            String subContent = value.get(0).get("c").asString();
            if (subType.equals("Str")) {
                Pattern pattern = Pattern.compile("^([A-Z0-9]+)\\([0-9]+\\)$");
                Matcher matcher = pattern.matcher(subContent);
                if (matcher.find()) {
                    String commandName = matcher.group(1).toLowerCase();
                    return MetaInlines(new JSONArray(
                            Str("The"), Space(),
                            Str(commandName),
                            Space(), Str("Command")));
                }
            }
        }
        return null;
    }

    /**
     * Main function
     */
    public static void main(String[] args) throws FileNotFoundException {
        StringBuffer input = new StringBuffer();
        InputStreamReader reader;
        if (args.length > 0)
            reader = new FileReader(args[0]);
        else {
            reader = new InputStreamReader(System.in);
        }
        new BufferedReader(reader).lines().forEach(line -> input.append(line));

        JSONValue json = JSON.parse(input.toString());
        PandocManPageHtmlFilter filter = new PandocManPageHtmlFilter();

        JSONValue meta = json.get("meta");
        if (meta != null && meta instanceof JSONObject) {
            JSONObject metaobj = (JSONObject) meta;
            metaobj.remove("date");
            JSONValue title = meta.get("title");
            if (title != null) {
                Callback callback = new Callback() {
                    @Override
                    public JSONValue callback(String type, JSONValue value) {
                        return filter.change_title(type, value);
                    }
                };
                metaobj.put("title", filter.traverse(title, callback));
            }
        }

        System.out.println(json);
    }
}
