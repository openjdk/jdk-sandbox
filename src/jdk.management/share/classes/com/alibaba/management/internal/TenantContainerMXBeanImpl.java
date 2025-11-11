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

package com.alibaba.management.internal;


import com.alibaba.tenant.TenantContainer;
import sun.management.Util;
import javax.management.ObjectName;
import java.util.List;

/**
 * Implementation class for TenantContainerMXBean.
 */
public class TenantContainerMXBeanImpl implements com.alibaba.management.TenantContainerMXBean {

    private final static String TENANT_CONTAINER_MXBEAN_NAME = "com.alibaba.management:type=TenantContainer";

    @Override
    public List<Long> getAllTenantIds() {
        return TenantContainer.getAllTenantIds();
    }

    @Override
    public long getTenantProcessCpuTimeById(long id) {
        TenantContainer container = TenantContainer.getTenantContainerById(id);
        if (null == container) {
            throw new IllegalArgumentException("The id of tenant is invalid !");
        }
        return container.getProcessCpuTime();
    }

    @Override
    public ObjectName getObjectName() {
        return Util.newObjectName(TENANT_CONTAINER_MXBEAN_NAME);
    }

    @Override
    public String getTenantNameById(long id) {
        TenantContainer container = TenantContainer.getTenantContainerById(id);
        if (null == container) {
            throw new IllegalArgumentException("The id of tenant is invalid !");
        }
        return container.getName();
    }

    @Override
    public long getActiveVirtualThreadCount(long id) {
        TenantContainer container = TenantContainer.getTenantContainerById(id);
        if(container == null) {
            return 0;
        }
        return container.getActiveVirtualThreadCount();
    }
}
