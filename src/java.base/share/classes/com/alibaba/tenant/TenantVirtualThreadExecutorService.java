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

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.SharedSecrets;

public class TenantVirtualThreadExecutorService extends AbstractExecutorService {

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();

    private static final VarHandle STATE;
    private static final Constructor<?> ofVirtualConstructor;

    static {
        try {
            MethodHandles.Lookup l = MethodHandles.lookup();
            STATE = l.findVarHandle(TenantVirtualThreadExecutorService.class, "state", int.class);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
        try {
            Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
            ofVirtualConstructor = clazz.getDeclaredConstructor(Executor.class);
            ofVirtualConstructor.setAccessible(true);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    private final int maxTaskCount;
    private final Semaphore semaphore;
    private final ThreadFactory factory;
    // states: RUNNING -> SHUTDOWN -> TERMINATED
    private static final int RUNNING = 0;
    private static final int SHUTDOWN = 1;
    private static final int TERMINATED = 2;
    private volatile int state;
    private final CountDownLatch terminationSignal = new CountDownLatch(1);
    private TenantVirtualThreadContainer threadContainer;
    private RejectedExecutionHandler rejectHandler;

    /**
     * reject policy is set to reject
     *
     * @param prefix       thread name prefix, will add counter start from 1
     * @param maxTaskCount max parallel
     * @param forceRoot    force to run in root tenant, otherwise tenant container will inherited from caller thread
     */
    public TenantVirtualThreadExecutorService(String prefix, int maxTaskCount, boolean forceRoot) {

        this(prefix, maxTaskCount, forceRoot, null);
    }

    /**
     * @param prefix        thread name prefix, will add counter start from 1
     * @param maxTaskCount  max parallel
     * @param forceRoot     force to run in root tenant, otherwise tenant container will inherited from caller thread
     * @param rejectHandler reject policy, default is reject if set null
     */
    public TenantVirtualThreadExecutorService(String prefix, int maxTaskCount, boolean forceRoot,
        RejectedExecutionHandler rejectHandler) {

        this(null, prefix, maxTaskCount, -1, -1, -1, forceRoot, rejectHandler);
    }

    /**
     * reject policy is set to reject
     *
     * @param tenantContainer tenant can not be null, tenant container will inherited from caller thread
     * @param prefix          thread name prefix, will add counter start from 1
     * @param maxTaskCount    max parallel
     */
    public TenantVirtualThreadExecutorService(TenantContainer tenantContainer, String prefix, int maxTaskCount) {

        this(tenantContainer, prefix, maxTaskCount, -1, -1 , -1, null);
    }

     /**
     * reject policy is set to reject
     *
     * @param tenantContainer tenant can not be null, tenant container will inherited from caller thread
     * @param prefix          thread name prefix, will add counter start from 1
     * @param maxTaskCount    max parallel
     * @param rejectHandler   reject policy, default is reject if set null
     */
    public TenantVirtualThreadExecutorService(TenantContainer tenantContainer, String prefix, int maxTaskCount,
        RejectedExecutionHandler rejectHandler) {

        this(tenantContainer, prefix, maxTaskCount, -1, -1, -1, rejectHandler);
    }

    /**
     * reject policy is set to reject
     *
     * @param tenantContainer tenant can not be null, tenant container will inherited from caller thread
     * @param prefix          thread name prefix, will add counter start from 1
     * @param maxTaskCount    max parallel
     * @param schedulerParallelism parallelism of carrier thread pool
     * @param maxSchedulerPoolSize max pool size of carrier thread pool
     * @param minSchedulerRunnable min runnable size of carrier thread pool
     * @param rejectHandler   reject policy, default is reject if set null
     */
    public TenantVirtualThreadExecutorService(TenantContainer tenantContainer, String prefix, int maxTaskCount,
        int schedulerParallelism, int maxSchedulerPoolSize, int minSchedulerRunnable,
        RejectedExecutionHandler rejectHandler) {

        this(tenantContainer, prefix, maxTaskCount, schedulerParallelism, maxSchedulerPoolSize, minSchedulerRunnable,
            false, rejectHandler);
        if (tenantContainer == null) {
            throw new NullPointerException("tenantContainer can not be null");
        }
    }

    /**
     * @param tenantContainer tenant container, is set to null, tenant container will inherited from caller thread
     * @param prefix          thread name prefix, will add counter start from 1
     * @param maxTaskCount    max parallel
     * @param schedulerParallelism parallelism of carrier thread pool
     * @param maxSchedulerPoolSize max pool size of carrier thread pool
     * @param minSchedulerRunnable min runnable size of carrier thread pool
     * @param forceRoot       force to run in root tenant, otherwise tenant container will inherited from caller thread
     * @param rejectHandler   reject policy, default is reject if set null
     */
    private TenantVirtualThreadExecutorService(TenantContainer tenantContainer, String prefix, int maxTaskCount,
        int schedulerParallelism, int maxSchedulerPoolSize, int minSchedulerRunnable,
        boolean forceRoot,
        RejectedExecutionHandler rejectHandler) {

        if (tenantContainer != null) {
            assert TenantGlobals.isTenantEnabled();
            if (forceRoot) {
                throw new IllegalArgumentException("cant not set forceRoot=true when tenantContainer is not null");
            }
        }
        this.maxTaskCount = maxTaskCount;
        this.semaphore = new Semaphore(maxTaskCount);
        if (forceRoot) {
            tenantContainer = null;
        } else if (tenantContainer == null && TenantGlobals.isTenantEnabled()) {
            tenantContainer = TenantContainer.currentPoolInheritedTenantContainer(this);
        }
        this.threadContainer = InnerThreadContainer.create(tenantContainer,
            new TenantVirtualThreadContainer.SchedulerPoolConfig(schedulerParallelism, maxSchedulerPoolSize, minSchedulerRunnable), this);
        try {
            Thread.Builder.OfVirtual ofVirtual = (Thread.Builder.OfVirtual) ofVirtualConstructor.newInstance(this.threadContainer.scheduler());
            this.factory = new TenantThreadFactory(ofVirtual.name(prefix, 1).factory(), tenantContainer);
        } catch(Exception ex) {
            throw new RuntimeException("init tenant virtual ThreadFactory failed", ex);
        }
        this.rejectHandler = rejectHandler;
    }

    @Override
    public void execute(Runnable command) {

        if (state >= SHUTDOWN) {
            handleReject(command, () -> new RejectedExecutionException("executor service had been shutdown"));
            return;
        }
        if (semaphore.tryAcquire()) {
            try {
                // task per virtual thread
                Thread thread = factory.newThread(() -> {
                    command.run();
                });
                JLA.start(thread, threadContainer);
            } catch (Throwable t) {
                semaphore.release();
                handleReject(command, () -> new RejectedExecutionException("start virtual thread failed", t));
            }
        } else {
            handleReject(command,
                () -> new RejectedExecutionException("virtual thread count exceeded, max size is " + maxTaskCount));
        }
    }

    private void onTaskTerminated() {

        semaphore.release();
        if (state == SHUTDOWN) {
            tryTerminate();
        }
    }

    private void handleReject(Runnable run, Supplier<RejectedExecutionException> exSupplier) {

        if (rejectHandler != null) {
            rejectHandler.rejectedExecution(run, this);
        } else {
            throw exSupplier.get();
        }
    }

    @Override
    public void shutdown() {

        if (!isShutdown()) {
            tryShutdownAndTerminate(false);
        }
    }

    @Override
    public List<Runnable> shutdownNow() {

        if (!isTerminated()) {
            tryShutdownAndTerminate(true);
        }
        return List.of();
    }

    @Override
    public boolean isShutdown() {

        return state >= SHUTDOWN;
    }

    @Override
    public boolean isTerminated() {

        return state >= TERMINATED;
    }

    @Override
    public void close() {

        awaitTermination();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {

        if (isTerminated()) {
            return true;
        } else {
            return terminationSignal.await(timeout, unit);
        }
    }

    /**
     * Waits for executor to terminate.
     * refer to java.util.ThreadPerTaskExecutor
     */
    private void awaitTermination() {

        boolean terminated = isTerminated();
        if (!terminated) {
            tryShutdownAndTerminate(false);
            boolean interrupted = false;
            while (!terminated) {
                try {
                    terminated = awaitTermination(1L, TimeUnit.DAYS);
                } catch (InterruptedException e) {
                    if (!interrupted) {
                        tryShutdownAndTerminate(true);
                        interrupted = true;
                    }
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Attempts to terminate if already shutdown. If this method terminates the
     * executor then it signals any threads that are waiting for termination.
     * refer to java.util.ThreadPerTaskExecutor
     */
    private void tryTerminate() {

        assert state >= SHUTDOWN;

        if (threadContainer.threadCount() == 0 && STATE.compareAndSet(this, SHUTDOWN, TERMINATED)) {

            // signal waiters
            terminationSignal.countDown();

            // remove from registry
            threadContainer.shutdown();
        }
    }

    /**
     * Attempts to shutdown and terminate the executor.
     * If interruptThreads is true then all running threads are interrupted.
     * refer to java.util.ThreadPerTaskExecutor
     */
    private void tryShutdownAndTerminate(boolean interruptThreads) {

        if (STATE.compareAndSet(this, RUNNING, SHUTDOWN)) {
            tryTerminate();
        }
        if (interruptThreads) {
            threadContainer.threads().forEach(Thread::interrupt);
        }
    }

    public interface RejectedExecutionHandler {

        /**
         * Method that may be invoked when cannot accept a
         * task.  This may occur when no more threads or queue slots are
         * available because their bounds would be exceeded, or upon
         * shutdown of the Executor.
         *
         * <p>In the absence of other alternatives, the method may throw
         * an unchecked {@link RejectedExecutionException}, which will be
         * propagated to the caller of {@code execute}.
         *
         * @param r        the runnable task requested to be executed
         * @param executor the executor attempting to execute this task
         * @throws RejectedExecutionException if there is no remedy
         */
        void rejectedExecution(Runnable r, ExecutorService executor);
    }

    private static class InnerThreadContainer extends TenantVirtualThreadContainer {

        private TenantVirtualThreadExecutorService executorService;

        private InnerThreadContainer(TenantContainer tenantContainer, TenantVirtualThreadContainer.SchedulerPoolConfig cfg,
            TenantVirtualThreadExecutorService executorService) {

            super(tenantContainer, cfg);
            this.executorService = executorService;
        }

        static TenantVirtualThreadContainer create(TenantContainer tenantContainer, TenantVirtualThreadContainer.SchedulerPoolConfig cfg,
            TenantVirtualThreadExecutorService executorService) {

            TenantVirtualThreadContainer container = new InnerThreadContainer(tenantContainer, cfg, executorService);
            TenantVirtualThreadContainer.register(container);
            return container;
        }

        @Override
        public void onExit(Thread thread) {

            super.onExit(thread);
            executorService.onTaskTerminated();
        }
    }

    private static class TenantThreadFactory implements ThreadFactory {

        private TenantContainer tenantContainer;
        private ThreadFactory factory;

        public TenantThreadFactory(ThreadFactory factory, TenantContainer tenantContainer) {

            this.factory = factory;
            this.tenantContainer = tenantContainer;
        }

        @Override
        public Thread newThread(Runnable r) {

            Thread[] threads = new Thread[1];
            if (tenantContainer == null) {
                if (TenantGlobals.isTenantEnabled()) {
                    // force virtual thread inherit root tenant
                    TenantContainer.primitiveRunInRoot(() -> {
                        threads[0] = factory.newThread(r);
                        TenantContainer.setInheritedTenantContainer(threads[0], null);
                    });
                } else {
                    threads[0] = factory.newThread(r);
                }
            } else {
                assert TenantGlobals.isTenantEnabled();

                TenantContainer.primitiveRunInRoot(() -> {
                    try {
                        tenantContainer.run(() -> {
                            threads[0] = factory.newThread(r);
                            TenantContainer.setInheritedTenantContainer(threads[0], tenantContainer);
                        });
                    } catch(TenantException ex) {
                        throw new RuntimeException(ex);
                    }

                });
            }
            return threads[0];
        }
    }
}
