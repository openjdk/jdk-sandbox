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
 * @library /test/lib
 * @run main/othervm/timeout=100 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                               -XX:+UnlockExperimentalVMOptions -XX:+VMContinuations
 *                               -XX:+MultiTenant
 *                               --add-opens=java.base/java.lang=ALL-UNNAMED TestTenantVirtualThreadMonitor
 *
 */

import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import com.alibaba.tenant.TenantVirtualThreadExecutorService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import jdk.test.lib.Asserts;

public class TestTenantVirtualThreadMonitor {

    private static synchronized void msg(String s) {

        System.err.println("[" + LocalDateTime.now() + "] " + s);
    }

    private static void fail() {

        msg("Failed thread is :" + Thread.currentThread());
        Asserts.assertTrue(false, "Failed!");
    }

    public static void main(String[] args) {

        testActiveVirtualThreadCounter();
    }

    private static void testActiveVirtualThreadCounter() {

        int threadCount = 100;
        final TenantContainer container = TenantContainer.create(new TenantConfiguration());
        TenantVirtualThreadExecutorService executorService = new TenantVirtualThreadExecutorService(container,
            "testPool", threadCount);

        AtomicBoolean stop = new AtomicBoolean(false);
        final CountDownLatch ready = new CountDownLatch(threadCount);
        final CountDownLatch latch = new CountDownLatch(threadCount);
        for (int i = 0;i < threadCount;i++) {
            executorService.submit(() -> {
                ready.countDown();

                while(!stop.get()) {
                    for(int j = 0; j < 10000; j++) {
                    }
                    try {
                        Thread.sleep(10);
                    } catch(InterruptedException e) {
                    }
                }
                latch.countDown();
            });
        }

        try {
            ready.await();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }

        Asserts.assertEquals((long)threadCount, container.getActiveVirtualThreadCount());

        stop.set(true);
        try {
            latch.await();
            executorService.shutdown();
            executorService.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }

        Asserts.assertEquals(0l, container.getActiveVirtualThreadCount());
    }
}
