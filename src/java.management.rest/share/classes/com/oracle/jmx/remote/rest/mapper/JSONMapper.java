/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.mapper;

import com.oracle.jmx.remote.rest.json.JSONElement;

/**
 * @author harsha
 */
public interface JSONMapper {
    public Object toJavaObject(JSONElement jsonValue) throws JSONDataException;

    public JSONElement toJsonValue(Object data) throws JSONMappingException;
}
