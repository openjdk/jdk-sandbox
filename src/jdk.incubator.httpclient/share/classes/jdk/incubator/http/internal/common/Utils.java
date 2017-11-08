/*
 * Copyright (c) 2015, 2016, Oracle and/or its affiliates. All rights reserved.
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

package jdk.incubator.http.internal.common;

import jdk.internal.misc.InnocuousThread;
import sun.net.NetProperties;
import sun.net.util.IPAddressUtil;

import javax.net.ssl.SSLParameters;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetSocketAddress;
import java.net.NetPermission;
import java.net.URI;
import java.net.URLPermission;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Predicate;
import java.util.function.Supplier;
import jdk.incubator.http.HttpHeaders;

/**
 * Miscellaneous utilities
 */
public final class Utils {

    public static final boolean ASSERTIONSENABLED;
    static {
        boolean enabled = false;
        assert enabled = true;
        ASSERTIONSENABLED = enabled;
    }
    public static final boolean TESTING;
    static {
        if (ASSERTIONSENABLED) {
            PrivilegedAction<String> action = () -> System.getProperty("test.src");
            TESTING = AccessController.doPrivileged(action) != null;
        } else TESTING = false;
    }
    public static final boolean DEBUG = // Revisit: temporary dev flag.
            getBooleanProperty(DebugLogger.HTTP_NAME, false);
    public static final boolean DEBUG_HPACK = // Revisit: temporary dev flag.
            getBooleanProperty(DebugLogger.HPACK_NAME, false);

    /**
     * Allocated buffer size. Must never be higher than 16K. But can be lower
     * if smaller allocation units preferred. HTTP/2 mandates that all
     * implementations support frame payloads of at least 16K.
     */
    public static final int DEFAULT_BUFSIZE = 16 * 1024;

    public static final int BUFSIZE = getIntegerNetProperty(
            "jdk.httpclient.bufsize", DEFAULT_BUFSIZE
    );

    private static final Set<String> DISALLOWED_HEADERS_SET = Set.of(
            "authorization", "connection", "cookie", "content-length",
            "date", "expect", "from", "host", "origin", "proxy-authorization",
            "referer", "user-agent", "upgrade", "via", "warning");

    public static final Predicate<String>
        ALLOWED_HEADERS = header -> !Utils.DISALLOWED_HEADERS_SET.contains(header);

    public static final Predicate<String>
        ALL_HEADERS = header -> true;

    public static ByteBuffer getBuffer() {
        return ByteBuffer.allocate(BUFSIZE);
    }

    // Used when we know the max amount we want to put in the buffer
    // In that case there's no reason to allocate a greater amount.
    // Still not allow to allocate more than BUFSIZE.
    public static ByteBuffer getBufferWithAtMost(int maxAmount) {
        return ByteBuffer.allocate(Math.min(BUFSIZE, maxAmount));
    }

    public static Throwable getCompletionCause(Throwable x) {
        if (!(x instanceof CompletionException)) return x;
        final Throwable cause = x.getCause();
        return cause == null ? x : cause;
    }

    public static IOException getIOException(Throwable t) {
        if (t instanceof IOException) {
            return (IOException) t;
        }
        Throwable cause = t.getCause();
        if (cause != null) {
            return getIOException(cause);
        }
        return new IOException(t);
    }

    /**
     * Puts position to limit and limit to capacity so we can resume reading
     * into this buffer, but if required > 0 then limit may be reduced so that
     * no more than required bytes are read next time.
     */
    static void resumeChannelRead(ByteBuffer buf, int required) {
        int limit = buf.limit();
        buf.position(limit);
        int capacity = buf.capacity() - limit;
        if (required > 0 && required < capacity) {
            buf.limit(limit + required);
        } else {
            buf.limit(buf.capacity());
        }
    }

    private Utils() { }

    // ABNF primitives defined in RFC 7230
    private static final boolean[] tchar      = new boolean[256];
    private static final boolean[] fieldvchar = new boolean[256];

    static {
        char[] allowedTokenChars =
                ("!#$%&'*+-.^_`|~0123456789" +
                 "abcdefghijklmnopqrstuvwxyz" +
                 "ABCDEFGHIJKLMNOPQRSTUVWXYZ").toCharArray();
        for (char c : allowedTokenChars) {
            tchar[c] = true;
        }
        for (char c = 0x21; c < 0xFF; c++) {
            fieldvchar[c] = true;
        }
        fieldvchar[0x7F] = false; // a little hole (DEL) in the range
    }

    /*
     * Validates a RFC 7230 field-name.
     */
    public static boolean isValidName(String token) {
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 255 || !tchar[c]) {
                return false;
            }
        }
        return !token.isEmpty();
    }

    /**
     * If the address was created with a domain name, then return
     * the domain name string. If created with a literal IP address
     * then return null. We do this to avoid doing a reverse lookup
     * Used to populate the TLS SNI parameter. So, SNI is only set
     * when a domain name was supplied.
     */
    public static String getServerName(InetSocketAddress addr) {
        String host = addr.getHostString();
        if (IPAddressUtil.textToNumericFormatV4(host) != null)
            return null;
        if (IPAddressUtil.textToNumericFormatV6(host) != null)
            return null;
        return host;
    }

    /*
     * Validates a RFC 7230 field-value.
     *
     * "Obsolete line folding" rule
     *
     *     obs-fold = CRLF 1*( SP / HTAB )
     *
     * is not permitted!
     */
    public static boolean isValidValue(String token) {
        boolean accepted = true;
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            if (c > 255) {
                return false;
            }
            if (accepted) {
                if (c == ' ' || c == '\t') {
                    accepted = false;
                } else if (!fieldvchar[c]) {
                    return false; // forbidden byte
                }
            } else {
                if (c != ' ' && c != '\t') {
                    if (fieldvchar[c]) {
                        accepted = true;
                    } else {
                        return false; // forbidden byte
                    }
                }
            }
        }
        return accepted;
    }

    public static void checkNetPermission(String target) {
        SecurityManager sm = System.getSecurityManager();
        if (sm == null) {
            return;
        }
        NetPermission np = new NetPermission(target);
        sm.checkPermission(np);
    }

    public static void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static int getIntegerNetProperty(String name, int defaultValue) {
        return AccessController.doPrivileged((PrivilegedAction<Integer>) () ->
                NetProperties.getInteger(name, defaultValue));
    }

    static String getNetProperty(String name) {
        return AccessController.doPrivileged((PrivilegedAction<String>) () ->
                NetProperties.get(name));
    }

    static boolean getBooleanProperty(String name, boolean def) {
        return AccessController.doPrivileged((PrivilegedAction<Boolean>) () ->
                Boolean.parseBoolean(System.getProperty(name, String.valueOf(def))));
    }

    public static SSLParameters copySSLParameters(SSLParameters p) {
        SSLParameters p1 = new SSLParameters();
        p1.setAlgorithmConstraints(p.getAlgorithmConstraints());
        p1.setCipherSuites(p.getCipherSuites());
        // JDK 8 EXCL START
        p1.setEnableRetransmissions(p.getEnableRetransmissions());
        p1.setMaximumPacketSize(p.getMaximumPacketSize());
        // JDK 8 EXCL END
        p1.setEndpointIdentificationAlgorithm(p.getEndpointIdentificationAlgorithm());
        p1.setNeedClientAuth(p.getNeedClientAuth());
        String[] protocols = p.getProtocols();
        if (protocols != null) {
            p1.setProtocols(protocols.clone());
        }
        p1.setSNIMatchers(p.getSNIMatchers());
        p1.setServerNames(p.getServerNames());
        p1.setUseCipherSuitesOrder(p.getUseCipherSuitesOrder());
        p1.setWantClientAuth(p.getWantClientAuth());
        return p1;
    }

    /**
     * Set limit to position, and position to mark.
     */
    public static void flipToMark(ByteBuffer buffer, int mark) {
        buffer.limit(buffer.position());
        buffer.position(mark);
    }

    public static String stackTrace(Throwable t) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        String s = null;
        try {
            PrintStream p = new PrintStream(bos, true, "US-ASCII");
            t.printStackTrace(p);
            s = bos.toString("US-ASCII");
        } catch (UnsupportedEncodingException ex) {
            // can't happen
        }
        return s;
    }

    /**
     * Copies as much of src to dst as possible.
     * Return number of bytes copied
     */
    public static int copy(ByteBuffer src, ByteBuffer dst) {
        int srcLen = src.remaining();
        int dstLen = dst.remaining();
        if (srcLen > dstLen) {
            int diff = srcLen - dstLen;
            int limit = src.limit();
            src.limit(limit - diff);
            dst.put(src);
            src.limit(limit);
        } else {
            dst.put(src);
        }
        return srcLen - src.remaining();
    }

    /** Threshold beyond which data is no longer copied into the current
     * buffer, if that buffer has enough unused space. */
    private static final int COPY_THRESHOLD = 8192;

    /**
     * Adds the data from buffersToAdd to currentList. Either 1) appends the
     * data from a particular buffer to the last buffer in the list ( if
     * there is enough unused space ), or 2) adds it to the list.
     *
     * @returns the number of bytes added
     */
    public static long accumulateBuffers(List<ByteBuffer> currentList,
                                         List<ByteBuffer> buffersToAdd) {
        long accumulatedBytes = 0;
        for (ByteBuffer bufferToAdd : buffersToAdd) {
            int remaining = bufferToAdd.remaining();
            if (remaining <= 0)
                continue;
            int listSize = currentList.size();
            if (listSize == 0) {
                currentList.add(bufferToAdd);
                accumulatedBytes = remaining;
                continue;
            }

            ByteBuffer lastBuffer = currentList.get(currentList.size() - 1);
            int freeSpace = lastBuffer.capacity() - lastBuffer.limit();
            if (remaining <= COPY_THRESHOLD && freeSpace >= remaining) {
                // append the new data to the unused space in the last buffer
                int position = lastBuffer.position();
                int limit = lastBuffer.limit();
                lastBuffer.position(limit);
                lastBuffer.limit(limit + bufferToAdd.limit());
                lastBuffer.put(bufferToAdd);
                lastBuffer.position(position);
            } else {
                currentList.add(bufferToAdd);
            }
            accumulatedBytes += remaining;
        }
        return accumulatedBytes;
    }

    // copy up to amount from src to dst, but no more
    public static int copyUpTo(ByteBuffer src, ByteBuffer dst, int amount) {
        int toCopy = Math.min(src.remaining(), Math.min(dst.remaining(), amount));
        copy(src, dst, toCopy);
        return toCopy;
    }

    /**
     * Copy amount bytes from src to dst. at least amount must be
     * available in both dst and in src
     */
    public static void copy(ByteBuffer src, ByteBuffer dst, int amount) {
        int excess = src.remaining() - amount;
        assert excess >= 0;
        if (excess > 0) {
            int srclimit = src.limit();
            src.limit(srclimit - excess);
            dst.put(src);
            src.limit(srclimit);
        } else {
            dst.put(src);
        }
    }

    public static ByteBuffer copy(ByteBuffer src) {
        ByteBuffer dst = ByteBuffer.allocate(src.remaining());
        dst.put(src);
        dst.flip();
        return dst;
    }

    public static String dump(Object... objects) {
        return Arrays.toString(objects);
    }

    public static String stringOf(Collection<?> source) {
        // We don't know anything about toString implementation of this
        // collection, so let's create an array
        return Arrays.toString(source.toArray());
    }

    public static int remaining(ByteBuffer buf) {
        return buf.remaining();
    }

    public static long remaining(ByteBuffer[] bufs) {
        long remain = 0;
        for (ByteBuffer buf : bufs) {
            remain += buf.remaining();
        }
        return remain;
    }

    public static boolean hasRemaining(List<ByteBuffer> bufs) {
        synchronized (bufs) {
            for (ByteBuffer buf : bufs) {
                if (buf.hasRemaining())
                    return true;
            }
        }
        return false;
    }

    public static long remaining(List<ByteBuffer> bufs) {
        long remain = 0;
        synchronized (bufs) {
            for (ByteBuffer buf : bufs) {
                remain += buf.remaining();
            }
        }
        return remain;
    }

    public static int remaining(List<ByteBuffer> bufs, int max) {
        long remain = 0;
        synchronized (bufs) {
            for (ByteBuffer buf : bufs) {
                remain += buf.remaining();
                if (remain > max) {
                    throw new IllegalArgumentException("too many bytes");
                }
            }
        }
        return (int) remain;
    }

    public static long remaining(ByteBufferReference[] refs) {
        long remain = 0;
        for (ByteBufferReference ref : refs) {
            remain += ref.get().remaining();
        }
        return remain;
    }

    public static int remaining(ByteBufferReference[] refs, int max) {
        long remain = 0;
        for (ByteBufferReference ref : refs) {
            remain += ref.get().remaining();
            if (remain > max) {
                throw new IllegalArgumentException("too many bytes");
            }
        }
        return (int) remain;
    }

    public static int remaining(ByteBuffer[] refs, int max) {
        long remain = 0;
        for (ByteBuffer b : refs) {
            remain += b.remaining();
            if (remain > max) {
                throw new IllegalArgumentException("too many bytes");
            }
        }
        return (int) remain;
    }

    // assumes buffer was written into starting at position zero
    static void unflip(ByteBuffer buf) {
        buf.position(buf.limit());
        buf.limit(buf.capacity());
    }

    public static void close(Closeable... closeables) {
        for (Closeable c : closeables) {
            try {
                c.close();
            } catch (IOException ignored) { }
        }
    }

    public static void close(Throwable t, Closeable... closeables) {
        for (Closeable c : closeables) {
            try {
                ExceptionallyCloseable.close(t, c);
            } catch (IOException ignored) { }
        }
    }

    /**
     * Returns an array with the same buffers, but starting at position zero
     * in the array.
     */
    public static ByteBuffer[] reduce(ByteBuffer[] bufs, int start, int number) {
        if (start == 0 && number == bufs.length) {
            return bufs;
        }
        ByteBuffer[] nbufs = new ByteBuffer[number];
        int j = 0;
        for (int i=start; i<start+number; i++) {
            nbufs[j++] = bufs[i];
        }
        return nbufs;
    }

    static String asString(ByteBuffer buf) {
        byte[] b = new byte[buf.remaining()];
        buf.get(b);
        return new String(b, StandardCharsets.US_ASCII);
    }

    // Put all these static 'empty' singletons here
    @SuppressWarnings("rawtypes")
    public static final CompletableFuture[] EMPTY_CFARRAY = new CompletableFuture[0];

    public static final ByteBuffer EMPTY_BYTEBUFFER = ByteBuffer.allocate(0);
    public static final ByteBuffer[] EMPTY_BB_ARRAY = new ByteBuffer[0];
    public static final List<ByteBuffer> EMPTY_BB_LIST;

    static {
        EMPTY_BB_LIST = Collections.unmodifiableList(new LinkedList<>());
    }

    public static ByteBuffer slice(ByteBuffer buffer, int amount) {
        ByteBuffer newb = buffer.slice();
        newb.limit(amount);
        buffer.position(buffer.position() + amount);
        return newb;
    }

    /**
     * Get the Charset from the Content-encoding header. Defaults to
     * UTF_8
     */
    public static Charset charsetFrom(HttpHeaders headers) {
        String encoding = headers.firstValue("Content-encoding")
                .orElse("UTF_8");
        try {
            return Charset.forName(encoding);
        } catch (IllegalArgumentException e) {
            return StandardCharsets.UTF_8;
        }
    }

    public static UncheckedIOException unchecked(IOException e) {
        return new UncheckedIOException(e);
    }

    /**
     * Get a logger for debug HTTP traces.
     *
     * The logger should only be used with levels whose severity is
     * {@code <= DEBUG}. By default, this logger will forward all messages
     * logged to an internal logger named "jdk.internal.httpclient.debug".
     * In addition, if the property -Djdk.internal.httpclient.debug=true is set,
     * it will print the messages on stderr.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     *
     * @return A logger for HTTP internal debug traces
     */
    public static Logger getDebugLogger(Supplier<String> dbgTag) {
        return getDebugLogger(dbgTag, DEBUG);
    }

    /**
     * Get a logger for debug HTTP traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger will forward all messages logged to an internal
     * logger named "jdk.internal.httpclient.debug".
     * In addition, if the message severity level is >= to
     * the provided {@code errLevel} it will print the messages on stderr.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stderr in
     *          addition to forwarding to the internal logger, use
     *          {@code getDebugLogger(this::dbgTag, Level.ALL);}.
     *          This is also equivalent to calling
     *          {@code getDebugLogger(this::dbgTag, true);}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code getDebugLogger(this::dbgTag, Level.OFF);}.
     *          This is also equivalent to calling
     *          {@code getDebugLogger(this::dbgTag, false);}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     * @param errLevel The level above which messages will be also printed on
     *               stderr (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HTTP internal debug traces
     */
    static Logger getDebugLogger(Supplier<String> dbgTag, Level errLevel) {
        return new DebugLogger(DebugLogger.HTTP, dbgTag, Level.OFF, errLevel);
    }

    /**
     * Get a logger for debug HTTP traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger will forward all messages logged to an internal
     * logger named "jdk.internal.httpclient.debug".
     * In addition, the provided boolean {@code on==true}, it will print the
     * messages on stderr.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stderr in
     *          addition to forwarding to the internal logger, use
     *          {@code getDebugLogger(this::dbgTag, true);}.
     *          This is also equivalent to calling
     *          {@code getDebugLogger(this::dbgTag, Level.ALL);}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code getDebugLogger(this::dbgTag, false);}.
     *          This is also equivalent to calling
     *          {@code getDebugLogger(this::dbgTag, Level.OFF);}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "SocketTube(3)", or "Http2Connection(SocketTube(3))")
     * @param on  Whether messages should also be printed on
     *               stderr (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HTTP internal debug traces
     */
    public static Logger getDebugLogger(Supplier<String> dbgTag, boolean on) {
        Level errLevel = on ? Level.ALL : Level.OFF;
        return getDebugLogger(dbgTag, errLevel);
    }

    /**
     * Get a logger for debug HPACK traces.
     *
     * The logger should only be used with levels whose severity is
     * {@code <= DEBUG}. By default, this logger will forward all messages
     * logged to an internal logger named "jdk.internal.httpclient.hpack.debug".
     * In addition, if the property -Djdk.internal.httpclient.hpack.debug=true
     * is set,  it will print the messages on stdout.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "Http2Connection(SocketTube(3))/hpack.Decoder(3)")
     *
     * @return A logger for HPACK internal debug traces
     */
    public static Logger getHpackLogger(Supplier<String> dbgTag) {
        Level errLevel = Level.OFF;
        Level outLevel = DEBUG_HPACK ? Level.ALL : Level.OFF;
        return new DebugLogger(DebugLogger.HPACK, dbgTag, outLevel, errLevel);
    }

    /**
     * Get a logger for debug HPACK traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger will forward all messages logged to an internal
     * logger named "jdk.internal.httpclient.hpack.debug".
     * In addition, if the message severity level is >= to
     * the provided {@code outLevel} it will print the messages on stdout.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stdout in
     *          addition to forwarding to the internal logger, use
     *          {@code getHpackLogger(this::dbgTag, Level.ALL);}.
     *          This is also equivalent to calling
     *          {@code getHpackLogger(this::dbgTag, true);}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code getHpackLogger(this::dbgTag, Level.OFF);}.
     *          This is also equivalent to calling
     *          {@code getHpackLogger(this::dbgTag, false);}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "Http2Connection(SocketTube(3))/hpack.Decoder(3)")
     * @param outLevel The level above which messages will be also printed on
     *               stdout (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HPACK internal debug traces
     */
    static Logger getHpackLogger(Supplier<String> dbgTag, Level outLevel) {
        Level errLevel = Level.OFF;
        return new DebugLogger(DebugLogger.HPACK, dbgTag, outLevel, errLevel);
    }

    /**
     * Get a logger for debug HPACK traces.The logger should only be used
     * with levels whose severity is {@code <= DEBUG}.
     *
     * By default, this logger will forward all messages logged to an internal
     * logger named "jdk.internal.httpclient.hpack.debug".
     * In addition, the provided boolean {@code on==true}, it will print the
     * messages on stdout.
     * The logger will add some decoration to the printed message, in the form of
     * {@code <Level>:[<thread-name>] [<elapsed-time>] <dbgTag>: <formatted message>}
     *
     * @apiNote To obtain a logger that will always print things on stdout in
     *          addition to forwarding to the internal logger, use
     *          {@code getHpackLogger(this::dbgTag, true);}.
     *          This is also equivalent to calling
     *          {@code getHpackLogger(this::dbgTag, Level.ALL);}.
     *          To obtain a logger that will only forward to the internal logger,
     *          use {@code getHpackLogger(this::dbgTag, false);}.
     *          This is also equivalent to calling
     *          {@code getHpackLogger(this::dbgTag, Level.OFF);}.
     *
     * @param dbgTag A lambda that returns a string that identifies the caller
     *               (e.g: "Http2Connection(SocketTube(3))/hpack.Decoder(3)")
     * @param on  Whether messages should also be printed on
     *            stdout (in addition to be forwarded to the internal logger).
     *
     * @return A logger for HPACK internal debug traces
     */
    public static Logger getHpackLogger(Supplier<String> dbgTag, boolean on) {
        Level outLevel = on ? Level.ALL : Level.OFF;
        return getHpackLogger(dbgTag, outLevel);
    }



    private static final class DebugLogger implements System.Logger {

        // deliberately not in the same subtree than standard loggers.
        final static String HTTP_NAME  = "jdk.internal.httpclient.debug";
        final static String HPACK_NAME = "jdk.internal.httpclient.hpack.debug";
        final static Logger HTTP = System.getLogger(HTTP_NAME);
        final static Logger HPACK = System.getLogger(HPACK_NAME);
        final static long START_NANOS = System.nanoTime();

        final Supplier<String> dbgTag;
        final Level errLevel;
        final Level outLevel;
        final Logger logger;
        final boolean debugOn;
        final boolean traceOn;

        DebugLogger(Logger logger,
                    Supplier<String> dbgTag,
                    Level outLevel,
                    Level errLevel) {
            this.dbgTag = dbgTag;
            this.errLevel = errLevel;
            this.outLevel = outLevel;
            this.logger = logger;
            // support only static configuration.
            this.debugOn = isEnabled(Level.DEBUG);
            this.traceOn = isEnabled(Level.TRACE);
        }

        @Override
        public String getName() {
            return logger.getName();
        }

        private boolean isEnabled(Level level) {
            if (level == Level.OFF) return false;
            int severity = level.getSeverity();
            return severity >= errLevel.getSeverity()
                    || severity >= outLevel.getSeverity()
                    || logger.isLoggable(level);
        }

        @Override
        public boolean isLoggable(Level level) {
            // fast path, we assume these guys never change.
            // support only static configuration.
            if (level == Level.DEBUG) return debugOn;
            if (level == Level.TRACE) return traceOn;
            return isEnabled(level);
        }

        @Override
        public void log(Level level, ResourceBundle unused,
                        String format, Object... params) {
            // fast path, we assume these guys never change.
            // support only static configuration.
            if (level == Level.DEBUG && !debugOn) return;
            if (level == Level.TRACE && !traceOn) return;

            int severity = level.getSeverity();
            if (errLevel != Level.OFF
                    && errLevel.getSeverity() <= severity) {
                print(System.err, level, format, params, null);
            }
            if (outLevel != Level.OFF
                    && outLevel.getSeverity() <= severity) {
                print(System.out, level, format, params, null);
            }
            if (logger.isLoggable(level)) {
                logger.log(level, unused,
                           getFormat(new StringBuilder(), format, params).toString(),
                           params);
            }
        }

        @Override
        public void log(Level level, ResourceBundle unused, String msg,
                        Throwable thrown) {
            // fast path, we assume these guys never change.
            if (level == Level.DEBUG && !debugOn) return;
            if (level == Level.TRACE && !traceOn) return;

            if (errLevel != Level.OFF
                    && errLevel.getSeverity() <= level.getSeverity()) {
                print(System.err, level, msg, null, thrown);
            }
            if (outLevel != Level.OFF
                    && outLevel.getSeverity() <= level.getSeverity()) {
                print(System.out, level, msg, null, thrown);
            }
            if (logger.isLoggable(level)) {
                logger.log(level, unused,
                           getFormat(new StringBuilder(), msg, null).toString(),
                           thrown);
            }
        }

        private void print(PrintStream out, Level level, String msg,
                           Object[] params, Throwable t) {
            StringBuilder sb = new StringBuilder();
            sb.append(level.name()).append(':').append(' ');
            sb = format(sb, msg, params);
            if (t != null) sb.append(' ').append(t.toString());
            out.println(sb.toString());
            if (t != null) {
                t.printStackTrace(out);
            }
        }

        private StringBuilder decorate(StringBuilder sb, String msg) {
            String tag = dbgTag == null ? null : dbgTag.get();
            String res = msg == null ? "" : msg;
            long elapsed = System.nanoTime() - START_NANOS;
            long nanos =  elapsed % 1000_000;
            long millis = elapsed / 1000_000;
            long secs   = millis / 1000;
            sb.append('[').append(Thread.currentThread().getName()).append(']')
                    .append(' ').append('[');
            if (secs > 0) {
                sb.append(secs).append('s');
            }
            millis = millis % 1000;
            if (millis > 0) {
                if (secs > 0) sb.append(' ');
                sb.append(millis).append("ms");
            }
            sb.append(']').append(' ');
            if (tag != null) {
                sb.append(tag).append(' ');
            }
            sb.append(res);
            return sb;
        }


        private StringBuilder getFormat(StringBuilder sb, String format, Object[] params) {
            if (format == null || params == null || params.length == 0) {
                return decorate(sb, format);
            } else if (format.contains("{0}") || format.contains("{1}")) {
                return decorate(sb, format);
            } else if (format.contains("%s") || format.contains("%d")) {
                try {
                    return decorate(sb, String.format(format, params));
                } catch (Throwable t) {
                    return decorate(sb, format);
                }
            } else {
                return decorate(sb, format);
            }
        }

        private StringBuilder format(StringBuilder sb, String format, Object[] params) {
            if (format == null || params == null || params.length == 0) {
                return decorate(sb, format);
            } else if (format.contains("{0}") || format.contains("{1}")) {
                return decorate(sb, java.text.MessageFormat.format(format, params));
            } else if (format.contains("%s") || format.contains("%d")) {
                try {
                    return decorate(sb, String.format(format, params));
                } catch (Throwable t) {
                    return decorate(sb, format);
                }
            } else {
                return decorate(sb, format);
            }
        }

    }
}
