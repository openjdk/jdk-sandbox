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
 * @run main/othervm/timeout=100 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                               -XX:+TraceTenantKillThreads -XX:+MultiTenant -XX:+TenantThreadStop -XX:+TenantCpuAccounting
 *                               -Dcom.alibaba.tenant.ShutdownSTWSoftLimit=10000 -Dcom.alibaba.tenant.DebugTenantShutdown=true -Dcom.alibaba.tenant.PrintStacksOnTimeoutDelay=30000
 *                               --add-opens=java.base/java.lang=ALL-UNNAMED TestKillThread
 *
 */

import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import com.alibaba.tenant.TenantException;
import com.alibaba.tenant.TenantState;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.locks.ReentrantLock;
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
public class TestKillThread extends BaseTenantKillTest {

    // used by testKillNewTenantThread to idicate that child thread has started and is ready to be killed
    private static volatile CountDownLatch cdl = null;

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
    // code snippet which will cause runner thread to do busy loop
    private static final Runnable RUN_BUSY_LOOP = () -> {
        int i = 0;
        final int bounceLimit = 0xFFFF;

        if (cdl != null) {
            cdl.countDown();
        }

        while (true) {
            if (i < bounceLimit) {
                ++i;
            } else {
                i -= bounceLimit;
            }
        }
    };

    // task running in compiled code
    private static void decIt(long num) {

        while (0 != num--)
            ;
    }

    private static final Runnable RUN_COMPILED_BUSY_LOOP = () -> {
        // warmup
        for (int i = 0; i < 100000; ++i) {
            decIt(i);
        }
        msg("Warmup finished, executing in compiled loop");
        if (cdl != null) {
            cdl.countDown();
        }
        while (true) {
            decIt(0xFFFFFFFFl);
        }
    };

    //------------------------ blocked on WAITING state --------------------------
    // code snippet which causes runner thread to block on object.wait()
    private static final Runnable RUN_BLOCK_ON_WAIT = () -> {
        Object obj = new Object();
        synchronized (obj) {
            try {
                signalChildThreadReady();
                obj.wait();
                fail();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (Throwable t) {
                t.printStackTrace();
                fail();
            } finally {
                System.out.println("finally!");
            }
        }
    };

    // code snippet which causes current thread to block on Thread.join()
    private static final Runnable RUN_BLOCK_ON_THREAD_JOIN = () -> {
        CountDownLatch childReady = new CountDownLatch(1);
        Thread child = new Thread(() -> {
            while (true) {
                if (childReady.getCount() > 0) {
                    childReady.countDown();
                }
                long l = 0;
                while (l++ < 8096)
                    ;
            }
        });
        child.setName("THREAD_JOIN_child");
        child.start();
        try {
            childReady.await();

            addExtraAssertTask(() -> {
                Asserts.assertTrue(!child.isAlive() && child.getState() == Thread.State.TERMINATED);
            });

            signalChildThreadReady();

            child.join();
            fail();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        } finally {
            System.out.println("finally!");
        }

    };

    // code snippet which causes runner thread to block on object.wait()
    private static final Runnable RUN_BLOCK_ON_LOCKSUPPORT_PARK = () -> {
        Object obj = new Object();
        synchronized (obj) {
            try {
                signalChildThreadReady();
                LockSupport.park();
                // NOTE: Thread.interrupt() from TenantContainer.destroy() may wake up LockSupport.park() according to
                // Java spec, thus we do not put a 'fail()' here
            } catch (Throwable t) {
                t.printStackTrace();
                fail();
            } finally {
                System.out.println("finally!");
            }
        }
    };

    // threads blocked on CountDownLatch.await()
    private static final Runnable RUN_BLOCK_ON_COUNTDOWNLATCH_AWAIT = () -> {
        CountDownLatch c = new CountDownLatch(1);
        try {
            signalChildThreadReady();
            c.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    };

    private static final Runnable RUN_BLOCK_ON_COUNTDOWNLATCH_TIMED_AWAIT = () -> {
        CountDownLatch c = new CountDownLatch(1);
        try {
            signalChildThreadReady();
            c.await(30_000, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    };

    private static final Runnable RUN_BLOCK_ON_PROCESS_WAIT_FOR = () -> {
        // read commond will block for stdin
        final String[] commands = {"read"};
        for (String c : commands) {
            try {
                Process p = Runtime.getRuntime().exec(c);
                signalChildThreadReady();
                p.waitFor();
            } catch (IOException e) {
                // ignore, commands may not be available on all machines
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    };

    //------------------------ blocked on TIMED_WAITING state --------------------------
    // code snippet which will cause runner thread to block on object.wait()
    private static final Runnable RUN_BLOCK_ON_TIMED_WAIT = () -> {
        Object obj = new Object();
        synchronized (obj) {
            while (true) {
                try {
                    signalChildThreadReady();
                    obj.wait(30_000);
                    // fail();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                } catch (Throwable t) {
                    t.printStackTrace();
                    fail();
                } finally {
                    System.out.println("finally!");
                }
            }
        }
    };

    // Code snippet which causes current thread to block on Thread.sleep()
    private static final Runnable RUN_BLOCK_ON_SLEEP = () -> {
        while (true) {
            try {
                signalChildThreadReady();
                Thread.sleep(30_000);
                fail(); // it is unlikely to reach here after 30 seconds, the parent should kill it immediately
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("finally!");
            }
        }
    };

    // code snippet which causes current thread to block on Thread.join(time)
    private static final Runnable RUN_BLOCK_ON_THREAD_TIMED_JOIN = () -> {
        CountDownLatch childReady = new CountDownLatch(1);
        Thread child = new Thread(() -> {
            while (true) {
                if (childReady.getCount() > 0) {
                    childReady.countDown();
                }
                long l = 0;
                while (l++ < 512)
                    ;
            }
        });
        child.setName("THREAD_TIMED_JOIN_child");
        child.start();
        try {
            try {
                childReady.await();

                addExtraAssertTask(() -> {
                    Asserts.assertTrue(!child.isAlive() && child.getState() == Thread.State.TERMINATED);
                });

                signalChildThreadReady();
                child.join(30_000);
                fail();
            } catch (InterruptedException e) {
                e.printStackTrace();
            } finally {
                System.out.println("finally!");
            }
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        } finally {
            System.out.println("finally!");
        }


    };

    // code snippet which causes runner thread to block on LockSupport.parkNanos()
    private static final Runnable RUN_BLOCK_ON_LOCKSUPPORT_PARK_NANOS = () -> {
        Object obj = new Object();
        try {
            signalChildThreadReady();
            LockSupport.parkNanos(30_000_000_000L);
            // NOTE: Thread.interrupt() from TenantContainer.destroy() may wake up LockSupport.park() according to
            // Java spec, thus we do not put a 'fail()' here
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        } finally {
            while (true)
                ;
        }
    };

    // code snippet which causes runner thread to block on LockSupport.parkUntil()
    private static final Runnable RUN_BLOCK_ON_LOCKSUPPORT_PARK_UNTIL = () -> {
        Object obj = new Object();
        long untilTime = System.currentTimeMillis() + 30_000;
        try {
            signalChildThreadReady();
            LockSupport.parkUntil(untilTime);
            // NOTE: Thread.interrupt() from TenantContainer.destroy() may wake up LockSupport.park() according to
            // Java spec, thus we do not put a 'fail()' here
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        } finally {
            System.out.println("finally!");
        }
    };

    //------------------------ blocked on I/O --------------------------
    private static final Runnable RUN_BLOCK_ON_STDIN = () -> {
        try {
            signalChildThreadReady();
            System.in.read();
        } catch (IOException e) {
            fail();
            e.printStackTrace();
        } finally {
            System.out.println("finally!");
        }
    };

    private static final Runnable RUN_BLOCK_ON_ACCEPT = () -> {
        try {
            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.bind(new InetSocketAddress(0));
            ssc.configureBlocking(true);
            signalChildThreadReady();
            ssc.accept();
        } catch (ClosedByInterruptException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            System.out.println("finally!");
        }
    };

    private static final Runnable RUN_BLOCK_ON_CONNECT = () -> {
        try {
            SocketChannel ch = SocketChannel.open();
            ch.configureBlocking(true);
            signalChildThreadReady();
            ch.connect(new InetSocketAddress("8.8.8.8", 80));
        } catch (ClosedByInterruptException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            System.out.println("finally!");
        }
    };

    private static final Runnable RUN_BLOCK_ON_SELECT = () -> {
        try {
            Selector selector = Selector.open();

            ServerSocketChannel ssc = ServerSocketChannel.open();
            ssc.bind(new InetSocketAddress(0));
            ssc.configureBlocking(false);
            ssc.accept();
            ssc.register(selector, SelectionKey.OP_ACCEPT);

            SocketChannel ch = SocketChannel.open();
            ch.configureBlocking(false);
            ch.connect(new InetSocketAddress("8.8.8.8", 80));
            ch.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            signalChildThreadReady();
            int num = selector.select();
            selector.selectedKeys().stream()
                .map(k -> "ops:" + k.interestOps())
                .forEach(System.out::println);
            Asserts.assertEquals(0, num);
        } catch (ClosedByInterruptException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        } finally {
            System.out.println("finally!");
        }
    };

    private static final Runnable RUN_BLOCK_ON_RECV = () -> {
        try {
            DatagramChannel ch = DatagramChannel.open();
            ch.bind(new InetSocketAddress("127.0.0.1", 0));
            ch.configureBlocking(true);
            signalChildThreadReady();
            ch.receive(ByteBuffer.allocate(1024));
        } catch (ClosedByInterruptException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    };

    private static final Runnable RUN_BLOCK_ON_UDP_READ = () -> {
        UDPSocketPair pair = new UDPSocketPair();
        DatagramChannel client = pair.clientEnd;
        try {
            signalChildThreadReady();
            client.read(ByteBuffer.allocate(1024));
        } catch (ClosedByInterruptException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
    };

    private static final Runnable RUN_BLOCK_ON_TCP_READ = () -> {
        TCPSocketPair pair = new TCPSocketPair();
        SocketChannel client = pair.clientEnd;
        try {
            addExtraAssertTask(() -> {
                pair.cleanup();
            });

            signalChildThreadReady();
            // try to read from a connected socket pair
            client.configureBlocking(true);
            client.read(ByteBuffer.allocate(1024));
        } catch (ClosedByInterruptException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }

    };

    //----------------------- composed scenarios -------------------------
    private static final Runnable RUN_BASIC_EXCLUSIVE_LOCKING = () -> {
        final Lock lock = new ReentrantLock();
        CountDownLatch childrenReady = new CountDownLatch(2);
        Runnable exclusiveTask = () -> {
            msg("exclusive task" + Thread.currentThread());
            childrenReady.countDown();
            lock.lock();
            try {
                while (true)
                    ;
            } finally {
                lock.unlock();
            }
        };

        Thread t1 = new Thread(exclusiveTask);
        t1.setName("Exclusive_lock_t1");
        t1.start();
        Thread t2 = new Thread(exclusiveTask);
        t2.setName("Exclusive_lock_t2");
        t2.start();
        try {
            childrenReady.await();
            signalChildThreadReady();
            t1.join();
            t2.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    };

    // one lock holder and many acquirer
    private static final Runnable RUN_LOOP_AND_TRY_LOCK = () -> {
        Object lock = new Object();
        CountDownLatch holderReady = new CountDownLatch(1);
        Thread holder = new Thread(() -> {
            synchronized (lock) {
                holderReady.countDown();
                while (true)
                    ;
                // never return
            }
        });
        holder.setName("LOCK_AND_TRY_holder");

        // many threads waiting for the lock
        int waiterCount = 64;
        Thread[] threads = new Thread[waiterCount];
        CountDownLatch testBegin = new CountDownLatch(waiterCount);
        for (int i = 0; i < waiterCount; ++i) {
            msg("waiter " + i + " started");
            threads[i] = new Thread(() -> {
                try {
                    msg("Started thread:" + Thread.currentThread());
                    holderReady.await();
                    testBegin.countDown();
                    synchronized (lock) { // will be blocked here
                        fail();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
            threads[i].setName("LOCK_AND_TRY_getter_" + i);
            threads[i].start();
        }
        holder.start();

        msg("testBegin=" + testBegin.getCount());
        // wait until all testing threads started, then trigger tenant destroy
        try {
            testBegin.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        // extra assertions after 'tenant.destroy()'
        addExtraAssertTask(() -> {
            Arrays.stream(threads).sequential()
                .forEach(thrd -> Asserts.assertFalse(thrd.isAlive()));

            Arrays.stream(threads).sequential().forEach(thrd -> {
                try {
                    thrd.join();
                    holder.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    fail();
                }
            });

            Asserts.assertEquals(holder.getState(), Thread.State.TERMINATED);
            Arrays.stream(threads).sequential()
                .forEach(thrd -> Asserts.assertEquals(thrd.getState(), Thread.State.TERMINATED));
        });

        signalChildThreadReady();

        Arrays.stream(threads).sequential().forEach(thrd -> {
            try {
                thrd.join();
                holder.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        });

    };

    // To test destroy of ForkJoinPool
    private static class BusyLoopAction extends RecursiveAction {

        private int cnt;
        private List<Thread> threads;
        private CountDownLatch countDown;

        BusyLoopAction(int cnt, List<Thread> thrds, CountDownLatch count) {

            this.cnt = cnt;
            this.threads = thrds;
            countDown = count;
        }

        @Override
        protected void compute() {

            if (cnt > 0) {
                BusyLoopAction task = new BusyLoopAction(cnt - 1, threads, countDown);
                task.fork();
                Asserts.assertNotNull(threads);
                Thread curThread = Thread.currentThread();
                if (!threads.contains(curThread)) {
                    threads.add(curThread);
                }
                // below statement is against JavaSE's convention, just for testing purpose,
                // in realworld application, one may never want to put a infinite loop into ForkJoinPool a task like this
                Runnable t = () -> {
                    while (true)
                        ;
                };
                countDown.countDown();
                t.run();
                task.join();
            }
        }
    }

    private static final Runnable RUN_BLOCK_THREADS_OF_FORKJOIN_POOL = () -> {
        List<Thread> threads = Collections.synchronizedList(new ArrayList<>(tasks.size()));
        int limit = tasks.size();
        ForkJoinPool myPool = new ForkJoinPool(limit);
        CountDownLatch childrenReady = new CountDownLatch(limit);
        myPool.submit(new BusyLoopAction(limit, threads, childrenReady));
        try {
            childrenReady.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            addExtraAssertTask(() -> {
                threads.stream().sequential().distinct()
                    .forEach(t -> Asserts.assertFalse(t.isAlive()));
                threads.stream().sequential().distinct().forEach(t -> {
                    try {
                        t.join();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        fail();
                    }
                });
                threads.stream().sequential().distinct()
                    .forEach(t -> Asserts.assertEquals(t.getState(), Thread.State.TERMINATED));
            });
            signalChildThreadReady();

            threads.stream().sequential().distinct().forEach(t -> {
                try {
                    t.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            });
        }
    };

    //------------------------- Testing entry ----------------------------------------------
    public static void main(String[] args) {

        TestKillThread test = new TestKillThread();
        test.testWaitingThreads(false);
        test.testNotKillRootThreads(false);
        test.testNotKillTenantRunInRootThread(false);
        test.testNotKillShutDownMaskedThread(false);
        test.testKillNewSingleThreadExecutorService();
        test.testKillNewTenantThreadPool();
    }

    protected void testNotKillRootThreads(boolean virtualThread) {

        msg(">> testNotKillRootThreads()");
        TenantConfiguration config = new TenantConfiguration();
        TenantContainer tenant = TenantContainer.create(config);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicBoolean endTest = new AtomicBoolean(false);
        AtomicBoolean safeEnd = new AtomicBoolean(false);

        // below thread is created in Root tenant, but attached to non-root tenant.
        // should not be impacted by TenantContainer.destroy();
        Runnable op = () -> {
            try {
                tenant.run(() -> {
                    latch.countDown();
                    long l = 0;
                    while (!endTest.get()) {
                        l++;
                    }
                });
            } catch (Throwable t) {
                t.printStackTrace();
                fail();
            }
            safeEnd.set(true);
        };
        Thread threadInRoot;
        if (virtualThread) {
            threadInRoot = Thread.ofVirtual().unstarted(op);
        } else {
            threadInRoot = new Thread(op);
        }
        threadInRoot.start();

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        Asserts.assertTrue(threadInRoot.isAlive());
        Asserts.assertNull(getInheritedTenantContainer(threadInRoot));
        Asserts.assertEquals(tenant, getAttachedTenantContainer(threadInRoot));
        Asserts.assertTrue(threadInRoot.getState() != Thread.State.TERMINATED);

        assertTenantDestroySuccess(tenant, true, 8);

        // thread should not be killed by 'tenant.destroy()'
        Asserts.assertTrue(threadInRoot.isAlive());
        Asserts.assertTrue(threadInRoot.getState() != Thread.State.TERMINATED);


        endTest.set(true);
        try {
            threadInRoot.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        Asserts.assertTrue(safeEnd.get());
        Asserts.assertNull(getInheritedTenantContainer(threadInRoot));
        Asserts.assertNull(getAttachedTenantContainer(threadInRoot));

        msg("<< testNotKillRootThreads()");
    }

    protected void testNotKillTenantRunInRootThread(boolean virtualThread) {

        msg(">> testNotKillTenantRunInRootThread()");
        TenantConfiguration config = new TenantConfiguration();
        TenantContainer tenant = TenantContainer.create(config);
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        CountDownLatch destroyLatch = new CountDownLatch(1);
        AtomicBoolean endTest = new AtomicBoolean(false);
        final Thread[] threads = new Thread[threadCount];
        final TenantContainer[] currenTenantContainers = new TenantContainer[threadCount];
        final TenantContainer[] runInRooTenantContainers = new TenantContainer[threadCount];
        try {
            for(int i = 0;i < threadCount; i++) {
                final int index = i;
                tenant.run(() -> {
                    Runnable op = () -> {
                        currenTenantContainers[index] = TenantContainer.current();
                        msg("new thread-->" + Thread.currentThread() + " TenantContainer: " + TenantContainer.current());
                        TenantContainer.primitiveRunInRoot(()-> {
                            runInRooTenantContainers[index] = TenantContainer.current();
                            latch.countDown();
                            long l = 0;
                            while (!endTest.get()) {
                                l++;
                                if(l % 1000 == 0) {
                                    try {
                                        Thread.sleep(10);
                                    } catch(InterruptedException ex) {
                                        // ignore
                                    }
                                }
                            }
                        });
                    };
                    Thread thread;
                    if (virtualThread) {
                        thread = Thread.ofVirtual().unstarted(op);
                    } else {
                        thread = new Thread(op);
                    }
                    threads[index] = thread;
                    thread.setName("test-thread-" + index);
                    thread.start();
                });
            }
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        for(int i = 0;i < threadCount; i++) {
            Asserts.assertEquals(tenant, currenTenantContainers[i]);
            Asserts.assertNull(runInRooTenantContainers[i]);
            Asserts.assertEquals(tenant, getInheritedTenantContainer(threads[i]));
            // attach to root
            Asserts.assertNull(getAttachedTenantContainer(threads[i]));
        }

        // destroy will block
        Thread destroyTask = new Thread(() -> {
            msg("Before destroy!");
            destroyLatch.countDown();
            assertTenantDestroySuccess(tenant, true, 18);
            msg("After destroy! ");
        });
        destroyTask.start();

        try {
            destroyLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        Asserts.assertEquals(tenant.getState(), TenantState.STOPPING);
        // check thread status
        for(int i = 0;i < threadCount; i++) {
            Asserts.assertTrue(threads[i].isAlive(), "tenant thread run in root should not be killed");
        }
        Asserts.assertTrue(destroyTask.isAlive(), "tenant thread run in root destroy should be blocked");

        endTest.set(true);

        try {
            destroyTask.join();
            threads[0].join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        Asserts.assertEquals(tenant.getState(), TenantState.DEAD);
        for(int i = 0;i < threadCount; i++) {
            Asserts.assertEquals(threads[i].getState(), Thread.State.TERMINATED, "Should be terminated after join()");
            Asserts.assertNull(threads[i].getThreadGroup());
            Asserts.assertNull(threads[i].getContextClassLoader());
            Asserts.assertNull(getInheritedTenantContainer(threads[i]));
            Asserts.assertNull(getAttachedTenantContainer(threads[i]));
        }
        msg("<< testNotKillTenantRunInRootThread");
    }

    protected void testNotKillShutDownMaskedThread(boolean virtualThread) {

        msg(">> testNotKillShutDownMaskedThread()");
        TenantConfiguration config = new TenantConfiguration();
        TenantContainer tenant = TenantContainer.create(config);
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        CountDownLatch destroyLatch = new CountDownLatch(1);
        AtomicBoolean endTest = new AtomicBoolean(false);
        final Thread[] threads = new Thread[threadCount];
        final TenantContainer[] currenTenantContainers = new TenantContainer[threadCount];

        try {
            for(int i = 0;i < threadCount; i++) {
                final int index = i;
                tenant.run(() -> {
                    Runnable op = () -> {
                        currenTenantContainers[index] = TenantContainer.current();
                        msg("new thread-->" + Thread.currentThread() + " TenantContainer: " + TenantContainer.current());
                        // code between maskTenantShutdown and unmaskTenantShutdown will not be interrupted
                        TenantContainer.maskTenantShutdown();
                        try {
                            // test nested mask & unmask
                            TenantContainer.maskTenantShutdown();
                            try {
                                TenantContainer.maskTenantShutdown();
                                try {
                                    latch.countDown();
                                    long l = 0;
                                    while (!endTest.get()) {
                                        l++;
                                        if(l % 1000 == 0) {
                                            try {
                                                Thread.sleep(10);
                                            } catch(InterruptedException ex) {
                                                // ignore
                                            }
                                        }
                                    }
                                } finally {
                                    TenantContainer.unmaskTenantShutdown();
                                }
                            } finally {
                                TenantContainer.unmaskTenantShutdown();
                            }
                        } finally {
                            TenantContainer.unmaskTenantShutdown();
                        }
                    };
                    Thread thread;
                    if (virtualThread) {
                        thread = Thread.ofVirtual().unstarted(op);
                    } else {
                        thread = new Thread(op);
                    }
                    threads[index] = thread;
                    thread.setName("test-thread-" + index);
                    thread.start();
                });
            }
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }

        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        for(int i = 0;i < threadCount; i++) {
            Asserts.assertEquals(tenant, currenTenantContainers[i]);
            Asserts.assertEquals(tenant, getInheritedTenantContainer(threads[i]));
            Asserts.assertEquals(tenant, getAttachedTenantContainer(threads[i]));
        }

        // destroy will block
        Thread destroyTask = new Thread(() -> {
            msg("Before destroy!");
            destroyLatch.countDown();
            assertTenantDestroySuccess(tenant, true, 18);
            msg("After destroy! ");
        });
        destroyTask.start();

        try {
            destroyLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        try {
            Thread.sleep(8000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        Asserts.assertEquals(tenant.getState(), TenantState.STOPPING);
        // check thread status
        for(int i = 0;i < threadCount; i++) {
            Asserts.assertTrue(threads[i].isAlive(), "thread with maskTenantShutdown should not be killed");
        }
        Asserts.assertTrue(destroyTask.isAlive(), "thread with maskTenantShutdown destroy should be blocked");

        endTest.set(true);

        try {
            destroyTask.join();
            threads[0].join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        Asserts.assertEquals(tenant.getState(), TenantState.DEAD);
        for(int i = 0;i < threadCount; i++) {
            Asserts.assertEquals(threads[i].getState(), Thread.State.TERMINATED, "Should be terminated after join()");
            Asserts.assertNull(threads[i].getThreadGroup());
            Asserts.assertNull(threads[i].getContextClassLoader());
            Asserts.assertNull(getInheritedTenantContainer(threads[i]));
            Asserts.assertNull(getAttachedTenantContainer(threads[i]));
        }
        msg("<< testNotKillShutDownMaskedThread");
    }

    protected void testWaitingThreads(boolean virtualThread) {

        tasks.stream().sequential()
            .forEach((ti) -> {
                testKillNewTenantThread(ti, virtualThread);
            });
    }

    // To test killing of independent tenant threads (non-threadpool)
    private void testKillNewTenantThread(TaskInfo ti, final boolean virtualThread) {

        msg(">> testKillNewTenantThread: task=" + ti.name + ", time=" + LocalDateTime.now());

        setUp();

        URLClassLoader testContextClassLoader = new URLClassLoader(
            new URL[]{this.getClass().getProtectionDomain().getCodeSource().getLocation()});
        // kill thread created from a tenant container;
        TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
        final Thread[] threads = new Thread[1];
        final TenantContainer[] currentTenantContainer = new TenantContainer[1];
        try {
            tenant.run(() -> {
                Thread thread;
                if (virtualThread) {
                    thread = Thread.ofVirtual().unstarted(() -> {
                        currentTenantContainer[0] = TenantContainer.current();
                        msg("new virtual thread-->" + Thread.currentThread()+ " TenantConainer: " + TenantContainer.current());
                        // attachedTenantContainer assert should not be outside, because task may park which may attached to root
                        if(tenant != getAttachedTenantContainer(Thread.currentThread())) {
                            signalChildThreadReady();
                            Asserts.assertTrue(false, "attachedTenantContainer is expected to " + tenant);
                        }
                        ti.task.run();
                    });
                } else {
                    thread = new Thread(() -> {
                        currentTenantContainer[0] = TenantContainer.current();
                        msg("new thread-->" + Thread.currentThread() + " TenantConainer: " + TenantContainer.current());
                        // attachedTenantContainer assert should not be outside, because task may park which may attached to root
                        if(tenant != getAttachedTenantContainer(Thread.currentThread())) {
                            signalChildThreadReady();
                            Asserts.assertTrue(false, "attachedTenantContainer is expected to " + tenant);
                        }
                        ti.task.run();
                    });
                }
                thread.setContextClassLoader(testContextClassLoader);
                threads[0] = thread;
                thread.setName("test-thread-" + ti.name);
                thread.start();
            });
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }

        msg("wait for chidren threads start!");
        // wait for ready signal from child-tenant thread
        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 16);

        msg("check thread before destroy -> " + threads[0] + ", state:" + threads[0].getState() + ", TenantConainer:" + getInheritedTenantContainer(threads[0]));

        Asserts.assertTrue(threads[0].isAlive(), "child thread is not alive before destroy");
        Asserts.assertEquals(testContextClassLoader, threads[0].getContextClassLoader());
        Asserts.assertEquals(tenant, currentTenantContainer[0]);
        Asserts.assertEquals(tenant, getInheritedTenantContainer(threads[0]));

        // destroy tenant container
        msg("Before destroy!");
        assertTenantDestroySuccess(tenant, true, 8);
        msg("After destroy! ");

        Asserts.assertEquals(tenant.getState(), TenantState.DEAD);

        // check thread status
        Asserts.assertFalse(threads[0].isAlive(), "thread not killed, task = " + ti.name);
        try {
            threads[0].join();
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }
        Asserts.assertEquals(threads[0].getState(), Thread.State.TERMINATED, "Should be terminated after join()");
        Asserts.assertNull(threads[0].getThreadGroup());
        Asserts.assertNull(threads[0].getContextClassLoader());
        Asserts.assertNull(getInheritedTenantContainer(threads[0]));
        Asserts.assertNull(getAttachedTenantContainer(threads[0]));

        msg("<< testKillNewTenantThread: task=" + ti.name + ", time=" + LocalDateTime.now()
            + ", tenant thread terminated");

        executeExtraAssertTasks();

        msg("<< testKillNewTenantThread: task=" + ti.name + ", time=" + LocalDateTime.now());
    }

    //------- thread pool related testing --------
    protected void testKillNewSingleThreadExecutorService() {

        msg(">>testKillNewSingleThreadExecutorService" + ", time=" + LocalDateTime.now());

        setUp();

        // kill thread created from a tenant container;
        TenantConfiguration config = new TenantConfiguration();
        TenantContainer tenant = TenantContainer.create(config);

        List<Thread> poolThreads = new ArrayList<>(1);
        final TenantContainer[] currentTenantContainer = new TenantContainer[1];
        ExecutorService[] executors = new ExecutorService[1];

        try {
            tenant.run(() -> {
                ExecutorService executor = Executors.newSingleThreadExecutor();
                executor.submit(() -> {
                    currentTenantContainer[0] = TenantContainer.current();
                    msg("in submitted task");
                    poolThreads.add(Thread.currentThread());
                    cdl.countDown();
                    while (true)
                        ;
                });
                executors[0] = executor;
            });
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        }

        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 16);

        // thread should have been submitted successfully
        Asserts.assertEquals(poolThreads.size(), 1);
        Asserts.assertTrue(poolThreads.get(0).isAlive());
        Asserts.assertEquals(tenant, currentTenantContainer[0]);
        Asserts.assertEquals(tenant, getInheritedTenantContainer(poolThreads.get(0)));
        Asserts.assertEquals(tenant, getAttachedTenantContainer(poolThreads.get(0)));

        assertTenantDestroySuccess(tenant, true, 8);

        Asserts.assertTrue(!poolThreads.get(0).isAlive());

        poolThreads.forEach(t -> {
            try {
                t.join();
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail();
            }
        });
        Asserts.assertEquals(poolThreads.get(0).getState(), Thread.State.TERMINATED);
        Asserts.assertNull(poolThreads.get(0).getThreadGroup());
        Asserts.assertNull(poolThreads.get(0).getContextClassLoader());
        Asserts.assertNull(getInheritedTenantContainer(poolThreads.get(0)));
        Asserts.assertNull(getAttachedTenantContainer(poolThreads.get(0)));
        executeExtraAssertTasks();

        msg("<<testKillNewSingleThreadExecutorService" + ", time=" + LocalDateTime.now());
    }

    // new thread pool executor with growable number of worker threads
    private void testKillNewTenantThreadPool() {

        msg(">> testKillNewTenantThreadPool" + ", time=" + LocalDateTime.now());

        setUp(tasks.size());

        TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
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
                        if(tenant != getAttachedTenantContainer(Thread.currentThread())) {
                            signalChildThreadReady();
                            Asserts.assertTrue(false, "attachedTenantContainer is expected to " + tenant);
                        }
                        t.task.run();
                    })
                    .forEach(executor::submit);
                executors[0] = executor;
            });
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }

        // wait for all task threads to count down
        dumpWhenTimeout(() -> waitChildrenThreadsToStart(), 32);

        threads.stream().sequential()
            .distinct().filter(t -> t != null)
            .forEach(thread -> {
                msg("check thread before destroy -> " + thread + ", state:" + thread.getState() + ", TenantConainer:" + getInheritedTenantContainer(thread));
                Asserts.assertTrue(thread.isAlive(), "child thread is not alive before destroy");
                Asserts.assertEquals(tenant, getInheritedTenantContainer(thread));
            });

        Asserts.assertGT(tenant.getProcessCpuTime(), 0l);
        // forcefully destroy tenant, all new threads created in new thread pool should be terminated
        msg("Trying to destroy tenant");
        assertTenantDestroySuccess(tenant, true, 32);
        msg("After destroy tenant! ");

        Asserts.assertEquals(tenant.getState(), TenantState.DEAD);

        // verify to confirm all threads terminated
        threads.stream().sequential()
            .distinct().filter(t -> t != null)
            .forEach(thread ->
                Asserts.assertFalse(thread.isAlive(), "pooled thread not killed"));

        // wait all child tasks to join
        threads.stream().sequential()
            .distinct().filter(t -> t != null)
            .forEach(thread -> {
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    fail();
                }
            });

        threads.stream().sequential()
            .distinct().filter(t -> t != null)
            .forEach(thread ->
                Asserts.assertEquals(thread.getState(), Thread.State.TERMINATED, "Should be terminated after join"));
        threads.stream().sequential()
            .distinct().filter(t -> t != null)
            .forEach(thread -> {
                Asserts.assertNull(thread.getThreadGroup());
                Asserts.assertNull(thread.getContextClassLoader());
                Asserts.assertNull(getInheritedTenantContainer(thread));
                Asserts.assertNull(getAttachedTenantContainer(thread));
            });

        executeExtraAssertTasks();

        msg("<< testKillNewTenantThreadPool" + ", time=" + LocalDateTime.now());
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
        initExtraAssertTasks();
    }

    private static void setUp(int count) {

        cdl = new CountDownLatch(count);
        initExtraAssertTasks();
    }

    // should be call by each child, testing threads, before entering blocking status
    private static void signalChildThreadReady() {

        if (cdl != null && cdl.getCount() > 0) {
            cdl.countDown();
        } else {
            msg("WARNING: main thread and testing threads are not synchronized, may leads to inaccuracy.");
        }
    }

    private static void waitChildrenThreadsToStart() {

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

    private static void dumpAllThreadStacks() {

        Thread[] threads = new Thread[Thread.activeCount()];
        int cnt = Thread.enumerate(threads);
        msg("Dump threads:");
        for (int i = 0; i < cnt; ++i) {
            msg("Thread:" + threads[i]);
            Arrays.stream(threads[i].getStackTrace()).sequential().map(s -> "\t" + s)
                .forEach(TestKillThread::msg);
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

    // utilities class to create a pair of TCP sockets connected to each other
    static class TCPSocketPair {

        ServerSocketChannel server;
        SocketChannel clientEnd;
        SocketChannel serverEnd;

        TCPSocketPair() {

            try {
                server = ServerSocketChannel.open();
                server.bind(new InetSocketAddress(InetAddress.getLocalHost(), 0));
                server.configureBlocking(true);
                SocketChannel ch[] = new SocketChannel[1];
                Thread t = new Thread(() -> {
                    try {
                        msg("Starting accept: tenant=" + TenantContainer.current());
                        ch[0] = server.accept();
                    } catch (IOException e) {
                        e.printStackTrace();
                        fail();
                    }
                });
                t.setName("TCP accepter");
                t.start();

                clientEnd = SocketChannel.open(server.getLocalAddress());
                clientEnd.configureBlocking(true);
                t.join();
                serverEnd = ch[0];

                Asserts.assertNotNull(serverEnd);
                Asserts.assertNotNull(clientEnd);
            } catch (Throwable t) {
                t.printStackTrace();
                fail();
            }
        }

        //  normally socket should have been closed by JVM, below code might not be needed
        void cleanup() {

            try {
                if (clientEnd != null) {
                    clientEnd.close();
                }
                if (serverEnd != null) {
                    serverEnd.close();
                }
                if (server != null) {
                    server.close();
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    ;

    static class UDPSocketPair {

        DatagramChannel serverEnd;
        DatagramChannel clientEnd;

        UDPSocketPair() {

            try {
                serverEnd = DatagramChannel.open();
                serverEnd.configureBlocking(true);
                serverEnd.bind(new InetSocketAddress(InetAddress.getLocalHost(), 0));
                clientEnd = DatagramChannel.open();
                clientEnd.configureBlocking(true);
                clientEnd.connect(serverEnd.getLocalAddress());
                Asserts.assertNotNull(serverEnd);
                Asserts.assertNotNull(clientEnd);
            } catch (Throwable t) {
                t.printStackTrace();
                fail();
            }
        }

        void cleanup() {

            try {
                if (clientEnd != null) {
                    clientEnd.close();
                }
                if (serverEnd != null) {
                    serverEnd.close();
                }
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }
    }

    static {
        // initialize known runnables
        tasks.add(new TaskInfo(false, "BUSY_LOOP", RUN_BUSY_LOOP));
        tasks.add(new TaskInfo(false, "COMPILED_BUSY_LOOP", RUN_COMPILED_BUSY_LOOP));
        tasks.add(new TaskInfo(true, "BLOCK_ON_WAIT", RUN_BLOCK_ON_WAIT));
        tasks.add(new TaskInfo(true, "BLOCK_ON_TIMED_WAIT", RUN_BLOCK_ON_TIMED_WAIT));
        tasks.add(new TaskInfo(true, "BLOCK_ON_PROCESS_WAIT_FOR", RUN_BLOCK_ON_PROCESS_WAIT_FOR));
        tasks.add(new TaskInfo(true, "BLOCK_ON_SLEEP", RUN_BLOCK_ON_SLEEP));
        tasks.add(new TaskInfo(true, "BLOCK_ON_COUNTDOWNLATCH_AWAIT", RUN_BLOCK_ON_COUNTDOWNLATCH_AWAIT));
        tasks.add(new TaskInfo(true, "BLOCK_ON_COUNTDOWNLATCH_TIMED_AWAIT", RUN_BLOCK_ON_COUNTDOWNLATCH_TIMED_AWAIT));
        tasks.add(new TaskInfo(true, "BLOCK_ON_THREAD_JOIN", RUN_BLOCK_ON_THREAD_JOIN));
        tasks.add(new TaskInfo(true, "BLOCK_ON_THREAD_TIMED_JOIN", RUN_BLOCK_ON_THREAD_TIMED_JOIN));
        tasks.add(new TaskInfo(true, "BLOCK_ON_LOCKSUPPORT_PARK", RUN_BLOCK_ON_LOCKSUPPORT_PARK));
        tasks.add(new TaskInfo(true, "BLOCK_ON_LOCKSUPPORT_PARK_NANOS", RUN_BLOCK_ON_LOCKSUPPORT_PARK_NANOS));
        tasks.add(new TaskInfo(true, "BLOCK_ON_LOCKSUPPORT_PARK_UNTIL", RUN_BLOCK_ON_LOCKSUPPORT_PARK_UNTIL));
        //tasks.add(new TaskInfo(true, "BLOCK_ON_IO", RUN_BLOCK_ON_STDIN)); // unsupported for now
        tasks.add(new TaskInfo(true, "BLOCK_ON_ACCEPT", RUN_BLOCK_ON_ACCEPT));
        tasks.add(new TaskInfo(true, "BLOCK_ON_CONNECT", RUN_BLOCK_ON_CONNECT));
        tasks.add(new TaskInfo(true, "BLOCK_ON_SELECT", RUN_BLOCK_ON_SELECT));
        tasks.add(new TaskInfo(true, "BLOCK_ON_RECV", RUN_BLOCK_ON_RECV));
        tasks.add(new TaskInfo(true, "BLOCK_ON_UDP_READ", RUN_BLOCK_ON_UDP_READ));
        tasks.add(new TaskInfo(true, "BLOCK_ON_TCP_READ", RUN_BLOCK_ON_TCP_READ));
        tasks.add(new TaskInfo(true, "BASIC_EXCLUSIVE_LOCKING", RUN_BASIC_EXCLUSIVE_LOCKING));
        tasks.add(new TaskInfo(true, "LOOP_AND_TRY_LOCK", RUN_LOOP_AND_TRY_LOCK));
        tasks.add(new TaskInfo(true, "BLOCK_THREADS_OF_FORKJOIN_POOL", RUN_BLOCK_THREADS_OF_FORKJOIN_POOL));

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
