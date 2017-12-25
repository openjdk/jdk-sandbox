package com.oracle.jmx.remote.rest.http;

import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONElement;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.json.JSONPrimitive;
import com.oracle.jmx.remote.rest.mapper.JSONMapper;
import com.oracle.jmx.remote.rest.mapper.JSONMappingException;
import com.oracle.jmx.remote.rest.mapper.JSONMappingFactory;
import com.sun.net.httpserver.HttpExchange;

import javax.management.*;
import javax.management.remote.rest.PlatformRestAdapter;
import java.io.IOException;
import java.util.*;

import static javax.management.MBeanOperationInfo.*;

public class MBeanResource implements RestResource {

    private final ObjectName objectName;
    private final MBeanServer mBeanServer;

    public MBeanResource(MBeanServer mBeanServer, ObjectName objectName) {
        this.mBeanServer = mBeanServer;
        this.objectName = objectName;
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
            jobj1.put("name", attr.getName());
            jobj1.put("type", attr.getType());
            jobj1.put("access", access);
            jobj1.put("description", attr.getDescription());
            jobj1.put("descriptor", getDescriptorJSON(attr.getDescriptor()));
            jarr.add(jobj1);
        }
        jobj.put("attributeInfo", jarr);

        // Add constructor Info
        MBeanConstructorInfo[] constructorInfo = mBeanInfo.getConstructors();
        jarr = new JSONArray();
        for (MBeanConstructorInfo constructor : constructorInfo) {
            JSONObject jobj1 = new JSONObject();
            jobj1.put("name", constructor.getName());
            JSONArray jarr1 = new JSONArray();
            for (MBeanParameterInfo paramInfo : constructor.getSignature()) {
                jarr1.add(getParamJSON(paramInfo));
            }
            jobj1.put("signature", jarr1);
            jobj1.put("description", constructor.getDescription());
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

            jobj1.put("name", op.getName());
            JSONArray jarr1 = new JSONArray();
            for (MBeanParameterInfo paramInfo : op.getSignature()) {
                jarr1.add(getParamJSON(paramInfo));
            }
            jobj1.put("signature", jarr1);
            jobj1.put("returnType", op.getReturnType());
            jobj1.put("impact", impactString);
            jobj1.put("description", op.getDescription());
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

            jobj1.put("name", notification.getName());
            JSONArray jarr1 = new JSONArray();
            for (String notifType : notification.getNotifTypes()) {
                jarr1.add(new JSONPrimitive(notifType));
            }
            jobj1.put("notifTypes", jarr1);
            jobj1.put("description", notification.getDescription());
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

    private Map<String, Object> getAllAttributes() throws IntrospectionException,
            InstanceNotFoundException, ReflectionException, AttributeNotFoundException, MBeanException {
        Map<String, Object> result = new HashMap<>();
        MBeanInfo mInfo = mBeanServer.getMBeanInfo(objectName);
        String[] attrs = Arrays.stream(mInfo.getAttributes())
                .map(MBeanAttributeInfo::getName)
                .toArray(String[]::new);
        AttributeList attrVals = mBeanServer.getAttributes(objectName, attrs);
        if (attrVals.size() != attrs.length) {
            List<String> missingAttrs = new ArrayList<>(Arrays.asList(attrs));
            for (Attribute a : attrVals.asList()) {
                missingAttrs.remove(a.getName());
                result.put(a.getName(), a.getValue());
            }
            for (String attr : missingAttrs) {
                try {
                    Object attribute = mBeanServer.getAttribute(objectName, attr);
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
        return result;
    }

    private HttpResponse doMBeanInfo(HttpExchange exchange) {
        try {
            JSONObject mBeanInfo = getMBeanInfo(mBeanServer, objectName);
            return new HttpResponse(200, mBeanInfo.toJsonString());
        } catch (RuntimeOperationsException | IntrospectionException | ReflectionException e) {
            return new HttpResponse(HttpResponse.SERVER_ERROR, HttpResponse.getErrorMessage(e));
        } catch (InstanceNotFoundException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Specified MBean does not exist");
        } catch (Exception e) {
            return new HttpResponse(HttpResponse.SERVER_ERROR, HttpResponse.getErrorMessage(e));
        }
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.matches("^\\/?jmx\\/servers\\/[a-zA-Z0-9\\-\\.]+\\/mbeans\\/[^\\/]+\\/?$") ||
                path.matches("^\\/?jmx\\/servers\\/[a-zA-Z0-9\\-\\.]+\\/mbeans\\/[^\\/]+\\/info$")) {
            RestResource.super.handle(exchange);
        } else {
            HttpUtil.sendResponse(exchange, new HttpResponse(404, "Not found"));
        }
    }

    @Override
    public HttpResponse doGet(HttpExchange exchange) {
        if(exchange.getRequestURI().getPath().endsWith("info")) {
            return doMBeanInfo(exchange);
        }
        String path = PlatformRestAdapter.getAuthority() + exchange.getRequestURI().getPath().replaceAll("\\/$", "");
        String info = path + "/info";

        try {
            Map<String, Object> allAttributes = getAllAttributes();
            Map<String, String> _links = new LinkedHashMap<>();
            _links.put("info",HttpUtil.escapeUrl(info));

            MBeanOperationInfo[] opInfo = mBeanServer.getMBeanInfo(objectName).getOperations();
            JSONArray jarr = new JSONArray();
            for (MBeanOperationInfo op : opInfo) {
                JSONObject jobj1 = new JSONObject();
                JSONArray jarr1 = new JSONArray();
                jobj1.put("name", op.getName());
                jobj1.put("href", HttpUtil.escapeUrl(path + "/" + op.getName()));
                jobj1.put("method", "POST");
                for (MBeanParameterInfo paramInfo : op.getSignature()) {
                    JSONObject jobj = new JSONObject();
                    jobj.put("name", paramInfo.getName());
                    jobj.put("type", paramInfo.getType());
                    jarr1.add(jobj);
                }
                jobj1.put("arguments", jarr1);
                jobj1.put("returnType", op.getReturnType());
                jarr.add(jobj1);
            }

            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(allAttributes);
            if(typeMapper != null) {
                JSONElement jsonElement1 = typeMapper.toJsonValue(allAttributes);
                JSONElement jsonElement2 = typeMapper.toJsonValue(_links);

                JSONObject jobj = new JSONObject();
                jobj.put("attributes",jsonElement1);
                jobj.put("operations",jarr);
                jobj.put("_links",jsonElement2);
                return new HttpResponse(200, jobj.toJsonString());
            } else {
                return new HttpResponse(HttpResponse.SERVER_ERROR, "Unable to find JSONMapper");
            }
        } catch (RuntimeOperationsException | IntrospectionException | ReflectionException | JSONMappingException e) {
            return new HttpResponse(HttpResponse.SERVER_ERROR, HttpResponse.getErrorMessage(e));
        } catch (InstanceNotFoundException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Specified MBean does not exist");
        } catch (AttributeNotFoundException e) {
            return new HttpResponse(HttpResponse.BAD_REQUEST, "Specified Attribute does not exist");
        } catch (MBeanException e) {
            Throwable cause = e.getCause();
            return new HttpResponse(HttpResponse.SERVER_ERROR, HttpResponse.getErrorMessage(e));
        } catch (Exception e) {
            return new HttpResponse(HttpResponse.SERVER_ERROR, HttpResponse.getErrorMessage(e));
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
