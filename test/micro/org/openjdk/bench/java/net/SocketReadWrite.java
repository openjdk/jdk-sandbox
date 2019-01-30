/*
 * Copyright (c) 2014, 2019 Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.bench.java.net;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

/**
 * Tests socket read/write.
 */
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@State(Scope.Thread)
public class SocketReadWrite {

    private Socket s1, s2;
    private InputStream in;
    private OutputStream out;

    @Setup
    public void beforeRun() throws IOException {
        InetAddress lb = InetAddress.getLoopbackAddress();
        try (ServerSocket ss = new ServerSocket(0)) {
            s1 = new Socket(lb, ss.getLocalPort());
            s2 = ss.accept();
        }
        s1.setTcpNoDelay(true);
        s2.setTcpNoDelay(true);
        in = s1.getInputStream();
        out = s2.getOutputStream();
    }

    @TearDown
    public void afterRun() throws IOException {
        s1.close();
        s2.close();
    }

    @Param({"1", "1024", "8192", "64000", "128000"})
    public int size;

    private final byte[] array = new byte[512*1024];

    @Benchmark
    public void test() throws IOException {
        if (size == 1) {
            out.write((byte) 47);
            int c = in.read();
        } else {
            out.write(array, 0, size);
            int nread = 0;
            while (nread < size) {
                int n = in.read(array, 0, size);
                if (n < 0) throw new RuntimeException();
                nread += n;
            }
        }
    }
}
