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

import build.tools.pandocfilter.json.JSON;
import build.tools.pandocfilter.json.JSONArray;
import build.tools.pandocfilter.json.JSONObject;
import build.tools.pandocfilter.json.JSONString;
import build.tools.pandocfilter.json.JSONValue;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Map;

public class PandocManPageTroffFilter {

    public interface Callback {
        JSONValue invoke(String type, JSONValue value);
    }

    /**
     * Traverse a tree of pandoc format objects, calling callback on each
     * element, and replacing it if callback returns a new object.
     * <p>
     * Inspired by the walk method in
     * https://github.com/jgm/pandocfilters/blob/master/pandocfilters.py
     */
    private JSONValue traverse(JSONValue obj, build.tools.pandocfilter.PandocManPageTroffFilter.Callback callback) {
        if (obj instanceof JSONArray) {
            JSONArray array = (JSONArray) obj;

            JSONArray processed_array = new JSONArray();
            for (JSONValue elem : array) {
                if (elem instanceof JSONObject && elem.contains("t")) {
                    JSONValue replacement = callback.invoke(elem.get("t").asString(), elem.contains("c") ? elem.get("c") : new JSONArray());
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
                JSONValue replacement = callback.invoke(obj.get("t").asString(), obj.contains("c") ? obj.get("c") : new JSONArray());
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

    private JSONValue Strong(JSONValue value) {
        return PandocAtom("Strong", value);
    }

    private JSONValue Header(JSONValue value) {
        return PandocAtom("Header", value);
    }

    /**
     * Callback to change all Str texts to upper case
     */
    private JSONValue uppercase(String type, JSONValue value) {
        if (type.equals("Str")) {
            return Str(value.asString().toUpperCase());
        }
        return null;
    }

    /**
     * Main callback function that performs our man page AST rewrites
     */
    private JSONValue manpage_filter(String type, JSONValue value) {
        // If it is a header, decrease the heading level by one, and
        // if it is a level 1 header, convert it to upper case.
        if (type.equals("Header")) {
            JSONArray array = value.asArray();
            int level = array.get(0).asInt();
            array.set(0, JSONValue.from(level - 1));
            if (value.asArray().get(0).asInt() == 1) {
                return Header(traverse(value, this::uppercase));
            }
        }

        // Man pages does not have superscript. We use it for footnotes, so
        // enclose in [...] for best representation.
        if (type.equals("Superscript")) {
            return new JSONArray(Str("["), value, Str("]"));
        }

        // If it is a link, put the link name in bold. If it is an external
        // link, put it in brackets. Otherwise, it is either an internal link
        // (like "#next-heading"), or a relative link to another man page
        // (like "java.html"), so remove it for man pages.
        if (type.equals("Link")) {
            JSONValue target = value.asArray().get(2).asArray().get(0);
            //var target = value[2][0];
            String targetStr = target.asString();
            if (targetStr.startsWith("https:") || targetStr.startsWith("http:")) {
                return new JSONArray(Strong(value.asArray().get(1)), Space(), Str("[" + targetStr + "]"));
            } else {
                return Strong(value.asArray().get(1));
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
        build.tools.pandocfilter.PandocManPageTroffFilter filter = new build.tools.pandocfilter.PandocManPageTroffFilter();

        JSONValue transformed_json = filter.traverse(json, filter::manpage_filter);

        System.out.println(transformed_json);
    }
}
