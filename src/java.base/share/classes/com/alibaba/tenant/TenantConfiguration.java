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

import java.lang.Long;
import java.lang.Boolean;
import java.lang.System;
import java.security.AccessController;

import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.VM;
import java.util.HashMap;
import java.util.Map;
import sun.security.action.GetPropertyAction;
/**
 *
 * The configuration used by tenant
 */
public class TenantConfiguration {

    // Property names
    private static final String PROP_THREAD_INHERITANCE = "com.alibaba.tenant.threadInheritance";
    private static final String PROP_ALLOW_PER_THREAD_INHERITANCE = "com.alibaba.tenant.allowPerThreadInheritance";

    // Property values
    private static final boolean THREAD_INHERITANCE;
    private static final boolean ALLOW_PER_THREAD_INHERITANCE;

    static {
        if (!VM.isBooted()) {
            throw new IllegalStateException("TenantConfiguration must be initialized after VM.booted()");
        }

        @SuppressWarnings("removal")
        String threadInheritance = AccessController.doPrivileged(new GetPropertyAction(PROP_THREAD_INHERITANCE));
        THREAD_INHERITANCE = threadInheritance == null ? true : Boolean.parseBoolean(threadInheritance);

        @SuppressWarnings("removal")
        String perThreadInheritance = AccessController.doPrivileged(new GetPropertyAction(PROP_ALLOW_PER_THREAD_INHERITANCE));
        ALLOW_PER_THREAD_INHERITANCE = perThreadInheritance == null ? true : Boolean.parseBoolean(perThreadInheritance);

        // default value is 'true', update system threads if 'false'
        if (!THREAD_INHERITANCE) {
            ThreadGroup tg = Thread.currentThread().getThreadGroup();
            while (tg.getParent() != null) {
                tg = tg.getParent();
            }
            Thread threads[] = new Thread[tg.activeCount() << 1];
            int nofThreads = tg.enumerate(threads);
            for (int i = 0; i < nofThreads; ++i) {
                SharedSecrets.getJavaLangTenantAccess()
                        .setChildShouldInheritTenant(threads[i], THREAD_INHERITANCE);
            }
        }
    }

    /*
     * @return true if newly created threads should inherit parent's {@code TenantContainer}, otherwise false
     */
    static boolean threadInheritance() {
        return THREAD_INHERITANCE;
    }

    /*
     * @return true if allow each thread to modify its policy of
     *         inherited {@code TenantContainer} by newly created children threads
     */
    static boolean allowPerThreadInheritance() {
        return ALLOW_PER_THREAD_INHERITANCE;
    }

    /*
     * Resource throttling configurations
     */
    private Map<ResourceType, ResourceLimit> limits = new HashMap<>();

    /**
     * Create an empty TenantConfiguration, no limitations on any resource
     */
    public TenantConfiguration() {
    }

    /**
     * Use cgroup's cpu.cfs_* controller to limit cpu time of
     * new {@code TenantContainer} object created from this configuration
     * @param period    corresponding to cpu.cfs_period
     * @param quota     corresponding to cpu.cfs_quota
     * @return current {@code TenantConfiguration}
     */
    public TenantConfiguration limitCpuCfs(int period, int quota) {
        if (!TenantGlobals.isCpuThrottlingEnabled()) {
            throw new UnsupportedOperationException("-XX:+TenantCpuThrottling is not enabled");
        }
        // according to https://www.kernel.org/doc/Documentation/scheduler/sched-bwc.txt
        // cpu.cfs_period_us should be in range [1 ms, 1 sec]
        if (period < 1_000 || period > 1_000_000
                // 'quota' should never be less than 1ms as well, but can exceed 'period' which means multiple cores
                // 'quota' == -1 means no limit
                || (quota < 1_000 && quota != -1)) {
            throw new IllegalArgumentException("Illegal CPU_CFS limit " + NativeDispatcher.CG_CPU_CFS_PERIOD + " = "
                    + period + ", " + NativeDispatcher.CG_CPU_CFS_QUOTA + " =" + quota);
        }
        limits.put(ResourceType.CPU_CFS, new CpuCfsLimit(period, quota));
        return this;
    }

    /**
     * Use cgroup's cpu.shares controller to limit cpu shares of
     * new {@code TenantContainer} object created from this configuration
     * @param share relative weight of cpu shares
     * @return current {@code TenantConfiguration}
     */
    public TenantConfiguration limitCpuShares(int share) {
        if (!TenantGlobals.isCpuThrottlingEnabled()) {
            // use warning messages instead of exception here to keep backward compatibility
            System.err.println("WARNING: -XX:+TenantCpuThrottling is disabled!");
            //throw new UnsupportedOperationException("-XX:+TenantCpuThrottling is not enabled");
        }
        if (share < 0) {
            throw new IllegalArgumentException("Illegal CPU_SHARES limit "
                    + NativeDispatcher.CG_CPU_SHARES + " = " + share);
        }
        limits.put(ResourceType.CPU_SHARES, new CpuShareLimit(share));
        return this;
    }

    /**
     * Use cgroup's cpu.cpuset controller to limit cpu cores allowed to be used
     * by new {@code TenantContainer} object created from this configuration
     * @param cpuSets string of cpuset description, such as "1,2,3", "0-7,11"
     * @return current {@code TenantConfiguration}
     */
    public TenantConfiguration limitCpuSet(String cpuSets) {
        if (!TenantGlobals.isCpuThrottlingEnabled()) {
            throw new UnsupportedOperationException("-XX:+TenantCpuThrottling is not enabled");
        }
        if (cpuSets == null || cpuSets.isEmpty()) {
            throw new IllegalArgumentException("Illegal CPUSET_CPUS limit "
                    + NativeDispatcher.CG_CPUSET_CPUS + " = " + cpuSets);
        }
        limits.put(ResourceType.CPUSET_CPUS, new CpuSetLimit(cpuSets));
        return this;
    }

    /*
     * @return all resource limits specified by this configuration
     */
    ResourceLimit[] getAllLimits() {
        if (limits.size() == 0) {
            return null;
        } else {
            return limits.values().toArray(new ResourceLimit[limits.size()]);
        }
    }

    /*
     * @param type  resource type
     * @return  resource {@type}'s limit specified by this configuration
     */
    ResourceLimit getLimit(ResourceType type) {
        return limits.get(type);
    }

    /*
     * Set limmit
     * @param type  resource type to be limited
     * @param limit value of the limit
     */
    void setLimit(ResourceType type, ResourceLimit limit) {
        limits.put(type, limit);
    }

    /**
     * @see TenantConfiguration#getMaxCpuPercent()
     */
    @Deprecated
    public int getMaxCPU() {
        return getMaxCpuPercent();
    }

    /**
     * Corresponding to combination of Linux cgroup's cpu.cfs_period_us and cpu.cfs_quota_us
     * @return the max percent of cpu the tenant is allowed to consume, -1 means unlimited
     */
    public int getMaxCpuPercent() {
        if (limits.containsKey(ResourceType.CPU_CFS)) {
            CpuCfsLimit limit = (CpuCfsLimit) limits.get(ResourceType.CPU_CFS);
            int period = limit.cpuCfsPeriod;
            int quota = limit.cpuCfsQuota;
            if (period > 0 && quota > 0) {
                return (int) (((float) quota / (float) period) * 100);
            }
        }
        return -1;
    }

    /**
     * @see TenantConfiguration#getCpuShares()
     */
    @Deprecated
    public int getWeight() {
        return getCpuShares();
    }

    /**
     * Corresponding to Linux cgroup's cpu.shares
     * @return the weight, impact the ratio of cpu among all tenants.
     */
    public int getCpuShares() {
        if (limits.containsKey(ResourceType.CPU_SHARES)) {
            CpuShareLimit limit = (CpuShareLimit)limits.get(ResourceType.CPU_SHARES);
            return limit.cpuShare;
        }
        return 0;
    }

    /**
     * Corresponding to Linux cgroup's cpuset.cpus
     * @return
     */
    public String getCpuSet() {
        if (limits.containsKey(ResourceType.CPUSET_CPUS)) {
            CpuSetLimit limit = (CpuSetLimit)limits.get(ResourceType.CPUSET_CPUS);
            return limit.cpus;
        }
        return null;
    }

    // ------------- implementation of resource limitations ----------------

    // Implementation of cpu.share limits
    private static class CpuShareLimit implements ResourceLimit {
        private int cpuShare;

        CpuShareLimit(int share) {
            cpuShare = share;
        }

        @Override public ResourceType type() {
            return ResourceType.CPU_SHARES;
        }

        @Override
        public void sync(JGroup jgroup) {
            if (NativeDispatcher.IS_CPU_SHARES_ENABLED) {
                jgroup.setValue(NativeDispatcher.CG_CPU_SHARES, Integer.toString(cpuShare));
            }
        }

        @Override
        public String toString() {
            return NativeDispatcher.CG_CPU_SHARES + " = " + cpuShare;
        }
    }

    // Implementation of CPUCFS limit
    private static class CpuCfsLimit implements ResourceLimit {
        private int cpuCfsPeriod;
        private int cpuCfsQuota;

        CpuCfsLimit(int period, int quota) {
            cpuCfsPeriod = period;
            cpuCfsQuota = quota;
        }

        @Override public ResourceType type() {
            return ResourceType.CPU_CFS;
        }

        @Override
        public void sync(JGroup jgroup) {
            if (NativeDispatcher.IS_CPU_CFS_ENABLED) {
                jgroup.setValue(NativeDispatcher.CG_CPU_CFS_PERIOD, Integer.toString(cpuCfsPeriod));
                jgroup.setValue(NativeDispatcher.CG_CPU_CFS_QUOTA, Integer.toString(cpuCfsQuota));
            }
        }

        @Override
        public String toString() {
            return NativeDispatcher.CG_CPU_CFS_PERIOD + " = " + cpuCfsPeriod + ", "
                    + NativeDispatcher.CG_CPU_CFS_QUOTA + " = " + cpuCfsQuota;
        }
    }

    // implementation of CPUSET_CPUS limit
    private static class CpuSetLimit implements ResourceLimit {
        private static final int MAX_CPUS = Runtime.getRuntime().availableProcessors();
        // String representation of cpuset.cpus limit, like "0-7"
        String cpus;

        CpuSetLimit(String cpus) {
            if ( cpus == null || cpus.isEmpty()) {
                throw new IllegalArgumentException("Cpuset must be between 0 and " + MAX_CPUS);
            }
            this.cpus = cpus;
        }

        @Override public ResourceType type() {
            return ResourceType.CPUSET_CPUS;
        }

        @Override
        public void sync(JGroup jgroup) {
            if (NativeDispatcher.IS_CPUSET_ENABLED) {
                jgroup.setValue(NativeDispatcher.CG_CPUSET_CPUS, cpus);
            }
        }

        @Override
        public String toString() {
            return NativeDispatcher.CG_CPUSET_CPUS + " = " + cpus;
        }
    }
}
