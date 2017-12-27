/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.oracle.jmx.remote.rest.http;

import com.oracle.jmx.remote.rest.json.JSONElement;
import com.oracle.jmx.remote.rest.mapper.JSONMapper;
import com.oracle.jmx.remote.rest.mapper.JSONMappingException;
import com.oracle.jmx.remote.rest.mapper.JSONMappingFactory;
import com.sun.jmx.remote.security.JMXPluggableAuthenticator;
import com.sun.jmx.remote.security.JMXSubjectDomainCombiner;
import com.sun.jmx.remote.security.SubjectDelegator;
import com.sun.net.httpserver.BasicAuthenticator;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import javax.management.JMX;
import javax.management.MBeanServer;
import javax.management.MBeanServerDelegate;
import javax.management.MBeanServerDelegateMBean;
import javax.management.remote.JMXAuthenticator;
import javax.management.remote.rest.PlatformRestAdapter;
import javax.security.auth.Subject;
import java.io.IOException;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.HttpURLConnection;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author harsha
 */
public final class JmxRestAdapter implements RestResource {

    public static final String AUTHENTICATOR
            = "jmx.remote.authenticator";
    public static final String LOGIN_CONFIG_PROP
            = "jmx.remote.x.login.config";
    public static final String PASSWORD_FILE_PROP
            = "jmx.remote.x.password.file";

    private final HttpServer httpServer;
    private final String contextStr;
    private final Map<String, ?> env;
    private final MBeanServer mbeanServer;

    private HttpContext httpContext;

    private JMXAuthenticator authenticator = null;

    private final MBeanServerDelegateMBean mBeanServerDelegateMBean;
    private final Map<String, MBeanServer> authenticatedMBeanServer = new ConcurrentHashMap<>();
    private final MBeanCollectionResource mbeansResource;
    private final Map<String, MBeanCollectionResource> authenticatedMBeanCollRes = new ConcurrentHashMap<>();
    private static int count = 0;
    private final String alias;

    public JmxRestAdapter(HttpServer hServer, String context, Map<String, ?> env, MBeanServer mbeanServer) {
        httpServer = hServer;
        this.env = env;
        this.mbeanServer = mbeanServer;
        mBeanServerDelegateMBean = JMX.newMBeanProxy(mbeanServer, MBeanServerDelegate.DELEGATE_NAME, MBeanServerDelegateMBean.class);

        if (context == null || context.isEmpty()) {
            alias = "server-" + count++;
            contextStr = alias;
        } else {
            contextStr = context;
            alias = context;
        }

        if (env.get("jmx.remote.x.authentication") != null) {
            authenticator = (JMXAuthenticator) env.get(JmxRestAdapter.AUTHENTICATOR);
            if (authenticator == null) {
                if (env.get("jmx.remote.x.password.file") != null
                        || env.get("jmx.remote.x.login.config") != null) {
                    authenticator = new JMXPluggableAuthenticator(env);
                } else {
                    // Throw exception for invalid authentication config
                }
            }
        }
        mbeansResource = new MBeanCollectionResource(mbeanServer);
        httpContext = httpServer.createContext("/jmx/servers/" + contextStr, this);
        if (env.get("jmx.remote.x.authentication") != null) {
            httpContext.setAuthenticator(new RestAuthenticator("jmx"));
        }
    }

    public String getAlias() {
        return alias;
    }

    private MBeanServer getMBeanServerProxy(MBeanServer mbeaServer, Subject subject) {
        return (MBeanServer) Proxy.newProxyInstance(MBeanServer.class.getClassLoader(),
                new Class<?>[]{MBeanServer.class},
                new AuthInvocationHandler(mbeaServer, subject));
    }

    @Override
    public HttpResponse doGet(HttpExchange exchange) {
        String selfUrl = PlatformRestAdapter.getAuthority() + exchange.getRequestURI().getPath().replaceAll("\\/$", "");
        Map<String, String> links = new LinkedHashMap<>();
        links.put("mbeans", selfUrl + "/mbeans");

        Map<String, Object> mBeanServerInfo = getMBeanServerInfo();
        mBeanServerInfo.put("_links",links);

        final JSONMapper typeMapper = JSONMappingFactory.INSTANCE.getTypeMapper(mBeanServerInfo);
        if (typeMapper != null) {
            try {
                JSONElement jsonElement = typeMapper.toJsonValue(mBeanServerInfo);
                return new HttpResponse(HttpURLConnection.HTTP_OK, jsonElement.toJsonString());
            } catch (JSONMappingException e) {
                return new HttpResponse(500, "Internal error");
            }
        }
        return new HttpResponse(500, "Internal error");
    }

    @Override
    public HttpResponse doPut(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    @Override
    public HttpResponse doPost(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    @Override
    public HttpResponse doDelete(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    @Override
    public HttpResponse doHead(HttpExchange exchange) {
        return HttpResponse.METHOD_NOT_ALLOWED;
    }

    public String getUrl() {
        String baseUrl = PlatformRestAdapter.getBaseURL();
        String path = httpContext.getPath();
        return baseUrl + path;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        MBeanCollectionResource mbeansRes = mbeansResource;
        if (env.get("jmx.remote.x.authentication") != null) {
            String authCredentials = HttpUtil.getCredentials(exchange);
            mbeansRes = authenticatedMBeanCollRes.get(authCredentials);
            if (mbeansRes == null) {
                throw new IllegalArgumentException("Invalid HTTP request Headers");
            }
        }

        String path = exchange.getRequestURI().getPath();

        // Route request to appropriate resource
        if (path.matches("^/?jmx/servers/[a-zA-Z0-9\\-\\.]+/?$")) {
            RestResource.super.handle(exchange);
        } else if (path.matches("^/?jmx/servers/[a-zA-Z0-9\\-\\.]+/mbeans.*")) {
            mbeansRes.handle(exchange);
        } else {
            HttpUtil.sendResponse(exchange, new HttpResponse(404, "Not found"));
        }
    }

    private class RestAuthenticator extends BasicAuthenticator {

        RestAuthenticator(String realm) {
            super(realm);
        }

        @Override
        public boolean checkCredentials(String username, String password) {
            String credentials = username + ":" + password;
            if (authenticatedMBeanServer.containsKey(credentials)) {
                return true;
            } else {
                Subject subject = null;
                if (authenticator != null) {
                    String[] credential = new String[]{username, password};
                    try {
                        subject = authenticator.authenticate(credential);
                    } catch (SecurityException e) {
                        return false;
                    }
                }
                MBeanServer proxy = getMBeanServerProxy(mbeanServer, subject);
                authenticatedMBeanServer.put(credentials, proxy);
                authenticatedMBeanCollRes.put(credentials, new MBeanCollectionResource(proxy));
                return true;
            }
        }
    }

    Map<String, Object> getMBeanServerInfo() {
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("id", mBeanServerDelegateMBean.getMBeanServerId());
        result.put("alias", getAlias());

        result.put("defaultDomain", mbeanServer.getDefaultDomain());
        result.put("mBeanCount", mbeanServer.getMBeanCount());
        result.put("domains", Arrays.toString(mbeanServer.getDomains()));

        result.put("specName", mBeanServerDelegateMBean.getSpecificationName());
        result.put("specVersion", mBeanServerDelegateMBean.getSpecificationVersion());
        result.put("specVendor", mBeanServerDelegateMBean.getSpecificationVendor());

        result.put("implName", mBeanServerDelegateMBean.getImplementationName());
        result.put("implVersion", mBeanServerDelegateMBean.getImplementationVersion());
        result.put("implVendor", mBeanServerDelegateMBean.getImplementationVendor());

        return result;
    }

    private class AuthInvocationHandler implements InvocationHandler {

        private final MBeanServer mbeanServer;
        private final AccessControlContext acc;

        AuthInvocationHandler(MBeanServer server, Subject subject) {
            this.mbeanServer = server;
            if (subject == null) {
                this.acc = null;
            } else {
                if (SubjectDelegator.checkRemoveCallerContext(subject)) {
                    acc = JMXSubjectDomainCombiner.getDomainCombinerContext(subject);
                } else {
                    acc = JMXSubjectDomainCombiner.getContext(subject);
                }
            }
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (acc == null) {
                return method.invoke(mbeanServer, args);
            } else {
                PrivilegedAction<Object> op = () -> {
                    try {
                        return method.invoke(mbeanServer, args);
                    } catch (Exception ex) {
                    }
                    return null;
                };
                return AccessController.doPrivileged(op, acc);
            }
        }
    }
}
