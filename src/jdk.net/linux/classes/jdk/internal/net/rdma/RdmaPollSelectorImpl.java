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
import java.nio.channels.spi.SelectorProvider;
import sun.nio.ch.PollSelectorImpl;
import sun.nio.ch.IOUtil;

/**
 * Selector implementation based on poll
 */

public class RdmaPollSelectorImpl extends PollSelectorImpl {

    private static final UnsupportedOperationException unsupported;

    private static final SelectorProvider checkSupported(SelectorProvider sp) {
        if (unsupported != null)
            throw new UnsupportedOperationException(unsupported.getMessage(),
                    unsupported);
        else
            return sp;
    }

    protected RdmaPollSelectorImpl(SelectorProvider sp) throws IOException {
        super(checkSupported(sp));
    }

    protected int poll(long pollAddress, int numfds, int timeout) {
        return poll0(pollAddress, numfds, timeout);
    }

    private static native int poll0(long pollAddress, int numfds, int timeout);

    static {
        java.security.AccessController.doPrivileged(
            new java.security.PrivilegedAction<>() {
                public Void run() {
                    System.loadLibrary("extnet");
                    return null;
                }
            });
        IOUtil.load();
        UnsupportedOperationException uoe = null;
        try {
            init();
        } catch (UnsupportedOperationException e) {
            uoe = e;
        }
        unsupported = uoe;
    }

    private static native void init() throws UnsupportedOperationException;
}
