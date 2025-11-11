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
 *                               -XX:+UnlockExperimentalVMOptions -XX:+VMContinuations
 *                               -Dcom.alibaba.tenant.ShutdownSTWSoftLimit=500  -Dcom.alibaba.tenant.DebugTenantShutdown=true
 *                               --add-opens=java.base/java.lang=ALL-UNNAMED --add-opens=java.base/com.alibaba.tenant=ALL-UNNAMED
 *                               TestTenantKillTimeoutNoStop
 *
 */
import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import com.alibaba.tenant.TenantException;
import com.alibaba.tenant.TenantState;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.CountDownLatch;
import java.util.Set;
import java.time.LocalDateTime;
import jdk.test.lib.Asserts;

/**
 * Below testcase tries to test thread killing timeout and continue shut shutdown in watch dog thread
 */
public class TestTenantKillTimeoutNoStop extends BaseTenantKillTest {

    //------------------------- Testing entry ----------------------------------------------
    public static void main(String[] args) {
        TestTenantKillTimeoutNoStop test = new TestTenantKillTimeoutNoStop();
        // threads
        test.testKillTimeoutNoStop(false);
        // virtual threads
        test.testKillTimeoutNoStop(true);
    }

    protected void testKillTimeoutNoStop(boolean virtualThread) {
        msg(">> testKillTimeoutNoStop()");
        TenantConfiguration config = new TenantConfiguration();
        TenantContainer tenant = TenantContainer.create(config);
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicBoolean endInRoot = new AtomicBoolean(false);
        final Thread[] threads = new Thread[threadCount];
        final TenantContainer[] currenTenantContainers = new TenantContainer[threadCount];
        final TenantContainer[] runInRooTenantContainers = new TenantContainer[threadCount];
        try {
            for(int i = 0;i < threadCount; i++) {
                final int index = i;
                tenant.run(() -> {
                    Runnable op = () -> {
                        currenTenantContainers[index] = TenantContainer.current();
                        System.err.println("new thread-->" + Thread.currentThread() + " TenantContainer: " + TenantContainer.current());
                        TenantContainer.primitiveRunInRoot(()-> {
                            runInRooTenantContainers[index] = TenantContainer.current();
                            latch.countDown();
                            long l = 0;
                            while (!endInRoot.get()) {
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
                        long l = 0;
                        while (true) {
                            l++;
                            if(l % 1000 == 0) {
                                try {
                                    Thread.sleep(10);
                                } catch(InterruptedException ex) {
                                    // ignore
                                }
                            }
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
            Asserts.assertNull(runInRooTenantContainers[i]);
            Asserts.assertEquals(tenant, getInheritedTenantContainer(threads[i]));
            // attach to root
            Asserts.assertNull(getAttachedTenantContainer(threads[i]));
        }
        System.err.println("Before destroy!");
        assertTenantDestroySuccess(tenant, false, 16);
        msg("After destroy! ");
        Asserts.assertEquals(tenant.getState(), TenantState.STOPPING);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        boolean killingInWatchDog = false;
        Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (t.getClass().getName().equals("com.alibaba.tenant.TenantContainer$WatchDogThread")) {
                killingInWatchDog = true;
                break;
            }
        }

        Asserts.assertTrue(killingInWatchDog, "should kill in watch dog");

        for(int i = 0;i < threadCount; i++) {
            Asserts.assertTrue(threads[i].isAlive(), "Should be alive");
        }
        endInRoot.set(true);
        msg("start to be killed by watch dog");
        // wait watch dog thread to kill threads
        long startWaitTime = System.currentTimeMillis();
        while(tenant.getState() != TenantState.DEAD && (System.currentTimeMillis() - startWaitTime) < 30000) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
                fail();
            }
        }
        Asserts.assertEquals(tenant.getState(), TenantState.DEAD);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            e.printStackTrace();
            fail();
        }

        killingInWatchDog = false;
        threadSet = Thread.getAllStackTraces().keySet();
        for (Thread t : threadSet) {
            if (t.getClass().getName().equals("com.alibaba.tenant.TenantContainer$WatchDogThread")) {
                killingInWatchDog = true;
                break;
            }
        }

        Asserts.assertFalse(killingInWatchDog, "should not kill in watch dog");
        for(int i = 0;i < threadCount; i++) {
            Asserts.assertEquals(threads[i].getState(), Thread.State.TERMINATED, "Should be terminated after join()");
            Asserts.assertNull(threads[i].getThreadGroup());
            Asserts.assertNull(threads[i].getContextClassLoader());
            Asserts.assertNull(getInheritedTenantContainer(threads[i]));
            Asserts.assertNull(getAttachedTenantContainer(threads[i]));
        }
        msg("<< testKillTimeoutNoStop");
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