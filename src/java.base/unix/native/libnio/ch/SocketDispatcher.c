/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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

 #include <string.h>
 #include <sys/types.h>
 #include <sys/uio.h>
 #include <unistd.h>

 #include "jni.h"
 #include "jni_util.h"
 #include "jlong.h"
 #include "nio.h"
 #include "nio_util.h"
 #include "sun_nio_ch_SocketDispatcher.h"

 JNIEXPORT jint JNICALL
 Java_sun_nio_ch_SocketDispatcher_read0(JNIEnv *env, jclass clazz,
                                        jobject fdo, jlong address, jint len)
 {
     jint fd = fdval(env, fdo);
     void *buf = (void *)jlong_to_ptr(address);
     jint n = read(fd, buf, len);
     if ((n == -1) && (errno == ECONNRESET || errno == EPIPE)) {
         JNU_ThrowByName(env, "sun/net/ConnectionResetException", "Connection reset");
         return IOS_THROWN;
     } else {
         return convertReturnVal(env, n, JNI_TRUE);
     }
 }

 JNIEXPORT jlong JNICALL
 Java_sun_nio_ch_SocketDispatcher_readv0(JNIEnv *env, jclass clazz,
                                         jobject fdo, jlong address, jint len)
 {
     jint fd = fdval(env, fdo);
     struct iovec *iov = (struct iovec *)jlong_to_ptr(address);
     jlong n = readv(fd, iov, len);
     if ((n == -1) && (errno == ECONNRESET || errno == EPIPE)) {
         JNU_ThrowByName(env, "sun/net/ConnectionResetException", "Connection reset");
         return IOS_THROWN;
     } else {
         return convertLongReturnVal(env, n, JNI_TRUE);
     }
 }

#define MAX_SEND_FDS 10

JNIEXPORT jint JNICALL
Java_sun_nio_ch_SocketDispatcher_maxsendfds0(JNIEnv *env, jclass clazz)
{
    return MAX_SEND_FDS;
}

/* read recvmsg impl. Only accepts one fd per call. Could be expanded
 * to accept more
 */
JNIEXPORT jint JNICALL
Java_sun_nio_ch_SocketDispatcher_recvmsg0(JNIEnv *env, jclass clazz,
                     jobject fdo, jlong address, jint len, jintArray fdarray)
{
    jint fd = fdval(env, fdo);
    int ret;
    void *buf = (void *)jlong_to_ptr(address);
    struct msghdr msg;
    struct iovec iov[1];
    struct cmsghdr *cmsg = NULL;
    union {
        char cmsgdata[CMSG_SPACE(sizeof(int) * MAX_SEND_FDS)];
        struct cmsghdr align;
    } u;

    memset(&msg, 0, sizeof(msg));
    memset(u.cmsgdata, 0, sizeof(u.cmsgdata));

    iov[0].iov_base = buf;
    iov[0].iov_len = len;
    msg.msg_iov = &iov[0];
    msg.msg_iovlen = 1;
    msg.msg_control = u.cmsgdata;
    msg.msg_controllen = sizeof(u.cmsgdata);

    ret = recvmsg(fd, &msg, 0);
    if (ret < 0) {
        return convertReturnVal(env, ret, JNI_TRUE);
    }
    if (msg.msg_controllen != 0) {
        cmsg = CMSG_FIRSTHDR(&msg);
        if (cmsg->cmsg_level == SOL_SOCKET && cmsg->cmsg_type == SCM_RIGHTS) {
            jint *newfds;
            jint nfds = (msg.msg_controllen - sizeof(struct cmsghdr))/sizeof(int);
            if (fdarray == NULL || (*env)->GetArrayLength(env, fdarray) == 0) {
                // close them
                int *fdptr = (int *)CMSG_DATA(cmsg);
                for (int i=0; i<nfds; i++) {
                    close(*fdptr++);
                }
            } else {
                newfds = (*env)->GetIntArrayElements(env, fdarray, NULL);
                if (newfds == NULL) {
                    JNU_ThrowIOExceptionWithLastError(env, "JNI error");
                    return -1;
                }
                memcpy(newfds, CMSG_DATA(cmsg), nfds * sizeof(int));
                (*env)->ReleaseIntArrayElements(env, fdarray, newfds, 0);
            }
        }
    }
    return convertReturnVal(env, ret, JNI_TRUE);
}


/**
 * Send a data buffer and optionaly a FilDescriptor if provided
 */
JNIEXPORT jint JNICALL
Java_sun_nio_ch_SocketDispatcher_sendmsg0(JNIEnv *env, jclass clazz,
                      jobject fdo, jlong address, jint len, jobjectArray fdarray)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);
    struct msghdr msg;
    struct iovec iov[1];
    struct cmsghdr *cmsg = NULL;
    union {
        char cmsgdata[CMSG_SPACE(sizeof(int) * MAX_SEND_FDS)];
        struct cmsghdr align;
    } u;
    int ret;

    memset(&msg, 0, sizeof(msg));
    memset(u.cmsgdata, 0, sizeof(u.cmsgdata));

    iov[0].iov_base = buf;
    iov[0].iov_len = len;
    msg.msg_iov = &iov[0];
    msg.msg_iovlen = 1;
    msg.msg_controllen = 0;

    if (fdarray != NULL) {
        jsize arraylen = (*env)->GetArrayLength(env, fdarray);
        int fds[arraylen];

        for (int i=0; i<arraylen; i++) {
            jobject fdsend = (*env)->GetObjectArrayElement(env, fdarray, i);
            fds[i] = fdval(env, fdsend);
        }
        msg.msg_control = u.cmsgdata;
        msg.msg_controllen = CMSG_SPACE(sizeof(int) * arraylen);
        cmsg = CMSG_FIRSTHDR(&msg);
        cmsg->cmsg_len = CMSG_LEN(sizeof(int) * arraylen);
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type = SCM_RIGHTS;
        memcpy(CMSG_DATA(cmsg), fds, sizeof(int) * arraylen);
    }

    ret = sendmsg(fd, &msg, 0);
    return convertReturnVal(env, ret, JNI_FALSE);
}
