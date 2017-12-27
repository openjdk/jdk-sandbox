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

import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.management.remote.rest.PlatformRestAdapter;
import java.util.List;

/**
 * This class handles all the HTTP requests for the base URL
 * for REST adapter.
 */
public class MBeanServerCollectionResource implements RestResource {

    private final List<JmxRestAdapter> restAdapters;
    private final int pageSize = 5;

    public MBeanServerCollectionResource(List<JmxRestAdapter> adapters, HttpServer server) {
        this.restAdapters = adapters;
        server.createContext("/jmx/servers", this);
    }

    @Override
    public HttpResponse doGet(HttpExchange exchange) {
        try {
            JSONObject _links = HttpUtil.getPaginationLinks(exchange, restAdapters, pageSize);
            List<JmxRestAdapter> filteredList = HttpUtil.filterByPage(exchange, restAdapters, pageSize);
            if (filteredList == null) {
                return HttpResponse.OK;
            }

            final String path = PlatformRestAdapter.getDomain() +
                    exchange.getRequestURI().getPath().replaceAll("\\/$", "");

            JSONObject root = new JSONObject();
            if (_links != null && !_links.isEmpty()) {
                root.put("_links", _links);
            }

            root.put("mBeanServerCount", Integer.toString(restAdapters.size()));

            JSONArray list = new JSONArray();
            filteredList.stream().map((adapter) -> {
                JSONObject result = new JSONObject();
                result.put("name", adapter.getAlias());
                result.put("href", path + "/" + adapter.getAlias());
                return result;
            }).forEachOrdered((result) -> {
                list.add(result);
            });
            root.put("items", list);
            return new HttpResponse(200, root.toJsonString());
        } catch (Exception ex) {
            ex.printStackTrace();
            return new HttpResponse(400, HttpResponse.getErrorMessage(ex));
        }
    }

    @Override
    public HttpResponse doPut(HttpExchange exchange) {
        return null;
    }

    @Override
    public HttpResponse doPost(HttpExchange exchange) {
        return null;
    }

    @Override
    public HttpResponse doDelete(HttpExchange exchange) {
        return null;
    }

    @Override
    public HttpResponse doHead(HttpExchange exchange) {
        return null;
    }
}
