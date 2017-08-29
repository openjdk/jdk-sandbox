/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javax.management.remote.rest;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsServer;

import javax.management.MBeanServer;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.net.InetSocketAddress;
import java.security.KeyStore;
import java.util.*;

/**
 * @author harsha
 */
public class PlatformRestAdapter {

    private static final Set<String> contextList = new HashSet<>();
    /*
     * Initializes HTTPServer with settings from config file
     * acts as container for platform rest adapter
     */
    private static HttpServer httpServer = null;
    private static JmxRestAdapter instance = null;

    private PlatformRestAdapter() {
    }

    public static synchronized void init(String portStr, Properties props) throws IOException {
        if (instance == null) {
            final int port;
            try {
                port = Integer.parseInt(portStr);
            } catch (NumberFormatException x) {
                throw new IllegalArgumentException("Invalid string for port");
            }
            if (port < 0) {
                throw new IllegalArgumentException("Invalid string for port");
            }

            boolean useSSL = Boolean.parseBoolean((String) props.get("com.sun.management.jmxremote.ssl"));
            if (useSSL) {
                final String sslConfigFileName
                        = props.getProperty(PropertyNames.SSL_CONFIG_FILE_NAME);
                SSLContext ctx = getSSlContext(sslConfigFileName);
                if (ctx != null) {
                    HttpsServer server = HttpsServer.create(new InetSocketAddress(port), 0);
                    server.setHttpsConfigurator(new HttpsConfigurator(ctx));
                    httpServer = server;
                } else {
                    httpServer = HttpServer.create(new InetSocketAddress(port), 0);
                }
            } else {
                httpServer = HttpServer.create(new InetSocketAddress(port), 0);
            }

            // Initialize rest adapter
            Map<String, Object> env = new HashMap<>();
            // Do we use authentication?
            final String useAuthenticationStr
                    = props.getProperty(PropertyNames.USE_AUTHENTICATION,
                            DefaultValues.USE_AUTHENTICATION);
            final boolean useAuthentication
                    = Boolean.valueOf(useAuthenticationStr);

            String loginConfigName;
            String passwordFileName;

            if (useAuthentication) {
                env.put("jmx.remote.x.authentication", Boolean.TRUE);
                // Get non-default login configuration
                loginConfigName
                        = props.getProperty(PropertyNames.LOGIN_CONFIG_NAME);
                env.put("jmx.remote.x.login.config", loginConfigName);

                if (loginConfigName == null) {
                    // Get password file
                    passwordFileName
                            = props.getProperty(PropertyNames.PASSWORD_FILE_NAME);
                    env.put("jmx.remote.x.password.file", passwordFileName);
                }
            }

            instance = new JmxRestAdapterImpl(httpServer, "default", env, ManagementFactory.getPlatformMBeanServer());
            httpServer.start();
        }
    }

    public static void stop() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    private static SSLContext getSSlContext(String sslConfigFileName) {
        final String keyStore, keyStorePassword, trustStore, trustStorePassword;

        try {
            if (sslConfigFileName == null || sslConfigFileName.isEmpty()) {
                keyStore = System.getProperty(PropertyNames.SSL_KEYSTORE_FILE);
                keyStorePassword = System.getProperty(PropertyNames.SSL_KEYSTORE_PASSWORD);
                trustStore = System.getProperty(PropertyNames.SSL_TRUSTSTORE_FILE);
                trustStorePassword = System.getProperty(PropertyNames.SSL_TRUSTSTORE_PASSWORD);
            } else {
                Properties p = new Properties();
                BufferedInputStream bin = new BufferedInputStream(new FileInputStream(sslConfigFileName));
                p.load(bin);
                keyStore = p.getProperty(PropertyNames.SSL_KEYSTORE_FILE);
                keyStorePassword = p.getProperty(PropertyNames.SSL_KEYSTORE_PASSWORD);
                trustStore = p.getProperty(PropertyNames.SSL_TRUSTSTORE_FILE);
                trustStorePassword = p.getProperty(PropertyNames.SSL_TRUSTSTORE_PASSWORD);
            }

            char[] keyStorePasswd = null;
            if (keyStorePassword.length() != 0) {
                keyStorePasswd = keyStorePassword.toCharArray();
            }

            char[] trustStorePasswd = null;
            if (trustStorePassword.length() != 0) {
                trustStorePasswd = trustStorePassword.toCharArray();
            }

            KeyStore ks = null;
            if (keyStore != null) {
                ks = KeyStore.getInstance(KeyStore.getDefaultType());
                FileInputStream ksfis = new FileInputStream(keyStore);
                ks.load(ksfis, keyStorePasswd);

            }
            KeyManagerFactory kmf = KeyManagerFactory.getInstance(
                    KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, keyStorePasswd);

            KeyStore ts = null;
            if (trustStore != null) {
                ts = KeyStore.getInstance(KeyStore.getDefaultType());
                FileInputStream tsfis = new FileInputStream(trustStore);
                ts.load(tsfis, trustStorePasswd);
            }
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                    TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ts);

            SSLContext ctx = SSLContext.getInstance("SSL");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return ctx;
        } catch (Exception ex) {
        }
        return null;
    }

    public static JmxRestAdapter getInstance() {
        if (instance == null) {
            throw new IllegalStateException("PlatformRestAdapter not initialized");
        }
        return instance;
    }

    public static synchronized JmxRestAdapter newRestAdapter(String context, Map<String, ?> env, MBeanServer mbeanServer) {
        if (!contextList.contains(context)) {
            contextList.add(context);
            return new JmxRestAdapterImpl(httpServer, context, env, mbeanServer);
        }
        throw new IllegalArgumentException(context + " is already taken");
    }

    /**
     * Default values for JMX configuration properties.
     */
    public static interface DefaultValues {

        public static final String PORT = "0";
        public static final String CONFIG_FILE_NAME = "management.properties";
        public static final String USE_SSL = "true";
        public static final String USE_LOCAL_ONLY = "true";
        public static final String USE_REGISTRY_SSL = "false";
        public static final String USE_AUTHENTICATION = "true";
        public static final String PASSWORD_FILE_NAME = "jmxremote.password";
        public static final String ACCESS_FILE_NAME = "jmxremote.access";
        public static final String SSL_NEED_CLIENT_AUTH = "false";
    }

    /**
     * Names of JMX configuration properties.
     */
    public static interface PropertyNames {

        public static final String PORT
                = "com.sun.management.jmxremote.rest.port";
        public static final String HOST
                = "com.sun.management.jmxremote.host";
        public static final String USE_SSL
                = "com.sun.management.jmxremote.ssl";
        public static final String USE_AUTHENTICATION
                = "com.sun.management.jmxremote.authenticate";
        public static final String PASSWORD_FILE_NAME
                = "com.sun.management.jmxremote.password.file";
        public static final String LOGIN_CONFIG_NAME
                = "com.sun.management.jmxremote.login.config";
        public static final String SSL_NEED_CLIENT_AUTH
                = "com.sun.management.jmxremote.ssl.need.client.auth";
        public static final String SSL_CONFIG_FILE_NAME
                = "com.sun.management.jmxremote.ssl.config.file";
        public static final String SSL_KEYSTORE_FILE
                = "javax.net.ssl.keyStore";
        public static final String SSL_TRUSTSTORE_FILE
                = "javax.net.ssl.trustStore";
        public static final String SSL_KEYSTORE_PASSWORD
                = "javax.net.ssl.keyStorePassword";
        public static final String SSL_TRUSTSTORE_PASSWORD
                = "javax.net.ssl.trustStorePassword";
    }
}
