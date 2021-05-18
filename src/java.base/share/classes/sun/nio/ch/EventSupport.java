/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnixDomainSocketAddress;
import jdk.internal.access.JavaIOFileDescriptorAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.event.AbstractSocketEvent;
import jdk.internal.event.SocketAcceptEvent;
import jdk.internal.event.SocketAcceptEndEvent;
import jdk.internal.event.SocketAcceptStartEvent;
import jdk.internal.event.SocketConnectEvent;
import jdk.internal.event.SocketConnectEndEvent;
import jdk.internal.event.SocketConnectStartEvent;

class EventSupport {

    private EventSupport() { }

    static void writeConnectEvent(SocketConnectEvent event,
                                  FileDescriptor fd,
                                  InetAddress address,
                                  int port,
                                  Throwable t) {
        if (event.shouldCommit()) {
            event.id = fdOrZero(fd);
            event.host = address.getHostName();
            event.address = address.getHostAddress();
            event.port = port;
            event.exceptionMessage = stringifyOrNull(t);
            event.commit();
        }
    }

    static void writeConnectEvent(SocketConnectEvent event,
                                  FileDescriptor fd,
                                  SocketAddress addr,
                                  Throwable t) {
        if (event.shouldCommit()) {
            event.id = fdOrZero(fd);
            setAddress(event, addr);
            event.exceptionMessage = stringifyOrNull(t);
            event.commit();
        }
    }

    static void writeAcceptEvent(SocketAcceptEvent event,
                                 FileDescriptor fd,
                                 InetAddress address,
                                 int port,
                                 FileDescriptor newfd,
                                 Throwable t) {
        if (event.shouldCommit()) {
            event.id = fdOrZero(fd);
            event.host = address.getHostName();
            event.address = address.getHostAddress();
            event.port = port;
            event.acceptedId = fdOrZero(newfd);
            event.exceptionMessage = stringifyOrNull(t);
            event.commit();
        }
    }

    static void writeAcceptEvent(SocketAcceptEvent event,
                                 FileDescriptor fd,
                                 SocketAddress addr,
                                 FileDescriptor newfd,
                                 Throwable t) {
        if (event.shouldCommit()) {
            event.id = fdOrZero(fd);
            setAddress(event, addr);
            event.acceptedId = fdOrZero(newfd);
            event.exceptionMessage = stringifyOrNull(t);
            event.commit();
        }
    }

    static void writeConnectStartEvent(FileDescriptor fd,
                                       SocketAddress addr) {
        var event = new SocketConnectStartEvent();
        if (event.shouldCommit()) {
            event.id = fdOrZero(fd);
            setAddress(event, addr);
            event.commit();
        }
    }

    static void writeConnectEndEvent(FileDescriptor fd,
                                     SocketAddress addr,
                                     Throwable t) {
        var event = new SocketConnectEndEvent();
        if (event.shouldCommit()) {
            event.id = fdOrZero(fd);
            setAddress(event, addr);
            event.exceptionMessage = stringifyOrNull(t);
            event.commit();
        }
    }

    private static final JavaIOFileDescriptorAccess FD_ACCESS =
            SharedSecrets.getJavaIOFileDescriptorAccess();

    private static int fdOrZero(FileDescriptor fd) {
        return fd != null ? FD_ACCESS.get(fd) : 0;
    }

    private static String stringifyOrNull(Throwable ex) {
        if (ex == null)
            return null;
        var m = ex.getMessage();
        return m != null ? m : ex.toString();
    }

    private static void setAddress(AbstractSocketEvent event, SocketAddress addr) {
        if (addr instanceof InetSocketAddress isa) {
            String hostString  = isa.getAddress().toString();
            int delimiterIndex = hostString.lastIndexOf('/');

            event.host = hostString.substring(0, delimiterIndex);
            event.address = hostString.substring(delimiterIndex + 1);
            event.port = isa.getPort();
        } else if (addr instanceof UnixDomainSocketAddress udsa) {
            event.host = "Unix domain socket";
            event.address = "[" + udsa.getPath().toString() + "]";
            event.port = 0;
        } else {
            throw new InternalError("unknown address: " + addr);
        }
    }
}
