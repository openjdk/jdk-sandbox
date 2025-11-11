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
 * @summary Test function of killing tenants' virtual threads
 * @library /test/lib
 * @run main/othervm/timeout=100 -Xbootclasspath/a:. -XX:+UnlockDiagnosticVMOptions -XX:+IgnoreUnrecognizedVMOptions
 *                               -XX:+TraceTenantKillThreads -XX:+MultiTenant -XX:+TenantThreadStop -XX:+TenantCpuAccounting
 *                               -XX:+UnlockExperimentalVMOptions -XX:+VMContinuations
 *                               -Dcom.alibaba.tenant.ShutdownSTWSoftLimit=10000 -Dcom.alibaba.tenant.DebugTenantShutdown=true -Dcom.alibaba.tenant.PrintStacksOnTimeoutDelay=30000
 *                               --add-opens=java.base/java.lang=ALL-UNNAMED TestKillVirtualThread
 *
 */

import java.util.concurrent.locks.LockSupport;

/**
 * Below testcase tries to test virtual thread killing feature of MultiTenant JDK for following scenarios
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
public class TestKillVirtualThread extends TestKillThread {

    //------------------------- Testing entry ----------------------------------------------
    public static void main(String[] args) {

        TestKillThread test = new TestKillThread();
        test.testWaitingThreads(true);
        test.testNotKillRootThreads(true);
        test.testNotKillTenantRunInRootThread(true);
        test.testNotKillShutDownMaskedThread(true);
    }
}
