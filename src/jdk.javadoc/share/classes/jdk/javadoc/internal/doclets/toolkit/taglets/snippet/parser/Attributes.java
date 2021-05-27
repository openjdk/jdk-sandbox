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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.parser;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/*
 * Provides convenient access to attributes.
 *
 * Although a four-state attribute could be modelled as
 *
 * Optional<Optional<String>> this class models it using a dedicated type for an
 * valueless attribute rather than yet another, inner, optional.
 *
 * This class clearly disambiguates between the following three cases:
 *
 *   1. An absent attribute
 *   2. An attribute with no value
 *   3. An attribute with an empty value
 */
public final class Attributes {

    private final Map<String, List<Attribute>> attributes;

    public Attributes(Collection<? extends Attribute> attributes) {
        this.attributes = attributes
                .stream()
                .collect(Collectors.groupingBy(Attribute::name,
                                               Collectors.toList()));
    }

    /*
     * 1. If there are multiple attributes with the same name and type, it is
     * unknown which one of these attributes will be returned.
     *
     * 2. If there are no attributes with this name and type, an empty optional
     * will be returned.
     *
     * 3. If a non-specific (any/or/union/etc.) result is required, query for
     * the Attribute.class type.
     */
    public <T extends Attribute> Optional<T> get(String name, Class<T> type) {
        return attributes.getOrDefault(name, List.of())
                .stream()
                .filter(type::isInstance)
                .map(type::cast)
                .findAny();
    }

    public int size() {
        return attributes.values().stream().mapToInt(List::size).sum();
    }

    public boolean isEmpty() {
        return attributes.isEmpty();
    }
}
