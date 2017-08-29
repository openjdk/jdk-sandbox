/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.http;

import javax.management.*;
import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.json.JSONPrimitive;
import com.oracle.jmx.remote.rest.mapper.JSONMappingException;
import com.oracle.jmx.remote.rest.mapper.JSONMappingFactory;
import java.net.HttpURLConnection;
import java.util.*;

import static javax.management.MBeanOperationInfo.*;

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
                                    new JSONPrimitive(resource + (query == null ? "" : query)),
                                    getMBeanInfo(mbeanServer, mbeanObj));
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

    private JSONObject getMBeanInfo(MBeanServer mbeanServer, ObjectName mbean) throws InstanceNotFoundException, IntrospectionException, ReflectionException {
        JSONObject jobj = new JSONObject();
        MBeanInfo mBeanInfo = mbeanServer.getMBeanInfo(mbean);
        if (mBeanInfo == null) {
            return jobj;
        }
        jobj.put("description", mBeanInfo.getDescription());

        // Populate Attribute Info
        MBeanAttributeInfo[] attributes = mBeanInfo.getAttributes();
        JSONArray jarr = new JSONArray();
        for (MBeanAttributeInfo attr : attributes) {
            String access;
            if (attr.isReadable()) {
                if (attr.isWritable()) {
                    access = "read/write";
                } else {
                    access = "read-only";
                }
            } else if (attr.isWritable()) {
                access = "write-only";
            } else {
                access = "no-access";
            }
            JSONObject jobj1 = new JSONObject();
            jobj1.put("description", attr.getDescription());
            jobj1.put("name", attr.getName());
            jobj1.put("type", attr.getType());
            jobj1.put("access", access);
            jobj1.put("descriptor", getDescriptorJSON(attr.getDescriptor()));
            jarr.add(jobj1);
        }
        jobj.put("attributeInfo", jarr);

        // Add constructor Info
        MBeanConstructorInfo[] constructorInfo = mBeanInfo.getConstructors();
        jarr = new JSONArray();
        for (MBeanConstructorInfo constructor : constructorInfo) {
            JSONObject jobj1 = new JSONObject();
            jobj1.put("description", constructor.getDescription());
            jobj1.put("name", constructor.getName());
            JSONArray jarr1 = new JSONArray();
            for (MBeanParameterInfo paramInfo : constructor.getSignature()) {
                jarr1.add(getParamJSON(paramInfo));
            }
            jobj1.put("signature", jarr1);
            if (constructor.getDescriptor().getFieldNames().length > 1) {
                jobj1.put("descriptor", getDescriptorJSON(constructor.getDescriptor()));
            }
            jarr.add(jobj1);
        }
        jobj.put("constructorInfo", jarr);

        MBeanOperationInfo[] opInfo = mBeanInfo.getOperations();
        jarr = new JSONArray();

        for (MBeanOperationInfo op : opInfo) {
            String impactString;
            switch (op.getImpact()) {
                case ACTION:
                    impactString = "action";
                    break;
                case ACTION_INFO:
                    impactString = "action/info";
                    break;
                case INFO:
                    impactString = "info";
                    break;
                case UNKNOWN:
                    impactString = "unknown";
                    break;
                default:
                    impactString = "(" + op.getImpact() + ")";
            }

            JSONObject jobj1 = new JSONObject();
            jobj1.put("description", op.getDescription());
            jobj1.put("name", op.getName());
            jobj1.put("returnType", op.getReturnType());
            JSONArray jarr1 = new JSONArray();
            for (MBeanParameterInfo paramInfo : op.getSignature()) {
                jarr1.add(getParamJSON(paramInfo));
            }
            jobj1.put("signature", jarr1);
            jobj1.put("impact", impactString);
            if (op.getDescriptor().getFieldNames().length > 1) {
                jobj1.put("descriptor", getDescriptorJSON(op.getDescriptor()));
            }
            jarr.add(jobj1);
        }
        jobj.put("operationInfo", jarr);

        MBeanNotificationInfo[] notifications = mBeanInfo.getNotifications();
        jarr = new JSONArray();

        for (MBeanNotificationInfo notification : notifications) {

            JSONObject jobj1 = new JSONObject();
            jobj1.put("description", notification.getDescription());
            jobj1.put("name", notification.getName());

            JSONArray jarr1 = new JSONArray();
            for (String notifType : notification.getNotifTypes()) {
                jarr1.add(new JSONPrimitive(notifType));
            }
            jobj1.put("notifTypes", jarr1);
            if (notification.getDescriptor().getFieldNames().length > 1) {
                jobj1.put("descriptor", getDescriptorJSON(notification.getDescriptor()));
            }
            jarr.add(jobj1);
        }
        jobj.put("notificationInfo", jarr);

        jobj.put("descriptor", getDescriptorJSON(mBeanInfo.getDescriptor()));
        return jobj;
    }

    private JSONObject getParamJSON(MBeanParameterInfo mParamInfo) {
        JSONObject jobj1 = new JSONObject();
        if (mParamInfo.getDescription() != null && !mParamInfo.getDescription().isEmpty()) {
            jobj1.put("description", mParamInfo.getDescription());
        }
        jobj1.put("name", mParamInfo.getName());
        jobj1.put("type", mParamInfo.getType());
        if (mParamInfo.getDescriptor() != null && mParamInfo.getDescriptor().getFieldNames().length > 1) {
            jobj1.put("descriptor", getDescriptorJSON(mParamInfo.getDescriptor()));
        }
        return jobj1;
    }

    private JSONObject getDescriptorJSON(Descriptor descriptor) {
        JSONObject jobj2 = new JSONObject();
        try {
            String[] descNames = descriptor.getFieldNames();
            for (String descName : descNames) {
                Object fieldValue = descriptor.getFieldValue(descName);
                jobj2.put(descName, fieldValue != null ? fieldValue.toString() : null);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
        return jobj2;
    }

    private Map<String, Object> readAttributes(MBeanServer mbeanServer,
                                               ObjectName objName, String requestStr)
            throws InstanceNotFoundException, IntrospectionException,
            ReflectionException, MBeanException, AttributeNotFoundException {
        requestStr = requestStr.trim();
        Map<String, Object> result = new HashMap<>();
        if (requestStr.trim().equalsIgnoreCase("all")) {
            MBeanInfo mInfo = mbeanServer.getMBeanInfo(objName);
            String[] attrs = Arrays.stream(mInfo.getAttributes())
                    .map(MBeanAttributeInfo::getName)
                    .toArray(String[]::new);
            AttributeList attrVals = mbeanServer.getAttributes(objName, attrs);
            if (attrVals.size() != attrs.length) {
                List<String> missingAttrs = new ArrayList<>(Arrays.asList(attrs));
                for (Attribute a : attrVals.asList()) {
                    missingAttrs.remove(a.getName());
                    result.put(a.getName(), a.getValue());
                }
                for (String attr : missingAttrs) {
                    try {
                        Object attribute = mbeanServer.getAttribute(objName, attr);
                    } catch (RuntimeException ex) {
                        if (ex.getCause() instanceof UnsupportedOperationException) {
                            result.put(attr, "< Attribute not supported >");
                        } else if (ex.getCause() instanceof IllegalArgumentException) {
                            result.put(attr, "< Invalid attributes >");
                        }
                        continue;
                    }
                    result.put(attr, "< Error: No such attribute >");
                }
            } else {
                attrVals.asList().forEach((a) -> {
                    result.put(a.getName(), a.getValue());
                });
            }
        } else {
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
