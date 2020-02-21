/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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

#include <stdlib.h>
#include <stdio.h>
#include <stdlib.h>
#include <stddef.h>
#include <time.h>
#include <assert.h>
#include <winsock2.h>
#include <ws2tcpip.h>
#include "net_util.h"
#include "jni_util.h"

#define MAX_STR_LEN         1024

#define STS_NO_CONFIG       0x0             /* no configuration found */
#define STS_SL_FOUND        0x1             /* search list found */
#define STS_NS_FOUND        0x2             /* name servers found */
#define STS_HN_FOUND        0x4             /* host name found */
#define STS_ERROR           -1              /* error return  lodConfig failed memory allocation failure*/

#define IS_SL_FOUND(sts)    (sts & STS_SL_FOUND)
#define IS_NS_FOUND(sts)    (sts & STS_NS_FOUND)

/* JNI ids */
static jfieldID searchlistID;
static jfieldID nameserversID;
static jfieldID hostnameID;

// Until the linking of resolver.dll with net.dll is resolved will
// keep the copy of getAdapters function here
// extern int getAdapters(JNIEnv *env, int flags, IP_ADAPTER_ADDRESSES **adapters);
/*
 * return an array of IP_ADAPTER_ADDRESSES containing one element
 * for each adapter on the system. Returned in *adapters.
 * Buffer is malloc'd and must be freed (unless error returned)
 */
const ULONG BUFF_SIZE = 15360;
const int MAX_TRIES = 3;
int getAdapters (JNIEnv *env, int flags, IP_ADAPTER_ADDRESSES **adapters) {
    DWORD ret;
    IP_ADAPTER_ADDRESSES *adapterInfo;
    ULONG len;
    int try;

    adapterInfo = (IP_ADAPTER_ADDRESSES *) malloc(BUFF_SIZE);
    if (adapterInfo == NULL) {
        JNU_ThrowByName(env, "java/lang/OutOfMemoryError",
            "Native heap allocation failure");
        return -1;
    }

    len = BUFF_SIZE;
    ret = GetAdaptersAddresses(AF_UNSPEC, flags, NULL, adapterInfo, &len);

    for (try = 0; ret == ERROR_BUFFER_OVERFLOW && try < MAX_TRIES; ++try) {
        IP_ADAPTER_ADDRESSES * newAdapterInfo = NULL;
        if (len < (ULONG_MAX - BUFF_SIZE)) {
            len += BUFF_SIZE;
        }
        newAdapterInfo =
            (IP_ADAPTER_ADDRESSES *) realloc (adapterInfo, len);
        if (newAdapterInfo == NULL) {
            free(adapterInfo);
            JNU_ThrowByName(env, "java/lang/OutOfMemoryError",
                "Native heap allocation failure");
            return -1;
        }

        adapterInfo = newAdapterInfo;

        ret = GetAdaptersAddresses(AF_UNSPEC, flags, NULL, adapterInfo, &len);
    }

    if (ret != ERROR_SUCCESS) {
        free (adapterInfo);
        if (ret == ERROR_INSUFFICIENT_BUFFER) {
            JNU_ThrowByName(env, "java/lang/Error",
                "IP Helper Library GetAdaptersAddresses function failed "
                "with ERROR_INSUFFICIENT_BUFFER");
        } else if (ret == ERROR_ADDRESS_NOT_ASSOCIATED ) {
            JNU_ThrowByName(env, "java/lang/Error",
                "IP Helper Library GetAdaptersAddresses function failed "
                "with ERROR_ADDRESS_NOT_ASSOCIATED");
        } else {
            char error_msg_buf[100];
            int _sr;
            _sr = _snprintf_s(error_msg_buf, sizeof(error_msg_buf),
                _TRUNCATE, "IP Helper Library GetAdaptersAddresses "
                            "function failed with error == %d", ret);
            if (_sr != -1) {
                JNU_ThrowByName(env, "java/lang/Error", error_msg_buf);
            } else {
                JNU_ThrowByName(env, "java/lang/Error",
                    "IP Helper Library GetAdaptersAddresses function failure");
            }
        }
        return -1;
    }
    *adapters = adapterInfo;
    return ERROR_SUCCESS;
}

/*
 * Utility routine to append s2 to s1 with a space delimiter.
 *  strappend(s1="abc", "def")  => "abc def"
 *  strappend(s1="", "def")     => "def
 */
void strappend(char *s1, char *s2) {
    size_t len;

    if (s2[0] == '\0')                      /* nothing to append */
        return;

    len = strlen(s1)+1;
    if (s1[0] != 0)                         /* needs space character */
        len++;
    if (len + strlen(s2) > MAX_STR_LEN)     /* insufficient space */
        return;

    if (s1[0] != 0) {
        strcat(s1, " ");
    }
    strcat(s1, s2);
}

/*
 * Windows 2000/XP
 *
 * Use registry approach based on settings described in Appendix C
 * of "Microsoft Windows 2000 TCP/IP Implementation Details".
 *
 * DNS suffix list is obtained from SearchList registry setting. If
 * this is not specified we compile suffix list based on the
 * per-connection domain suffix.
 *
 * DNS name servers and domain settings are on a per-connection
 * basic. We therefore enumerate the network adapters to get the
 * names of each adapter and then query the corresponding registry
 * settings to obtain NameServer/DhcpNameServer and Domain/DhcpDomain.
 */
static int loadConfig(JNIEnv *env, char *sl, char *ns, char *hn) {
    IP_ADAPTER_ADDRESSES *adapters, *adapter;
    IP_ADAPTER_DNS_SERVER_ADDRESS *dnsServer;
    WCHAR *suffix;
    DWORD ret, flags;
    DWORD dwLen;
    ULONG ulType;
    char result[MAX_STR_LEN];
    char hostname[MAX_STR_LEN];
    HANDLE hKey;
    DWORD dwHostNameLen;
    int gotHostName = 0;
    SOCKADDR *sockAddr;
    struct sockaddr_in6 *sockAddrIpv6;

    /*
     * First see if there is a global suffix list specified.
     */
    ret = RegOpenKeyEx(HKEY_LOCAL_MACHINE,
                       "SYSTEM\\CurrentControlSet\\Services\\Tcpip\\Parameters",
                       0,
                       KEY_READ,
                       (PHKEY)&hKey);
    if (ret == ERROR_SUCCESS) {
        dwLen = sizeof(result);
        ret = RegQueryValueEx(hKey, "SearchList", NULL, &ulType,
                             (LPBYTE)&result, &dwLen);
        if (ret == ERROR_SUCCESS) {
            assert(ulType == REG_SZ);
            if (strlen(result) > 0) {
                strappend(sl, result);
            }
        }
       /*
        * Get the hostname from registry key
        */
        dwHostNameLen = sizeof(hostname);
        ret = RegQueryValueEx(hKey, "Hostname", NULL, &ulType,
                              (LPBYTE)&hostname, &dwHostNameLen);
        if (ret == ERROR_SUCCESS) {
            // Check that value type is String
            assert(ulType == REG_SZ);
            if (strlen(hostname) > 0) {
                strappend(hn, hostname);
                gotHostName = 1;
            }
        }
        RegCloseKey(hKey);
    }

    // We only need DNS server addresses so skip everything else.
    flags = GAA_FLAG_SKIP_UNICAST;
    flags |= GAA_FLAG_SKIP_ANYCAST;
    flags |= GAA_FLAG_SKIP_MULTICAST;
    flags |= GAA_FLAG_SKIP_FRIENDLY_NAME;
    ret = getAdapters(env, flags, &adapters);
    if (ret != ERROR_SUCCESS) {
        return STS_ERROR;
    }
    adapter = adapters;
    while (adapter != NULL) {
        // Only load config from enabled adapters.
        if (adapter->OperStatus == IfOperStatusUp) {
           dnsServer = adapter->FirstDnsServerAddress;
            while (dnsServer != NULL) {
                sockAddr = dnsServer->Address.lpSockaddr;
                if (sockAddr->sa_family == AF_INET6) {
                    sockAddrIpv6 = (struct sockaddr_in6 *)sockAddr;
                    if (sockAddrIpv6->sin6_scope_id != 0) {
                        // An address with a scope is either link-local or
                        // site-local, which aren't valid for DNS queries so
                        // we can skip them.
                        dnsServer = dnsServer->Next;
                        continue;
                    }
                }

                dwLen = sizeof(result);
                ret = WSAAddressToStringA(sockAddr,
                          dnsServer->Address.iSockaddrLength, NULL,
                          result, &dwLen);
                if (ret == 0) {
                    strappend(ns, result);
                }
                dnsServer = dnsServer->Next;
            }
            // Add connection-specific search domains in addition to global one.
            suffix = adapter->DnsSuffix;
            if (suffix != NULL) {
                ret = WideCharToMultiByte(CP_UTF8, 0, suffix, -1,
                    result, sizeof(result), NULL, NULL);
                if (ret != 0) {
                    strappend(sl, result);
                }
            }
        }
        adapter = adapter->Next;
    }

    free(adapters);

    return STS_SL_FOUND & STS_NS_FOUND & STS_HN_FOUND;
}


/*
 * Initialize JNI field IDs.
 */
JNIEXPORT void JNICALL
Java_jdk_dns_conf_DnsResolverConfiguration_init0(JNIEnv *env, jclass cls)
{
    searchlistID = (*env)->GetStaticFieldID(env, cls, "os_searchlist",
                                      "Ljava/lang/String;");
    CHECK_NULL(searchlistID);

    nameserversID = (*env)->GetStaticFieldID(env, cls, "os_nameservers",
                                      "Ljava/lang/String;");
    CHECK_NULL(nameserversID);

    hostnameID = (*env)->GetStaticFieldID(env, cls, "os_hostname",
                                      "Ljava/lang/String;");
    CHECK_NULL(hostnameID);
}

/*
 * Class:     jdk_dns_conf_DnsResolverConfiguration
 * Method:    loadDNSconfig0
 * Signature: ()V
 */
JNIEXPORT void JNICALL
Java_jdk_dns_conf_DnsResolverConfiguration_loadDNSconfig0(JNIEnv *env, jclass cls)
{
    char searchlist[MAX_STR_LEN];
    char nameservers[MAX_STR_LEN];
    char hostname[MAX_STR_LEN];
    jstring obj;

    searchlist[0] = '\0';
    nameservers[0] = '\0';
    hostname[0] = '\0';

    if (loadConfig(env, searchlist, nameservers, hostname) != STS_ERROR) {

        /*
         * Populate static fields in jdk.dns.conf.ResolverConfiguration
         */
        obj = (*env)->NewStringUTF(env, searchlist);
        CHECK_NULL(obj);
        (*env)->SetStaticObjectField(env, cls, searchlistID, obj);

        obj = (*env)->NewStringUTF(env, nameservers);
        CHECK_NULL(obj);
        (*env)->SetStaticObjectField(env, cls, nameserversID, obj);

        obj = (*env)->NewStringUTF(env, hostname);
        CHECK_NULL(obj);
        (*env)->SetStaticObjectField(env, cls, hostnameID, obj);
    }
}


char *getLocalHostAddressesString(const char *lhn) {
    struct addrinfo hints, *res =  NULL, *iterator;
    int error = 0;
    // Initialize addresses String
    char addressesString[MAX_STR_LEN];
    char addressHolder[MAX_STR_LEN];
    addressesString[0] = '\0';
    addressHolder[0] = '\0';
    int ret;

    // Call native resolve
    memset(&hints, 0, sizeof(hints));
    hints.ai_flags = AI_CANONNAME;
    hints.ai_family = AF_UNSPEC;

    error = getaddrinfo(lhn, NULL, &hints, &res);
    if (error) {
        return _strdup("");
    }
    // Iterate over all addresses and convert them to string
    int addrskipcount = 0;
    int addr4count = 0;
    int addr6count = 0;
    iterator = res;

    while (iterator != NULL) {
        BOOL addAddress = TRUE;
        BOOL isIpv6 = FALSE;
        if (iterator->ai_family == AF_INET) {
            addr4count++;
        } else if (iterator->ai_family == AF_INET6) {
            isIpv6 = TRUE;
            addr6count++;
        } else {
            addAddress = FALSE;
            addrskipcount++;
        }

        DWORD dwlen = MAX_STR_LEN;
        if (addAddress) {
            ret = WSAAddressToStringA(iterator->ai_addr, (DWORD) iterator->ai_addrlen, NULL, addressHolder, &dwlen);
            if (ret == 0) {
                strappend(addressesString, addressHolder);
            } else {
                printf("Error converting address:%d\n", ret);
            }
        }
        iterator = iterator->ai_next;
    }
    return _strdup(addressesString);
}

/*
 * Class:     jdk_dns_conf_DnsResolverConfiguration
 * Method:    nativeLocalhostResolve0
 * Signature: (ILjava/lang/String;)Ljava/lang/String;
 */
JNIEXPORT jstring JNICALL
Java_jdk_dns_conf_DnsResolverConfiguration_nativeLocalhostResolve0(JNIEnv *env, jclass cls,
    jstring jlocalhostName)
{
    const char *lhn;
    char *addressesString;

    lhn = JNU_GetStringPlatformChars(env, jlocalhostName, JNI_FALSE);
    CHECK_NULL_RETURN(lhn, NULL);

    // Construct local host addresses string
    addressesString = getLocalHostAddressesString(lhn);

    (*env)->ReleaseStringChars(env, jlocalhostName, lhn);

    return (*env)->NewStringUTF(env, addressesString);
}

/*
 * Class:     jdk_dns_conf_DnsResolverConfiguration
 * Method:    notifyAddrChange0
 * Signature: ()I
 */
JNIEXPORT jint JNICALL
Java_jdk_dns_conf_DnsResolverConfiguration_notifyAddrChange0(JNIEnv *env, jclass cls)
{
    OVERLAPPED ol;
    HANDLE h;
    DWORD rc, xfer;

    ol.hEvent = (HANDLE)0;
    rc = NotifyAddrChange(&h, &ol);
    if (rc == ERROR_IO_PENDING) {
        rc = GetOverlappedResult(h, &ol, &xfer, TRUE);
        if (rc != 0) {
            return 0;   /* address changed */
        }
    }

    /* error */
    return -1;
}
