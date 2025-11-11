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

import jdk.test.lib.process.ProcessTools;
import jdk.test.lib.process.OutputAnalyzer;
import java.lang.reflect.Method;
import com.alibaba.tenant.TenantConfiguration;
import com.alibaba.tenant.TenantContainer;
import com.alibaba.tenant.TenantException;
import com.alibaba.tenant.TenantGlobals;
import com.alibaba.tenant.TenantState;

/* @test
 * @summary unit tests to verify the tenant related classes are preloaded
 * @library /test/lib
 * @compile TestTenantClassPreLoad.java
 * @run main/othervm  TestTenantClassPreLoad
 */
public class TestTenantClassPreLoad {
    public static void main(String[] args) throws Exception {
        TestTenantClassPreLoad test = new TestTenantClassPreLoad();
        test.testClassLoadingOutputWithMT();
    }

    /**
     * Verify the output of -XX:+TraceClassLoading when the -XX:+MultiTenant is present.
     * @throws Exception when the output is not correct
     */
    void testClassLoadingOutputWithMT() throws Exception {
        System.out.println("TestTenantClassPreLoad.testClassLoadingOutputWithMT:");
        Method createBuilderMethod;
        try {
            // jdk 21.0.4
            createBuilderMethod = ProcessTools.class.getDeclaredMethod("createTestJavaProcessBuilder", String[].class);
        } catch(Exception ex) {
            // jdk 21.0.3
            createBuilderMethod = ProcessTools.class.getDeclaredMethod("createJavaProcessBuilder", String[].class);
        }

        String[] params = new String[] {"-XX:+MultiTenant", "-Xlog:class+load=info", "-version"};
        // jdk21 use -Xlog:class+load=info instead of -XX:+TraceClassLoading in jdk11
        ProcessBuilder pb = (ProcessBuilder) createBuilderMethod.invoke(null, (Object) params);
        OutputAnalyzer output = new OutputAnalyzer(pb.start());

        /* make sure the tenant related classes are loaded. */
        output.shouldContain(TenantGlobals.class.getCanonicalName());
        output.shouldContain(TenantConfiguration.class.getCanonicalName());
        output.shouldContain(TenantState.class.getCanonicalName());
        output.shouldContain(TenantException.class.getCanonicalName());
        output.shouldContain(TenantContainer.class.getCanonicalName());
        output.shouldHaveExitValue(0);
    }
}
