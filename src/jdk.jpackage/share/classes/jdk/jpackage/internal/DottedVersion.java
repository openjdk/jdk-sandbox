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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Dotted numeric version string.
 * E.g.: 1.0.37, 10, 0.5
 */
class DottedVersion implements Comparable<String> {

    public DottedVersion(String version) {
        components = parseVersionString(version);
        value = version;
    }

    @Override
    public int compareTo(String o) {
        int result = 0;
        int[] otherComponents = parseVersionString(o);
        for (int i = 0; i < Math.min(components.length, otherComponents.length)
                && result == 0; ++i) {
            result = components[i] - otherComponents[i];
        }

        if (result == 0) {
            result = components.length - otherComponents.length;
        }

        return result;
    }

    private static int[] parseVersionString(String version) {
        Objects.requireNonNull(version);
        if (version.isEmpty()) {
            throw new IllegalArgumentException("Version may not be empty string");
        }

        int lastNotZeroIdx = -1;
        List<Integer> components = new ArrayList<>();
        for (var component : version.split("\\.", -1)) {
            if (component.isEmpty()) {
                throw new IllegalArgumentException(String.format(
                        "Version [%s] contains a zero lenght component", version));
            }

            int num = Integer.parseInt(component);
            if (num < 0) {
                throw new IllegalArgumentException(String.format(
                        "Version [%s] contains invalid component [%s]", version,
                        component));
            }

            if (num != 0) {
                lastNotZeroIdx = components.size();
            }
            components.add(num);
        }

        if (lastNotZeroIdx + 1 != components.size()) {
            // Strip trailing zeros.
            components = components.subList(0, lastNotZeroIdx + 1);
        }

        return components.stream().mapToInt(Integer::intValue).toArray();
    }

    @Override
    public String toString() {
        return value;
    }

    final private int[] components;
    final private String value;
}
