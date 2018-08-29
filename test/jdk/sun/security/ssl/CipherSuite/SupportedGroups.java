/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 * @bug 8171279
 * @library /javax/net/ssl/templates
 * @summary Test TLS connection with each individual supported group
 * @run main/othervm SupportedGroups secp256r1
 * @run main/othervm SupportedGroups secp384r1
 * @run main/othervm SupportedGroups secp521r1
 * @run main/othervm SupportedGroups x25519
 * @run main/othervm SupportedGroups x448
 * @run main/othervm SupportedGroups ffdhe2048
 * @run main/othervm SupportedGroups ffdhe3072
 * @run main/othervm SupportedGroups ffdhe4096
 * @run main/othervm SupportedGroups ffdhe6144
 * @run main/othervm SupportedGroups ffdhe8192
 */


public class SupportedGroups extends SSLSocketTemplate {
    /*
     * Run the test case.
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("jdk.tls.namedGroups", args[0]);

        (new SupportedGroups()).run();
    }
}
