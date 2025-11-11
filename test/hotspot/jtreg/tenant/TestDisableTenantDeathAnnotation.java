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

import java.io.File;
import java.lang.reflect.Method;
import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import com.alibaba.tenant.TenantException;
import com.alibaba.tenant.TenantGlobals;
import com.alibaba.tenant.TenantState;

/* @test
 * @summary unit tests to verify the DisableTenantDeath annotation
 * @library /test/lib
 * @requires vm.debug == true
 * @compile TestDisableTenantDeathAnnotation.java
 *          DisableTenantDeathClassLoad.java
 * @run main/othervm TestDisableTenantDeathAnnotation
 */
public class TestDisableTenantDeathAnnotation {
    public static void main(String[] args) throws Exception {
        TestDisableTenantDeathAnnotation test = new TestDisableTenantDeathAnnotation();
        test.testVerifyDisableTenantDeathAnnotation();
    }

    /**
     * Verify the output of -XX:+TraceClassLoading when the -XX:+MultiTenant is present.
     * @throws Exception when the output is not correct
     */
    void testVerifyDisableTenantDeathAnnotation() throws Exception {
        System.out.println("TestDisableTenantDeathAnnotation.testVerifyDisableTenantDeathAnnotation:");
        Method createBuilderMethod;
        try {
            // jdk 21.0.4
            createBuilderMethod = ProcessTools.class.getDeclaredMethod("createTestJavaProcessBuilder", String[].class);
        } catch(Exception ex) {
            // jdk 21.0.3
            createBuilderMethod = ProcessTools.class.getDeclaredMethod("createJavaProcessBuilder", String[].class);
        }

        // jdk21 use -Xlog:class+load=info instead of -XX:+TraceClassLoading in jdk11
        String[] params = { "-XX:+MultiTenant", "-XX:+TenantThreadStop", "-XX:+TraceTenantThreadStop",
        "--add-opens=java.base/java.lang=ALL-UNNAMED", "--add-opens=java.base/java.util.concurrent.locks=ALL-UNNAMED",
        // -Xshare:off close cds, parse all classes again
        "--add-opens=java.base/java.util.concurrent=ALL-UNNAMED", "-Xshare:off", "DisableTenantDeathClassLoad"};
        ProcessBuilder pb = (ProcessBuilder) createBuilderMethod.invoke(null, (Object) params);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        /* make sure the tenant related classes are loaded. */
        output.shouldContain("DisableTenantDeath:java/lang/VirtualThread");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/ReentrantLock");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/ReentrantLock$NonfairSync");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/ReentrantLock$FairSync");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/ReentrantReadWriteLock");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/ReentrantReadWriteLock$FairSync");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/ReentrantReadWriteLock$NonfairSync");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/ReentrantReadWriteLock$ReadLock");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/ReentrantReadWriteLock$WriteLock");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/StampedLock");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/StampedLock$ReadLockView");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/StampedLock$WriteLockView");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/StampedLock$ReadWriteLockView");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/StampedLock$ReaderNode");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/StampedLock$WriterNode");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/AbstractQueuedSynchronizer$ConditionObject");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/AbstractQueuedSynchronizer$ConditionNode");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/AbstractQueuedSynchronizer$ExclusiveNode");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/AbstractQueuedSynchronizer$SharedNode");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/AbstractQueuedLongSynchronizer$ConditionObject");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/AbstractQueuedLongSynchronizer$ConditionNode");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/AbstractQueuedLongSynchronizer$ExclusiveNode");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/locks/AbstractQueuedLongSynchronizer$SharedNode");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/CountDownLatch$Sync");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/Semaphore$NonfairSync");
        output.shouldContain("DisableTenantDeath:java/util/concurrent/Semaphore$FairSync");

        output.shouldHaveExitValue(0);
    }
}
