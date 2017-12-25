/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
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
    private final String detail;

    public HttpResponse(int code, String message) {
        this(code, message, "");
    }

    public HttpResponse(int code, String message, String detail) {
        this.code = code;
        this.message = message;
        this.detail = detail;
    }

    public HttpResponse(HttpResponse response, String detail) {
        this.code = response.code;
        this.message = response.message;
        this.detail = detail;
    }

    public int getCode() {
        return code;
    }

    public String getResponse() {
        if(code != HttpURLConnection.HTTP_OK) {
            JSONObject jobj = new JSONObject();
            jobj.put("status",new JSONPrimitive(code));
            jobj.put("message",new JSONPrimitive(message));
            if(detail != null && !detail.isEmpty()) {
                jobj.put("details", new JSONPrimitive(detail));
            }
            return jobj.toJsonString();
        }
        return message;
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
