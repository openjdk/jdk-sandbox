/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.jmx.remote.rest.http;

import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.ReflectionException;
import com.oracle.jmx.remote.rest.json.JSONElement;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.json.JSONPrimitive;
import com.oracle.jmx.remote.rest.mapper.JSONDataException;
import com.oracle.jmx.remote.rest.mapper.JSONMappingException;
import com.oracle.jmx.remote.rest.json.parser.ParseException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;

/**
 * @author harsha
 */
public class HttpResponse {

    public static final HttpResponse OK = new HttpResponse(HttpURLConnection.HTTP_OK, "Success");
    public static final HttpResponse SERVER_ERROR = new HttpResponse(HttpURLConnection.HTTP_INTERNAL_ERROR, "Internal server error");
    public static final HttpResponse METHOD_NOT_ALLOWED = new HttpResponse(HttpURLConnection.HTTP_BAD_METHOD, "Method not allowed");
    public static final HttpResponse BAD_REQUEST = new HttpResponse(HttpURLConnection.HTTP_BAD_REQUEST, "Bad request");
    public static final HttpResponse REQUEST_NOT_FOUND = new HttpResponse(HttpURLConnection.HTTP_NOT_FOUND, "Request not found");

    private final int code;
    private final String message;
    private final String body;

    public HttpResponse(int code, String message) {
        this(code, message, "");
    }

    public HttpResponse(String message) {
        this(200,message,"");
    }

    public HttpResponse(int code, String message, String detail) {
        this.code = code;
        this.message = message;

        if (code != HttpURLConnection.HTTP_OK) {
            JSONObject jobj = new JSONObject();
            jobj.put("status", new JSONPrimitive(code));
            jobj.put("message", new JSONPrimitive(message));
            if (detail != null && !detail.isEmpty()) {
                jobj.put("details", new JSONPrimitive(detail));
            }
            this.body = jobj.toJsonString();
        } else {
            this.body = message;
        }
    }

    public HttpResponse(HttpResponse response, String detail) {
        this(response.code, response.message, detail);
    }

    public int getCode() {
        return code;
    }

    public String getBody() {
        return body;
    }

    static int getHttpErrorCode(Exception ex) {
        if (ex instanceof JSONDataException
                || ex instanceof ParseException || ex instanceof IllegalArgumentException) {
            return HttpURLConnection.HTTP_BAD_REQUEST;
        } else if (ex instanceof JSONMappingException) {
            return HttpURLConnection.HTTP_INTERNAL_ERROR;
        } else if (ex instanceof AttributeNotFoundException
                || ex instanceof InstanceNotFoundException
                || ex instanceof ReflectionException) {
            return HttpURLConnection.HTTP_NOT_FOUND;
        }
        return HttpURLConnection.HTTP_BAD_REQUEST;
    }

    public static String getErrorMessage(Exception ex) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        ex.printStackTrace(pw);
        return sw.toString();
    }

    static JSONObject getJsonObject(int code, JSONElement request, JSONElement response) {
        JSONObject jobj = new JSONObject();
        jobj.put("status", new JSONPrimitive(code));
        jobj.put("request", request);
        jobj.put("response", response);
        return jobj;
    }
}
