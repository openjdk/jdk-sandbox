/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static java.lang.StackWalker.Option.RETAIN_CLASS_REFERENCE;

/**
 * A base dummy ldap server.
 *
 * For any ldap tests which required a simple dummy server to support the
 * test, may use this server directly with specifying ConnectionHandler,
 * SessionHandler/RequestHandler, or extends to build more complex server logic.
 *
 * This server already extends Thread and implements AutoCloseable, so it can
 * be started in thread and integrated with try-with-resources
 *
 * To initiate a instance of this server, valid ServerSocket could be supplied,
 * it will allow the flexibility for listening address/port customization
 * and SSL usage, for default no parameter constructor, a ServerSocket which
 * listen on loopback address will be created.
 *
 * To use this dummy server in test, user could customize the processing logic
 * in three level with below handler interface.
 *   -ConnectionHandler provide connection level handling, server will hand
 *                      over accepted socket and processing thread to handler.
 *                      By default, DefaultConnectionHandler will be used if no
 *                      specified, it reads full ldap request message then
 *                      pass it to RequestHandler instance which returned by
 *                      SessionHandler per session.
 *
 *   -SessionHandler    provide session level handling when DefaultConnectionHandler
 *                      been used, it's to retrieve RequestHandler instance of
 *                      current session.
 *                      For most of tests, only one session need to be handled
 *                      on server or all ldap request could be handled by same
 *                      logic whatever current session is, user can use
 *                      setCommonRequestHandler to setup one single session
 *                      handler which will always return given RequestHandler
 *                      instance.
 *
 *   -RequestHandler    provide ldap message request handling when
 *                      DefaultConnectionHandler been used.
 *
 * @see ConnectionHandler
 * @see SessionHandler
 * @see RequestHandler
 */
public class BaseLdapServer extends Thread implements AutoCloseable {
    private volatile boolean isRunning;
    private final List<Socket> socketList = new ArrayList<>();
    private ServerSocket serverSocket;
    private ExecutorService workingPool;
    private ConnectionHandler connectionHandler;
    private SessionHandler sessionHandler;
    private boolean useDaemonThread = false;

    enum DebugLevel {
        FULL,      // all debug message will be printed
        NONE,      // none of debug message will be printed
        CUSTOMIZE  // only specified class debug message will be printed
    }

    private StackWalker stackWalker = null;
    private DebugLevel debugLevel = DebugLevel.NONE;
    private Set<Class<?>> debugOptions = new HashSet<>();

    /**
     * BaseLdapServer overload default constructor.
     *
     * @throws IOException if an I/O error occurs when opening the socket.
     */
    public BaseLdapServer() throws IOException {
        this(new ServerSocket(0, 0, InetAddress.getLoopbackAddress()));
    }

    /**
     * BaseLdapServer overload constructor with given server socket.
     *
     * @param serverSocket given server socket
     */
    public BaseLdapServer(ServerSocket serverSocket) {
        this(serverSocket, false);
    }

    /**
     * BaseLdapServer constructor with given server socket and specify whether
     * use daemon for each accept connection handling thread.
     *
     * @param serverSocket    given server socket
     * @param useDaemonThread <tt>true</tt> if use daemon thread
     */
    public BaseLdapServer(ServerSocket serverSocket, boolean useDaemonThread) {
        this.serverSocket = Objects.requireNonNull(serverSocket);
        this.useDaemonThread = useDaemonThread;
        if (useDaemonThread) {
            workingPool = Executors
                    .newCachedThreadPool(new DefaultDaemonThreadFactory());
        } else {
            workingPool = Executors.newCachedThreadPool();
        }
        try {
            stackWalker = StackWalker.getInstance(RETAIN_CLASS_REFERENCE);
        } catch (SecurityException se) {
            // just ignore
        }
    }

    @Override
    public void run() {
        if (getConnectionHandler() == null) {
            debug("INFO: No connection handler been specified, try default.");
            connectionHandler = new DefaultConnectionHandler();
        }
        debug("INFO: Using connection handler : " + getConnectionHandler()
                .getClass().getName());
        debug("INFO: LdapServer running and listening on port " + getPort());
        try {
            while (isRunning) {
                Socket socket = serverSocket.accept();
                debug("INFO: Accept new connection " + socket);
                synchronized (socketList) {
                    socketList.add(socket);
                }
                workingPool.submit(() -> getConnectionHandler()
                        .handleConnection(socket));
            }
        } catch (IOException | RejectedExecutionException e) {
            if (isRunning) {
                throw new RuntimeException(e);
            } else {
                debug("INFO: Server exit.");
            }
        }
    }

    /*
     * Override Thread.start()
     */
    @Override
    public synchronized void start() {
        super.start();
        isRunning = true;
    }

    /**
     * Start Server thread and return itself for method chaining
     *
     * @return current server instance
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseLdapServer> T startServer() {
        start();
        return (T) this;
    }

    /**
     * Stop server.
     */
    public void stopServer() {
        debug("INFO: Stopping Server.");
        isRunning = false;
        workingPool.shutdown();
        cleanupClosableRes(serverSocket);
        if (!useDaemonThread) {
            // let's cleanup thread pool
            synchronized (socketList) {
                socketList.forEach(BaseLdapServer::cleanupClosableRes);
            }
            try {
                if (!workingPool.awaitTermination(10, TimeUnit.SECONDS)) {
                    workingPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                workingPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Return local port which server is listening.
     *
     * @return port which server is listening
     */
    public int getPort() {
        if (serverSocket != null) {
            return serverSocket.getLocalPort();
        } else {
            return -1;
        }
    }

    /**
     * Return flag to indicate whether current server is running.
     *
     * @return <tt>true</tt> if current server is running.
     */
    public boolean isRunning() {
        return isRunning;
    }

    /**
     * Return ConnectionHandler instance
     *
     * @return ConnectionHandler instance
     * @see ConnectionHandler
     */
    ConnectionHandler getConnectionHandler() {
        return connectionHandler;
    }

    /**
     * Set ConnectionHandler when server is not running.
     *
     * @param connHandler ConnectionHandler instance
     * @return current server instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseLdapServer> T setConnectionHandler(
            ConnectionHandler connHandler) {
        if (!isRunning) {
            connectionHandler = connHandler;
        }

        return (T) this;
    }

    /**
     * Return SessionHandler instance
     *
     * @return SessionHandler instance
     * @see SessionHandler
     */
    SessionHandler getSessionHandler() {
        return sessionHandler;
    }

    /**
     * Set SessionHandler when server is not running.
     *
     * @param sessionHandler given SessionHandler
     * @return current server instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseLdapServer> T setSessionHandler(
            SessionHandler sessionHandler) {
        if (!isRunning) {
            this.sessionHandler = sessionHandler;
        }

        return (T) this;
    }

    /**
     * Set one common RequestHandler, it will be used to handle all requests
     * whatever current session is.
     *
     * For most of tests, server only need to handle one session, use this
     * method will create stateless session handler with given request handler.
     *
     * @param requestHandler RequestHandler instance
     * @return current server instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseLdapServer> T setCommonRequestHandler(
            RequestHandler requestHandler) {
        if (!isRunning) {
            // ignore any session, always return fixed request handler
            setSessionHandler(socket -> requestHandler);
        }

        return (T) this;
    }

    @Override
    public void close() {
        stopServer();
    }

    /**
     * Cleanup any given closable resource
     *
     * @param res given closable resource
     */
    static void cleanupClosableRes(Closeable res) {
        if (res != null) {
            try {
                res.close();
            } catch (IOException e) {
                // ignore
            }
        }
    }

    /**
     * Set debug level to specify which kinds of debug message will be printed.
     *
     * @param debugLevel given debug level
     * @param opts       given opts if debug level is DebugLevel.CUSTOMIZE
     * @return current server instance for method chaining
     */
    @SuppressWarnings("unchecked")
    public <T extends BaseLdapServer> T setDebugLevel(DebugLevel debugLevel,
            Class<?>... opts) {
        Objects.requireNonNull(debugLevel);
        if (!isRunning) {
            this.debugLevel = debugLevel;
            if (debugLevel == DebugLevel.CUSTOMIZE) {
                debugOptions.clear();
                Stream.of(opts).filter(Objects::nonNull)
                        .forEach(debugOptions::add);
            }
        }

        return (T) this;
    }

    /**
     * Print given message if debug enabled.
     *
     * @param message given message to print
     */
    void debug(String message) {
        switch (debugLevel) {
            case FULL:
                System.out.println((stackWalker != null ?
                        stackWalker.getCallerClass().getName() :
                        "") + ": " + message);
                break;
            case CUSTOMIZE:
                if (stackWalker != null) {
                    if (debugOptions.contains(stackWalker.getCallerClass())) {
                        System.out.println(
                                stackWalker.getCallerClass().getName() + ": "
                                        + message);
                    }
                }
                break;
            case NONE:
            default:
                break;
        }
    }

    class DefaultDaemonThreadFactory implements ThreadFactory {

        private ThreadFactory defaultThreadFactory = Executors
                .defaultThreadFactory();

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = defaultThreadFactory.newThread(r);
            thread.setDaemon(true);
            return thread;
        }
    }

    /**
     * Default connection handler implementation.
     */
    class DefaultConnectionHandler implements ConnectionHandler {
        @Override
        public void handleConnection(Socket socket) {
            try (socket;
                    OutputStream out = socket.getOutputStream();
                    InputStream in = socket.getInputStream()) {
                byte[] inBuffer = new byte[1024];
                int count;
                byte[] request;

                ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                int msgLen = -1;

                while ((count = in.read(inBuffer)) > 0) {
                    buffer.write(inBuffer, 0, count);
                    if (msgLen <= 0) {
                        msgLen = getMessageLength(buffer.toByteArray());
                    }

                    if (msgLen > 0 && buffer.size() >= msgLen) {
                        if (buffer.size() > msgLen) {
                            byte[] tmpBuffer = buffer.toByteArray();
                            request = Arrays.copyOf(tmpBuffer, msgLen);
                            buffer.reset();
                            buffer.write(tmpBuffer, msgLen,
                                    tmpBuffer.length - msgLen);
                        } else {
                            request = buffer.toByteArray();
                            buffer.reset();
                        }
                        msgLen = -1;
                    } else {
                        debug("INFO: request msg not complete, received "
                                + buffer.size() + ", expected " + msgLen);
                        continue;
                    }

                    if (getSessionHandler() != null) {
                        var handler = getSessionHandler()
                                .getRequestHandler(socket);
                        if (handler != null) {
                            debug("INFO: Process request. Session handler : "
                                    + getSessionHandler()
                                    + ", Request handler : " + handler);
                            handler.handleRequest(new LdapMessage(request),
                                    out);
                        } else {
                            debug("WARNING: no valid request handler returned from "
                                    + getSessionHandler() + ", " + socket);
                        }
                    } else {
                        debug("WARNING: no valid session handler been specified, discard request.");
                    }
                }
                debug("INFO: Connection Handler exit.");
            } catch (IOException e) {
                if (!isRunning()) {
                    debug("INFO: Connection Handler exit : " + e.getMessage());
                } else {
                    e.printStackTrace();
                }
            }
        }

        private int getMessageLength(byte[] encoding) {
            if (encoding.length < 2) {
                // no enough data to extract msg len, just return -1
                return -1;
            }

            if (encoding[0] != 0x30) {
                throw new RuntimeException("Error: bad LDAP encoding message: "
                        + "expected ASN.1 SEQUENCE tag (0x30), encountered "
                        + encoding[0]);
            }

            int len;
            int index = 1;
            int payloadLen = 0;

            if ((encoding[1] & 0x80) == 0x80) {
                len = (encoding[1] & 0x0F);
                index++;
            } else {
                len = 1;
            }

            if (len > 4) {
                throw new RuntimeException(
                        "Error: LDAP encoding message payload too large");
            }

            if (encoding.length < index + len) {
                // additional data required to extract payload len, return -1
                return -1;
            }

            for (byte b : Arrays.copyOfRange(encoding, index, index + len)) {
                payloadLen = payloadLen << 8 | (b & 0xFF);
            }

            if (payloadLen <= 0) {
                throw new RuntimeException(
                        "Error: invalid LDAP encoding message length or payload too large");
            }

            return index + len + payloadLen;
        }
    }
}
