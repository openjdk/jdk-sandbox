/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package jdk.jpackage.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.stream.Collectors;
import java.util.stream.Stream;


/**
 * Group of paths.
 * Each path in the group is assigned a unique id.
 */
final class PathGroup {
    PathGroup(Map<Object, Path> paths) {
        entries = Collections.unmodifiableMap(paths);
    }

    Path getPath(Object id) {
        return entries.get(id);
    }

    /**
     * All configured entries.
     */
    List<Path> paths() {
        return entries.values().stream().collect(Collectors.toList());
    }

    /**
     * Root entries.
     */
    List<Path> roots() {
        // Sort by the number of path components in ascending order.
        List<Map.Entry<Path, Path>> sorted = normalizedPaths().stream().sorted(
                (a, b) -> a.getKey().getNameCount() - b.getKey().getNameCount()).collect(
                        Collectors.toList());

        // Returns `true` if `a` is a parent of `b`
        BiFunction<Map.Entry<Path, Path>, Map.Entry<Path, Path>, Boolean> isParentOrSelf = (a, b) -> {
            return a == b || b.getKey().startsWith(a.getKey());
        };

        return sorted.stream().filter(
                v -> v == sorted.stream().sequential().filter(
                        v2 -> isParentOrSelf.apply(v2, v)).findFirst().get()).map(
                        v -> v.getValue()).collect(Collectors.toList());
    }

    long sizeInBytes() throws IOException {
        long reply = 0;
        for (Path dir : roots().stream().filter(f -> Files.isDirectory(f)).collect(
                Collectors.toList())) {
            try (Stream<Path> stream = Files.walk(dir)) {
                reply += stream.filter(p -> Files.isRegularFile(p)).mapToLong(
                        f -> f.toFile().length()).sum();
            }
        }
        return reply;
    }

    PathGroup resolveAt(Path root) {
        return new PathGroup(entries.entrySet().stream().collect(
                Collectors.toMap(e -> e.getKey(),
                        e -> root.resolve(e.getValue()))));
    }

    void copy(PathGroup dst) throws IOException {
        copy(this, dst, false);
    }

    void move(PathGroup dst) throws IOException {
        copy(this, dst, true);
    }

    static interface Facade<T> {
        PathGroup pathGroup();

        default Collection<Path> paths() {
            return pathGroup().paths();
        }

        default List<Path> roots() {
            return pathGroup().roots();
        }

        default long sizeInBytes() throws IOException {
            return pathGroup().sizeInBytes();
        }

        T resolveAt(Path root);

        default void copy(Facade<T> dst) throws IOException {
            pathGroup().copy(dst.pathGroup());
        }

        default void move(Facade<T> dst) throws IOException {
            pathGroup().move(dst.pathGroup());
        }
    }

    private static void copy(PathGroup src, PathGroup dst, boolean move) throws
            IOException {
        copy(move, src.entries.keySet().stream().filter(
                id -> dst.entries.containsKey(id)).map(id -> Map.entry(
                src.entries.get(id), dst.entries.get(id))).collect(
                Collectors.toCollection(ArrayList::new)));
    }

    private static void copy(boolean move, List<Map.Entry<Path, Path>> entries)
            throws IOException {

        // destination -> source file mapping
        Map<Path, Path> actions = new HashMap<>();
        for (var action: entries) {
            Path src = action.getKey();
            Path dst = action.getValue();
            if (src.toFile().isDirectory()) {
               try (Stream<Path> stream = Files.walk(src)) {
                   stream.forEach(path -> actions.put(dst.resolve(
                           src.relativize(path)).toAbsolutePath().normalize(),
                           path));
               }
            } else {
                actions.put(dst.toAbsolutePath().normalize(), src);
            }
        }

        for (var action : actions.entrySet()) {
            Path dst = action.getKey();
            Path src = action.getValue();

            if (src.equals(dst) || !src.toFile().exists()) {
                continue;
            }

            if (src.toFile().isDirectory()) {
                Files.createDirectories(dst);
            } else {
                Files.createDirectories(dst.getParent());
                if (move) {
                    Files.move(src, dst);
                } else {
                    Files.copy(src, dst);
                }
            }
        }

        if (move) {
            // Delete source dirs.
            for (var entry: entries) {
                File srcFile = entry.getKey().toFile();
                if (srcFile.isDirectory()) {
                    IOUtils.deleteRecursive(srcFile);
                }
            }
        }
    }

    private static Map.Entry<Path, Path> normalizedPath(Path v) {
        final Path normalized;
        if (!v.isAbsolute()) {
            normalized = Path.of("./").resolve(v.normalize());
        } else {
            normalized = v.normalize();
        }

        return Map.entry(normalized, v);
    }

    private List<Map.Entry<Path, Path>> normalizedPaths() {
        return entries.values().stream().map(PathGroup::normalizedPath).collect(
                Collectors.toList());
    }

    private final Map<Object, Path> entries;
}
