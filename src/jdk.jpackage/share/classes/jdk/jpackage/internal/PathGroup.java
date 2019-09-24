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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
    Collection<Path> paths() {
        return entries.values();
    }

    /**
     * Root entries.
     */
    List<Path> roots() {
        // Sort by the number of path components in ascending order.
        List<Path> sorted = paths().stream().sorted(
                (a, b) -> a.getNameCount() - b.getNameCount()).collect(
                        Collectors.toList());

        return paths().stream().filter(
                v -> v == sorted.stream().sequential().filter(
                        v2 -> v == v2 || v2.endsWith(v)).findFirst().get()).collect(
                        Collectors.toList());
    }

    long sizeInBytes() throws IOException {
        long reply = 0;
        for (Path dir : roots().stream().filter(f -> Files.isDirectory(f)).collect(
                Collectors.toList())) {
            reply += Files.walk(dir).filter(p -> Files.isRegularFile(p)).mapToLong(
                    f -> f.toFile().length()).sum();
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

        // Reorder entries. Entries with source entries with the least amount of
        // descending entries found between source entries should go first.
        entries.sort((e1, e2) -> e1.getKey().getNameCount() - e2.getKey().getNameCount());

        for (var entry : entries.stream().sequential().filter(e -> {
            return e == entries.stream().sequential().filter(e2 -> isDuplicate(e2, e)).findFirst().get();
                }).collect(Collectors.toList())) {
            Path src = entry.getKey();
            Path dst = entry.getValue();

            if (src.equals(dst)) {
                continue;
            }

            Files.createDirectories(dst.getParent());
            if (move) {
                Files.move(src, dst);
            } else if (src.toFile().isDirectory()) {
                IOUtils.copyRecursive(src, dst);
            } else {
                IOUtils.copyFile(src.toFile(), dst.toFile());
            }
        }
    }

    private static boolean isDuplicate(Map.Entry<Path, Path> a,
            Map.Entry<Path, Path> b) {
        if (a == b || a.equals(b)) {
            return true;
        }

        if (b.getKey().getNameCount() < a.getKey().getNameCount()) {
            return isDuplicate(b, a);
        }

        if (!a.getKey().endsWith(b.getKey())) {
            return false;
        }

        Path relativeSrcPath = a.getKey().relativize(b.getKey());
        Path relativeDstPath = a.getValue().relativize(b.getValue());

        return relativeSrcPath.equals(relativeDstPath);
    }

    private final Map<Object, Path> entries;
}
