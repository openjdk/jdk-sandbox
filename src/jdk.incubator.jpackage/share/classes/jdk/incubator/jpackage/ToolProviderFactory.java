/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
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

package jdk.incubator.jpackage;

import jdk.incubator.jpackage.internal.JPackageToolProvider;
import java.io.PrintWriter;
import java.util.Optional;
import java.util.spi.ToolProvider;

/**
 * A factory class to obtain a {@linkplain ToolProvider tool provider}
 * for the incubating {@code jpackage} tool.
 * 
 * It is planned to implement {@code jpackage} tool as a service provider
 * to {@link ToolProvider} in a future release at which point
 * {@link ToolProvider#findFirst} can be used to look up jpackage tool.
 *
 * @since   14
 */

public class ToolProviderFactory { 

    private static ToolProvider provider = new JPackageToolProvider();

    // Prevent creating an instance of this class
    private ToolProviderFactory() {
    }

    /**
     * Returns an {@link Optional} containing the {@code ToolProvider}
     * if the given toolname is "jpackage". Returns an empty
     * {@code Optional} if the given toolname is not "jpackage".
     *
     * @param   toolname {@code String} name of tool to look for.
     * @return  an {@link Optional} containing the {@code ToolPovider}
     *
     * @since 14
     */
    public static Optional<ToolProvider> findFirst(String toolName) {
        if ("jpackage".equals(toolName)) {
            return Optional.of(provider);
        } else {
            return Optional.empty();
        }
    } 

}
