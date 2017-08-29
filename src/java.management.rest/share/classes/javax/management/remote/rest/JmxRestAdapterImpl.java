/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package javax.management.remote.rest;

import com.oracle.jmx.remote.rest.http.PostRequestHandler;
import com.oracle.jmx.remote.rest.http.GetRequestHandler;
import com.sun.jmx.remote.security.JMXPluggableAuthenticator;
import com.sun.jmx.remote.security.JMXSubjectDomainCombiner;
import com.sun.jmx.remote.security.SubjectDelegator;
import com.sun.net.httpserver.*;

import javax.management.*;
import javax.management.relation.MBeanServerNotificationFilter;
import javax.management.remote.JMXAuthenticator;
import com.oracle.jmx.remote.rest.json.JSONArray;
import com.oracle.jmx.remote.rest.json.JSONObject;
import com.oracle.jmx.remote.rest.json.JSONPrimitive;
import com.oracle.jmx.remote.rest.mapper.JSONMappingFactory;
import javax.security.auth.Subject;
import java.io.*;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.*;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.*;
import com.sun.net.httpserver.Authenticator;

/**
 * @author harsha
 */
public final class JmxRestAdapterImpl implements JmxRestAdapter, NotificationListener {

    // TODO: These should be wrapped in ReadWriteLock
    private static final Map<String, MBeanServer> authMBeanServer = new HashMap<>();
    final List<String> allowedMbeans = new ArrayList<>();
    private final HttpServer httpServer;
    private final String contextStr;
    private final Map<String, ?> env;
    private final MBeanServer mbeanServer;
    private final String realm = "";
    private final GetRequestHandler getHandler;
    private final PostRequestHandler postHandler;
    private HttpContext httpContext;
    private JMXAuthenticator authenticator = null;
    private boolean started = false;

    JmxRestAdapterImpl(HttpServer hServer, String context, Map<String, ?> env, MBeanServer mbeanServer) {
        httpServer = hServer;
        this.contextStr = context;
        this.env = env;
        this.mbeanServer = mbeanServer;
        if (env.get("jmx.remote.x.authentication") != null) {
            authenticator = (JMXAuthenticator) env.get(JmxRestAdapterImpl.AUTHENTICATOR);
            if (authenticator == null) {
                if (env.get("jmx.remote.x.password.file") != null
                        || env.get("jmx.remote.x.login.config") != null) {
                    authenticator = new JMXPluggableAuthenticator(env);
                } else {
                    // Throw exception for invalid authentication config
                }
            }
        }
        introspectMBeanTypes(mbeanServer);
        MBeanServerNotificationFilter filter = new MBeanServerNotificationFilter();
        filter.enableAllObjectNames();
        try {
            mbeanServer.addNotificationListener(MBeanServerDelegate.DELEGATE_NAME, this, filter, null);
        } catch (InstanceNotFoundException ex) {
        }
        getHandler = new GetRequestHandler(mbeanServer, allowedMbeans);
        postHandler = new PostRequestHandler(mbeanServer, allowedMbeans);
    }

    @Override
    public synchronized void start() {
        if (!started) {
            httpContext = httpServer.createContext("/jmx/" + contextStr + "/", new DefaultHTTPHandler());
            if (env.get("jmx.remote.x.authentication") != null) {
                httpContext.setAuthenticator(new BasicAuthenticator());
            }
            started = true;
        }
    }

    @Override
    public synchronized void stop() {
        if (!started) {
            throw new IllegalStateException("Rest Adapter not started yet");
        }
        httpServer.removeContext(httpContext);
        started = false;
    }

    @Override
    public String getBaseUrl() {
        if (!started) {
            throw new IllegalStateException("Adapter not started");
        }
        try {
            if (httpServer instanceof HttpsServer) {
                return "https://" + InetAddress.getLocalHost().getHostName() + ":" + httpServer.getAddress().getPort() + "/jmx/" + contextStr + "/";
            }
            return "http://" + InetAddress.getLocalHost().getHostName() + ":" + httpServer.getAddress().getPort() + "/jmx/" + contextStr + "/";
        } catch (UnknownHostException ex) {
            return "http://localhost" + ":" + httpServer.getAddress().getPort() + "/jmx/" + contextStr + "/";
        }
    }

    @Override
    public MBeanServer getMBeanServer() {
        return mbeanServer;
    }

    private boolean isMBeanAllowed(ObjectName objName) {
        try {
            MBeanInfo mInfo = mbeanServer.getMBeanInfo(objName);
            MBeanAttributeInfo[] attrsInfo = mInfo.getAttributes();
            for (MBeanAttributeInfo attrInfo : attrsInfo) {
                String type = attrInfo.getType();
                if (!JSONMappingFactory.INSTANCE.isTypeMapped(type)) {
                    return false;
                }
            }
            MBeanOperationInfo[] operations = mInfo.getOperations();
            for (MBeanOperationInfo opInfo : operations) {
                MBeanParameterInfo[] signature = opInfo.getSignature();
                for (MBeanParameterInfo sig : signature) {
                    if (!JSONMappingFactory.INSTANCE.isTypeMapped(sig.getType())) {
                        return false;
                    }
                }
                if (!JSONMappingFactory.INSTANCE.isTypeMapped(opInfo.getReturnType())) {
                    return false;
                }
            }
            return true;
        } catch (InstanceNotFoundException | IntrospectionException | ReflectionException | ClassNotFoundException ex) {
            ex.printStackTrace();
            return false;
        }
    }

    private void introspectMBeanTypes(MBeanServer server) {
        if (allowedMbeans.isEmpty()) {
            Set<ObjectInstance> allMBeans = server.queryMBeans(null, null); // get all Mbeans
            allMBeans.stream().filter((objIns) -> (isMBeanAllowed(objIns.getObjectName())))
                    .forEachOrdered(objIns -> allowedMbeans.add(objIns.getObjectName().toString()));
        }
    }

    @Override
    public void handleNotification(Notification notification, Object handback) {
        try {
            MBeanServerNotification mbs = (MBeanServerNotification) notification;
            if (MBeanServerNotification.REGISTRATION_NOTIFICATION.equals(mbs.getType())) {
                ObjectName mBeanName = mbs.getMBeanName();
                if (isMBeanAllowed(mBeanName)) {
                    allowedMbeans.add(mBeanName.toString());
                }
            } else if (MBeanServerNotification.UNREGISTRATION_NOTIFICATION.equals(mbs.getType())) {
                if (allowedMbeans.contains(mbs.getMBeanName().toString())) {
                    allowedMbeans.remove(mbs.getMBeanName().toString());
                }
            }
        } catch (Exception e) {
        }
    }

    private MBeanServer getMBeanServerProxy(MBeanServer mbeaServer, Subject subject) {
        return (MBeanServer) Proxy.newProxyInstance(MBeanServer.class.getClassLoader(),
                new Class<?>[]{MBeanServer.class},
                new AuthInvocationHandler(mbeaServer, subject));
    }

    private static class HttpUtil {

        public static String getRequestCharset(HttpExchange ex) {
            String charset = null;
            List<String> contentType = ex.getRequestHeaders().get("Content-type");
            if (contentType != null) {
                for (String kv : contentType) {
                    for (String value : kv.split(";")) {
                        value = value.trim();
                        if (value.toLowerCase().startsWith("charset=")) {
                            charset = value.substring("charset=".length());
                        }
                    }
                }
            }
            return charset;
        }

        public static String getAcceptCharset(HttpExchange ex) {
            List<String> acceptCharset = ex.getRequestHeaders().get("Accept-Charset");
            if (acceptCharset != null && acceptCharset.size() > 0) {
                return acceptCharset.get(0);
            }
            return null;
        }

        public static String getGetRequestResource(HttpExchange ex, String charset) throws UnsupportedEncodingException {
            String httpHandlerPath = ex.getHttpContext().getPath();
            String requestURIpath = ex.getRequestURI().getPath();
            String getRequestPath = requestURIpath.substring(httpHandlerPath.length());
            if (charset != null) {
                return URLDecoder.decode(getRequestPath, charset);
            } else {
                return getRequestPath;
            }
        }

        public static String getGetRequestQuery(HttpExchange ex, String charset) throws UnsupportedEncodingException {
            String query = ex.getRequestURI().getQuery();
            if (charset != null && query != null) {
                return URLDecoder.decode(query, charset);
            } else {
                return query;
            }
        }
    }

    private class BasicAuthenticator extends Authenticator {

        @Override
        public Authenticator.Result authenticate(HttpExchange he) {
            Headers rmap = he.getRequestHeaders();
            String auth = rmap.getFirst("Authorization");
            if (auth == null) {
                Headers map = he.getResponseHeaders();
                map.set("WWW-Authenticate", "Basic realm=" + realm);
                return new Authenticator.Retry(401);
            }
            int sp = auth.indexOf(' ');
            if (sp == -1 || !auth.substring(0, sp).equals("Basic")) {
                return new Authenticator.Failure(401);
            }
            byte[] b = Base64.getDecoder().decode(auth.substring(sp + 1));
            String credentials = new String(b);
            int colon = credentials.indexOf(':');
            String uname = credentials.substring(0, colon);
            String pass = credentials.substring(colon + 1);

            if (authMBeanServer.containsKey(credentials)) {
                return new Authenticator.Success(new HttpPrincipal(uname, realm));
            } else {
                Subject subject = null;
                if (authenticator != null) {
                    String[] credential = new String[]{uname, pass};
                    try {
                        subject = authenticator.authenticate(credential);
                    } catch (SecurityException e) {
                        return new Authenticator.Failure(400);
                    }
                }
                MBeanServer proxy = getMBeanServerProxy(mbeanServer, subject);
                authMBeanServer.put(credentials, proxy);
                return new Authenticator.Success(new HttpPrincipal(uname, realm));
            }
        }
    }

    private class AuthInvocationHandler implements InvocationHandler {

        private final MBeanServer mbeanServer;
        private final AccessControlContext acc;

        public AuthInvocationHandler(MBeanServer server, Subject subject) {
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

    private class DefaultHTTPHandler implements HttpHandler {

        Map<String, List<String>> allowedMbeans = new HashMap<>();

        DefaultHTTPHandler() {
        }

        @Override
        public void handle(HttpExchange he) throws IOException {
            MBeanServer server = mbeanServer;
            if (env.get("jmx.remote.x.authentication") != null) {
                Headers rmap = he.getRequestHeaders();
                String auth = rmap.getFirst("Authorization");
                int sp = auth.indexOf(' ');
                byte[] b = Base64.getDecoder().decode(auth.substring(sp + 1));
                String authCredentials = new String(b);
                server = authMBeanServer.get(authCredentials);
                if (server == null) {
                    throw new IllegalArgumentException("Invalid HTTP request Headers");
                }
            }

            String charset = HttpUtil.getRequestCharset(he);
            try {
                switch (he.getRequestMethod()) {
                    case "GET":
                        JSONObject resp = getHandler.handle(HttpUtil.getGetRequestResource(he, charset),
                                HttpUtil.getGetRequestQuery(he, charset));
                        sendResponse(he, resp.toJsonString(), ((Long) (((JSONPrimitive) resp.get("status")).getValue())).intValue(), charset);
                        break;
                    case "POST":
                        String requestBody = readRequestBody(he, charset);
                        List<JSONObject> responses = postHandler.handle(requestBody);
                        if (responses.size() == 1) {
                            JSONObject jobj = responses.get(0);
                            sendResponse(he, jobj.toJsonString(), ((Long) (((JSONPrimitive) responses.get(0).get("status")).getValue())).intValue(), charset);
                        } else {
                            int finalCode = HttpURLConnection.HTTP_OK;
                            boolean isHttpOkPresent = responses.stream()
                                    .filter(r -> ((Long) (((JSONPrimitive) responses.get(0).get("status")).getValue())).intValue() == HttpURLConnection.HTTP_OK)
                                    .findFirst().isPresent();
                            if (!isHttpOkPresent) {
                                finalCode = ((Long) (((JSONPrimitive) responses.get(0).get("status")).getValue())).intValue();
                            }

                            JSONArray jarr = new JSONArray();
                            responses.forEach(r -> jarr.add(r));
                            JSONObject jobj = new JSONObject();
                            jobj.put("status", new JSONPrimitive(finalCode));
                            jobj.put("result", jarr);
                            String finalResult = jobj.toJsonString();
                            sendResponse(he, finalResult, finalCode, charset);
                        }
                        break;
                    default:
                        sendResponse(he, "Not supported", HttpURLConnection.HTTP_BAD_METHOD, charset);
                        break;
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }

        private String readRequestBody(HttpExchange he, String charset) throws IOException {
            StringBuilder stringBuilder = new StringBuilder();
            InputStreamReader in = charset != null ? new InputStreamReader(he.getRequestBody(), charset) : new InputStreamReader(he.getRequestBody());
            BufferedReader br = new BufferedReader(in);
            String line;
            while ((line = br.readLine()) != null) {
                String decode = charset != null ? URLDecoder.decode(line, charset) : line;
                stringBuilder.append(decode);
            }
            return stringBuilder.toString();
        }

        private void sendResponse(HttpExchange exchange, String response, int code, String charset) throws IOException {
            String acceptCharset = HttpUtil.getAcceptCharset(exchange);
            if (acceptCharset != null) {
                charset = acceptCharset;
            }

            // Set response headers explicitly
            String msg = charset == null ? response : URLEncoder.encode(response, charset);
            byte[] bytes = msg.getBytes();
            Headers resHeaders = exchange.getResponseHeaders();
            resHeaders.add("Content-Type", "application/json; charset=" + charset);

            exchange.sendResponseHeaders(code, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}
