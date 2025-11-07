/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

#include <time.h>
#include <stdio.h>

#ifndef WINDOWS
#include <dlfcn.h>
#ifndef RTLD_NEXT
# define RTLD_NEXT      ((void *) -1l)
#endif
#endif

// This library is not Java related, but include jni_md to get JNIEXPORT to set visibility.
#include <jni_md.h>

#define TRUE 1
#define FALSE 0

#ifndef WINDOWS
int clock_enabled = FALSE;

struct timespec local_ts;

/**
 * Set the time that we will respond with to clock_gettime calls.
 * Parameter is in seconds.
 */
JNIEXPORT
void set_revival_time_s(unsigned long long t) {
    local_ts.tv_sec = t;
    local_ts.tv_nsec = 0;
    clock_enabled = TRUE;
}

/**
 * Set the time that we will respond with to clock_gettime calls.
 * Parameter is in nanoseconds.
 */
JNIEXPORT
void set_revival_time_ns(unsigned long long t) {
    local_ts.tv_sec = 0;
    local_ts.tv_nsec = t;
    clock_enabled = TRUE;
}

/**
 * clock_gettime which will return our set time, not the real time.
 */
JNIEXPORT
int clock_gettime(clockid_t clockid, struct timespec *tp) {

    static int (*func)(clockid_t, struct timespec *); // real function

    if (!clock_enabled) {
        if (!func) {
            // First call, get the real function.
            func = (int(*)()) dlsym(RTLD_NEXT, "clock_gettime");
        }
        return func(clockid, &local_ts);
    } else {
        // Enabled: return our set value.
        tp->tv_sec = local_ts.tv_sec;
        tp->tv_nsec = local_ts.tv_nsec;
        return 0;
    }
}
#endif // WINDOWS

