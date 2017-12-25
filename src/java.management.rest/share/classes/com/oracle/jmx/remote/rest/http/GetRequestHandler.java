/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.http;

import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.json.JSONPrimitive;
import com.oracle.jmx.remote.rest.mapper.JSONMappingException;
import com.oracle.jmx.remote.rest.mapper.JSONMappingFactory;

import javax.management.*;
import java.net.HttpURLConnection;
import java.util.*;

/**
 * @author harsha
 */
public class GetRequestHandler {

    private final MBeanServer mbeanServer;
    private final List<String> allowedMBeans;

    public GetRequestHandler(MBeanServer mServer, List<String> allowedMBeans) {
        this.mbeanServer = mServer;
        this.allowedMBeans = allowedMBeans;
    }

    public synchronized JSONObject handle(String resource, String query) {

        System.out.println("Resource = " + resource + ", Body = " + query);

        try {
            if ((query == null || query.isEmpty()) && (resource == null || resource.isEmpty())) {
                return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK,
                        new JSONPrimitive(resource + (query == null ? "" : query)),
                        new JSONPrimitive("Nothing to see here.. move along"));
            }
            if (query != null && resource.isEmpty()) { // Handle default domain
                String[] tokens = query.split(Tokens.FORWARD_SLASH);
                if (tokens.length == 1 && tokens[0].equalsIgnoreCase(Tokens.DEFAULT_DOMAIN)) {
                    // Get default domain
                    return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK,
                            new JSONPrimitive(resource + (query == null ? "" : query)),
                            new JSONPrimitive(mbeanServer.getDefaultDomain()));

                } else { // Get mbeans belonging to a domain

                }
            } else {
                // handle string escaping for '/'
                String[] tokens = resource.split("/");
                switch (tokens[0]) {
                    case Tokens.DOMAINS:
                        String[] domains = mbeanServer.getDomains();
                        JSONArray jarr = new JSONArray();
                        Arrays.stream(domains).forEach(a -> jarr.add(new JSONPrimitive(a)));
                        return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK,
                                new JSONPrimitive(resource + (query == null ? "" : query)),
                                jarr);
                    case Tokens.MBEANS:

                        //Set<ObjectInstance> mbeans = mbeanServer.queryMBeans(null, null);
                        jarr = new JSONArray();
                        //mbeans.stream()
                        //.map(objIns -> objIns.getObjectName().toString())
                        allowedMBeans.stream().forEach(a -> jarr.add(new JSONPrimitive(a)));
                        return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK,
                                new JSONPrimitive(resource + (query == null ? "" : query)),
                                jarr);
                    default:
                        if (tokens.length == 2) {
                            if (!allowedMBeans.contains(tokens[0])) {
                                throw new InstanceNotFoundException("Invalid MBean");
                            }
                            ObjectName mbean = ObjectName.getInstance(tokens[0]);
                            JSONObject jsonObject = getJSONObject(readAttributes(mbeanServer, mbean, tokens[1]));
                            return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK,
                                    new JSONPrimitive(resource + (query == null ? "" : query)),
                                    jsonObject);
                        } else if (tokens.length == 1 && query != null && !query.isEmpty()) {
                            if (!allowedMBeans.contains(tokens[0])) {
                                throw new InstanceNotFoundException("Invalid MBean");
                            }
                            ObjectName mbean = ObjectName.getInstance(tokens[0]);
                            if (query.startsWith(Tokens.ATTRS)) {
                                String attrs = query.split(Tokens.EQUALS)[1];
                                JSONObject jsonObject = getJSONObject(readAttributes(mbeanServer, mbean, attrs));
                                return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK,
                                        new JSONPrimitive(resource + (query == null ? "" : query)),
                                        jsonObject);
                            }
                        } else if (tokens.length == 1 && (query == null || query.isEmpty())) {
                            if (!allowedMBeans.contains(tokens[0])) {
                                throw new InstanceNotFoundException("Invalid MBean");
                            }

                            // We get MBeanInfo
                            ObjectName mbeanObj = ObjectName.getInstance(tokens[0]);
                            return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK,
                                    new JSONPrimitive(5), new JSONPrimitive(5));
                        }
                        System.out.println("Unrecognized token : " + tokens[0]);
                }
            }
        } catch (MBeanException | JSONMappingException | IntrospectionException ex) {
            return HttpResponse.getJsonObject(HttpResponse.getHttpErrorCode(ex),
                    new JSONPrimitive(resource + (query == null ? "" : query)),
                    new JSONPrimitive("Invalid Mbean attribute"));

        } catch (AttributeNotFoundException ex) {
            return HttpResponse.getJsonObject(HttpResponse.getHttpErrorCode(ex),
                    new JSONPrimitive(resource + (query == null ? "" : query)),
                    new JSONPrimitive(ex.getMessage()));
        } catch (InstanceNotFoundException | ReflectionException | MalformedObjectNameException ex) {
            return HttpResponse.getJsonObject(HttpResponse.getHttpErrorCode(ex),
                    new JSONPrimitive(resource + (query == null ? "" : query)),
                    new JSONPrimitive("Invalid Mbean"));
        }
        return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK,
                new JSONPrimitive(resource + (query == null ? "" : query)),
                new JSONPrimitive("Nothing to see here.. move along"));
    }

    private Map<String, Object> readAttributes(MBeanServer mbeanServer,
                                               ObjectName objName, String requestStr)
            throws InstanceNotFoundException, IntrospectionException,
            ReflectionException, MBeanException, AttributeNotFoundException {
        requestStr = requestStr.trim();
        Map<String, Object> result = new HashMap<>();

        String[] attrs = Arrays.stream(requestStr.split(Tokens.COMMA))
                .map(String::trim)
                .toArray(String[]::new);

        if (attrs.length == 1) {
            result.put(attrs[0], mbeanServer.getAttribute(objName, attrs[0]));
        } else {
            AttributeList attrVals = mbeanServer.getAttributes(objName, attrs);
            if (attrVals.size() != attrs.length) {
                List<String> missingAttrs = new ArrayList<>(Arrays.asList(attrs));
                for (Attribute a : attrVals.asList()) {
                    missingAttrs.remove(a.getName());
                    result.put(a.getName(), a.getValue());
                }
                for (String attr : missingAttrs) {
                    result.put(attr, "< Error: No such attribute >");
                }
            } else {
                attrVals.asList().forEach((a) -> {
                    result.put(a.getName(), a.getValue());
                });
            }
        }

        return result;
    }

    private JSONObject getJSONObject(Map<String, Object> attributeMap) throws JSONMappingException {
        JSONObject jobject = new JSONObject();
        JSONMappingFactory mappingFactory = JSONMappingFactory.INSTANCE;
        for (String key : attributeMap.keySet()) {
            Object attrVal = attributeMap.get(key);
            if (attrVal == null) {
                jobject.put(key, new JSONPrimitive());
            } else if (mappingFactory.getTypeMapper(attrVal) != null) {
                jobject.put(key, mappingFactory.getTypeMapper(attrVal).toJsonValue(attrVal));
            }
        }
        return jobject;
    }

    private interface Tokens {

        public static final String DOMAINS = "domains";
        public static final String MBEANS = "mbeans";
        public static final String ATTRS = "attributes";
        public static final String DOMAIN = "domain";
        public static final String DEFAULT_DOMAIN = "domain=default";
        public static final String ALL = "all";
        public static final String EQUALS = "=";
        public static final String COMMA = ",";
        public static final String FORWARD_SLASH = "/";
    }
}
