/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

package insp;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.net.spi.InetNameService;
import java.net.spi.InetNameServiceProvider;
import java.util.stream.Stream;

public class ThrowingLookupsProviderImpl extends InetNameServiceProvider {
    @Override
    public InetNameService get(Configuration configuration) {
        System.out.println("The following provider will be used by current test:" +
                this.getClass().getCanonicalName());

        return new InetNameService() {
            @Override
            public Stream<InetAddress> lookupAddresses(String host, LookupPolicy lookupPolicy)
                    throws UnknownHostException {
                if (THROW_RUNTIME_EXCEPTION) {
                    System.err.println(name()+" forward lookup: throwing RuntimeException");
                    throw new RuntimeException(RUNTIME_EXCEPTION_MESSAGE);
                } else {
                    System.err.println(name()+" forward lookup: throwing UnknownHostException");
                    throw new UnknownHostException();
                }
            }

            @Override
            public String lookupHostName(byte[] addr) throws UnknownHostException {
                if (THROW_RUNTIME_EXCEPTION) {
                    System.err.println(name()+" reverse lookup: throwing RuntimeException");
                    throw new RuntimeException(RUNTIME_EXCEPTION_MESSAGE);
                } else {
                    System.err.println(name()+" reverse lookup: throwing UnknownHostException");
                    throw new UnknownHostException();
                }
            }
        };
    }

    @Override
    public String name() {
        return "ThrowingLookupsProvider";
    }

    private static final String EXCEPTION_TYPE_SP_NAME = "provider.throws.runtime.exception";
    // Indicates if provider need to throw RuntimeException for forward and reverse lookup operations.
    // If it is set to 'false' then UnknownHostException will thrown for each operation.
    public static final boolean THROW_RUNTIME_EXCEPTION = Boolean.getBoolean(EXCEPTION_TYPE_SP_NAME);
    public static final String RUNTIME_EXCEPTION_MESSAGE = "This provider only throws exceptions";
}
