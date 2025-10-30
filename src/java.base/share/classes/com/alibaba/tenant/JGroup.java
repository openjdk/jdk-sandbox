/*
 * Copyright (c) 2025, Alibaba Group Holding Limited. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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

package com.alibaba.tenant;

import sun.security.action.GetPropertyAction;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * JGroup is a java mirror of Linux control group
 */
class JGroup {

    // to dispatch calls to libcgroup and file system
    private static NativeDispatcher dispatcher;

    /*
     * The cgroup hierarchy is:
     *
     * OS/Container ROOT group
     * |
     * |__ JDK Group
     *     |
     *     |__ JVM group of process 1
     *     |    |___ TenantContainer1's group
     *     |    |___ TenantContainer2's group
     *     |    |___ ...
     *     |
     *     |__ JVM group of process 2
     *     |
     *     |__ ...
     *
     * The cgroup path identifying TenantContainer 't0' is in following format
     * /<cgroup mountpoint>/<controller name>/<optional user specified ROOT path/<JDK path>/<JVM path>/<TenantContainer group path>
     *
     * e.g.
     * /sys/fs/cgroup/cpuset,cpu,cpuacct/ajdk_multi_tenant/12345/t0
     * /sys/fs/cgroup/cpuset/system.slice/docker-0fdsnjk23njkfnkfnwe/ajdk_multi_tenant/12345/t0
     *
     */

    // OS/Container ROOT group, will be appended to each cgroup controller's root path
    static final String ROOT_GROUP_PATH;

    // JDK group must be initialized before running any JVM process with CPU throttling/accounting enabled
    static final String JDK_GROUP_PATH;

    // Current JVM process's cgroup path, parent of all non-ROOT TenantContainers' groups
    static final String JVM_GROUP_PATH;

    // constants
    static final int DEFAULT_WEIGHT = 1024;

    static final int DEFAULT_MAXCPU = 100;

    // unique top level groups
    // rootGroup and jdkGroup are static, just to serve 'parent' API
    private static JGroup rootGroup;
    private static JGroup jdkGroup;
    // jvm group is the top level active cgroup
    private static JGroup jvmGroup;

    static JGroup jvmGroup() {
        return jvmGroup;
    }

    static JGroup jdkGroup() {
        return jdkGroup;
    }

    static JGroup rootGroup() {
        return rootGroup;
    }

    /*
     * CGroup path of this cgroup
     */
    private String groupPath;

    /*
     * parent group
     */
    private JGroup parentGroup;

    // Create a JGroup and initialize necessary underlying cgroup configurations
    JGroup(TenantContainer tenant) {
        if (tenant == null) {
            throw new IllegalArgumentException("Must provide a non-null TenantContainer to start with");
        } else {
            init(tenant, getTenantGroupPath(tenant));
        }
    }

    //
    // only directly called by initializeJGroupClass to initialize jvmGroup and jdkGroup
    //
    private JGroup(TenantContainer tenant, String path) {
        init(tenant, path);
    }

    private void init(TenantContainer tenant, String path) {
        this.groupPath = path;
        if (tenant == null) {
            this.parentGroup = null;
            if (JVM_GROUP_PATH.equals(path)) {
                this.parentGroup = jdkGroup();
                dispatcher.initSystemGroup(this);
            } else if (JDK_GROUP_PATH.equals(path)) {
                this.parentGroup = rootGroup();
                // JDK group has already been initialized by JGroupMain
            } else if (!ROOT_GROUP_PATH.equals(path)) {
                System.err.println("Should not call this with path=" + path);
            }
        } else {
            this.parentGroup = tenant.getParent() == null ? jvmGroup() : tenant.getParent().getJGroup();
            dispatcher.initUserGroup(this, tenant.getConfiguration().getAllLimits());
        }
    }

    //
    // To generate group path for non-ROOT tenant containers
    // current pattern:
    //      "JVM_GROUP_PATH/t<tenant id>"
    //
    private String getTenantGroupPath(TenantContainer tenant) {
        TenantContainer parent = tenant.getParent();
        return (parent == null ? JVM_GROUP_PATH : parent.getJGroup().groupPath)
                + File.separator + "t" + tenant.getTenantId();
    }

    // returns the relative cgroup path of this JGroup
    String groupPath() {
        return groupPath;
    }

    // returns parent JGroup of this one, null if current is the top most JGroup
    JGroup parent() {
        return parentGroup;
    }
    /*
     * Destroy this jgroup
     */
    void destroy() {
        dispatcher.destroyGroup(this);
    }

    /*
     * Attach the current thread into this jgroup.
     * @return 0 if successful
     */
    void attach() {
        dispatcher.attach(this);
    }

    /**
     * Detach the current thread from this jgroup
     * @return 0 if successful
     */
    void detach() {
        dispatcher.attach(jvmGroup);
    }

    /**
     * Get cpuacct usage of this group
     * @return cpu usage in nano seconds
     */
    long getCpuTime() {
        try {
            return Long.parseLong(dispatcher.getValue(this, "cpuacct.usage"));
        } catch (Exception e) {
            System.err.println("Exception from JGroup.getCpuTime()");
            e.printStackTrace();
        }
        return -1;
    }

    /*
     * Set the value of a cgroup attribute
     */
    synchronized void setValue(String key, String name) {
        dispatcher.setValue(this, key, name);
    }

    /*
     * Get the value of a cgroup attribute
     */
    synchronized String getValue(String key) {
        return dispatcher.getValue(this, key);
    }

    /*
     * Initialize the JGroup class, called by "Threads::create_vm()"
     */
    private static void initializeJGroupClass() {
        try {
            // NOTE: below sequence should not be changed!
            rootGroup = new JGroup(null, ROOT_GROUP_PATH);
            jdkGroup = new JGroup(null, JDK_GROUP_PATH);
            jvmGroup = new JGroup(null, JVM_GROUP_PATH);

            // check before attach
            checkRootGroupPath();

            dispatcher.attach(jvmGroup);
        } catch (Throwable t) {
            System.err.println("Failed to initialize JGroup");
            t.printStackTrace();
            // do not throw any exception during VM initialization, but just exit
            System.exit(128);
        }
    }

    /*
     * Destory the JGroup class, called by Threads::destroy_vm.
     */
    private static void destroyJGroupClass() {
        jvmGroup.destroy();
        jvmGroup = null;
    }

    // Check if mandatory root group paths exist
    private static void checkRootGroupPath() {
        List<String> controllers = new ArrayList<>(4);
        if (TenantGlobals.isCpuThrottlingEnabled()) {
            controllers.add("cpu");
            controllers.add("cpuset");
        }
        if (TenantGlobals.isCpuAccountingEnabled()) {
            controllers.add("cpuacct");
        }
        for (String ctrl : controllers) {
            String r = rootPathOf(ctrl);
            if (r == null || !Files.exists(Paths.get(r, ROOT_GROUP_PATH))) {
                throw new IllegalArgumentException("Bad ROOT group path: " + r + File.separator + ROOT_GROUP_PATH);
            }
        }
    }

    // For now only used by test code
    static String rootPathOf(String controllerName) {
        return NativeDispatcher.rootPathOf(controllerName);
    }

    // --- debugging support ---
    static boolean debugJGroup;
    private static final String DEBUG_JGROUP_PROP = "com.alibaba.tenant.debugJGroup";

    // property name of user specified ROOT cgroup path, relative to each controller's ROOT path
    private static final String PROP_ROOT_GROUP = "com.alibaba.tenant.jgroup.rootGroup";

    // property name of user specified JDK group path, relative to ROOT_GROUP_PATH
    private static final String PROP_JDK_GROUP = "com.alibaba.tenant.jgroup.jdkGroup";

    // default value of property PROP_JDK_GROUP
    private static final String DEFAULT_JDK_GROUP_NAME = "ajdk_multi_tenant";

    // default value of ROOT_GROUP_PATH
    private static final String DEFAULT_ROOT_GROUP_PATH = "/";

    static {
        dispatcher = new NativeDispatcher();

        String prop = System.getProperty(PROP_ROOT_GROUP);
        if (prop == null) {
            ROOT_GROUP_PATH = DEFAULT_ROOT_GROUP_PATH;
        } else {
            ROOT_GROUP_PATH = prop;
        }

        prop = System.getProperty(PROP_JDK_GROUP);
        if (prop == null) {
            JDK_GROUP_PATH = ROOT_GROUP_PATH + File.separator + DEFAULT_JDK_GROUP_NAME;
        } else {
            JDK_GROUP_PATH = ROOT_GROUP_PATH + File.separator + prop;
        }

        JVM_GROUP_PATH = JDK_GROUP_PATH + File.separator + ProcessHandle.current().pid();

        Properties props = GetPropertyAction.privilegedGetProperties();
        debugJGroup = Boolean.parseBoolean(props.getProperty(DEBUG_JGROUP_PROP));
    }
}
