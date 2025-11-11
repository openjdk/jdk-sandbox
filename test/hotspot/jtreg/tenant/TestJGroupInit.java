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

import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import com.alibaba.tenant.TenantException;
import jdk.test.lib.TestUtils;
import jdk.test.lib.tenant.JGroupMirror;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;
import static jdk.test.lib.Asserts.*;
import static jdk.test.lib.tenant.JGroupMirror.*;

/*
 * @test
 * @summary Test initialization code of JGroup
 * @requires (vm.gc == "G1" | vm.gc == null) & !vm.graal.enabled
 * @library /test/lib
 * @build jdk.test.lib.tenant.JGroupMirror TestJGroupInit
 * @run main/othervm -XX:+MultiTenant -XX:+TenantCpuThrottling
 *                   --add-opens java.base/com.alibaba.tenant=ALL-UNNAMED
 *                   --illegal-access=permit
 *                   -XX:+UseG1GC -Dcom.alibaba.tenant.DebugJGroup=true
 *                   TestJGroupInit
 */
public class TestJGroupInit {

    private void testJGroupHierarchy() {
        TenantConfiguration config = new TenantConfiguration()
                .limitCpuShares(1024)
                //.limitHeap(64 * 1024 * 1024)
                .limitCpuCfs(1_000_000, 500_000);
        TenantContainer tenant = TenantContainer.create(config);
        try {
            JGroupMirror jgroup = JGroupMirror.jgroupOf(tenant); // tenant group
            assertNotNull(jgroup); // tenant group
            assertNotNull(jgroup.parent()); // jvm process group
            assertNotNull(jgroup.parent().parent()); // jdk group
            assertNotNull(jgroup.parent().parent().parent()); // root
            assertNull(jgroup.parent().parent().parent().parent()); // null
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    private void testRootGroupExists() {
        Path rootGroupPath = getRootCGroupPathOf("cpu");
        System.err.println("rootpath=" + rootGroupPath);
        assertTrue(Files.exists(rootGroupPath) && Files.isDirectory(rootGroupPath),
                rootGroupPath + " cannot be found or is not a directory");
        assertTrue(Files.exists(rootGroupPath.resolve("cpu.shares")), "cpu.shares cannot be found");
    }

    private void testNonRootGroupExists() {
        Path rootGroupPath = getRootCGroupPathOf("cpu");
        System.err.println("rootpath=" + rootGroupPath);
        assertTrue(Files.exists(rootGroupPath) && Files.isDirectory(rootGroupPath));

        int cpuShare = 1024;
        long heapLimit = 128 * 1024 * 1024;
        int cpuCfsPeriod = 1_000_000;
        int cpuCfsQuota = 500_000;

        TenantConfiguration config = new TenantConfiguration()
                .limitCpuShares(cpuShare)
                //.limitHeap(heapLimit)
                .limitCpuCfs(cpuCfsPeriod, cpuCfsQuota);
        TenantContainer tenant = TenantContainer.create(config);
        Path tenantPath = rootGroupPath.resolve("t" + tenant.getTenantId());
        assertTrue(Files.exists(tenantPath) && Files.isDirectory(tenantPath));

        // verify CGroup configurations
        {
            int actual = Integer.parseInt(getTenantConfig(tenant, "cpu.cfs_period_us"));
            assertEquals(cpuCfsPeriod, actual);

            actual = Integer.parseInt(getTenantConfig(tenant, "cpu.cfs_quota_us"));
            assertEquals(cpuCfsQuota, actual);

            actual = Integer.parseInt(getTenantConfig(tenant, "cpu.shares"));
            assertEquals(cpuShare, actual);
        }

        assertTrue(Files.exists(tenantPath) && Files.isDirectory(tenantPath),
                tenantPath + " cannot be found or is not a directory");
        Path path = tenantPath.resolve("cpu.shares");
        assertTrue(Files.exists(path), path + " cannot be found");

        try {
            tenant.run(()->{
                System.out.println("Hello from simple tenant");
            });
        } catch (TenantException e) {
            e.printStackTrace();
            fail();
        } finally {
            tenant.destroy();
        }

        assertTrue(!Files.exists(tenantPath));
        assertTrue(!Files.exists(tenantPath.resolve("cpu.shares")));
    }

    private void testGetSetNonExistConfigs() {
        TenantConfiguration config = new TenantConfiguration()
                .limitCpuShares(1024);
                //.limitHeap(128 * 1024 * 1024);
        TenantContainer tenant = TenantContainer.create(config);
        JGroupMirror jgroup = JGroupMirror.jgroupOf(tenant);

        // controller name does not have separator "."
        try {
            jgroup.setValue("NonExistConfigName", "456");
        } catch (IllegalArgumentException e) {
            // ignore
        }

        // controller name does have separator ".", and looks like normal
        try {
            jgroup.setValue("NonExist.controller", "456");
            fail();
        } catch (Throwable e) {
            // ignore
        }

        // controller name does not have separator "."
        try {
            String val = jgroup.getValue("NonExistConfigfsdjkfksdj123rName");
        } catch (IllegalArgumentException e) {
            // ignore
        }

        // controller name does have separator ".", and looks like normal
        try {
            String val = jgroup.getValue("NonExistConf.ksdj123rName");
            fail();
        } catch (Throwable e) {
        }

        jgroup.setValue("cpu.shares", "1023");
        assertEquals("1023", jgroup.getValue("cpu.shares"));
    }

    private void testGetSetJVMGroupControllers() {
        TenantConfiguration config = new TenantConfiguration();
        TenantContainer tenant = TenantContainer.create(config);
        JGroupMirror group = JGroupMirror.jgroupOf(tenant);
        assertTrue(group.parent() != null);

        JGroupMirror jvmGroup = group.parent();
        jvmGroup.setValue("cpu.shares", "1023");
        assertEquals(jvmGroup.getValue("cpu.shares"), "1023");
        jvmGroup.setValue("cpu.shares", "1024");
        assertEquals(jvmGroup.getValue("cpu.shares"), "1024");
    }

    public static void main(String[] args) {
        TestUtils.runWithPrefix("test",
                TestJGroupInit.class,
                new TestJGroupInit());
    }

    //---------utilities
    private static volatile Map<String, Path> rootCGroupPathMap;

    private static Path getRootCGroupPathOf(String controllerName) {
        if (rootCGroupPathMap == null) {
            synchronized (TestJGroupInit.class) {
                if (rootCGroupPathMap == null) {
                    rootCGroupPathMap = new HashMap<>();
                    String processName = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
                    assertNotNull(processName);
                    long pid = Long.parseLong(processName.split("@")[0]);
                    assertGreaterThan(pid, 0L);

                    try {
                        Files.readAllLines(Paths.get("/proc/mounts"))
                                .stream().forEach(line -> {
                            if (line.startsWith("cgroup ")) {
                                StringTokenizer st = new StringTokenizer(line);
                                st.nextToken();
                                String tok = st.nextToken();
                                assertNotNull(tok);
                                Path curPath = Paths.get(tok, "ajdk_multi_tenant", "" + pid);
                                if (line.contains("cpu,") || line.contains("cpu ")) {
                                    rootCGroupPathMap.put("cpu", curPath);
                                } else if (line.contains("cpuset")) {
                                    rootCGroupPathMap.put("cpuset", curPath);
                                } else if (line.contains("cpuacct")) {
                                    rootCGroupPathMap.put("cpuacct", curPath);
                                }
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                        fail();
                    }
                }
            }
        }
        return rootCGroupPathMap.get(controllerName);
    }
}
