/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.internal.net.rdma;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketImpl;
import java.net.SocketOption;
import java.net.SocketOptions;
import java.net.StandardSocketOptions;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import jdk.net.RdmaSocketOptions;

public class RdmaSocketProvider {

    static class RdmaClientSocketImpl extends RdmaSocketImpl {

        private final Set<SocketOption<?>> options =
                Set.of(StandardSocketOptions.SO_SNDBUF,
                       StandardSocketOptions.SO_RCVBUF,
                       StandardSocketOptions.SO_REUSEADDR,
                       StandardSocketOptions.TCP_NODELAY,
                       RdmaSocketOptions.RDMA_SQSIZE,
                       RdmaSocketOptions.RDMA_RQSIZE,
                       RdmaSocketOptions.RDMA_INLINE);

        RdmaClientSocketImpl(ProtocolFamily family) {
            super(family);
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return options;
        }

        @Override
        protected <T> void setOption(SocketOption<T> name, T value)
                throws IOException {
            if (!rdmaOptions.isOptionSupported(name)) {
                int opt;
                if (name == StandardSocketOptions.SO_SNDBUF) {
                    opt = SocketOptions.SO_SNDBUF;
                } else if (name == StandardSocketOptions.SO_RCVBUF) {
                    opt = SocketOptions.SO_RCVBUF;
                } else if (name == StandardSocketOptions.SO_REUSEADDR) {
                    opt = SocketOptions.SO_REUSEADDR;
                } else if (name == StandardSocketOptions.TCP_NODELAY) {
                    opt = SocketOptions.TCP_NODELAY;
                } else {
                    throw new UnsupportedOperationException(
                            "unsupported option: " + name);
                }
                setOption(opt, value);
            } else {
                rdmaOptions.setOption(fd, name, value);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <T> T getOption(SocketOption<T> name) throws IOException {
            Object value;
            if (!rdmaOptions.isOptionSupported(name)) {
                int opt;
                if (name == StandardSocketOptions.SO_SNDBUF) {
                    opt = SocketOptions.SO_SNDBUF;
                } else if (name == StandardSocketOptions.SO_RCVBUF) {
                    opt = SocketOptions.SO_RCVBUF;
                } else if (name == StandardSocketOptions.SO_REUSEADDR) {
                    opt = SocketOptions.SO_REUSEADDR;
                } else if (name == StandardSocketOptions.TCP_NODELAY) {
                    opt = SocketOptions.TCP_NODELAY;
                } else {
                    throw new UnsupportedOperationException(
                            "unsupported option: " + name);
                }
                value = getOption(opt);
            } else {
                value = rdmaOptions.getOption(fd, name);
            }
            return (T) value;
        }
    }

    static class RdmaServerSocketImpl extends RdmaSocketImpl {
        private final Set<SocketOption<?>> options =
                Set.of(StandardSocketOptions.SO_RCVBUF,
                       StandardSocketOptions.SO_REUSEADDR,
                       RdmaSocketOptions.RDMA_SQSIZE,
                       RdmaSocketOptions.RDMA_RQSIZE,
                       RdmaSocketOptions.RDMA_INLINE);

        RdmaServerSocketImpl(ProtocolFamily family) {
            super(family);
        }

        @Override
        public Set<SocketOption<?>> supportedOptions() {
            return options;
        }

        @Override
        protected <T> void setOption(SocketOption<T> name, T value)
                throws IOException {
            if (!rdmaOptions.isOptionSupported(name)) {
                int opt;
                if (name == StandardSocketOptions.SO_RCVBUF) {
                    opt = SocketOptions.SO_RCVBUF;
                } else if (name == StandardSocketOptions.SO_REUSEADDR) {
                    opt = SocketOptions.SO_REUSEADDR;
                } else {
                    throw new UnsupportedOperationException(
                            "unsupported option: " + name);
                }
                setOption(opt, value);
            } else {
                rdmaOptions.setOption(fd, name, value);
            }
        }

        @SuppressWarnings("unchecked")
        @Override
        protected <T> T getOption(SocketOption<T> name) throws IOException {
            Object value;
            if (!rdmaOptions.isOptionSupported(name)) {
                int opt;
                if (name == StandardSocketOptions.SO_RCVBUF) {
                    opt = SocketOptions.SO_RCVBUF;
                } else if (name == StandardSocketOptions.SO_REUSEADDR) {
                    opt = SocketOptions.SO_REUSEADDR;
                } else {
                    throw new UnsupportedOperationException(
                            "unsupported option: " + name);
                }
                value = getOption(opt);
            } else {
                value = rdmaOptions.getOption(fd, name);
            }
            return (T) value;
         }
    }

    public static Socket openSocket(ProtocolFamily family) throws IOException {
        return new Socket(new RdmaClientSocketImpl(family)) {  };
    }

    public static ServerSocket openServerSocket(ProtocolFamily family)
            throws IOException {
        return new ServerSocket(new RdmaServerSocketImpl(family)) {
            public Socket accept() throws IOException {
                if (isClosed())
                    throw new SocketException("Socket is closed");
                if (!isBound())
                    throw new SocketException("Socket is not bound yet");

                Socket s = openSocket(family);
                implAccept(s);
                return s;
            }
        };
    }

    private RdmaSocketProvider() {}
}
