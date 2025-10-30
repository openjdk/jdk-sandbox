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
 *                               -Dcom.alibaba.tenant.ShutdownSTWSoftLimit=3000 -Dcom.alibaba.tenant.DebugTenantShutdown=true -Dcom.alibaba.tenant.PrintStacksOnTimeoutDelay=30000
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

public class TestPoolPredicateKillThread {

	//------------------------- Testing entry ----------------------------------------------
	public static void main(String[] args) {
		TestPoolPredicateKillThread test = new TestPoolPredicateKillThread();
		test.testThreadPoolWithPredicate();
	}

	public void testThreadPoolWithPredicate() {
		TenantConfiguration config = new TenantConfiguration();
        final TenantContainer tenant = TenantContainer.create(config);
		CountDownLatch cdl = new CountDownLatch(2);
		SharedSecrets.getTenantAccess().setNewPoolTenantInheritancePredicate(tuple -> {
			cdl.countDown();
			if (tuple.executorService() == null) {
				return true;
			}
			return false;
		});
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
		ExecutorService[] executors = new ExecutorService[2];
		Thread[] threads = new Thread[2];
		try {
			tenant.run(() -> {
				executors[0] = Executors.newVirtualThreadPerTaskExecutor();
				executors[0].submit(()-> {
					threads[0] = Thread.currentThread();
				});
				executors[1] = Executors.newFixedThreadPool(1);
				executors[1].submit(()-> {
					threads[1] = Thread.currentThread();
				});
			});
		} catch (TenantException e) {
			fail();
		}

		try {
			cdl.await(3, TimeUnit.SECONDS);
		} catch (InterruptedException interruptedException) {
			fail();
		}
		Asserts.assertEquals(tenant, getAttachedTenantContainer(threads[0]));
		Asserts.assertEquals(null, getAttachedTenantContainer(threads[1]));
	}

}
