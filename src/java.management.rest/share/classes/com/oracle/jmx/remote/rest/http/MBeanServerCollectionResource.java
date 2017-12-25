package com.oracle.jmx.remote.rest.http;

import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerDelegateMBean;
import javax.management.remote.rest.PlatformRestAdapter;
import java.util.List;

public class MBeanServerCollectionResource implements RestResource {

    private final List<JmxRestAdapter> restAdapters;
    private final int pageSize = 5;

    public MBeanServerCollectionResource(List<JmxRestAdapter> adapters, HttpServer server) {
        this.restAdapters = adapters;
        server.createContext("/jmx/servers", this);
    }

    private String getMBeanServerID(MBeanServer server) {
        MBeanServerDelegateMBean mbean = JMX.newMBeanProxy(server,
                MBeanServerDelegate.DELEGATE_NAME, MBeanServerDelegateMBean.class);
        return mbean.getMBeanServerId();
    }

    @Override
    public HttpResponse doGet(HttpExchange exchange) {
        try {
            JSONObject _links = HttpUtil.getPaginationLinks(exchange, restAdapters, pageSize);
            List<JmxRestAdapter> filteredList = HttpUtil.filterByPage(exchange, restAdapters, pageSize);
            if (filteredList == null) {
                return new HttpResponse(HttpResponse.BAD_REQUEST, "Invald query parameters");
            }

            final String path = PlatformRestAdapter.getAuthority() + exchange.getRequestURI().getPath().replaceAll("\\/$", "");

            JSONObject root = new JSONObject();
            if(_links != null && !_links.isEmpty()) {
                root.put("_links",_links);
            }

            root.put("mBeanServerCount",Integer.toString(restAdapters.size()));

            JSONArray list = new JSONArray();
            filteredList.stream().map((adapter) -> {
                JSONObject result = new JSONObject();
                result.put("name", adapter.getAlias());
                result.put("href", path +"/" +adapter.getAlias());
                return result;
            }).forEachOrdered((result) -> {
                list.add(result);
            });
            root.put("items",list);
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
