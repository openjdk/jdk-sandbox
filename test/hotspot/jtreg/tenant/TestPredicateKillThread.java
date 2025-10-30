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

/*
 * @test
 * @summary Test function of killing tenants' threads
 * @library /test/lib
 * @modules java.base/jdk.internal.access
 * @run main/othervm/timeout=100 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                               -XX:+TraceTenantKillThreads -XX:+MultiTenant -XX:+TenantThreadStop -XX:+TenantCpuAccounting
 *                               -Dcom.alibaba.tenant.ShutdownSTWSoftLimit=6000 -Dcom.alibaba.tenant.DebugTenantShutdown=true -Dcom.alibaba.tenant.PrintStacksOnTimeoutDelay=30000
 *                               --add-opens=java.base/jdk.internal.access=ALL-UNNAMED
 *                               --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/java.util.concurrent=ALL-UNNAMED TestPredicateKillThread
 *
 */

import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import com.alibaba.tenant.TenantException;
import com.alibaba.tenant.TenantState;
import java.io.IOException;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinPool.ForkJoinWorkerThreadFactory;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;

import jdk.internal.access.SharedSecrets;
import jdk.test.lib.Asserts;

/**
 * Below testcase tries to test thread killing feature of MultiTenant JDK for following scenarios
 * <ul>A busy loop</ul>
 * <ul><code>Thread.getState() == WAITING </code></ul>
 * <ul>
 *   <li>{@link Object#wait() Object.wait} with no timeout</li>
 *   <li>{@link Thread#join} with no timeout</li>
 *   <li>{@link LockSupport#park() LockSupport.park}</li>
 * </ul>
 * <ul><code>Thread.getState() == TIMED_WAITING </code></ul>
 * <ul>
 *   <li>{@link Thread#sleep}</li>
 *   <li>{@link Object#wait(long) Object.wait} with timeout</li>
 *   <li>{@link Thread#join} with timeout</li>
 *   <li>{@link LockSupport#parkNanos LockSupport.parkNanos}</li>
 *   <li>{@link LockSupport#parkUntil LockSupport.parkUntil}</li>
 * </ul>
 */
public class TestPredicateKillThread extends BaseTenantKillTest {

    // used by testKillNewTenantThread to idicate that child thread has started and is ready to be killed
    private static volatile CountDownLatch cdl = null;

    private static volatile CountDownLatch startSignal = null;

    // to hold information about runnable tasks
    private static class TaskInfo {

        boolean willWaiting;
        String name;
        Runnable task;

        TaskInfo(boolean wait, String nm, Runnable t) {

            willWaiting = wait;
            name = nm;
            task = t;
        }

        @Override
        public boolean equals(Object obj) {

            if (obj instanceof TaskInfo) {
                return task.equals(((TaskInfo) obj).task);
            } else if (obj instanceof Runnable) {
                return task.equals((Runnable) obj);
            }
            return super.equals(obj);
        }
    }
    /*
     * Below Runnable objects are extracted code snippet to create special threading status, like waiting, parking,
     * deadlocking, etc, and they will be used in single-thread testing or threadpool/forkjoinpool.
     *
     * key points for adding new tasks
     * 1, private static member 'cdl' is used to signal that the testing threads are ready, and 'tenant.destroy()' may
     *      be called now.
     * 2, 'cdl' should be initialized by testing method, with the number of testing threads usually. and must be set to
     *      'null' when leaving testing method.
     * 3, extra assertions can be added via 'addExtraAddsertion()' method, to enable the runnables to register some
     *      checking statement after execution of Runnable.run().
     */

    // available runnable tasks.
    private static final List<TaskInfo> tasks = Collections.synchronizedList(new LinkedList<>());

    //------------------------ Non-blocking scenarios ----------------------------
    // threads blocked on CountDownLatch.await()
    private static final Runnable RUN_BLOCK_ON_COUNTDOWNLATCH_AWAIT = () -> {
        signalChildThreadReady();
        waitTerminate();
    };

    private static final Runnable RUN_BUSY = () -> {
        signalChildThreadReady();
        int hundredPlus = 0;
        for (int i = 1 ; i <= 100 ; i++) {
            hundredPlus += i;
        }
        waitTerminate();
    };

    private static final Runnable PROCESS_RUNNABLE = () -> {
        signalChildThreadReady();
        try {
            Process p = Runtime.getRuntime().exec("read");
            p.waitFor();
        } catch (IOException e) {
            // ignore, commands may not be available on all machines
        } catch (InterruptedException e) {
            //ignore
        }
        waitTerminate();
    };

    private static final Runnable FORK_JOIN_RUNNABLE = () -> {
        signalChildThreadReady();
        //do nothing
        waitTerminate();
    };

    private static class TestRootTenantThreadFactory implements ThreadFactory {

        public Thread newThread(Runnable r) {
            Thread[] threads = new Thread[1];
            // run in root tenant
            TenantContainer.primitiveRunInRoot(() -> {
                threads[0] = new Thread(r);
                TenantContainer.setInheritedTenantContainer(threads[0], null);
            });
            return threads[0];
        }
    }

    private static class TestRootTenantForkJoinThreadFactory implements ForkJoinWorkerThreadFactory {

        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread[] threads = new ForkJoinWorkerThread[1];
            // run in root tenant
            TenantContainer.primitiveRunInRoot(() -> {
                threads[0] = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                TenantContainer.setInheritedTenantContainer(threads[0], null);
            });
            return threads[0];
        }
    }

    private static class TestTenantThreadFactory implements ThreadFactory {
        private TenantContainer tenantContainer;

        public TestTenantThreadFactory(TenantContainer tenantContainer) {
            this.tenantContainer = tenantContainer;
        }

        public Thread newThread(Runnable r) {
            Thread[] threads = new Thread[1];
            TenantContainer.primitiveRunInRoot(() -> {
                try {
                    tenantContainer.run(() -> {
                        threads[0] = new Thread(r);
                        TenantContainer.setInheritedTenantContainer(threads[0], tenantContainer);
                    });
                } catch (TenantException ex) {
                    throw new RuntimeException("new thread error in tenant container", ex);
                }
            });
            return threads[0];
        }
    }

    private static class TestTenantForkJoinThreadFactory implements ForkJoinWorkerThreadFactory {
        private TenantContainer tenantContainer;

        public TestTenantForkJoinThreadFactory(TenantContainer tenantContainer) {
            this.tenantContainer = tenantContainer;
        }

        public ForkJoinWorkerThread newThread(ForkJoinPool pool) {
            ForkJoinWorkerThread[] threads = new ForkJoinWorkerThread[1];
            TenantContainer.primitiveRunInRoot(() -> {
                try {
                    tenantContainer.run(() -> {
                        threads[0] = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
                        TenantContainer.setInheritedTenantContainer(threads[0], tenantContainer);
                    });
                } catch (TenantException ex) {
                    throw new RuntimeException("new thread error in tenant container", ex);
                }
            });
            return threads[0];
        }
    }
    //------------------------- Testing entry ----------------------------------------------
    public static void main(String[] args) {

        TestPredicateKillThread test = new TestPredicateKillThread();
        test.testRootTenantThreadFactory();
        test.testTenantThreadFactory();
        test.testNewThreadNoInheritance(false);
        test.testThreadPoolNoInheritance();
        test.testInnocuousThread(false);
        test.testCommonForkJoinPool();
    }

    public void testRootTenantThreadFactory() {
        setUp(4);
        TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
        SharedSecrets.getTenantAccess().setNewPoolTenantInheritancePredicate(tuple -> true);
        SharedSecrets.getTenantAccess().setNewThreadTenantInheritancePredicate(tuple -> true);
        SharedSecrets.getTenantAccess().setPoolThreadTenantInheritancePredicate(tuple -> {
            if(tuple.executorService() instanceof ThreadPoolExecutor
                && ((ThreadPoolExecutor)tuple.executorService()).getThreadFactory() instanceof TestRootTenantThreadFactory) {
                return false;
            }
            if(tuple.executorService() instanceof ForkJoinPool
                && ((ForkJoinPool)tuple.executorService()).getFactory() instanceof TestRootTenantForkJoinThreadFactory) {
                return false;
            }
            return true;
        });
        ExecutorService[] executors = new ExecutorService[4];
        Thread[] threads = new Thread[4];
        try {
            tenant.run(() -> {
                executors[0] = Executors.newFixedThreadPool(1, new TestRootTenantThreadFactory());
                executors[0].submit(() -> {
                    threads[0] = Thread.currentThread();
                    signalChildThreadReady();
                    waitTerminate();
                });

                executors[1] = Executors.newFixedThreadPool(1);
                executors[1].submit(() -> {
                    threads[1] = Thread.currentThread();
                    signalChildThreadReady();
                    waitTerminate();
                });

                executors[2] = new ForkJoinPool(1, new TestRootTenantForkJoinThreadFactory(), (t, e) -> {}, true);
                executors[2].submit(() -> {
                    threads[2] = Thread.currentThread();
                    signalChildThreadReady();
                    waitTerminate();
                });

                executors[3] = new ForkJoinPool(1);
                executors[3].submit(() -> {
                    threads[3] = Thread.currentThread();
                    signalChildThreadReady();
                    waitTerminate();
                });
            });
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }
        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 8);
        Asserts.assertNull(getAttachedTenantContainer(threads[0]));
        Asserts.assertEquals(tenant, getAttachedTenantContainer(threads[1]));
        Asserts.assertNull(getAttachedTenantContainer(threads[2]));
        Asserts.assertEquals(tenant, getAttachedTenantContainer(threads[3]));

        msg("Before destroy");
        assertTenantDestroySuccess(tenant, true, 8);
        msg("After destroy");
        Asserts.assertEquals(threads[0].getState(), Thread.State.WAITING);
        Asserts.assertEquals(threads[1].getState(), Thread.State.TERMINATED);;
        Asserts.assertEquals(threads[2].getState(), Thread.State.WAITING);
        Asserts.assertEquals(threads[3].getState(), Thread.State.TERMINATED);;

        signalAllThreadsExit(2);
    }

    public void testTenantThreadFactory() {
        setUp(4);
        TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
        SharedSecrets.getTenantAccess().setNewPoolTenantInheritancePredicate(tuple -> true);
        SharedSecrets.getTenantAccess().setNewThreadTenantInheritancePredicate(tuple -> true);
        SharedSecrets.getTenantAccess().setPoolThreadTenantInheritancePredicate(tuple -> {
            if(tuple.executorService() instanceof ThreadPoolExecutor
                && ((ThreadPoolExecutor)tuple.executorService()).getThreadFactory() instanceof TestTenantThreadFactory) {
                return false;
            }
            if(tuple.executorService() instanceof ForkJoinPool
                && ((ForkJoinPool)tuple.executorService()).getFactory() instanceof TestTenantForkJoinThreadFactory) {
                return false;
            }
            return true;
        });
        ExecutorService[] executors = new ExecutorService[4];
        Thread[] threads = new Thread[4];
        TenantContainer.primitiveRunInRoot(() -> {
            executors[0] = Executors.newFixedThreadPool(1, new TestTenantThreadFactory(tenant));
            executors[0].submit(() -> {
                threads[0] = Thread.currentThread();
                signalChildThreadReady();
                waitTerminate();
            });

            executors[1] = Executors.newFixedThreadPool(1);
            executors[1].submit(() -> {
                threads[1] = Thread.currentThread();
                signalChildThreadReady();
                waitTerminate();
            });

            executors[2] = new ForkJoinPool(1, new TestTenantForkJoinThreadFactory(tenant), (t, e) -> {}, true);
            executors[2].submit(() -> {
                threads[2] = Thread.currentThread();
                signalChildThreadReady();
                waitTerminate();
            });

            executors[3] = new ForkJoinPool(1);
            executors[3].submit(() -> {
                threads[3] = Thread.currentThread();
                signalChildThreadReady();
                waitTerminate();
            });
        });
        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 8);
        Asserts.assertEquals(tenant, getAttachedTenantContainer(threads[0]));
        Asserts.assertNull(getAttachedTenantContainer(threads[1]));
        Asserts.assertEquals(tenant, getAttachedTenantContainer(threads[2]));
        Asserts.assertNull(getAttachedTenantContainer(threads[3]));

        msg("Before destroy");
        assertTenantDestroySuccess(tenant, true, 8);
        msg("After destroy");
        Asserts.assertEquals(threads[0].getState(), Thread.State.TERMINATED);
        Asserts.assertEquals(threads[1].getState(), Thread.State.WAITING);
        Asserts.assertEquals(threads[2].getState(), Thread.State.TERMINATED);
        Asserts.assertEquals(threads[3].getState(), Thread.State.WAITING);

        signalAllThreadsExit(2);
    }

    public void testCommonForkJoinPool() {
        setUp();
        TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
        SharedSecrets.getTenantAccess().setNewPoolTenantInheritancePredicate(tuple -> true);
        SharedSecrets.getTenantAccess().setNewThreadTenantInheritancePredicate(tuple -> true);
        Thread[] threads = new Thread[1];
        try {
            tenant.run(() -> {
                CompletableFuture<String> future = CompletableFuture.supplyAsync(() -> {
                    threads[0] = Thread.currentThread();
                    FORK_JOIN_RUNNABLE.run();
                    return "Hello World";
                });
            });
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }
        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 8);
        Asserts.assertNull(getAttachedTenantContainer(threads[0]));
        msg("Before destroy");
        assertTenantDestroySuccess(tenant, true, 8);
        msg("After destroy");
        Asserts.assertEquals(threads[0].getState(), Thread.State.WAITING);
        signalAllThreadsExit(1);
    }

    public void testInnocuousThread(boolean virtual) {
        setUp();
        TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
        SharedSecrets.getTenantAccess().setNewPoolTenantInheritancePredicate(tuple -> true);
        SharedSecrets.getTenantAccess().setNewThreadTenantInheritancePredicate(tuple -> true);
        Thread[] threads = new Thread[1];
        try {
            tenant.run(() -> {
                if (virtual) {
                    Thread t = Thread.ofVirtual().unstarted(PROCESS_RUNNABLE);
                    t.start();
                    threads[0] = t;
                } else {
                    Thread t = new Thread(() -> {
                        PROCESS_RUNNABLE.run();
                    });
                    t.start();
                    threads[0] = t;
                }
            });
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }
        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 8);
        Asserts.assertNotNull(getAttachedTenantContainer(threads[0]));
        threads[0].interrupt();
        signalAllThreadsExit(1);
    }


    public void testNewThreadNoInheritance(boolean virtual) {
        msg(">> testNoKillThread" + ", time=" + LocalDateTime.now());
        setUp(tasks.size());

        TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
        SharedSecrets.getTenantAccess().setNewPoolTenantInheritancePredicate(tuple -> false);
        SharedSecrets.getTenantAccess().setNewThreadTenantInheritancePredicate(tuple -> false);
        final List<Thread> threads = new ArrayList<>();

        try {
            tenant.run(() -> {
                tasks.stream().sequential().forEach((t) -> {
                    if (virtual) {
                        Thread childVirtualThread = Thread.ofVirtual().unstarted(() -> {
                            t.task.run();
                        });
                        threads.add(childVirtualThread);
                        childVirtualThread.start();
                    } else {
                        Thread childThread = new Thread(()-> {
                            t.task.run();
                        });
                        threads.add(childThread);
                        childThread.start();
                    }
                });
            });
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }
        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 8);
        threads.stream().sequential().forEach((t) -> {
            Asserts.assertNull(getAttachedTenantContainer(t));
        });
        msg("Before destroy!");
        assertTenantDestroySuccess(tenant, true, 8);
        msg("After destroy!");
        threads.stream().sequential().forEach((t) -> {
            Asserts.assertEquals(t.getState(), Thread.State.WAITING);
        });
        Asserts.assertEquals(tenant.getState(), TenantState.DEAD);
        signalAllThreadsExit(tasks.size());
    }

    public void testThreadPoolNoInheritance() {

        msg(">> testNoKillThreadPool" + ", time=" + LocalDateTime.now());

        setUp(tasks.size());

        TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
        SharedSecrets.getTenantAccess().setNewPoolTenantInheritancePredicate(tuple -> false);
        SharedSecrets.getTenantAccess().setNewThreadTenantInheritancePredicate(tuple -> false);
        final List<Thread> threads = new ArrayList<>();

        ExecutorService[] executors = new ExecutorService[1];
        try {
            tenant.run(() -> {
                // create a thread pool which will create new threads as needed
                ExecutorService executor = Executors.newCachedThreadPool();
                Asserts.assertNotNull(executor);
                tasks.stream().sequential()
                    /* wrap the task with more code */
                    .map(t -> (Runnable) () -> {
                        threads.add(Thread.currentThread());
                        msg("submitted task started: task=" + t.name + ",tenant=" + TenantContainer.current());
                        // attachedTenantContainer assert should not be outside, because task may park which may attached to root
                        Asserts.assertNull(getAttachedTenantContainer(Thread.currentThread()));
                        t.task.run();
                    })
                    .forEach(executor::submit);
                executor.shutdown(); // submit all tasks,
                //  but the orderly shutdown operation will never end without tenant.destroy()
                executors[0] = executor;
            });
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }
        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 16);
        msg("Before destroy!");
        assertTenantDestroySuccess(tenant, true, 32);
        msg("After destroy!");
        threads.stream().sequential().forEach((t) -> {
            Asserts.assertEquals(t.getState(), Thread.State.WAITING);
        });
        Asserts.assertEquals(tenant.getState(), TenantState.DEAD);
        signalAllThreadsExit(tasks.size());
    }


    // facilities to execute some extra assertions after child threads ended, NOTE: not thread safe;
    private static List<Runnable> extraAssertTasks = null;

    private static void initExtraAssertTasks() {

        extraAssertTasks = new ArrayList<>();
    }

    private static void addExtraAssertTask(Runnable task) {

        Asserts.assertTrue(extraAssertTasks != null, "Should call initExtraAssertTask before add one");
        extraAssertTasks.add(task);
    }

    private static void executeExtraAssertTasks() {

        extraAssertTasks.stream().sequential().forEach(Runnable::run);
        cdl = null;
    }

    // facilities to coordinate main testing thread and test threads
    private static void setUp() {

        cdl = new CountDownLatch(1);
        startSignal = new CountDownLatch(1);
        initExtraAssertTasks();
    }

    private static void setUp(int count) {

        cdl = new CountDownLatch(count);
        startSignal = new CountDownLatch(count);
        initExtraAssertTasks();
    }

    private static void signalAllThreadsExit(int count) {
        if (cdl != null && count > 0) {
            for (int i = 0 ; i<count ; i++) {
                cdl.countDown();
            }
        } else {
            msg("WARNING: cdl lock is null or count GT 0");
        }
    }

    // should be call by each child, testing threads, before entering blocking status
    private static void signalChildThreadReady() {

        if (startSignal != null && startSignal.getCount() > 0) {
            startSignal.countDown();
        } else {
            msg("WARNING: main thread and testing threads are not synchronized, may leads to inaccuracy.");
        }
    }

    private static void waitTerminate() {
        if (cdl != null) {
            try {
                cdl.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail();
            }
        } else {
            msg("WARNING: main thread and testing threads are not synchronized, may leads to inaccuracy.");
        }
    }

    private static void waitChildrenThreadsToStart() {

        if (startSignal != null) {
            try {
                startSignal.await();
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail();
            }
        } else {
            msg("WARNING: main thread and testing threads are not synchronized, may leads to inaccuracy.");
        }
    }

    private static void dumpAllThreadStacks() {

        Thread[] threads = new Thread[Thread.activeCount()];
        int cnt = Thread.enumerate(threads);
        msg("Dump threads:");
        for (int i = 0; i < cnt; ++i) {
            msg("Thread:" + threads[i]);
            Arrays.stream(threads[i].getStackTrace()).sequential().map(s -> "\t" + s)
                .forEach(TestPredicateKillThread::msg);
        }
    }

    private static Field inheritedTenantContainerField;
    private static Field attachedTenantContainerField;

    private static TenantContainer getInheritedTenantContainer(Thread t) {

        if (t != null && inheritedTenantContainerField != null) {
            try {
                return (TenantContainer) inheritedTenantContainerField.get(t);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                fail();
            }
        }
        return null;
    }

    private static TenantContainer getAttachedTenantContainer(Thread t) {

        if (t != null && attachedTenantContainerField != null) {
            try {
                return (TenantContainer) attachedTenantContainerField.get(t);
            } catch (IllegalAccessException e) {
                e.printStackTrace();
                fail();
            }
        }
        return null;
    }

    static {
        // initialize known runnables
        tasks.add(new TaskInfo(true, "BLOCK_ON_COUNTDOWNLATCH_AWAIT", RUN_BLOCK_ON_COUNTDOWNLATCH_AWAIT));
        tasks.add(new TaskInfo(true, "RUN_BUSY", RUN_BUSY));

        try {
            inheritedTenantContainerField = Thread.class.getDeclaredField("inheritedTenantContainer");
            inheritedTenantContainerField.setAccessible(true);

            attachedTenantContainerField = Thread.class.getDeclaredField("attachedTenantContainer");
            attachedTenantContainerField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
            fail();
        }
    }
}
