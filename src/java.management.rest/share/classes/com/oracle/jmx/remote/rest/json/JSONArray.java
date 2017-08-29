/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.json;

import java.util.ArrayList;
import java.util.Iterator;

/**
 * @author harsha
 */
public class JSONArray extends ArrayList<JSONElement> implements JSONElement {

    private static final long serialVersionUID = -506975558678956426L;

    @Override
    public String toJsonString() {
        if (isEmpty()) {
            return null;
        }
        StringBuilder sbuild = new StringBuilder();
        sbuild.append("[");
        Iterator<JSONElement> itr = iterator();
        while (itr.hasNext()) {
            JSONElement val = itr.next();
            if (val != null)
                sbuild.append(val.toJsonString()).append(", ");
            else
                sbuild.append("null").append(", ");
        }

        sbuild.deleteCharAt(sbuild.lastIndexOf(","));
        sbuild.append("]");
        return sbuild.toString();
    }
}
