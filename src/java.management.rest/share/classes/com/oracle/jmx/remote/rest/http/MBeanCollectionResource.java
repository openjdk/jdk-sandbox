package com.oracle.jmx.remote.rest.http;

import com.oracle.jmx.remote.rest.json.JSONElement;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.mapper.JSONMapper;
import com.oracle.jmx.remote.rest.mapper.JSONMappingException;
import com.oracle.jmx.remote.rest.mapper.JSONMappingFactory;
import com.sun.net.httpserver.HttpExchange;

import javax.management.*;
import javax.management.remote.rest.PlatformRestAdapter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MBeanCollectionResource implements RestResource, NotificationListener {

    private List<ObjectName> allowedMbeans;
    private final MBeanServer mBeanServer;
    private final Map<String, MBeanResource> mBeanResourceMap = new ConcurrentHashMap<>();
    private static final int pageSize = 10;

    private boolean isMBeanAllowed(ObjectName objName) {
        try {
            MBeanInfo mInfo = mBeanServer.getMBeanInfo(objName);
            MBeanAttributeInfo[] attrsInfo = mInfo.getAttributes();
            for (MBeanAttributeInfo attrInfo : attrsInfo) {
                String type = attrInfo.getType();
                if (!JSONMappingFactory.INSTANCE.isTypeMapped(type)) {
                    return false;
                }
            }
            MBeanOperationInfo[] operations = mInfo.getOperations();
            for (MBeanOperationInfo opInfo : operations) {
                MBeanParameterInfo[] signature = opInfo.getSignature();
                for (MBeanParameterInfo sig : signature) {
                    if (!JSONMappingFactory.INSTANCE.isTypeMapped(sig.getType())) {
                        return false;
                    }
                }
                if (!JSONMappingFactory.INSTANCE.isTypeMapped(opInfo.getReturnType())) {
                    return false;
                }
            }
            return true;
        } catch (InstanceNotFoundException | IntrospectionException | ReflectionException | ClassNotFoundException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void introspectMBeanTypes(MBeanServer server) {
        if (allowedMbeans.isEmpty()) {
            Set<ObjectInstance> allMBeans = server.queryMBeans(null, null); // get all Mbeans
            allMBeans.stream().filter((objIns) -> (isMBeanAllowed(objIns.getObjectName())))
                    .forEachOrdered(objIns -> allowedMbeans.add(objIns.getObjectName()));
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        try {
            MBeanServerNotification mbs = (MBeanServerNotification) notification;
            if (MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(mbs.getType())) {
                ObjectName mBeanName = mbs.getMBeanName();
                if (isMBeanAllowed(mBeanName)) {
                    allowedMbeans.add(mBeanName);
                }
            } else if (MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(mbs.getType())) {
                if (allowedMbeans.contains(mbs.getMBeanName().toString())) {
                    allowedMbeans.remove(mbs.getMBeanName().toString());
                }
            }
        } catch (Exception e) {
        }
    }

    public MBeanCollectionResource(MBeanServer mBeanServer) {
        this.mBeanServer = mBeanServer;
        allowedMbeans = new ArrayList<>();
        introspectMBeanTypes(mBeanServer);
        allowedMbeans = new CopyOnWriteArrayList<>(allowedMbeans);
        allowedMbeans.forEach(objectName -> mBeanResourceMap.put(objectName.toString(),
                new MBeanResource(mBeanServer, objectName)));
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String pathPrefix = "^/?jmx/servers/[a-zA-Z0-9\\-\\.]+/mbeans";
        if (path.matches(pathPrefix + "/?$")) {
            RestResource.super.handle(exchange);
        } else if (path.matches(pathPrefix + "/[^/]+/?.*")) {
            // Extract mbean name
            // Forward the request to its corresponding rest resource
            Pattern mbeans = Pattern.compile(pathPrefix + "/");
            Matcher matcher = mbeans.matcher(path);

            if (matcher.find()) {
                String ss = path.substring(matcher.end());
                String mBeanName = ss;
                if (ss.indexOf('/') != -1) {
                    mBeanName = ss.substring(0, ss.indexOf('/'));
                }
                MBeanResource mBeanResource = mBeanResourceMap.get(mBeanName);
                if (mBeanResource == null) {
                    HttpUtil.sendResponse(exchange, new HttpResponse(404, "Not found"));
                    return;
                }
                mBeanResource.handle(exchange);
            }
        }
    }

    @Override
    public HttpResponse doGet(HttpExchange exchange) {
        // add links

        final String path = PlatformRestAdapter.getAuthority() + exchange.getRequestURI().getPath().replaceAll("\\/$", "");
        try {
            List<ObjectName> mbeans = allowedMbeans;
            Map<String, String> queryMap = HttpUtil.getGetRequestQueryMap(exchange);
            if(queryMap.containsKey("query")) {
                Set<ObjectName> queryMBeans = mBeanServer.queryNames(new ObjectName(queryMap.get("query")),null);
                queryMBeans.retainAll(allowedMbeans);
                mbeans = new ArrayList<>(queryMBeans);
            }

            JSONObject _links = HttpUtil.getPaginationLinks(exchange, mbeans, pageSize);
            List<ObjectName> filteredMBeans = HttpUtil.filterByPage(exchange, mbeans, pageSize);
            List<Map<String, String>> items = new ArrayList<>(filteredMBeans.size());
            filteredMBeans.forEach(objectName -> {
                Map<String, String> item = new LinkedHashMap<>(2);
                item.put("name", objectName.toString());
                String href = path + "/" + objectName.toString();
                href = HttpUtil.escapeUrl(href);
                item.put("href", href);
                items.add(item);
            });

            Map<String, String> properties = new HashMap<>();

            properties.put("mbeanCount", Integer.toString(mbeans.size()));

            JSONMapper typeMapper1 = JSONMappingFactory.INSTANCE.getTypeMapper(items);
            JSONMapper typeMapper2 = JSONMappingFactory.INSTANCE.getTypeMapper(properties);

            JSONElement linkElem = typeMapper1.toJsonValue(items);
            JSONElement propElem = typeMapper2.toJsonValue(properties);
            JSONObject jobj = new JSONObject();
            if(_links != null && !_links.isEmpty()) {
                jobj.put("_links",_links);
            }

            jobj.putAll((JSONObject) propElem);
            jobj.put("items", linkElem);

            return new HttpResponse(200, jobj.toJsonString());
        } catch (JSONMappingException e) {
            return new HttpResponse(500, "Internal server error");
        } catch (UnsupportedEncodingException e) {
            return HttpResponse.SERVER_ERROR;
        } catch (MalformedObjectNameException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Invalid query string");
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
