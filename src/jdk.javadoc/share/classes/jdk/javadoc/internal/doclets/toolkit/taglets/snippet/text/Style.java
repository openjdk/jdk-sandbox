/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.SortedLists.computeIfAbsent;

/*
 * A union of primitive styles.
 *
 * This class shares returned instances. Because of this, clients must not use
 * identity-sensitive operations on instances of Style; this class is
 * value-based in the same sense that, for example, java.util.Optional is.
 */
public class Style {

    // Nodes along the path are sorted; siblings may not be

    // StyleFactory for flexible caching (e.g. per text)
    // total order on styles

    // Given components, how to find their cached aggregating object?
    // The trick is not to create an object unnecessarily.

    private static final Node root = new Node(null, null);
    private static final None NONE = new None();
    private final List<Style> primitiveStyles;
    private final Set<Style> styles;

    public Style(Style[] path) {
        this.primitiveStyles = List.of(path);
        this.styles = Set.of(path);
    }

    private Style() {
        primitiveStyles = List.of(this);
        styles = Set.of(this);
    }

    public static Link link(String target) {
        return (Link) computeIfAbsent(root.children, target, Style::compareByLink, (t) -> {
            Node node = new Node(root, new Link(t));
            node.unionStyle = node.primitiveStyle;
            return node;
        }).primitiveStyle;
    }

    private static int compareByLink(Node node, String t) {
        Style s = node.primitiveStyle;
        int cmp = compare(s.getClass(), Link.class);
        if (cmp != 0)
            return cmp;
        return compareLink((Link) s, t);
    }

    private static int compare(Class<? extends Style> a, Class<? extends Style> b) {
        return a.getName().compareTo(b.getName());
    }

    private static int compareLink(Link style, String target) {
        return style.target.compareTo(target);
    }

    public static Name name(String name) {
        return (Name) computeIfAbsent(root.children, name, Style::compareByName, (String n) -> {
            Node node = new Node(root, new Name(n));
            node.unionStyle = node.primitiveStyle;
            return node;
        }).primitiveStyle;
    }

    private static int compareByName(Node node, String name) {
        Style s = node.primitiveStyle;
        int cmp = compare(s.getClass(), Name.class);
        if (cmp != 0)
            return cmp;
        return compareName((Name) s, name);
    }

    public static Style none() {
        return NONE;
    }

    private static int compareName(Name style, String name) {
        return style.name.compareTo(name);
    }

    public Style and(Style that) {
        Node node = root;
        int depth = 0;
        int i = 0, j = 0;
        while (i < this.primitiveStyles.size() && j < that.primitiveStyles.size()) {
            Style thisStyle = this.primitiveStyles.get(i);
            Style thatStyle = that.primitiveStyles.get(j);
            Style min;
            int cmp = compare(thisStyle, thatStyle);
            if (cmp > 0) {
                min = thatStyle;
                j++;
            } else if (cmp < 0) {
                min = thisStyle;
                i++;
            } else {
                min = thisStyle;
                i++;
                j++;
            }
            assert compare(min, thisStyle) <= 0 && compare(min, thatStyle) <= 0;
            node = node.getOrPut(min);
            depth++;
        }
        // at most one of the two loops below will be executed
        while (i < this.primitiveStyles.size()) {
            node = node.getOrPut(this.primitiveStyles.get(i++));
            depth++;
        }
        while (j < that.primitiveStyles.size()) {
            node = node.getOrPut(that.primitiveStyles.get(j++));
            depth++;
        }

        if (node.unionStyle != null) {
            return node.unionStyle;
        }

        Style[] path = new Style[depth];
        for (Node n = node; n != root; n = n.parent) {
            path[--depth] = n.primitiveStyle;
        }
        return node.unionStyle = new Style(path);
    }

    private static int compare(Style a, Style b) { // heterogeneously compare
        int cmp = compare(a.getClass(), b.getClass());
        if (cmp != 0) {
            assert a.getClass() != b.getClass();
            return cmp;
        }
        return a.fineCompare(b);
    }

    protected int fineCompare(Style b) { // homogeneously compare
        throw new UnsupportedOperationException();
    }

    @Override
    public String toString() {
        return primitiveStyles.stream()
                .map(Style::toString)
                .collect(Collectors.joining(", "));
    }

    public Set<Style> getStyles() {
        return styles;
    }

    private static final class Node {

        final Style primitiveStyle;
        final List<Node> children = new ArrayList<>();
        Node parent;
        Style unionStyle;

        public Node getOrPut(Style style) {
            // Passing "this" rather than capturing it so as not to create a stateful lambda;
            // this passed as an unused parameter to the comparator, but is used to construct a node
            return computeIfAbsent(children, this, style,
                                   (Node n, Node unused, Style s) -> compare(n.primitiveStyle, s),
                                   (Node p, Style s) -> new Node(p, s));
        }

        Node(Node parent, Style primitiveStyle) {
            this.parent = parent;
            this.primitiveStyle = primitiveStyle;
        }

        @Override
        public String toString() {
            if (parent == null) {
                return "root";
            }
            String s = unionStyle == null ? "none" : unionStyle.toString();
            int size = children.size();
            if (size != 0) {
                s += " (children: " + size + ")";
            }
            return s;
        }
    }

    public static final class Name extends Style {

        private final String name;

        private Name(String name) {
            this.name = name;
        }

        @Override
        protected int fineCompare(Style other) {
            return compareName(this, ((Name) other).name);
        }

        @Override
        public String toString() {
            return "'" + name + "'";
        }

        @Override
        public int hashCode() {
            return name.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Name name1 = (Name) o;
            return name.equals(name1.name);
        }

        public String getName() {
            return name;
        }
    }

    private static final class None extends Style {

        None() {
            super(new Style[]{}); // empty
        }

        @Override
        protected int fineCompare(Style other) {
            assert other instanceof None;
            return 0;
        }

        @Override
        public String toString() {
            return "none";
        }
    }

    public static final class Link extends Style {

        private final String target; // ReferenceTree?

        private Link(String target) {
            this.target = target;
        }

        @Override
        protected int fineCompare(Style other) {
            Link link = (Link) other;
            return compareLink(this, link.target);
        }

        @Override
        public String toString() {
            return "-> " + target;
        }

        @Override
        public int hashCode() {
            return target.hashCode();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Link link = (Link) o;
            return target.equals(link.target);
        }

        public String getTarget() {
            return target;
        }
    }
}
