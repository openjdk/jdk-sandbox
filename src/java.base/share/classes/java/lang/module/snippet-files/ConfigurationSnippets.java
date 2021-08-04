/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.module.Configuration;
import java.lang.module.ModuleFinder;
import java.lang.module.ResolvedModule;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Snippets used in ConfigurationSnippets.
 */ 

final class ConfigurationSnippets {
private static void snippet1(Path dir1,
                             Path dir2,
                             Path dir3
                             ) {
// @start region=snippet1 :
    ModuleFinder finder = ModuleFinder.of(dir1, dir2, dir3);

    Configuration parent = ModuleLayer.boot().configuration();

    Configuration cf = parent.resolve(finder, ModuleFinder.of(), Set.of("myapp"));
    cf.modules().forEach(m -> {
        System.out.format("%s -> %s%n",
            m.name(),
            m.reads().stream()
                .map(ResolvedModule::name)
                .collect(Collectors.joining(", ")));
    });
// @end snippet1
}

}
