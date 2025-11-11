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

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.LocalDateTime;
import com.alibaba.tenant.TenantContainer;
import jdk.test.lib.Asserts;
public class BaseTenantKillTest {

    protected static void fail() {
        msg("Failed thread is :" + Thread.currentThread());
        Asserts.assertTrue(false, "Failed!");
    }

    protected static synchronized void msg(String s) {
        System.err.println("[" + LocalDateTime.now() + "] " + s);
    }

    protected static void dumpWhenTimeout(Runnable r, int timeoutSeconds) {
        // dump if kill timeout
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(timeoutSeconds * 1000);
            } catch (InterruptedException ex) {
                return;
            }
            dumpHeap();
        });
        t.start();
        r.run();
        t.interrupt();
        try {
            t.join();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
    }

    // assert tenantContainer.destroy() success, otherwise dump hean
    protected static void assertTenantDestroySuccess(TenantContainer tenantContainer, boolean expectedResult, int timeoutSeconds) {
        // dump if kill timeout
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(timeoutSeconds * 1000);
            } catch (InterruptedException ex) {
                return;
            }
            dumpHeap();
        });
        t.start();
        boolean success = tenantContainer.destroy();
        t.interrupt();
        try {
            t.join();
        } catch (InterruptedException ex) {
            ex.printStackTrace();
            fail();
        }
        // dump if kill result is not expected
        if (success != expectedResult) {
            dumpHeap();
        }
        Asserts.assertEquals(success, expectedResult , expectedResult ? "tenant destroy all threads failed" : "tenant destroy should fail");
    }

    protected static void dumpHeap() {
        try {
            String usrHome = System.getProperty("user.home");
            final File dumpPath = new File(usrHome, "jtreg-tenant/");
            if (!dumpPath.exists()) {
                dumpPath.mkdirs();
            }
            RuntimeMXBean runtimeMXBean = ManagementFactory.getRuntimeMXBean();
            final int pid = Integer.valueOf(runtimeMXBean.getName().split("@")[0]).intValue();
            String javaHome = System.getProperty("java.home");
            Process p = Runtime.getRuntime()
                .exec(
                    new String[]{javaHome + "/bin/jmap",
                        "-dump:live,format=b,file=" + dumpPath.getAbsolutePath() + "/heap_" + pid + ".bin",
                        String.valueOf(pid)});
            InputStream inputStream = p.getInputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            while ((line = br.readLine()) != null) {
                msg(line);
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }
}