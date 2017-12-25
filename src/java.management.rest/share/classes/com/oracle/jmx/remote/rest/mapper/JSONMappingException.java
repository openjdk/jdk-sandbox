/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.mapper;

/**
 * @author harsha
 */
public class JSONMappingException extends Exception {

    private static final long serialVersionUID = -3099452281524742227L;

    public static final JSONMappingException UNABLE_TO_MAP = new JSONMappingException("Unable to map types");

    public JSONMappingException() {
        super();
    }

    public JSONMappingException(String s) {
        super(s);
    }

    public JSONMappingException(String s, Throwable ex) {
        super(s, ex);
    }
}
