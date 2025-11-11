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
 * @summary Test function of virtual threads without tenant
 * @library /test/lib
 * @run main/othervm/timeout=100 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                               -XX:+UnlockExperimentalVMOptions -XX:+VMContinuations
 *                               --add-opens=java.base/java.lang=ALL-UNNAMED TestVirtualThreadExecutorWithoutTenant
 *
 */

import com.alibaba.tenant.TenantVirtualThreadExecutorService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.test.lib.Asserts;

public class TestVirtualThreadExecutorWithoutTenant {


    private static synchronized void msg(String s) {

        System.err.println("[" + LocalDateTime.now() + "] " + s);
    }

    private static void fail() {

        msg("Failed thread is :" + Thread.currentThread());
        Asserts.assertTrue(false, "Failed!");
    }


    public static void main(String[] args) {

        testMaxParallelReject();
        testMaxParallelRejectWithHandler();
        testRejectAfterShutdown();
        testRejectAfterShutdownWithHandler();
        testRunBasic();
        testShutdown();
        testShutdownNow();
    }

    private static void testRunBasic() {

        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService("testPool", 3,
            true);

        int[] values = new int[3];
        for (int i = 0; i < 3; i++) {
            final int index = i + 1;
            executorService.submit(() -> {
                values[index - 1] = index;
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

    private static void testShutdown() {

        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService("testPool", 3,
            true);

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

        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService("testPool", 3,
            true);

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

        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService("testPool", 3,
            true);

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

        // caller run policy when rejected
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService("testPool", 3, true,
            (r, excutor) -> r.run());

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

        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService("testPool", 3,
            true);

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

        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService("testPool", 3, true,
            (r, excutor) -> r.run());

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
