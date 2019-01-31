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

#include "jdk_internal_net_rdma_RdmaSocketDispatcherImpl.h"
#include "nio.h"
#include "nio_util.h"
#include <Rsocket.h>

static jfieldID fd_fdID;

jint
convertReturnVal(JNIEnv *env, jint n, jboolean reading) {
    if (n > 0)
        return n;
    else if (n == 0) {
        if (reading) {
            return IOS_EOF;
        } else {
            return 0;
        }
    }
    else if (errno == EAGAIN)
        return IOS_UNAVAILABLE;
    else if (errno == EINTR)
        return IOS_INTERRUPTED;
    else {
        const char *msg = reading ? "Read failed" : "Write failed";
        JNU_ThrowIOExceptionWithLastError(env, msg);
        return IOS_THROWN;
    }
}

jlong
convertLongReturnVal(JNIEnv *env, jlong n, jboolean reading) {
    if (n > 0)
        return n;
    else if (n == 0) {
        if (reading) {
            return IOS_EOF;
        } else {
            return 0;
        }
    }
    else if (errno == EAGAIN)
        return IOS_UNAVAILABLE;
    else if (errno == EINTR)
        return IOS_INTERRUPTED;
    else {
        const char *msg = reading ? "Read failed" : "Write failed";
        JNU_ThrowIOExceptionWithLastError(env, msg);
        return IOS_THROWN;
    }
}

JNIEXPORT void JNICALL
Java_jdk_internal_net_rdma_RdmaSocketDispatcherImpl_init(JNIEnv *env,
        jclass cl) {
    loadRdmaFuncs(env);
    CHECK_NULL(cl = (*env)->FindClass(env, "java/io/FileDescriptor"));
    CHECK_NULL(fd_fdID = (*env)->GetFieldID(env, cl, "fd", "I"));
}

JNIEXPORT jint JNICALL
Java_jdk_internal_net_rdma_RdmaSocketDispatcherImpl_read0(JNIEnv *env,
        jclass clazz, jobject fdo, jlong address, jint len) {
    jint fd = (*env)->GetIntField(env, fdo, fd_fdID);
    void *buf = (void *)jlong_to_ptr(address);
    return convertReturnVal(env, rs_read(fd, buf, len), JNI_TRUE);
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_net_rdma_RdmaSocketDispatcherImpl_readv0(JNIEnv *env,
        jclass clazz, jobject fdo, jlong address, jint len) {
    jint fd = (*env)->GetIntField(env, fdo, fd_fdID);
    struct iovec *iov = (struct iovec *)jlong_to_ptr(address);
    return convertLongReturnVal(env, rs_readv(fd, iov, len), JNI_TRUE);
}

JNIEXPORT jint JNICALL
Java_jdk_internal_net_rdma_RdmaSocketDispatcherImpl_write0(JNIEnv *env,
        jclass clazz, jobject fdo, jlong address, jint len) {
    jint fd = (*env)->GetIntField(env, fdo, fd_fdID);
    void *buf = (void *)jlong_to_ptr(address);
    return convertReturnVal(env, rs_write(fd, buf, len), JNI_FALSE);
}

JNIEXPORT jlong JNICALL
Java_jdk_internal_net_rdma_RdmaSocketDispatcherImpl_writev0(JNIEnv *env,
        jclass clazz, jobject fdo, jlong address, jint len) {
    jint fd = (*env)->GetIntField(env, fdo, fd_fdID);
    struct iovec *iov = (struct iovec *)jlong_to_ptr(address);
    return convertLongReturnVal(env, rs_writev(fd, iov, len), JNI_FALSE);
}

static void closeFileDescriptor(JNIEnv *env, int fd) {
    if (fd != -1) {
        int result = rs_close(fd);
        if (result < 0)
            JNU_ThrowIOExceptionWithLastError(env, "Close failed");
    }
}

JNIEXPORT void JNICALL
Java_jdk_internal_net_rdma_RdmaSocketDispatcherImpl_close0(JNIEnv *env,
        jclass clazz, jobject fdo) {
    jint fd = (*env)->GetIntField(env, fdo, fd_fdID);
    closeFileDescriptor(env, fd);
}
