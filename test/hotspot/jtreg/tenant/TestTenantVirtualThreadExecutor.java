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
 * @summary Test function of tenants' virtual threads
 * @requires vm.compMode != "Xcomp"
 * @library /test/lib
 * @run main/othervm/timeout=100 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                               -XX:+UnlockExperimentalVMOptions -XX:+VMContinuations
 *                               -XX:+MultiTenant
 *                               --add-opens=java.base/java.lang=ALL-UNNAMED TestTenantVirtualThreadExecutor
 *
 */

import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import com.alibaba.tenant.TenantVirtualThreadExecutorService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.test.lib.Asserts;

public class TestTenantVirtualThreadExecutor {

    private static Field inheritedTenantContainerField;
    private static Method currentCarrierThreadMethod;

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

    private static Thread getCurrentCarrierThread() {
        if (currentCarrierThreadMethod != null) {
            try {
                return (Thread) currentCarrierThreadMethod.invoke(null);
            } catch (Exception e) {
                e.printStackTrace();
                fail();
            }
        }
        return null;
    }

    private static synchronized void msg(String s) {

        System.err.println("[" + LocalDateTime.now() + "] " + s);
    }

    private static void fail() {

        msg("Failed thread is :" + Thread.currentThread());
        Asserts.assertTrue(false, "Failed!");
    }

    static {
        try {
            inheritedTenantContainerField = Thread.class.getDeclaredField("inheritedTenantContainer");
            inheritedTenantContainerField.setAccessible(true);

            currentCarrierThreadMethod = Thread.class.getDeclaredMethod("currentCarrierThread");
            currentCarrierThreadMethod.setAccessible(true);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    public static void main(String[] args) {

        testMaxParallelReject();
        testMaxParallelRejectWithHandler();
        testRejectAfterShutdown();
        testRejectAfterShutdownWithHandler();
        testThreadPerTaskExecutorRunInRoot();
        testRunInRoot();
        testTenantNullPointer();
        testRootExecutorCreateInTenant();
        testTenantExecutorCreateInRoot();
        testThreadPerTaskExecutorRunInTenant();
        testRunInTenant();
        testRunInTenantInherited();
        testThreadPerTaskExecutorCrossTenant();
        testCallNewThreadFromOtherTenant();
        testShutdown();
        testShutdownNow();
    }

    private static void testRootExecutorCreateInTenant() {
        final TenantContainer container2 = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService[] executorServiceArray = new TenantVirtualThreadExecutorService[1];
        try {
            container2.run(()-> {
                executorServiceArray[0] = new TenantVirtualThreadExecutorService("testPool", 3, true);
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
        TenantVirtualThreadExecutorService executorService = executorServiceArray[0];
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            try {
                container2.run(() -> {
                    executorService.submit(() -> {
                        Asserts.assertNull(getInheritedTenantContainer(Thread.currentThread()));
                        Asserts.assertNull(getInheritedTenantContainer(getCurrentCarrierThread()));
                        values[index - 1] = index;
                    });
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                fail();
            }
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testTenantExecutorCreateInRoot() {
        final TenantContainer container2 = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService[] executorServiceArray = new TenantVirtualThreadExecutorService[1];
        TenantContainer.primitiveRunInRoot(() -> {
            executorServiceArray[0] = new TenantVirtualThreadExecutorService(container2, "testPool", 3);
        });
        TenantVirtualThreadExecutorService executorService = executorServiceArray[0];
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            TenantContainer.primitiveRunInRoot(() -> {
                executorService.submit(() -> {
                    Asserts.assertEquals(container2, getInheritedTenantContainer(Thread.currentThread()));
                    Asserts.assertEquals(container2, getInheritedTenantContainer(getCurrentCarrierThread()));
                    values[index - 1] = index;
                });
            });
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testRunInRoot() {
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService("testPool", 3,
            true);
        final TenantContainer container2 = TenantContainer.create(new TenantConfiguration());

        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            try {
                container2.run(() -> {
                    executorService.submit(() -> {
                        Asserts.assertNull(getInheritedTenantContainer(Thread.currentThread()));
                        Asserts.assertNull(getInheritedTenantContainer(getCurrentCarrierThread()));
                        values[index - 1] = index;
                    });
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                fail();
            }
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testThreadPerTaskExecutorRunInRoot() {
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        final TenantContainer container2 = TenantContainer.create(new TenantConfiguration());
        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            try {
                container2.run(() -> {
                    executorService.submit(() -> {
                        Asserts.assertNull(getInheritedTenantContainer(Thread.currentThread()));
                        Asserts.assertNull(getInheritedTenantContainer(getCurrentCarrierThread()));
                        values[index - 1] = index;
                    });
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                fail();
            }
        }

        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testTenantNullPointer() {

        Throwable th = null;
        try {
            new TenantVirtualThreadExecutorService(null, "testPool", 3);
        } catch (Throwable t) {
            th = t;
        }
        Asserts.assertTrue(th != null && th instanceof NullPointerException);

    }

    private static void testThreadPerTaskExecutorRunInTenant() {

        final TenantContainer container = TenantContainer.create(new TenantConfiguration());
        ExecutorService[] executorServices = new ExecutorService[1];
        try {
            container.run(() -> {
                executorServices[0] = Executors.newVirtualThreadPerTaskExecutor();
            });
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }
        ExecutorService executorService = executorServices[0];

        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            TenantContainer.primitiveRunInRoot(() -> {
                executorService.submit(() -> {
                    Asserts.assertEquals(container, getInheritedTenantContainer(Thread.currentThread()));
                    Asserts.assertEquals(container, getInheritedTenantContainer(getCurrentCarrierThread()));
                    values[index - 1] = index;
                });
            });
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testRunInTenant() {

        final TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", 3);

        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            TenantContainer.primitiveRunInRoot(() -> {
                executorService.submit(() -> {
                    Asserts.assertEquals(container, getInheritedTenantContainer(Thread.currentThread()));
                    Asserts.assertEquals(container, getInheritedTenantContainer(getCurrentCarrierThread()));
                    values[index - 1] = index;
                });
            });
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testRunInTenantInherited() {

        final TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService[] executorServiceArray = new TenantVirtualThreadExecutorService[1];
        try {
            // inherit tenant container in TenantVirtualThreadExecutorService constructor
            container.run(() -> {
                executorServiceArray[0] = new TenantVirtualThreadExecutorService("testPool", 3,
                    false);
            });
        } catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }

        TenantVirtualThreadExecutorService executorService = executorServiceArray[0];

        int[] values = new int[3];
        try {
            for (int i = 0; i < 3; i++) {
                final int index = i + 1;
                TenantContainer.primitiveRunInRoot(() -> {
                    executorService.submit(() -> {
                        Asserts.assertEquals(container, getInheritedTenantContainer(Thread.currentThread()));
                        Asserts.assertEquals(container, getInheritedTenantContainer(getCurrentCarrierThread()));
                        values[index - 1] = index;
                    });
                });
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testThreadPerTaskExecutorCrossTenant() {

        final TenantContainer callerContainer = TenantContainer.create(new TenantConfiguration());

        final TenantContainer container = TenantContainer.create(new TenantConfiguration());
        ExecutorService[] executorServices = new ExecutorService[1];
        try {
            container.run(() -> {
                executorServices[0] = Executors.newVirtualThreadPerTaskExecutor();
            });
        } catch (Throwable t) {
            t.printStackTrace();
            fail();
        }
        ExecutorService executorService = executorServices[0];

        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            try {
                callerContainer.run(() -> {
                    executorService.submit(() -> {
                        Asserts.assertEquals(container, getInheritedTenantContainer(Thread.currentThread()));
                        Asserts.assertEquals(container, getInheritedTenantContainer(getCurrentCarrierThread()));
                        values[index - 1] = index;
                    });
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                fail();
            }
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testCallNewThreadFromOtherTenant() {

        final TenantContainer callerContainer = TenantContainer.create(new TenantConfiguration());

        final TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", 3);

        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            try {
                callerContainer.run(() -> {
                    executorService.submit(() -> {
                        Asserts.assertEquals(container, getInheritedTenantContainer(Thread.currentThread()));
                        Asserts.assertEquals(container, getInheritedTenantContainer(getCurrentCarrierThread()));
                        values[index - 1] = index;
                    });
                });
            } catch (Exception ex) {
                ex.printStackTrace();
                fail();
            }
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(500, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }


    private static void testShutdown() {

        TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", 3);

        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            executorService.submit(() -> {
                try {
                    Thread.sleep(500);
                    values[index - 1] = index;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        executorService.shutdown();
        try {
            executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertTrue(executorService.isShutdown());
        Asserts.assertTrue(executorService.isTerminated());
        Asserts.assertEquals(values[0], 1);
        Asserts.assertEquals(values[1], 2);
        Asserts.assertEquals(values[2], 3);
    }

    private static void testShutdownNow() {

        TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", 3);

        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            executorService.submit(() -> {
                try {
                    Thread.sleep((index - 1) * 1000);
                    values[index - 1] = index;
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        try {
            Thread.sleep(300);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        List<Runnable> tasks = executorService.shutdownNow();
        try {
            executorService.awaitTermination(1000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertTrue(executorService.isShutdown());
        Asserts.assertTrue(executorService.isTerminated());
        Asserts.assertEquals(0, tasks.size());
        Asserts.assertEquals(values[0], 1);
        // thread 2 and 3 was interrupted
        Asserts.assertEquals(values[1], 0);
        Asserts.assertEquals(values[2], 0);
    }

    private static void testMaxParallelReject() {

        TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", 3);

        for (int i = 0; i < 3; i++) {
            executorService.submit(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        Throwable th = null;
        try {
            executorService.submit(() -> {
            });
        } catch (Throwable t) {
            th = t;
        }
        Asserts.assertTrue(th != null && th instanceof RejectedExecutionException);
    }

    private static void testMaxParallelRejectWithHandler() {

        TenantContainer container = TenantContainer.create(new TenantConfiguration());
        // caller run policy when rejected
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", 3, (r, excutor) -> r.run());

        final String name = Thread.currentThread().getName();

        for (int i = 0; i < 3; i++) {
            executorService.submit(() -> {
                Asserts.assertNotEquals(Thread.currentThread().getName(), name);
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });
        }
        AtomicBoolean checked = new AtomicBoolean();
        try {
            executorService.submit(() -> {
                // caller run policy check
                Asserts.assertEquals(Thread.currentThread().getName(), name);
                checked.set(true);
            }).get();
        } catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertTrue(checked.get());
    }

    private static void testRejectAfterShutdown() {

        TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", 3);

        executorService.shutdown();
        Throwable th = null;
        try {
            executorService.submit(() -> {
            });
        } catch (Throwable t) {
            th = t;
        }
        Asserts.assertTrue(th != null && th instanceof RejectedExecutionException);
    }

    private static void testRejectAfterShutdownWithHandler() {

        TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", 3, (r, excutor) -> r.run());

        executorService.shutdown();

        final String name = Thread.currentThread().getName();
        AtomicBoolean checked = new AtomicBoolean();
        try {
            executorService.submit(() -> {
                // caller run policy check
                Asserts.assertEquals(Thread.currentThread().getName(), name);
                checked.set(true);
            }).get();
        } catch (Exception ex) {
            ex.printStackTrace();
            fail();
        }
        Asserts.assertTrue(checked.get());
    }
}
