/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javax.management.remote.rest;

import javax.management.MBeanServerFactoryListener;

import com.oracle.jmx.remote.rest.http.JmxRestAdapter;
import com.oracle.jmx.remote.rest.http.MBeanServerCollectionResource;
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.security.KeyStore;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

/**
 * @author harsha
 */
public class PlatformRestAdapter implements MBeanServerFactoryListener {

    /*
     * Initializes HTTPServer with settings from config file
     * acts as container for platform rest adapter
     */
    private static HttpServer httpServer = null;
    private static List<JmxRestAdapter> restAdapters = new CopyOnWriteArrayList<>();
    private static Map<String, Object> env;

    private static String portStr;
    private static Properties props;

    private PlatformRestAdapter() {
    }

    public static synchronized void init(String portStr, Properties props) throws IOException {
        PlatformRestAdapter.portStr = portStr;
        PlatformRestAdapter.props = props;

        if (httpServer == null) {
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
                    HttpsServer server = HttpsServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                    server.setHttpsConfigurator(new HttpsConfigurator(ctx));
                    httpServer = server;
                } else {
                    httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
                }
            } else {
                httpServer = HttpServer.create(new InetSocketAddress("0.0.0.0", port), 0);
            }

            // Initialize rest adapter
			env = new HashMap<>();
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

            restAdapters.add(new JmxRestAdapter(httpServer, "platform", env, ManagementFactory.getPlatformMBeanServer()));
            new MBeanServerCollectionResource(restAdapters, httpServer);
            httpServer.setExecutor(Executors.newCachedThreadPool());
            httpServer.start();
        }
    }

    public synchronized static void stop() {
        if (httpServer != null) {
            httpServer.stop(0);
            httpServer = null;
        }
    }

    public synchronized static void start() throws IOException {
        if(httpServer == null) {
            PlatformRestAdapter.init(portStr,props);
        }
    }

    @Override
    public void onMBeanServerCreated(MBeanServer mBeanServer) {
        JmxRestAdapter restAdapter = new JmxRestAdapter(httpServer, "", env, mBeanServer);
        restAdapters.add(restAdapter);
    }

    @Override
    public void onMBeanServerRemoved(MBeanServer mBeanServer) {

    }

    public static synchronized String getAuthority() {
        if(httpServer == null) {
            throw new IllegalStateException("Platform rest adapter not initialized");
        }
        try {
            if (httpServer instanceof HttpsServer) {
                return "https://" + InetAddress.getLocalHost().getHostName() + ":" + httpServer.getAddress().getPort();
            }
            return "http://" + InetAddress.getLocalHost().getHostName() + ":" + httpServer.getAddress().getPort();
        } catch (UnknownHostException ex) {
            return "http://localhost" + ":" + httpServer.getAddress().getPort();
        }
    }

    public static synchronized String getBaseURL() {
        if(httpServer == null) {
            throw new IllegalStateException("Platform rest adapter not initialized");
        }
        try {
            if (httpServer instanceof HttpsServer) {
                return "https://" + InetAddress.getLocalHost().getHostName() + ":" + httpServer.getAddress().getPort() + "/jmx/servers";
            }
            return "http://" + InetAddress.getLocalHost().getHostName() + ":" + httpServer.getAddress().getPort() + "/jmx/servers";
        } catch (UnknownHostException ex) {
            return "http://localhost" + ":" + httpServer.getAddress().getPort() + "/jmx/servers";
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

        String PORT
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
