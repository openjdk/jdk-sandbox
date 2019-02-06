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

package jdk.internal.net.rdma;

import java.io.FileDescriptor;
import java.io.IOException;
import sun.nio.ch.SocketDispatcher;

/**
 * Allows different platforms to call different native methods
 * for read and write operations.
 */

public class RdmaSocketDispatcher extends SocketDispatcher
{
    protected int read(FileDescriptor fd, long address, int len)
            throws IOException {
        return RdmaSocketDispatcherImpl.read0(fd, address, len);
    }

    protected long readv(FileDescriptor fd, long address, int len)
            throws IOException {
        return RdmaSocketDispatcherImpl.readv0(fd, address, len);
    }

    protected int write(FileDescriptor fd, long address, int len)
            throws IOException {
        return RdmaSocketDispatcherImpl.write0(fd, address, len);
    }

    protected long writev(FileDescriptor fd, long address, int len)
            throws IOException {
        return RdmaSocketDispatcherImpl.writev0(fd, address, len);
    }

    protected void close(FileDescriptor fd) throws IOException {
        RdmaSocketDispatcherImpl.close0(fd);
    }

    public void preClose(FileDescriptor fd) throws IOException {
        /* With RDMA socket channels, no need to do preClose */
    }
}
