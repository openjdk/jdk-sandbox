/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.json;

import java.util.LinkedHashMap;

/**
 * @author harsha
 */
public class JSONObject extends LinkedHashMap<String, JSONElement> implements JSONElement {

    private static final long serialVersionUID = -9148596129640441014L;

    public JSONElement put(String key, String value) {
        return super.put(key, new JSONPrimitive(value)); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public String toJsonString() {
        if (isEmpty()) {
            return null;
        }

        StringBuilder sbuild = new StringBuilder();
        sbuild.append("{");
        keySet().forEach((s) -> {
            sbuild.append("\"").append(s).append("\"").append(": ").
                    append((get(s) != null) ? get(s).toJsonString() : "null").append(",");
        });

        sbuild.deleteCharAt(sbuild.lastIndexOf(","));
        sbuild.append("}");
        return sbuild.toString();
    }
}
