/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jfr.internal.instrument;

import java.io.FileDescriptor;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;

import jdk.jfr.events.Handlers;
import jdk.jfr.internal.handlers.EventHandler;

/**
 * See {@link JITracer} for an explanation of this code.
 */
@JIInstrumentationTarget("java.net.Socket$SocketInputStream")
final class SocketInputStreamInstrumentor {

    private SocketInputStreamInstrumentor() {
    }

    @SuppressWarnings("deprecation")
    @JIInstrumentationMethod
    public int read(byte b[], int off, int length) throws IOException {
        EventHandler handler = Handlers.SOCKET_READ;
        if (!handler.isEnabled()) {
            return read(b, off, length);
        }
        int bytesRead = 0;
        long start = 0;
        Exception ex = null;
        try {
            start = EventHandler.timestamp();
            bytesRead = read(b, off, length);
        } catch (Exception e) {
            ex = e;
            throw e;
        } finally {
            long duration = EventHandler.timestamp() - start;
            if (handler.shouldCommit(duration)) {
                InetAddress remote = parent.getInetAddress();
                String host = remote.getHostName();
                String address = remote.getHostAddress();
                int port = parent.getPort();
                int timeout = parent.getSoTimeout();
                //String exMsg = EventSupport.stringifyOrNull(ex);
                String exMsg = ex == null ? null : ex.getMessage() == null ? ex.toString() : ex.getMessage();
                if (bytesRead < 0) {
                    handler.write(start, duration, fd, host, address, port, timeout, 0L, true, exMsg);
                } else {
                    handler.write(start, duration, fd, host, address, port, timeout, bytesRead, false, exMsg);
                }
            }
        }
        return bytesRead;
    }

    // private field in java.net.Socket$SocketInputStream
    private Socket parent;
    private int fd;
}
