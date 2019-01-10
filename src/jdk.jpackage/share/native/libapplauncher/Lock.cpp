/*
 * Copyright (c) 2014, 2019, Oracle and/or its affiliates. All rights reserved.
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

#include "Lock.h"


Lock::Lock(void) {
    Initialize();
}

Lock::Lock(bool Value) {
    Initialize();

    if (Value == true) {
        Enter();
    }
}

void Lock::Initialize() {
#ifdef WINDOWS
    InitializeCriticalSectionAndSpinCount(&FCriticalSection, 0x00000400);
#endif // WINDOWS
#ifdef MAC
    // FMutex =  PTHREAD_RECURSIVE_MUTEX_INITIALIZER;
#endif // MAC
#ifdef LINUX
    // FMutex =  PTHREAD_RECURSIVE_MUTEX_INITIALIZER_NP;
#endif // LINUX
}

Lock::~Lock(void) {
#ifdef WINDOWS
    DeleteCriticalSection(&FCriticalSection);
#endif // WINDOWS
#ifdef POSIX
    pthread_mutex_unlock(&FMutex);
#endif // POSIX
}

void Lock::Enter() {
#ifdef WINDOWS
    EnterCriticalSection(&FCriticalSection);
#endif // WINDOWS
#ifdef POSIX
    pthread_mutex_lock(&FMutex);
#endif // POSIX
}

void Lock::Leave() {
#ifdef WINDOWS
    LeaveCriticalSection(&FCriticalSection);
#endif // WINDOWS
#ifdef POSIX
    pthread_mutex_unlock(&FMutex);
#endif // POSIX
}

bool Lock::TryEnter() {
    bool result = false;
#ifdef WINDOWS
    if (TryEnterCriticalSection (&FCriticalSection) != 0)
        result = true;
#endif // WINDOWS
#ifdef POSIX
    if (pthread_mutex_lock(&FMutex) == 0)
        result = true;
#endif // POSIX
    return result;
}
