/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package org.openjdk.micro.util;

import java.lang.management.ManagementPermission;
import java.lang.reflect.ReflectPermission;
import java.io.File;
import java.io.FilePermission;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.security.NoSuchAlgorithmException;
import java.security.Permission;
import java.security.Policy;
import java.security.SecurityPermission;
import java.security.URIParameter;
import java.util.PropertyPermission;

/**
 * Help class to create and load Security Policy file from a set of Permissions
 */
public class SecurityManagerHelper {

    /**
     * Create and load a security manager using the provided permissions.
     *
     * @param perms Permissions to add to the file
     *
     * @throws IOException If file could not be created or written to.
     * @throws NoSuchAlgorithmException if no Provider supports a PolicySpi
     * implementation for the specified type.
     */
    public static void setupSecurityManager(Permission... perms)
            throws IOException, NoSuchAlgorithmException {

        URI policyURI = createSecurityFile(perms);
        Policy.setPolicy(Policy.getInstance("JavaPolicy", new URIParameter(policyURI)));
        System.setSecurityManager(new SecurityManager());
    }

    private static URI createSecurityFile(Permission... perms) throws IOException {

        File policyFile = File.createTempFile("security", ".policy");
        policyFile.deleteOnExit();

        try (PrintStream writer = new PrintStream(policyFile)) {
            writer.println("grant {");
            for (Permission p : perms) {
                appendPermission(writer, p);
            }
            // Permissions required by JMH
            appendPermission(writer, new RuntimePermission("modifyThread"));
            // Required when running without forking
            appendPermission(writer, new FilePermission("<<ALL FILES>>", "read,write,delete,execute"));
            appendPermission(writer, new RuntimePermission("accessDeclaredMembers"));
            appendPermission(writer, new RuntimePermission("createSecurityManager"));
            appendPermission(writer, new ReflectPermission("suppressAccessChecks"));
            appendPermission(writer, new ManagementPermission("monitor"));
            appendPermission(writer, new PropertyPermission("*", "read"));
            appendPermission(writer, new SecurityPermission("createPolicy.JavaPolicy"));
            appendPermission(writer, new SecurityPermission("setPolicy"));
            appendPermission(writer, new RuntimePermission("setSecurityManager"));
            writer.println("};");
        }

        return policyFile.toURI();
    }

    private static void appendPermission(PrintStream writer, Permission p) {
        writer.printf("\tpermission %s \"%s\", \"%s\";\n",
                      p.getClass().getName(), p.getName(), p.getActions());
    }
}
