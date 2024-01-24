/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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

package sun.nio.ch.iouring;

import sun.nio.ch.iouring.foreign.*;

import java.io.IOException;
import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Optional;

import static sun.nio.ch.iouring.Util.*;
import static sun.nio.ch.iouring.foreign.iouring_h.*;

/**
 * An implementation of Socket operations through an IOURing.
 * Functions not supported through the io_uring interface are
 * invoked through traditional system calls, via java.lang.foreign
 */
public class IOURingSocket extends IOURingReaderWriter {

    private Optional<Duration> duration = Optional.empty();

    private IOURingSocket(BlockingRing ring, FDImpl fd) {
        super(ring, fd);
    }

    public static IOURingSocket create(BlockingRing ring, int domain, int type, int protocol) throws IOException, InterruptedException {
        Sqe request = new Sqe()
                .opcode(IORING_OP_SOCKET())
                .fd(domain)
                .off(type)
                .len(protocol);
        Cqe response = ring.blockingSubmit(request);
        int result = response.res();
        if (result < 0) {
            throw new IOException("Open failed: " + strerror(-result));
        }
        return new IOURingSocket(ring, new FDImpl(result));
    }


    public void timeout(Duration duration) {
        this.duration = Optional.of(duration);
    }
    public IOURingSocket accept() throws IOException, InterruptedException {
        Sqe request = new Sqe()
                .opcode(IORING_OP_ACCEPT())
                .fd(fd.fd())
                .xxx_flags(0) // Not currently setting flags
                .addr(MemorySegment.NULL) // Not currently returning the remote addr
                .addr2(MemorySegment.NULL);
        Cqe response = ring.blockingSubmit(request, duration);
        int result = response.res();
        if (result < 0) {
            throw new IOException("accept failed: " + strerror(-result));
        }
        return new IOURingSocket(ring, new FDImpl(result));
    }

    public void close() throws IOException, InterruptedException {
        Sqe request = new Sqe()
                .opcode(IORING_OP_CLOSE())
                .fd(fd.fd());
        Cqe response = ring.blockingSubmit(request);
        int result = response.res();
        if (result < 0) {
            throw new IOException("Close failed: " + strerror(-result));
        }
    }
    /**
     * Not implmenented by IOURING
     *
     * @param fd
     * @param address
     * @return
     */
    public void bind(InetSocketAddress address) throws IOException {
        MemorySegment addrseg = socketAddressToSegment(address);
        //Util.print(addrseg, "Address segment");
        int ret = 0;
        try {
            int bs = (int)addrseg.byteSize();
            ret = (int)bind_fn.invoke(ctx.errnoCaptureSegment(), fd.fd(), addrseg, bs);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        ctx.throwIOExceptionOnError(ret);
    }

    public void connect(InetSocketAddress address) throws IOException, InterruptedException {
        MemorySegment addrseg = socketAddressToSegment(address);
        Sqe request = new Sqe()
                .opcode(IORING_OP_CONNECT())
                .fd(fd.fd())
                .addr(addrseg)
                .off(addrseg.byteSize());
        Cqe response = ring.blockingSubmit(request, duration);
        int result = response.res();
        if (result < 0) {
            throw new IOException("connect failed: " + strerror(-result));
        }
    }

    public void listen(int backlog) throws IOException {
        int ret = 0;
        try {
            ret = (int)listen_fn.invoke(ctx.errnoCaptureSegment(), fd.fd(), backlog);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        ctx.throwIOExceptionOnError(ret);
    }

    public InetSocketAddress getpeername() throws IOException {
        return getname(getpeername_fn);
    }
    public InetSocketAddress getsockname() throws IOException {
        return getname(getsockname_fn);
    }

    public void setsockopt(int level, int name, int value) throws IOException {
        int ret = 0;
        try {
            MemorySegment val = autoArena.allocateFrom(ValueLayout.JAVA_INT, value);
            ret = (int)setsockopt_fn.invoke(ctx.errnoCaptureSegment(), fd.fd(),
                    level, name, val, 4);
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        ctx.throwIOExceptionOnError(ret);
    }

    private static final MethodHandle bind_fn = locateStdHandle(
            "bind", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, // int return code
                    ValueLayout.JAVA_INT, // fd
                    //ValueLayout.JAVA_LONG,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT  // socklen_t
            ), SystemCallContext.errnoLinkerOption()
    );

    private static final MethodHandle listen_fn = locateStdHandle(
            "listen", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT, // int return code
                    ValueLayout.JAVA_INT, // fd
                    ValueLayout.JAVA_INT  // backlog
            ), SystemCallContext.errnoLinkerOption()
    );
    private static final MethodHandle getsockname_fn = locateStdHandle(
            "getsockname", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // int return code
                    ValueLayout.JAVA_INT,   // fd
                    ValueLayout.ADDRESS,  // returned address
                    ValueLayout.JAVA_LONG   // returned addrlen
            ), SystemCallContext.errnoLinkerOption()
    );
    private static final MethodHandle setsockopt_fn = locateStdHandle(
            "setsockopt", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // int return code
                    ValueLayout.JAVA_INT,   // fd
                    ValueLayout.JAVA_INT,   // level
                    ValueLayout.JAVA_INT,   // name
                    ValueLayout.ADDRESS,  // Value address
                    ValueLayout.JAVA_INT    // Value size
            ), SystemCallContext.errnoLinkerOption()
    );
    private static final MethodHandle getpeername_fn = locateStdHandle(
            "getpeername", FunctionDescriptor.of(
                    ValueLayout.JAVA_INT,   // int return code
                    ValueLayout.JAVA_INT,   // fd
                    ValueLayout.ADDRESS,    // returned address
                    ValueLayout.JAVA_LONG   // returned addrlen
            ), SystemCallContext.errnoLinkerOption()
    );


    private InetSocketAddress getname(MethodHandle mth) throws IOException {
        MemorySegment addrseg = autoArena.allocate(sockaddr_storage.$LAYOUT());
        MemorySegment adlenseg = autoArena.allocate(ValueLayout.JAVA_INT, 4L);
        adlenseg.set(ValueLayout.JAVA_INT, 0L, (int)addrseg.byteSize());
        int ret = 0;
        try {
            ret = (int)mth.invoke(
                    ctx.errnoCaptureSegment(),
                    fd.fd(), addrseg,
                    adlenseg.address());
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
        ctx.throwIOExceptionOnError(ret);
        return segmentToSocketAddress(addrseg);
    }

    private InetSocketAddress segmentToSocketAddress(MemorySegment addrseg) throws IOException {
        int family = sockaddr.sa_family(addrseg);
        InetAddress ia;
        int port;
        if (family == AF_INET()) {
            byte[] iab = new byte[4];
            MemorySegment inaddr = sockaddr_in.sin_addr(addrseg);
            int ipv4_addr = in_addr.s_addr(inaddr);
            // already converted to native byte order
            for (int i=3; i>=0; i--) {
                iab[i] = (byte)(ipv4_addr & 0xff);
                ipv4_addr >>= 8;
            }
            port = sockaddr_in.sin_port(addrseg) & 0xFFFF; // already in native byte order
            ia = InetAddress.getByAddress(iab);
        } else if (family == AF_INET6()) {
            byte[] iab = new byte[16];
            MemorySegment in6addr = sockaddr_in6.sin6_addr(addrseg);
            for (int i=0; i<16; i++) {
                iab[i] = in6addr.get(ValueLayout.JAVA_BYTE, i);
            }
            port = sockaddr_in6.sin6_port(addrseg) & 0xffff;
            ia = InetAddress.getByAddress(iab);
        } else {
            throw new IOException("Unsupported address type");
        }
        return new InetSocketAddress(ia, port);
    }

    private MemorySegment socketAddressToSegment(InetSocketAddress address) {
        MemorySegment addrseg;
        InetAddress addr = address.getAddress();
        short family;
        MemoryLayout ml;
        int port = address.getPort();
        byte[] ia = addr.getAddress();
        if (ia.length == 4) {
            ml = sockaddr_in.$LAYOUT();
            family = (short) AF_INET();
            addrseg = autoArena.allocate(ml).fill((byte)0);
            MemorySegment inaddr = sockaddr_in.sin_addr(addrseg);
            // Byte order constraint is applied by editing jextract generated
            // code for sockaddr_in.sin_addr and sockaddr_in.sin_port
            int ipv4_addr = (((((ia[0] << 8) + ia[1]) << 8) + ia[2]) << 8) + ia[3];
            in_addr.s_addr(inaddr, ipv4_addr);
            sockaddr_in.sin_port(addrseg, (short)port);
            sockaddr_in.sin_family(addrseg, family);
        } else {
            ml = sockaddr_in6.$LAYOUT();
            family = (short) AF_INET6();
            addrseg = autoArena.allocate(ml).fill((byte)0);
            sockaddr_in6.sin6_family(addrseg, family);
            // Byte order constraint is applied by editing jextract generated code below
            sockaddr_in6.sin6_port(addrseg, (short)port);
            var addrSlice = sockaddr_in6.sin6_addr(addrseg);
            for (int i=0; i<16; i++) {
                addrSlice.set(ValueLayout.JAVA_BYTE, i, ia[i]);
            }
        }
        return addrseg;
    }
}
