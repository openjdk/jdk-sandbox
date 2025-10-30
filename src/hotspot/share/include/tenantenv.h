/*
 * Copyright (c) 2025, Alibaba Group Holding Limited. All rights reserved.
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

#ifndef _TENANT_ENV_H_
#define _TENANT_ENV_H_

#include "jni.h"

#define TENANT_ENV_VERSION_1_0  0x00200010 // 0x00200000 represents tenant module and the last 10 represents version 1.0

/*
 * Tenant Native Method Interface.
 */

struct TenantNativeInterface_;
struct TenantEnv_;

#ifdef __cplusplus
typedef TenantEnv_ TenantEnv;
#else
typedef const struct TenantNativeInterface_* TenantEnv;
#endif

/*
 * We use inlined functions for C++ so that programmers can write:
 *   tenantEnv->GetTenantFlags(cls);
 * in C++ rather than:
 *   (*tenantEnv)->GetTenantFlags(tenantEnv, cls);
 * in C.
 */
struct TenantNativeInterface_ {
  jint (JNICALL *GetTenantFlags)(TenantEnv *env, jclass cls);
};

struct TenantEnv_ {
  const struct TenantNativeInterface_ *functions;
#ifdef __cplusplus
  jint GetTenantFlags(jclass cls) {
    return functions->GetTenantFlags(this, cls);
  }
#endif
};

#endif
