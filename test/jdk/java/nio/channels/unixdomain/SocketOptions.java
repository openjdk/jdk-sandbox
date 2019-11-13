/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

/**
 * @test
 * @bug 8231358
 * @run main SocketOptions
 * @summary Socket option test
 */

import java.io.IOException;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/**
 * Check that all supported options can actually be set and got
 */
public class SocketOptions {

    public static void main(String args[]) throws Exception {
        test(ServerSocketChannel.open(StandardProtocolFamily.UNIX));
        test(SocketChannel.open(StandardProtocolFamily.UNIX));
    }

    @SuppressWarnings("unchecked")
    public static void test(NetworkChannel chan) throws IOException {
        System.out.println("Checking: " + chan.getClass());
        Set<SocketOption<?>> supported = chan.supportedOptions();
        for (SocketOption<?> option : supported) {
            String name = option.name();
            System.out.println("Checking option " + name);
            if (option.type() == Boolean.class) {
                chan.setOption((SocketOption<Boolean>)option, true);
                chan.setOption((SocketOption<Boolean>)option, false);
                chan.getOption(option);
            } else if (option.type() == Integer.class) {
                chan.setOption((SocketOption<Integer>)option, 10);
                chan.getOption(option);
            }
        }
    }
}
