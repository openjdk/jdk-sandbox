/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.mapper;

/**
 * @author harsha
 */
public class JSONDataException extends Exception {

    private static final long serialVersionUID = 2430707794210680967L;

    public JSONDataException() {
        super();
    }

    public JSONDataException(String s) {
        super(s);
    }

    public JSONDataException(String s, Throwable ex) {
        super(s, ex);
    }
}
