/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.http;

import javax.management.*;
import javax.management.openmbean.OpenMBeanAttributeInfo;
import javax.management.openmbean.OpenMBeanParameterInfo;
import javax.management.openmbean.OpenType;
import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONElement;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.json.JSONPrimitive;
import com.oracle.jmx.remote.rest.mapper.JSONDataException;
import com.oracle.jmx.remote.rest.mapper.JSONMapper;
import com.oracle.jmx.remote.rest.mapper.JSONMappingException;
import com.oracle.jmx.remote.rest.mapper.JSONMappingFactory;
import com.oracle.jmx.remote.rest.json.parser.JSONParser;
import com.oracle.jmx.remote.rest.json.parser.ParseException;
import java.net.HttpURLConnection;
import java.util.*;

/**
 * @author harsha
 */
public class PostRequestHandler {

    private static final String MBEAN_NAME = "name";
    private static final String MBEAN_ARGUMENTS = "arguments";
    private final static Map<String, Class<?>> primitiveToObject = new HashMap<>();

    static {
        primitiveToObject.put("int", Integer.TYPE);
        primitiveToObject.put("long", Long.TYPE);
        primitiveToObject.put("double", Double.TYPE);
        primitiveToObject.put("float", Float.TYPE);
        primitiveToObject.put("boolean", Boolean.TYPE);
        primitiveToObject.put("char", Character.TYPE);
        primitiveToObject.put("byte", Byte.TYPE);
        primitiveToObject.put("void", Void.TYPE);
        primitiveToObject.put("short", Short.TYPE);
    }

    private final MBeanServer mbeanServer;
    private final List<String> allowedMbeans;

    public PostRequestHandler(MBeanServer mServer, List<String> allowedMbeans) {
        this.mbeanServer = mServer;
        this.allowedMbeans = allowedMbeans;
    }

    public synchronized List<JSONObject> handle(String jsonStr) {

        JSONElement object;
        List<JSONObject> postResponse = new ArrayList<>();
        try {
            object = new JSONParser(jsonStr).parse();
        } catch (ParseException ex) {
            postResponse.add(HttpResponse.getJsonObject(HttpResponse.getHttpErrorCode(ex), new JSONPrimitive(jsonStr), new JSONPrimitive("Invalid JSON String")));
            return postResponse;
        }

        if (object instanceof JSONObject) {
            postResponse.add(handleIndividualRequest((JSONObject) object));
            return postResponse;
        } else if (object instanceof JSONArray) {
            JSONArray jarr = (JSONArray) object;

            for (JSONElement jval : jarr) {
                if (jval instanceof JSONObject) {
                    JSONObject resp = handleIndividualRequest((JSONObject) jval);
                    postResponse.add(resp);
                }
            }
            return postResponse;
        } else {
            postResponse.add(HttpResponse.getJsonObject(HttpURLConnection.HTTP_BAD_REQUEST, new JSONPrimitive(jsonStr), new JSONPrimitive("Invalid request")));
            return postResponse;
        }
    }

    private JSONObject handleIndividualRequest(JSONObject jobject) {

        try {
            JSONObject map = jobject;
            ObjectName name = getObjectName(map);
            MBeanOps operation = null;
            for (MBeanOps op : MBeanOps.values()) {
                if (map.containsKey(op.toString())) {
                    operation = op;
                    break;
                }
            }

            if (operation == null) {
                throw new JSONDataException("Invalid operation string");
            }

            JSONElement val = map.get(operation.toString());
            if (!(val instanceof JSONPrimitive) || !(((JSONPrimitive) val).getValue() instanceof String)) {
                throw new JSONDataException("Invalid JSON String");
            }

            String attrOrOperation = (String) ((JSONPrimitive) val).getValue();
            JSONElement result = new JSONPrimitive("success");

            val = map.get(MBEAN_ARGUMENTS);
            if (val != null) {
                if (!(val instanceof JSONArray)) {
                    throw new JSONDataException("Invalid JSON String");
                }
            }

            JSONArray args = (JSONArray) val;

            switch (operation) {
                case READ:
                    result = readAttribute(name, attrOrOperation, args);
                    break;
                case WRITE:
                    writeAttribute(name, attrOrOperation, args);
                    break;
                case EXEC:
                    result = execOperation(name, attrOrOperation, args);
                    break;
            }
            return HttpResponse.getJsonObject(HttpURLConnection.HTTP_OK, jobject, result);
        } catch (JSONDataException | MBeanException | JSONMappingException | ClassNotFoundException | IntrospectionException | InvalidAttributeValueException | IllegalArgumentException ex) {
            return HttpResponse.getJsonObject(HttpResponse.getHttpErrorCode(ex), jobject, new JSONPrimitive(ex.toString()));
        } catch (AttributeNotFoundException ex) {
            return HttpResponse.getJsonObject(HttpResponse.getHttpErrorCode(ex), jobject, new JSONPrimitive("Invalid Mbean attribute"));
        } catch (InstanceNotFoundException | ReflectionException ex) {
            return HttpResponse.getJsonObject(HttpResponse.getHttpErrorCode(ex), jobject, new JSONPrimitive("Invalid Mbean"));
        }
    }

    private JSONElement readAttribute(ObjectName name, String attribute, JSONArray args)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException, ClassNotFoundException,
            MBeanException, AttributeNotFoundException, JSONMappingException {
        MBeanInfo mInfos = mbeanServer.getMBeanInfo(name);
        MBeanAttributeInfo attrInfo = Arrays.stream(mInfos.getAttributes()).filter(a -> a.getName().equals(attribute)).findFirst().orElse(null);
        if (attrInfo != null && attrInfo.isReadable()) {
            JSONMapper mapper;
            if (attrInfo instanceof OpenMBeanAttributeInfo) {
                OpenType<?> type = ((OpenMBeanAttributeInfo) attrInfo).getOpenType();
                mapper = JSONMappingFactory.INSTANCE.getTypeMapper(type);
            } else {
                mapper = JSONMappingFactory.INSTANCE.getTypeMapper(Class.forName(attrInfo.getType()));
            }
            Object attrVal = mbeanServer.getAttribute(name, attribute);
            return mapper.toJsonValue(attrVal);
        } else {
            throw new AttributeNotFoundException();
        }
    }

    private void writeAttribute(ObjectName name, String attribute, JSONArray args)
            throws InstanceNotFoundException, IntrospectionException, ReflectionException, ClassNotFoundException,
            JSONDataException, AttributeNotFoundException, InvalidAttributeValueException, MBeanException {

        if (args == null || args.isEmpty()) {
            throw new JSONDataException("Null arguments for set attribute");
        }

        MBeanInfo mInfos = mbeanServer.getMBeanInfo(name);
        MBeanAttributeInfo attrInfo = Arrays.stream(mInfos.getAttributes()).filter(a -> a.getName().equals(attribute)).findFirst().orElse(null);
        if (attrInfo != null && attrInfo.isWritable()) {
            JSONMapper mapper;
            if (attrInfo instanceof OpenMBeanAttributeInfo) {
                OpenType<?> type = ((OpenMBeanAttributeInfo) attrInfo).getOpenType();
                mapper = JSONMappingFactory.INSTANCE.getTypeMapper(type);
            } else {
                mapper = JSONMappingFactory.INSTANCE.getTypeMapper(Class.forName(attrInfo.getType()));
            }

            JSONElement val = args.get(0);
            Object argVal = mapper.toJavaObject(val);
            Attribute attrObj = new Attribute(attribute, argVal);
            mbeanServer.setAttribute(name, attrObj);

        } else {
            throw new AttributeNotFoundException();
        }
    }

    private Object[] mapArgsToSignature(JSONArray args, MBeanParameterInfo[] signature) {
        if (args.size() != signature.length) {
            throw new IllegalArgumentException("Invalid parameters : expected - " + signature.length + " parameters, got - " + args.size());
        }
        if (signature.length == 0 && args.isEmpty()) {
            return new Object[0];
        }
        int i = 0;
        Object[] params = new Object[signature.length];
        for (MBeanParameterInfo info : signature) {
            if (info instanceof OpenMBeanParameterInfo) {
                OpenType<?> openType = ((OpenMBeanParameterInfo) info).getOpenType();
                JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(openType);
                try {
                    params[i] = typeMapper.toJavaObject(args.get(i));
                } catch (JSONDataException ex) {
                    throw new IllegalArgumentException("Invalid JSON String : " + args.get(i).toJsonString() + " for arguments");
                }
            } else {
                Class<?> inputCls = primitiveToObject.get(info.getType());
                try {
                    if (inputCls == null) {
                        inputCls = Class.forName(info.getType());
                    }
                } catch (ClassNotFoundException | ClassCastException ex) {
                    throw new IllegalArgumentException("Invalid parameters : " + args.get(i).toJsonString() + " cannot be mapped to : " + info.getType());
                }
                JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(inputCls);
                if (typeMapper == null) {
                    throw new IllegalArgumentException("Invalid parameters : " + args.get(i).toJsonString() + " cannot be mapped to : " + info.getType());
                }
                try {
                    params[i] = typeMapper.toJavaObject(args.get(i));
                } catch (JSONDataException ex) {
                    throw new IllegalArgumentException("Invalid JSON String : " + args.get(i).toJsonString() + " for arguments");
                }
            }
            i++;
        }
        return params;
    }

    private JSONElement execOperation(ObjectName name, String opstr, JSONArray args)
            throws MBeanException, JSONMappingException, IntrospectionException, ReflectionException {
        if (args == null) {
            args = new JSONArray();
        }
        MBeanInfo mBeanInfo;
        try {
            mBeanInfo = mbeanServer.getMBeanInfo(name);
        } catch (InstanceNotFoundException ex) {
            throw new IllegalArgumentException("Invalid Operation String");
        }

        MBeanOperationInfo[] opinfos = Arrays.stream(mBeanInfo.getOperations()).
                filter(a -> a.getName().equals(opstr)).toArray(MBeanOperationInfo[]::new);

        if (opinfos.length == 0) {
            throw new IllegalArgumentException("Invalid Operation String");
        }

        String[] signature = null;
        Object[] params = null;

        if (opinfos.length == 1) {
            MBeanParameterInfo[] sig = opinfos[0].getSignature();
            params = mapArgsToSignature(args, sig);
            signature = Arrays.asList(sig).stream().map(a -> a.getType()).toArray(a -> new String[a]);
        } else if (opinfos.length > 1) {
            IllegalArgumentException exception = null;
            for (MBeanOperationInfo opInfo : opinfos) {
                MBeanParameterInfo[] sig = opInfo.getSignature();
                try {
                    params = mapArgsToSignature(args, sig);
                    signature = Arrays.asList(sig).stream().map(a -> a.getType()).toArray(a -> new String[a]);
                    exception = null;
                    break;
                } catch (IllegalArgumentException ex) {
                    exception = ex;
                }
            }
            if (exception != null) {
                throw exception;
            }
        }

        Object invoke;
        try {
            invoke = mbeanServer.invoke(name, opstr, params, signature);
        } catch (InstanceNotFoundException ex) {
            throw new IllegalArgumentException("Invalid Operation String");
        }
        if (invoke != null) {
            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(invoke);
            if (typeMapper != null) {
                return typeMapper.toJsonValue(invoke);
            } else {
                return new JSONPrimitive();
            }
        } else {
            return new JSONPrimitive();
        }
    }

    private ObjectName getObjectName(JSONObject map) throws JSONDataException, InstanceNotFoundException {
        do {
            if (map.get(MBEAN_NAME) == null || !(map.get(MBEAN_NAME) instanceof JSONPrimitive)) {
                break;
            }
            JSONPrimitive mbean_name = (JSONPrimitive) map.get(MBEAN_NAME);
            if (!(mbean_name.getValue() instanceof String)) {
                break;
            }
            if (!allowedMbeans.contains((String) mbean_name.getValue())) {
                throw new InstanceNotFoundException("Invalid MBean");
            }
            try {
                return ObjectName.getInstance((String) mbean_name.getValue());
            } catch (MalformedObjectNameException ex) {
            }
        } while (false);
        throw new JSONDataException("Invalid JSON String");
    }

    private static enum MBeanOps {
        READ("read"),
        WRITE("write"),
        EXEC("exec");

        private final String text;

        private MBeanOps(final String text) {
            this.text = text;
        }

        @Override
        public String toString() {
            return text;
        }
    }
}
