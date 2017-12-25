/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;

/**
 *
 * @author harsha
 */
public interface RestResource extends HttpHandler {
    @Override
    public default void handle (HttpExchange exchange) throws IOException {
        HttpResponse httpResponse = HttpResponse.METHOD_NOT_ALLOWED;
        switch (exchange.getRequestMethod()) {
            case "GET":
                httpResponse = doGet(exchange);
                break;
            case "POST":
                httpResponse = doPost(exchange);
                break;
            case "PUT":
                httpResponse = doPut(exchange);
                break;
            case "DELETE":
                httpResponse = doDelete(exchange);
                break;
            case "HEAD":
                httpResponse = doHead(exchange);
                break;
        }
        HttpUtil.sendResponse(exchange,httpResponse);
    }
    
    public HttpResponse doGet(HttpExchange exchange);
    public HttpResponse doPut(HttpExchange exchange);
    public HttpResponse doPost(HttpExchange exchange);
    public HttpResponse doDelete(HttpExchange exchange);
    public HttpResponse doHead(HttpExchange exchange);
}
