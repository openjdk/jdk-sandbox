/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates. All rights reserved.
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

package jdk.jpackage.runtime.singleton;

/**
 * The {@code SingleInstanceListener} interface is used for implementing
 * Single Instance functionality for applications packaged by jpackage.
 *
 * @since 13
 */
public interface SingleInstanceListener {

    /**
     * {@code newActivation()} should be implemented by the application to
     * handle the single instance behavior.
     * When a single instance application is running, the launcher of a
     * secondary instance of that application will call {@code newActivation()}
     * in the first running instance instead of launching another instance of
     * the application.
     *
     * @param args
     * Arguments from the instances of the application will be passed
     * into the {@code newActivation()} method. An application developer can
     * decide how to handle the arguments passed by implementating this method.
     */
    public void newActivation(String... args);

}
