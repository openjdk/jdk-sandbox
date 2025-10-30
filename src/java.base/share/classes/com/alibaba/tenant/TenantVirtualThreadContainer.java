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

import static java.util.concurrent.TimeUnit.SECONDS;

import java.security.AccessController;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;
import jdk.internal.access.SharedSecrets;
import jdk.internal.misc.CarrierThread;
import jdk.internal.misc.InnocuousThread;
import jdk.internal.vm.ThreadContainer;
import jdk.internal.vm.ThreadContainers;
import sun.security.action.GetIntegerAction;
import sun.security.action.GetPropertyAction;
/**
 * a VirtualThreadContainer with independent carrier thread pool.
 * VirtualThreadContainer will binded with a TenantContainer,
 * if TenantContainer is null, all carrier thread is force to root tenant,
 * otherwise all carrier thread will inherit this tenant.
 * and custom fork-join parallelism and pool size is supported.
 */
class TenantVirtualThreadContainer extends ThreadContainer {

    private static final int AVAILABLE_PROCESSORS = Runtime.getRuntime().availableProcessors();
    private static final AtomicLong CONTAINER_COUNTER = new AtomicLong(0);
    private SchedulerPoolConfig scheduleConfig;
    private String name;
    private Object key;
    private TenantContainer tenantContainer;
    private final Set<Thread> CARRIER_THREADS = ConcurrentHashMap.newKeySet();
    private final Set<Thread> VIRTUAL_THREADS = ConcurrentHashMap.newKeySet();
    private ForkJoinPool scheduler = null;

    static class SchedulerPoolConfig {
        private int parallelism = AVAILABLE_PROCESSORS;
        private int maxPoolSize = Integer.max(AVAILABLE_PROCESSORS, 256);
        private int minRunnable = Integer.max(AVAILABLE_PROCESSORS / 2, 1);

        SchedulerPoolConfig() {
        }

        SchedulerPoolConfig(int parallelism, int maxPoolSize, int minRunnable) {
            this.parallelism = parallelism;
            this.maxPoolSize = maxPoolSize;
            this.minRunnable = minRunnable;
        }

        public int getParallelism() {
            return this.parallelism;
        }

        public int getMaxPoolSize() {
            return this.maxPoolSize;
        }

        public int getMinRunnable() {
            return this.minRunnable;
        }
    }

    TenantVirtualThreadContainer(TenantContainer tenantContainer, SchedulerPoolConfig cfg) {

        super(true);

        if (tenantContainer != null) {
            assert TenantGlobals.isTenantEnabled();
            this.name = "TenantVirtualThreadContainer-" + tenantContainer.getTenantId() + "-"
                    + CONTAINER_COUNTER.incrementAndGet();
        } else {
            this.name = "RootVirtualThreadContainer-" + CONTAINER_COUNTER.incrementAndGet();
        }

        this.tenantContainer = tenantContainer;

        int parallelism, maxPoolSize, minRunnable;

        if(cfg != null && cfg.getParallelism() > 0) {
            parallelism = cfg.getParallelism();
        } else {
            @SuppressWarnings("removal")
            Integer parallelismValue = AccessController.doPrivileged(
                new GetIntegerAction("jdk.virtualThreadScheduler.parallelism", AVAILABLE_PROCESSORS));
            parallelism = parallelismValue;
        }

        if(cfg != null && cfg.getMaxPoolSize() > 0) {
            maxPoolSize = cfg.getMaxPoolSize();
            parallelism = Integer.min(parallelism, maxPoolSize);
        } else {
            @SuppressWarnings("removal")
            String maxPoolSizeValue = AccessController.doPrivileged(
                new GetPropertyAction("jdk.virtualThreadScheduler.maxPoolSize"));
            if (maxPoolSizeValue != null) {
                maxPoolSize = Integer.parseInt(maxPoolSizeValue);
                parallelism = Integer.min(parallelism, maxPoolSize);
            } else {
                maxPoolSize = Integer.max(parallelism, 256);
            }
        }


        if(cfg != null && cfg.getMinRunnable() > 0) {
            minRunnable = Integer.min(cfg.getMinRunnable(), maxPoolSize);
        } else {
            @SuppressWarnings("removal")
            String minRunnableValue = AccessController.doPrivileged(
                new GetPropertyAction("jdk.virtualThreadScheduler.minRunnable"));
            if (minRunnableValue != null) {
                minRunnable = Integer.parseInt(minRunnableValue);
            } else {
                minRunnable = Integer.max(parallelism / 2, 1);
            }
        }
        this.scheduleConfig = new SchedulerPoolConfig(parallelism, maxPoolSize, minRunnable);
    }

    static TenantVirtualThreadContainer create(TenantContainer tenantContainer) {

        TenantVirtualThreadContainer container = new TenantVirtualThreadContainer(tenantContainer, null);
        register(container);
        return container;
    }

    static void register(TenantVirtualThreadContainer container) {

        // register it to allow discovery by serviceability tools
        container.key = ThreadContainers.registerContainer(container);
    }

    public ForkJoinPool scheduler() {

        if (scheduler != null) {
            return scheduler;
        }
        synchronized (this) {
            if (scheduler != null) {
                return scheduler;
            }
            scheduler = createScheduler();
            return scheduler;
        }
    }

    /**
     * use a special ForkJoinPool to make CarrierThread inherited threadFactory's TenantContainer forcelly
     * @see com.alibaba.tenant.TenantContainer#setPoolThreadInheritedTenantContainer
     */
    static class TenantForkJoinPool extends ForkJoinPool {

        public TenantForkJoinPool(int parallelism,
                        ForkJoinWorkerThreadFactory factory,
                        Thread.UncaughtExceptionHandler handler,
                        boolean asyncMode,
                        int corePoolSize,
                        int maximumPoolSize,
                        int minimumRunnable,
                        Predicate<? super ForkJoinPool> saturate,
                        long keepAliveTime,
                        TimeUnit unit) {
            super(parallelism, factory, handler, asyncMode,
                corePoolSize, maximumPoolSize, minimumRunnable, saturate, keepAliveTime, unit);
        }
    }

    class TenantCarrierThread extends CarrierThread {

        public TenantCarrierThread(ForkJoinPool pool) {
            super(pool);
        }

        @Override
        public void start() {
            // add carrier thread to thread container
            SharedSecrets.getJavaLangAccess().start(this, TenantVirtualThreadContainer.this);
        }
    }

    private ForkJoinPool createScheduler() {
        ForkJoinWorkerThreadFactory factory = pool -> {
            CarrierThread[] carrierThreads = new CarrierThread[1];
            if (tenantContainer == null) {
                if (TenantGlobals.isTenantEnabled()) {
                    // run in root tenant
                    TenantContainer.primitiveRunInRoot(() -> {
                        carrierThreads[0] = new CarrierThread(pool);
                        TenantContainer.setInheritedTenantContainer(carrierThreads[0], null);
                    });
                } else {
                    carrierThreads[0] = new CarrierThread(pool);
                }
            } else {
                TenantContainer.primitiveRunInRoot(() -> {
                    try {
                        tenantContainer.run(() -> {
                            carrierThreads[0] = new TenantCarrierThread(pool);
                            TenantContainer.setInheritedTenantContainer(carrierThreads[0], tenantContainer);
                        });
                    } catch (TenantException ex) {
                        throw new RuntimeException("new carrier thread error in tenant container", ex);
                    }
                });
            }
            return carrierThreads[0];
        };
        Thread.UncaughtExceptionHandler handler = (t, e) -> {
        };
        boolean asyncMode = true; // FIFO
        return new TenantForkJoinPool(scheduleConfig.getParallelism(), factory, handler, asyncMode,
            0, scheduleConfig.getMaxPoolSize(), scheduleConfig.getMinRunnable(), pool -> true, 30, SECONDS);
    }

    public void shutdown() {
        ThreadContainers.deregisterContainer(key);
    }

    @Override
    public String name() {

        return name;
    }

    @Override
    public String toString() {

        return name();
    }

    @Override
    public void onStart(Thread thread) {

        if (thread.isVirtual()) {
            VIRTUAL_THREADS.add(thread);
        } else {
            CARRIER_THREADS.add(thread);
            if(TenantGlobals.isTenantEnabled() && tenantContainer != null) {
                tenantContainer.onCarrierThreadStart(thread);
            }
        }
    }

    @Override
    public void onExit(Thread thread) {

        if (thread.isVirtual()) {
            VIRTUAL_THREADS.remove(thread);
        } else {
            CARRIER_THREADS.remove(thread);
            if (TenantGlobals.isTenantEnabled() && tenantContainer != null) {
                tenantContainer.onCarrierThreadExit(thread);
            }
        }
    }

    @Override
    public long threadCount() {

        return VIRTUAL_THREADS.size() + CARRIER_THREADS.size();
    }

    @Override
    public Stream<Thread> threads() {

        return Stream.concat(CARRIER_THREADS.stream().filter((t) -> t.isAlive() && t.getState() != Thread.State.TERMINATED),
            VIRTUAL_THREADS.stream().filter((t) -> t.isAlive() && t.getState() != Thread.State.TERMINATED));
    }
}
