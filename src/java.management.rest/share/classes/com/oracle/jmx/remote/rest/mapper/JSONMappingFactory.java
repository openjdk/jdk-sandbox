/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.mapper;

import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.openmbean.*;
import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONElement;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.json.JSONPrimitive;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author harsha
 */
public final class JSONMappingFactory {

    public static final JSONMappingFactory INSTANCE;
    private static final Map<Class<?>, JSONMapper> typeMapper;
    private static final Map<String, Class<?>> primitiveMap = new HashMap<>();

    static {
        // Make order of Initialization explicit
        typeMapper = new HashMap<>();

        primitiveMap.put("boolean", Boolean.TYPE);
        primitiveMap.put("int", Integer.TYPE);
        primitiveMap.put("long", Long.TYPE);
        primitiveMap.put("double", Double.TYPE);
        primitiveMap.put("float", Float.TYPE);
        primitiveMap.put("bool", Boolean.TYPE);
        primitiveMap.put("char", Character.TYPE);
        primitiveMap.put("byte", Byte.TYPE);
        primitiveMap.put("void", Void.TYPE);
        primitiveMap.put("short", Short.TYPE);

        INSTANCE = new JSONMappingFactory();
    }

    private JSONMappingFactory() {

        typeMapper.put(void.class, null);
        typeMapper.put(Void.class, null);

        typeMapper.put(boolean.class, new BooleanMapper());
        typeMapper.put(Boolean.class, new BooleanMapper());

        typeMapper.put(byte.class, new ByteMapper());
        typeMapper.put(Byte.class, new ByteMapper());

        typeMapper.put(short.class, new ShortMapper());
        typeMapper.put(Short.class, new ShortMapper());

        typeMapper.put(int.class, new IntegerMapper());
        typeMapper.put(Integer.class, new IntegerMapper());

        typeMapper.put(long.class, new LongMapper());
        typeMapper.put(Long.class, new LongMapper());

        typeMapper.put(float.class, new FloatMapper());
        typeMapper.put(Float.class, new FloatMapper());

        typeMapper.put(double.class, new DoubleMapper());
        typeMapper.put(Double.class, new DoubleMapper());

        typeMapper.put(char.class, new CharacterMapper());
        typeMapper.put(Character.class, new CharacterMapper());

        typeMapper.put(String.class, new StringMapper());
        typeMapper.put(BigInteger.class, new BigIntegerMapper());
        typeMapper.put(BigDecimal.class, new BigDecimalMapper());
        typeMapper.put(ObjectName.class, new ObjectNameMapper());
        typeMapper.put(Date.class, new DateMapper());
    }

    private Class<?> getArrayComponent(Class<?> cls) {
        if (cls == null) {
            return cls;
        }
        Class<?> compType = cls;
        // TODO: Add check for max array dimention of 15
        while (compType.isArray()) {
            compType = compType.getComponentType();
        }
        return compType;
    }

    private Object getArrayElement(Object arrayObj) {
        if (arrayObj != null && arrayObj.getClass().isArray()) {
            while (arrayObj.getClass().isArray()) {
                Class<?> componentType = arrayObj.getClass().getComponentType();
                if (Array.getLength(arrayObj) > 0) {
                    Object component = null;
                    for (int i = 0; i < Array.getLength(arrayObj); i++) {
                        component = Array.get(arrayObj, i);
                        if (component != null) {
                            break;
                        }
                    }
                    if (component == null) {
                        return null;
                    }
                    if (componentType.isPrimitive()) {
                        componentType = component.getClass();
                    }
                    arrayObj = componentType.cast(component);
                } else {
                    return null;
                }
            }
        }
        return arrayObj;
    }

    // TODO: This should be access controlled. Define new permissions
    public void addMapping(Class<?> cls, JSONMapper mapper) {
        Class<?> input = cls;
        if (cls.isArray()) {
            input = getArrayComponent(cls);
        }
        if (!typeMapper.containsKey(input)) {
            typeMapper.put(input, mapper);
        }
    }

    public JSONMapper getTypeMapper(Object object) {
        Object obj = object;
        Class<?> cls = object.getClass();
        if (cls.isArray()) {
            Object arrayElement = getArrayElement(obj);
            if (arrayElement instanceof CompositeDataSupport) {
                CompositeDataSupport cds = (CompositeDataSupport) arrayElement;
                return new OpenArrayTypeMapper(cls, cds.getCompositeType());
            } else if (arrayElement instanceof TabularDataSupport) {
                TabularDataSupport tds = (TabularDataSupport) arrayElement;
                return new OpenArrayTypeMapper(cls, tds.getTabularType());
            }
        }

        if (object instanceof CompositeDataSupport) {
            CompositeDataSupport cds = (CompositeDataSupport) object;
            return getTypeMapper(cds.getCompositeType());
        } else if (object instanceof TabularDataSupport) {
            TabularDataSupport cds = (TabularDataSupport) object;
            return getTypeMapper(cds.getTabularType());
        } else {
            return getTypeMapper(cls);
        }
    }

    /*
    Using class object is better than class name as class objects for same class
    name differ for each classloader
     */
    public JSONMapper getTypeMapper(Class<?> type) {
        if (type.isArray()) {
            Class<?> compType = getArrayComponent(type);
            if (typeMapper.get(compType) != null) {
                return new GenericArrayMapper(type);
            } else {
                return null;
            }
        }
        return typeMapper.get(type);
    }

    public JSONMapper getTypeMapper(OpenType<?> type) {
        if (type instanceof CompositeType) {
            return new OpenCompositeTypeMapper((CompositeType) type);
        } else if (type instanceof SimpleType) {
            try {
                return getTypeMapper(Class.forName(((SimpleType) type).getClassName()));
            } catch (ClassNotFoundException ex) { // This should not happen as SimpleTypes are always loaded
                throw new RuntimeException(ex);
            }
        } else if (type instanceof ArrayType) {
            try {
                ArrayType<?> at = (ArrayType) type;
                Class<?> arrayClass = Class.forName(type.getClassName());
                return new OpenArrayTypeMapper(arrayClass, at.getElementOpenType());
            } catch (ClassNotFoundException ex) { // This should not happen as SimpleTypes are always loaded
                throw new RuntimeException(ex);
            }
        } else if (type instanceof TabularType) {
            // TODO
        }
        return null; //keep compiler happy
    }

    public boolean isTypeMapped(String type) throws ClassNotFoundException {
        if (primitiveMap.get(type) != null) {
            return true;
        }
        Class<?> inputCls = Class.forName(type);
        inputCls = getArrayComponent(inputCls);
        if (inputCls.equals(CompositeData.class) || inputCls.equals(TabularData.class)
                || inputCls.equals(CompositeDataSupport.class) || inputCls.equals(TabularDataSupport.class)) {
            return true;
        }
        return JSONMappingFactory.INSTANCE.getTypeMapper(inputCls) != null;
    }

    private static class OpenArrayTypeMapper extends GenericArrayMapper {

        public OpenArrayTypeMapper(Class<?> type, OpenType<?> elementOpenType) {
            super(type);
            mapper = JSONMappingFactory.INSTANCE.getTypeMapper(elementOpenType);
        }
    }

    private static class GenericArrayMapper implements JSONMapper {

        private final Class<?> type;
        protected JSONMapper mapper;

        GenericArrayMapper(Class<?> type) {
            this.type = type;
            mapper = JSONMappingFactory.INSTANCE.getTypeMapper(JSONMappingFactory.INSTANCE.getArrayComponent(type));
        }

        private Object handleArrayType(Class<?> classType, JSONElement jsonValue) throws JSONDataException {
            if (!(jsonValue instanceof JSONArray)) {
                throw new JSONDataException("Invald JSON data format");
            }
            JSONArray jarr = (JSONArray) jsonValue;
            Class<?> compType = classType.getComponentType();
            Object resultArray = Array.newInstance(compType, jarr.size());

            for (int i = 0; i < jarr.size(); i++) {
                if (compType != null && compType.isArray()) {
                    Array.set(resultArray, i, handleArrayType(compType, jarr.get(i)));
                } else {
                    Array.set(resultArray, i, mapper.toJavaObject(jarr.get(i)));
                }
            }
            return resultArray;
        }

        @Override
        public Object toJavaObject(JSONElement jsonValue) throws JSONDataException {
            return handleArrayType(type, jsonValue);
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            if (data == null) {
                return new JSONPrimitive();
            }
            if (!data.getClass().equals(type)) {
                throw new JSONMappingException("Illegal type : " + data.getClass());
            }
            return getJasonValue(data);
        }

        private JSONElement getJasonValue(Object data) throws JSONMappingException {
            if (data == null) {
                return new JSONPrimitive();
            }
            if (!data.getClass().isArray()) {
                return mapper.toJsonValue(data);
            } else {
                JSONArray jArray = new JSONArray();
                for (int i = 0; i < Array.getLength(data); i++) {
                    jArray.add(getJasonValue(Array.get(data, i)));
                }
                return jArray;
            }
        }
    }

    private static class OpenCompositeTypeMapper implements JSONMapper {

        private final CompositeType type;

        OpenCompositeTypeMapper(CompositeType type) {
            this.type = type;
        }

        @Override
        public CompositeDataSupport toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (!(jsonValue instanceof JSONObject)) {
                throw new JSONDataException("JSON String not an object");
            }

            JSONObject jObject = (JSONObject) jsonValue;
            Map<String, Object> compositeDataMap = new HashMap<>();
            for (String itemName : type.keySet()) {
                OpenType<?> oType = type.getType(itemName);
                JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(oType);
                compositeDataMap.put(itemName, typeMapper.toJavaObject(jObject.get(itemName)));
            }
            try {
                return new CompositeDataSupport(type, compositeDataMap);
            } catch (OpenDataException ex) {
                throw new JSONDataException("Could not create CompositeData", ex);
            }
        }

        @Override
        public JSONElement toJsonValue(Object d) throws JSONMappingException {
            CompositeDataSupport data = (CompositeDataSupport) d;
            if (data == null) {
                return new JSONPrimitive();
            }
            JSONObject jObject = new JSONObject();
            for (String itemName : type.keySet()) {
                OpenType<?> oType = type.getType(itemName);
                JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(oType);
                if (typeMapper != null) {
                    jObject.put(itemName, typeMapper.toJsonValue(data.get(itemName)));
                } else {
                    System.out.println("Unable to find mapper for : " + oType);
                }
            }
            return jObject;
        }
    }

    private static class OpenTabularTypeMapper implements JSONMapper {

        private final TabularType type;

        public OpenTabularTypeMapper(TabularType type) {
            this.type = type;
        }

        /*
        Tabular data in JSON can be represented in below format
        [
            {
                "key" : [<list of elements>],
                "value": { <CompositeData> }
            },
            {
                "key" : [<list of elements>],
                "value": { <CompositeData> }
            }
        ]
         */
        @Override
        public TabularDataSupport toJavaObject(JSONElement jsonValue) throws JSONDataException {
//            if(jsonValue instanceof JSONArray) {
//                JSONArray jarr = (JSONArray) jsonValue;
//                for(JSONValue jval : jarr) {
//                    if(jval instanceof JSONObject) {
//                        JSONObject jObject = (JSONObject) jval;
//                        JSONValue jval1 = jObject.get("key");
//                        Object[] key;
//                        if(jval1 != null && jval1 instanceof JSONArray) {
//                            JSONArray jarr1 = (JSONArray) jval1;
//                            key = new Object[jarr1.size()];
//                            int i=0;
//                            for(JSONValue jval2: jarr1){
//                                key[i++] = ((JSONPrimitive)jval2).getValue();
//                            }
//                        }
//                        JSONValue jval2 = jObject.get("value");
//                        
//                        if(jval2 instanceof JSONObject) {
//                            JSONObject jObj1 = (JSONObject) jval2;
//                            JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(type.getRowType());
//                            Object valueObj = typeMapper.toJavaObject(jObj1);
//                        }
//                    }
//                }
//            }
            throw new UnsupportedOperationException();
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            throw new UnsupportedOperationException();
        }
    }

    private static class VoidMapper implements JSONMapper {

        @Override
        public Void toJavaObject(JSONElement jsonValue) throws JSONDataException {
            return null;
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive();
        }
    }

    private static class BooleanMapper implements JSONMapper {

        @Override
        public Boolean toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Boolean) {
                return (Boolean) ((JSONPrimitive) jsonValue).getValue();
            } else {
                throw new JSONDataException("Invalid type convertion - cannot convert "
                        + ((JSONPrimitive) jsonValue).getValue() + "(" + ((JSONPrimitive) jsonValue).getValue().getClass() + ")" + " to boolean");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((Boolean) data);
        }
    }

    private static class ByteMapper implements JSONMapper {

        @Override
        public Byte toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Long) {
                return ((Long) ((JSONPrimitive) jsonValue).getValue()).byteValue();
            } else {
                throw new JSONDataException("Invalid type convertion - cannot convert "
                        + (((JSONPrimitive) jsonValue).getValue().getClass()) + " to byte");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((Byte) data);
        }
    }

    private static class ShortMapper implements JSONMapper {

        @Override
        public Short toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Long) {
                return ((Long) ((JSONPrimitive) jsonValue).getValue()).shortValue();
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((Short) data);
        }
    }

    private static class IntegerMapper implements JSONMapper {

        @Override
        public Integer toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Long) {
                return ((Long) ((JSONPrimitive) jsonValue).getValue()).intValue();
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((Integer) data);
        }
    }

    private static class LongMapper implements JSONMapper {

        @Override
        public Long toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Long) {
                return (Long) ((JSONPrimitive) jsonValue).getValue();
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((Long) data);
        }
    }

    private static class FloatMapper implements JSONMapper {

        @Override
        public Float toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Double) {
                return ((Double) ((JSONPrimitive) jsonValue).getValue()).floatValue();
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((Float) data);
        }
    }

    private static class DoubleMapper implements JSONMapper {

        @Override
        public Double toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Long) {
                return (Double) ((JSONPrimitive) jsonValue).getValue();
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((Double) data);
        }
    }

    private static class CharacterMapper implements JSONMapper {

        @Override
        public Character toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof String) {
                String data = ((String) ((JSONPrimitive) jsonValue).getValue());
                if (data.length() < 1) {
                    throw new JSONDataException("Invalid char");
                }
                return data.charAt(0);
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((Character) data);
        }
    }

    private static class StringMapper implements JSONMapper {

        @Override
        public String toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof String) {
                return (String) ((JSONPrimitive) jsonValue).getValue();
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive((String) data);
        }
    }

    private static class BigDecimalMapper implements JSONMapper {

        @Override
        public BigDecimal toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Double) {
                return BigDecimal.valueOf((Double) ((JSONPrimitive) jsonValue).getValue());
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive(((BigDecimal) data).doubleValue());
        }
    }

    private static class BigIntegerMapper implements JSONMapper {

        @Override
        public BigInteger toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof Long) {
                return BigInteger.valueOf((Long) ((JSONPrimitive) jsonValue).getValue());
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive(((BigInteger) data).longValue());
        }
    }

    private static class ObjectNameMapper implements JSONMapper {

        @Override
        public ObjectName toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof String) {
                try {
                    return new ObjectName((String) ((JSONPrimitive) jsonValue).getValue());
                } catch (MalformedObjectNameException ex) {
                    throw new JSONDataException("Invalid Objectname");
                }
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive(data.toString());
        }
    }

    private static class DateMapper implements JSONMapper {

        private final DateFormat df = new SimpleDateFormat("YYYY-MM-DD");

        @Override
        public Date toJavaObject(JSONElement jsonValue) throws JSONDataException {
            if (jsonValue instanceof JSONPrimitive && ((JSONPrimitive) jsonValue).getValue() instanceof String) {
                String data = ((String) ((JSONPrimitive) jsonValue).getValue());
                try {
                    return df.parse(data);
                } catch (ParseException ex) {
                    throw new JSONDataException("Invalid Data " + data);
                }
            } else {
                throw new JSONDataException("Invalid JSON");
            }
        }

        @Override
        public JSONElement toJsonValue(Object data) throws JSONMappingException {
            return new JSONPrimitive(df.format((Date) data));
        }
    }
}
