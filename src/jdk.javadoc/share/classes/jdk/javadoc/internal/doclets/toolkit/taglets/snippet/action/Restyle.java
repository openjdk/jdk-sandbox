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

package jdk.javadoc.internal.doclets.toolkit.taglets.snippet.action;

import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.Scope;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.Style;
import jdk.javadoc.internal.doclets.toolkit.taglets.snippet.text.StyledText;

import java.util.Iterator;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// highlight [type-of-highlighter; default=bold]
//           [regex; default=/.*/]
//           [scope; default=this-line]

public class Restyle implements Action {

    private final Function<Style, Style> restylingFunction;
    private final Pattern pattern;
    private final Scope scope;

    public Restyle(Function<Style, Style> f, Pattern p, Scope s) {
        this.restylingFunction = f;
        this.pattern = p;
        this.scope = s;
    }

    @Override
    public void perform() {
        Iterator<StyledText> texts = scope.newTextsIterator();
        while (texts.hasNext()) {
            StyledText text = texts.next();
            CharSequence s = text.asCharSequence();
            Matcher matcher = pattern.matcher(s);
            while (matcher.find()) {
                int start = matcher.start();
                int end = matcher.end();
                text.restyle(start, end, restylingFunction);
            }
        }
    }
}
