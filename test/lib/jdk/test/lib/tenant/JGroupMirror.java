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

package jdk.test.lib.tenant;

import com.alibaba.tenant.TenantContainer;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static jdk.test.lib.Asserts.*;

//
// Use reflection to operate JGroup class
// requires extra VM options:
//      --add-opens java.base/com.alibaba.tenant=ALL-UNNAMED --illegal-access=permit
//
public class JGroupMirror {
    private static final Class JGROUP_CLASS;
    private static final Field TENANT_JGROUP_FIELD;
    private static final Field PARENT_GROUP_FIELD;
    private static final Method SET_VALUE_METHOD;
    private static final Method GET_VALUE_METHOD;
    private static final Method ROOT_PATH_OF;
    public static final String JVM_GROUP_PATH;

    static {

        Field parentGroupField = null;
        Field tenantJGroupField = null;
        Class jgroupClass = null;
        String jvmGroupPath = null;

        try {
            jgroupClass = Class.forName("com.alibaba.tenant.JGroup");
            parentGroupField = jgroupClass.getDeclaredField("parentGroup");
            parentGroupField.setAccessible(true);

            Field jvmGroupPathField = jgroupClass.getDeclaredField("JVM_GROUP_PATH");
            jvmGroupPathField.setAccessible(true);
            jvmGroupPath = (String) jvmGroupPathField.get(null);

            tenantJGroupField = TenantContainer.class.getDeclaredField("jgroup");
            tenantJGroupField.setAccessible(true);
        } catch (Throwable e) {
            e.printStackTrace();
            fail();
        }
        JGROUP_CLASS = jgroupClass;
        PARENT_GROUP_FIELD = parentGroupField;
        TENANT_JGROUP_FIELD = tenantJGroupField;
        SET_VALUE_METHOD = getDeclaredMethod(JGROUP_CLASS, "setValue", String.class, String.class);
        GET_VALUE_METHOD = getDeclaredMethod(JGROUP_CLASS, "getValue", String.class);
        ROOT_PATH_OF = getDeclaredMethod(JGROUP_CLASS, "rootPathOf", String.class);
        JVM_GROUP_PATH = jvmGroupPath;
    }

    private Object jgroup;
    private JGroupMirror parent;

    private JGroupMirror(TenantContainer tenant) {
        if (tenant != null) {
            try {
                jgroup = TENANT_JGROUP_FIELD.get(tenant);
            } catch (Exception e) {
                e.printStackTrace();
                fail();
            }
        }
    }

    public JGroupMirror parent() {
        if (parent == null && jgroup != null) {
            try {
                Object jg = PARENT_GROUP_FIELD.get(jgroup);
                if (jg != null) {
                    parent = new JGroupMirror(null);
                    parent.jgroup = jg;
                }
            } catch (Exception e) {
                e.printStackTrace();
                fail();
            }
        }
        return parent;
    }

    public void setValue(String config, String value) throws IllegalArgumentException {
        try {
            SET_VALUE_METHOD.invoke(jgroup, config, value);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            fail();
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw (IllegalArgumentException)e.getCause();
            }
            e.printStackTrace();
            fail();
        }
    }

    public String getValue(String config) throws IllegalArgumentException {
        try {
            return (String)GET_VALUE_METHOD.invoke(jgroup, config);
        } catch (IllegalAccessException e) {
            e.printStackTrace();
            fail();
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof IllegalArgumentException) {
                throw (IllegalArgumentException)e.getCause();
            }
            e.printStackTrace();
            fail();
        }
        return null;
    }

    public static JGroupMirror jgroupOf(TenantContainer tenant) {
        return new JGroupMirror(tenant);
    }

    public static String rootPathOf(String controllerName) {
        try {
            return (String)ROOT_PATH_OF.invoke(null, controllerName);
        } catch (Throwable e) {
            fail();
        }
        return null;
    }

    public static String getTenantConfig(TenantContainer tenant, String configName) {
        String controllerName = configName.split("\\.")[0];
        Path configPath = Paths.get(rootPathOf(controllerName),
                JVM_GROUP_PATH,
                "t" + tenant.getTenantId(),
                configName);
        try {
            return new String(Files.readAllBytes(configPath)).trim();
        } catch (IOException e) {
            e.printStackTrace();
            fail();
        }
        return null;
    }

    private static Object parentGroupOf(Object jgroup) throws Exception {
        assertNotNull(jgroup);
        Object res = PARENT_GROUP_FIELD.get(jgroup);
        return res;
    }

    private static Method getDeclaredMethod(Class clazz, String name, Class ... args) {
        Method m = null;
        try {
            m = clazz.getDeclaredMethod(name, args);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
            fail();
        }
        return m;
    }
}
