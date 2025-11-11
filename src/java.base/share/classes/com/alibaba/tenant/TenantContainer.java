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

import java.lang.Thread;
import java.lang.ref.WeakReference;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Supplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import jdk.internal.access.JavaLangAccess;
import jdk.internal.access.JavaLangTenantAccess;
import jdk.internal.access.SharedSecrets;
import jdk.internal.access.TenantAccess;
import jdk.internal.misc.InnocuousThread;

import sun.security.action.GetBooleanAction;
import sun.security.action.GetLongAction;

public class TenantContainer {

    /* Make sure registerNatives is the first thing <clinit> does. */
    private static native void registerNatives();

    private static TenantResourceAccounting tenantResourceAccounting;

    static {
        registerNatives();

        @SuppressWarnings("removal")
        Boolean debugTenantShutdown = AccessController.doPrivileged(
            new GetBooleanAction("com.alibaba.tenant.DebugTenantShutdown"));
        DEBUG_SHUTDOWN = debugTenantShutdown;
        @SuppressWarnings("removal")
        Long killThreadInterval = AccessController.doPrivileged(
            new GetLongAction("com.alibaba.tenant.KillThreadInterval", 20L));
        KILL_THREAD_INTERVAL = killThreadInterval;
        @SuppressWarnings("removal")
        Boolean stopShutdownWhenTimeout = AccessController.doPrivileged(
            new GetBooleanAction("com.alibaba.tenant.StopShutdownWhenTimeout"));
        STOP_SHUTDOWN_WHEN_TIMEOUT = stopShutdownWhenTimeout;
        @SuppressWarnings("removal")
        Long shutdownSTWSoftLimit = AccessController.doPrivileged(
            new GetLongAction("com.alibaba.tenant.ShutdownSTWSoftLimit", -1L));
        SHUTDOWN_SOFT_LIMIT = shutdownSTWSoftLimit;
        @SuppressWarnings("removal")
        Long printStacksOnTimeoutDelay= AccessController.doPrivileged(
            new GetLongAction("com.alibaba.tenant.PrintStacksOnTimeoutDelay", -1L));
        PRINT_STACKS_ON_TIMEOUT_DELAY = printStacksOnTimeoutDelay;
    }

    public record NewThreadTenantPredicateTuple(Thread currentThread, Thread newThread, TenantContainer currentTenantContainer) {
    }

    public record NewPoolTenantPredicateTuple(Thread currentThread, AbstractExecutorService executorService, TenantContainer currentTenantContainer) {
    }

    public record PoolThreadTenantPredicateTuple(Thread newThread, AbstractExecutorService executorService,
        TenantContainer currentTenantContainer, TenantContainer poolInheritedTenantContainer) {
    }

	/**
	 * the child thread inherited tenant container from current creating thread
	 * @param newThread the thread to be created or null if it is a thread pool
	 * @return null if marked as root
	 */
	public static TenantContainer currentInheritedTenantContainer(Thread newThread) {
		TenantContainer tenantContainer = TenantContainer.current();
		if (newThread instanceof InnocuousThread) {
			//system thread, keep as it is
			return null;
		}
		if (shouldNewThreadInherit(newThread, tenantContainer)) {
			return tenantContainer;
		}
		return null;
	}

    /**
	 * the thread pool's inherited tenant container from current creating thread
	 * @return null if marked as root
	 */
    public static TenantContainer currentPoolInheritedTenantContainer(AbstractExecutorService abstractExecutorService) {
		TenantContainer tenantContainer = TenantContainer.current();
		if (shouldNewPoolInherit(Thread.currentThread(), abstractExecutorService, tenantContainer)) {
			return tenantContainer;
		}
		return null;
	}

	/**
	 * set the executor's tenant as root
	 *
	 * @param executor {@see java.util.concurrent.AbstractExecutorService}
	 */
	public static void setThreadPoolAsRootContainer(Executor executor) {
		if (!TenantGlobals.isTenantEnabled()) {
			return;
		}
		if (executor instanceof AbstractExecutorService) {
            SharedSecrets.getJavaUtilConcurrentAESTenantAccess().setInheritedTenantContainer((AbstractExecutorService)executor, null);
        }
	}

    private static TenantContainer getAttachedTenantContainer(Thread t) {
        if (t != null) {
            return JLTA.getAttachedTenantContainer(t);
        }
        return null;
    }

    private static void setAttachedTenantContainer(Thread t, TenantContainer container) {
        if (t != null) {
            JLTA.setAttachedTenantContainer(t, container);
        }
    }

    static boolean getTenantInheritance(Thread t) {
        if (t != null) {
            return JLTA.getTenantInheritance(t);
        }
        return false;
    }

	public static void setInheritedTenantContainer(Thread t, TenantContainer inheritedContainer) {
        if (t != null) {
            JLTA.setInheritedTenantContainer(t, inheritedContainer);
        }
	}

	public static void setPoolThreadInheritedTenantContainer(Thread t, AbstractExecutorService executor, TenantContainer poolInheritedContainer) {
        if (t != null) {
            if (t instanceof InnocuousThread) {
                //system thread, keep as it is
                JLTA.setInheritedTenantContainer(t, null);
            } else if (executor instanceof TenantVirtualThreadExecutorService
                || executor instanceof TenantVirtualThreadContainer.TenantForkJoinPool) {
                // jdk inner tenant virtual executor and it's pool is tenantContainer forced when thread created
                return;
            } else if (poolThreadTenantInheritancePredicate == null
                // check if created pool thread should inherit AbstractExecutorService's inheritedTenantContainer
                || poolThreadTenantInheritancePredicate.test(new PoolThreadTenantPredicateTuple(t, executor, TenantContainer.current(), poolInheritedContainer))) {
                JLTA.setInheritedTenantContainer(t, poolInheritedContainer);
            }
        }
	}

    private static final JavaLangAccess JLA = SharedSecrets.getJavaLangAccess();
    private static final JavaLangTenantAccess JLTA = SharedSecrets.getJavaLangTenantAccess();

    /*
     * Used to generate the tenant id.
     */
    private static long tenantID = 0;

    private static synchronized long nextTenantID() {
        return tenantID++;
    }

    /*
     * Used to hold the mapping from tenant id to TenantContainer object for all
     * tenants
     */
    private static Map<Long, TenantContainer> tenantContainerMap = null;

    // value of property "com.alibaba.tenant.DebugTenantShutdown"
    private static final boolean DEBUG_SHUTDOWN;

    // value of property "com.alibaba.tenant.KillThreadInterval"
    private static final long KILL_THREAD_INTERVAL;

    // value of property "com.alibaba.tenant.StopShutdownWhenTimeout"
    private static final boolean STOP_SHUTDOWN_WHEN_TIMEOUT;

    // value of property "com.alibaba.tenant.ShutdownSTWSoftLimit"
    private static final long SHUTDOWN_SOFT_LIMIT;

    // value of property "com.alibaba.tenant.PrintStacksOnTimeoutDelay"
    private static final long PRINT_STACKS_ON_TIMEOUT_DELAY;

    /*
     * Newly created threads which attach to this tenant container, read write need destroy lock
     */
    private List<WeakReference<Thread>> spawnedThreads = Collections.synchronizedList(new ArrayList<>());

    /*
     * all virtual threads managed by ThreadContainers, here readonly and read is thread safe, can read without destroy lock
     */
    private Set<Thread> virtualThreads = ConcurrentHashMap.newKeySet();

    /*
     * all carrier threads managed by ThreadContainers, here readonly and read is thread safe, can read without destroy lock
     */
    private Set<Thread> carrierThreads = ConcurrentHashMap.newKeySet();

    private TenantVirtualThreadContainer defaultVirtualThreadContainer;

    /*
     * tenant state
     */
    private volatile TenantState state;

    /*
     * tenant id
     */
    private long tenantId;

    /*
     * tenant name
     */
    private String name;
    
    /*
     * tenant jgroup
     */
    private volatile JGroup jgroup;

    /*
     * the parent tenant contianer.
     */
    private TenantContainer parent;

    /*
     * the configuration of this tenant container
     */
    private TenantConfiguration configuration = null;

    // Used to synchronize between destroy() and runThread()
    private ReentrantReadWriteLock destroyLock = new ReentrantReadWriteLock();

    // Timestamp when destroy() starts
    private long destroyBeginTimestamp = -1;

    /*
     * If stacktraces of die-hard threads have been printed after TenantContainer.destroy() timeout.
     * To let thread-dump happen only once.
     */
    private boolean stacksPrintedOnTimeout = false;

	/**
	 * predicate whether the new Thread() should inherit current tenant
	 */
	private static Predicate<NewThreadTenantPredicateTuple> newThreadTenantInheritancePredicate;
    /**
	 * predicate whether the new ThreadPoolExecutor()/ new ForkJoinPool() should inherit current tenant
	 */
	private static Predicate<NewPoolTenantPredicateTuple> newPoolTenantInheritancePredicate;
    /**
	 * predicate whether the worker thread of thread pool should inherit pool's tenant
	 */
	private static Predicate<PoolThreadTenantPredicateTuple> poolThreadTenantInheritancePredicate;

    /**
     * record accumulated cpu time for exited tenant threads
     */
    private AtomicLong exitedThreadAccumulatedCpuTime = new AtomicLong(0);

    /*
     * Get the tenant container attached with current thread.
     * @return the tenant of the current thread
     */
    private static TenantContainer current0() {
       return getAttachedTenantContainer(Thread.currentThread());
    }

    /*
     * Attach the current thread into the receiver.
     * @return 0 if successful
     */
    private void attach0(Thread thread) {
        setAttachedTenantContainer(thread, this);
    }

    /*
     * Detach current thread from this tenant
     *
     * @return 0 if successful
     */
    private void detach0(Thread thread) {
        setAttachedTenantContainer(thread, null);
    }

    /**
     * Gets the TenantContainer attached to the current thread.
     *
     * @return The TenantContainer attached to the current thread, null if no
     * TenantContainer is attached to the current thread.
     */
    public static TenantContainer current() {
        checkIfTenantIsEnabled();
        return current0();
    }

    /**
     * Gets the cpu time consumed by this tenant
     * @return the cpu time used by this tenant, 0 if tenant cpu throttling or accounting feature is disabled.
     */
    public long getProcessCpuTime() {
        if (!TenantGlobals.isCpuAccountingEnabled()) {
            throw new IllegalStateException("-XX:+TenantCpuAccounting is not enabled");
        }
        long cpuTime = exitedThreadAccumulatedCpuTime.get();
        List<Long> ids;
        // here should acquire readlock, otherwise may throw ConcurrentModificationException
        if (destroyLock.readLock().tryLock()) {
            ids = new ArrayList<>(spawnedThreads.size());
            try {
                spawnedThreads.forEach((ref) -> {
                    Thread t = ref.get();
                    if(t == null || !t.isAlive() || t.getState() == Thread.State.TERMINATED) {
                        return;
                    }
                    ids.add(t.threadId());
                });
            } finally {
                destroyLock.readLock().unlock();
            }
        } else {
            ids = Collections.emptyList();
        }

        if(ids.size() > 0) {
            long[] idArray = new long[ids.size()];
            for(int i = 0; i < ids.size(); i++) {
                idArray[i] = ids.get(i);
            }
            long[] resultArray = getTenantThreadsCpuTime(idArray);
            for(long t: resultArray) {
                if(t > 0 ) {
                    cpuTime += t;
                }
            }
        }
        return cpuTime;
    }

    /**
     * return active virtual thread belong to current tenant container
     */
    public long getActiveVirtualThreadCount() {
        long count = 0;
        for (Thread t : virtualThreads) {
            if (t.isAlive() && t.getState() != Thread.State.TERMINATED) {
                count++;
            }
        }
        return count;
    }

    /**
     * Try to kill all {@code threads}
     * @return True if all {@code threads} killed successfully, otherwise false
     */
    public boolean destroy() {
        if (TenantContainer.current() != null) {
            throw new RuntimeException("Should only call destroy() in ROOT tenant");
        }

        destroyBeginTimestamp = System.currentTimeMillis();

        destroyLock.writeLock().lock();
        try {
            if (state != TenantState.STOPPING && state != TenantState.DEAD) {
                setState(TenantState.STOPPING);

                tenantContainerMap.remove(getTenantId());

                // Kill all threads
                if (TenantGlobals.isThreadStopEnabled()) {
                    if (killAllThreads(virtualThreads, carrierThreads, spawnedThreads, true)) {
                        cleanUp();
                        return true;
                    }
                } else {
                    cleanUp();
                    return true;
                }
            } else {
                return true;
            }
        } catch (Throwable t) {
            System.err.println("Exception from TenantContainer.destroy()");
            t.printStackTrace();
        } finally {
            destroyLock.writeLock().unlock();
        }

        debug_shutdown(
            "TenantContainer.destroy() costs " + (System.currentTimeMillis() - destroyBeginTimestamp) + "ms");
        return false;
    }

    /*
     * Try to kill all {@code threads}
     * @param virtualThreads            List of virtualThreads to be killed, read only here
     * @param carrierThreads            List of carrierThreads to be killed, read only here
     * @param threads                   List of threads to be killed
     * @param asyncIfExceedsSoftLimit   Create a new WatchDog thread and finish remaining work if exceed
     *                                  {@code com.alibaba.tenant.ShutdownSTWSoftLimit} and value of
     *                                  {@code com.alibaba.tenant.ShutdownSTWSoftLimit} is greater than zero.
     * @return                          True if all {@code threads} killed successfully, otherwise false
     *
     */
    private boolean killAllThreads(Set<Thread> virtualThreads, Set<Thread> carrierThreads,
        List<WeakReference<Thread>> threads,
        boolean asyncIfExceedsSoftLimit) {

        Long tries = 0L;    // number of calls to prepareForDestroy0()
        Long timeSTW = 0L;    // approximate total stop-the-world time, in ms
        Long maxSTW = -1L;   // approximate maximum stop-the-world time, in ms
        Long lastTime = 0L;
        Long timeBegin = System.currentTimeMillis();
        Boolean result = true;
        Integer oldPriority = Thread.currentThread().getPriority();

        Thread.currentThread().setPriority(Thread.MAX_PRIORITY);

        // Increase priority of dying threads to give them higher possibility to handle exceptions
        if (!asyncIfExceedsSoftLimit) {
            for (WeakReference<Thread> ref : threads) {
                Thread t = ref.get();
                if (t != null) {
                    t.setPriority(Thread.MAX_PRIORITY - 1);
                }
            }

            for (Thread t : carrierThreads) {
                t.setPriority(Thread.MAX_PRIORITY - 1);
            }
        }

        while (!threads.isEmpty() || !virtualThreads.isEmpty() || !carrierThreads.isEmpty()) {
            long now = System.currentTimeMillis();
            boolean forcefully = (now - timeBegin
                > KILL_THREAD_INTERVAL * 10); // if should try to kill thread forcefully
            // virtualThreads and carrierThreads is managed by ThreadContainer, here do not need to purge
            purgeDeadThreads(threads);

            if (lastTime == 0 || now - lastTime >= KILL_THREAD_INTERVAL) {
                // only enter safepoint when there is unmarked java threads
                boolean anyUnmarkedThreads = false;
                for (WeakReference<Thread> ref : threads) {
                    Thread t = ref.get();
                    if (t != null && !hasTenantDeathException(t)) {
                        anyUnmarkedThreads = true;
                        break;
                    }
                }

                for (Thread t : carrierThreads) {
                    if (!hasTenantDeathException(t)) {
                        anyUnmarkedThreads = true;
                        break;
                    }
                }

                if (anyUnmarkedThreads) {
                    now = System.currentTimeMillis();

                    // prepareForDestory0 will submit a VM operation and wait for it to complete
                    if(virtualThreads.isEmpty()) {
                        // kill platform threads
                        prepareForDestroy0(false, true);
                    } else {
                        // only kill virtual threads
                        prepareForDestroy0(true, true);
                    }

                    long curSTW = System.currentTimeMillis() - now;
                    if (curSTW > maxSTW) {
                        maxSTW = curSTW;
                    }
                    timeSTW += curSTW;
                    ++tries;

                    lastTime = System.currentTimeMillis();
                }

                // do waking-up at certain interval to avoid making target thread to process signals all the time
                if (forcefully) {
                    purgeDeadThreads(threads);
                    for (WeakReference<Thread> ref : threads) {
                        Thread t = ref.get();
                        if (t != null) {
                            wakeUpTenantThread(t);
                        }
                    }
                    for (Thread t: carrierThreads) {
                        wakeUpTenantThread(t);
                    }
                }
            }

            purgeDeadThreads(threads);
            // when killing vthread, interrupt pthread may cause carrier thread exit, vthread schedule slow
            if (virtualThreads.isEmpty()) {
                interruptThreads(carrierThreads, forcefully);
                interruptThreads(threads, forcefully);
            }
            interruptThreads(virtualThreads, forcefully);
            // Print stacktraces of die-hard threads
            if (!stacksPrintedOnTimeout
                && (!threads.isEmpty() || !virtualThreads.isEmpty() || !carrierThreads.isEmpty())
                && PRINT_STACKS_ON_TIMEOUT_DELAY > 0
                && (System.currentTimeMillis() - destroyBeginTimestamp) > PRINT_STACKS_ON_TIMEOUT_DELAY) {
                // dump both threads and carrier threads
                Set<Thread> threadsToDump = new HashSet<>(threads.size() + carrierThreads.size());
                threadsToDump.addAll(threads.stream().map(r -> r.get()).collect(Collectors.toList()));
                threadsToDump.addAll(carrierThreads);
                Thread[] thrdArray = threadsToDump.toArray(new Thread[threadsToDump.size()]);
                dumpThreads(thrdArray);
                stacksPrintedOnTimeout = true;
            }

            // if cannot kill all threads within time of 'com.alibaba.tenant.ShutdownSTWSoftLimit',
            // start a daemon thread to watch and kill them
            if (asyncIfExceedsSoftLimit && SHUTDOWN_SOFT_LIMIT > 0
                && (!threads.isEmpty() || !virtualThreads.isEmpty() || !carrierThreads.isEmpty())
                && (timeSTW > SHUTDOWN_SOFT_LIMIT
                || (System.currentTimeMillis() - timeBegin) > (SHUTDOWN_SOFT_LIMIT << 4))) {
                // if stop shutdow with timeout, return failed and will not try with WatchDogThread again
                if (!STOP_SHUTDOWN_WHEN_TIMEOUT) {
                    // spawn a watch dog thread to take care of the remaining threads
                    WatchDogThread watchDog = new WatchDogThread(this, virtualThreads, carrierThreads, threads);
                    watchDog.start();
                }

                result = false;
                break;
            }
        }

        Thread.currentThread().setPriority(oldPriority);

        if(result == true) {
            setState(TenantState.DEAD);
        }

        if (DEBUG_SHUTDOWN) {
            debug_shutdown("TenantContainer.killThreads() costs " + (System.currentTimeMillis() - timeBegin)
                + "ms, paused " + timeSTW + "ms, tried " + tries + " times, max paused " + maxSTW + "ms, "
                + (result ? "successfully!" : "failed!"));
        }

        return result;
    }

    /*
     * Last resort to kill remaining threads when {@code prepareForDestroy0} exceeds soft STW time limit,
     * which is defined by property {@code com.alibaba.tenant.ShutdownSTWSoftLimit}
     */
    private class WatchDogThread extends Thread {

        private TenantContainer tenant;
        private Set<Thread> virtualThreads;
        private Set<Thread> carrierThreads;
        private List<WeakReference<Thread>> threads;

        WatchDogThread(TenantContainer tenant,
            Set<Thread> virtualThreads, Set<Thread> carrierThreads, List<WeakReference<Thread>> threads) {

            this.tenant = tenant;
            this.virtualThreads = virtualThreads;
            this.carrierThreads = carrierThreads;
            this.threads = threads;
            setDaemon(true);
            setPriority(MAX_PRIORITY);
            setName("WatchDog-" + tenant.getName());

            if (DEBUG_SHUTDOWN) {
                if (threads.size() > 0) {
                    debug_shutdown("Failed to kill all threads within soft limit, remaining "
                        + threads.size() + " threads are:");
                    for (WeakReference<Thread> ref : threads) {
                        Thread t = ref.get();
                        if (t != null) {
                            debug_shutdown("platform thread:" +t.toString() + ",id=" + t.threadId() + ",status=" + t.getState());
                        }
                    }
                }
                if (virtualThreads.size() > 0) {
                    debug_shutdown("Failed to kill all threads within soft limit, remaining "
                        + virtualThreads.size() + " virtual threads are:");
                    for (Thread t : virtualThreads) {
                        if (t != null) {
                            debug_shutdown("virtual thread:" +t.toString() + ",id=" + t.threadId() + ",status=" + t.getState());
                        }
                    }
                }
                debug_shutdown("Spawning watch dog thread" + this);
            }
        }

        @Override
        public void run() {
            long timeStart = System.currentTimeMillis();
            int remainingCount = threads.size();
            destroyLock.writeLock().lock();
            try {
                killAllThreads(virtualThreads, carrierThreads, threads, false);
                debug_shutdown("WatchDogThread costs " + (System.currentTimeMillis() - timeStart)
                    + "ms to kill remaining " + remainingCount + " threads");
                tenant.cleanUp();
            } finally {
                destroyLock.writeLock().unlock();
            }
        }
    }

    // clean up 'dead' threads from thread list
    private static void purgeDeadThreads(List<WeakReference<Thread>> threads) {
        threads.removeIf(ref -> {
            Thread t = ref.get();
            return t == null || !t.isAlive() || t.getState() == Thread.State.TERMINATED;
        });
    }

    /**
     * interrupt thread call {@link Thread.#interrupt()} and it will unpark thread
     * @param force     True if caller wants to call {@link Thread.#interrupt()} unconditionally, otherwise will only
     *                  do that when {@link Thread.#getState()} is in {@link Thread.State.#WAITING}
     *                  or {@link Thread.State.#TIMED_WAITING} status.
     */
    private static void interruptThreads(Set<Thread> threads, boolean force) {
        for (Thread t : threads) {
            if (force || t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING) {
                try {
                    t.interrupt();
                } catch (Throwable ignore) {
                    if (DEBUG_SHUTDOWN) {
                        debug_shutdown(t.isVirtual() ? "Exception from VirtualThread.interrupt()" : "Exception from Thread.interrupt()");
                        ignore.printStackTrace();
                    }
                }
            }
        }
    }

    /*
     * Call {@link Thread.#interrupt()} to wake up all threads in {@code threads}
     * @param force     True if caller wants to call {@link Thread.#interrupt()} unconditionally, otherwise will only
     *                  do that when {@link Thread.#getState()} is in {@link Thread.State.#WAITING}
     *                  or {@link Thread.State.#TIMED_WAITING} status.
     */
    private static void interruptThreads(List<WeakReference<Thread>> threads, boolean force) {
        for (WeakReference<Thread> ref : threads) {
            Thread t = ref.get();
            if (t != null &&
                (force || t.getState() == Thread.State.WAITING || t.getState() == Thread.State.TIMED_WAITING)) {
                try {
                    t.interrupt();
                } catch (Throwable ignore) {
                    if (DEBUG_SHUTDOWN) {
                        debug_shutdown("Exception from Thread.interrupt()");
                        ignore.printStackTrace();
                    }
                }
            }
        }
    }


    private TenantContainer(Long tenantId, String name, TenantConfiguration configuration) {
        this.tenantId = tenantId;
        this.name = name;
        this.configuration = configuration;
        defaultVirtualThreadContainer = TenantVirtualThreadContainer.create(this);
    }

    TenantConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * @return the tenant state
     */
    public TenantState getState() {
        return state;
    }

    /*
     * Set the tenant state
     * @param state used to set
     */
    private void setState(TenantState state) {
        this.state = state;
    }

    public boolean isRunnable() {
        return state == TenantState.STARTING || state == TenantState.RUNNING;
    }

    /**
     * Returns the tenant' id
     *
     * @return tenant id
     */
    public long getTenantId() {
        return tenantId;
    }

    /**
     * Returns this tenant's name.
     *
     * @return this tenant's name.
     */
    public String getName() {

        return name;
    }

    public ForkJoinPool defaultVirtualThreadScheduler() {
        return defaultVirtualThreadContainer.scheduler();
    }

    public static boolean isTenantScheduler(Executor scheduler) {
        return scheduler instanceof TenantVirtualThreadContainer.TenantForkJoinPool;
    }

    public void onVThreadStart(Thread thread) {
        if (destroyLock.readLock().tryLock()) {
            try {
                if (this.isRunnable()) {
                    attach0(thread);
                    this.virtualThreads.add(thread);
                } else {
                    throw new RejectedExecutionException("TenantContainer is dead, virtual thread start is rejected");
                }
            } finally {
                destroyLock.readLock().unlock();
            }
        } else {
            throw new RejectedExecutionException("TenantContainer is shutdown, virtual thread start is rejected");
        }
    }

    public void onVThreadExit(Thread thread) {
        detach0(thread);
        this.virtualThreads.remove(thread);
    }

    public void onCarrierThreadStart(Thread thread) {
        this.carrierThreads.add(thread);
    }

    public void onCarrierThreadExit(Thread thread) {
        this.carrierThreads.remove(thread);
    }

    /**
     * Get the tenant container by id
     *
     * @param id tenant id.
     * @return the tenant specified by id, null if the id doesn't exist.
     */
    public static TenantContainer getTenantContainerById(long id) {
        checkIfTenantIsEnabled();
        return tenantContainerMap.get(id);
    }

    /**
     * Create tenant container by the configuration
     *
     * @param configuration used to create tenant
     * @return the tenant container
     */
    public static TenantContainer create(TenantConfiguration configuration) {
        return create(TenantContainer.current(), configuration);
    }

    /**
     * Create tenant container by the configuration
     *
     * @param parent        parent tenant container
     * @param configuration used to create tenant
     * @return the tenant container
     */
    public static TenantContainer create(TenantContainer parent, TenantConfiguration configuration) {
        checkIfTenantIsEnabled();
        //parameter checking
        if (null == configuration) {
            throw new UnsupportedOperationException("Failed to create tenant, illegal arguments: configuration is null");
        }

        long id = nextTenantID();
        String name = "Tenant-" + id; // default name for tenant
        return create(parent, id, name, configuration);
    }

    /**
     * Create tenant container by the name and configuration
     *
     * @param name          the tenant name
     * @param configuration used to create tenant
     * @return the tenant container
     */
    public static TenantContainer create(String name, TenantConfiguration configuration) {
        return create(TenantContainer.current(), name, configuration);
    }

    /**
     * Create tenant container by the name and configuration
     *
     * @param parent        parent tenant container
     * @param name          the tenant name
     * @param configuration used to create tenant
     * @return the tenant container
     */
    public static TenantContainer create(TenantContainer parent, String name, TenantConfiguration configuration) {
        checkIfTenantIsEnabled();
        //parameter checking
        if (null == name) {
            throw new UnsupportedOperationException("Failed to create tenant, illegal arguments: name is null");
        }
        if (null == configuration) {
            throw new IllegalArgumentException("Failed to create tenant, illegal arguments: configuration is null");
        }
        return create(parent, nextTenantID(), name, configuration);
    }

    private static TenantContainer create(TenantContainer parent, Long id, String name,
        TenantConfiguration configuration) {
        TenantContainer tc = new TenantContainer(id, name, configuration);
        tc.setState(TenantState.STARTING);
        tc.parent = parent;

        // cgroup for tenant
        if (TenantGlobals.isCpuThrottlingEnabled()) {
            tc.jgroup = new JGroup(tc);
        }

        tenantContainerMap.put(tc.getTenantId(), tc);
        return tc;
    }

    TenantContainer getParent() {
        return parent;
    }

    JGroup getJGroup() {
        return jgroup;
    }

    public void startVirtualThread(Thread thread) {
        assert thread.isVirtual();
        JLA.start(thread, this.defaultVirtualThreadContainer);
    }

    /**
     * Gets the tenant id list
     *
     * @return the tenant id list, Collections.emptyList if no tenant exists.
     */
    @SuppressWarnings("unchecked")
    public static List<Long> getAllTenantIds() {
        checkIfTenantIsEnabled();
        if (null == tenantContainerMap) {
            throw new IllegalStateException("TenantContainer class is not initialized !");
        }
        if (tenantContainerMap.size() == 0) {
            return Collections.EMPTY_LIST;
        }

        List<Long> ret = new ArrayList<Long>();
        for (Map.Entry<Long, TenantContainer> entry : tenantContainerMap.entrySet()) {
            ret.add(entry.getValue().getTenantId());
        }
        return ret;
    }

    private static native void maskTenantShutdown0(Thread thread);

    private static native void unmaskTenantShutdown0(Thread thread);

    /**
     * Hide current thread from TenantThreadStop request.
     * Should be used with {@code unmaskTenantShutdown} in pairs to mark a code snippet
     * to be immune to {@code TenantContainer.destroy}.
     *
     * A common pattern to use these two APIs would be
     * <pre>
     *     tenant.maskTenantShutdown();
     *     try {
     *         // Uninterruptible operation
     *         ... ...
     *     } finally {
     *         tenant.unmaskTenantShutdown();
     *     }
     * </pre>
     */
    public static void maskTenantShutdown() {

        if (TenantGlobals.isThreadStopEnabled()) {
            maskTenantShutdown0(Thread.currentThread());
        }
    }

    /**
     * Restore current thread from {@code maskTenantShutdown}.
     * If {@code TenantContainer.destroy()} happens between
     * {@code maskTenantShutdown} and {@code unmaskTenantShutdown},
     * the "masked" thread will start external exit protocol
     * immediately after returning from {@code unmaskTenantShutdown}.
     */
    public static void unmaskTenantShutdown() {
        if (TenantGlobals.isThreadStopEnabled()) {
            unmaskTenantShutdown0(Thread.currentThread());
        }
    }

    /**
     * Runs {@code Supplier.get} in the root tenant.
     * vitural thread can not operate attach0 and detach0
     * when other vthread exit, carrier thread my attach0 to tenant, but current vthread need in root
     * so here will not do attach and detach, just set threadlocal to force inherit root
     * @param supplier target used to call
     * @return the result of {@code Supplier.get}
     */
    public static <T> T primitiveRunInRoot(Supplier<T> supplier) {
        TenantContainer tenant = TenantContainer.current();
        if(tenant != null) {
            tenant.detach0(Thread.currentThread());
        }
        try {
            maskTenantShutdown();
            try {
                return supplier.get();
            } finally {
                unmaskTenantShutdown();
            }
        } finally {
            if(tenant != null) {
                tenant.attach0(Thread.currentThread());
            }
        }

    }

    /**
     * Runs a block of code in the root tenant.
     * vitural thread can not operate attach0 and detach0
     * when other vthread exit, carrier thread my attach0 to tenant, but current vthread need in root
     * so here will not do attach and detach, just set threadlocal to force inherit root
     *
     * @param runnable the code to run
     */
    public static void primitiveRunInRoot(Runnable runnable) {
        TenantContainer tenant = TenantContainer.current();
        if(tenant != null) {
            tenant.detach0(Thread.currentThread());
        }
        try {
            maskTenantShutdown();
            try {
                runnable.run();
            } finally {
                unmaskTenantShutdown();
            }
        } finally {
            if(tenant != null) {
                tenant.attach0(Thread.currentThread());
            }
        }
    }


    /**
     * Runs the code in the target tenant container
     * will not do attach/detach operation
     * @param runnable the code to run
     */
    public void run(final Runnable runnable) throws TenantException {
        // when stopping still can run, but new Thread is forbidden, for virtual thread exit
        if (state == TenantState.DEAD) {
            throw new TenantException("Tenant is dead");
        }

        TenantContainer currentContainer = TenantContainer.current();
        if (currentContainer != null && currentContainer != this) {
            throw new TenantException("must be in root tenant before running into non-root tenant.");
        }

        if(currentContainer == this) {
            runnable.run();
        } else {
            if (jgroup != null) {
                jgroup.attach();
            }
            attach0(Thread.currentThread());
            try {
                runnable.run();
            } finally {
                detach0(Thread.currentThread());
                if (jgroup != null) {
                    jgroup.detach();
                }
            }
        }
    }

    /**
     * Try to modify resource limit of current tenant,
     * for resource whose limit cannot be changed after creation of {@code TenantContainer}, its limit will be ignored.
     * @param config  new TenantConfiguration to
     */
    public void update(TenantConfiguration config) {
        for (ResourceLimit rlimit : config.getAllLimits()) {
            // only save configurations of configurable
            if (rlimit.type().isJGroupResource() && jgroup != null) {
                rlimit.sync(jgroup);
                getConfiguration().setLimit(rlimit.type(), rlimit);
            }
        }
    }

    /*
     * Check if the tenant feature is enabled.
     */
    private static void checkIfTenantIsEnabled() {
        if (!TenantGlobals.isTenantEnabled()) {
            throw new UnsupportedOperationException("The multi-tenant feature is not enabled!");
        }
    }

    // for debugging purpose
    private static void debug_shutdown(String msg) {
        if (DEBUG_SHUTDOWN) {
            System.err.println("[DEBUG] " + msg);
        }
    }

    /*
     * Release all native resources and Java references
     * should be the very last step of {@link #destroy()} operation.
     * If cannot kill all threads in {@link #killAllThreads()}, should do this in {@link WatchDogThread}
     *
     */
    private void cleanUp() {
        if (jgroup != null) {
            jgroup.destroy();
            jgroup = null;
        }

        // clear references
        spawnedThreads.clear();
        virtualThreads.clear();
        carrierThreads.clear();
        parent = null;
        this.defaultVirtualThreadContainer.shutdown();
    }

    /*
     * Invoked by the VM to run a thread (thread run entry) in multi-tenant mode.
     *
     * NOTE: please ensure relevant logic has been fully understood before changing any code
     *
     * @throws TenantException
     */
    private void runThread(final Thread thread) throws TenantException {
        // call from platform thread entry, here should not be virtual threads
        assert !thread.isVirtual();
        if (destroyLock.readLock().tryLock()) {
            // This is the first thread which runs in this tenant container
            if (getState() == TenantState.STARTING) {
                // move the tenant state to RUNNING
                this.setState(TenantState.RUNNING);
            }
            if (state != TenantState.STOPPING && state != TenantState.DEAD) {
                spawnedThreads.add(new WeakReference<>(thread));

                try {
                    attach0(Thread.currentThread());
                    destroyLock.readLock().unlock();
                    thread.run();
                } finally {
                    detach0(Thread.currentThread());
                    // accumulate current thread cpu time to deadThreadCpuProcessTime before thread exit
                    if(TenantGlobals.isCpuAccountingEnabled()) {
                        exitedThreadAccumulatedCpuTime.addAndGet(getTenantThreadCpuTime(Thread.currentThread().threadId()));
                    }
                }
            } else {
                destroyLock.readLock().unlock();
                if (DEBUG_SHUTDOWN) {
                    debug_shutdown(thread + " run() is rejected because tenant is dead");
                }
            }

            // try to clean up once
            if (destroyLock.readLock().tryLock()) {

                if (state != TenantState.STOPPING && state != TenantState.DEAD) {
                    spawnedThreads.removeIf(ref -> ref.get() == null || ref.get() == thread);
                }

                destroyLock.readLock().unlock();
            }
        } else if (thread instanceof TenantVirtualThreadContainer.TenantCarrierThread) {
            // if destroying, carrier thread can still runThread
            // because yield vthread may submit to new carrier thread when unparked
            // here do not need to add carrier thread to spawnedThreads
            try {
                attach0(Thread.currentThread());
                thread.run();
            } finally {
                detach0(Thread.currentThread());
                // accumulate current thread cpu time to deadThreadCpuProcessTime before thread exit
                if(TenantGlobals.isCpuAccountingEnabled()) {
                    exitedThreadAccumulatedCpuTime.addAndGet(getTenantThreadCpuTime(Thread.currentThread().threadId()));
                }
            }
        } else {
            if (DEBUG_SHUTDOWN) {
                debug_shutdown(thread + " run() is rejected because TenantContainer is stopping");
            }
        }
    }

	private static boolean shouldNewThreadInherit(Thread newThread, TenantContainer curTenantContainer) {
		Thread currentThread = Thread.currentThread();
		return getTenantInheritance(currentThread) &&
		        (newThreadTenantInheritancePredicate == null ? true : newThreadTenantInheritancePredicate.test(new NewThreadTenantPredicateTuple(currentThread, newThread, curTenantContainer)));
	}

    private static boolean shouldNewPoolInherit(Thread currentThread, AbstractExecutorService abstractExecutorService, TenantContainer curTenantContainer) {
		assert currentThread != null;
		return getTenantInheritance(currentThread) &&
		        (newPoolTenantInheritancePredicate == null ? true : newPoolTenantInheritancePredicate.test(new NewPoolTenantPredicateTuple(currentThread, abstractExecutorService, curTenantContainer)));
	}

    /*
     * Initialize the TenantContainer class, called after System.initializeSystemClass by VM.
     */
    private static void initializeTenantContainerClass() {
        //Initialize this field after the system is booted.
        tenantContainerMap = Collections.synchronizedMap(new HashMap<>());

        // initialize TenantAccess
        if (SharedSecrets.getTenantAccess() == null) {
            SharedSecrets.setTenantAccess(new TenantAccess() {

                @Override
                public boolean threadInheritance() {

                    return TenantConfiguration.threadInheritance();
                }

                @Override
                public boolean threadInheritance(Thread thread) {

                    return TenantContainer.getTenantInheritance(thread);
                }

                @Override
                public void setTenantDeathToVThread(Thread virtualThread) {

                    TenantContainer.setTenantDeathToVThread(virtualThread);
                }

                @Override
                public Runnable createTenantContinuationEntry(Runnable runnable) {

                    return new TenantContinuationEntry(runnable);
                }

                @Override
                public void setNewThreadTenantInheritancePredicate(Predicate<NewThreadTenantPredicateTuple> predicate) {
                    newThreadTenantInheritancePredicate = predicate;
                }

                @Override
                public void setNewPoolTenantInheritancePredicate(Predicate<NewPoolTenantPredicateTuple> predicate) {
                    newPoolTenantInheritancePredicate = predicate;
                }

                @Override
                public void setPoolThreadTenantInheritancePredicate(Predicate<PoolThreadTenantPredicateTuple> predicate) {
                    poolThreadTenantInheritancePredicate = predicate;
                }
            });
        }

        try {
            // force initialization of TenantConfiguration
            Class.forName("com.alibaba.tenant.TenantConfiguration");
            Class.forName("com.alibaba.tenant.TenantContinuationEntry");
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    private static void initializeTenantCpuAccounting(TenantResourceAccounting accounting) {
        tenantResourceAccounting = accounting;
    }

    private static long getTenantThreadCpuTime(long id) {
        if(tenantResourceAccounting == null) {
            return -1;
        }
        return tenantResourceAccounting.getTenantThreadCpuTime(id);
    }

    private static long[] getTenantThreadsCpuTime(long[] ids) {
        if(tenantResourceAccounting == null) {
            int length = ids.length;
            long[] times = new long[length];
            java.util.Arrays.fill(times, -1);
            return times;
        }
        return tenantResourceAccounting.getTenantThreadsCpuTime(ids);
    }

    /*
     * set tenant death to virtual thread if both virtual thread and it's carrier does not mask shutdown (disable shutdown)
     * @param thread  virtual Thread object to be setted
     */
    private static native void setTenantDeathToVThread(Thread thread);

    /*
     * Prepare native data structures for tenant destroy
     * @param virtualThreadOnly kill virtual thread only if is true, otherwise set tenant death to platform threads
     * @param osWakeUp  Whether or not to use operating system's thread control facilities to wake up thread.
     */
    private native void prepareForDestroy0(boolean virtualThreadOnly, boolean osWakeUp);

    /*
     * Determines if a thread is marked as being killed by {@code TenantContainer.destroy()}
     *
     * @param thread    Thread object to be checked, if {@code null} current thread will be checked
     * @return          True if {@code thread} marked, otherwise false
     */
    private static native boolean hasTenantDeathException(Thread thread);

    /*
     * Tries to wake up a thread using operating system's facilities.
     * The behavior is platform dependant, on Linux a signal (with empty handler)
     * will be sent to interrupt current operation.
     *
     * @param thread    Thread to be waken up
     */
    private static native void wakeUpTenantThread(Thread thread);


    // Dump thread stacks of a group of threads
    private static native void dumpThreads(Thread[] threads);
}
