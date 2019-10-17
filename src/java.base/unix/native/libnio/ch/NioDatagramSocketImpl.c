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

#include "jni.h"
#include "jni_util.h"
#include "jvm.h"
#include "jlong.h"

#include <netdb.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>

#if defined(__linux__) || defined(_ALLBSD_SOURCE)
#include <netinet/in.h>
#endif

#include "net_util.h"
#include "net_util_md.h"
#include "nio.h"
#include "nio_util.h"

#include "sun_nio_ch_NioDatagramSocketImpl.h"

static jfieldID dsi_senderID;   /* sender in sun.nio.ch.NioDatagramSocketImpl */
static jclass isa_class;        /* java.net.InetSocketAddress */
static jmethodID isa_ctorID;    /* InetSocketAddress(InetAddress, int) */

JNIEXPORT void JNICALL
Java_sun_nio_ch_NioDatagramSocketImpl_initIDs(JNIEnv *env, jclass clazz)
{
    clazz = (*env)->FindClass(env, "java/net/InetSocketAddress");
    CHECK_NULL(clazz);
    isa_class = (*env)->NewGlobalRef(env, clazz);
    if (isa_class == NULL) {
        JNU_ThrowOutOfMemoryError(env, NULL);
        return;
    }
    isa_ctorID = (*env)->GetMethodID(env, clazz, "<init>", "(Ljava/net/InetAddress;I)V");
    CHECK_NULL(isa_ctorID);

    clazz = (*env)->FindClass(env, "sun/nio/ch/NioDatagramSocketImpl");
    CHECK_NULL(clazz);
    dsi_senderID = (*env)->GetFieldID(env, clazz, "sender", "Ljava/net/InetSocketAddress;");
    CHECK_NULL(dsi_senderID);
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_NioDatagramSocketImpl_send0(JNIEnv *env,
                                            jclass clazz,
                                            jboolean preferIPv6,
                                            jobject fdo,
                                            jlong address,
                                            jint len,
                                            jobject destAddress,
                                            jint destPort)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);
    SOCKETADDRESS sa;
    struct sockaddr *saP = NULL;
    int sa_len = 0;
    jint n = 0;

    if (destAddress != NULL) {  // not connected
        if (NET_InetAddressToSockaddr(env, destAddress, destPort, &sa,
                                      &sa_len, preferIPv6) != 0) {
            return IOS_THROWN;
        }
        saP = &sa.sa;
    }

    n = sendto(fd, buf, len, 0, saP, sa_len);
    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return IOS_UNAVAILABLE;
        }
        if (errno == EINTR) {
            return IOS_INTERRUPTED;
        }
        if (errno == ECONNREFUSED) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "PortUnreachableException", 0);
            return IOS_THROWN;
        }
        return handleSocketError(env, errno);
    }
    return n;
}

JNIEXPORT jint JNICALL
Java_sun_nio_ch_NioDatagramSocketImpl_receive0(JNIEnv *env,
                                               jobject this,
                                               jobject fdo,
                                               jlong address,
                                               jint len,
                                               jboolean isPeek,
                                               jobject cachedSenderAddress,
                                               jint cachedSenderPort)
{
    jint fd = fdval(env, fdo);
    void *buf = (void *)jlong_to_ptr(address);
    SOCKETADDRESS sa;
    socklen_t sa_len = sizeof(SOCKETADDRESS);
    jboolean retry = JNI_FALSE;
    int flags = 0;
    jint n = 0;
    jobject senderAddr;

    if (isPeek == JNI_TRUE) {
        flags = MSG_PEEK;
    }

    n = recvfrom(fd, buf, len, flags, &sa.sa, &sa_len);
    if (n < 0) {
        if (errno == EAGAIN || errno == EWOULDBLOCK) {
            return IOS_UNAVAILABLE;
        } else if (errno == EINTR) {
            return IOS_INTERRUPTED;
        } else if (errno == ECONNREFUSED) {
            JNU_ThrowByName(env, JNU_JAVANETPKG "PortUnreachableException",
                            "ICMP Port Unreachable");
            return IOS_THROWN;
        } else {
            return handleSocketError(env, errno);
        }
    }

    // If the cached address does not match, then create a new one.
    if (cachedSenderAddress == NULL ||
        !NET_SockaddrEqualsInetAddress(env, &sa, cachedSenderAddress) ||
        cachedSenderPort != NET_GetPortFromSockaddr(&sa)) {

        jobject isa = NULL;
        int port = 0;
        jobject ia = NET_SockaddrToInetAddress(env, &sa, &port);
        if (ia != NULL) {
            isa = (*env)->NewObject(env, isa_class, isa_ctorID, ia, port);
        }
        CHECK_NULL_RETURN(isa, IOS_THROWN);

        (*env)->SetObjectField(env, this, dsi_senderID, isa);
    }

    return n;
}

JNIEXPORT void JNICALL
Java_sun_nio_ch_NioDatagramSocketImpl_disconnect0(JNIEnv *env,
                                                 jobject clazz,
                                                 jobject fdo,
                                                 jboolean isIPv6)
{
    jint fd = fdval(env, fdo);
    int rv;

#if defined(__solaris__)
    rv = connect(fd, 0, 0);
#else
    SOCKETADDRESS sa;
    socklen_t len = isIPv6 ? sizeof(struct sockaddr_in6) :
                             sizeof(struct sockaddr_in);

    memset(&sa, 0, sizeof(sa));
#if defined(_ALLBSD_SOURCE)
    sa.sa.sa_family = isIPv6 ? AF_INET6 : AF_INET;
#else
    sa.sa.sa_family = AF_UNSPEC;
#endif

    rv = connect(fd, &sa.sa, len);

#if defined(_ALLBSD_SOURCE)
    if (rv < 0 && errno == EADDRNOTAVAIL)
        rv = errno = 0;
#elif defined(_AIX)
    /* See W. Richard Stevens, "UNIX Network Programming, Volume 1", p. 254:
     * 'Setting the address family to AF_UNSPEC might return EAFNOSUPPORT
     * but that is acceptable.
     */
    if (rv < 0 && errno == EAFNOSUPPORT)
        rv = errno = 0;
#endif // defined(_ALLBSD_SOURCE) || defined(_AIX)

#endif // defined(__solaris__)

    if (rv < 0)
        handleSocketError(env, errno);
}
