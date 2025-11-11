/*
 * Copyright (c) 2025, Alibaba Group Holding Limited. All rights reserved.
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

package com.alibaba.tenant;

/**
 * Type of resource that can be throttled
 */
enum ResourceType {
    /**
     * Memory resource type
     */
    MEMORY(false),

    /**
     * Corresponding to 'cpu.shares' in CGroup
     */
    CPU_SHARES(true),

    /**
     * Corresponding to 'cpuset' in CGroup
     */
    CPUSET_CPUS(true),

    /**
     * Corresponding to 'cpu.cfs_quota_us' & 'cpu.cfs_period_us' in CGroup
     */
    CPU_CFS(true),

    /**
     * Socket resource type
     */
    SOCKET(false);

    // if this type of resource is controlled by JGroup
    private boolean isJGroupResource;

    ResourceType(boolean isJGroupRes) {
        this.isJGroupResource = isJGroupRes;
    }

    /**
     * Check if this type of resource is controlled by cgroup
     * @return
     */
    public boolean isJGroupResource() {
        return this.isJGroupResource;
    }
}
