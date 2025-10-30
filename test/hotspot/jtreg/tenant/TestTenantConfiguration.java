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

/* @test
 * @summary test TenantConfiguration facilities
 * @requires (vm.gc == "G1" | vm.gc == null) & !vm.graal.enabled
 * @library /test/lib
 * @run main/othervm -XX:+MultiTenant -XX:+UseG1GC -XX:+TenantCpuThrottling
 *                   --add-opens java.base/com.alibaba.tenant=ALL-UNNAMED
 *                   --illegal-access=permit
 *                   TestTenantConfiguration
 */

import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import jdk.test.lib.TestUtils;
import java.lang.reflect.Field;
import static jdk.test.lib.Asserts.*;

public class TestTenantConfiguration {

    private void testBasic() {
        try {
            TenantConfiguration config = new TenantConfiguration()
                    .limitCpuCfs(1_000_000, 500_000)
                    .limitCpuShares(1024);
                    //.limitHeap(64 * 1024 * 1024);
            assertNotNull(config);
            TenantContainer tenant = TenantContainer.create(config);
            assertNotNull(tenant);
            tenant.run(()-> {
                System.out.println("");
            });
            tenant.destroy();
        } catch (Throwable e) {
            e.printStackTrace();
            fail();
        }
    }

    private void testIllegalLimits() {
        Runnable illegalActions[] = {
                ()->{ new TenantConfiguration().limitCpuSet(""); },
                ()->{ new TenantConfiguration().limitCpuSet(null); },
                ()->{ new TenantConfiguration().limitCpuShares(-128); },
                //()->{ new TenantConfiguration().limitHeap(-64 * 1024 * 1024); },
                ()->{ new TenantConfiguration().limitCpuCfs(-123, 10_000_000); },
                ()->{ new TenantConfiguration().limitCpuCfs(1_000_000, -2); },
                ()->{ new TenantConfiguration().limitCpuCfs(999, -1); }, // lower bound of period
                ()->{ new TenantConfiguration().limitCpuCfs(1_000_001, -1); }, // upper bound of period
                ()->{ new TenantConfiguration().limitCpuCfs(10_000, 999); }, // lower bound of quota
        };
        for (Runnable action : illegalActions) {
            try {
                action.run();
                fail("should throw IllegalArgumentException");
            } catch (IllegalArgumentException e) {
                // expected
            } catch (Throwable e) {
                e.printStackTrace();
                fail("Should not throw any other exceptions");
            }
        }

    }

    private void testValidLimits() {
        Runnable validActions[] = {
                ()->{ new TenantConfiguration().limitCpuCfs(1_000_000, 2_000_000); },
                ()->{ new TenantConfiguration().limitCpuCfs(100_000, 10_000); },
                ()->{ new TenantConfiguration().limitCpuCfs(100_000, -1); }
        };
        for (Runnable action : validActions) {
            try {
                action.run();
            } catch (Throwable e) {
                e.printStackTrace();
                fail("Should not throw any exceptions");
            }
        }
    }

    private void testGetSetLimits() {
        TenantConfiguration config = new TenantConfiguration();

        int cpuShare = 1024;
        config.limitCpuShares(1024);
        assertEquals(config.getCpuShares(), cpuShare);

        String cpuSet = "0-1";
        config.limitCpuSet(cpuSet);
        assertEquals(cpuSet, config.getCpuSet());

        int cpuCfsQuota = 10_000;
        int cpuCfsPeriod = 10_000;
        config.limitCpuCfs(cpuCfsPeriod, cpuCfsQuota);
        int percent = config.getMaxCpuPercent();
        assertEquals(percent, 100);
    }

    private void testUniqueConfigWhenCreatingContainer() {
        TenantConfiguration config = new TenantConfiguration().limitCpuShares(1024);
        TenantContainer tenant = TenantContainer.create(config);
        try {
            Field configField = TenantContainer.class.getDeclaredField("configuration");
            configField.setAccessible(true);
            TenantConfiguration configGet = (TenantConfiguration) configField.get(tenant);
            assertTrue(configGet == config);
        } catch (Exception e) {
            e.printStackTrace();
            fail();
        }
    }

    public static void main(String[] args) {
        TestUtils.runWithPrefix("test",
                TestTenantConfiguration.class,
                new TestTenantConfiguration());
    }
}
