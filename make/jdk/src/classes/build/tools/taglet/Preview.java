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

package build.tools.taglet;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import javax.lang.model.element.Element;
import com.sun.source.doctree.DocTree;
import com.sun.source.doctree.UnknownBlockTagTree;
import jdk.javadoc.doclet.Taglet;
import static jdk.javadoc.doclet.Taglet.Location.*;

/**
 * An block tag to insert a standard warning about a preview API.
 */
public class Preview implements Taglet {

    /** Returns the set of locations in which a taglet may be used. */
    @Override
    public Set<Location> getAllowedLocations() {
        return EnumSet.of(MODULE, PACKAGE, TYPE, CONSTRUCTOR, METHOD, FIELD);
    }

    @Override
    public boolean isInlineTag() {
        return false;
    }

    @Override
    public String getName() {
        return "preview";
    }

    @Override
    public String toString(List<? extends DocTree> tags, Element elem) {
        StringBuilder sb = new StringBuilder();
	sb.append("<dt>Preview Feature:\n");
        for (DocTree tag : tags) {
	    UnknownBlockTagTree ubt = (UnknownBlockTagTree) tag;
	    sb.append("<dd style=\"border: 1px solid red; border-radius: 5px; padding: 5px; font-size: larger\"> <b>Preview:</b> ")
		.append(ubt.getContent()); 
        }
	return sb.toString();
    }
}

