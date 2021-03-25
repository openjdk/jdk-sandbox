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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action;

import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.Scope;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.StyledText;

import java.util.Iterator;

// Populates bookmarks in StyledText
public class Start implements Action {

    private final String name;
    private final Scope scope;

    public Start(String name, Scope s) {
        this.name = name;
        this.scope = s;
    }

    @Override
    public void perform() {
        Iterator<StyledText> texts = scope.newTextsIterator();
        if (!texts.hasNext()) {
            return;
        }
        StyledText text = texts.next();
        assert !texts.hasNext();
        text.setBookmark(name, 0, text.length()); // TODO: revisit this logic
    }
}
