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

#include "PlatformThread.h"


PlatformThread::PlatformThread(void) {
}

PlatformThread::~PlatformThread(void) {
    Wait();
    Terminate();
}

#ifdef WINDOWS
DWORD WINAPI PlatformThread::Do(LPVOID Data) {
    PlatformThread* self = (PlatformThread*)Data;
    self->Execute();
    return 0;
}
#endif // WINDOWS
#ifdef POSIX
void* PlatformThread::Do(void *Data) {
    PlatformThread* self = (PlatformThread*)Data;
    self->Execute();
    pthread_exit(NULL);
}
#endif // POSIX

void PlatformThread::Run() {
#ifdef WINDOWS
    FHandle = CreateThread(NULL, 0, Do, this, 0, &FThreadID);
#endif // WINDOWS
#ifdef POSIX
    pthread_create(&FHandle, NULL, Do, this);
#endif // POSIX
}

void PlatformThread::Terminate() {
#ifdef WINDOWS
    CloseHandle(FHandle);
#endif // WINDOWS
#ifdef POSIX
    pthread_cancel(FHandle);
#endif // POSIX
}

void PlatformThread::Wait() {
#ifdef WINDOWS
    WaitForSingleObject(FHandle, INFINITE);
#endif // WINDOWS
#ifdef POSIX
    pthread_join(FHandle, NULL);
#endif // POSIX
}
