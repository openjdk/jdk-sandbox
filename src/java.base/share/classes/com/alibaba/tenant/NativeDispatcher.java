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

import java.io.File;
import java.io.IOException;
import java.nio.file.StandardOpenOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.AccessController;
import java.util.Collection;
import java.util.LinkedList;

import static com.alibaba.tenant.JGroup.*;

/**
 * All cgroup operations are in this class
 *
 */
class NativeDispatcher {

    // names of cgroup controllers
    static final String CG_CPU              = "cpu";
    static final String CG_CPU_SHARES       = "cpu.shares";
    static final String CG_CPU_CFS_QUOTA    = "cpu.cfs_quota_us";
    static final String CG_CPU_CFS_PERIOD   = "cpu.cfs_period_us";
    static final String CG_CPUSET           = "cpuset";
    static final String CG_CPUSET_CPUS      = "cpuset.cpus";
    static final String CG_CPUSET_MEMS      = "cpuset.mems";
    static final String CG_CPUACCT          = "cpuacct";
    static final String CG_TASKS            = "tasks";

    // flags to indicate if target cgroup controllers has been enabled
    // will be initialized by native code
    static boolean IS_CPU_SHARES_ENABLED  = false;
    static boolean IS_CPU_CFS_ENABLED     = false;
    static boolean IS_CPUSET_ENABLED      = false;
    static boolean IS_CPUACCT_ENABLED     = false;

    // mountpoint of each controller, will be initialized by native code
    private static String CG_MP_CPU       = null;
    private static String CG_MP_CPUSET    = null;
    private static String CG_MP_CPUACCT   = null;


    // controllers to clean up when destroying a group,
    // to handle several controllers linked to same directory.
    private static final Collection<Path> VICTIM_CTRL_ROOTS = new LinkedList<>();

    // Initialize a cgroup with user specified limits after JVM bootstrapping,
    // for non-ROOT tenant containers
    void initUserGroup(JGroup jgroup, ResourceLimit ... limits) {
        try {
            initCgroupCommon(jgroup);

            // set value with user specified values
            // ignoring non-cgroup resources, like ResourceType.MEMORY
            if (limits != null) {
                for (ResourceLimit limit : limits) {
                    if (limit.type().isJGroupResource()) {
                        limit.sync(jgroup);
                    }
                }
            }
        } catch (RuntimeException e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    // Initialize system cgroups, used by JGroup#initializeJGroupClass
    // during JVM initialization to create the JDK and JVM group.
    // NOTE: never throw exception here, will cause VM code to crash, very rude!
    void initSystemGroup(JGroup jgroup) {
        try {
            initCgroupCommon(jgroup);
        } catch (Throwable t) {
            t.printStackTrace();
            System.exit(127);
        }
    }

    // create and initialize underlying cgroups
    private void initCgroupCommon(JGroup jgroup) {
        if (jgroup == null || jgroup.groupPath() == null
                || jgroup.groupPath().isEmpty()) {
            throw new IllegalArgumentException("Bad argument to createCGroup()");
        }

        if (createCgroup0(jgroup.groupPath()) == 0) {
            // init two mandatory fields
            if (IS_CPUSET_ENABLED) {
                copyValueFromParentGroup(jgroup, CG_CPUSET_CPUS);
                copyValueFromParentGroup(jgroup, CG_CPUSET_MEMS);
            }
            debug("Created group with standard controllers");
        } else {
            throw new RuntimeException("Failed to create cgroup at " + jgroup.groupPath());
        }
    }

    // Copy config value from parent group
    private void copyValueFromParentGroup(JGroup jgroup, String configName) {
        String parentValue = getValue(jgroup.parent(), configName);
        setValue(jgroup, configName, parentValue);
        debug("Set group " + jgroup.groupPath()+ "'s config " + configName
                + " with parent group's value: " + parentValue);
    }

    // evacuate all tasks to another taskfile and delete the group recursively
    private void evacuateTasksAndDeleteGroup(Path groupPath,
                                     Path destTaskPath) throws IOException {
        if (!Files.exists(groupPath) || !Files.isDirectory(groupPath)) {
            return;
        }

        boolean hasSubDirs = Files.list(groupPath).anyMatch(Files::isDirectory);
        if (hasSubDirs) {
            Files.list(groupPath)
                    .filter(Files::isDirectory)
                    .forEach(path -> {
                        try {
                            // delete sub-directories firstly, recursively
                            evacuateTasksAndDeleteGroup(path, destTaskPath);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
        Files.readAllLines(Paths.get(groupPath.toString(), CG_TASKS))
                .forEach(pid -> {
                    try {
                        Files.write(destTaskPath, pid.getBytes(), StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                });

        Files.deleteIfExists(groupPath);
    }

    // Not a frequent path, implementing with Java
    void destroyGroup(JGroup jgroup) {
        Path parentTasks = configPathOf(jgroup.parent(), CG_TASKS);
        // for each separate mount point
        VICTIM_CTRL_ROOTS.stream().forEach(ps -> {
            Path absGroupPath = Paths.get(ps.toString(), jgroup.groupPath());
            try {
                evacuateTasksAndDeleteGroup(absGroupPath, parentTasks);
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
    }

    void attach(JGroup jgroup) {
        if (attachTask0(jgroup.groupPath()) != 0) {
            throw new IllegalStateException("Cannot attach to group " + jgroup);
        } else {
            debug("Attached to cgroup: " + jgroup.groupPath());
        }
    }

    // Not a frequent operation, using Java implementation
    String getValue(JGroup group, String key) {
        if (group == null || group.groupPath() == null || group.groupPath().isEmpty()
                || key == null || key.isEmpty() || !key.contains(".")) {
            return null;
        }
        String ctrlName = key.split("\\.")[0];
        Path configPath = Paths.get(rootPathOf(ctrlName), group.groupPath(), key);
        if (Files.exists(configPath)) {
            try {
                String value = new String(Files.readAllBytes(configPath)).trim();
                debug(key + "=" + value);
                return value;
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            debug("getValue: config path " + configPath + " does not exist");
        }
        return null;
    }

    // Not a frequent operation, using Java implementation
    void setValue(JGroup group, String key, String value) {
        if (group == null || group.groupPath() == null || group.groupPath().isEmpty()
                || key == null || key.isEmpty()
                || value == null || value.isEmpty()
                || !key.contains(".")) {
            throw new IllegalArgumentException("Cannot set " + group.groupPath() + File.separator + key + "=" + value);
        }
        String controllerName = key.split("\\.")[0];
        Path configPath = Paths.get(rootPathOf(controllerName), group.groupPath(), key);

        if (Files.exists(configPath)) {
            try {
                debug("Set value: " + configPath + "=" + value);
                Files.write(configPath, value.getBytes());
            } catch (IOException e) {
                throw new RuntimeException("Failed to set " + group.groupPath() + File.separator + key + " = " + value, e);
            }
        } else {
            debug("setValue: config path " + configPath + " does not exist");
        }
    }

    static String rootPathOf(String controllerName) {
        switch (controllerName) {
            case CG_CPU     : return CG_MP_CPU;
            case CG_CPUSET  : return CG_MP_CPUSET;
            case CG_CPUACCT : return CG_MP_CPUACCT;
            default:
                throw new IllegalArgumentException("Unsupported controller : " + controllerName);
        }
    }

    private static Path configPathOf(JGroup group, String config) {
        String rootPath = null;
        try {
            rootPath = rootPathOf(config.split("\\.")[0]);
        } catch (IllegalArgumentException e) {
            // using 'cpu' group root as default
            rootPath = rootPathOf(CG_CPU);
        }
        return Paths.get(rootPath, group.groupPath(), config);
    }

    int getAliOSPrimaryVersion() {
        return getAliOSPrimaryVersion0();
    }

    // ------------ for debugging ----------------
    private static void debug(String... messages) {
        if (debugJGroup) {
            System.out.print("[JGroupDispatcher] ");
            for (String msg : messages) {
                System.out.print(msg);
                System.out.print(" ");
            }
            System.out.println();
        }
    }

    // -- native wrapper --
    private static native int createCgroup0(String groupPath);

    private static native int attachTask0(String groupPath);

    private static native int init0();

    private static native int getAliOSPrimaryVersion0();

    private static final boolean CGROUP_INIT_SUCCESS;

    private static void resolveAndAddRootPath(String controller) {
        try {
            // resolve symbolic links
            Path p = Paths.get(rootPathOf(controller)).toRealPath();
            if (!VICTIM_CTRL_ROOTS.contains(p)) {
                VICTIM_CTRL_ROOTS.add(p);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void detectVictimRootPaths() {
        if (IS_CPU_CFS_ENABLED || IS_CPU_SHARES_ENABLED) {
            resolveAndAddRootPath(CG_CPU);
        }
        if (IS_CPUSET_ENABLED) {
            resolveAndAddRootPath(CG_CPUSET);
        }
        if (IS_CPUACCT_ENABLED) {
            resolveAndAddRootPath(CG_CPUACCT);
        }
    }

    static {
        System.loadLibrary("java");
        CGROUP_INIT_SUCCESS = (0 == init0());
        if (!CGROUP_INIT_SUCCESS) {
            throw new RuntimeException("JGroup native dispatcher initialization failed!");
        }

        detectVictimRootPaths();
    }
}