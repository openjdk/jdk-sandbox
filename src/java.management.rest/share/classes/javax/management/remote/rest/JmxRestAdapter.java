/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javax.management.remote.rest;

import javax.management.MBeanServer;

/**
 * @author harsha
 */
public interface JmxRestAdapter {

    public static final String AUTHENTICATOR
            = "jmx.remote.authenticator";
    public static final String LOGIN_CONFIG_PROP
            = "jmx.remote.x.login.config";
    public static final String PASSWORD_FILE_PROP
            = "jmx.remote.x.password.file";

    public void start();

    public void stop();

    public String getBaseUrl();

    public MBeanServer getMBeanServer();
}
