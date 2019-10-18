/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package sun.nio.ch;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ProtocolFamily;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketOption;
import java.net.SocketOptions;
import java.net.SocketTimeoutException;
import java.net.StandardProtocolFamily;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import jdk.internal.access.JavaNetDatagramPacketAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.ref.CleanerFactory;
import sun.net.PlatformDatagramSocketImpl;
import sun.net.ResourceManager;
import sun.net.ext.ExtendedSocketOptions;
import sun.net.util.IPAddressUtil;
import sun.security.action.GetPropertyAction;
import static java.net.StandardProtocolFamily.INET6;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

/**
 * A DatagramSocketImpl based on low-level NIO primitives.
 */
public class NioDatagramSocketImpl extends PlatformDatagramSocketImpl {

    private static final NativeDispatcher nd = new SocketDispatcher();

    private static final JavaNetDatagramPacketAccess DATAGRAM_PACKET_ACCESS =
            SharedSecrets.getJavaNetDatagramPacketAccess();

    private static final int MAX_PACKET_LEN = 65536;

    private static final ProtocolFamily family = family();

    // Lock held by current reading or connecting thread
    private final ReentrantLock readLock = new ReentrantLock();

    // Lock held by current writing or connecting thread
    private final ReentrantLock writeLock = new ReentrantLock();

    // The stateLock for read/changing state
    private final Object stateLock = new Object();
    private static final int ST_NEW = 0;
    private static final int ST_UNCONNECTED = 1;
    private static final int ST_CONNECTING = 2;
    private static final int ST_CONNECTED = 3;
    private static final int ST_CLOSING = 4;
    private static final int ST_CLOSED = 5;
    private volatile int state;  // need stateLock to change

    // set by create, protected by stateLock
    private FileDescriptorCloser closer;

    // set to true when the socket is in non-blocking mode
    private volatile boolean nonBlocking;

    // used by connect/read/write/accept, protected by stateLock
    private long readerThread;
    private long writerThread;

    // Binding and remote address (when connected)
    private InetSocketAddress remoteAddress;

    // receive timeout in millis
    private volatile int timeout;

    /**  Returns true if the socket is open. */
    private boolean isOpen() {
        return state < ST_CLOSING;
    }

    /** Throws SocketException if the socket is not open. */
    private void ensureOpen() throws SocketException {
        int state = this.state;
        if (state == ST_NEW)
            throw new SocketException("Socket not created");
        if (state >= ST_CLOSING)
            throw new SocketException("Socket closed");
    }

    /**
     * Returns the socket protocol family.
     */
    private static ProtocolFamily family() {
        if (Net.isIPv6Available()) {
            return StandardProtocolFamily.INET6;
        } else {
            return StandardProtocolFamily.INET;
        }
    }

    @Override
    protected void create() throws SocketException {
        synchronized (stateLock) {
            if (state != ST_NEW)
                throw new SocketException("Already created");
            ResourceManager.beforeUdpCreate();
            FileDescriptor fd;
            try {
                fd = Net.socket(false);
            } catch (IOException ioe) {
                ResourceManager.afterUdpClose();
                SocketException se = new SocketException(ioe.getMessage());
                se.initCause(ioe);
                throw se;
            }
            this.fd = fd;
            this.closer = FileDescriptorCloser.create(this);
            this.state = ST_UNCONNECTED;
        }
    }

    @Override
    protected void bind(int port, InetAddress addr) throws SocketException {
        synchronized (stateLock) {
            ensureOpen();
            if (localPort != 0)
                throw new SocketException("Already bound");
            try {
                Net.bind(fd, addr, port);
                localPort = Net.localAddress(fd).getPort();
            } catch (SocketException e) {
                throw e;
            } catch (IOException ioe) {
                SocketException se = new SocketException(ioe.getMessage());
                se.initCause(ioe);
                throw se;
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable e) throws E {
        throw (E) e;
    }

    @Override
    protected int peek(InetAddress i) {
        DatagramPacket packet = new DatagramPacket(new byte[1], 0, 1);
        try {
            receive(packet, true);
            return packet.getPort();
        } catch (IOException e) {
            sneakyThrow(e);
            throw new InternalError("should not reach here");
        }
    }

    @Override
    protected int peekData(DatagramPacket packet) {
        try {
            receive(packet, true);
            return packet.getPort();
        } catch (IOException e) {
            sneakyThrow(e);
            throw new InternalError("should not reach here");
        }
    }

    /**
     * Disables the current thread for scheduling purposes until the socket is
     * ready for I/O, or is asynchronously closed, for up to the specified
     * waiting time.
     * @throws IOException if an I/O error occurs
     */
    private void park(int event, long nanos) throws IOException {
        long millis;
        if (nanos == 0) {
            millis = -1;
        } else {
            millis = NANOSECONDS.toMillis(nanos);
        }
        Net.poll(fd, event, millis);
    }

    /**
     * Disables the current thread for scheduling purposes until the socket is
     * ready for I/O or is asynchronously closed.
     * @throws IOException if an I/O error occurs
     */
    private void park(int event) throws IOException {
        park(event, 0);
    }

    /**
     * Marks the beginning of a write operation that might block.
     * @throws SocketException if the socket is closed or not connected
     */
    private InetSocketAddress beginWrite() throws SocketException {
        synchronized (stateLock) {
            ensureOpen();
            writerThread = NativeThread.current();
            return remoteAddress;
        }
    }
    /**
     * Marks the end of a write operation that may have blocked.
     */
    private void endWrite(boolean completed) throws SocketException {
        synchronized (stateLock) {
            writerThread = 0;
            int state = this.state;
            if (state == ST_CLOSING)
                tryFinishClose();
            if (!completed && state >= ST_CLOSING)
                throw new SocketException("Socket closed");
        }
    }

    /**
     * Attempts to send bytes from the given byte array, to the given (optional)
     * address.
     */
    private int trySend(byte[] b, int off, int len, InetAddress address, int port)
        throws IOException
    {
        ByteBuffer src = Util.getTemporaryDirectBuffer(len);
        assert src.position() == 0 : "Expected source position of 0, in " + src;
        assert src.remaining() == len : "Expected remaining " + len  + ", in " + src;
        try {
            src.put(b, off, len);
            return send0(true, fd, ((DirectBuffer)src).address(), len, address, port);
        } finally {
            Util.offerFirstTemporaryDirectBuffer(src);
        }
    }

    @Override
    protected void send(DatagramPacket p) throws IOException {
        Objects.requireNonNull(p);
        InetSocketAddress target = Net.checkAddress(p.getSocketAddress());
        byte[] b = p.getData();
        int off = p.getOffset();
        int len = p.getLength();
        if (len > MAX_PACKET_LEN)
            len = MAX_PACKET_LEN;

        writeLock.lock();
        try {
            int n = 0;
            InetAddress targetAddress = null;
            int targetPort = 0;
            try {
                SocketAddress remote = beginWrite();
                if (remote != null) {
                    // connected
                    if (!target.equals(remote)) {
                        String msg = "Connected address and packet address differ";
                        throw new IllegalArgumentException(msg);
                    }
                } else {
                    // not connected
                    if (target.getAddress().isLinkLocalAddress())
                        target = IPAddressUtil.toScopedAddress(target);
                    targetAddress = target.getAddress();
                    targetPort = target.getPort();
                }
                n = trySend(b, off, len, targetAddress, targetPort);
                while (IOStatus.okayToRetry(n) && isOpen()) {
                    park(Net.POLLOUT);
                    n = trySend(b, off, len, targetAddress, targetPort);
                }
            } finally {
                endWrite(n > 0);
                assert n >= 0;
            }
        } finally {
            writeLock.unlock();
        }
    }

    /**
     * Configures the socket to blocking mode. This method is a no-op if the
     * socket is already in blocking mode.
     * @throws IOException if closed or there is an I/O error changing the mode
     */
    private void configureBlocking() throws IOException {
        assert readLock.isHeldByCurrentThread();
        if (nonBlocking) {
            synchronized (stateLock) {
                ensureOpen();
                IOUtil.configureBlocking(fd, true);
                nonBlocking = false;
            }
        }
    }

    /**
     * Configures the socket to non-blocking mode. This method is a no-op if the
     * socket is already in non-blocking mode.
     * @throws IOException if closed or there is an I/O error changing the mode
     */
    private void configureNonBlocking() throws IOException {
        assert readLock.isHeldByCurrentThread();
        if (!nonBlocking) {
            synchronized (stateLock) {
                ensureOpen();
                IOUtil.configureBlocking(fd, false);
                nonBlocking = true;
            }
        }
    }

    private InetSocketAddress beginRead() throws SocketException {
        synchronized (stateLock) {
            ensureOpen();
            readerThread = NativeThread.current();
            return remoteAddress;
        }
    }

    private void endRead(boolean completed) throws SocketException {
        synchronized (stateLock) {
            readerThread = 0;
            int state = this.state;
            if (state == ST_CLOSING)
                tryFinishClose();
            if (!completed && state >= ST_CLOSING)
                throw new SocketException("Socket closed");
        }
    }

    private InetSocketAddress sender = new InetSocketAddress(0);  // Set by receive0

    /**
     * Attempts to read bytes from the socket into the given byte array.
     */
    private int tryReceive(byte[] b, int off, int len, boolean isPeek)
        throws IOException
    {
        ByteBuffer dst = Util.getTemporaryDirectBuffer(len);
        assert dst.position() == 0;
        assert dst.remaining() >= len;
        try {
            int n = receive0(fd, ((DirectBuffer)dst).address(), len, isPeek,
                             sender.getAddress(), sender.getPort());
            assert n <= len : "received:" + n + ", expected len:" + len;
            if (n > 0) {
                dst.get(b, off, n);
            }
            return n;
        } finally {
            Util.offerFirstTemporaryDirectBuffer(dst);
        }
    }

    /**
     * Reads bytes from the socket into the given byte array with a timeout.
     * @throws SocketTimeoutException if the read timeout elapses
     */
    private int timedReceive(byte[] b, int off, int len, long nanos, boolean isPeek)
        throws IOException
    {
        long startNanos = System.nanoTime();
        int n = tryReceive(b, off, len, isPeek);
        while (n == IOStatus.UNAVAILABLE && isOpen()) {
            long remainingNanos = nanos - (System.nanoTime() - startNanos);
            if (remainingNanos <= 0) {
                throw new SocketTimeoutException("Receive timed out");
            }
            park(Net.POLLIN, remainingNanos);
            n = tryReceive(b, off, len, isPeek);
        }
        return n;
    }

    @Override
    protected void receive(DatagramPacket p) throws IOException {
        receive(p, false);
    }

    private void receive(DatagramPacket p, boolean isPeek) throws IOException {
        Objects.requireNonNull(p);
        byte[] b = p.getData();
        int off = p.getOffset();
        int len = DATAGRAM_PACKET_ACCESS.getBufLengthField(p);
        assert len >= 0;
        if (len > MAX_PACKET_LEN)
            len = MAX_PACKET_LEN;

        readLock.lock();
        try {
            int n = 0;
            try {
                SocketAddress remote = beginRead();
                boolean connected = (remote != null);
                int timeout = this.timeout;
                if (timeout > 0) {
                    // receive with timeout
                    configureNonBlocking();
                    long nanos = MILLISECONDS.toNanos(timeout);
                    n = timedReceive(b, off, len, nanos, isPeek);
                } else {
                    // receive, no timeout
                    n = tryReceive(b, off, len, isPeek);
                    while (IOStatus.okayToRetry(n) && isOpen()) {
                        park(Net.POLLIN);
                        n = tryReceive(b, off, len, isPeek);
                    }
                }
                assert n > 0;
                assert sender != null;
                if (p.getAddress() == null || !p.getAddress().equals(sender.getAddress()))
                    p.setAddress(sender.getAddress());
                if (p.getPort() != sender.getPort())
                    p.setPort(sender.getPort());
                DATAGRAM_PACKET_ACCESS.setLengthField(p, n);
            } catch (IOException e) {
                // #### reset packet offset and length! ??
                throw e;
            } finally {
                endRead(n > 0);
                assert IOStatus.check(n);
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    protected void connect(InetAddress address, int port) throws SocketException {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                synchronized (stateLock) {
                    ensureOpen();
                    if (state == ST_CONNECTED) {
                        // #### already connected? throw? or connect to new remote
                    }

                    int n = Net.connect(family, fd, address, port);
                    if (n <= 0)
                        throw new InternalError("should not reach here");

                    remoteAddress = new InetSocketAddress(address, port);
                    state = ST_CONNECTED;

                    // refresh local address
                    localPort = Net.localAddress(fd).getPort();

                    // flush any packets already received.
                    try {
                        byte[] ba = new byte[1];
                        configureNonBlocking();
                        while (tryReceive(ba, 0, 1, false) > 0) { }
                    } finally {
                        configureBlocking();
                    }
                }
            } catch (SocketException e) {
                throw e;
            } catch (IOException e) {
                throw new SocketException(e.getMessage());
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    @Override
    protected void disconnect() {
        readLock.lock();
        try {
            writeLock.lock();
            try {
                synchronized (stateLock) {
                    if (!isOpen() || (state != ST_CONNECTED))
                        return;

                    try {
                        disconnect0(fd, family == INET6);

                        // no longer connected
                        remoteAddress = null;
                        state = ST_UNCONNECTED;

                        // check whether rebind is needed
                        InetSocketAddress isa = Net.localAddress(fd);
                        if (isa.getPort() == 0) {
                            // On Linux, if bound to ephemeral port,
                            // disconnect does not preserve that port.
                            // In this case, try to rebind to the previous port.
                            int port = localPort;
                            Net.bind(family, fd, isa.getAddress(), port);
                            isa = Net.localAddress(fd); // refresh address
                            assert isa.getPort() == port;
                        }

                        // refresh local port
                        localPort = isa.getPort();
                    } catch (IOException e) {
                        sneakyThrow(e);
                        throw new InternalError("should not reach here");
                    }
                }
            } finally {
                writeLock.unlock();
            }
        } finally {
            readLock.unlock();
        }
    }

    /**
     * Closes the socket if there are no I/O operations in progress.
     */
    private boolean tryClose() throws IOException {
        assert Thread.holdsLock(stateLock) && state == ST_CLOSING;
        if (readerThread == 0 && writerThread == 0) {
            try {
                closer.run();
            } catch (UncheckedIOException ioe) {
                throw ioe.getCause();
            } finally {
                state = ST_CLOSED;
            }
            return true;
        } else {
            return false;
        }
    }

    /**
     * Invokes tryClose to attempt to close the socket.
     *
     * This method is used for deferred closing by I/O operations.
     */
    private void tryFinishClose() {
        try {
            tryClose();
        } catch (IOException ignore) { }
    }

    /**
     * Closes the socket. If there are I/O operations in progress then the
     * socket is pre-closed and the threads are signalled. The socket will be
     * closed when the last I/O operation aborts.
     */
    @Override
    protected void close() {
        synchronized (stateLock) {
            int state = this.state;
            if (state >= ST_CLOSING)
                return;
            if (state == ST_NEW) {
                this.state = ST_CLOSED;
                return;
            }
            this.state = ST_CLOSING;

            // Attempt to close the socket. If there are I/O operations in
            // progress then the socket is pre-closed and the thread(s)
            // signalled. The last thread will close the file descriptor.
            try {
                if (!tryClose()) {
                    nd.preClose(fd);
                    long reader = readerThread;
                    if (reader != 0)
                        NativeThread.signal(reader);
                    long writer = writerThread;
                    if (writer != 0)
                        NativeThread.signal(writer);
                }
            } catch (IOException e) {
                throw new UncheckedIOException(e);  // Ugh!
            }
        }
    }

    private static final Set<SocketOption<?>> socketOptions = socketOptions();

    private static Set<SocketOption<?>> socketOptions() {
        HashSet<SocketOption<?>> options = new HashSet<>();
        options.add(StandardSocketOptions.SO_SNDBUF);
        options.add(StandardSocketOptions.SO_RCVBUF);
        options.add(StandardSocketOptions.SO_REUSEADDR);
        options.add(StandardSocketOptions.IP_TOS);
        if (Net.isReusePortAvailable())
            options.add(StandardSocketOptions.SO_REUSEPORT);
        options.addAll(ExtendedSocketOptions.datagramSocketOptions());
        return Collections.unmodifiableSet(options);
    }

    @Override
    protected Set<SocketOption<?>> supportedOptions() {
        return socketOptions;
    }

    @Override
    protected <T> void setOption(SocketOption<T> opt, T value) throws IOException {
        if (!supportedOptions().contains(opt))
            throw new UnsupportedOperationException("'" + opt + "' not supported");
        if (!opt.type().isInstance(value))
            throw new IllegalArgumentException("Invalid value '" + value + "'");
        synchronized (stateLock) {
            ensureOpen();
            if (opt == StandardSocketOptions.IP_TOS) {
                // maps to IP_TOS or IPV6_TCLASS
                Net.setSocketOption(fd, family(), opt, value);
            } else if (opt == StandardSocketOptions.SO_REUSEADDR) {
                setOption(SocketOptions.SO_REUSEADDR, value);
            } else if (opt == StandardSocketOptions.SO_REUSEPORT) {
                setOption(SocketOptions.SO_REUSEPORT, value);
            } else {
                // option does not need special handling
                Net.setSocketOption(fd, opt, value);
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T> T getOption(SocketOption<T> opt) throws IOException {
        if (!supportedOptions().contains(opt))
            throw new UnsupportedOperationException("'" + opt + "' not supported");
        synchronized (stateLock) {
            ensureOpen();
            if (opt == StandardSocketOptions.IP_TOS) {
                return (T) Net.getSocketOption(fd, family(), opt);
            } else if (opt == StandardSocketOptions.SO_REUSEADDR) {
                return (T) getOption(SocketOptions.SO_REUSEADDR);
            } else if (opt == StandardSocketOptions.SO_REUSEPORT) {
                return (T) getOption(SocketOptions.SO_REUSEPORT);
            } else {
                // option does not need special handling
                return (T) Net.getSocketOption(fd, opt);
            }
        }
    }

    private static boolean booleanValue(Object value, String desc)
        throws SocketException
    {
        if (!(value instanceof Boolean))
            throw new SocketException("Bad value for " + desc);
        return (boolean) value;
    }

    private static int intValue(Object value, String desc) throws SocketException {
        if (!(value instanceof Integer))
            throw new SocketException("Bad value for " + desc);
        return (int) value;
    }

    @Override
    public void setOption(int opt, Object value) throws SocketException {
        synchronized (stateLock) {
            ensureOpen();
            try {
                switch (opt) {
                case SO_TIMEOUT: {
                    int i = intValue(value, "SO_TIMEOUT");
                    if (i < 0)
                        throw new IllegalArgumentException("timeout < 0");
                    timeout = i;
                    break;
                }
                case IP_TOS: {
                    int i = intValue(value, "IP_TOS");
                    Net.setSocketOption(fd, family, StandardSocketOptions.IP_TOS, i);
                    break;
                }
                case SO_REUSEADDR: {
                    boolean b = booleanValue(value, "SO_REUSEADDR");
                    Net.setSocketOption(fd, StandardSocketOptions.SO_REUSEADDR, b);
                    break;
                }
                case SO_BROADCAST: {
                    boolean b = booleanValue(value, "SO_BROADCAST");
                    Net.setSocketOption(fd, StandardSocketOptions.SO_BROADCAST, b);
                    break;
                }
                case SO_BINDADDR: {
                    throw new SocketException("Cannot re-bind Socket");
                }
                case SO_RCVBUF: {
                    int i = intValue(value, "SO_RCVBUF");
                    if (i <= 0)
                        throw new SocketException("SO_RCVBUF <= 0");
                    Net.setSocketOption(fd, StandardSocketOptions.SO_RCVBUF, i);
                    break;
                }
                case SO_SNDBUF: {
                    int i = intValue(value, "SO_SNDBUF");
                    if (i <= 0)
                        throw new SocketException("SO_SNDBUF <= 0");
                    Net.setSocketOption(fd, StandardSocketOptions.SO_SNDBUF, i);
                    break;
                }
                case SO_REUSEPORT: {
                    if (!Net.isReusePortAvailable())
                        throw new UnsupportedOperationException("SO_REUSEPORT not supported");
                    boolean b = booleanValue(value, "SO_REUSEPORT");
                    Net.setSocketOption(fd, StandardSocketOptions.SO_REUSEPORT, b);
                    break;
                }
                default:
                    throw new SocketException("unknown option: " + opt);
                }
            } catch (SocketException e) {
                throw e;
            } catch (IllegalArgumentException | IOException e) {
                throw new SocketException(e.getMessage());
            }
        }
    }

    @Override
    public Object getOption(int opt) throws SocketException {
        synchronized (stateLock) {
            ensureOpen();
            try {
                switch (opt) {
                case SO_TIMEOUT:
                    return timeout;
                case IP_TOS:
                    return Net.getSocketOption(fd, family(), StandardSocketOptions.IP_TOS);
                case SO_BINDADDR:
                    return Net.localAddress(fd).getAddress();
                case SO_RCVBUF:
                    return Net.getSocketOption(fd, StandardSocketOptions.SO_RCVBUF);
                case SO_SNDBUF:
                    return Net.getSocketOption(fd, StandardSocketOptions.SO_SNDBUF);
                case SO_REUSEADDR:
                    return Net.getSocketOption(fd, StandardSocketOptions.SO_REUSEADDR);
                case SO_BROADCAST:
                    return Net.getSocketOption(fd, StandardSocketOptions.SO_BROADCAST);
                case SO_REUSEPORT:
                    if (!Net.isReusePortAvailable())
                        throw new UnsupportedOperationException("SO_REUSEPORT not supported");
                    return Net.getSocketOption(fd, StandardSocketOptions.SO_REUSEPORT);
                default:
                    throw new SocketException("Unknown option " + opt);
                }
            } catch (SocketException e) {
                throw e;
            } catch (IllegalArgumentException | IOException e) {
                throw new SocketException(e.getMessage());
            }
        }
    }

    /**
     * A task that closes a DatagramSocketImpl's file descriptor. The task is
     * run when the NioDatagramSocketImpl is explicitly closed or when the
     * NioDatagramSocketImpl becomes phantom reachable.
     */
    private static class FileDescriptorCloser implements Runnable {
        private static final VarHandle CLOSED;
        static {
            try {
                MethodHandles.Lookup l = MethodHandles.lookup();
                CLOSED = l.findVarHandle(FileDescriptorCloser.class,
                                         "closed",
                                         boolean.class);
            } catch (Exception e) {
                throw new InternalError(e);
            }
        }
        private volatile boolean closed;  // accessed through VarHandle
        private final FileDescriptor fd;

        FileDescriptorCloser(FileDescriptor fd) { this.fd = fd; }

        static FileDescriptorCloser create(NioDatagramSocketImpl impl) {
            assert Thread.holdsLock(impl.stateLock);
            var closer = new FileDescriptorCloser(impl.fd);
            CleanerFactory.cleaner().register(impl, closer);
            return closer;
        }

        @Override
        public void run() {
            if (CLOSED.compareAndSet(this, false, true)) {
                try {
                    nd.close(fd);
                } catch (IOException ioe) {
                    throw new UncheckedIOException(ioe);
                } finally {
                    ResourceManager.afterUdpClose();
                }
            }
        }
    }

    @Deprecated
    @Override
    protected void setTTL(byte ttl) {
        throw new InternalError("should not reach here");
    }

    @Deprecated
    @Override
    protected byte getTTL() {
        throw new InternalError("should not reach here");
    }

    @Override
    protected void setTimeToLive(int ttl) {
        throw new InternalError("should not reach here");
    }

    @Override
    protected int getTimeToLive() {
        throw new InternalError("should not reach here");
    }

    @Override
    protected void join(InetAddress inetaddr) {
        throw new InternalError("should not reach here");
    }

    @Override
    protected void leave(InetAddress inetaddr) {
        throw new InternalError("should not reach here");
    }

    @Override
    protected void joinGroup(SocketAddress mcastaddr, NetworkInterface netIf) {
        throw new InternalError("should not reach here");
    }

    @Override
    protected void leaveGroup(SocketAddress mcastaddr, NetworkInterface netIf) {
        throw new InternalError("should not reach here");
    }

    /** Set if the native connect() call is not to be used */
    private static final boolean connectDisabled =
            GetPropertyAction.privilegedGetProperty("os.name").contains("OS X");

    @Override
    public boolean nativeConnectDisabled() {
        return connectDisabled;
    }

    // -- Native methods --

    private static native void initIDs();

    private native int receive0(FileDescriptor fd,
                                long address,
                                int len,
                                boolean isPeek,
                                InetAddress cachedSenderAddress,
                                int cachedSenderPort)
        throws IOException;

    private static native int send0(boolean preferIPv6,
                                    FileDescriptor fd,
                                    long address,
                                    int len,
                                    InetAddress addr,
                                    int port)
        throws IOException;

    private static native void disconnect0(FileDescriptor fd,
                                           boolean isIPv6)
        throws IOException;

    static {
        IOUtil.load();
        initIDs();
    }
}
