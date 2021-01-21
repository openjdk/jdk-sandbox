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

package com.sun.source.doctree;

import javax.lang.model.element.Name;
import java.util.List;

/**
 * A tree node for an attribute (a name-value pair) in an inline tag.
 *
 * @apiNote Note that this is different from {@link AttributeTree}, which is an attribute in an HTML element.
 *
 * @since
 */
public interface TagAttributeTree extends DocTree {

    /**
     * The kind of an attribute value. More constants may be added to this enum later.
     */
    enum ValueKind {
        /** The attribute value is enclosed in single quotation marks. */
        SINGLE,
        /** The attribute value is enclosed in double quotation marks. */
        DOUBLE,
    }

    /**
     * Returns the name of the attribute.
     * @return the name
     */
    Name getName();

    /**
     * Returns the value of the attribute.
     * @return the value
     */
    // FIXME: (API consistency) the return type could be List<? extends TextTree> or even TextTree
    List<? extends DocTree> getValue();

    /**
     * Returns the kind of the attribute value.
     * @return the kind of the attribute value
     */
    ValueKind getValueKind();
}
