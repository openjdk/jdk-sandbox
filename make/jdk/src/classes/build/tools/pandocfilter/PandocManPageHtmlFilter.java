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

import build.tools.json.JsonArray;
import build.tools.json.JsonObject;
import build.tools.json.JsonString;
import build.tools.json.JsonValue;

import java.io.FileNotFoundException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PandocManPageHtmlFilter extends PandocFilter {

    private JsonValue MetaInlines(JsonValue value) {
        return createPandocNode("MetaInlines", value);
    }

    private JsonValue changeTitle(String type, JsonValue value) {
        if (type.equals("MetaInlines") && value instanceof JsonArray ja
                && ja.get(0) instanceof JsonObject jo
                && jo.get("t") instanceof JsonString t
                && jo.get("c") instanceof JsonString c) {
            String subType = t.value();
            String subContent = c.value();
            if (subType.equals("Str")) {
                Pattern pattern = Pattern.compile("^([A-Z0-9]+)\\([0-9]+\\)$");
                Matcher matcher = pattern.matcher(subContent);
                if (matcher.find()) {
                    String commandName = matcher.group(1).toLowerCase();
                    return MetaInlines(JsonArray.ofValues(
                            createStr("The"), createSpace(),
                            createStr(commandName),
                            createSpace(), createStr("Command")));
                }
            }
        }
        return null;
    }

    /**
     * Main function
     */
    public static void main(String[] args) throws FileNotFoundException {
        JsonValue json = loadJson(args);
        if (json instanceof JsonObject jo) {
            JsonValue out = json;
            PandocManPageHtmlFilter filter = new PandocManPageHtmlFilter();
            JsonValue meta = jo.get("meta");
            if (meta != null && meta instanceof JsonObject jobj) {
                JsonObject.Builder bldr = new JsonObject.Builder(jo);
                bldr.remove("date");
                JsonValue title = jobj.get("title");
                if (title != null) {
                    bldr.put("title", filter.traverse(title, filter::changeTitle, true));
                }
                out = bldr.build();
            }
            System.out.println(out);
        } else {
            throw new RuntimeException("Json format incorrect");
        }
    }
}
