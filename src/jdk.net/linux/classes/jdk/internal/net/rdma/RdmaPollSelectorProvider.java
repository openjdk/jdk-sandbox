/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import sun.nio.ch.Net;

import java.io.IOException;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.nio.channels.DatagramChannel;
import java.nio.channels.Pipe;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.channels.spi.AbstractSelector;
import java.nio.channels.spi.SelectorProvider;

import static java.net.StandardProtocolFamily.INET;
import static java.net.StandardProtocolFamily.INET6;

public class RdmaPollSelectorProvider
    extends SelectorProvider
{
    private static final Object lock = new Object();
    private static final RdmaPollSelectorProvider provider =
            new RdmaPollSelectorProvider();

    public static RdmaPollSelectorProvider provider() {
        return provider;
    }

    @Override
    public AbstractSelector openSelector() throws IOException {
        return new RdmaPollSelectorImpl(this);
    }

    @Override
    public SocketChannel openSocketChannel() throws IOException {
        if (Net.isIPv6Available()) {
            return openSocketChannel(INET6);
        }
        return openSocketChannel(INET);
    }

    public SocketChannel openSocketChannel(ProtocolFamily family)
        throws IOException
    {
        return new RdmaSocketChannelImpl(this, family);
    }

    @Override
    public ServerSocketChannel openServerSocketChannel() throws IOException {
        if (Net.isIPv6Available()) {
            return openServerSocketChannel(INET6);
        }
        return openServerSocketChannel(INET);
    }

    public ServerSocketChannel openServerSocketChannel(ProtocolFamily family)
        throws IOException
    {
        return new RdmaServerSocketChannelImpl(this, family);
    }

    @Override
    public DatagramChannel openDatagramChannel() {
        throw new UnsupportedOperationException();
    }

    @Override
    public DatagramChannel openDatagramChannel(ProtocolFamily family) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Pipe openPipe() {
        throw new UnsupportedOperationException();
    }
}
