/*
 * Copyright (c) 2002, 2013, Oracle and/or its affiliates. All rights reserved.
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

// Signalling operations on native threads
//
// On some operating systems (e.g., Linux), closing a channel while another
// thread is blocked in an I/O operation upon that channel does not cause that
// thread to be released.  This class provides access to the native threads
// upon which Java threads are built, and defines a simple signal mechanism
// that can be used to release a native thread from a blocking I/O operation.

import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

public class NativeThread {
    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    /**
     * Returns the current thread's ID.
     */
    public static long current() {
        long tid = JLA.nativeTid();
        if (tid == 0) {
            tid = current0();
            JLA.setNativeTid(tid);
        }
        return tid;
    }

    /**
     * Signals the given thread.
     */
    public static void signal(long tid) {
        signal0(tid);
    }

    private static native long current0();

    private static native void signal0(long tid);

    private static native void init();

    static {
        IOUtil.load();
        init();
    }

}
