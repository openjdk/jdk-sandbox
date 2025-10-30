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

package jdk.internal.access;

import com.alibaba.tenant.TenantContainer.NewPoolTenantPredicateTuple;
import com.alibaba.tenant.TenantContainer.NewThreadTenantPredicateTuple;
import com.alibaba.tenant.TenantContainer.PoolThreadTenantPredicateTuple;
import java.util.function.Predicate;

public interface TenantAccess {

    /**
     * Use may use {@code TenantContainer.setCurrentThreadInheritance} to set status of each Java thread separately
     *
     * @return by default, if newly created threads should inherit parent thread's TenantContainer.
     */
    boolean threadInheritance();

    boolean threadInheritance(Thread thread);

    void setTenantDeathToVThread(Thread virtualThread);

    Runnable createTenantContinuationEntry(Runnable runnable);

    void setNewThreadTenantInheritancePredicate(Predicate<NewThreadTenantPredicateTuple> predicate);

    void setNewPoolTenantInheritancePredicate(Predicate<NewPoolTenantPredicateTuple> predicate);

    void setPoolThreadTenantInheritancePredicate(Predicate<PoolThreadTenantPredicateTuple> predicate);
}
