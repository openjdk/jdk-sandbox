/*
 * Copyright (c) 2014, 2018, Oracle and/or its affiliates. All rights reserved.
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

#include "Platform.h"

#ifndef PLATFORMTHREAD_H
#define PLATFORMTHREAD_H

#ifdef POSIX
#include <pthread.h>
#endif //POSIX


class PlatformThread {
private:
#ifdef WINDOWS
    HANDLE FHandle;
    DWORD FThreadID;
    static DWORD WINAPI Do(LPVOID lpParam);
#endif //WINDOWS
#ifdef POSIX
    pthread_t FHandle;
    static void* Do(void *threadid);
#endif //POSIX

protected:
    // Never call directly. Override this method and this is your code that runs in a thread.
    virtual void Execute() = 0;

public:
    PlatformThread(void);
    virtual ~PlatformThread(void);

    void Run();
    void Terminate();
    void Wait();
};

#endif //PLATFORMTHREAD_H
